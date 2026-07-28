package com.git.hui.springai.advance.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆配置类 - 基于 MySQL 的 JDBC 持久化方案
 * <p>
 * 本配置类负责创建 {@link ChatMemory} Bean，将对话上下文存储到 MySQL 数据库中。
 * <p>
 * 工作原理：
 * <ol>
 *     <li>Spring AI 自动配置会根据数据源类型创建对应的 {@link ChatMemoryRepository}（本模块为 MySQL 方言）</li>
 *     <li>本配置将自动注入的 ChatMemoryRepository 包装为 {@link MessageWindowChatMemory}</li>
 *     <li>MessageWindowChatMemory 采用滑动窗口策略，仅保留最近 N 条消息作为上下文</li>
 * </ol>
 * <p>
 * 与 A02 模块的区别：
 * <ul>
 *     <li>A01（本模块）：依赖 Spring AI 自动配置，自动识别 MySQL 方言，无需手动指定 Dialect</li>
 *     <li>A02：手动创建 JdbcChatMemoryRepository 并显式指定 Dialect（适用于 H2 等非默认数据库）</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/8/6
 * @see MessageWindowChatMemory
 * @see ChatMemoryRepository
 */
@Configuration
public class MemConfig {

    /**
     * 创建基于 JDBC（MySQL）的对话记忆 Bean
     * <p>
     * 利用 Spring AI 自动装配的 ChatMemoryRepository（已配置 MySQL 方言），
     * 构建滑动窗口式对话记忆，默认保留最近的消息窗口。
     *
     * @param chatMemoryRepository Spring AI 自动配置的 JDBC 对话记忆仓库（MySQL 方言）
     * @return 基于消息窗口的 ChatMemory 实例
     */
    @Bean
    public ChatMemory jdbcChatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .build();
    }
}
