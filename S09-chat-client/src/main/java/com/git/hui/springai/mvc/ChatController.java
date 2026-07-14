package com.git.hui.springai.mvc;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ChatClient 使用示例控制器
 * <p>
 * 本控制器演示了 Spring AI 中 {@link ChatClient} 的核心用法，包括：
 * <ul>
 *     <li>结构化输出映射（单对象 / 集合）</li>
 *     <li>流式响应（SSE）</li>
 *     <li>流式响应 + 结构化输出组合</li>
 *     <li>提示词模板与变量替换</li>
 *     <li>基于 defaultSystem/defaultOptions 的预配置 ChatClient</li>
 *     <li>通过 Advisor 实现日志记录与聊天记忆</li>
 * </ul>
 * <p>
 * ChatClient 是 Spring AI 提供的高级 API，相比底层 ChatModel 提供了更流畅的链式调用体验，
 * 内置对提示词模板、结构化输出、Advisor 拦截链等能力的支持。
 *
 * @author YiHui
 * @date 2025/7/14
 */
@RestController
public class ChatController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatController.class);

    /**
     * 通用 ChatClient 实例 —— 无预设系统提示词，每次请求时按需指定 system/user 内容
     */
    private final ChatClient chatClient;

    /**
     * 诗人角色 ChatClient 实例 —— 预配置了系统提示词模板和生成选项（maxTokens=500），
     * 后续调用时只需补充 {role} 参数即可切换诗人身份
     */
    private final ChatClient poemClient;

    /**
     * 聊天记忆存储器 —— 由 Spring AI 自动注入（基于 application.yml 中的配置）
     * <p>
     * {@link ChatMemory} 负责存储和管理对话历史，
     * 配合 {@link MessageChatMemoryAdvisor} 可在每次请求时自动携带历史上下文，
     * 实现多轮对话记忆能力。
     */
    @Autowired
    private ChatMemory chatMemory;


    /**
     * 构造方法：基于注入的 {@link ChatModel} 创建两个 ChatClient 实例
     * <p>
     * {@link ChatClient#builder(ChatModel)} 是创建 ChatClient 的入口，
     * 通过链式调用可设置默认系统提示词（defaultSystem）、默认生成选项（defaultOptions）等，
     * 最终调用 {@code build()} 完成构建。
     *
     * @param chatModel Spring AI 自动装配的聊天模型（本项目使用智谱 GLM-4.7-Flash）
     */
    public ChatController(ChatModel chatModel) {
        // 通用客户端：无任何预设，灵活性最高
        chatClient = ChatClient.builder(chatModel).build();

        // 诗人客户端：预设系统提示词模板 + 最大 Token 限制
        // defaultSystem 中的 {role} 为模板占位符，在实际调用时通过 system(sp -> sp.param("role", xxx)) 填充
        poemClient = ChatClient.builder(chatModel)
                .defaultSystem("你现在扮演著名的诗人{role}，接下来我们进行对话")
                .defaultOptions(ChatOptions.builder().maxTokens(500).build())
                .build();
    }

    /**
     * 结构化输出 —— 单对象映射
     * <p>
     * 通过 {@code call().entity(Class)} 将 AI 返回的文本自动解析为指定的 Java 对象。
     * Spring AI 会在底层自动构建 JSON Schema 约束提示词，引导模型输出符合 {@link Poem} 结构的 JSON，
     * 然后反序列化为 Poem 实例。
     *
     * @param msg 用户输入的提示消息，默认值 "你好"
     * @return 解析后的 {@link Poem} 对象（包含 title 和 content 字段）
     */
    @GetMapping("/ai/generate")
    public Object generate(@RequestParam(value = "msg", defaultValue = "你好") String msg) {
        Poem poem = chatClient.prompt()
                .system("你现在扮演盛唐著名的诗人李白，接下来我们进行对话")  // 设置系统提示词，指定 AI 角色
                .user(msg)                                                      // 设置用户消息
                .call().entity(Poem.class);                                     // 同步调用并将响应映射为 Poem 对象
        return poem;
    }

    /**
     * 结构化输出 —— 集合映射
     * <p>
     * 当需要将 AI 返回映射为 {@code List<Poem>} 等泛型集合时，
     * 由于 Java 泛型擦除，无法直接传入 {@code Class<List<Poem>>}，
     * 需使用 {@link ParameterizedTypeReference} 来保留完整的泛型信息。
     * <p>
     * Spring AI 会根据泛型信息生成对应的 JSON Schema，引导模型输出一个 JSON 数组。
     *
     * @param msg 用户输入的提示消息
     * @return 解析后的 {@link Poem} 列表
     */
    @GetMapping("/ai/batchGen")
    public Object batchGen(@RequestParam(value = "msg", defaultValue = "你好") String msg) {
        List<Poem> poem = chatClient.prompt()
                .system("你现在扮演盛唐著名的诗人李白，接下来我们进行对话")
                .user(msg)
                .call().entity(new ParameterizedTypeReference<List<Poem>>() {   // 通过匿名内部类保留 List<Poem> 泛型
                });
        return poem;
    }

    /**
     * 流式响应 —— SSE（Server-Sent Events）
     * <p>
     * 通过 {@code stream().content()} 获取流式文本输出，返回类型为 {@link Flux}{@code <String>}。
     * 接口声明了 {@code produces = "text/event-stream"}，浏览器可直接通过 EventSource 消费。
     * <p>
     * 流式调用适用于需要实时展示生成过程的场景（如打字机效果），
     * 用户无需等待完整响应即可看到逐步生成的内容。
     *
     * @param msg 用户输入的提示消息
     * @return 流式文本响应，每个元素为一小段生成的文本片段
     */
    @GetMapping(path = "/ai/fluxGen", produces = "text/event-stream")
    public Flux<String> fluxGen(@RequestParam(value = "msg", defaultValue = "你好") String msg) {
        return chatClient.prompt()
                .system("你现在扮演盛唐著名的诗人李白，接下来我们进行对话")
                .user(msg).stream().content();   // stream() 切换为流式调用，content() 提取纯文本内容流
    }

    /**
     * 流式响应 + 结构化输出组合
     * <p>
     * 本方法演示了一种在流式场景下实现结构化输出的思路：
     * <ol>
     *     <li>创建 {@link BeanOutputConverter}，它会根据目标类型生成 JSON Schema 格式说明文本</li>
     *     <li>将格式说明追加到用户消息中，引导模型按指定 JSON 格式输出</li>
     *     <li>通过 {@code stream().content()} 收集完整的流式响应文本</li>
     *     <li>使用 {@code converter.convert()} 将拼接后的完整文本解析为 {@code List<Poem>}</li>
     * </ol>
     * <p>
     * 注意：这种方式会阻塞等待流式数据全部收集完毕后再做解析，
     * 本质上等同于同步调用，但展示了流式数据与结构化转换器的组合用法。
     *
     * @param msg 用户输入的提示消息
     * @return 解析后的 {@link Poem} 列表
     */
    @GetMapping(path = "/ai/fluxGenV2")
    public List<Poem> fluxGenV2(@RequestParam(value = "msg", defaultValue = "你好") String msg) {
        // BeanOutputConverter：根据目标泛型类型自动生成 JSON Schema 格式约束说明
        var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<Poem>>() {
        });

        // 构建流式请求：在用户消息中嵌入格式约束，引导模型输出符合 List<Poem> 结构的 JSON
        Flux<String> flux = chatClient.prompt()
                .system("你现在扮演盛唐著名的诗人李白，接下来我们进行对话")
                // user() 支持 Lambda 方式构建模板消息：{msg} 和 {format} 为模板占位符
                .user(u -> u.text("{msg}.\n{format}").param("msg", msg).param("format", converter.getFormat()))
                .stream().content();

        // 收集流式数据：将 Flux<String> 中所有片段拼接为完整字符串
        String content = flux.collectList().block().stream().collect(Collectors.joining());

        // 使用转换器将完整 JSON 文本解析为 List<Poem>
        return converter.convert(content);
    }


    /**
     * 提示词模板 —— 动态变量替换
     * <p>
     * 演示 ChatClient 的提示词模板能力：
     * <ul>
     *     <li>system/user 均支持 Lambda 方式构建模板，通过 {@code {变量名}} 定义占位符</li>
     *     <li>使用 {@code .param("key", value)} 或 {@code .params(Map)} 传入变量值</li>
     *     <li>默认使用 Spring AI 内置的 {@code DefaultTemplateRenderer}，占位符语法为 {@code {name}}</li>
     * </ul>
     * <p>
     * 注释中的 {@code .templateRenderer(...)} 展示了如何切换为自定义的模板渲染器
     * （如 {@code StTemplateRenderer}，基于 StringTemplate 引擎），
     * 可实现更复杂的模板逻辑（如条件判断、循环等）。
     *
     * @param role 诗人角色名称，默认 "李白"
     * @param msg  用户提问内容，默认 "你好"
     * @return AI 生成的纯文本响应
     */
    @GetMapping("/ai/template")
    public String template(@RequestParam(value = "role", defaultValue = "李白") String role,
                           @RequestParam(value = "msg", defaultValue = "你好") String msg) {
        return chatClient.prompt()
                // system 模板：{role} 会被替换为实际的诗人名称
                .system(u -> u.text("你现在扮演盛唐著名的诗人{role}，接下来我们进行对话")
                        .param("role", role))
                // user 模板：{msg} 会被替换为用户的实际提问；params(Map) 支持批量传参
                .user(u -> u.text("我是一个现代诗歌爱好者，我的提问是：{msg}").params(Map.of("msg", msg)))
//                使用自定义的模板变量替换规则（StTemplateRenderer 基于 StringTemplate 引擎，定界符严格匹配）
//                .templateRenderer(StTemplateRenderer.builder().startDelimiterToken('{').endDelimiterToken('}').build())
                .call().content();   // 同步调用并提取纯文本内容
    }

    /**
     * 预配置 ChatClient 的使用 —— 诗人角色对话
     * <p>
     * 使用构造阶段预配置了 defaultSystem 的 {@code poemClient}，
     * 调用时只需通过 {@code system(sp -> sp.param("role", role))} 补充模板变量即可，
     * 无需重复指定完整的系统提示词文本。
     * <p>
     * 这种模式适合需要固定角色设定但参数可变的场景，减少每次请求的模板拼接成本。
     *
     * @param role 诗人角色名称（如 "李白"、"杜甫" 等）
     * @param msg  用户提问内容
     * @return 解析后的 {@link Poem} 对象
     */
    @GetMapping("/ai/poet")
    public Poem poetChat(String role, String msg) {
        // poemClient 已在构造时预设了 defaultSystem 模板，此处仅需填充 {role} 参数
        return poemClient.prompt().system(sp -> sp.param("role", role))
                .user(msg)
                .call().entity(Poem.class);   // 结构化输出为 Poem 对象
    }



    /**
     * 带聊天记忆的对话接口
     * <p>
     * 通过 {@link org.springframework.ai.chat.client.advisor.api.Advisor} 机制实现请求/响应的拦截增强：
     * <ul>
     *     <li>{@link SimpleLoggerAdvisor} —— 日志记录 Advisor，在请求和响应阶段分别打印自定义格式的日志，
     *         便于调试和追踪 AI 交互过程。构造参数为：请求预处理函数、响应后处理函数、执行顺序（order=0）</li>
     *     <li>{@link MessageChatMemoryAdvisor} —— 聊天记忆 Advisor，
     *         基于注入的 {@link ChatMemory} 自动在请求中附加历史消息，并在响应后保存当前对话</li>
     * </ul>
     * <p>
     * Advisor 按 order 值从小到大依次执行，SimpleLoggerAdvisor(order=0) 先于记忆 Advisor 执行，
     * 因此日志中可以看到包含历史上下文的完整请求内容。
     *
     * @param msg 用户输入消息
     * @return AI 生成的纯文本响应（包含基于历史上下文的回答）
     */
    @GetMapping("/ai/historyChat")
    public String historyChat(String msg) {
        return chatClient.prompt()
                .system("你现在扮演盛唐著名诗人李白，我们接下来开启对话")
                .user(msg)
                // 配置 Advisor 拦截链：日志记录 + 聊天记忆
                .advisors(new SimpleLoggerAdvisor(
                                req -> ("[request] " + req),     // 请求日志格式化：添加 [request] 前缀
                                res -> ("[response] " + res),    // 响应日志格式化：添加 [response] 前缀
                                0),                              // 执行顺序：0（最先执行）
                        MessageChatMemoryAdvisor.builder(chatMemory).build())  // 聊天记忆 Advisor：自动管理对话历史
                .call().content();
    }


    /**
     * 诗歌结构化输出模型
     * <p>
     * 使用 Java Record 定义不可变数据载体，包含：
     * <ul>
     *     <li>{@code title} —— 诗歌标题</li>
     *     <li>{@code content} —— 诗歌正文内容</li>
     * </ul>
     * <p>
     * 当作为 {@code entity()} 或 {@code BeanOutputConverter} 的目标类型时，
     * Spring AI 会自动生成对应的 JSON Schema，引导模型按此结构输出。
     *
     * @param title   诗歌标题
     * @param content 诗歌正文
     */
    record Poem(String title, String content) {
    }

}
