package com.git.hui.springai.advance.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 对话控制器 — 带双层记忆的对话接口
 * <p>
 * 通过 {@link ChatClient} 与大模型交互，ChatClient 已配置：
 * <ul>
 *     <li>短期记忆 Advisor（MessageChatMemoryAdvisor）：自动管理对话上下文</li>
 *     <li>长期记忆 Advisor（LongTermMemoryAdvisor）：自动检索并注入历史记忆</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/7/29
 */
@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 带双层记忆的对话接口
     * <p>
     * user 参数作为 conversationId，区分不同用户/会话。
     * 同一 user 的多轮对话会保持短期上下文，同时自动注入长期记忆。
     *
     * @param user 用户标识，作为 conversationId
     * @param msg  用户输入的消息内容
     * @return AI 模型的回复文本
     */
    @GetMapping("/{user}/generate")
    public Object generate(@PathVariable String user, @RequestParam String msg) {
        return chatClient.prompt()
                .user(msg)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user))
                .call()
                .content();
    }
}
