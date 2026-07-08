package com.git.hui.offer.service;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 方式二：ToolCallbackProvider 手动注册（配置类桥接）。
 *
 * <h3>原理</h3>
 * <p>通过 {@link MethodToolCallbackProvider} 扫描带有 {@code @Tool} 注解的 Spring Bean，
 * 将其中的方法注册为 {@link ToolCallbackProvider} Bean。
 * Spring AI MCP Server Boot Starter 的自动配置会自动拾取容器中所有的
 * {@link ToolCallbackProvider} Bean，将其中的工具回调转换为 MCP 协议的工具规范并注册。</p>
 *
 * <h3>使用步骤</h3>
 * <ol>
 *   <li>工具类使用 {@code @Tool} + {@code @ToolParam} 注解标记方法和参数</li>
 *   <li>工具类注册为 Spring Bean（{@code @Service} / {@code @Component}）</li>
 *   <li>编写配置类，通过 {@code MethodToolCallbackProvider.builder().toolObjects(...)} 扫描工具对象</li>
 *   <li>将返回的 {@link ToolCallbackProvider} 声明为 Bean</li>
 * </ol>
 *
 * <h3>优点</h3>
 * <ul>
 *   <li>{@code @Tool} 是 Spring AI 通用注解，同一工具方法既可用于 MCP Server，
 *       也可用于 ChatClient / ChatModel 的本地 Function Calling</li>
 *   <li>可以精确控制哪些工具对象注册到 MCP Server</li>
 *   <li>支持一次注册多个工具对象（可变参数）</li>
 * </ul>
 *
 * <h3>适用场景</h3>
 * <ul>
 *   <li>需要同时支持 MCP Server 和本地 Function Calling 的混合场景</li>
 *   <li>从已有的 {@code @Tool} 工具迁移到 MCP Server</li>
 *   <li>Spring AI 1.1.0 之前的版本（无 {@code @McpTool} 注解）</li>
 * </ul>
 *
 * @author YiHui
 * @date 2026/7/8
 * @see TimeService 使用 @Tool 注解的工具服务
 */
@Configuration
public class ToolCallbackConfig {

    /**
     * 将 TimeService 中的 @Tool 方法注册为 MCP Server 工具。
     *
     * <p>{@code toolObjects()} 支持可变参数，可同时传入多个工具服务对象，
     * 如 {@code .toolObjects(timeService, weatherService, otherService)}。</p>
     *
     * @param timeService 时间查询工具服务，由 Spring 容器自动注入
     * @return ToolCallbackProvider Bean，MCP Server 自动配置会拾取其中所有工具回调
     */
    @Bean
    public ToolCallbackProvider timeToolProvider(TimeService timeService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(timeService)  // 传入包含 @Tool 方法的工具对象
                .build();
    }
}
