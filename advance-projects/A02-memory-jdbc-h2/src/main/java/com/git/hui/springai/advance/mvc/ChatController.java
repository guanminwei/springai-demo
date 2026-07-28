package com.git.hui.springai.advance.mvc;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 对话控制器 - 提供带记忆能力的聊天接口（H2 存储版）
 * <p>
 * 本控制器通过 {@link ChatClient} 与大模型交互，并集成以下 Advisor 增强能力：
 * <ul>
 *     <li>{@link MessageChatMemoryAdvisor} - 自动管理对话上下文，将历史消息注入 Prompt</li>
 *     <li>{@link SimpleLoggerAdvisor} - 记录请求/响应日志，便于调试和观测</li>
 * </ul>
 * <p>
 * 接口说明：
 * <pre>
 * GET /{user}/chat?msg=你好
 * - user: 路径参数，作为会话 ID（conversationId），不同 user 拥有独立的对话上下文
 * - msg:  查询参数，用户输入的消息内容
 * </pre>
 * <p>
 * 注意事项：
 * <ul>
 *     <li>H2 为内存数据库，服务重启后对话历史将丢失（仅适用于开发/测试环境）</li>
 *     <li>生产环境建议使用 MySQL/PostgreSQL 等持久化数据库（参见 A01 模块）</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/8/5
 * @see MessageChatMemoryAdvisor
 * @see ChatMemory
 */
@RestController
public class ChatController {
    private final ChatClient chatClient;

    /**
     * 构造方法 - 初始化 ChatClient 并注册默认 Advisor
     *
     * @param chatModel  Spring AI 自动配置的聊天模型（如 DashScope、OpenAI 等）
     * @param chatMemory 对话记忆组件（由 MemConfig 配置，底层为 H2 内存数据库）
     */
    public ChatController(ChatModel chatModel, ChatMemory chatMemory) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor())
                .build();
    }

    /**
     * 聊天对话接口 - 支持多轮上下文记忆
     * <p>
     * 通过路径参数 user 区分不同会话，每个 user 拥有独立的对话历史。
     * 对话记录存储在 H2 内存数据库中，服务重启后数据丢失。
     *
     * @param user 用户标识，作为 conversationId 区分不同会话
     * @param msg  用户输入的消息内容
     * @return AI 模型的回复文本
     */
    @GetMapping("/{user}/chat")
    public Object chat(@PathVariable String user, String msg) {
        return chatClient.prompt().user(msg)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user))
                .call().content();
    }
}
