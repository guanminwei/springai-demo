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
     * 
     * 
        核心区别在于 **是否需要手动指定 Dialect**：

        **A01（MySQL 模块）**：直接注入 Spring AI **自动配置**好的 `ChatMemoryRepository`，不需要手动创建。
        因为 classpath 中有 `spring-ai-starter-model-chat-memory-repository-jdbc`，Spring AI 的 AutoConfiguration 检测到数据源是 MySQL，
        会**自动选择 MySQL Dialect** 并创建 `JdbcChatMemoryRepository` Bean。所以 A01 只需要把它包装成 `MessageWindowChatMemory` 即可。

        **A02（H2 模块）**：H2 内存数据库**不是** Spring AI JDBC ChatMemory 的默认支持数据库（默认是 MySQL/PostgreSQL），
        自动配置无法正确识别方言，所以需要**手动构建** `JdbcChatMemoryRepository`，
        并通过 `.dialect(new MysqlChatMemoryRepositoryDialect())` 显式指定方言。
        这里用 MySQL 方言是因为 H2 的 MySQL 兼容模式可以复用 MySQL 的 SQL 语法。

        简单总结：

        | | A01 (MySQL) | A02 (H2) |
        |---|---|---|
        | Repository 来源 | Spring AI 自动配置注入 | 手动 builder 创建 |
        | Dialect | 自动识别 MySQL | 手动指定 `MysqlChatMemoryRepositoryDialect` |
        | 原因 | 数据源是 MySQL，自动配置能识别 | H2 不在自动配置的默认方言列表中 |

        本质上 A02 是在演示：**当你使用的数据库不在 Spring AI 自动配置覆盖范围内时，如何通过手动指定 Dialect 来完成适配**。



        Spring AI 1.1.x 中 JdbcChatMemoryRepositoryDialect 接口共有 7 种内置实现：
        Dialect 类	对应数据库
        MysqlChatMemoryRepositoryDialect	MySQL / MariaDB
        PostgresChatMemoryRepositoryDialect	PostgreSQL（默认兜底）
        H2ChatMemoryRepositoryDialect	H2
        HsqldbChatMemoryRepositoryDialect	HSQLDB
        OracleChatMemoryRepositoryDialect	Oracle
        SqlServerChatMemoryRepositoryDialect	SQL Server
        SqliteChatMemoryRepositoryDialect	SQLite

        关于自动识别机制：JdbcChatMemoryRepositoryDialect.from(DataSource) 这个静态方法会根据 DataSource 的 JDBC URL 自动推断使用哪个 Dialect。
        如果识别不了，默认回退到 PostgresChatMemoryRepositoryDialect。


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
