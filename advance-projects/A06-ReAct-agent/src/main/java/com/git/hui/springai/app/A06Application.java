package com.git.hui.springai.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A06 模块启动类 - ReAct Agent 智能体实现
 * <p>
 * 本模块演示如何从零实现 ReAct（Reasoning + Acting）范式的智能体，
 * 包括同步版和流式版两种实现方式。
 * <p>
 * ReAct 范式核心流程：
 * <pre>
 * 用户提问 → Thinking（思考）→ Act（行动：调用工具）→ Observe（观察结果）→ ... → 最终答案
 * </pre>
 * <p>
 * 核心组件：
 * <ul>
 *     <li>{@link com.git.hui.springai.app.react.simple.SimpleReActAgent} - 同步版 ReAct Agent</li>
 *     <li>{@link com.git.hui.springai.app.react.stream.StreamReActAgent} - 流式版 ReAct Agent</li>
 *     <li>{@link com.git.hui.springai.app.react.simple.CalculatorTools} - 计算器和天气查询工具</li>
 *     <li>{@link com.git.hui.springai.app.react.service.LlmService} - LLM 服务工厂</li>
 *     <li>{@link com.git.hui.springai.app.advisor.MyLoggingAdvisor} - 自定义日志 Advisor</li>
 * </ul>
 * <p>
 * 与 A04/A05 的区别：
 * <ul>
 *     <li>A04/A05：使用 Langgraph4j 框架提供的 Agent 抽象</li>
 *     <li>A06（本模块）：手动实现 ReAct 循环，深入理解 Agent 底层原理</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/8/4
 * @see com.git.hui.springai.app.react.simple.SimpleReActAgent
 * @see com.git.hui.springai.app.react.stream.StreamReActAgent
 */
@SpringBootApplication
public class A06Application {
    public static void main(String[] args) {
        SpringApplication.run(A06Application.class, args);
    }
}