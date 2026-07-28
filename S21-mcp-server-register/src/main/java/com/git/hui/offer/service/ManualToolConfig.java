package com.git.hui.offer.service;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * 方式三：SyncToolSpecification 完全手动注册（MCP Java SDK 方式）。
 *
 * <h3>原理</h3>
 * <p>直接使用 MCP Java SDK 的底层 API，手动构建 {@link McpServerFeatures.SyncToolSpecification}
 * 对象，并通过 {@link McpSyncServer#addTool} 方法将其注册到 MCP Server。
 * 这种方式完全绕过了 Spring AI 的注解扫描和自动配置机制，
 * 提供了最大程度的灵活性和控制力。</p>
 *
 * <h3>使用步骤</h3>
 * <ol>
 *   <li>手动构建 {@link McpSchema.Tool} 对象，定义工具名称、描述和 JSON Schema 输入参数</li>
 *   <li>编写工具执行逻辑（{@code BiFunction} 处理器），接收请求并返回结果</li>
 *   <li>将 Tool 定义和处理器组装为 {@code SyncToolSpecification}</li>
 *   <li>通过 {@link McpSyncServer#addTool} 注册到 MCP Server</li>
 * </ol>
 *
 * <h3>优点</h3>
 * <ul>
 *   <li>完全控制工具的定义和执行逻辑，不依赖任何注解</li>
 *   <li>支持动态注册/注销工具（运行时增删工具）</li>
 *   <li>适合从非 Spring AI 项目迁移或集成第三方工具库</li>
 *   <li>可以精确控制 JSON Schema 的每个字段</li>
 * </ul>
 *
 * <h3>缺点</h3>
 * <ul>
 *   <li>代码量大，需要手动编写 JSON Schema</li>
 *   <li>需要了解 MCP Java SDK 底层 API</li>
 *   <li>无法利用注解带来的自动扫描和类型转换便利</li>
 * </ul>
 *
 * <h3>适用场景</h3>
 * <ul>
 *   <li>需要在运行时动态注册/注销工具</li>
 *   <li>工具逻辑来自外部系统，无法使用注解标记</li>
 *   <li>需要精确控制 MCP 协议的底层行为</li>
 * </ul>
 *
 * @author YiHui
 * @date 2026/7/8
 */
@Configuration
public class ManualToolConfig {

    /**
     * 手动构建并注册一个天气查询工具到 MCP Server。
     *
     * <p>本方法演示了完整的底层注册流程：
     * <ol>
     *   <li>定义 JSON Schema 描述工具的输入参数结构</li>
     *   <li>创建 {@link McpSchema.Tool} 对象封装工具元信息</li>
     *   <li>编写 {@code BiFunction} 处理器实现工具逻辑</li>
     *   <li>组装为 {@code SyncToolSpecification} 并注册到 MCP Server</li>
     * </ol>
     *
     * @param mcpSyncServer MCP 同步服务器实例，由 Spring Boot 自动配置注入
     * @return 注册的工具规范对象
     */
    @Bean
    public McpServerFeatures.SyncToolSpecification weatherManualTool(McpSyncServer mcpSyncServer) {

        // 第一步：定义输入参数的 JSON Schema
        // 使用 McpSchema.JsonSchema 构建符合 JSON Schema 规范的结构，描述工具需要的参数
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",                                          // type
                Map.of(
                        "city", Map.of(
                                "type", "string",
                                "description", "需要查询天气的城市名称，如 北京、上海、Tokyo"
                        )
                ),                                                 // properties
                List.of("city"),                                   // required
                null,                                              // additionalProperties
                null,                                              // $defs
                null                                               // definitions
        );

        // 第二步：创建 Tool 定义对象，包含工具名称、描述和输入参数 Schema
        McpSchema.Tool toolDefinition = new McpSchema.Tool(
                "manual_weather_query",           // 工具名称（MCP Client 通过此名称调用）
                null,                             // title（可选的人类可读标题）
                "根据城市名称查询该城市的当前天气信息（手动注册方式）",  // 工具描述，AI 模型据此判断何时调用
                inputSchema,                      // 输入参数的 JSON Schema
                null,                             // outputSchema
                null,                             // annotations
                null                              // _meta
        );

        // 第三步：创建工具执行逻辑（BiFunction 处理器）
        // 入参：McpSyncServerExchange（服务器交换上下文）+ Map<String,Object>（工具调用参数）
        // 出参：CallToolResult（工具调用结果）
        McpServerFeatures.SyncToolSpecification toolSpec = new McpServerFeatures.SyncToolSpecification(
                toolDefinition,
                (exchange, args) -> {
                    // 从参数 Map 中提取城市名称
                    String city = (String) args.getOrDefault("city", "未知城市");

                    System.out.println("[ManualTool] 查询天气: " + city);

                    // 模拟天气数据返回（实际项目中应对接真实天气 API）
                    String weatherResult = city + "：晴，25°C，湿度 60%，东南风3级";

                    // 将结果封装为 MCP 协议的文本响应
                    // 使用 CallToolResult(String, Boolean) 便捷构造器，内部自动包装为 TextContent
                    return new McpSchema.CallToolResult(
                            weatherResult,
                            false  // isError=false，表示执行成功
                    );
                }
        );

        // 第四步：将工具规范注册到 MCP Server
        mcpSyncServer.addTool(toolSpec);

        return toolSpec;
    }
}
