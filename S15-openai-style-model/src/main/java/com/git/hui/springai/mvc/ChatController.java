package com.git.hui.springai.mvc;

import io.micrometer.common.util.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenAI 兼容接口风格的大模型对话控制器
 *
 * <p>本模块演示如何通过 Spring AI 的 OpenAI 兼容协议接入不同的大模型服务商。
 * Spring AI 提供了对 OpenAI API 协议的通用抽象，任何兼容 OpenAI 接口规范的服务商
 * （如讯飞星火、阿里百炼等）都可以通过修改 baseUrl 和 apiKey 的方式直接接入，
 * 而无需编写特定适配代码。</p>
 *
 * <p>本控制器注册了两个 HTTP GET 接口：</p>
 * <ul>
 *     <li>{@code /chat} — 使用默认配置的讯飞星火模型（通过 application.yml 配置）</li>
 *     <li>{@code /aliChat} — 使用手动注册的阿里百炼模型（通过代码编程式配置）</li>
 * </ul>
 *
 * <p>核心要点：</p>
 * <ul>
 *     <li>默认 ChatModel 由 Spring Boot 自动装配，基于 application.yml 中的 spring.ai.openai 配置</li>
 *     <li>阿里百炼模型通过手动构建 OpenAiApi + OpenAiChatModel 的方式注册，展示编程式配置能力</li>
 *     <li>API Key 支持三级优先级获取：启动参数 > JVM 系统属性 > 环境变量</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/8/26
 * @see org.springframework.ai.openai.OpenAiChatModel
 * @see org.springframework.ai.chat.client.ChatClient
 */
@RestController
public class ChatController {

    /**
     * 默认的 ChatClient 实例，基于 application.yml 中配置的讯飞星火模型构建
     *
     * <p>通过 Spring 自动注入的 {@link ChatModel} 创建，底层连接讯飞星火的 OpenAI 兼容接口。
     * 集成了 {@link SimpleLoggerAdvisor} 用于在 debug 日志级别下记录请求/响应的详细信息，
     * 便于开发阶段排查和调试 AI 交互过程。</p>
     */
    private final ChatClient chatClient;

    /**
     * 阿里百炼（DashScope）大模型实例，通过编程式手动注册
     *
     * <p>该模型使用 OpenAI 兼容协议连接阿里百炼平台，接口地址为
     * {@code https://dashscope.aliyuncs.com/compatible-mode}，默认使用 {@code qwen-plus} 模型。
     * 与自动装配的默认模型不同，此实例完全由代码手动构建，展示了如何在运行时动态注册
     * 额外的模型服务商。</p>
     */
    private final ChatModel dashModel;


    /**
     * 构造函数：初始化默认 ChatClient 和阿里百炼 ChatModel
     *
     * <p>初始化流程分为两步：</p>
     * <ol>
     *     <li>使用 Spring 自动注入的 {@code chatModel}（讯飞星火）构建 ChatClient，
     *         并挂载 {@link SimpleLoggerAdvisor} 实现请求/响应日志记录</li>
     *     <li>通过 {@link OpenAiApi.Builder} 手动构建阿里百炼的 API 连接，
     *         指定 DashScope 的 OpenAI 兼容端点和 API Key，然后创建 {@link OpenAiChatModel}
     *         并设置默认模型为 {@code qwen-plus}</li>
     * </ol>
     *
     * @param chatModel   Spring 自动装配的默认 ChatModel，对应 application.yml 中配置的讯飞星火模型
     * @param environment Spring 环境对象，用于按优先级读取阿里百炼 API Key 配置
     */
    public ChatController(ChatModel chatModel, Environment environment) {
        // 构建默认 ChatClient：基于讯飞星火模型，集成 SimpleLoggerAdvisor 用于 debug 级别日志
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();


        // 通过手动方式注册阿里百炼模型：构建 OpenAI 兼容的 API 连接
        // baseUrl 指向 DashScope 的 OpenAI 兼容模式端点
        OpenAiApi openAiApi = OpenAiApi.builder().apiKey(getDashApiKey(environment))
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .build();
        // 创建 OpenAiChatModel 实例，指定默认使用 qwen-plus 模型
        dashModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-plus").build())
                .build();

    }

    /**
     * 按优先级获取阿里百炼（DashScope）的 API Key
     *
     * <p>API Key 的获取遵循三级优先级回退策略，确保灵活的配置方式：</p>
     * <ol>
     *     <li><b>启动命令参数</b>：通过 {@code --dash-api-key=xxx} 传入，
     *         由 Spring {@link Environment#getProperty(String)} 读取，优先级最高</li>
     *     <li><b>JVM 系统属性</b>：通过 {@code -Ddash-api-key=xxx} 传入，
     *         由 {@link System#getProperty(String)} 读取</li>
     *     <li><b>操作系统环境变量</b>：通过 {@code export dash-api-key=xxx} 设置，
     *         由 {@link System#getenv(String)} 读取，优先级最低</li>
     * </ol>
     *
     * @param environment Spring 环境对象，用于读取启动命令参数
     * @return 阿里百炼 API Key 字符串；若三级来源均未配置则可能返回 null
     */
    private String getDashApiKey(Environment environment) {
        final String key = "DASHBOARD_API_KEY";
        // 第1优先级：通过启动命令参数 --dash-api-key=xxx 传入
        String val = environment.getProperty(key);
        if (StringUtils.isBlank(val)) {
            // 第2优先级：通过 JVM 系统属性 -Ddash-api-key=xxx 传入
            val = System.getProperty(key);
            if (val == null) {
                // 第3优先级：通过操作系统环境变量 dash-api-key=xxx 传入
                val = System.getenv(key);
            }
        }
        return val;
    }

    /**
     * 默认模型对话接口 — 使用讯飞星火模型
     *
     * <p>通过 {@link ChatClient} 发送用户消息并获取 AI 回复。
     * ChatClient 内部已集成 {@link SimpleLoggerAdvisor}，会在 debug 日志级别下
     * 自动记录请求和响应的详细内容。</p>
     *
     * <p>请求示例：{@code GET /chat?msg=你好}</p>
     *
     * @param msg 用户输入的聊天消息内容
     * @return AI 模型生成的回复文本
     */
    @GetMapping(path = "chat")
    public String chat(String msg) {
        return chatClient.prompt(msg).call().content();
    }


    /**
     * 阿里百炼模型对话接口 — 使用 DashScope qwen-plus 模型
     *
     * <p>直接调用手动注册的 {@link ChatModel} 实例（非 ChatClient），
     * 构造 {@link UserMessage} 发送给阿里百炼模型并获取回复。
     * 此接口展示了绕过 ChatClient 直接使用底层 ChatModel 的调用方式。</p>
     *
     * <p>请求示例：{@code GET /aliChat?msg=你好}</p>
     *
     * @param msg 用户输入的聊天消息内容
     * @return 阿里百炼模型生成的回复文本
     */
    @GetMapping(path = "aliChat")
    public String aliChat(String msg) {
        return dashModel.call(new UserMessage(msg));
    }
}
