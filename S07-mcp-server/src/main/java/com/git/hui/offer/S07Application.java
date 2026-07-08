package com.git.hui.offer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * S07-mcp-server 模块启动类 —— 基于 Spring AI MCP Server 协议的工具服务。
 *
 * <p>本模块演示如何使用 Spring AI 的 MCP（Model Context Protocol）Server 能力，
 * 将本地工具方法以标准化协议形式对外暴露，供 MCP Client（如 AI 对话应用）远程发现和调用。</p>
 *
 * <h3>模块核心能力</h3>
 * <ul>
 *   <li>通过 {@code application.yml} 配置 MCP Server 的 SSE 端点、工具能力开关等参数</li>
 *   <li>通过 {@link com.git.hui.offer.service.ToolConfig} 注册工具回调，
 *       将 {@link com.git.hui.offer.service.DateService} 中的 {@code @Tool} 方法发布为 MCP 工具</li>
 *   <li>对外提供两个 SSE 端点：
 *       <ul>
 *         <li>{@code /sse} —— MCP Client 建立 SSE 长连接的入口</li>
 *         <li>{@code /mcp/messages} —— Client 发送工具调用请求的消息端点</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>启动后访问方式</h3>
 * <p>启动后，MCP Client 可通过 {@code http://localhost:{port}/sse} 建立连接，
 * 并通过 MCP 协议查询可用工具列表、发起工具调用。</p>
 *
 * @author YiHui
 * @date 2025/7/11
 * @see com.git.hui.offer.service.ToolConfig 工具注册配置
 * @see com.git.hui.offer.service.DateService 时区时间查询工具实现
 */
@SpringBootApplication
public class S07Application {

    /**
     * Spring Boot 应用入口，启动时会自动加载 MCP Server 相关自动配置，
     * 包括 SSE 端点注册、工具回调扫描等。
     *
     * @param args 命令行参数，可覆盖 application.yml 中的配置
     */
    public static void main(String[] args) {
        SpringApplication.run(S07Application.class, args);
    }
}