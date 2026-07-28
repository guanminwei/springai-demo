package com.git.hui.springai.advance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A02 模块启动类 - 使用 H2 内存数据库持久化对话历史
 * <p>
 * 本模块演示如何通过手动配置 {@link org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository}
 * 并显式指定数据库方言（Dialect），将 AI 对话上下文持久化到 H2 内存数据库中。
 * <p>
 * 核心特性：
 * <ul>
 *     <li>手动创建 JdbcChatMemoryRepository，显式指定 MysqlChatMemoryRepositoryDialect</li>
 *     <li>使用 H2 内存数据库，无需外部数据库依赖，适合快速验证和学习</li>
 *     <li>通过 schema-h2.sql 自动初始化表结构</li>
 *     <li>演示了 Dialect 机制 —— 同一套代码可适配不同数据库</li>
 * </ul>
 * <p>
 * 与 A01 模块的区别：
 * <ul>
 *     <li>A01：依赖 Spring AI 自动配置 ChatMemoryRepository，自动识别数据库方言</li>
 *     <li>A02（本模块）：手动构建 JdbcChatMemoryRepository 并指定 Dialect，灵活度更高</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/8/4
 * @see com.git.hui.springai.advance.config.MemConfig
 * @see com.git.hui.springai.advance.mvc.ChatController
 */
@SpringBootApplication
public class A02Application {
    public static void main(String[] args) {
        SpringApplication.run(A02Application.class, args);
    }
}