package com.git.hui.offer;

import com.git.hui.offer.config.FilterConfig;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;

/**
 * MCP Server 请求鉴权过滤器
 * <p>
 * 本过滤器对 MCP 协议的核心端点（/sse 和 /mcp/messages）进行访问控制，
 * 支持两种标准的 HTTP 鉴权方式：
 * <ul>
 *   <li><b>Bearer Token 认证</b>：请求头格式为 {@code Authorization: Bearer <token>}，
 *       适用于 API Key / 访问令牌场景，常用于服务间调用</li>
 *   <li><b>Basic Auth 认证</b>：请求头格式为 {@code Authorization: Basic <base64(username:password)>}，
 *       适用于用户名+密码场景，符合 RFC 7617 标准</li>
 * </ul>
 * <p>
 * 注意：本过滤器作为 Spring Bean 注册（参见 {@link FilterConfig}），
 * 因为 Spring AI MCP Server 的 SSE 传输层基于异步 Servlet 机制实现长连接，
 * 过滤器需开启异步支持（asyncSupported = true），否则会阻塞 SSE 流式响应，
 * 导致客户端无法正常建立或维持 SSE 连接。
 *
 * @author YiHui
 * @date 2025/7/28
 * @see FilterConfig 过滤器注册配置，设置 asyncSupported 等参数
 * @see S08Application 启动类
 */
@Component
public class ReqFilter implements Filter {

    /**
     * Bearer Token 认证使用的预共享令牌（PSK）
     * <p>
     * 客户端请求头示例：{@code Authorization: Bearer yihuihui-blog}
     * 通过 {@code mcp.auth.token} 配置项注入，生产环境建议通过配置文件或密钥管理服务动态注入，避免硬编码
     */
    @Value("${mcp.auth.token}")
    private String token;

    /**
     * Basic Auth 认证使用的用户名
     */
    public static final String USER = "yihui";

    /**
     * Basic Auth 认证使用的密码
     * <p>
     * 生产环境应使用加密存储（如 Vault / Jasypt）而非明文
     */
    public static final String PWD = "12345678";

    /**
     * 过滤器核心逻辑：拦截请求，记录日志，并对 MCP 端点进行鉴权
     * <p>
     * 处理流程：
     * <ol>
     *   <li>将 {@link ServletRequest} 强转为 {@link HttpServletRequest}，提取 URL 和查询参数并打印请求日志</li>
     *   <li>判断请求路径是否为 MCP 核心端点（/sse 或 /mcp/messages）</li>
     *   <li>若是，则从 {@code Authorization} 请求头中提取鉴权信息</li>
     *   <li>根据前缀（Bearer / Basic）选择对应的认证策略进行校验</li>
     *   <li>校验通过则放行，否则抛出 {@link RuntimeException} 中断请求</li>
     * </ol>
     *
     * @param servletRequest  客户端请求对象
     * @param servletResponse 服务端响应对象
     * @param filterChain     过滤器链，调用 doFilter 将请求传递给下一个过滤器或目标 Servlet
     * @throws IOException      读写异常
     * @throws ServletException Servlet 处理异常
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        // 将通用 ServletRequest 强转为 HttpServletRequest，以便获取 HTTP 特有的方法（getHeader、getQueryString 等）
        HttpServletRequest req = (HttpServletRequest) servletRequest;

        // 获取请求路径（不含域名和查询参数），例如：/sse、/mcp/messages、/showTime
        String url = req.getRequestURI();

        // 获取 URL 中 ? 后面的查询参数字符串，例如：area=Asia/Tokyo；若无参数则为 null
        String params = req.getQueryString();

        // 打印请求日志，便于调试和排查问题
        System.out.println("请求 " + url + " params=" + params);

        // 从请求头中提取 Authorization 字段，用于后续鉴权判断
        // 若客户端未携带该头，则值为 null
        String auth = req.getHeader("Authorization");

        // 仅对 MCP 协议的两个核心端点进行鉴权，其他端点（如 /showTime）直接放行
        // /sse         ：SSE 长连接端点，客户端通过此端点建立事件流
        // /mcp/messages：MCP 消息端点，客户端通过此端点发送 JSON-RPC 请求
        if (url.equals("/sse") || url.equals("/mcp/messages")) {

            // 若 Authorization 头为空，说明客户端未携带任何认证信息，直接拒绝
            if (auth == null) {
                throw new RuntimeException("认证头格式错误");
            }

            if (auth.startsWith("Bearer ")) {
                // ── Bearer Token 认证 ──────────────────────────────────────────
                // 标准格式：Authorization: Bearer <token>
                // "Bearer " 占 7 个字符，substring(7) 截取实际令牌值
                String clientToken = auth.substring(7);

                // 将客户端传入的 token 与配置文件中的令牌进行比对
                // 不匹配则抛出异常，中断本次请求
                if (!token.equals(clientToken)) {
                    throw new RuntimeException("token error");
                }
                System.out.println("token鉴权通过!");

            } else if (auth.startsWith("Basic ")) {
                // ── Basic Auth 认证 ────────────────────────────────────────────
                // 标准格式（RFC 7617）：Authorization: Basic <Base64(username:password)>
                // 示例：Authorization: Basic eWlodWk6MTIzNDU2Nzg=
                //        解码后为：yihui:12345678

                // "Basic " 占 6 个字符，substring(6) 截取 Base64 编码的凭据字符串
                String encodedCredentials = auth.substring(6);

                // 对 Base64 字符串进行解码，还原为明文的 "username:password"
                String decodedCredentials = new String(Base64.getDecoder().decode(encodedCredentials));

                // 以第一个冒号（:）为分隔符，将凭据拆分为用户名和密码
                // split(":", 2) 限制最多分割为 2 段，防止密码中包含冒号时被错误截断
                String[] credentials = decodedCredentials.split(":", 2);
                String username = credentials[0];
                String password = credentials[1];

                // 将客户端传入的用户名/密码与预设值逐一比对
                // 任一不匹配则拒绝访问
                if (!USER.equals(username) || !PWD.equals(password)) {
                    throw new RuntimeException("用户名密码错误");
                }
                System.out.println("basic auth 鉴权通过!");
            }
            // 注：若 Authorization 头既不以 Bearer 也不以 Basic 开头，
            // 则不进入任何分支，请求将被直接放行（可根据需求改为拒绝）
        }

        // 鉴权通过后，将请求传递给过滤器链中的下一个节点（可能是另一个 Filter 或目标 Servlet）
        // 若不调用此方法，请求将在此处终止，客户端不会收到任何响应
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
