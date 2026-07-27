package com.git.hui.springai.app.mvc;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxResponse;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * MCP Client 聊天控制器。
 *
 * <p>本控制器展示了两种使用 MCP Server 工具的方式：</p>
 * <ol>
 *   <li><b>直接调用</b>：通过注入 {@link McpAsyncClient} 直接调用 MCP Server 暴露的工具，
 *       不经过大模型，适合调试和验证 MCP 服务可用性。</li>
 *   <li><b>AI 对话调用</b>：将 MCP Server 提供的工具注册到 {@link ChatClient}，
 *       由大模型在对话过程中自主决策并调用，实现 Function Calling 能力。</li>
 * </ol>
 *
 * <h3>架构说明</h3>
 * <pre>
 *   [浏览器] ──HTMX──▶ [ChatController] ──▶ [ChatClient]
 *                                                │
 *                                    ┌────────┴────────┐
 *                                    ▼                 ▼
 *                              [ChatModel]    [ToolCallbackProvider]
 *                              (智谱 GLM)           │
 *                                        ┌───────┴───────┐
 *                                        ▼               ▼
 *                                 [SSE MCP Server]  [STDIO MCP Server]
 *                                 (S07-时区查询)    (Selenium 爬虫)
 * </pre>
 *
 * <h3>关键依赖说明</h3>
 * <ul>
 *   <li>{@link ChatModel}：由 {@code spring-ai-starter-model-zhipuai} 自动配置，提供智谱 AI 聊天能力</li>
 *   <li>{@link ToolCallbackProvider}：由 {@code spring-ai-starter-mcp-client} 自动配置，
 *       汇总所有已连接 MCP Server 提供的工具，将其封装为 Spring AI 可识别的 ToolCallback</li>
 *   <li>{@link McpAsyncClient}：MCP 异步客户端，由 MCP Client Starter 根据 application.yml 配置自动创建，
 *       每个 MCP Server 连接对应一个实例，因此注入为 {@link List}</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/8/5
 * @see org.springframework.ai.tool.ToolCallbackProvider MCP 工具回调提供者
 * @see io.modelcontextprotocol.client.McpAsyncClient MCP 异步客户端
 */
@Controller
public class ChatController {

    /**
     * Spring AI 聊天客户端，封装了与大模型的交互逻辑。
     * <p>已配置 MCP 工具回调和 Advisor 链（会话记忆 + 日志记录）。</p>
     */
    private final ChatClient chatClient;

    /**
     * 所有已注册的 MCP 异步客户端列表。
     * <p>每个 MCP Server 连接会生成一个 {@link McpAsyncClient} 实例。
     * 当前配置了两个 MCP Server：
     * <ul>
     *   <li>SSE 模式：连接 S07-mcp-server（{@code localhost:8080/sse}），提供时区查询工具</li>
     *   <li>STDIO 模式：本地 Selenium MCP Server，提供网页数据抓取工具</li>
     * </ul>
     * </p>
     */
    @Autowired
    private List<McpAsyncClient> mcpClients;

    /**
     * 构造方法：初始化 ChatClient 并注册 MCP 工具。
     *
     * <p>构造过程完成以下工作：</p>
     * <ol>
     *   <li>打印当前通过 {@link ToolCallbackProvider} 注册的工具总数，便于调试确认</li>
     *   <li>调用 {@code defaultToolCallbacks} 将所有 MCP 工具注册为 ChatClient 的默认工具，
     *       大模型在对话时可通过 Function Calling 机制自主调用这些工具</li>
     *   <li>添加 {@link MessageChatMemoryAdvisor}：基于内存窗口的会话记忆 Advisor，
     *       维护多轮对话上下文，默认窗口大小为最近 20 条消息</li>
     *   <li>添加 {@link SimpleLoggerAdvisor}：请求/响应日志 Advisor，
     *       以 DEBUG 级别打印完整的 Prompt 和 Response 内容，方便调试</li>
     * </ol>
     *
     * @param chatModel          聊天模型实例，由 Spring Boot 自动配置（智谱 GLM-4.7-Flash）
     * @param toolCallbackProvider MCP 工具回调提供者，汇总所有 MCP Server 暴露的工具
     */
    public ChatController(ChatModel chatModel, ToolCallbackProvider toolCallbackProvider) {
        // 打印注册工具数量，用于启动时验证 MCP Server 工具是否正确加载
        System.out.println("当前注册的工具数量: " + toolCallbackProvider.getToolCallbacks().length);

        // 构建 ChatClient，注册 MCP 工具和 Advisor 链
        this.chatClient = ChatClient.builder(chatModel)
                // 将 MCP Client 提供的工具注册为大模型可调用的函数工具（Function Calling）
                // ToolCallbackProvider 会自动发现所有 MCP Server 暴露的工具并封装为统一接口
                .defaultToolCallbacks(toolCallbackProvider)
                // 配置 Advisor 链（按添加顺序执行）：
                // 1. MessageChatMemoryAdvisor：维护会话上下文，实现多轮对话记忆
                //    内部使用 MessageWindowChatMemory（滑动窗口策略，默认保留最近 20 条消息）
                // 2. SimpleLoggerAdvisor：以 DEBUG 级别记录请求/响应详情，便于开发调试
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build(),
                        new SimpleLoggerAdvisor())
                .build();
    }

    /**
     * 首页路由：渲染聊天页面。
     *
     * <p>返回 Thymeleaf 模板 {@code index.html}，该页面包含：
     * <ul>
     *   <li>基于 Tailwind CSS 的聊天界面 UI</li>
     *   <li>基于 HTMX 的无刷新消息提交与响应渲染</li>
     * </ul>
     * </p>
     *
     * @param model Spring MVC 模型对象，可用于向模板传递数据（当前未使用）
     * @return 模板视图名称 {@code "index"}，对应 {@code templates/index.html}
     */
    @GetMapping("/")
    public String index(Model model) {
        return "index";
    }


    /**
     * 直接调用 MCP Server 工具（不经过大模型）。
     *
     * <p>本接口演示如何绕过 ChatClient，直接通过 {@link McpAsyncClient} 调用 MCP Server 暴露的工具。
     * 主要用途：</p>
     * <ul>
     *   <li>调试和验证 MCP Server 连接是否正常</li>
     *   <li>查看 MCP Server 提供的所有可用工具列表</li>
     *   <li>直接调用指定工具并获取结果，不涉及大模型的推理过程</li>
     * </ul>
     *
     * <p><b>调用流程：</b></p>
     * <ol>
     *   <li>通过 {@code mcpClients.get(0)} 获取第一个 MCP 客户端（SSE 连接的 S07-mcp-server）</li>
     *   <li>调用 {@code listTools()} 列出该 MCP Server 提供的所有工具</li>
     *   <li>调用 {@code callTool()} 执行指定工具 {@code getTimeByZoneId}，传入时区参数</li>
     *   <li>使用 {@code .block()} 将异步结果转为同步返回（Reactor Mono → 阻塞获取）</li>
     * </ol>
     *
     * @param area 时区区域标识，如 {@code "Asia/Shanghai"}、{@code "America/New_York"}，
     *             对应 Java {@link java.time.ZoneId} 的合法值
     * @return MCP 工具返回的内容列表（{@link McpSchema.Content}），通常为包含当前时间的文本
     */
    @GetMapping("/directCallMcp")
    @ResponseBody
    public Object directCallMcp(String area) {
        // 获取第一个 MCP Server（SSE 模式连接的 S07-mcp-server）提供的所有工具列表
        // listTools() 返回 McpSchema.ListToolsResult，其中 tools 字段为工具定义列表
        var tools = mcpClients.get(0).listTools().block().tools();
        System.out.println("当前mcp的工具: " + tools);

        // 直接调用 MCP Server 的 getTimeByZoneId 工具
        // CallToolRequest 参数：工具名称 + 工具参数（Map 格式，key 为参数名，value 为参数值）
        // callTool() 返回 Mono<CallToolResult>，其中 content 字段为工具执行结果内容
        Mono<McpSchema.CallToolResult> result = mcpClients.get(0).callTool(
                new McpSchema.CallToolRequest("getTimeByZoneId", Map.of("area", area))
        );
        // 阻塞等待异步结果完成，返回工具执行内容
        return result.block().content();
    }

    /**
     * AI 对话接口（基于 HTMX 的局部刷新）。
     *
     * <p>接收用户输入的消息，通过 {@link ChatClient} 发送给大模型处理。
     * 大模型会根据对话上下文自主决定是否调用已注册的 MCP 工具（如时区查询、网页抓取等），
     * 并综合工具返回结果生成最终回复。</p>
     *
     * <p><b>HTMX 交互流程：</b></p>
     * <ol>
     *   <li>前端表单通过 {@code hx-post="/ask"} 发送 AJAX POST 请求</li>
     *   <li>后端处理后将问答结果放入 {@link Model}，返回 Thymeleaf 片段 {@code chat :: chatFragment}</li>
     *   <li>HTMX 接收到响应后，将片段内容追加到 {@code #chat} 容器末尾（{@code hx-swap="beforeend"}）</li>
     *   <li>前端 JavaScript 自动滚动到底部并清空输入框</li>
     * </ol>
     *
     * <p><b>Advisor 执行链：</b></p>
     * <ol>
     *   <li>{@link MessageChatMemoryAdvisor}：将历史对话消息注入当前 Prompt，实现多轮对话</li>
     *   <li>{@link SimpleLoggerAdvisor}：记录完整的请求/响应日志到控制台（DEBUG 级别）</li>
     *   <li>ChatClient 内部：将用户消息 + 工具定义发送给大模型，大模型可能触发 Function Calling</li>
     * </ol>
     *
     * @param message 用户输入的消息内容，由前端表单通过 {@code name="message"} 参数传递
     * @param model   Spring MVC 模型对象，用于向 Thymeleaf 模板传递渲染数据
     * @return {@link HtmxResponse} 包含要渲染的 Thymeleaf 片段引用，HTMX 会将其追加到页面
     */
    @PostMapping("/ask")
    public HtmxResponse chat(String message, Model model) {
        // 空消息校验：避免 prompt() 抛出 IllegalArgumentException
        if (message == null || message.isBlank()) {
            model.addAttribute("question", "");
            model.addAttribute("response", "请输入有效的消息内容");
            return HtmxResponse.builder().view("chat :: chatFragment").build();
        }

        // 通过 ChatClient 发送用户消息
        // prompt(message)：创建用户消息
        // call()：同步调用大模型（内部会自动执行 Advisor 链和 Function Calling）
        // content()：提取大模型返回的文本内容
        String res = this.chatClient.prompt(message).call().content();

        // 将问题和回答放入 Model，供 Thymeleaf 模板渲染
        model.addAttribute("question", message);
        model.addAttribute("response", res);

        // 构建 HTMX 响应：返回 chat.html 中 chatFragment 片段
        // HTMX 收到后会将该片段的 HTML 追加到 #chat 容器末尾，实现无刷新对话展示
        return HtmxResponse.builder().view("chat :: chatFragment").build();
    }
}
