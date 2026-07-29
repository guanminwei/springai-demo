package com.git.hui.springai.advance.repository;

import com.git.hui.springai.advance.util.JsonUtil;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Redis 对话记忆仓库 - 自定义实现 ChatMemoryRepository 接口
 * <p>
 * 本类实现了 Spring AI 的 {@link ChatMemoryRepository} 接口，使用 Redis 作为对话历史的存储后端。
 * 适用于需要高性能读写、分布式共享对话状态的生产场景。
 * <p>
 * Redis 数据结构设计：
 * <ul>
 *     <li>Key 格式：{@code chat:{conversationId}}（如 {@code chat:user001}）</li>
 *     <li>Value 类型：Redis List，每个元素为一条消息的 JSON 字符串</li>
 *     <li>写入策略：MULTI-EXEC 事务内先 DEL 再 RPUSH，保证原子性覆盖更新</li>
 *     <li>顺序保证：使用 RPUSH 追加到列表尾部，range(0,-1) 读取即为时间正序</li>
 *     <li>TTL 策略：每次写入后设置可配置的过期时间，避免内存无限增长</li>
 * </ul>
 * <p>
 * 可选方案对比：
 * <ul>
 *     <li>方案一（本实现）：List 结构，key = conversationId, value = 消息 JSON 列表</li>
 *     <li>方案二：Hash 结构，field = conversationId, value = 序列化列表（适合需要部分更新的场景）</li>
 * </ul>
 * <p>
 * 序列化说明：
 * <ul>
 *     <li>消息序列化/反序列化通过 {@link com.git.hui.springai.advance.util.JsonUtil} 完成</li>
 *     <li>Message 为接口类型，反序列化时需要自定义 {@link com.git.hui.springai.advance.util.MessageDeserializer}</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/8/7
 * @see ChatMemoryRepository
 * @see com.git.hui.springai.advance.util.JsonUtil
 * @see com.git.hui.springai.advance.util.MessageDeserializer
 */
@Component
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String PREFIX = "chat:";
    /** SCAN 每次迭代建议返回的数量，平衡内存与网络往返 */
    private static final long SCAN_COUNT = 100;

    private final StringRedisTemplate redisTemplate;
    /** 会话 key 的过期时间；为 null 时不设置 TTL */
    private final Duration ttl;

    /**
     * 构造方法 - 注入 Redis 模板与可选的 TTL 配置
     *
     * @param redisTemplate Spring Data Redis 字符串模板
     * @param ttl           会话过期时间，配置项 {@code app.chat-memory.redis.ttl}，
     *                      默认 24 小时；设为 0 或负值表示不过期
     */
    public RedisChatMemoryRepository(
            StringRedisTemplate redisTemplate,
            @Value("${app.chat-memory.redis.ttl:24h}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        // 非正数视为不过期
        this.ttl = (ttl != null && !ttl.isNegative() && !ttl.isZero()) ? ttl : null;
    }

    /**
     * 查询所有会话id（基于 SCAN 游标迭代，不阻塞 Redis）
     * <p>
     * 使用 SCAN 替代 KEYS 命令，避免在大数据量下阻塞 Redis 主线程。
     * 每次迭代返回少量结果，通过游标逐步遍历直至完成。
     *
     * @return 所有活跃的 conversationId 列表（已去除前缀）
     */
    @Override
    public List<String> findConversationIds() {
        List<String> ids = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(PREFIX + "*")
                .count(SCAN_COUNT)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                ids.add(key.substring(PREFIX.length()));
            }
        }
        return ids;
    }

    /**
     * 查询会话记录
     *
     * @param conversationId 会话id
     * @return
     */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = PREFIX + conversationId;
        // 查询对话历史
        List<String> ans = redisTemplate.opsForList().range(key, 0, -1);
        if (CollectionUtils.isEmpty(ans)) {
            return Collections.emptyList();
        }

        return ans.stream().map(item -> JsonUtil.toObj(item, Message.class)).collect(Collectors.toList());
    }

    /**
     * 保存会话记录（原子性覆盖写入）
     * <p>
     * 使用 Redis MULTI-EXEC 事务保证 delete + rightPushAll 的原子性：
     * <ul>
     *     <li>若事务执行失败（如 Redis 宕机），旧数据不会被删除，避免数据丢失</li>
     *     <li>使用 rightPushAll 保证消息按时间顺序追加到列表尾部</li>
     * </ul>
     *
     * @param conversationId 会话id
     * @param messages       当前上下文的全量数据（按时间顺序排列）
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = PREFIX + conversationId;
        List<String> messageJsons = messages.stream().map(JsonUtil::toStr).toList();

        // MULTI-EXEC 事务：保证 delete + rPush + expire 原子执行
        redisTemplate.execute(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                byte[] rawKey = key.getBytes();
                connection.multi();
                connection.keyCommands().del(rawKey);
                if (!messageJsons.isEmpty()) {
                    byte[][] values = messageJsons.stream()
                            .map(String::getBytes)
                            .toArray(byte[][]::new);
                    connection.listCommands().rPush(rawKey, values);
                }
                // 设置 TTL，每次写入刷新过期时间
                if (ttl != null) {
                    connection.keyCommands().expire(rawKey, ttl.getSeconds());
                }
                connection.exec();
                return null;
            }
        });
    }

    /**
     * 删除会话记录
     *
     * @param conversationId
     */
    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(PREFIX + conversationId);
    }
}
