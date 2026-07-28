package com.git.hui.springai.advance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A04 模块启动类 - 使用 Langgraph4j 实现多轮对话记忆
 * <p>
 * 本模块演示如何通过 Langgraph4j 框架的 {@link org.bsc.langgraph4j.checkpoint.MemorySaver}
 * 实现 Agent 级别的对话记忆管理，而非依赖 Spring AI 内置的 ChatMemory 机制。
 * <p>
 * 核心特性：
 * <ul>
 *     <li>基于 Langgraph4j 的 StateGraph 构建 Agent 工作流</li>
 *     <li>使用 MemorySaver（Checkpoint 机制）自动保存每轮对话状态</li>
 *     <li>通过 threadId 区分不同用户会话，实现多用户隔离</li>
 *     <li>Agent 执行器（AgentExecutor）封装了 LLM 调用和工具执行逻辑</li>
 * </ul>
 * <p>
 * 与 A01-A03 模块的区别：
 * <ul>
 *     <li>A01-A03：使用 Spring AI 的 ChatMemory + Advisor 模式管理上下文</li>
 *     <li>A04（本模块）：使用 Langgraph4j 的 Checkpoint 机制，更适合复杂 Agent 工作流</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/8/4
 * @see com.git.hui.springai.advance.mem.MemAgent
 * @see com.git.hui.springai.advance.mvc.ChatController
 */
@SpringBootApplication
public class A04Application {
    public static void main(String[] args) {
        SpringApplication.run(A04Application.class, args);
    }
}