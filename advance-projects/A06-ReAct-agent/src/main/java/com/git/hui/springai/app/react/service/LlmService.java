package com.git.hui.springai.app.react.service;

import com.git.hui.springai.app.advisor.MyLoggingAdvisor;
import io.micrometer.common.util.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 服务工厂 - 管理不同模型的 ChatClient 实例
 * <p>
 * 本类负责创建和缓存 {@link ChatClient} 实例，支持按模型名称动态切换大模型。
 * 每个模型名称对应一个独立的 ChatClient，内部集成自定义 {@link MyLoggingAdvisor} 进行日志观测。
 * <p>
 * 设计要点：
 * <ul>
 *     <li>使用 ConcurrentHashMap 缓存 ChatClient，避免重复创建</li>
 *     <li>支持通过 modelName 参数指定不同的大模型（如 Qwen、GPT 等）</li>
 *     <li>默认模型为 "Qwen/Qwen2.5-7B-Instruct"</li>
 *     <li>每个 ChatClient 自动注册 MyLoggingAdvisor 用于调试观测</li>
 * </ul>
 * <p>
 * 使用场景：
 * <ul>
 *     <li>ReAct Agent 需要指定特定模型进行推理</li>
 *     <li>对比不同模型在 ReAct 任务中的表现</li>
 * </ul>
 *
 * @author YiHui
 * @date 2026/3/3
 * @see MyLoggingAdvisor
 * @see ChatClient
 */
@Service
public class LlmService {
    @Autowired
    private ChatModel chatModel;

    private Map<String, ChatClient> chatClientMap = new ConcurrentHashMap<>();

    /**
     * 获取指定模型的 ChatClient 实例
     * <p>
     * 若该模型名称对应的 ChatClient 已缓存则直接返回，否则创建新实例并缓存。
     * 每个 ChatClient 内部自动注册 MyLoggingAdvisor 用于调试观测。
     *
     * @param modelName 模型名称（如 "Qwen/Qwen2.5-7B-Instruct"），为空时使用默认模型
     * @return 配置好的 ChatClient 实例
     */
    public ChatClient getChatClient(String modelName) {
        if (StringUtils.isBlank(modelName)) {
            modelName = "Qwen/Qwen2.5-7B-Instruct";
        }

        if (chatClientMap.containsKey(modelName)) {
            return chatClientMap.get(modelName);
        }

        ChatClient client = ChatClient.builder(chatModel).defaultOptions(ChatOptions.builder().model(modelName).build())
                .defaultAdvisors(
                        // Custom logging advisor
                        MyLoggingAdvisor.builder()
                                .showAvailableTools(true)
                                .showSystemMessage(true)
                                .build())
                .build();
        chatClientMap.put(modelName, client);
        return client;
    }

}
