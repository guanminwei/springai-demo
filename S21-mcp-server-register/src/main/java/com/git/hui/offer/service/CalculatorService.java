package com.git.hui.offer.service;

import io.modelcontextprotocol.server.annotation.McpTool;
import io.modelcontextprotocol.server.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 方式一：@McpTool 注解自动注册（推荐方式）。
 *
 * <h3>原理</h3>
 * <p>Spring AI MCP Server Boot Starter 内置了注解扫描器（annotation-scanner），
 * 启动时自动扫描所有 Spring Bean 中标记了 {@link McpTool @McpTool} 的方法，
 * 自动生成 JSON Schema、创建 {@code ToolSpecification}，并注册到 MCP Server。</p>
 *
 * <h3>使用步骤</h3>
 * <ol>
 *   <li>引入 {@code spring-ai-starter-mcp-server-webmvc} 依赖（已包含注解模块）</li>
 *   <li>将工具类注册为 Spring Bean（{@code @Component} / {@code @Service}）</li>
 *   <li>在方法上添加 {@code @McpTool} 注解，参数上添加 {@code @McpToolParam} 注解</li>
 *   <li>无需任何配置类，启动即自动注册</li>
 * </ol>
 *
 * <h3>优点</h3>
 * <ul>
 *   <li>零配置，代码最简洁，只需注解即可</li>
 *   <li>自动生成 JSON Schema，无需手动定义参数结构</li>
 *   <li>支持特殊参数注入（McpSyncServerExchange、McpTransportContext 等）</li>
 *   <li>同时支持 SYNC 和 ASYNC 两种服务器模式</li>
 * </ul>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>{@code @McpTool} 是 MCP 协议专用注解，仅在 MCP Server 场景下生效</li>
 *   <li>与 {@code @Tool} 注解不同，{@code @McpTool} 直接由 MCP Server 扫描，
 *       不经过 Spring AI 的 ToolCallbackProvider 体系</li>
 *   <li>需要 Spring AI 1.1.0+ 版本</li>
 * </ul>
 *
 * @author YiHui
 * @date 2026/7/8
 */
@Component
public class CalculatorService {

    /**
     * 加法计算工具。
     *
     * <p>MCP Client 调用时，AI 模型会根据 description 判断何时使用此工具，
     * 并自动从用户消息中提取参数 a 和 b 的值。</p>
     *
     * @param a 第一个加数
     * @param b 第二个加数
     * @return 两数之和
     */
    @McpTool(name = "calculator_add", description = "将两个数字相加，返回它们的和")
    public double add(
            @McpToolParam(description = "第一个数字", required = true) double a,
            @McpToolParam(description = "第二个数字", required = true) double b) {
        System.out.println("[McpTool] 计算加法: " + a + " + " + b);
        return a + b;
    }

    /**
     * 乘法计算工具。
     *
     * @param x 第一个因子
     * @param y 第二个因子
     * @return 两数之积
     */
    @McpTool(name = "calculator_multiply", description = "将两个数字相乘，返回它们的积")
    public double multiply(
            @McpToolParam(description = "第一个数字", required = true) double x,
            @McpToolParam(description = "第二个数字", required = true) double y) {
        System.out.println("[McpTool] 计算乘法: " + x + " * " + y);
        return x * y;
    }
}
