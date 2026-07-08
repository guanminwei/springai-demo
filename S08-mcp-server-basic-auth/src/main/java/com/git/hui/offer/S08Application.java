package com.git.hui.offer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * S08 模块启动类 —— 带 Basic Auth 鉴权的 MCP Server
 * <p>
 * 本模块在 S07（基础 MCP Server）的基础上增加了 HTTP 请求鉴权能力，
 * 通过 Servlet Filter 对 MCP 协议端点进行访问控制，支持 Bearer Token 和 Basic Auth 两种方式。
 * <p>
 * 关键注解说明：
 * <ul>
 *   <li>{@link SpringBootApplication}：组合注解，启用自动配置、组件扫描，标记为 Spring Boot 应用入口</li>
 * </ul>
 * <p>
 * 注意：{@link com.git.hui.offer.ReqFilter ReqFilter} 通过 {@code @Component} 注册为 Spring Bean，
 * 并由 {@link com.git.hui.offer.config.FilterConfig FilterConfig} 通过 {@code FilterRegistrationBean} 完成 Servlet 过滤器注册。
 *
 * @author YiHui
 * @date 2025/7/11
 * @see com.git.hui.offer.ReqFilter 请求鉴权过滤器
 * @see com.git.hui.offer.config.FilterConfig 过滤器注册配置
 */
@SpringBootApplication
public class S08Application {

    /**
     * Spring Boot 应用入口方法
     * <p>
     * 调用 {@link SpringApplication#run(Class, String...)} 启动嵌入式 Tomcat 容器，
     * 加载 Spring 上下文，初始化 MCP Server 及相关 Bean（工具注册、过滤器等）。
     * <p>
     * 过滤器 {@link ReqFilter} 通过 {@code @Component} + {@code FilterRegistrationBean} 注册，
     * 不再依赖 {@code @ServletComponentScan}。
     *
     * @param args 命令行参数，可覆盖 application.yml 中的配置
     */
    public static void main(String[] args) {
        SpringApplication.run(S08Application.class, args);
    }
}