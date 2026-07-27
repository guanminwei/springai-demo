package com.git.hui.ai.app.mvc;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;


/**
 * 流式对话控制器 —— 演示 Spring AI 中多种异步流式调用 LLM 的方式
 *
 * <p>本控制器围绕「流式（Stream）」这一核心主题，提供了 5 种典型的调用姿势：</p>
 * <ol>
 *   <li><b>chatV1</b>：ChatModel + SSE，直接返回 {@code Flux<ChatResponse>}，由框架自动以 text/event-stream 推送</li>
 *   <li><b>chatV2</b>：ChatModel 流式调用 + 阻塞收集，将多段响应拼接为完整字符串一次性返回</li>
 *   <li><b>chatV3</b>：ChatClient 流式调用 + SSE，返回 {@code Flux<String>} 纯文本片段</li>
 *   <li><b>chatV4</b>：ChatClient 流式调用 + reduce 聚合，阻塞等待全部片段后返回完整文本</li>
 *   <li><b>chatV5</b>：ChatClient 流式调用 + 手动 SseEmitter，适用于需要精细控制 SSE 生命周期的场景</li>
 * </ol>
 *
 * <h3>核心技术点</h3>
 * <ul>
 *   <li>{@link ChatModel#stream(Prompt)} —— 底层模型流式接口，返回 Reactor {@code Flux<ChatResponse>}</li>
 *   <li>{@link ChatClient} —— Spring AI 高级客户端封装，提供流式/非流式统一 API</li>
 *   <li>{@code MediaType.TEXT_EVENT_STREAM_VALUE} —— 声明响应为 SSE 格式，浏览器/EventSource 可逐条接收</li>
 *   <li>{@link SseEmitter} —— Spring MVC 原生 SSE 支持，适合需要手动管理连接超时、异常回调的场景</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>
 *   GET /chat?msg=你好         → SSE 流式返回 ChatResponse JSON
 *   GET /chatV2?msg=你好       → 阻塞等待，返回完整文本
 *   GET /chatV3?msg=你好       → SSE 流式返回纯文本片段
 *   GET /chatV4?msg=你好       → 阻塞等待，返回完整文本
 *   GET /chatV5?msg=你好       → SSE 流式返回（SseEmitter 方式）
 * </pre>
 *
 * @author YiHui
 * @date 2025/12/11
 * @see ChatModel#stream(Prompt)
 * @see ChatClient
 */
@RestController
public class ChatController {

    /**
     * 底层聊天模型 —— 由 Spring AI 自动装配（当前配置为智谱 GLM-4.7-Flash）
     * <p>提供 {@code call()} 同步调用和 {@code stream()} 异步流式调用两种模式</p>
     */
    private final ChatModel chatModel;

    /**
     * 高级聊天客户端 —— 基于 ChatModel 构建的 Fluent API 封装
     * <p>支持 prompt 模板、Advisor 增强、流式/非流式统一调用等能力</p>
     */
    private final ChatClient chatClient;

    /**
     * 构造器注入
     *
     * @param chatModel Spring 容器自动注入的聊天模型实例（由 spring-ai-zhipuai starter 提供）
     */
    public ChatController(ChatModel chatModel) {
        this.chatModel = chatModel;
        // 基于 chatModel 构建 ChatClient，后续可使用 .defaultAdvisors() 等方法扩展
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /**
     * 【方式一】ChatModel 原生流式调用 + SSE 推送
     *
     * <p>直接调用 {@link ChatModel#stream(Prompt)}，返回 {@code Flux<ChatResponse>}。
     * 配合 {@code produces = TEXT_EVENT_STREAM_VALUE}，Spring WebFlux/MVC 会将每个
     * ChatResponse 元素序列化为一条 SSE event 推送给客户端。</p>
     *
     * <h4>响应格式示例</h4>
     * <pre>
     * data: {"results":[{"output":{"content":"你"},...}]}
     * data: {"results":[{"output":{"content":"好"},...}]}
     * </pre>
     *
     * <h4>适用场景</h4>
     * <p>需要获取完整的 ChatResponse 元数据（token 用量、finish_reason 等）时使用</p>
     *
     * @param msg 用户输入的对话消息
     * @return 流式 ChatResponse 序列，每个元素包含一个文本片段及元数据
     */
    @GetMapping(path = "chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> chatV1(String msg) {
        return this.chatModel.stream(new Prompt(msg));
    }

    /**
     * 【方式二】ChatModel 流式调用 + 阻塞聚合为完整文本
     *
     * <p>虽然底层使用 {@code stream()} 获取流式响应，但通过 {@code collectList().block()}
     * 阻塞等待所有片段到达后，拼接为完整字符串一次性返回。</p>
     *
     * <h4>注意事项</h4>
     * <ul>
     *   <li>{@code block()} 会阻塞当前线程直到流完成，不适合高并发场景</li>
     *   <li>响应为普通 JSON/文本，非 SSE 格式</li>
     *   <li>适用于后台任务、批处理等不需要实时推送的场景</li>
     * </ul>
     *
     * @param msg 用户输入的对话消息
     * @return 拼接后的完整回复文本
     */
    @GetMapping(path = "chatV2")
    public String chatV2(String msg) {
        // 1. 发起流式调用，获取 Flux<ChatResponse>
        Flux<ChatResponse> res = chatModel.stream(new Prompt(msg));
        // 2. 阻塞收集所有响应片段到 List
        List<ChatResponse> responses = res.collectList().block();
        // 3. 遍历每个片段，提取文本并拼接
        StringBuilder content = new StringBuilder();
        for (ChatResponse response : responses) {
            content.append(response.getResult().getOutput().getText());
        }
        return content.toString();
    }

    /**
     * 【方式三】ChatClient 流式调用 + SSE 推送纯文本
     *
     * <p>使用 {@link ChatClient} 的 Fluent API：{@code prompt(msg).stream().content()}，
     * 直接获取 {@code Flux<String>} 纯文本片段流，无需手动解析 ChatResponse。</p>
     *
     * <h4>与 chatV1 的区别</h4>
     * <ul>
     *   <li>chatV1 返回完整 ChatResponse（含元数据），本方法仅返回文本内容</li>
     *   <li>ChatClient 封装度更高，后续可链式追加 Advisor、SystemPrompt 等</li>
     * </ul>
     *
     * <h4>响应格式示例</h4>
     * <pre>
     * data: 你
     * data: 好
     * data: ！
     * </pre>
     *
     * @param msg 用户输入的对话消息
     * @return 流式纯文本片段序列，以 SSE 格式逐条推送
     */
    @GetMapping(path = "chatV3", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatV3(String msg) {
        return chatClient.prompt(msg).stream().content();
    }

    /**
     * 【方式四】ChatClient 流式调用 + reduce 聚合为完整文本
     *
     * <p>与 chatV2 类似，但使用 ChatClient API 和 Reactor 的 {@code reduce} 操作符
     * 将所有文本片段聚合为一个完整字符串。</p>
     *
     * <h4>实现细节</h4>
     * <ul>
     *   <li>{@code reduce("", (a, b) -> a + b)} —— 以空串为初始值，逐段拼接</li>
     *   <li>{@code .block()} —— 阻塞等待聚合完成</li>
     *   <li>注释中的 {@code collect(StringBuilder::new, ...)} 是更高效的替代方案（避免字符串频繁创建）</li>
     * </ul>
     *
     * @param msg 用户输入的对话消息
     * @return 聚合后的完整回复文本
     */
    @GetMapping(path = "chatV4")
    public String chatV4(String msg) {
        // 获取纯文本片段流
        Flux<String> res = chatClient.prompt(msg).stream().content();
        // 使用 reduce 将所有片段拼接为完整字符串（阻塞等待）
        String content = res.reduce("", (a, b) -> a + b).block();
        // 替代方案：使用 StringBuilder 收集，性能更优
        // res.collect(StringBuilder::new, StringBuilder::append).block().toString();
        return content;
    }

    /**
     * 【方式五】ChatClient 流式调用 + 手动 SseEmitter 控制
     *
     * <p>使用 Spring MVC 原生的 {@link SseEmitter} 手动管理 SSE 连接生命周期，
     * 适用于以下场景：</p>
     * <ul>
     *   <li>需要设置自定义超时时间（默认 30s）</li>
     *   <li>需要注册 onCompletion / onTimeout / onError 回调</li>
     *   <li>需要在流结束后发送额外的自定义事件（如 [DONE] 标记）</li>
     *   <li>与不支持 WebFlux 的传统 Spring MVC 项目集成</li>
     * </ul>
     *
     * <h4>工作流程</h4>
     * <ol>
     *   <li>创建 SseEmitter 实例（可指定超时毫秒数）</li>
     *   <li>订阅 Flux 文本流，每收到一个片段调用 {@code sseEmitter.send()} 推送</li>
     *   <li>流完成时调用 {@code sseEmitter.complete()} 关闭连接</li>
     * </ol>
     *
     * <h4>注意事项</h4>
     * <ul>
     *   <li>若客户端提前断开，{@code send()} 会抛出 IOException，当前实现直接包装为 RuntimeException</li>
     *   <li>生产环境建议增加 {@code sseEmitter.onTimeout()} 和 {@code sseEmitter.onError()} 处理</li>
     * </ul>
     *
     * @param msg 用户输入的对话消息
     * @return SseEmitter 实例，Spring MVC 会保持连接直到 complete() 被调用
     */
    @GetMapping(path = "chatV5", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatV5(String msg) {
        // 创建 SSE 发射器，默认超时 30 秒；可传入参数如 new SseEmitter(60_000L) 自定义
        SseEmitter sseEmitter = new SseEmitter();
        // 获取 ChatClient 流式纯文本响应
        Flux<String> res = chatClient.prompt(msg).stream().content();
        // 订阅流：每收到一个文本片段 → 通过 SSE 推送；流结束 → 关闭连接
        res.doOnComplete(sseEmitter::complete)
                .subscribe(txt -> {
                    try {
                        sseEmitter.send(txt);
                    } catch (IOException e) {
                        // 客户端断开连接时可能触发此异常
                        throw new RuntimeException(e);
                    }
                });
        return sseEmitter;
    }
}
