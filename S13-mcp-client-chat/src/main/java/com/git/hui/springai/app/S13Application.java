package com.git.hui.springai.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * S13 - MCP Client Chat 模块启动类。
 *
 * <p>本模块演示如何在 Spring AI 应用中作为 <b>MCP Client</b> 接入外部 MCP Server，
 * 并将 MCP Server 提供的工具（Tools）自动注册为大模型可调用的函数工具，
 * 从而实现 AI 对话过程中对 MCP 工具的透明调用。</p>
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>通过 SSE（Server-Sent Events）方式连接远程 MCP Server（如 S07-mcp-server 提供的时区查询服务）</li>
 *   <li>通过 STDIO 进程通信方式连接本地 MCP Server（如基于 Selenium 的网页数据抓取工具）</li>
 *   <li>利用 {@link org.springframework.ai.tool.ToolCallbackProvider} 自动将 MCP 工具注册到 ChatClient</li>
 *   <li>结合 HTMX + Thymeleaf 实现无刷新的 Web 聊天界面</li>
 * </ul>
 *
 * <h3>前置依赖</h3>
 * <p>启动本模块前，需确保以下服务已运行：</p>
 * <ul>
 *   <li><b>S07-mcp-server</b>：提供时区时间查询 MCP 工具，监听在 {@code localhost:8080}</li>
 *   <li><b>Selenium MCP Server</b>：通过 npx 自动启动的本地 STDIO 模式 MCP 工具</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/8/4
 * @see com.git.hui.springai.app.mvc.ChatController 聊天控制器，演示 MCP 工具的调用方式
 */
@SpringBootApplication
public class S13Application {
    /**
     * 应用程序入口。
     *
     * @param args 命令行启动参数，可通过 {@code --spring.ai.zhipuai.api-key=xxx} 覆盖配置
     */
    public static void main(String[] args) {
        SpringApplication.run(S13Application.class, args);
    }
}