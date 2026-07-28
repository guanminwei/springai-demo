package com.git.hui.springai.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 自定义增强日志 Advisor —— 在 ChatClient 请求/响应链路中记录详细的调试信息。
 *
 * <p>本类实现 Spring AI 的 {@link BaseAdvisor} 接口，以 AOP 拦截器的方式嵌入
 * ChatClient 的调用链。相比 Spring AI 内置的 {@code SimpleLoggerAdvisor}，
 * 本 Advisor 提供更丰富的日志维度：</p>
 * <ul>
 *   <li><b>请求阶段（before）</b>：记录 System 消息内容、当前可用工具列表、
 *       最后一条用户消息或工具响应消息的内容</li>
 *   <li><b>响应阶段（after）</b>：记录大模型返回的工具调用请求（tool_calls）
 *       和/或最终文本回复</li>
 * </ul>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>通过 {@link Builder} 模式构建，支持配置是否展示 SystemMessage 和可用工具列表</li>
 *   <li>实现 {@link org.springframework.core.Ordered} 语义（通过 {@code getOrder()}），
 *       可与其他 Advisor 协同控制执行顺序</li>
 *   <li>日志级别为 DEBUG，生产环境默认不输出，开发调试时调整日志级别即可启用</li>
 *   <li>对长文本进行截断处理（{@link #first(String, int)}），避免日志膨胀</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ChatClient chatClient = chatClientBuilder
 *     .defaultAdvisors(
 *         new SimpleLoggerAdvisor(),
 *         MyLoggingAdvisor.builder()
 *             .showSystemMessage(true)
 *             .showAvailableTools(true)
 *             .build())
 *     .build();
 * }</pre>
 *
 * @author YiHui
 * @date 2026/1/26
 * @see BaseAdvisor
 * @see org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
 */
public class MyLoggingAdvisor implements BaseAdvisor {
    private static final Logger log = LoggerFactory.getLogger(MyLoggingAdvisor.class);

    /** Advisor 执行顺序，数值越小优先级越高，用于多 Advisor 场景下的排序控制 */
    private final int order;

    /** 是否在 before 阶段记录 SystemMessage 内容（默认 true） */
    public final boolean showSystemMessage;

    /** 是否在 before 阶段记录当前请求可用的工具名称列表（默认 true） */
    public final boolean showAvailableTools;

    /**
     * 私有构造方法 —— 通过 {@link Builder} 创建实例。
     *
     * @param order             执行顺序
     * @param showSystemMessage 是否展示系统消息
     * @param showAvailableTools 是否展示可用工具列表
     */
    private MyLoggingAdvisor(int order, boolean showSystemMessage, boolean showAvailableTools) {
        this.order = order;
        this.showSystemMessage = showSystemMessage;
        this.showAvailableTools = showAvailableTools;
    }

    /**
     * 返回 Advisor 的执行顺序。
     *
     * <p>在多 Advisor 链中，Spring 按 order 值从小到大依次执行 before 方法，
     * 响应阶段则按逆序执行 after 方法（类似 Servlet Filter 的洋葱模型）。</p>
     *
     * @return 排序值，默认 0
     */
    @Override
    public int getOrder() {
        return this.order;
    }

    /**
     * 请求前置拦截 —— 在 Prompt 发送给大模型之前记录详细的请求上下文信息。
     *
     * <p>记录内容包括（按配置开关控制）：</p>
     * <ol>
     *   <li>SystemMessage 内容（截断至 300 字符）</li>
     *   <li>当前请求注册的所有工具名称列表（JSON 格式）</li>
     *   <li>消息列表中最后一条 UserMessage 或 ToolResponseMessage 的内容</li>
     * </ol>
     *
     * <p>第 3 点的设计意图：在多轮工具调用场景中，最后一条消息可能是工具响应
     * （第 2 次回传结果时），此时记录工具响应内容比记录用户原始输入更有调试价值。</p>
     *
     * @param chatClientRequest 当前请求对象，包含 Prompt 和配置选项
     * @param advisorChain      Advisor 链，用于传递到下一个 Advisor
     * @return 原样返回请求对象（本 Advisor 不修改请求内容，仅做日志记录）
     */
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        StringBuilder sb = new StringBuilder("\n[log] USER INPUT⬇️：");

        // 记录 System 消息（如果存在且开关开启）
        if (this.showSystemMessage && chatClientRequest.prompt().getSystemMessage() != null) {
            sb.append("\n [log] SYSTEM: ").append(first(chatClientRequest.prompt().getSystemMessage().getText(), 300));
        }

        // 记录当前请求可用的工具名称列表
        if (this.showAvailableTools) {
            Object tools = "No Tools";

            // 从 Prompt 的 Options 中提取 ToolCallingChatOptions，获取已注册的 ToolCallback 名称
            if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions) {
                tools = toolOptions.getToolCallbacks().stream().map(tc -> tc.getToolDefinition().name()).toList();
            }

            sb.append("\n [log] TOOLS: ").append(ModelOptionsUtils.toJsonString(tools));
        }

        // 从消息列表中逆序查找最后一条 UserMessage 或 ToolResponseMessage
        // 逆序查找的原因：消息列表可能包含多轮对话历史，我们只关心最新的那条
        List<Message> msgList = chatClientRequest.prompt().getInstructions();
        Message lastMessage = null;
        for (int i = msgList.size() - 1; i >= 0; i--) {
            Message message = msgList.get(i);
            if (message instanceof UserMessage || message instanceof ToolResponseMessage) {
                lastMessage = message;
                break;
            }
        }
        // 容错：若消息列表为空或无匹配消息，使用空 UserMessage 兜底
        if (lastMessage == null) {
            lastMessage = new UserMessage("");
        }

        // 根据最后一条消息的类型，记录不同维度的信息
        if (lastMessage.getMessageType() == MessageType.TOOL) {
            // 工具响应消息：记录每个工具的执行结果（截断至 1000 字符）
            ToolResponseMessage toolResponseMessage = (ToolResponseMessage) lastMessage;
            for (var toolResponse : toolResponseMessage.getResponses()) {
                var tr = toolResponse.name() + ": " + first(toolResponse.responseData(), 1000);
                sb.append("\n [log] TOOL-RESPONSE: ").append(tr);
            }
        } else if (lastMessage.getMessageType() == MessageType.USER) {
            // 用户消息：记录用户输入的文本内容（截断至 1000 字符）
            if (StringUtils.hasText(lastMessage.getText())) {
                sb.append("\n [log] TEXT: ").append(first(lastMessage.getText(), 1000));
            }
        }

        log.debug("[log] before: {}", sb);
        return chatClientRequest;
    }

    /**
     * 响应后置拦截 —— 在大模型返回结果后记录响应内容。
     *
     * <p>记录内容包括：</p>
     * <ul>
     *   <li>工具调用请求（tool_calls）：记录工具名称和调用参数</li>
     *   <li>文本回复：记录大模型生成的自然语言文本（截断至 1200 字符）</li>
     * </ul>
     *
     * <p>注意：在手动执行模式下，第一次调用的响应通常只包含 tool_calls 而无文本；
     * 第二次调用（回传工具结果后）的响应才包含最终文本。</p>
     *
     * @param chatClientResponse 大模型的响应对象
     * @param advisorChain       Advisor 链
     * @return 原样返回响应对象（本 Advisor 不修改响应内容，仅做日志记录）
     */
    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        StringBuilder sb = new StringBuilder("\nASSISTANT: ");

        // 空响应保护：大模型可能返回 null（如超时、限流等异常情况）
        if (chatClientResponse.chatResponse() == null || chatClientResponse.chatResponse().getResults() == null) {
            sb.append(" [log] No chat response ");
            log.debug("[log] after: {}", sb);
            return chatClientResponse;
        }

        // 遍历所有 Generation 结果（通常只有一个，但 API 支持 n>1 的多候选返回）
        for (var generation : chatClientResponse.chatResponse().getResults()) {
            var message = generation.getOutput();

            // 记录工具调用请求：工具名 + JSON 参数
            if (message.getToolCalls() != null) {
                for (var toolCall : message.getToolCalls()) {
                    sb.append("\n [log] TOOL-CALL: ")
                            .append(toolCall.name())
                            .append(" (")
                            .append(toolCall.arguments())
                            .append(")");
                }
            }

            // 记录文本回复（截断至 1200 字符，避免长文本撑爆日志）
            if (message.getText() != null) {
                if (StringUtils.hasText(message.getText())) {
                    sb.append("\n [log] TEXT: ").append(first(message.getText(), 1200));
                }
            }
        }

        // 将换行替换为制表符，使日志在单行内展示，便于日志检索工具过滤
        log.debug("[log] after: {}", sb.toString().replaceAll("\n", "\t"));
        return chatClientResponse;
    }

    /**
     * 文本截断工具方法 —— 超过指定长度时截断并追加省略号。
     *
     * @param text 原始文本
     * @param n    最大保留字符数
     * @return 截断后的文本（若未超长则原样返回）
     */
    private String first(String text, int n) {
        if (text.length() <= n) {
            return text;
        }
        return text.substring(0, n) + "...";
    }

    /**
     * 创建 Builder 实例 —— 入口方法。
     *
     * @return 新的 Builder 对象
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * MyLoggingAdvisor 构建器 —— 通过链式调用配置 Advisor 行为。
     *
     * <p>支持配置项：</p>
     * <ul>
     *   <li>{@code order} - 执行顺序（默认 0）</li>
     *   <li>{@code showSystemMessage} - 是否记录 SystemMessage（默认 true）</li>
     *   <li>{@code showAvailableTools} - 是否记录可用工具列表（默认 true）</li>
     * </ul>
     */
    public static class Builder {

        /** 执行顺序，默认 0（最高优先级） */
        private int order = 0;

        /** 是否展示系统消息，默认开启 */
        private boolean showSystemMessage = true;

        /** 是否展示可用工具列表，默认开启 */
        private boolean showAvailableTools = true;

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder showSystemMessage(boolean showSystemMessage) {
            this.showSystemMessage = showSystemMessage;
            return this;
        }

        public Builder showAvailableTools(boolean showAvailableTools) {
            this.showAvailableTools = showAvailableTools;
            return this;
        }

        public MyLoggingAdvisor build() {
            MyLoggingAdvisor advisor = new MyLoggingAdvisor(this.order, this.showSystemMessage,
                    this.showAvailableTools);
            return advisor;
        }
    }

}
