package com.git.hui.springai.advance.advisor;

import com.git.hui.springai.advance.service.MemoryRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 长期记忆 Advisor — 在对话前自动检索并注入历史记忆到 System Prompt
 * <p>
 * 实现 {@link BaseAdvisor} 接口，在 {@code before()} 阶段：
 * <ol>
 *     <li>从请求上下文中获取 userId（conversationId）</li>
 *     <li>提取用户最新的查询内容</li>
 *     <li>调用 {@link MemoryRetrievalService} 检索相关长期记忆</li>
 *     <li>将格式化的记忆文本（带时间警告）追加到 System Prompt</li>
 * </ol>
 * <p>
 * 与 {@link org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor} 组成双层 Advisor：
 * <ul>
 *     <li>MessageChatMemoryAdvisor（order=100）：先加载短期对话历史</li>
 *     <li>LongTermMemoryAdvisor（order=200）：再注入长期记忆</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/7/29
 * @see MemoryRetrievalService
 * @see BaseAdvisor
 */
public class LongTermMemoryAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryAdvisor.class);

    private final MemoryRetrievalService retrievalService;
    private final int order;

    public LongTermMemoryAdvisor(MemoryRetrievalService retrievalService) {
        this(retrievalService, 200);
    }

    public LongTermMemoryAdvisor(MemoryRetrievalService retrievalService, int order) {
        this.retrievalService = retrievalService;
        this.order = order;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 1. 获取用户标识（从 Advisor 上下文中取 conversationId）
        String userId = (String) request.context().get(ChatMemory.CONVERSATION_ID);
        if (userId == null || userId.isBlank()) {
            userId = "default";
        }

        // 2. 提取用户最新查询内容
        String userQuery = extractUserQuery(request);
        if (userQuery == null || userQuery.isBlank()) {
            return request;
        }

        // 3. 检索长期记忆
        String memoryText = retrievalService.retrieveAndFormat(userId, userQuery);
        if (memoryText.isEmpty()) {
            log.debug("用户 [{}] 无相关长期记忆", userId);
            return request;
        }

        log.debug("为用户 [{}] 注入长期记忆: {} 字符", userId, memoryText.length());

        // 4. 将记忆追加到 System Prompt
        return injectMemory(request, memoryText);
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        // 长期记忆 Advisor 不需要修改响应
        return response;
    }

    /**
     * 从请求中提取用户最新的查询文本
     */
    private String extractUserQuery(ChatClientRequest request) {
        List<Message> instructions = request.prompt().getInstructions();
        if (instructions == null || instructions.isEmpty()) {
            return null;
        }

        // 从后往前找最后一条 UserMessage
        for (int i = instructions.size() - 1; i >= 0; i--) {
            Message msg = instructions.get(i);
            if (msg instanceof UserMessage) {
                return msg.getText();
            }
        }
        return null;
    }

    /**
     * 将记忆文本注入到请求的 System Prompt 中
     */
    private ChatClientRequest injectMemory(ChatClientRequest request, String memoryText) {
        // 使用 Prompt.augmentSystemMessage 追加记忆文本到 System Prompt
        org.springframework.ai.chat.prompt.Prompt augmentedPrompt = request.prompt().augmentSystemMessage(memoryText);
        return request.mutate().prompt(augmentedPrompt).build();
    }
}
