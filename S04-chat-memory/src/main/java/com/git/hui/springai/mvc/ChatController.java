package com.git.hui.springai.mvc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// ==================== Spring AI 核心依赖 ====================
import org.springframework.ai.chat.client.ChatClient;                          // 流式/同步对话客户端，提供 Builder 模式构建
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;   // 记忆注入策略一：将历史消息作为独立消息对象注入
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;    // 记忆注入策略二：将历史消息追加到 System Prompt
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;        // 日志 Advisor：记录请求/响应的 JSON 详情
import org.springframework.ai.chat.memory.ChatMemory;                         // 记忆存储抽象接口，支持多种持久化实现
import org.springframework.ai.model.ModelOptionsUtils;                        // 模型选项工具类，提供 JSON 序列化等辅助方法
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;                      // 智谱AI（GLM）聊天模型实现

// ==================== Spring 框架依赖 ====================
import org.springframework.beans.factory.annotation.Autowired;                // 自动注入注解
import org.springframework.web.bind.annotation.GetMapping;                    // GET 请求映射
import org.springframework.web.bind.annotation.PathVariable;                  // URL 路径变量绑定
import org.springframework.web.bind.annotation.RequestParam;                 // 请求参数绑定
import org.springframework.web.bind.annotation.RestController;               // REST 控制器（@Controller + @ResponseBody）



/**
 * 聊天记忆（Chat Memory）示例控制器。
 *
 * <h2>概述</h2>
 * <p>
 * 本控制器演示 Spring AI 中基于 {@link ChatMemory} 的多轮对话记忆机制。
 * Spring AI 通过 <b>Advisor 拦截链</b>（类似 Servlet Filter 模式）实现记忆注入：
 * 在每次模型调用前，Advisor 自动从 {@link ChatMemory} 检索历史消息并注入到当前对话上下文中；
 * 调用完成后，Advisor 又将新的用户消息和 AI 回复持久化回记忆存储，形成完整的记忆闭环。
 * </p>
 *
 * <h3>Spring AI 框架上下文</h3>
 * <p>
 * Spring AI 是 Spring 生态对大语言模型（LLM）应用的统一抽象框架，核心概念包括：
 * <ul>
 *   <li><b>ChatModel</b>：大模型调用的统一接口，屏蔽不同厂商（智谱、OpenAI、Ollama等）的差异</li>
 *   <li><b>ChatClient</b>：类似 RestTemplate/WebClient 的高级客户端，提供流式API和 Advisor 拦截链</li>
 *   <li><b>Advisor</b>：类似 Spring MVC 的 HandlerInterceptor，可在调用前后执行拦截逻辑</li>
 *   <li><b>ChatMemory</b>：对话记忆的统一存储抽象，支持多种持久化方案</li>
 * </ul>
 * 本模块属于 S04-chat-memory 示例工程，重点演示 {@link ChatMemory} 与 Advisor 的结合使用。
 * 本系列教程模块演进：S01(基础对话) → S02(提示词) → S03(结构化输出) → <b>S04(聊天记忆)</b> → S05(自定义模型) ...
 * </p>
 *
 * <h3>组件交互关系</h3>
 * <pre>{@code
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │                        ChatController                              │
 *  │                                                                    │
 *  │  ┌────────────┐   ┌──────────────┐   ┌──────────────┐             │
 *  │  │ chatClient │   │sessionClient │   │ promptClient │             │
 *  │  └──────┬─────┘   └──────┬───────┘   └──────┬───────┘             │
 *  │         │                │                   │                     │
 *  │         ▼                ▼                   ▼                     │
 *  │  ┌─────────────────────────────────────────────────────────┐     │
 *  │  │              Advisor 拦截链（共享）                         │     │
 *  │  │  SimpleLoggerAdvisor + ChatMemoryAdvisor                  │     │
 *  │  └───────────────────────────┬─────────────────────────────┘     │
 *  │                              │                                    │
 *  └──────────────────────────────┼────────────────────────────────────┘
 *                                 ▼
 *  ┌──────────────────────────────────────────────────────────────────┐
 *  │  ┌──────────────────┐          ┌──────────────────┐              │
 *  │  │ ZhiPuAiChatModel │          │    ChatMemory     │              │
 *  │  │  (大模型推理)      │          │  (记忆持久化)      │              │
 *  │  └──────────────────┘          └──────────────────┘              │
 *  └──────────────────────────────────────────────────────────────────┘
 * }</pre>
 *
 * <h3>涉及的设计模式</h3>
 * <ul>
 *   <li><b>Builder 模式</b>：{@link ChatClient} 通过 {@code ChatClient.builder(model).build()} 构建，
 *       链式配置 System Prompt、Advisor 等参数，构建后为不可变对象</li>
 *   <li><b>责任链模式（Chain of Responsibility）</b>：多个 {@code Advisor} 按注册顺序依次拦截处理，
 *       每个 Advisor 可修改请求/响应或执行副作用（如日志、记忆注入）</li>
 *   <li><b>策略模式（Strategy）</b>：{@code MessageChatMemoryAdvisor} 和 {@code PromptChatMemoryAdvisor}
 *       是同一接口（记忆注入）的两种不同实现策略，可互换使用</li>
 *   <li><b>模板方法模式</b>：系统提示词中的 {@code {role}} 占位符由 {@code SystemPromptTemplate} 在运行时填充</li>
 * </ul>
 *
 * <h3>Advisor 拦截链执行流程</h3>
 * <pre>{@code
 *  HTTP 请求
 *    │
 *    ▼
 *  ┌─────────────────────────┐
 *  │ SimpleLoggerAdvisor     │  ← order=0，最先执行，记录请求日志
 *  └────────────┬────────────┘
 *               ▼
 *  ┌─────────────────────────┐
 *  │ ChatMemoryAdvisor       │  ← 从 ChatMemory 检索历史消息并注入上下文
 *  │ (Message 或 Prompt 模式) │
 *  └────────────┬────────────┘
 *               ▼
 *  ┌─────────────────────────┐
 *  │ ZhiPuAiChatModel.call() │  ← 携带完整上下文调用大模型
 *  └────────────┬────────────┘
 *               ▼
 *  ┌─────────────────────────┐
 *  │ ChatMemory.save()       │  ← 将本轮 user/assistant 消息写回记忆存储
 *  └─────────────────────────┘
 * }</pre>
 *
 * <h3>两种 Advisor 策略对比</h3>
 * <table border="1" cellpadding="4">
 *   <caption>ChatMemoryAdvisor 策略差异</caption>
 *   <tr><th>策略</th><th>记忆注入方式</th><th>消息结构变化</th><th>适用场景</th></tr>
 *   <tr>
 *     <td>{@link MessageChatMemoryAdvisor}</td>
 *     <td>将历史消息作为独立的 User/Assistant 消息对象注入消息列表</td>
 *     <td>消息列表变长（N 条历史 + 1 条当前）</td>
 *     <td>标准多轮对话，保留完整消息结构（<b>推荐</b>）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link PromptChatMemoryAdvisor}</td>
 *     <td>将历史消息序列化为纯文本，追加到 System Prompt 末尾</td>
 *     <td>系统消息变长，消息列表不变</td>
 *     <td>需要紧凑上下文、或模型对系统消息更敏感的场景</td>
 *   </tr>
 * </table>
 *
 * <h3>接口快速参考</h3>
 * <table border="1" cellpadding="4">
 *   <caption>API 接口清单</caption>
 *   <tr><th>接口</th><th>记忆策略</th><th>角色</th><th>会话隔离</th></tr>
 *   <tr>
 *     <td>{@code GET /ai/generate?msg=xxx}</td>
 *     <td>MessageChatMemoryAdvisor</td>
 *     <td>固定（李白）</td>
 *     <td>否（共享默认会话）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code GET /ai/gen3?msg=xxx}</td>
 *     <td>PromptChatMemoryAdvisor</td>
 *     <td>固定（李白）</td>
 *     <td>否（共享默认会话）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code GET /ai/{user}/gen?role=xxx&msg=xxx}</td>
 *     <td>MessageChatMemoryAdvisor</td>
 *     <td>动态（{role}参数）</td>
 *     <td>是（按 user 隔离）</td>
 *   </tr>
 * </table>
 *
 * <h3>配置前置条件</h3>
 * <p>
 * 本模块依赖以下配置（在 {@code application.yml} 中设置）：
 * <ul>
 *   <li>{@code spring.ai.zhipuai.api-key} — 智谱AI API 密钥</li>
 *   <li>{@code spring.ai.zhipuai.chat.options.model} — 模型名称（如 {@code GLM-4.7-Flash}）</li>
 * </ul>
 * 同时需要配置 {@link ChatMemory} Bean（默认使用 {@code InMemoryChatMemory}）。
 * </p>
 *
 * <h3>线程安全说明</h3>
 * <p>
 * {@link ChatClient} 是不可变对象，构建后可安全地在多线程间共享。
 * {@link ChatMemory} 的线程安全性取决于具体实现（内存实现通常线程安全，JDBC/Redis 实现依赖事务隔离）。
 * </p>
 *
 * <h3>Spring Bean 生命周期</h3>
 * <p>
 * 本类标注 {@code @RestController}，由 Spring 容器作为单例 Bean 管理：
 * <ol>
 *   <li><b>实例化</b>：Spring 调用构造方法，注入 {@link ZhiPuAiChatModel} 和 {@link ChatMemory}</li>
 *   <li><b>初始化</b>：在构造方法中完成三个 {@link ChatClient} 的构建（一次性初始化）</li>
 *   <li><b>服务</b>：每次 HTTP 请求由 Servlet 线程调用对应的 {@code @GetMapping} 方法处理</li>
 *   <li><b>销毁</b>：应用关闭时 Spring 容器销毁此单例 Bean</li>
 * </ol>
 * 由于 ChatClient 是不可变对象，单例模式下多线程并发访问完全安全。
 * </p>
 *
 * @author YiHui
 * @date 2025/7/14
 * @see ChatMemory
 * @see MessageChatMemoryAdvisor
 * @see PromptChatMemoryAdvisor
 * @see SimpleLoggerAdvisor
 */
@RestController  // 等同于 @Controller + @ResponseBody，所有方法返回值直接序列化为 HTTP 响应体
public class ChatController {

    /**
     * 日志记录器，用于输出运行时调试信息。
     * <p>
     * 注意：此处使用全限定类名 {@code org.slf4j.Logger} 声明，避免额外 import；
     * 实际项目中推荐通过 Lombok {@code @Slf4j} 注解或标准方式声明：
     * <pre>{@code
     * private static final Logger log = LoggerFactory.getLogger(ChatController.class);
     * }</pre>
     * </p>
     * <p><b>修饰符说明</b>：
     * {@code static} — 属于类而非实例，所有对象共享同一日志器；
     * {@code final} — 引用不可变，确保日志器始终指向同一个 Logger 实例。</p>
     */
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    /**
     * 智谱AI聊天模型实例，由 Spring 容器自动注入，负责实际的大模型推理调用。
     * <p>
     * {@link ZhiPuAiChatModel} 是 Spring AI 对智谱 AI（GLM 系列模型）的适配实现，
     * 封装了 HTTP 请求构建、流式/同步调用、响应解析等底层细节。
     * 通过 {@code application.yml} 中的 {@code spring.ai.zhipuai.*} 配置项完成初始化。
     * </p>
     * <p><b>{@code final} 语义</b>：字段声明为 {@code final}，确保引用不可变，
     * 构造方法赋值后无法被重新赋值，保证线程安全。</p>
     */
    private final ZhiPuAiChatModel chatModel;

    /**
     * 聊天记忆存储，用于持久化和检索多轮对话历史。
     * <p>
     * {@link ChatMemory} 是 Spring AI 提供的记忆存储抽象接口，核心方法包括：
     * <ul>
     *   <li>{@code get(conversationId)} — 按会话ID检索历史消息列表</li>
     *   <li>{@code add(conversationId, messages)} — 追加新消息到指定会话</li>
     *   <li>{@code clear(conversationId)} — 清除指定会话的全部记忆</li>
     * </ul>
     * 底层可对接多种持久化方案（参见 advance-projects 模块）：
     * <ul>
     *   <li>{@code InMemoryChatMemory} — 基于 ConcurrentHashMap，应用重启后丢失</li>
     *   <li>{@code JdbcChatMemory} — 基于 JDBC（MySQL/H2），支持持久化</li>
     *   <li>{@code RedisChatMemory} — 基于 Redis，支持分布式共享</li>
     * </ul>
     * </p>
     */
    private final ChatMemory chatMemory;

    /**
     * 会话上下文压缩器：当对话历史过多时，自动调用 LLM 将早期消息摘要为一条总结，
     * 从而在保留关键上下文的同时减少消息数量，避免注意力分散和 Token 超限。
     *
     * <p>压缩策略：当消息数超过阈值（默认20条）时，将较早的消息通过 LLM 生成摘要，
     * 保留最近的消息（默认10条）不变。压缩后消息列表变为：[摘要消息] + [近期消息]。</p>
     *
     * @see ChatMemoryCompressor
     */
    private final ChatMemoryCompressor compressor;

    /**
     * 默认角色 ChatClient：固定扮演"诗仙李白"。
     * <p>
     * 使用 {@link MessageChatMemoryAdvisor} 将历史消息集作为独立消息对象注入上下文，
     * 采用默认 conversationId（由 Advisor 自动生成，所有请求共享同一会话）。
     * </p>
     * <p><b>注意</b>：由于使用默认 conversationId，所有通过此 client 的请求共享同一段对话历史。
     * 默认 conversationId 由 {@code MessageChatMemoryAdvisor} 内部生成（通常为固定值 {@code "default"}），
     * 若需要隔离不同用户的对话记忆，请使用 {@link #sessionClient}（参见 {@link #gen2} 方法）。</p>
     * <p><b>常见陷阱</b>：在多用户场景下，若未指定 conversationId，不同用户的对话会混在一起，
     * 导致 A 用户看到 B 用户的对话历史。生产环境中应始终使用会话隔离（参见 {@link #gen2}）。</p>
     */
    private final ChatClient chatClient;

    /**
     * 会话级 ChatClient：支持动态角色切换 + 按用户隔离记忆。
     * <p>
     * 系统提示词包含 {@code {role}} 模板占位符，运行时通过请求参数动态填充；
     * 通过 {@link ChatMemory#CONVERSATION_ID} 参数实现不同用户的对话记忆完全隔离。
     * </p>
     */
    private final ChatClient sessionClient;

    /**
     * 提示词追加模式 ChatClient：使用 {@link PromptChatMemoryAdvisor}。
     * <p>
     * 与 {@link #chatClient} 使用相同角色设定，但记忆注入方式不同：
     * 历史对话被序列化为纯文本追加到系统提示词末尾，而非作为独立消息注入。
     * </p>
     */
    private final ChatClient promptClient;

    /**
     * 构造方法：注入模型与记忆组件，并初始化三个不同策略的 ChatClient。
     * <p>
     * 三个 ChatClient 共享同一个 {@link ZhiPuAiChatModel} 和 {@link ChatMemory}，
     * 但采用不同的 Advisor 组合来实现差异化的记忆注入方式。
     * ChatClient 采用 Builder 模式构建，构建完成后为不可变对象，可安全共享。
     * </p>
     *
     * <h4>{@code @Autowired} 构造器注入说明</h4>
     * <p>
     * Spring 推荐通过构造器注入依赖（而非字段注入 {@code @Autowired} on field），优势包括：
     * <ul>
     *   <li><b>不可变性</b>：依赖字段可声明为 {@code final}，构造后不可修改</li>
     *   <li><b>必选依赖</b>：构造器参数必须由容器提供，缺失时启动即报错（fail-fast）</li>
     *   <li><b>可测试性</b>：单元测试中可直接通过 {@code new} 传入 mock 对象，无需反射</li>
     * </ul>
     * Spring 4.3+ 后，若类只有一个构造器，可省略 {@code @Autowired} 注解。
     * </p>
     *
     * <h4>Advisor 执行链路说明</h4>
     * <p>
     * {@link SimpleLoggerAdvisor} 和 {@link MessageChatMemoryAdvisor}/{@link PromptChatMemoryAdvisor}
     * 构成 Advisor 拦截链。Spring AI 按注册顺序依次执行：
     * <ol>
     *   <li>{@code SimpleLoggerAdvisor}（order=0）：记录请求/响应日志，不修改消息内容</li>
     *   <li>{@code ChatMemoryAdvisor}：从 ChatMemory 检索历史消息并注入当前对话上下文</li>
     * </ol>
     * 调用完成后，ChatMemoryAdvisor 还会将本次新增的用户消息和AI回复写回 ChatMemory 持久化。
     * </p>
     *
     * <h4>SimpleLoggerAdvisor 参数说明</h4>
     * <pre>{@code
     * new SimpleLoggerAdvisor(
     *     requestFormatter,    // Function<Request, String> — 请求体日志格式化函数
     *     responseFormatter,   // Function<Response, String> — 响应体日志格式化函数
     *     order                // int — 执行优先级，数值越小优先级越高
     * )
     * }</pre>
     *
     * <h4>ChatClient.Builder 核心 API</h4>
     * <ul>
     *   <li>{@code .defaultSystem(String)} — 设置默认系统提示词（支持 {@code {xxx}} 模板变量）</li>
     *   <li>{@code .defaultAdvisors(Advisor...)} — 注册默认 Advisor 拦截链</li>
     *   <li>{@code .build()} — 构建不可变的 ChatClient 实例</li>
     * </ul>
     *
     * <h4>ChatClient 运行时 API</h4>
     * <ul>
     *   <li>{@code .prompt()} — 创建空的 PromptSpec（手动构建消息）</li>
     *   <li>{@code .prompt(String)} — 创建包含用户消息的 PromptSpec</li>
     *   <li>{@code .system(Consumer)} — 覆盖/增强系统提示词</li>
     *   <li>{@code .user(String)} — 设置用户消息</li>
     *   <li>{@code .advisors(Consumer)} — 覆盖/增强 Advisor 参数</li>
     *   <li>{@code .call()} — 发起同步调用，返回 CallResponseSpec</li>
     *   <li>{@code .content()} — 提取回复的纯文本内容</li>
     * </ul>
     *
     * @param chatModel  智谱AI聊天模型，负责实际的大模型推理调用
     * @param chatMemory 聊天记忆存储，保存和检索多轮对话的历史消息
     */
    @Autowired  // 构造器注入：Spring 容器自动解析 ZhiPuAiChatModel 和 ChatMemory 两个 Bean 并传入
    public ChatController(ZhiPuAiChatModel chatModel, ChatMemory chatMemory) {
        this.chatModel = chatModel;
        // 使用 MessageWindowChatMemory 限制历史消息数量上限为 20 条
        // 当消息超过上限时，自动移除较早的消息（始终保留系统消息），避免 Token 超限
        // 注：Spring AI 自动配置的 ChatMemory 默认即为 MessageWindowChatMemory（默认20条）
        this.chatMemory = chatMemory;
        // 初始化压缩器：当消息超过 12 条时自动触发压缩，压缩后保留最近 6 条
        this.compressor = new ChatMemoryCompressor(chatModel, this.chatMemory, 12, 6);

        // ========== 1. chatClient：固定角色 + MessageChatMemoryAdvisor ==========
        //
        // 【构建流程】
        //   1. defaultSystem()  → 设置固定的系统提示词，硬编码角色为"诗仙李白"
        //      系统提示词（System Prompt）用于设定 AI 的角色、行为准则和回复风格
        //   2. defaultAdvisors() → 注册 Advisor 拦截链（按声明顺序执行）：
        //      a. SimpleLoggerAdvisor → 使用 JSON 格式化函数打印请求/响应详情，便于调试
        //         - 第1个参数 (ModelOptionsUtils::toJsonStringPrettyPrinter)：请求体格式化函数
        //         - 第2个参数 (ModelOptionsUtils::toJsonStringPrettyPrinter)：响应体格式化函数
        //         - 第3个参数 (0)：执行优先级，0 = 最高优先级，先于 MemoryAdvisor 执行
        //
        //         【方法引用说明】
        //         ModelOptionsUtils::toJsonStringPrettyPrinter 是 Java 8 方法引用语法，
        //         等价于 Lambda: obj -> ModelOptionsUtils.toJsonStringPrettyPrinter(obj)
        //         此处将静态方法作为 Function<T, String> 传入，用于将请求/响应对象序列化为格式化的 JSON 字符串
        //      b. MessageChatMemoryAdvisor → 核心记忆注入器
        //         - 调用前：从 chatMemory 按 conversationId 检索历史消息
        //         - 注入方式：将历史消息作为独立的 UserMessage/AssistantMessage 对象追加到消息列表
        //         - 调用后：将本轮新的用户消息和AI回复写回 chatMemory 持久化
        //   3. build() → 构建不可变的 ChatClient 实例
        //
        // 【注意】此 client 使用默认 conversationId，所有请求共享同一段对话历史
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("你现在是狂放不羁的诗仙李白，我们现在开始对话")
                .defaultAdvisors(new SimpleLoggerAdvisor(ModelOptionsUtils::toJsonStringPrettyPrinter, ModelOptionsUtils::toJsonStringPrettyPrinter, 0),
                        // 每次交互时从记忆库检索历史消息，并将其作为消息集合注入提示词
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        // ========== 2. sessionClient：动态角色 + MessageChatMemoryAdvisor ==========
        //
        // 【与 chatClient 的差异】
        //   - 系统提示词使用 {role} 占位符（而非硬编码），运行时通过 .system(sp -> sp.param("role", role)) 动态填充
        //     Spring AI 的 SystemPromptTemplate 会自动将 {xxx} 语法识别为模板变量
        //   - 调用时通过 .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user)) 指定会话ID
        //     使不同 user 的对话记忆完全隔离，互不干扰
        this.sessionClient = ChatClient.builder(chatModel)
                .defaultSystem("你现在是{role}，我们现在开始对话")
                .defaultAdvisors(new SimpleLoggerAdvisor(ModelOptionsUtils::toJsonStringPrettyPrinter, ModelOptionsUtils::toJsonStringPrettyPrinter, 0),
                        // 每次交互时从记忆库检索历史消息，并将其作为消息集合注入提示词
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        // ========== 3. promptClient：固定角色 + PromptChatMemoryAdvisor ==========
        //
        // 【与 MessageChatMemoryAdvisor 的核心区别】
        //   - MessageChatMemoryAdvisor：历史消息作为独立消息对象注入 → 模型看到 N 条独立消息
        //     消息列表结构：[System, User1, Assistant1, User2, Assistant2, ..., UserN]
        //   - PromptChatMemoryAdvisor：历史消息序列化为文本追加到 System Prompt → 模型看到 1 条长系统消息
        //     消息列表结构：[System + 历史文本摘要, UserN]
        //
        // 【PromptChatMemoryAdvisor 注入效果示意】
        //   最终 System Prompt ≈ "你现在是狂放不羁的诗仙李白，我们现在开始对话\n---\nUser: 你好\nAssistant: ..."
        //   这种方式适合模型对系统消息更敏感、或需要紧凑上下文展示的场景
        this.promptClient = ChatClient.builder(chatModel)
                .defaultSystem("你现在是狂放不羁的诗仙李白，我们现在开始对话")
                .defaultAdvisors(new SimpleLoggerAdvisor(ModelOptionsUtils::toJsonStringPrettyPrinter, ModelOptionsUtils::toJsonStringPrettyPrinter, 0),
                        // 将之前的消息内容以文本的方式追加到系统提示词中
                        PromptChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 默认角色对话接口（MessageChatMemoryAdvisor 策略）。
     * <p>
     * 使用固定的"诗仙李白"角色设定，通过 {@link MessageChatMemoryAdvisor}
     * 自动从 {@link ChatMemory} 中检索历史消息并作为独立消息注入对话上下文，
     * 实现多轮对话的上下文连贯性。默认对话ID由 Advisor 自动生成。
     * </p>
     *
     * <h4>完整调用链路</h4>
     * <pre>{@code
     * GET /ai/generate?msg=xxx
     *   │
     *   ├─→ chatClient.prompt(msg)        // 构建 Prompt，封装为用户消息
     *   ├─→ SimpleLoggerAdvisor           // 记录请求 JSON 日志
     *   ├─→ MessageChatMemoryAdvisor      // 从 ChatMemory 检索历史消息并注入
     *   ├─→ ZhiPuAiChatModel.call()       // 携带 [System + 历史消息 + 当前消息] 调用大模型
     *   ├─→ MessageChatMemoryAdvisor      // 将本轮 user/assistant 消息写回 ChatMemory
     *   └─→ .content()                    // 提取 AssistantMessage 的纯文本内容返回
     * }</pre>
     *
     * <h4>调用示例</h4>
     * <pre>
     * 第1轮: GET /ai/generate?msg=请吟诗一首       → 李白回复诗句
     * 第2轮: GET /ai/generate?msg=再来一首更豪放的  → 李白基于上一轮记忆继续创作
     * 第3轮: GET /ai/generate?msg=你刚才写的什么    → 李白能回忆前两轮的内容
     * </pre>
     *
     * @param msg 用户输入消息，默认为"你好"
     * @return 模型生成的AI回复文本内容（String类型）；
     *         由于返回类型为 {@code Object}，Spring MVC 会直接将 String 写入 HTTP 响应体
     *         （Content-Type 默认为 {@code text/plain}）
     */
    @GetMapping("/ai/generate")  // 映射 GET /ai/generate 请求到本方法
    public Object generate(
            @RequestParam(value = "msg", defaultValue = "你好") String msg) {
            // @RequestParam 绑定 URL 查询参数 ?msg=xxx
            // value="msg"       → 参数名为 msg
            // defaultValue="你好" → 未传参时使用默认值，避免 null
        // prompt(msg)  → 构建 ChatClient.PromptSpec，将 msg 封装为 UserMessage
        // .call()      → 发起同步调用，依次经过 Advisor 拦截链处理后调用大模型
        //                Advisor 链会自动注入历史消息、记录日志等
        // .content()   → 从 ChatResponse 中提取 AssistantMessage 的纯文本内容
        //                等价于 .chatResponse().getResult().getOutput().getText()
        return chatClient.prompt(msg).call().content();
    }

    /**
     * 带自动压缩的对话接口（MessageChatMemoryAdvisor + 上下文压缩）。
     *
     * <p>在每次调用前，自动检查会话消息数量：
     * 当消息超过压缩阈值时，调用 LLM 将早期消息摘要为一条总结，
     * 保留最近的对话内容，从而避免上下文过长导致注意力分散。</p>
     *
     * <h4>压缩流程</h4>
     * <pre>{@code
     * 请求进入 → 检查消息数 → 超过阈值? → 调用LLM生成摘要 → 重写记忆 → 正常对话
     *                              ↓ 否
     *                         直接正常对话
     * }</pre>
     *
     * @param msg 用户输入消息，默认为"你好"
     * @return 模型生成的AI回复文本内容
     */
    @GetMapping("/ai/generate/compress")
    public Object generateWithCompress(
            @RequestParam(value = "msg", defaultValue = "你好") String msg) {
        // 在调用大模型前，自动检查并压缩过长的会话上下文
        boolean compressed = compressor.compressIfNeeded("default");
        if (compressed) {
            log.info("默认会话上下文已自动压缩");
        }
        return chatClient.prompt(msg).call().content();
    }

    /**
     * 提示词追加模式对话接口（PromptChatMemoryAdvisor 策略）。
     * <p>
     * 与 {@link #generate} 使用相同角色设定，但采用 {@link PromptChatMemoryAdvisor}
     * 将历史对话以纯文本形式追加到系统提示词中，而非作为独立消息注入。
     * 这种方式使模型在一条系统消息中看到所有历史上下文，适用于需要紧凑上下文展示的场景。
     * </p>
     *
     * <h4>与 /ai/generate 的核心区别</h4>
     * <table border="1" cellpadding="4">
     *   <tr><th>维度</th><th>/ai/generate (Message模式)</th><th>/ai/gen3 (Prompt模式)</th></tr>
     *   <tr>
     *     <td>历史注入位置</td>
     *     <td>作为独立 User/Assistant 消息注入消息列表</td>
     *     <td>追加到 System Prompt 文本末尾</td>
     *   </tr>
     *   <tr>
     *     <td>消息列表结构</td>
     *     <td>[System, User1, Asst1, User2, Asst2, User3]</td>
     *     <td>[System+历史文本, User3]</td>
     *   </tr>
     *   <tr>
     *     <td>Token 消耗</td>
     *     <td>较高（每条消息有角色标记开销）</td>
     *     <td>较低（历史合并为一条系统消息）</td>
     *   </tr>
     * </table>
     *
     * <h4>调用示例</h4>
     * <pre>
     * 第1轮: GET /ai/gen3?msg=你好              → 李白打招呼（首轮，无历史）
     * 第2轮: GET /ai/gen3?msg=你还记得我是谁吗    → 李白基于系统提示词中的历史文本回答
     * </pre>
     *
     * @param msg 用户输入消息，默认为"你好"
     * @return 模型生成的AI回复文本内容（String类型）；
     *         由于返回类型为 {@code Object}，Spring MVC 会直接将 String 写入 HTTP 响应体
     */
    @GetMapping("/ai/gen3")  // 映射 GET /ai/gen3 请求到本方法
    public Object gen3(
            @RequestParam(value = "msg", defaultValue = "你好") String msg) {
            // @RequestParam 绑定 URL 查询参数 ?msg=xxx，未传参时默认"你好"
        // 使用 promptClient 发起调用，调用链路与 generate() 相同，
        // 唯一区别在于 PromptChatMemoryAdvisor 的记忆注入方式：
        // 1. 从 ChatMemory 中按 conversationId 检索历史消息列表
        // 2. 将历史消息序列化为纯文本格式（如 "User: xxx\nAssistant: xxx"）
        // 3. 将序列化文本追加到 defaultSystem 定义的提示词末尾
        // 4. 最终发送给大模型的消息只有 2 条：[增强后的System消息, 当前User消息]
        return promptClient.prompt(msg).call().content();
    }

    /**
     * 会话隔离的动态角色对话接口（MessageChatMemoryAdvisor + 按用户隔离记忆）。
     * <p>
     * 本接口同时演示两个核心能力：
     * <ul>
     *   <li><b>动态角色切换</b>：通过 {@code {role}} 占位符和 {@code .system(sp -> sp.param("role", role))}
     *       在运行时动态填充系统提示词中的角色模板变量，无需为每个角色创建独立的 ChatClient。
     *       Spring AI 的 {@code SystemPromptTemplate} 会自动将 {@code {xxx}} 语法识别为模板变量并替换。</li>
     *   <li><b>会话记忆隔离</b>：通过 {@code ChatMemory.CONVERSATION_ID} 参数设置会话ID，
     *       使不同 {@code user} 路径参数对应独立的对话记忆，互不干扰。
     *       例如 {@code user=alice} 和 {@code user=bob} 各自维护完全独立的多轮对话历史。</li>
     * </ul>
     * </p>
     *
     * <h4>记忆隔离原理</h4>
     * <pre>{@code
     * ChatMemory 内部存储结构（简化）：
     * {
     *   "alice": [UserMsg("你好"), AsstMsg("..."), UserMsg("..."), ...],
     *   "bob":   [UserMsg("你好"), AsstMsg("..."), ...],
     *   // 默认会话（generate/gen3 接口使用）
     *   "default": [UserMsg(...), AsstMsg(...), ...]
     * }
     * }</pre>
     *
     * <h4>调用示例</h4>
     * <pre>
     * 第1轮: GET /ai/alice/gen?role=苏轼&amp;msg=你好           → 苏轼回复（alice的会话）
     * 第2轮: GET /ai/alice/gen?msg=你还记得刚才说了什么吗    → 苏轼基于 alice 的历史记忆回复
     * 第1轮: GET /ai/bob/gen?role=杜甫&amp;msg=你好             → 杜甫回复（bob的独立会话，与alice互不干扰）
     * </pre>
     *
     * @param user 用户标识（路径变量），用作对话记忆的隔离键（CONVERSATION_ID），
     *             不同 user 值对应完全独立的多轮对话历史
     * @param role 角色名称（请求参数），用于填充系统提示词中的 {@code {role}} 占位符，
     *             默认为"狂放不羁的诗仙李白"
     * @param msg  用户输入消息（请求参数），默认为"你好"
     * @return 模型生成的AI回复文本内容（String类型）；
     *         由于返回类型为 {@code Object}，Spring MVC 会直接将 String 写入 HTTP 响应体
     */
    @GetMapping("/ai/{user}/gen")  // 映射 GET /ai/{user}/gen，{user} 为路径变量
    public Object gen2(
            @PathVariable("user") String user,           // 从 URL 路径中提取用户标识，如 /ai/alice/gen → user="alice"
            @RequestParam(value = "role", defaultValue = "狂放不羁的诗仙李白") String role,  // 角色名，动态填充系统提示词中的 {role}
            @RequestParam(value = "msg", defaultValue = "你好") String msg) {                 // 用户消息
        return sessionClient.prompt()
                // .system() 通过 Lambda 回调动态设置系统提示词参数：
                // sp.param("role", role) 将 {role} 占位符替换为实际角色名
                // 例如 role="苏轼" → 最终系统提示词变为 "你现在是苏轼，我们现在开始对话"
                // 底层由 SystemPromptTemplate 执行模板变量替换
                //
                // 【Lambda 回调模式说明】
                // .system(Consumer<ChatClient.SystemSpec>) 接受一个 Consumer 函数式接口，
                // 在调用时延迟执行，允许每次请求使用不同的参数值（而非构造时固定）
                .system(sp -> sp.param("role", role))
                // .user() 设置本轮用户输入消息，等价于 .user(new UserMessage(msg))
                .user(msg)
                // .advisors() 通过 Lambda 回调向 Advisor 传递运行时参数：
                // ChatMemory.CONVERSATION_ID 指定本次调用的会话ID
                // MessageChatMemoryAdvisor 据此从 ChatMemory 中检索该 user 专属的历史消息
                // 不同 user 值 → 不同 conversationId → 完全隔离的对话记忆
                // 若不设置此参数，将使用默认 conversationId（与 /ai/generate 共享记忆）
                //
                // 【Lambda 回调模式说明】
                // .advisors(Consumer<ChatClient.AdvisorSpec>) 同样采用延迟执行模式，
                // 允许每次请求动态指定 Advisor 参数（如 conversationId），
                // 而非在 ChatClient 构建时固定（defaultAdvisors 是构建时固定的）
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user))
                .call()       // 发起同步调用，经过 Advisor 链（日志 → 记忆注入）→ 大模型推理
                .content();   // 提取并返回 AI 回复的纯文本内容
    }

    /**
     * 带自动压缩的会话隔离对话接口（动态角色 + 记忆隔离 + 上下文压缩）。
     *
     * <p>在 {@link #gen2} 基础上增加自动压缩能力：每次调用前检查该用户的消息数量，
     * 超过阈值时自动压缩早期上下文。</p>
     *
     * @param user 用户标识（路径变量），用作对话记忆的隔离键
     * @param role 角色名称（请求参数），默认为"狂放不羁的诗仙李白"
     * @param msg  用户输入消息（请求参数），默认为"你好"
     * @return 模型生成的AI回复文本内容
     */
    @GetMapping("/ai/{user}/gen/compress")
    public Object gen2WithCompress(
            @PathVariable("user") String user,
            @RequestParam(value = "role", defaultValue = "狂放不羁的诗仙李白") String role,
            @RequestParam(value = "msg", defaultValue = "你好") String msg) {
        // 自动检查并压缩该用户的会话上下文
        boolean compressed = compressor.compressIfNeeded(user);
        if (compressed) {
            log.info("用户 [{}] 会话上下文已自动压缩", user);
        }
        return sessionClient.prompt()
                .system(sp -> sp.param("role", role))
                .user(msg)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user))
                .call()
                .content();
    }

    // ==================== 会话管理接口 ====================

    /**
     * 手动触发会话上下文压缩。
     *
     * <p>无论消息数量是否超过阈值，都强制对指定会话执行压缩。
     * 适用于用户主动请求"总结之前的对话"或"清理上下文"的场景。</p>
     *
     * <h4>调用示例</h4>
     * <pre>
     * POST /ai/memory/compress?conversationId=alice
     * → 返回: "会话 [alice] 压缩完成，压缩前消息数: 15, 压缩后消息数: 7"
     * </pre>
     *
     * @param conversationId 会话ID，对应 {@link #gen2} 中的 user 参数；
     *                       不传则使用默认会话ID
     * @return 压缩结果描述（包含压缩前后的消息数量）
     */
    @GetMapping("/ai/memory/compress")
    public Object manualCompress(
            @RequestParam(value = "conversationId", defaultValue = "default") String conversationId) {
        int beforeCount = compressor.getHistoryCount(conversationId);
        boolean success = compressor.manualCompress(conversationId);
        int afterCount = compressor.getHistoryCount(conversationId);
        if (success) {
            return String.format("会话 [%s] 压缩完成，压缩前消息数: %d, 压缩后消息数: %d",
                    conversationId, beforeCount, afterCount);
        }
        return String.format("会话 [%s] 无需压缩（当前消息数: %d）", conversationId, beforeCount);
    }

    /**
     * 查询指定会话的历史上下文信息。
     *
     * <p>返回当前消息数量，帮助用户判断是否需要手动压缩。</p>
     *
     * <h4>调用示例</h4>
     * <pre>
     * GET /ai/memory/info?conversationId=alice
     * → 返回: "会话 [alice] 当前历史消息数: 12"
     * </pre>
     *
     * @param conversationId 会话ID
     * @return 会话历史信息描述
     */
    @GetMapping("/ai/memory/info")
    public Object memoryInfo(
            @RequestParam(value = "conversationId", defaultValue = "default") String conversationId) {
        int count = compressor.getHistoryCount(conversationId);
        return String.format("会话 [%s] 当前历史消息数: %d", conversationId, count);
    }
}
