package com.git.hui.springai.advance.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.MysqlChatMemoryRepositoryDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 对话记忆配置类 - 基于 H2 内存数据库的手动 JDBC 持久化方案
 * <p>
 * 本配置类演示如何手动创建 {@link JdbcChatMemoryRepository} 并显式指定数据库方言（Dialect），
 * 而非依赖 Spring AI 的自动配置。这种方式适用于以下场景：
 * <ul>
 *     <li>使用 H2、PostgreSQL 等非默认数据库时需要指定对应方言</li>
 *     <li>需要自定义 JdbcTemplate（如多数据源场景）</li>
 *     <li>需要更细粒度地控制 Repository 的创建过程</li>
 * </ul>
 * <p>
 * Dialect 机制说明：
 * <ul>
 *     <li>{@link MysqlChatMemoryRepositoryDialect} - MySQL/MariaDB 方言</li>
 *     <li>Spring AI 还提供了 H2、PostgreSQL、Oracle 等数据库的方言实现</li>
 *     <li>方言决定了 SQL 语句的生成方式（如 JSON 字段处理、UPSERT 语法等）</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/8/6
 * @see JdbcChatMemoryRepository
 * @see MysqlChatMemoryRepositoryDialect
 * @see MessageWindowChatMemory
 */
@Configuration
public class MemConfig {

    /**
     * 创建基于 H2 + 手动 Dialect 的对话记忆 Bean
     * <p>
     * 手动构建 JdbcChatMemoryRepository 并指定 MySQL 方言（H2 兼容 MySQL 语法），
     * 再包装为 MessageWindowChatMemory 实现滑动窗口式上下文管理。
     *
     * @param jdbcTemplate Spring 自动配置的 JdbcTemplate（指向 H2 内存数据库）
     * @return 基于消息窗口的 ChatMemory 实例
     */
    @Bean
    public ChatMemory jdbcChatMemory(JdbcTemplate jdbcTemplate) {
        ChatMemoryRepository chatMemoryRepository = JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                // 在这里，指定不同数据库对应的Dialect
                .dialect(new MysqlChatMemoryRepositoryDialect())
                .build();

        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .build();
    }
}
