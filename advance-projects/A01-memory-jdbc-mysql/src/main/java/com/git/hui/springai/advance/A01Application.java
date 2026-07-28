package com.git.hui.springai.advance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A01 模块启动类 - 使用 MySQL 持久化对话历史
 * <p>
 * 本模块演示如何通过 Spring AI 的 JDBC ChatMemoryRepository 将 AI 对话上下文
 * 持久化到 MySQL 数据库中，实现跨会话的对话记忆能力。
 * <p>
 * 核心特性：
 * <ul>
 *     <li>基于 Spring AI 自动配置的 JdbcChatMemoryRepository（自动识别 MySQL 方言）</li>
 *     <li>使用 MessageWindowChatMemory 实现滑动窗口式上下文管理</li>
 *     <li>通过 MessageChatMemoryAdvisor 自动将历史对话注入 Prompt</li>
 * </ul>
 * <p>
 * 启动前需确保：
 * <ol>
 *     <li>MySQL 服务已启动，且已创建对应数据库</li>
 *     <li>application.yml 中配置了正确的数据源连接信息</li>
 *     <li>Spring AI 会自动执行建表 DDL（spring.ai.chat.memory.repository.jdbc.initialize-schema=always）</li>
 * </ol>
 *
 * @author YiHui
 * @date 2025/8/4
 * @see com.git.hui.springai.advance.config.MemConfig
 * @see com.git.hui.springai.advance.mvc.ChatController
 */
@SpringBootApplication
public class A01Application {
    public static void main(String[] args) {
        SpringApplication.run(A01Application.class, args);
    }
}