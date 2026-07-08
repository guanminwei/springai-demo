package com.git.hui.offer.mvc;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 对话接口控制器
 * <p>
 * 作为 Web 层入口，接收用户请求并通过 {@link ChatClient} 调用自定义的星火大模型。
 * 本控制器演示了如何使用 Spring AI 的 ChatClient 构建器配置：
 * <ul>
 *     <li><b>系统提示词</b>：通过 defaultSystem 设定 AI 角色（此处为“诗仙李白”）</li>
 *     <li><b>日志 Advisor</b>：通过 {@link SimpleLoggerAdvisor} 记录请求/响应的详细日志</li>
 * </ul>
 * </p>
 * <p>
 * <b>调用链路：</b>
 * <pre>
 *   GET /ai/generate?msg=xxx
 *     → ChatController.generate(msg)
 *       → ChatClient.prompt(msg).call().content()
 *         → SimpleLoggerAdvisor（记录日志）
 *         → SparkLiteModel.call(prompt)  // 自定义 ChatModel
 *           → 星火 API HTTP 调用
 * </pre>
 * </p>
 *
 * @author YiHui
 * @date 2025/7/14
 */
@RestController
public class ChatController {

    /**
     * Spring AI ChatClient，封装了模型调用、Advisor 拦截链等能力
     * <p>
     * ChatClient 是 Spring AI 提供的高层 API，相比直接使用 ChatModel，
     * 它支持系统提示词、Advisor 拦截链、结构化输出等高级功能。
     * </p>
     */
    private final ChatClient chatClient;

    /**
     * 构造器注入 ChatModel，并构建 ChatClient
     * <p>
     * 此处的 ChatModel 实际类型为 {@link com.git.hui.offer.model.SparkLiteModel}，
     * 由 Spring 容器自动注入。通过 ChatClient.builder 构建时配置：
     * <ul>
     *     <li>defaultSystem：设定系统提示词，定义 AI 的角色和行为</li>
     *     <li>defaultAdvisors：配置 Advisor 拦截链，此处使用 SimpleLoggerAdvisor 记录日志</li>
     * </ul>
     * </p>
     *
     * @param chatModel Spring 容器注入的 ChatModel 实现（即 SparkLiteModel）
     */
    @Autowired
    public ChatController(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
                // 系统提示词：设定 AI 角色为“诗仙李白”，影响所有后续对话的风格
                .defaultSystem("你现在是狂放不羁的诗仙李白，我们现在开始对话")
                // 配置 SimpleLoggerAdvisor：记录请求和响应的详细日志
                // 参数1: 请求日志格式化函数（使用 JSON 美化输出）
                // 参数2: 响应日志格式化函数（使用 JSON 美化输出）
                // 参数3: Advisor 执行顺序（0 表示最高优先级）
                .defaultAdvisors(new SimpleLoggerAdvisor(
                        ModelOptionsUtils::toJsonStringPrettyPrinter,
                        ModelOptionsUtils::toJsonStringPrettyPrinter,
                        0))
                .build();

    }

    /**
     * AI 对话生成接口
     * <p>
     * 接收用户输入的消息，通过 ChatClient 调用星火大模型，返回 AI 生成的文本内容。
     * ChatClient 内部会自动：
     * <ol>
     *     <li>将用户消息与系统提示词组装为 Prompt</li>
     *     <li>经过 SimpleLoggerAdvisor 记录请求日志</li>
     *     <li>调用 SparkLiteModel.call() 发送请求到星火 API</li>
     *     <li>经过 SimpleLoggerAdvisor 记录响应日志</li>
     *     <li>提取并返回生成内容</li>
     * </ol>
     * </p>
     *
     * @param msg 用户输入的消息，默认为“你好”；通过 URL 参数 ?msg=xxx 传入
     * @return AI 生成的文本内容（字符串）
     */
    @GetMapping("/ai/generate")
    public Object generate(@RequestParam(value = "msg", defaultValue = "你好") String msg) {
        // prompt(msg): 创建用户消息
        // call(): 同步调用大模型（经过 Advisor 拦截链）
        // content(): 提取生成结果中的文本内容
        return chatClient.prompt(msg).call().content();
    }
}
