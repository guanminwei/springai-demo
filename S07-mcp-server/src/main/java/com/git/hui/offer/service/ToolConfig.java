package com.git.hui.offer.service;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 工具注册配置类。
 *
 * <p>本类是 S07-mcp-server 模块的核心配置，负责将业务服务中通过 {@link org.springframework.ai.tool.annotation.Tool}
 * 注解标记的工具方法注册为 Spring AI 可识别的工具回调（ToolCallback），使其能够被 MCP Client
 * 或 AI 模型在对话过程中通过 Function Calling 机制自动发现和调用。</p>
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>{@link MethodToolCallbackProvider} 会扫描传入的工具对象（toolObjects），
 *       找到所有被 {@code @Tool} 注解标记的方法。</li>
 *   <li>对每个工具方法，提取方法签名、参数信息（{@link org.springframework.ai.tool.annotation.ToolParam}）
 *       以及 description 描述，生成符合 JSON Schema 的工具定义。</li>
 *   <li>将工具定义封装为 {@link org.springframework.ai.tool.ToolCallback}，
 *       通过 {@link ToolCallbackProvider} 暴露给 Spring AI 运行时。</li>
 *   <li>当 AI 模型决定调用某个工具时，Spring AI 框架会根据工具名称路由到对应方法执行，
 *       并将返回结果作为上下文反馈给模型。</li>
 * </ol>
 *
 * <h3>与 MCP 协议的关系</h3>
 * <p>MCP（Model Context Protocol）Server 通过 SSE/HTTP 端点对外暴露工具列表。
 * 本配置类注册的 {@link ToolCallbackProvider} Bean 会被 MCP Server 端点自动拾取，
 * 将工具定义以 MCP 协议格式发布，供远程 MCP Client 发现和调用。</p>
 *
 * @author YiHui
 * @date 2025/7/27
 * @see DateService 具体的工具方法实现（时区时间查询）
 * @see WeatherService 具体的工具方法实现（天气查询）
 * @see MethodToolCallbackProvider Spring AI 提供的基于方法反射的工具回调提供者
 */
@Configuration
public class ToolConfig {

    /**
     * 注册多个工具的回调提供者 Bean。
     *
     * <p>将 {@link DateService} 和 {@link WeatherService} 实例同时作为工具对象传入
     * {@link MethodToolCallbackProvider}，框架会自动扫描其中所有 {@code @Tool} 注解方法，
     * 生成对应的工具回调。返回的 {@link ToolCallbackProvider} 包含所有工具回调，
     * Spring AI 在交互时会将其作为可用工具列表提供给 AI 模型。</p>
     *
     * <p>{@code toolObjects()} 支持可变参数，可传入任意数量的工具服务对象。
     * 若后续新增更多工具服务（如数据库查询、翻译等），只需将其也加入参数列表即可。</p>
     *
     * @param dateService   日期时间工具服务，包含 {@code @Tool} 标记的方法，由 Spring 容器自动注入
     * @param weatherService 天气查询工具服务，包含 {@code @Tool} 标记的方法，由 Spring 容器自动注入
     * @return 封装了所有工具回调的 {@link ToolCallbackProvider}，
     *         供 MCP Server 端点或 ChatClient 使用
     */
    @Bean
    public ToolCallbackProvider dateProvider(DateService dateService, WeatherService weatherService) {
        // 使用 MethodToolCallbackProvider 构建器，将多个工具对象中所有 @Tool 方法
        // 一次性扫描并注册为工具回调，toolObjects() 支持传入多个对象（可变参数）
        return MethodToolCallbackProvider.builder()
                .toolObjects(dateService, weatherService)   // 注册多个工具对象
                .build();                                   // 构建 ToolCallbackProvider 实例
    }
}
