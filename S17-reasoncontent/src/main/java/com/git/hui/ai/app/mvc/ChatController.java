package com.git.hui.ai.app.mvc;

import io.micrometer.common.util.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 推理大模型对话控制器 —— 演示如何接入支持"思考/推理"能力的大模型，并获取推理过程（reasoningContent）
 *
 * <p>核心功能：
 * <ul>
 *   <li>手动构建阿里百炼（DashScope）和智谱（GLM）两个 OpenAI 兼容风格的 ChatModel</li>
 *   <li>通过 extraBody 开启模型的 thinking/推理能力</li>
 *   <li>从流式/同步响应中提取 reasoningContent（思考过程）与正文内容</li>
 *   <li>提供 SSE 流式推送（阿里）和同步 JSON 返回（智谱）两种交互方式</li>
 * </ul>
 *
 * <p>接口一览：
 * <pre>
 *   GET /aliChatWithThinking?msg=xxx   → SSE 流式返回（含实时思考过程）
 *   GET /zhipuChat?msg=xxx             → 同步返回（流式收集后一次性 JSON）
 *   GET /zhipuChatV2?msg=xxx           → 同步返回（直接阻塞调用，更简洁）
 *   GET /zhipuChatStream?msg=xxx       → SSE 流式返回（含实时思考过程，生产推荐）
 * </pre>
 *
 * <p>API Key 获取优先级：启动命令参数 > JVM -D 参数 > 系统环境变量
 *
 * @author YiHui
 * @date 2025/8/26
 * @see org.springframework.ai.openai.OpenAiChatModel
 * @see org.springframework.ai.chat.model.ChatResponse
 */
@RestController
public class ChatController {
    /**
     * 阿里百炼（DashScope）聊天模型实例
     * <p>使用 OpenAI 兼容接口协议接入，模型为 qwen-plus-latest，
     * 通过 extraBody 中 enable_thinking=true 开启推理/思考能力
     */
    private final ChatModel dashModel;


    /**
     * 智谱 AI（GLM）聊天模型实例
     * <p>使用 OpenAI 兼容接口协议接入，模型为 glm-4.5-flash，
     * 该模型默认开启推理能力，可通过 extraBody 中 thinking.type=disabled 关闭
     */
    private final ChatModel zhipuModel;

    /**
     * 构造方法 —— 手动初始化两个推理大模型的 ChatModel 实例
     *
     * <p>构建流程：
     * <ol>
     *   <li>通过 OpenAiApi.builder() 配置 baseUrl、completionsPath、apiKey</li>
     *   <li>通过 OpenAiChatModel.builder() 绑定 API 并设置默认选项（模型名 + extraBody）</li>
     * </ol>
     *
     * <p>extraBody 说明：
     * <ul>
     *   <li>阿里百炼：{@code Map.of("enable_thinking", true)} → 开启思考链</li>
     *   <li>智谱 GLM：默认开启推理；关闭方式为 {@code Map.of("thinking", Map.of("type", "disabled"))}</li>
     * </ul>
     *
     * @param environment Spring 环境对象，用于读取 API Key 配置
     */
    public ChatController(Environment environment) {
        // ========== 1. 注册阿里百炼模型（DashScope OpenAI 兼容模式） ==========
        //completionsPath 是 OpenAiApi.builder() 的一个配置项，用于指定调用大模型 Chat Completions 接口的 URL 路径。
        // 最终拼接出的完整请求地址为：https://dashscope.aliyuncs.com/compatible-mode + /v1/chat/completions
        // 为什么需要它？因为不同的模型服务商虽然都兼容 OpenAI 协议，但各自的 API 路径不同：
        // 如果不设置，Spring AI 默认使用 OpenAI 官方的 /v1/chat/completions。对于阿里百炼来说恰好一致，所以第 87 行其实可以省略；
        // 但智谱的路径是 /api/paas/v4/chat/completions，就必须显式指定了。
        // 简言之：它告诉 Spring AI 向哪个具体路径发送聊天补全请求，是适配不同 OpenAI 兼容服务商的关键配置。
        OpenAiApi openAiApi = OpenAiApi.builder().apiKey(getApiKey(environment, "DASHBOARD_API_KEY"))
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .completionsPath("/v1/chat/completions")
                .build();
        dashModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen-plus-latest")
                        // 通过 extraBody 传递非标准参数，开启模型的推理/思考能力
                        .extraBody(Map.of("enable_thinking", true))
                        .build())
                .build();

        // ========== 2. 注册智谱 AI 模型（GLM OpenAI 兼容模式） ==========
        OpenAiApi zhipuApi = OpenAiApi.builder().apiKey(getApiKey(environment, "ZHIPUAI_API_KEY"))
                .baseUrl("https://open.bigmodel.cn")
                .completionsPath("/api/paas/v4/chat/completions")
                .build();
        zhipuModel = OpenAiChatModel.builder()
                .openAiApi(zhipuApi)
                .defaultOptions(OpenAiChatOptions.builder().model("glm-4.5-flash")
//                        智谱默认开启推理；如需关闭，取消下面注释：
//                        .extraBody(Map.of("thinking", Map.of("type", "disabled")))
                        .build())
                .build();

    }

    /**
     * 级联获取 API Key —— 按优先级依次尝试三种来源
     *
     * <p>查找顺序：
     * <ol>
     *   <li>Spring Environment（对应启动命令 --key=value 或 application.yml 配置）</li>
     *   <li>JVM 系统属性（对应 -Dkey=value）</li>
     *   <li>操作系统环境变量（对应 export KEY=value）</li>
     * </ol>
     *
     * @param environment Spring 环境对象
     * @param key         配置键名，如 "dash-api-key"、"zhipuai-api-key"
     * @return 找到的 API Key 值；若均未找到则返回 null
     */
    private String getApiKey(Environment environment, String key) {
        // 优先级1：通过 --dash-api-key=xxx 启动命令传参
        String val = environment.getProperty(key);
        if (StringUtils.isBlank(val)) {
            // 优先级2：通过 JVM 参数 -Ddash-api-key=xxx 传参
            val = System.getProperty(key);
            if (val == null) {
                // 优先级3：通过操作系统环境变量传参
                val = System.getenv(key);
            }
        }
        return val;
    }

    /**
     * 阿里百炼模型 —— SSE 流式对话（含推理过程实时输出）
     *
     * <p>实现原理：
     * <ol>
     *   <li>调用 dashModel.stream() 获取 Flux&lt;ChatResponse&gt; 流式响应</li>
     *   <li>每收到一个 chunk，从 metadata 中提取 reasoningContent（思考片段）</li>
     *   <li>同时提取正文 text 片段，拼接后通过 SseEmitter 实时推送给前端</li>
     *   <li>流结束时调用 sseEmitter.complete() 关闭连接</li>
     * </ol>
     *
     * <p>响应格式（SSE data）：{@code 思考:{累计思考内容}===>\n<br/>\n==>{累计正文内容}}
     *
     * @param msg 用户输入的消息文本
     * @return SseEmitter 服务端推送事件发射器，Content-Type 为 text/event-stream
     */
    @GetMapping(path = "aliChatWithThinking", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatV5(String msg) {
        // 创建 SSE 发射器（默认超时 30s，生产环境建议设置更长超时）
        SseEmitter sseEmitter = new SseEmitter();
        // 发起流式调用，获取逐 chunk 返回的 ChatResponse 流
        Flux<ChatResponse> res = dashModel.stream(new Prompt(new UserMessage(msg)));
        // 累计拼接思考过程与正文内容
        StringBuilder content = new StringBuilder();
        StringBuilder reason = new StringBuilder();
        res.doOnComplete(() -> {
                    // 流结束：关闭 SSE 连接，并在控制台打印完整的思考与结果
                    sseEmitter.complete();
                    System.out.println("思考过程:" + reason);
                    System.out.println("结果:" + content);
                })
                .subscribe(txt -> {
                    // 逐 chunk 处理：提取推理内容 + 正文内容
                    Generation generation = txt.getResult();

                    // 从 metadata 中获取 reasoningContent（模型思考链片段）
                    var r = generation.getOutput().getMetadata().get("reasoningContent");
                    if (r != null) {
                        reason.append(r);
                    }

                    // 获取正文回复片段
                    var t = generation.getOutput().getText();
                    if (t != null) {
                        content.append(t);
                    }
                    try {
                        // 实时推送当前累计的思考 + 正文给客户端
                        sseEmitter.send("思考:" + reason + "===>\n<br/>\n==>" + content);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, error -> {
                    // 错误处理：网络中断/服务端断连时优雅关闭 SSE 连接
                    System.err.println("[aliChatWithThinking] 流式调用异常: " + error.getMessage());
                    sseEmitter.completeWithError(error);
                });
        return sseEmitter;
    }

    /**
     * 智谱模型 —— 流式收集后同步返回（含推理过程 + Token 用量）
     *
     * <p>实现原理：
     * <ol>
     *   <li>通过 ChatClient（附带 SimpleLoggerAdvisor 日志增强）发起流式请求</li>
     *   <li>使用 doOnNext 逐 chunk 收集 reasoningContent 和正文</li>
     *   <li>调用 blockLast() 阻塞等待流结束，获取最后一个 ChatResponse（含 usage 元数据）</li>
     *   <li>将思考过程、正文结果、Token 消耗封装为 Map 一次性返回</li>
     * </ol>
     *
     * <p>注意：blockLast() 会阻塞当前线程，适合后端演示；生产环境建议使用流式接口
     *
     * @param msg 用户输入的消息文本
     * @return Map 包含三个键："思考过程"、"结果"、"token消耗"
     */
    @GetMapping(path = "zhipuChat")
    public Map zhipuChat(String msg) {
        // 构建 ChatClient，添加 SimpleLoggerAdvisor 用于 debug 级别日志输出
        ChatClient client = ChatClient.builder(zhipuModel).defaultAdvisors(new SimpleLoggerAdvisor()).build();
        // 发起流式调用，获取 ChatResponse 流
        Flux<ChatResponse> res = client.prompt(new Prompt(msg)).stream().chatResponse();
        StringBuilder content = new StringBuilder();
        StringBuilder reason = new StringBuilder();
        // 阻塞收集所有 chunk，blockLast() 返回最后一个响应（包含完整 usage 信息）
        ChatResponse response = res.doOnComplete(() -> {
            System.out.println("思考过程:" + reason);
            System.out.println("结果:" + content);
        }).doOnNext(txt -> {
            // 逐 chunk 提取推理内容与正文
            Generation generation = txt.getResult();
            var r = generation.getOutput().getMetadata().get("reasoningContent");
            if (r != null) {
                reason.append(r);
                System.out.println("思考:" + r);
            }
            var t = generation.getOutput().getText();
            if (t != null) {
                content.append(t);
                System.out.println("结果:" + t);
            }
        }).blockLast();
        // 从最后一个响应中获取 Token 使用统计（promptTokens + completionTokens）
        var usage = response.getMetadata().getUsage();
        // 封装为 JSON 返回
        return Map.of("思考过程", reason, "结果", content, "token消耗", usage);
    }

    /**
     * 智谱模型 —— SSE 流式对话（含推理过程实时输出，生产环境推荐）
     *
     * <p>实现原理：
     * <ol>
     *   <li>通过 ChatClient 发起流式请求，获取 Flux&lt;ChatResponse&gt; 逐 chunk 响应</li>
     *   <li>每收到一个 chunk，从 metadata 中提取 reasoningContent（思考片段）</li>
     *   <li>同时提取正文 text 片段，拼接后通过 ResponseBodyEmitter 实时推送给前端</li>
     *   <li>流结束时调用 emitter.complete() 关闭连接</li>
     * </ol>
     *
     * <p>与 zhipuChat 的区别：不阻塞线程，逐 chunk 实时推送，适合生产环境前端实时展示
     *
     * <p>与 aliChatWithThinking 的区别：
     * <ul>
     *   <li>使用 ResponseBodyEmitter（纯文本流）代替 SseEmitter（SSE 协议），前端无需 EventSource 解析</li>
     *   <li>输出分为"【思考过程】"和"【回答】"两个阶段，带标题头，可读性更好</li>
     *   <li>内置重试机制（Retry.backoff），应对偶发网络断连</li>
     *   <li>通过 AtomicBoolean 状态机防止超时/断连后继续写入导致 IllegalStateException</li>
     * </ul>
     *
     * <p>响应格式（纯文本流，逐段追加）：
     * <pre>
     *   【思考过程】
     *   {逐 chunk 输出的推理内容...}
     *
     *   【回答】
     *   {逐 chunk 输出的正文内容...}
     * </pre>
     *
     * <p>容错设计：
     * <ul>
     *   <li>超时时间 600s（10分钟），适应推理大模型较长的思考时间</li>
     *   <li>重试策略：最多 2 次，初始退避 1s，最大退避 5s，仅对网络瞬态异常生效</li>
     *   <li>重试前清空累计缓冲区和标题头标记，确保重新输出完整内容</li>
     * </ul>
     *
     * @param msg      用户输入的消息文本（作为 query param 传入）
     * @param response HttpServletResponse 对象，由 Spring MVC 自动注入，用于显式设置编码
     * @return ResponseBodyEmitter 流式响应发射器，Content-Type 为 text/plain;charset=UTF-8
     */
    @GetMapping(path = "zhipuChatStream", produces = "text/plain;charset=UTF-8")
    public ResponseBodyEmitter zhipuChatStream(String msg, jakarta.servlet.http.HttpServletResponse response) {
        // ==================== 第一阶段：响应编码设置 ====================
        // 显式设置 Content-Type 和字符编码为 UTF-8
        // 原因：ResponseBodyEmitter 异步写出时，若不显式指定，Servlet 容器可能使用默认 ISO-8859-1
        // 导致中文推理内容在浏览器端显示为乱码
        response.setContentType("text/plain;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");

        // ==================== 第二阶段：创建流式发射器与状态管理 ====================
        // 使用 ResponseBodyEmitter 代替 SseEmitter：
        //   - SseEmitter 输出遵循 SSE 协议（每条消息带 "data:" 前缀），前端需用 EventSource 接收
        //   - ResponseBodyEmitter 输出纯文本流，前端通过 fetch + ReadableStream 即可逐段读取，更轻量
        // 超时设为 600_000ms（10分钟）：推理大模型思考链可能很长，默认 30s 远远不够
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(600_000L);

        // completed 状态标记 —— 核心防护机制：
        // 当 emitter 因超时/客户端断连/正常结束而关闭后，后续 subscribe 回调中的 send() 会抛
        // IllegalStateException("ResponseBodyEmitter has already completed")
        // 通过此原子布尔量实现无锁状态机，确保只在 emitter 存活期间写入
        AtomicBoolean completed = new AtomicBoolean(false);
        // 注册三个生命周期回调，无论哪种方式结束都标记为已完成
        emitter.onCompletion(() -> completed.set(true));  // 正常完成（流结束调用 complete()）
        emitter.onTimeout(() -> completed.set(true));      // 超时（超过 600s 未结束）
        emitter.onError(e -> completed.set(true));         // 异常（客户端主动断开等）

        // ==================== 第三阶段：构建 ChatClient 并发起流式调用 ====================
        // 构建 ChatClient：
        //   - 基于 zhipuModel（构造方法中初始化的智谱 GLM OpenAI 兼容模型）
        //   - SimpleLoggerAdvisor：在 DEBUG 日志级别输出请求/响应原文，便于开发调试
        ChatClient client = ChatClient.builder(zhipuModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();

        // 累计缓冲区：分别拼接完整的思考过程和正文内容
        // 用途：1) 控制台最终打印完整结果  2) 重试时清空重来
        StringBuilder content = new StringBuilder();  // 正文回复累计
        StringBuilder reason = new StringBuilder();   // 推理/思考过程累计

        // 标题头输出标记 —— 实现"分阶段输出"效果：
        // 推理大模型的响应流通常先输出 reasoningContent（思考链），再输出 text（正文）
        // 通过 CAS 操作确保标题头只输出一次，后续 chunk 直接追加内容
        AtomicBoolean reasonHeaderSent = new AtomicBoolean(false);  // "【思考过程】"标题是否已输出
        AtomicBoolean answerHeaderSent = new AtomicBoolean(false);  // "【回答】"标题是否已输出

        // 发起流式调用：
        //   client.prompt(new Prompt(msg)) → 构建用户消息提示
        //   .stream()                      → 声明流式调用模式
        //   .chatResponse()                → 获取 Flux<ChatResponse>，每个元素是一个 chunk
        // 附带重试逻辑：应对智谱服务端偶发的 Connection reset（HTTP 连接被中途关闭）
        Flux<ChatResponse> res = client.prompt(new Prompt(msg)).stream().chatResponse()
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))  // 最多重试 2 次，初始退避 1 秒
                        .maxBackoff(Duration.ofSeconds(5))           // 退避上限 5 秒（指数退避：1s → 2s）
                        .filter(this::isRetryableException)          // 仅对网络瞬态异常重试，业务错误不重试
                        .doBeforeRetry(signal -> {
                            // 重试前重置所有状态，确保新一轮输出从头开始：
                            // 否则前端会看到重复/混乱的内容
                            content.setLength(0);          // 清空正文缓冲
                            reason.setLength(0);           // 清空思考缓冲
                            reasonHeaderSent.set(false);   // 重置思考标题标记
                            answerHeaderSent.set(false);   // 重置回答标题标记
                            System.out.println("[zhipuStream] 检测到可重试异常，第 " + (signal.totalRetries() + 1) + " 次重试: " + signal.failure().getMessage());
                        }));

        // ==================== 第四阶段：订阅流并逐 chunk 处理 ====================
        res.doOnComplete(() -> {
                    // 流正常结束回调：关闭 emitter，通知浏览器响应完毕
                    // 使用 CAS 防止与 onTimeout/onError 回调重复关闭
                    if (completed.compareAndSet(false, true)) {
                        emitter.complete();
                    }
                    // 控制台打印完整结果，便于后端日志排查
                    System.out.println("[zhipuStream] 思考过程:" + reason);
                    System.out.println("[zhipuStream] 结果:" + content);
                })
                .doOnError(e -> {
                    // 流异常回调（重试耗尽后仍失败，或遇到不可重试异常）：
                    // 区分日志级别 —— 网络异常为 warn 级（可恢复），业务异常为 error 级（需关注）
                    if (isRetryableException(e)) {
                        System.err.println("[zhipuStream] 网络异常（重试耗尽）: " + e.getMessage());
                    } else {
                        System.err.println("[zhipuStream] 不可重试异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    }
                    // CAS 关闭 emitter，将异常传播给 Servlet 容器（触发 HTTP 500 或连接关闭）
                    if (completed.compareAndSet(false, true)) {
                        emitter.completeWithError(e);
                    }
                })
                .subscribe(txt -> {
                    // 前置检查：若 emitter 已关闭（超时/客户端断开），直接跳过后续处理
                    // 避免向已关闭的 emitter 写入导致 IllegalStateException
                    if (completed.get()) {
                        return;
                    }

                    // 从当前 chunk 中提取 Generation（包含模型输出的元数据和文本）
                    Generation generation = txt.getResult();

                    // 提取推理/思考内容：
                    // 智谱 GLM 模型将思考链放在 output.metadata["reasoningContent"] 字段中
                    // 每个 chunk 包含一小段思考文本，逐步拼接即为完整思考过程
                    var r = generation.getOutput().getMetadata().get("reasoningContent");
                    if (r != null) {
                        reason.append(r);
                    }

                    // 提取正文回复内容：
                    // 当模型思考完毕后，开始输出正式回答，放在 output.text 字段中
                    var t = generation.getOutput().getText();
                    if (t != null) {
                        content.append(t);
                    }

                    try {
                        // ===== 输出阶段1：思考过程 =====
                        // 条件：当前 chunk 包含非空的推理内容
                        if (r != null && !r.toString().isEmpty()) {
                            // CAS 确保"【思考过程】"标题头只输出一次
                            // compareAndSet(false, true)：第一个包含推理内容的 chunk 触发标题输出
                            if (reasonHeaderSent.compareAndSet(false, true)) {
                                emitter.send("【思考过程】\n", new MediaType("text", "plain", StandardCharsets.UTF_8));
                            }
                            // 逐 chunk 追加输出推理文本（前端实时看到思考过程逐字出现）
                            emitter.send(r.toString(), new MediaType("text", "plain", StandardCharsets.UTF_8));
                        }

                        // ===== 输出阶段2：正式回答 =====
                        // 条件：当前 chunk 包含非空的正文内容
                        // 通常思考链输出完毕后，模型才开始输出正文（两阶段在时间上基本不重叠）
                        if (t != null && !t.isEmpty()) {
                            // CAS 确保"【回答】"标题头只输出一次，同时用换行与思考过程视觉分隔
                            if (answerHeaderSent.compareAndSet(false, true)) {
                                emitter.send("\n\n【回答】\n", new MediaType("text", "plain", StandardCharsets.UTF_8));
                            }
                            // 逐 chunk 追加输出正文文本
                            emitter.send(t, new MediaType("text", "plain", StandardCharsets.UTF_8));
                        }
                    } catch (IOException | IllegalStateException e) {
                        // IOException：客户端已断开连接（如用户关闭浏览器），写入失败
                        // IllegalStateException：emitter 已被其他线程关闭（超时/错误回调先触发）
                        // 两种情况都标记为已完成，后续 chunk 将在方法入口的 completed.get() 处被跳过
                        completed.set(true);
                    }
                });
        // 返回 emitter 给 Spring MVC 框架，框架会保持 HTTP 连接打开直到 emitter.complete() 被调用
        return emitter;
    }

    /**
     * 智谱模型 V2 —— 同步阻塞调用（最简写法）
     *
     * <p>与 zhipuChat 的区别：
     * <ul>
     *   <li>直接使用 ChatClient.call() 同步调用，无需手动收集流</li>
     *   <li>从单次 ChatResponse 中一次性提取 reasoningContent 和正文</li>
     *   <li>代码更简洁，适合不需要实时展示思考过程的场景</li>
     * </ul>
     *
     * <p>推理内容获取路径：{@code response.getResult().getOutput().getMetadata().get("reasoningContent")}
     *
     * @param msg 用户输入的消息文本
     * @return Map 包含三个键："思考过程"（可能为空串）、"结果"、"token消耗"
     */
    @GetMapping(path = "zhipuChatV2")
    public Map zhipuChatV2(String msg) {
        // 构建 ChatClient 并添加日志 Advisor
        ChatClient client = ChatClient.builder(zhipuModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
        // 同步调用包装为 Mono，附加重试逻辑应对偶发 Connection reset
        ChatResponse response = Mono.fromCallable(() -> client.prompt(new Prompt(msg)).call().chatResponse())
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter(this::isRetryableException)
                        .doBeforeRetry(signal -> System.out.println("[zhipuChatV2] 检测到可重试异常，第 " + (signal.totalRetries() + 1) + " 次重试: " + signal.failure().getMessage())))
                .block();
        // 从 metadata 中提取推理/思考内容（部分模型可能不返回此字段）
        var reason = response.getResult().getOutput().getMetadata().get("reasoningContent");
        // 获取正文回复
        var content = response.getResult().getOutput().getText();

        // 获取 Token 使用统计
        var usage = response.getMetadata().getUsage();
        // 封装返回；reason 为 null 时降级为空串，避免 Map.of 抛 NPE
        return Map.of("思考过程", reason == null ? "" : reason, "结果", content, "token消耗", usage);
    }

    /**
     * 判断异常是否为可重试的瞬态网络错误
     *
     * <p>可重试条件：
     * <ul>
     *   <li>SocketException（含 Connection reset）</li>
     *   <li>IOException 且消息包含 "Connection reset" / "Broken pipe"</li>
     *   <li>WebClientResponseException 且根因为上述网络异常（服务端中途断连，HTTP 200 但 body 读取失败）</li>
     * </ul>
     *
     * <p>不可重试：4xx 认证/参数错误、模型不存在等业务异常
     *
     * @param throwable 待判断的异常
     * @return true 表示可安全重试
     */
    private boolean isRetryableException(Throwable throwable) {
        // 直接是 SocketException（Connection reset、Broken pipe 等）
        if (throwable instanceof SocketException) {
            return true;
        }
        // IOException 中包含连接重置关键字
        if (throwable instanceof IOException) {
            String msg = throwable.getMessage();
            return msg != null && (msg.contains("Connection reset") || msg.contains("Broken pipe"));
        }
        // WebClientResponseException：HTTP 状态码已返回（如 200），但 body 读取时连接断开
        // 此时 getCause() 通常是 SocketException / IOException
        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            return isRetryableException(cause);
        }
        return false;
    }
}
