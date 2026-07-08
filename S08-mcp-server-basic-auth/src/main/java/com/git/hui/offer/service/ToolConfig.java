package com.git.hui.offer.service;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 工具注册配置类
 * <p>
 * 本配置类负责将 {@link DateService} 中标记了 {@link org.springframework.ai.tool.annotation.Tool @Tool} 注解的方法
 * 注册为 MCP Server 可暴露的工具回调。
 * <p>
 * 工作原理：
 * <ol>
 *   <li>{@link MethodToolCallbackProvider} 通过反射扫描目标对象（{@code dateService}）中所有 {@code @Tool} 方法</li>
 *   <li>为每个方法生成符合 MCP 协议的工具描述（包括工具名称、描述、参数 JSON Schema）</li>
 *   <li>将生成的 {@link ToolCallbackProvider} 注册为 Spring Bean，MCP Server 自动发现并暴露给客户端</li>
 * </ol>
 * <p>
 * 若需注册多个工具服务类，可在 {@code toolObjects()} 中传入多个对象，
 * 或创建多个 {@code @Bean} 方法分别注册。
 *
 * @author YiHui
 * @date 2025/7/27
 * @see DateService 包含实际工具方法的 Service 类
 */
@Configuration
public class ToolConfig {

    /**
     * 创建时区时间工具的回调提供者
     * <p>
     * 将 {@link DateService} 实例作为工具对象传入，
     * {@link MethodToolCallbackProvider} 会自动扫描其中所有 {@code @Tool} 注解方法，
     * 生成对应的 MCP 工具回调并注册到 MCP Server。
     *
     * @param dateService 时区时间服务实例，由 Spring 容器自动注入
     * @return 工具回调提供者，MCP Server 会自动发现并注册其中的工具
     */
    @Bean
    public ToolCallbackProvider dateProvider(DateService dateService) {
        return MethodToolCallbackProvider.builder().toolObjects(dateService).build();
    }
}
