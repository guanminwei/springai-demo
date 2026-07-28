package com.git.hui.springai.advance.mem;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.springframework.ai.chat.model.ChatModel;

/**
 * 基于 Langgraph4j 的记忆 Agent 封装类
 * <p>
 * 本类封装了 Langgraph4j 的 {@link StateGraph} 和 {@link CompiledGraph}，
 * 提供带对话记忆能力的 Agent 工作流。通过 {@link BaseCheckpointSaver} 实现状态持久化，
 * 使得同一 threadId 下的多轮对话能够自动恢复上下文。
 * <p>
 * 核心组件：
 * <ul>
 *     <li>{@link StateGraph} - 定义 Agent 的状态图（节点 + 边），描述工作流拓扑</li>
 *     <li>{@link CompiledGraph} - 编译后的可执行工作流，支持 invoke/stream 调用</li>
 *     <li>{@link BaseCheckpointSaver} - 检查点保存器，负责持久化每步执行状态</li>
 *     <li>{@link MemorySaver} - 内存级别的 Checkpoint 实现（重启后丢失）</li>
 * </ul>
 * <p>
 * 使用方式：
 * <pre>
 * MemAgent agent = new MemAgent(chatModel);
 * CompiledGraph&lt;AgentExecutor.State&gt; workflow = agent.workflow();
 * // 通过 threadId 区分会话
 * var config = RunnableConfig.builder().threadId("user001").build();
 * var state = workflow.invoke(Map.of("messages", new UserMessage("你好")), config);
 * </pre>
 *
 * @author YiHui
 * @date 2025/8/8
 * @see AgentExecutor
 * @see MemorySaver
 * @see CompileConfig
 */
public class MemAgent {
    private final StateGraph<AgentExecutor.State> graph;
    private final CompiledGraph<AgentExecutor.State> workflow;

    public MemAgent(ChatModel model) throws GraphStateException {
        this(model, new MemorySaver());
    }

    public MemAgent(ChatModel model, BaseCheckpointSaver memorySaver) throws GraphStateException {
        this.graph = AgentExecutor.builder().chatModel(model).build();
        this.workflow = graph.compile(CompileConfig.builder().checkpointSaver(memorySaver).build());
    }

    public CompiledGraph<AgentExecutor.State> workflow() {
        return workflow;
    }

    public CompiledGraph<AgentExecutor.State> newWorkflow(CompileConfig config) throws GraphStateException {
        return graph.compile(config);
    }

    public CompiledGraph<AgentExecutor.State> newWorkflow(MemorySaver memory) throws GraphStateException {
        return graph.compile(
                CompileConfig.builder().checkpointSaver(memory).build()
        );
    }
}
