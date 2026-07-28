package com.git.hui.springai.mvc;

import com.git.hui.springai.advisor.MyLoggingAdvisor;
import com.git.hui.springai.tools.QuizTools;
import com.git.hui.springai.tools.ToolResponseType;
import com.git.hui.springai.tools.WeatherTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 手动工具执行控制器 —— 演示 Spring AI 中工具调用（Function Calling）的两种执行模式。
 *
 * <p>本控制器对比展示了以下两种模式：</p>
 * <ul>
 *   <li><b>自动执行模式</b>（{@link #aiExecuteTools}）：由 Spring AI 框架自动完成工具调用与结果回填，
 *       开发者无需干预执行过程，适用于大多数标准场景。</li>
 *   <li><b>手动执行模式</b>（{@link #qa}）：禁用框架的自动执行，由开发者自行遍历工具调用请求、
 *       匹配并执行对应工具、收集执行结果后再回传给大模型进行最终总结。
 *       适用于需要在工具执行前后插入自定义逻辑（如权限校验、日志审计、结果加工）的高级场景。</li>
 * </ul>
 *
 * <p>核心设计要点：</p>
 * <ul>
 *   <li>通过 {@link ToolCallingChatOptions#internalToolExecutionEnabled} 控制执行权归属</li>
 *   <li>通过 {@link ToolContext} 向工具方法透传业务上下文（sessionId、userId 等）</li>
 *   <li>通过反射获取 {@link MethodToolCallback} 内部的 toolMethod，
 *       进而读取自定义注解 {@link ToolResponseType} 获取工具返回类型元数据</li>
 * </ul>
 *
 * @author YiHui
 * @date 2026/3/6
 * @see QuizTools
 * @see WeatherTools
 * @see ToolResponseType
 * @see MyLoggingAdvisor
 */
@Slf4j
@RestController
public class QaController {
    /** 知识问答工具实例，提供 createQuiz 等 @Tool 方法 */
    private QuizTools quizTools;
    /** 天气查询工具实例，提供 queryWeather 等 @Tool 方法 */
    private WeatherTools weatherTools;

    /** 聚合所有工具回调（QuizTools + WeatherTools），用于在请求时注册给 ChatClient */
    private final List<ToolCallback> tools;
    /** Spring AI 聊天客户端，已预配置日志 Advisor 链 */
    private final ChatClient chatClient;

    /**
     * 构造方法 —— 完成工具注册与 ChatClient 初始化。
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>注入 QuizTools、WeatherTools 两个工具 Bean</li>
     *   <li>构建 ChatClient 并挂载两个 Advisor：
     *       {@link SimpleLoggerAdvisor}（Spring AI 内置简洁日志）+
     *       {@link MyLoggingAdvisor}（自定义增强日志，展示系统消息与可用工具列表）</li>
     *   <li>通过 {@link MethodToolCallbackProvider} 扫描工具对象上的 @Tool 注解方法，
     *       将其转换为 {@link ToolCallback} 列表统一管理</li>
     * </ol>
     *
     * @param quizTools           知识问答工具 Bean
     * @param weatherTools        天气查询工具 Bean
     * @param chatClientBuilder   Spring AI 自动配置的 ChatClient 构建器
     */
    public QaController(QuizTools quizTools, WeatherTools weatherTools, ChatClient.Builder chatClientBuilder) {
        this.quizTools = quizTools;
        this.weatherTools = weatherTools;

        // 构建 ChatClient，注册 Advisor 链：
        // - SimpleLoggerAdvisor: 记录请求/响应的简洁日志
        // - MyLoggingAdvisor: 自定义日志，可展示 SystemMessage 和可用工具列表
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor(),
                        MyLoggingAdvisor.builder()
                                .showSystemMessage(true).showAvailableTools(true).build())
                .build();

        // 通过 MethodToolCallbackProvider 反射扫描 @Tool 注解方法，生成 ToolCallback 数组
        ToolCallback[] t1 = MethodToolCallbackProvider.builder()
                .toolObjects(quizTools)       // 扫描 QuizTools 中的 @Tool 方法
                .build()
                .getToolCallbacks();
        ToolCallback[] t2 = MethodToolCallbackProvider.builder()
                .toolObjects(weatherTools)    // 扫描 WeatherTools 中的 @Tool 方法
                .build()
                .getToolCallbacks();

        // 合并所有工具回调到统一列表，后续请求时一次性注册给 ChatClient
        tools = new ArrayList<>();
        tools.addAll(List.of(t1));
        tools.addAll(List.of(t2));
    }

    /**
     * 自动工具执行接口 —— 演示 Spring AI 框架自动完成工具调用的标准模式。
     *
     * <p>工作原理：设置 {@code internalToolExecutionEnabled(true)}，Spring AI 在收到大模型返回的
     * tool_calls 指令后，会自动执行所有被请求的工具，将执行结果回填给大模型，
     * 最终由大模型组装完整答案返回。整个过程对开发者透明，一次 HTTP 调用即可完成。</p>
     *
     * <p>适用场景：不需要在工具执行前后插入自定义逻辑的标准 Function Calling 场景。</p>
     *
     * @param msg 用户输入的自然语言消息（如"帮我查一下北京的天气"）
     * @return 大模型组装后的最终回复文本
     */
    @RequestMapping(path = "executeTools")
    public String aiExecuteTools(String msg) {
        // ========== 1. 构建工具执行上下文 ==========
        // ToolContext 会透传给每个被调用的工具方法，工具内部可通过 ToolContext 参数获取这些业务信息
        Map<String, Object> toolContextData = new HashMap<>();
        toolContextData.put("sessionId", "demo-session-123");  // 会话标识，用于链路追踪
        toolContextData.put("userId", "demo-user-456");        // 用户标识，用于权限/审计
        toolContextData.put("timestamp", System.currentTimeMillis()); // 调用时间戳

        // ========== 2. 配置自动执行模式 ==========
        // internalToolExecutionEnabled(true): 框架自动执行工具 → 自动回填结果 → 大模型总结
        // 如果大模型一次性请求调用多个工具，Spring AI 会全部执行后再统一返回给大模型
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(true)  // 启用框架自动执行
                .toolContext(toolContextData)         // 注入业务上下文
                .build();

        // ========== 3. 发起调用并获取最终结果 ==========
        // toolCallbacks 注册本次请求可用的工具集合；call() 内部会自动处理多轮工具调用
        Prompt prompt = new Prompt(msg, options);
        return chatClient.prompt(prompt).toolCallbacks(this.tools).call().content();
    }

    /**
     * 手动工具执行接口 —— 演示开发者自行控制工具调用全流程的高级模式。
     *
     * <p>工作原理：设置 {@code internalToolExecutionEnabled(false)} 禁用框架自动执行，
     * 整个工具调用分为两个阶段：</p>
     * <ol>
     *   <li><b>第 1 次调用</b>：将用户消息发送给大模型，大模型返回 tool_calls 请求（而非最终文本）。
     *       开发者自行遍历 tool_calls，匹配本地注册的 ToolCallback 并手动执行。</li>
     *   <li><b>第 2 次调用</b>：将工具执行结果封装为 {@link ToolResponseMessage}，
     *       连同原始用户消息和 AI 的工具调用请求一起回传给大模型，由大模型生成最终总结。</li>
     * </ol>
     *
     * <p>手动模式的优势：可在工具执行前进行权限校验、参数改写；执行后可进行结果加工、
     * 审计日志记录；还可通过反射读取工具方法上的自定义注解（如 {@link ToolResponseType}）
     * 实现动态路由或前端渲染类型提示。</p>
     * 
     * 优点总结
        优点	说明
        完全控制执行流程	工具调用前后可插入任意自定义逻辑（鉴权、日志、限流等）
        结果可干预	可对工具返回值进行修改、过滤、脱敏后再回传大模型
        元数据可读	通过反射获取工具方法上的注解信息，实现动态路由或前端渲染提示
        上下文透传	通过 ToolContext 向工具传递 sessionId、userId 等业务信息
        灵活度高	可实现条件执行、跳过执行、替换执行等高级策略

        核心流程分三个阶段：
    第一次调用大模型 — 大模型不直接回答，而是返回 tool_calls 指令（要调用哪些工具、传什么参数）
    手动执行工具 — 开发者自行匹配工具、执行调用、读取注解元数据、封装结果
    第二次调用大模型 — 将 用户问题 → AI工具调用请求 → 工具执行结果 完整消息链回传，大模型基于工具结果生成最终总结
    与自动模式相比，关键区别在于阶段 2 完全由开发者控制，可以在工具执行前后插入任意自定义逻辑。
     * 
        sequenceDiagram
        participant User as 用户
        participant Controller as QaController
        participant ChatClient as ChatClient
        participant LLM as 大模型
        participant Tools as 工具(QuizTools/WeatherTools)

        User->>Controller: 请求 /qa?msg=xxx
        Controller->>Controller: 构建 ToolContext(sessionId, userId, timestamp)
        Controller->>Controller: 设置 internalToolExecutionEnabled(false)

        rect rgb(240, 248, 255)
            Note over Controller,LLM: 第 1 次调用：获取工具调用请求
            Controller->>ChatClient: prompt(msg).toolCallbacks(tools).call()
            ChatClient->>LLM: 发送用户消息 + 可用工具列表
            LLM-->>ChatClient: 返回 tool_calls(工具名+参数)
            ChatClient-->>Controller: ChatResponse(AssistantMessage含tool_calls)
        end

        Controller->>Controller: 判断是否有 tool_calls

        alt 无 tool_calls
            Controller-->>User: 直接返回文本回复
        end

        rect rgb(255, 248, 240)
            Note over Controller,Tools: 手动执行工具阶段
            loop 遍历每个 tool_call
                Controller->>Controller: 匹配同名 ToolCallback
                Controller->>Tools: callback.call(arguments, toolContext)
                Tools-->>Controller: 返回工具执行结果(toolRsp)

                Controller->>Controller: 反射读取 @ToolResponseType 注解
                Controller->>Controller: 记录工具定义信息(description)
                Controller->>Controller: 封装 ToolResponse(callId, name, toolRsp)
            end
        end

        rect rgb(240, 255, 240)
            Note over Controller,LLM: 第 2 次调用：回传工具结果，生成最终总结
            Controller->>Controller: 构建消息链: UserMessage + AssistantMessage + ToolResponseMessage
            Controller->>ChatClient: prompt(消息链).call()
            ChatClient->>LLM: 发送完整上下文(问题+工具调用+工具结果)
            LLM-->>ChatClient: 返回最终自然语言总结
            ChatClient-->>Controller: ChatResponse(最终文本)
        end

        Controller-->>User: 返回最终总结文本
     *
     * <p>注意事项：注册的工具列表中不能存在同名工具，否则会抛出
     * {@code IllegalStateException: Multiple tools with the same name}。</p>
     *
     * @param msg 用户输入的自然语言消息（如"出一道关于AI的选择题"）
     * @return 大模型基于工具执行结果生成的最终总结文本
     */
    @RequestMapping(path = "qa")
    public String qa(String msg) {
        // ========== 1. 构建工具执行上下文 ==========
        // 与自动模式相同，ToolContext 会在手动执行时传递给 ToolCallback.call() 方法
        Map<String, Object> toolContextData = new HashMap<>();
        toolContextData.put("sessionId", "demo-session-123");  // 会话标识
        toolContextData.put("userId", "demo-user-456");        // 用户标识
        toolContextData.put("timestamp", System.currentTimeMillis()); // 调用时间戳

        // ========== 2. 配置手动执行模式 ==========
        // internalToolExecutionEnabled(false): 框架不自动执行工具，大模型仅返回 tool_calls 指令
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false)  // 禁用自动执行，由开发者手动控制
                .toolContext(toolContextData)
                .build();

        // ========== 3. 第 1 次调用：获取大模型的工具调用请求 ==========
        Prompt prompt = new Prompt(msg, options);
        ChatResponse chatResponse = chatClient.prompt(prompt).toolCallbacks(this.tools).call().chatResponse();
        AssistantMessage assistantMessage = chatResponse.getResult().getOutput();

        // 判断大模型是否请求了工具调用；若无 tool_calls，说明大模型直接给出了文本回复
        if (CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
            log.info("非工具调用结果：{}", assistantMessage.getText());
            return assistantMessage.getText();
        }

        // ========== 4. 手动执行工具 ==========
        // 遍历大模型返回的每一个 tool_call，在本地工具列表中匹配同名工具并执行
        // 注意：不能有同名工具，否则会报错
        List<ToolResponseMessage.ToolResponse> list = new ArrayList<>();
        for (var call : assistantMessage.getToolCalls()) {
            for (ToolCallback callback : tools) {
                if (callback.getToolDefinition().name().equals(call.name())) {
                    // 执行工具：传入大模型生成的 JSON 参数 + 业务上下文
                    var toolRsp = callback.call(call.arguments(), new ToolContext(toolContextData));

                    // 将工具执行结果封装为 ToolResponse，保留 callId 以便大模型关联上下文
                    ToolResponseMessage.ToolResponse toolResponse =
                            new ToolResponseMessage.ToolResponse(call.id(), call.name(), toolRsp);
                    list.add(toolResponse);

                    // ========== 5. （扩展）通过反射读取工具方法的自定义注解 ==========
                    // MethodToolCallback 内部持有 toolMethod 字段（private），需反射获取
                    if (callback instanceof MethodToolCallback) {
                        var target = ((MethodToolCallback) callback);
                        // 反射获取 MethodToolCallback 的私有字段 toolMethod
                        Field field = ReflectionUtils.findField(target.getClass(), "toolMethod");
                        field.setAccessible(true);
                        var method = (Method) ReflectionUtils.getField(field, target);
                        if (method != null) {
                            // 读取工具方法上的 @ToolResponseType 注解，获取返回类型元数据
                            // 实际应用中可据此决定前端渲染方式（如 card → 卡片组件，quiz → 答题组件）
                            var rspType = method.getDeclaredAnnotation(ToolResponseType.class);
                            log.info("工具方法定义信息：{}", rspType);
                        }
                    }

                    // 打印工具的 description 定义信息（来源于 @Tool 注解的 description 属性）
                    log.info("工具定义信息：{}", callback.getToolDefinition().description());
                }
            }
        }

        // ========== 6. 第 2 次调用：将工具结果回传大模型生成最终总结 ==========
        // 构建完整的消息链：UserMessage → AssistantMessage(tool_calls) → ToolResponseMessage
        ToolResponseMessage toolMsg = ToolResponseMessage.builder().responses(list).build();

        ChatResponse finalResponse = chatClient.prompt(
                        new Prompt(List.of(
                                new UserMessage(msg),           // 用户原始问题
                                assistantMessage,            // AI 的工具调用请求（含 tool_calls）
                                toolMsg                      // 工具执行结果集合
                        )))
                .call()
                .chatResponse();

        // 返回大模型基于工具结果生成的最终自然语言总结
        return finalResponse.getResult().getOutput().getText();
    }
}
