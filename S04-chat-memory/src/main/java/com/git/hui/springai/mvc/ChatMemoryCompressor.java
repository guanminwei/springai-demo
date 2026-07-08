package com.git.hui.springai.mvc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * 会话上下文压缩器。
 *
 * <p>当对话历史过多时，使用 LLM 将较早的消息摘要为一条总结消息，
 * 从而在保留关键上下文的同时大幅减少消息数量，避免注意力分散和 Token 超限。</p>
 *
 * <h3>压缩策略</h3>
 * <pre>{@code
 * 压缩前（假设 threshold=10，当前 16 条消息）:
 *   [User1, Asst1, User2, Asst2, ..., User8, Asst8]
 *
 * 压缩后:
 *   [SummaryMsg("前6轮对话摘要: ..."), User7, Asst7, User8, Asst8]
 * }</pre>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>检查指定会话的消息数量是否超过压缩阈值</li>
 *   <li>将消息分为"待压缩"（较早的一半）和"保留"（最近的一半）两组</li>
 *   <li>调用 LLM 对待压缩消息生成摘要</li>
 *   <li>清除原始记忆，先写入摘要消息，再写回保留的近期消息</li>
 * </ol>
 *
 * @author YiHui
 * @date 2025/7/14
 * @see ChatMemory
 */
public class ChatMemoryCompressor {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryCompressor.class);

    /** 触发压缩的消息数量阈值：当消息数超过此值时执行压缩 */
    private final int compressThreshold;

    /** 压缩后保留的最近消息数量（必须为偶数，保证 user/assistant 成对保留） */
    private final int keepRecentCount;

    private final ChatModel chatModel;
    private final ChatMemory chatMemory;

    public ChatMemoryCompressor(ChatModel chatModel, ChatMemory chatMemory,
                                int compressThreshold, int keepRecentCount) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.compressThreshold = compressThreshold;
        // 确保保留数量为偶数
        this.keepRecentCount = (keepRecentCount / 2) * 2;
    }

    /**
     * 默认构造器：阈值 20 条消息时压缩，保留最近 10 条。
     */
    public ChatMemoryCompressor(ChatModel chatModel, ChatMemory chatMemory) {
        this(chatModel, chatMemory, 20, 10);
    }

    /**
     * 检查并在需要时自动压缩指定会话的上下文。
     *
     * <p>当消息数量超过 {@code compressThreshold} 时，自动触发压缩流程。
     * 此方法可在每次对话前调用，实现"上下文过长时自动压缩"。</p>
     *
     * @param conversationId 会话ID
     * @return true 表示执行了压缩，false 表示未达到阈值无需压缩
     */
    public boolean compressIfNeeded(String conversationId) {
        List<Message> messages = chatMemory.get(conversationId);
        if (messages.size() <= compressThreshold) {
            return false;
        }
        log.info("会话 [{}] 消息数 {} 超过阈值 {}，触发自动压缩", conversationId, messages.size(), compressThreshold);
        doCompress(conversationId, messages);
        return true;
    }

    /**
     * 手动触发压缩指定会话的上下文（无论消息数量是否超过阈值）。
     *
     * <p>适用于用户主动请求清理上下文的场景。
     * 如果消息数量少于 4 条（不足以拆分），则跳过压缩。</p>
     *
     * @param conversationId 会话ID
     * @return true 表示压缩成功，false 表示消息太少无需压缩
     */
    public boolean manualCompress(String conversationId) {
        List<Message> messages = chatMemory.get(conversationId);
        if (messages.size() < 4) {
            log.info("会话 [{}] 消息数 {} 太少，跳过压缩", conversationId, messages.size());
            return false;
        }
        log.info("手动触发会话 [{}] 压缩，当前消息数 {}", conversationId, messages.size());
        doCompress(conversationId, messages);
        return true;
    }

    /**
     * 获取指定会话当前的历史消息数量。
     *
     * @param conversationId 会话ID
     * @return 消息数量
     */
    public int getHistoryCount(String conversationId) {
        return chatMemory.get(conversationId).size();
    }

    /**
     * 执行实际的压缩逻辑。
     *
     * <p>将消息列表拆分为两部分：
     * <ul>
     *   <li><b>待压缩部分</b>：除最近 {@code keepRecentCount} 条之外的所有早期消息</li>
     *   <li><b>保留部分</b>：最近 {@code keepRecentCount} 条消息</li>
     * </ul>
     * 然后调用 LLM 生成待压缩部分的摘要，最后重写记忆。</p>
     */
    private void doCompress(String conversationId, List<Message> messages) {
        // 计算拆分点：保留最近 keepRecentCount 条，其余压缩
        int splitIndex = Math.max(messages.size() - keepRecentCount, 0);
        List<Message> toCompress = messages.subList(0, splitIndex);
        List<Message> toKeep = messages.subList(splitIndex, messages.size());

        if (toCompress.isEmpty()) {
            log.info("会话 [{}] 无需压缩的消息", conversationId);
            return;
        }

        // 1. 构建待压缩消息的文本
        StringBuilder sb = new StringBuilder();
        for (Message msg : toCompress) {
            String role = (msg instanceof AssistantMessage) ? "助手" : "用户";
            sb.append(role).append(": ").append(msg.getText()).append("\n");
        }

        // 2. 调用 LLM 生成摘要
        String summaryPrompt = String.format(
                "请对以下对话历史进行简洁的摘要总结，保留关键信息和上下文要点，" +
                        "以便后续对话能基于此摘要继续。摘要应简明扼要，不超过300字。\n\n对话历史:\n%s",
                sb.toString()
        );

        String summary;
        try {
            summary = chatModel.call(new Prompt(summaryPrompt)).getResult().getOutput().getText();
            log.info("会话 [{}] 压缩摘要生成成功，原文 {} 字符 → 摘要 {} 字符",
                    conversationId, sb.length(), summary.length());
        } catch (Exception e) {
            log.error("会话 [{}] 压缩摘要生成失败，跳过压缩", conversationId, e);
            return;
        }

        // 3. 清除原始记忆并重写
        chatMemory.clear(conversationId);

        // 先写入摘要消息（作为 UserMessage 注入，模型会将其视为上下文）
        String summaryText = "[历史对话摘要] " + summary;
        chatMemory.add(conversationId, new UserMessage(summaryText));

        // 再写回保留的近期消息
        if (!toKeep.isEmpty()) {
            chatMemory.add(conversationId, toKeep);
        }

        log.info("会话 [{}] 压缩完成: {} 条消息 → 1 条摘要 + {} 条近期消息",
                conversationId, messages.size(), toKeep.size());
    }
}
