package com.git.hui.offer.stdio;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * STDIO 模式下的工具服务示例。
 *
 * <p>STDIO 模式下的工具注册方式与 WebMVC 模式 <b>完全相同</b>，
 * 三种注册方式（@McpTool / ToolCallbackProvider / SyncToolSpecification）均可使用。
 * 区别仅在于传输层：STDIO 通过 stdin/stdout 通信，而非 HTTP SSE。</p>
 *
 * <h3>STDIO 模式工具注册配置要点</h3>
 * <ol>
 *   <li><b>依赖变更</b>：使用 {@code spring-ai-starter-mcp-server}（非 webmvc 版本）</li>
 *   <li><b>配置变更</b>：设置 {@code spring.ai.mcp.server.stdio=true}</li>
 *   <li><b>工具注册代码</b>：与 WebMVC 模式完全一致，无需任何修改</li>
 * </ol>
 *
 * <h3>STDIO 模式 Maven 依赖</h3>
 * <pre>{@code
 * <!-- STDIO 模式：使用 spring-ai-starter-mcp-server（不带 -webmvc 后缀） -->
 * <dependency>
 *     <groupId>org.springframework.ai</groupId>
 *     <artifactId>spring-ai-starter-mcp-server</artifactId>
 * </dependency>
 * }</pre>
 *
 * <h3>STDIO 模式 application.yml 配置</h3>
 * <pre>{@code
 * spring:
 *   ai:
 *     mcp:
 *       server:
 *         name: my-stdio-server
 *         version: 1.0.0
 *         type: SYNC
 *         stdio: true  # 启用 STDIO 传输模式
 * }</pre>
 *
 * <h3>与 WebMVC 模式的依赖对比</h3>
 * <pre>
 * | 模式          | Maven 依赖                              | 关键配置                    |
 * |--------------|----------------------------------------|---------------------------|
 * | STDIO        | spring-ai-starter-mcp-server           | stdio: true              |
 * | WebMVC SSE   | spring-ai-starter-mcp-server-webmvc    | sse-endpoint: /sse       |
 * | WebFlux SSE  | spring-ai-starter-mcp-server-webflux   | sse-endpoint: /sse       |
 * </pre>
 *
 * @author YiHui
 * @date 2026/7/8
 */
@Component
public class StdioEchoTool {

    /**
     * 回声工具 —— 简单演示 STDIO 模式下的工具调用。
     *
     * <p>在 STDIO 模式下，此工具通过 stdin/stdout 协议被 MCP Client 发现和调用，
     * 而非通过 HTTP SSE 端点。工具的定义和注册方式与 WebMVC 模式完全一致。</p>
     *
     * @param message 用户输入的消息
     * @return 回声结果
     */
    @McpTool(name = "stdio_echo", description = "回声工具，将输入的消息原样返回，用于验证 STDIO 模式是否正常工作")
    public String echo(
            @McpToolParam(description = "需要回声返回的消息内容", required = true) String message) {
        System.out.println("[STDIO-Echo] 收到消息: " + message);
        return "STDIO Echo: " + message;
    }

    /**
     * 字符串长度计算工具。
     *
     * @param text 输入文本
     * @return 文本长度信息
     */
    @McpTool(name = "stdio_string_length", description = "计算给定字符串的字符数和字节数")
    public String stringLength(
            @McpToolParam(description = "需要计算长度的字符串", required = true) String text) {
        int charCount = text.length();
        int byteCount = text.getBytes().length;
        System.out.println("[STDIO-Length] 字符数: " + charCount + ", 字节数: " + byteCount);
        return String.format("字符数: %d, 字节数: %d", charCount, byteCount);
    }
}
