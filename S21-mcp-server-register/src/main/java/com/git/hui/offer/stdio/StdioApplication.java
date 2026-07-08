package com.git.hui.offer.stdio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * STDIO 模式 MCP Server 启动类。
 *
 * <h3>STDIO 模式 vs WebMVC SSE 模式</h3>
 * <table border="1">
 *   <tr><th>维度</th><th>STDIO 模式</th><th>WebMVC SSE 模式</th></tr>
 *   <tr><td>通信方式</td><td>标准输入/输出（stdin/stdout）</td><td>HTTP SSE 长连接</td></tr>
 *   <tr><td>依赖</td><td>spring-ai-starter-mcp-server</td><td>spring-ai-starter-mcp-server-webmvc</td></tr>
 *   <tr><td>配置</td><td>spring.ai.mcp.server.stdio=true</td><td>配置 SSE 端点路径</td></tr>
 *   <tr><td>Web 服务器</td><td>不需要</td><td>需要（Tomcat/Jetty）</td></tr>
 *   <tr><td>适用场景</td><td>进程内嵌入、本地工具</td><td>远程服务、多客户端</td></tr>
 *   <tr><td>客户端连接</td><td>同一进程内的 MCP Client</td><td>通过网络 HTTP 连接</td></tr>
 * </table>
 *
 * <h3>STDIO 模式特点</h3>
 * <ul>
 *   <li>MCP Server 嵌入在宿主应用中，通过 stdin/stdout 与 Client 通信</li>
 *   <li>无需启动 Web 服务器，资源占用更少</li>
 *   <li>适合本地 AI 应用（如 IDE 插件、桌面应用）内嵌使用</li>
 *   <li>工具注册方式与 WebMVC 模式完全相同（@McpTool / ToolCallbackProvider / SyncToolSpecification）</li>
 * </ul>
 *
 * <h3>启动方式</h3>
 * <p>STDIO 模式通常由 MCP Client 作为子进程启动，而非手动运行。例如：</p>
 * <pre>
 * // 在 MCP Client 的配置中指定 STDIO Server 的启动命令
 * spring.ai.mcp.client.stdio.connections.my-server.command=java
 * spring.ai.mcp.client.stdio.connections.my-server.args=-jar,mcp-server.jar
 * </pre>
 *
 * @author YiHui
 * @date 2026/7/8
 */
@SpringBootApplication
public class StdioApplication {

    public static void main(String[] args) {
        SpringApplication.run(StdioApplication.class, args);
    }
}
