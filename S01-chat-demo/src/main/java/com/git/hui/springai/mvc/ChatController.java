package com.git.hui.springai.mvc;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 基于大模型访问的聊天接口
 *
 * @author YiHui
 * @date 2025/7/11
 */
@RestController
public class ChatController {

    private final ZhiPuAiChatModel chatModel;

    @Autowired
    public ChatController(ZhiPuAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @GetMapping("/ai/generate")
    public Map generate(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        return Map.of("generation", chatModel.call(message));
    }

    /**
     * 流式接口
     * produces = "text/event-stream" 告诉浏览器：这不是普通的 JSON 响应，而是 SSE（Server-Sent Events）流式响应。
     * 数据会像"打字机"一样，一个字一个字地推送到浏览器，而不是等全部生成完再返回。
     * 适合用于生成内容较长，需要实时查看生成进度的场景。
     * flux：响应式流，用于返回多个响应式数据，这里用于返回流式数据
     * @param message
     * @return
     */
    @GetMapping(value = "/ai/generateStream", produces = "text/event-stream")
    public Flux<ChatResponse> generateStream(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        // 将用户输入的文本消息封装为 Prompt 对象，供大模型调用使用
        /**
         * 为什么要套两层？因为 Prompt 除了用户消息，还可以包含系统设定、多个对话历史等，是一个更完整的结构。
         */
        var prompt = new Prompt(new UserMessage(message));
        return chatModel.stream(prompt);
    }
}