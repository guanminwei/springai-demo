package com.git.hui.springai.app.react.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式 ReAct Agent 实现
 * <p>
 * 本类是 {@link com.git.hui.springai.app.react.simple.SimpleReActAgent} 的流式版本，
 * 使用 Flux 流式响应与大模型交互，可实时获取模型的思考过程和工具调用决策。
 * <p>
 * 与 SimpleReActAgent 的核心区别：
 * <ul>
 *     <li>SimpleReActAgent：使用 call() 同步调用，等待完整响应后处理</li>
 *     <li>StreamReActAgent（本类）：使用 stream() 流式调用，通过 Flux 逐步接收响应片段</li>
 * </ul>
 * <p>
 * 流式处理要点：
 * <ul>
 *     <li>使用 AtomicReference 保存最后一个 AssistantMessage（流式场景下需要聚合）</li>
 *     <li>使用 StringBuilder 累积流式文本片段，最终构建完整的回复</li>
 *     <li>通过 doOnNext + blockLast 阻塞等待流式响应完成</li>
 *     <li>当检测到工具调用时，停止文本累积，进入 Act 阶段</li>
 * </ul>
 * <p>
 * 执行流程（与 SimpleReActAgent 一致）：
 * <pre>
 * 1. 用户提问 → 构建初始消息列表
 * 2. Thinking  → 流式发送给大模型
 * 3. 检查响应  → 是否包含工具调用？
 *    ├─ 是 → Act: 执行工具 → Observe: 结果加入历史 → 回到步骤 2
 *    └─ 否 → 返回聚合后的最终答案
 * </pre>
 *
 * @author YiHui
 * @date 2026/3/4
 * @see com.git.hui.springai.app.react.simple.SimpleReActAgent
 * @see CalculatorTools
 */
public class StreamReActAgent {
    private static final Logger log = LoggerFactory.getLogger(StreamReActAgent.class);

    // 最大迭代次数
    private static final int MAX_ITERATIONS = 10;

    private final ChatClient chatClient;
    private final List<ToolCallback> tools;

    /**
     * 构造方法
     *
     * @param chatClient 用于与大模型交互的 ChatClient
     * @param tools      可供 Agent 调用的工具列表（可为 null）
     */
    public StreamReActAgent(ChatClient chatClient, List<ToolCallback> tools) {
        this.chatClient = chatClient;
        this.tools = tools != null ? tools : new ArrayList<>();
    }

    /**
     * 运行 ReAct 循环（流式交互）
     *
     * @param question 用户问题
     * @return 最终答案
     */
    public String run(String question) {
        // 1. 初始化对话历史
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(question));

        log.info("╔════════════════════════════════════════╗");
        log.info("║       🌊 Stream ReAct Agent 启动        ║");
        log.info("╚════════════════════════════════════════╝");
        log.info("💬 问题：{}", question);

        // 2. ReAct 循环
        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            log.info("┌────────────────────────────────────────");
            log.info("│ 🔁 第 {} 轮思考", iteration);
            log.info("└────────────────────────────────────────");

            try {
                // Thinking: 流式获取大模型的思考过程
                AssistantMessage assistantMessage = thinkStreaming(messages);

                // Act & Observe: 检查是否需要调用工具
                if (hasToolCalls(assistantMessage)) {
                    // 执行工具调用
                    String toolResult = executeTools(assistantMessage);

                    // 将工具结果添加到对话历史
                    var toolCall = getToolCall(assistantMessage);
                    messages.add(ToolResponseMessage.builder().responses(
                            List.of(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), toolResult)
                            )
                    ).build());

                    log.info("工具执行结果 {} => {}", toolCall.name(), toolResult);

                } else {
                    // 没有工具调用，说明大模型已给出最终答案
                    String finalAnswer = assistantMessage.getText();
                    log.info(" ✅ 无工具调用，直接返回结果：{}", finalAnswer);

                    log.info("╔════════════════════════════════════════╗");
                    log.info("║       ✨ ReAct 任务完成 ✨              ║");
                    log.info("╚════════════════════════════════════════╝\n");
                    return finalAnswer;
                }

            } catch (Exception e) {
                log.error("ReAct 循环出错：{}", e.getMessage(), e);
                break;
            }
        }

        log.info("╔════════════════════════════════════════╗");
        log.info("║ ⚠️  达到最大迭代次数，任务终止          ║");
        log.info("╚════════════════════════════════════════╝\n");
        return "达到最大迭代次数 (" + MAX_ITERATIONS + ")，无法完成任务。";
    }

    /**
     * Thinking 阶段 - 流式获取大模型响应
     */
    private AssistantMessage thinkStreaming(List<Message> messages) {
        // 构建系统提示
        String systemPrompt = """
                你是一个智能助手，使用 ReAct 范式解决问题。
                                
                请按以下步骤思考：
                1. 分析当前问题和已有信息
                2. 判断是否需要使用工具获取更多信息
                3. 如果需要工具，调用一个合适的工具
                4. 【重要】如果已有足够信息，直接给出最终答案
                                
                响应规则：
                - 你必须一次只调用一个工具，不允许同时调用多个工具
                - 关键：调用工具时，你必须使用工具定义中完整的工具名称（例如："fs-read-file-operator"）
                - 【重要】如果之前的工具执行已经提供了足够的信息来回答原始问题，不要再调用另一个工具。相反，综合结果并直接提供你的最终答案
                                
                可用工具列表：\n""" + getToolDescriptions();

        Message systemMessage = new org.springframework.ai.chat.messages.SystemMessage(systemPrompt);
        List<Message> allMessages = new ArrayList<>();
        allMessages.add(systemMessage);
        allMessages.addAll(messages);

        // 设置工具调用选项
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false)  // 禁用自动执行，由我们手动控制
                .build();

        Prompt prompt = new Prompt(allMessages, options);

        // 使用流式响应
        AtomicReference<AssistantMessage> lastMessage = new AtomicReference<>();
        StringBuilder fullText = new StringBuilder();

        Flux<ChatResponse> responseFlux = chatClient.prompt(prompt)
                .toolCallbacks(tools)
                .stream()
                .chatResponse();

        // 处理流式响应
        responseFlux.doOnNext(response -> {
            if (response.getResult() != null && response.getResult().getOutput() != null) {
                AssistantMessage output = response.getResult().getOutput();

                // 累积文本内容
                String text = output.getText();
                if (text != null && !text.isEmpty()) {
                    fullText.append(text);
                    log.info("【流式文本】{}", text);
                }

                // 检测工具调用
                if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                    log.info("【检测到工具调用】");
                }

                // 保存最后一个消息
                lastMessage.set(output);
            }
        }).blockLast();

        // 如果有累积的文本且没有工具调用，构建完整的消息
        if (lastMessage.get() != null) {
            AssistantMessage currentMessage = lastMessage.get();
            if ((currentMessage.getToolCalls() == null || currentMessage.getToolCalls().isEmpty())
                    && fullText.length() > 0) {
                // 重新构建包含完整文本的消息
                return AssistantMessage.builder()
                        .content(fullText.toString())
                        .build();
            }
        }

        return lastMessage.get();
    }

    /**
     * 检查是否有工具调用
     */
    private boolean hasToolCalls(AssistantMessage message) {
        return message.getToolCalls() != null && !message.getToolCalls().isEmpty();
    }

    /**
     * 获取第一个工具调用
     */
    private AssistantMessage.ToolCall getToolCall(AssistantMessage message) {
        if (hasToolCalls(message)) {
            return message.getToolCalls().get(0);
        }
        return null;
    }

    /**
     * Act & Observe 阶段 - 执行工具并观察结果
     */
    private String executeTools(AssistantMessage message) throws Exception {
        var toolCall = getToolCall(message);  // 只处理第一个工具调用

        log.debug("  ┌─ 🔧 执行工具调用");
        log.debug("  │   工具名称：{}", toolCall.name());
        log.debug("  │   工具参数：{}", toolCall.arguments());

        // 查找并执行匹配的工具
        for (ToolCallback tool : tools) {
            if (tool.getToolDefinition().name().equals(toolCall.name())) {
                Object result = tool.call(toolCall.arguments());
                String resultText = result != null ? result.toString() : "null";
                log.debug("  │   执行结果：{}", resultText);
                log.debug("  └─ ✓ 工具执行完成\n");
                return resultText;
            }
        }

        throw new RuntimeException("未找到工具：" + toolCall.name());
    }

    /**
     * 获取工具描述信息
     */
    private String getToolDescriptions() {
        if (tools.isEmpty()) {
            return "无";
        }

        StringBuilder sb = new StringBuilder();
        for (ToolCallback tool : tools) {
            sb.append("- ").append(tool.getToolDefinition().name())
                    .append(": ").append(tool.getToolDefinition().description())
                    .append("\n");
        }
        return sb.toString();
    }
}
