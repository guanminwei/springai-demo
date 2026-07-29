package com.git.hui.springai.advance.config;

import com.git.hui.springai.advance.advisor.LongTermMemoryAdvisor;
import com.git.hui.springai.advance.service.MemoryRetrievalService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置类 — 装配双层 Advisor 记忆架构
 * <p>
 * 构建带有短期记忆 + 长期记忆双层 Advisor 的 ChatClient Bean：
 * <ul>
 *     <li>{@link MessageChatMemoryAdvisor}（order=100）：短期对话历史管理</li>
 *     <li>{@link LongTermMemoryAdvisor}（order=200）：长期记忆检索注入</li>
 *     <li>{@link SimpleLoggerAdvisor}：请求/响应日志</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/7/29
 */
@Configuration
public class ChatClientConfig {

    /**
     * 短期记忆 Bean（内存级，用于演示；生产环境可替换为 Redis/MySQL 实现）
     */
    @Bean
    public ChatMemory chatMemory() {
        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .build();
    }

    /**
     * 构建带双层 Advisor 的 ChatClient
     *
     * @param chatModel          AI 聊天模型
     * @param chatMemory         短期对话记忆
     * @param retrievalService   长期记忆检索服务
     * @return 配置完成的 ChatClient
     */
    @Bean
    public ChatClient chatClient(org.springframework.ai.chat.model.ChatModel chatModel,
                                 ChatMemory chatMemory,
                                 MemoryRetrievalService retrievalService) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        // 短期记忆：自动管理对话上下文
                        MessageChatMemoryAdvisor.builder(chatMemory).order(100).build(),
                        // 长期记忆：检索并注入历史记忆
                        new LongTermMemoryAdvisor(retrievalService, 200),
                        // 日志记录
                        new SimpleLoggerAdvisor()
                )
                .build();
    }
}
