package com.git.hui.springai.advance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A03 模块启动类 - 使用 Redis 持久化对话历史
 * <p>
 * 本模块演示如何自定义实现 {@link org.springframework.ai.chat.memory.ChatMemoryRepository} 接口，
 * 将 AI 对话上下文持久化到 Redis 中，适用于高并发、分布式部署场景。
 * <p>
 * 核心特性：
 * <ul>
 *     <li>自定义 RedisChatMemoryRepository 实现 ChatMemoryRepository 接口</li>
 *     <li>使用 Redis List 数据结构存储每个会话的消息列表</li>
 *     <li>自定义 Jackson 序列化/反序列化器处理 Spring AI Message 多态类型</li>
 *     <li>基于 MessageWindowChatMemory 实现滑动窗口式上下文管理</li>
 * </ul>
 * <p>
 * 与 A01/A02 模块的区别：
 * <ul>
 *     <li>A01/A02：使用 Spring AI 内置的 JDBC 方案（关系型数据库）</li>
 *     <li>A03（本模块）：自定义 Redis 方案，具有更高的读写性能和可扩展性</li>
 * </ul>
 * <p>
 * 启动前需确保 Redis 服务已启动，且 application.yml 中配置了正确的连接信息。
 *
 * @author YiHui
 * @date 2025/8/4
 * @see com.git.hui.springai.advance.MemConfig
 * @see com.git.hui.springai.advance.repository.RedisChatMemoryRepository
 * @see com.git.hui.springai.advance.mvc.ChatController
 */
@SpringBootApplication
public class A03Application {
    public static void main(String[] args) {
        SpringApplication.run(A03Application.class, args);
    }
}