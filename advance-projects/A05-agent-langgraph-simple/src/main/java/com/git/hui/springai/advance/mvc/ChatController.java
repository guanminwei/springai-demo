package com.git.hui.springai.advance.mvc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.git.hui.springai.advance.agents.WeatherRecommendAgent;
import com.git.hui.springai.advance.times.TimeWeatherTools;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Content;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 对话控制器 - 提供多种 Agent 调用方式的 REST 接口
 * <p>
 * 本控制器集成了三种不同的 AI 调用方式，便于对比学习：
 * <ul>
 *     <li>/chat - 通过 Langgraph4j AgentExecutor 工作流调用（支持工具调用 + 流式输出）</li>
 *     <li>/chat2 - 通过 Spring AI ChatClient 直接调用（自动工具调用）</li>
 *     <li>/recommend - 通过自定义 WeatherRecommendAgent 条件路由图调用</li>
 * </ul>
 * <p>
 * 接口说明：
 * <pre>
 * GET /chat?msg=北京现在几点       → AgentExecutor 工作流（自动调用时间工具）
 * GET /chat2?msg=上海天气怎么样     → ChatClient 直接调用（自动调用天气工具）
 * GET /recommend?area=北京         → 自定义旅游推荐 Agent（条件路由）
 * </pre>
 *
 * @author YiHui
 * @date 2025/8/5
 * @see AgentExecutor
 * @see WeatherRecommendAgent
 * @see TimeWeatherTools
 */
@RestController
public class ChatController {
    private final CompiledGraph<AgentExecutor.State> workflow;

    private final ChatClient chatClient;

    private final WeatherRecommendAgent weatherAgent;

    public ChatController(ChatModel chatModel) throws GraphStateException {
        workflow = AgentExecutor.builder()
                .chatModel(chatModel)
                .toolsFromObject(new TimeWeatherTools())
                .build()
                .compile();

        chatClient = ChatClient.builder(chatModel)
                .defaultTools(new TimeWeatherTools())
                .build();

        weatherAgent = new WeatherRecommendAgent(chatClient);
    }

    /**
     * 对象转 JSON 字符串（辅助方法）
     *
     * @param obj 待序列化对象
     * @return JSON 字符串
     */
    public String toStr(Object obj) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 通过 Langgraph4j AgentExecutor 工作流访问大模型
     * <p>
     * 使用 stream 方式逐步执行 Agent 工作流，打印每步状态变化。
     * AgentExecutor 会自动判断是否需要调用工具（如时间/天气查询），
     * 并在工具返回结果后继续推理直到得出最终答案。
     *
     * @param msg 用户输入的消息
     * @return Agent 的最终回复文本
     */
    @GetMapping("/chat")
    public Object chat(String msg) {
        AgentExecutor.State last = null;
        int i = 0;
        for (NodeOutput<AgentExecutor.State> item : workflow.stream(Map.of("messages", new UserMessage(msg)))) {
            System.out.println(item);
            last = item.state();
            System.out.printf("%02d : %s%n", i++, toStr(last.messages()));
        }

        // 返回最后一条消息
        return last.lastMessage().map(Content::getText).orElse("NoData");
    }

    /**
     * 通过 Spring AI ChatClient 直接调用大模型
     * <p>
     * 与 /chat 不同，本接口使用 ChatClient 的 prompt 方式直接调用大模型。
     * 大模型会自动判断是否需要调用已注册的工具（TimeWeatherTools），
     * 并自动执行工具调用循环直到得出最终答案。
     *
     * @param msg 用户输入的消息
     * @return 大模型的回复文本
     */
    @GetMapping("/chat2")
    public Object chat2(String msg) {
        return chatClient.prompt(msg).call().content();
    }

    /**
     * 旅游推荐接口 - 基于自定义 StateGraph 条件路由
     * <p>
     * 调用 WeatherRecommendAgent，根据输入地区的天气情况，
     * 通过条件路由图自动推荐户外或室内旅游项目。
     *
     * @param area 地区名称（如"北京"、"上海"）
     * @return 包含推荐结果的 Map（location, weather, outdoor/indoor_recommendations）
     */
    @GetMapping("/recommend")
    public Object recommend(String area) {
        return weatherAgent.recommendByLocation(area);
    }
}
