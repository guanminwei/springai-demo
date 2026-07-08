package com.git.hui.offer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * S21-mcp-server-register 模块启动类。
 *
 * <p>本模块演示 Spring AI MCP Server 工具注册的 <b>三种不同方式</b>，
 * 所有工具同时注册在同一个 MCP Server 中，MCP Client 连接后可统一发现与调用。</p>
 *
 * <h3>三种注册方式概览</h3>
 * <ol>
 *   <li><b>@McpTool 注解自动注册</b>（推荐）—— 零配置，Spring Boot 自动扫描</li>
 *   <li><b>ToolCallbackProvider 手动注册</b> —— 基于 @Tool 注解 + 配置类桥接</li>
 *   <li><b>SyncToolSpecification 完全手动注册</b> —— 直接操作 MCP Java SDK</li>
 * </ol>
 *
 * @author YiHui
 * @date 2026/7/8
 */
@SpringBootApplication
public class S21Application {

    public static void main(String[] args) {
        SpringApplication.run(S21Application.class, args);
    }
}
