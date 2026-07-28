package com.git.hui.springai.advance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A05 模块启动类 - Langgraph4j 简单 Agent 工作流示例
 * <p>
 * 本模块演示如何使用 Langgraph4j 构建自定义的 Agent 工作流，包含：
 * <ul>
 *     <li>基于 AgentExecutor 的工具调用 Agent（自动决策是否调用工具）</li>
 *     <li>自定义 StateGraph 实现条件路由（根据天气推荐户外/室内活动）</li>
 *     <li>Spring AI ChatClient 与 Langgraph4j 的集成使用</li>
 * </ul>
 * <p>
 * 核心组件：
 * <ul>
 *     <li>{@link com.git.hui.springai.advance.agents.WeatherRecommendAgent} - 自定义旅游推荐 Agent（条件路由图）</li>
 *     <li>{@link com.git.hui.springai.advance.times.TimeWeatherTools} - 时间和天气查询工具</li>
 *     <li>{@link com.git.hui.springai.advance.mvc.ChatController} - 提供多种调用方式的 REST 接口</li>
 * </ul>
 * <p>
 * 与 A04 模块的区别：
 * <ul>
 *     <li>A04：使用 AgentExecutor + MemorySaver 实现带记忆的通用 Agent</li>
 *     <li>A05（本模块）：自定义 StateGraph 节点和条件边，实现特定业务逻辑的 Agent</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/8/4
 * @see com.git.hui.springai.advance.agents.WeatherRecommendAgent
 * @see com.git.hui.springai.advance.mvc.ChatController
 */
@SpringBootApplication
public class A05Application {
    public static void main(String[] args) {
        SpringApplication.run(A05Application.class, args);
    }
}