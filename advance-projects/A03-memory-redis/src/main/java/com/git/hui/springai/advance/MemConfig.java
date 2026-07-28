package com.git.hui.springai.advance;

import com.git.hui.springai.advance.repository.RedisChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆配置类 - 基于 Redis 的自定义持久化方案
 * <p>
 * 本配置类将自定义的 {@link RedisChatMemoryRepository} 包装为 {@link MessageWindowChatMemory}，
 * 实现基于 Redis 的滑动窗口式对话记忆管理。
 * <p>
 * 工作流程：
 * <ol>
 *     <li>RedisChatMemoryRepository 负责与 Redis 交互，完成消息的 CRUD 操作</li>
 *     <li>MessageWindowChatMemory 在 Repository 之上提供窗口裁剪策略</li>
 *     <li>当消息数量超过窗口大小时，自动丢弃最早的消息</li>
 * </ol>
 *
 * @author YiHui
 * @date 2025/8/6
 * @see RedisChatMemoryRepository
 * @see MessageWindowChatMemory
 */
@Configuration
public class MemConfig {

    /**
     * 创建基于 Redis 的对话记忆 Bean
     * <p>
     * 将自定义的 RedisChatMemoryRepository 作为底层存储，
     * 构建滑动窗口式对话记忆。
     *
     * @param chatMemoryRepository 自定义的 Redis 对话记忆仓库（Spring 自动扫描注入）
     * @return 基于消息窗口的 ChatMemory 实例
     */
    @Bean
    public ChatMemory jdbcChatMemory(RedisChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .build();
    }
}
