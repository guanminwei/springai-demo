package com.git.hui.offer.config;

import com.git.hui.offer.ReqFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 请求过滤器注册配置
 * <p>
 * 由于 {@link ReqFilter} 从 {@code @WebFilter} 改为 {@code @Component}（以支持 {@code @Value} 注入），
 * 需要通过 {@link FilterRegistrationBean} 手动注册过滤器，并显式开启 {@code asyncSupported = true}，
 * 确保与 Spring AI MCP Server 的 SSE 异步长连接机制兼容。
 * <p>
 * 若不开启异步支持，过滤器将阻塞 SSE 流式响应，导致客户端无法正常建立或维持 SSE 连接。
 *
 * @author YiHui
 * @date 2025/7/28
 * @see ReqFilter
 */
@Configuration
public class FilterConfig {

    /**
     * 注册 ReqFilter 并开启异步支持
     * <p>
     * 配置说明：
     * <ul>
     *   <li>{@code asyncSupported = true}：启用异步 Servlet 支持，兼容 MCP SSE 长连接</li>
     *   <li>{@code addUrlPatterns("/*")}：拦截所有请求路径，由 ReqFilter 内部判断是否需要鉴权</li>
     * </ul>
     *
     * @param reqFilter 由 Spring 容器注入的 ReqFilter 实例（已包含 @Value 注入的配置值）
     * @return FilterRegistrationBean 过滤器注册包装对象
     */
    @Bean
    public FilterRegistrationBean<ReqFilter> reqFilterRegistration(ReqFilter reqFilter) {
        FilterRegistrationBean<ReqFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(reqFilter);
        registration.addUrlPatterns("/*");
        registration.setAsyncSupported(true);
        registration.setOrder(1);
        return registration;
    }
}
