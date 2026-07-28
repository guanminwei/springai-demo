package com.git.hui.springai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot 启动类 - 语音转文字示例工程
 * <p>
 * 本类是 S19-audio-transaction 模块的入口，主要职责：
 * <ul>
 *     <li>启动 Spring Boot 应用</li>
 *     <li>配置 {@link RestClient.Builder} Bean，确保底层 HTTP 客户端支持 multipart 音频上传</li>
 * </ul>
 * <p>
 * <b>为什么需要自定义 RestClient.Builder？</b><br>
 * Spring AI 的语音转写 API 需要将音频文件以 multipart/form-data 格式上传。
 * 通过显式配置 {@link SimpleClientHttpRequestFactory} 和相关的
 * {@link org.springframework.http.converter.HttpMessageConverter HttpMessageConverter}，
 * 可以确保请求体被缓冲后以 Content-Length 方式发送。
 * <p>
 * <b>注意：</b>不能使用 {@code JdkClientHttpRequestFactory}，它对未知长度的 multipart 请求体
 * 采用 {@code Transfer-Encoding: chunked} 流式传输，而 SiliconFlow 网关会拒绝 chunked 请求，
 * 直接返回 HTTP 400 且无响应体（No response body available）。
 *
 * @author YiHui
 * @date 2026/2/25
 */
@SpringBootApplication
public class S19Application {

    /**
     * 应用程序入口
     * <p>
     * 启动 Spring Boot 应用，并打印测试访问地址。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(S19Application.class, args);
        System.out.println("启动成功，前端测试访问地址： http://localhost:8080/translateAudio");
    }


    /**
     * 配置 RestClient.Builder Bean
     * <p>
     * 自定义 RestClient 的构建器，确保：
     * <ol>
     *     <li>使用 {@link SimpleClientHttpRequestFactory} 作为底层 HTTP 客户端，
     *         它默认缓冲请求体并以 Content-Length 方式发送，避免 chunked 传输编码</li>
     *     <li>添加 {@link FormHttpMessageConverter} 处理表单和 multipart 数据</li>
     *     <li>添加 {@link ResourceHttpMessageConverter} 处理资源文件（如音频文件）</li>
     *     <li>添加 {@link StringHttpMessageConverter} 处理字符串数据</li>
     * </ol>
     * <p>
     * 这个配置是语音转写功能正常工作的关键：音频文件需以 multipart 格式上传，
     * 且 SiliconFlow 网关不接受 chunked 编码请求，必须缓冲后以 Content-Length 发送。
     *
     * @return 配置好的 RestClient.Builder 实例
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                // 使用 SimpleClientHttpRequestFactory（基于 HttpURLConnection），默认缓冲请求体并以 Content-Length 发送。
                // 注意：不能换成 JdkClientHttpRequestFactory，它对 multipart 采用 chunked 流式传输，
                // 而 SiliconFlow 网关拒绝 chunked 请求（返回 HTTP 400 且无响应体），导致音频上传失败。
                .requestFactory(new SimpleClientHttpRequestFactory())
                .messageConverters(converters -> {
                    converters.add(new FormHttpMessageConverter());
                    converters.add(new ResourceHttpMessageConverter());
                    converters.add(new StringHttpMessageConverter());
                });
    }
}