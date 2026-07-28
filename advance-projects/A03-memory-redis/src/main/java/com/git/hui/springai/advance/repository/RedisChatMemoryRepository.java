package com.git.hui.springai.advance.repository;

import com.git.hui.springai.advance.util.JsonUtil;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
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
 *     <li>更新策略：覆盖式更新（先删除旧数据，再写入全量新数据）</li>
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
    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String PREFIX = "chat:";

    /**
     * 查询所有会话id
     *
     * @return
     */
    @Override
    public List<String> findConversationIds() {
        Set<String> ans = redisTemplate.keys(PREFIX + "*");
        return ans.stream().map(key -> key.substring(PREFIX.length())).collect(Collectors.toList());
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
     * 保存会话记录
     *
     * @param conversationId 会话id
     * @param messages       当前上下文的全量数据
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = PREFIX + conversationId;
        List<String> messageJsons = messages.stream().map(JsonUtil::toStr).toList();
        // 先删除旧数据
        redisTemplate.delete(key);
        // 添加新数据，采用覆盖式更新方式
        redisTemplate.opsForList().leftPushAll(key, messageJsons);
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
