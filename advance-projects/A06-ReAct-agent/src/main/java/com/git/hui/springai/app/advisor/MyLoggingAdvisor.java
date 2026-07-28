package com.git.hui.springai.app.advisor;

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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自定义日志 Advisor - 用于观测 ReAct Agent 的请求/响应过程
 * <p>
 * 本类实现了 Spring AI 的 {@link BaseAdvisor} 接口，在 LLM 调用前后拦截请求和响应，
 * 记录详细的调试信息，包括：
 * <ul>
 *     <li>before() - 请求拦截：记录系统提示词、可用工具列表、用户输入、工具响应</li>
 *     <li>after() - 响应拦截：记录模型的工具调用（Tool-Call）和文本回复</li>
 * </ul>
 * <p>
 * 设计特点：
 * <ul>
 *     <li>采用 Builder 模式，支持链式配置 order、showSystemMessage、showAvailableTools</li>
 *     <li>支持通过 order 控制在 Advisor 链中的执行顺序</li>
 *     <li>日志级别为 DEBUG，避免生产环境输出过多信息</li>
 *     <li>对超长文本自动截断（通过 first() 方法），防止日志过大</li>
 * </ul>
 * <p>
 * 在 ReAct 循环中的观测价值：
 * <ul>
 *     <li>观察每轮 Thinking 阶段模型是否决定调用工具</li>
 *     <li>观察工具调用参数和返回结果</li>
 *     <li>追踪多轮循环的完整执行链路</li>
 * </ul>
 *
 * @author YiHui
 * @date 2026/1/26
 * @see BaseAdvisor
 * @see ChatClientRequest
 * @see ChatClientResponse
 */
public class MyLoggingAdvisor implements BaseAdvisor {
    private static final Logger log = LoggerFactory.getLogger(MyLoggingAdvisor.class);
    private final int order;

    public final boolean showSystemMessage;

    public final boolean showAvailableTools;

    private MyLoggingAdvisor(int order, boolean showSystemMessage, boolean showAvailableTools) {
        this.order = order;
        this.showSystemMessage = showSystemMessage;
        this.showAvailableTools = showAvailableTools;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        StringBuilder sb = new StringBuilder("\n[log] USER INPUT⬇️：");

        if (this.showSystemMessage && chatClientRequest.prompt().getSystemMessage() != null) {
            sb.append("\n [log] SYSTEM: ").append(first(chatClientRequest.prompt().getSystemMessage().getText(), 300));
        }

        if (this.showAvailableTools) {
            Object tools = "No Tools";

            if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions) {
                tools = toolOptions.getToolCallbacks().stream().map(tc -> tc.getToolDefinition().name()).toList();
            }

            sb.append("\n [log] TOOLS: ").append(ModelOptionsUtils.toJsonString(tools));
        }

        List<Message> msgList = chatClientRequest.prompt().getInstructions();
        Message lastMessage = null;
        for (int i = msgList.size() - 1; i >= 0; i--) {
            Message message = msgList.get(i);
            if (message instanceof UserMessage || message instanceof ToolResponseMessage) {
                lastMessage = message;
                break;
            }
        }
        if (lastMessage == null) {
            lastMessage = new UserMessage("");
        }

        if (lastMessage.getMessageType() == MessageType.TOOL) {
            ToolResponseMessage toolResponseMessage = (ToolResponseMessage) lastMessage;
            for (var toolResponse : toolResponseMessage.getResponses()) {
                var tr = toolResponse.name() + ": " + first(toolResponse.responseData(), 1000);
                sb.append("\n [log] TOOL-RESPONSE: ").append(tr);
            }
        } else if (lastMessage.getMessageType() == MessageType.USER) {
            if (StringUtils.hasText(lastMessage.getText())) {
                sb.append("\n [log] TEXT: ").append(first(lastMessage.getText(), 1000));
            }
        }

        log.debug("[log] before: {}", sb);
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        StringBuilder sb = new StringBuilder("\nASSISTANT: ");

        if (chatClientResponse.chatResponse() == null || chatClientResponse.chatResponse().getResults() == null) {
            sb.append(" [log] No chat response ");
            log.debug("[log] after: {}", sb);
            return chatClientResponse;
        }

        for (var generation : chatClientResponse.chatResponse().getResults()) {
            var message = generation.getOutput();
            if (message.getToolCalls() != null) {
                for (var toolCall : message.getToolCalls()) {
                    sb.append("\n [log] TOOL-CALL: ")
                            .append(toolCall.name())
                            .append(" (")
                            .append(toolCall.arguments())
                            .append(")");
                }
            }

            if (message.getText() != null) {
                if (StringUtils.hasText(message.getText())) {
                    sb.append("\n [log] TEXT: ").append(first(message.getText(), 1200));
                }
            }
        }

        log.debug("[log] after: {}", sb.toString().replaceAll("\n", "\t"));
        return chatClientResponse;
    }

    private String first(String text, int n) {
        if (text.length() <= n) {
            return text;
        }
        return text.substring(0, n) + "...";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int order = 0;

        private boolean showSystemMessage = true;

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
