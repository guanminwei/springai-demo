package com.git.hui.springai.advance.mvc;

import com.git.hui.springai.advance.mem.MemAgent;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Content;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 对话控制器 - 基于 Langgraph4j Agent 的聊天接口
 * <p>
 * 本控制器使用 Langgraph4j 的 {@link CompiledGraph} 工作流处理用户请求，
 * 通过 {@link RunnableConfig} 中的 threadId 实现多用户会话隔离和上下文记忆。
 * <p>
 * 接口说明：
 * <pre>
 * GET /{user}/chat?msg=你好
 * - user: 路径参数，作为 threadId 区分不同会话
 * - msg:  查询参数，用户输入的消息内容
 * </pre>
 * <p>
 * 工作流程：
 * <ol>
 *     <li>接收用户消息，构建 UserMessage</li>
 *     <li>通过 RunnableConfig 指定 threadId（即 user），关联会话上下文</li>
 *     <li>调用 workflow.invoke() 执行 Agent 工作流（含 LLM 推理 + 工具调用）</li>
 *     <li>MemorySaver 自动保存本轮状态，下次同 threadId 请求时自动恢复</li>
 *     <li>从最终 State 中提取 lastMessage 作为响应返回</li>
 * </ol>
 *
 * @author YiHui
 * @date 2025/8/5
 * @see MemAgent
 * @see CompiledGraph
 * @see AgentExecutor.State
 */
@RestController
public class ChatController {
    private final CompiledGraph<AgentExecutor.State> workflow;

    /**
     * 构造方法 - 初始化 Langgraph4j Agent 工作流
     *
     * @param chatModel Spring AI 自动配置的聊天模型
     * @throws GraphStateException 图构建异常（如节点/边定义不合法）
     */
    public ChatController(ChatModel chatModel) throws GraphStateException {
        this.workflow = new MemAgent(chatModel).workflow();
    }

    /**
     * 聊天对话接口 - 基于 Langgraph4j Agent 的多轮对话
     * <p>
     * 通过路径参数 user 作为 threadId，实现会话级别的上下文隔离。
     * Langgraph4j 的 MemorySaver 会自动保存/恢复每个 threadId 的状态。
     *
     * @param user 用户标识，作为 threadId 区分不同会话
     * @param msg  用户输入的消息内容
     * @return AI Agent 的最终回复文本，若无响应则返回 "No Response"
     */
    @GetMapping("/{user}/chat")
    public Object chat(@PathVariable String user, String msg) {
        var runnableConfig = RunnableConfig.builder().threadId(user).build();
        var state = workflow.invoke(Map.of("messages", new UserMessage(msg)), runnableConfig).orElseThrow();

        return state.lastMessage().map((Content::getText)).orElse("No Response");
    }
}
