package com.git.hui.offer.mvc;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * 函数工具（Function Tool / Function Calling）示例控制器
 * <p>
 * 本控制器演示 Spring AI 中多种注册和调用工具的方式，帮助理解 AI 模型如何在对话过程中
 * 自动选择并调用外部工具来获取信息。核心概念：
 * <ul>
 *   <li><b>@Tool 注解方式</b>：通过在方法上添加 @Tool 注解，声明该方法为可供 AI 调用的工具</li>
 *   <li><b>编程式注册（MethodToolCallback）</b>：通过反射获取方法，手动构建 ToolDefinition 和 ToolCallback</li>
 *   <li><b>函数式工具（FunctionToolCallback）</b>：使用 Function 接口实现工具逻辑，适合简单的一入一出场景</li>
 *   <li><b>声明式 Bean 工具</b>：通过 Spring Bean 名称直接引用已注册的工具</li>
 * </ul>
 * <p>
 * 工具调用的基本流程：
 * <ol>
 *   <li>用户发送消息 → AI 模型分析消息内容</li>
 *   <li>AI 模型判断需要调用某个工具 → 返回工具调用请求（而非直接文本回复）</li>
 *   <li>Spring AI 框架自动执行工具方法 → 将结果返回给 AI 模型</li>
 *   <li>AI 模型基于工具返回结果生成最终回复 → 返回给用户</li>
 * </ol>
 *
 * @author YiHui
 * @date 2025/7/26
 * @see Tool 工具声明注解
 * @see ToolCallback 工具回调接口
 * @see ChatClient 聊天客户端，支持流式/同步调用及工具集成
 */
@RestController
public class ChatController {

    /**
     * Spring AI 聊天客户端，封装了与 AI 模型交互的高层 API，
     * 支持提示词构建、工具注册、Advisor 链式调用等能力
     */
    private final ChatClient chatClient;

    /**
     * 底层聊天模型实例（此处为智谱 AI 模型），
     * 可直接调用 call() 方法进行同步推理，也可被 ChatClient 包装使用
     */
    private final ChatModel chatModel;

    /**
     * 构造函数：注入智谱 AI 聊天模型，并构建 ChatClient 实例
     * <p>
     * 构建 ChatClient 时注册了 {@link SimpleLoggerAdvisor}，它会在每次请求/响应时
     * 自动打印日志，方便调试和观察 AI 交互过程。
     *
     * @param chatModel Spring 容器注入的智谱 AI 聊天模型实例
     */
    public ChatController(ZhiPuAiChatModel chatModel) {
        this.chatModel = chatModel;
        // 使用 Builder 模式构建 ChatClient，绑定默认模型并添加日志 Advisor
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    /**
     * 方式一：通过底层 ChatModel 直接调用工具（最原始的方式）
     * <p>
     * 演示如何使用 {@link ToolCallbacks} 工具类从 POJO 对象中自动扫描 @Tool 注解方法，
     * 将其注册为可供 AI 调用的工具集合，然后通过 {@link ToolCallingChatOptions} 将工具列表
     * 附加到 Prompt 中发送给模型。
     * <p>
     * 流程：
     * <ol>
     *   <li>从 {@link DateTimeTools} 实例中反射扫描所有 @Tool 方法，生成 ToolCallback 数组</li>
     *   <li>构建 ToolCallingChatOptions，将工具列表绑定到本次请求</li>
     *   <li>创建 Prompt 并附带 ChatOptions，调用模型进行推理</li>
     *   <li>模型若判断需要调用工具，框架会自动执行工具并将结果回传给模型生成最终回复</li>
     * </ol>
     *
     * @param msg 用户输入的消息，例如 "现在几点了"
     * @return AI 模型结合工具返回结果生成的文本回复
     */
    @RequestMapping(path = "showTime")
    public String showTime(String msg) {
        // 利用 ToolCallbacks.from() 自动扫描 DateTimeTools 中所有 @Tool 注解的方法，生成工具回调数组
        ToolCallback[] tools = ToolCallbacks.from(new DateTimeTools());
        // 构建包含工具列表的聊天选项，告知模型本次对话有哪些工具可用
        ChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .build();
        // 直接调用底层 ChatModel，传入带工具选项的 Prompt，提取最终回复文本
        return chatModel.call(new Prompt(msg, options)).getResult().getOutput().getText();
    }

    /**
     * 方式二：通过 ChatClient 快捷注册工具（推荐方式）
     * <p>
     * 使用 ChatClient 的流式 API，通过 {@code .tools()} 方法直接传入工具对象实例，
     * 框架会自动扫描其中的 @Tool 注解方法并注册。相比方式一，代码更简洁，
     * 且自动享有 ChatClient 已配置的 Advisor 链（如日志、记忆等）。
     *
     * @param msg 用户输入的消息
     * @return AI 模型结合工具返回结果生成的文本回复
     */
    @RequestMapping(path = "time")
    public String getTime(String msg) {
        // 通过 ChatClient 的 tools() 方法直接传入工具对象，框架自动扫描 @Tool 方法
        return chatClient.prompt(msg).tools(new DateTimeTools()).call().content();
    }

    /**
     * 对照实验：不注册任何工具时的对话效果
     * <p>
     * 与 {@link #getTime(String)} 接口对比，当不传入工具时，AI 模型无法获取实时时间信息，
     * 只能基于自身训练数据回答（可能不准确或拒绝回答），用于对比说明工具的作用。
     *
     * @param msg 用户输入的消息
     * @return AI 模型仅基于自身知识的文本回复（无工具辅助）
     */
    @RequestMapping(path = "timeNoTools")
    public String getTimeNoTools(String msg) {
        // 不注册任何工具，AI 模型将无法调用外部能力，仅依赖训练数据回答
        return chatClient.prompt(msg).call().content();
    }

    /**
     * 方式三：编程式手动注册工具（MethodToolCallback）
     * <p>
     * 演示如何完全通过代码手动构建工具的各个组成部分，而非依赖 @Tool 注解自动扫描。
     * 这种方式提供了最大的灵活性，适用于需要动态注册工具或自定义工具元信息的场景。
     * <p>
     * 构建步骤：
     * <ol>
     *   <li><b>获取方法引用</b>：通过反射获取目标方法</li>
     *   <li><b>定义 ToolDefinition</b>：指定工具名称、描述、输入参数的 JSON Schema</li>
     *   <li><b>定义 ToolMetadata</b>：设置工具元信息，如是否直接返回结果给用户</li>
     *   <li><b>构建 MethodToolCallback</b>：将方法、定义、元信息、执行对象组装为完整的工具回调</li>
     * </ol>
     *
     * @param msg 用户输入的消息
     * @return AI 模型结合工具返回结果生成的文本回复
     */
    @RequestMapping(path = "timeByCodeTool")
    public String getTimeByCodeTool(String msg) {
        // 第一步：通过反射获取 DateTimeTools 中的 getTimeByZoneId 方法
        Method method = ReflectionUtils.findMethod(DateTimeTools.class, "getTimeByZoneId", ZoneId.class);

        // 第二步：构建工具定义（ToolDefinition），描述工具的名称、功能和输入参数格式
        // AI 模型会根据 name 和 description 判断何时调用此工具
        // inputSchema 使用 JsonSchemaGenerator 自动根据方法签名生成参数的 JSON Schema
        ToolDefinition toolDefinition = ToolDefinition.builder()
                .name("getTimeByZoneId")
                .description("传入时区，返回对应时区的当前时间给用户")
                .inputSchema(JsonSchemaGenerator.generateForMethodInput(method))
                .build();

        // 第三步：构建工具元信息（ToolMetadata）
        // returnDirect=false 表示工具结果先返回给 AI 模型处理，而非直接返回给用户
        ToolMetadata toolMetadata = ToolMetadata.builder()
                .returnDirect(false)
                .build();

        // 第四步：组装完整的 MethodToolCallback，绑定方法定义、元信息、执行方法和执行对象
        ToolCallback callBack = MethodToolCallback.builder()
                .toolDefinition(toolDefinition)
                .toolMetadata(toolMetadata)
                .toolMethod(method)
                .toolObject(new DateTimeTools())  // 工具方法的实际执行对象实例
                .build();
        // 将手动构建的工具回调注册到 ChatClient 并发起调用
        return chatClient.prompt(msg).toolCallbacks(callBack).call().content();
    }


    /**
     * 方式四：函数式工具（FunctionToolCallback）
     * <p>
     * 使用 {@link Function} 函数式接口实现工具逻辑，适用于简单的「单输入 → 单输出」场景。
     * 工具类 {@link NowService} 实现 {@code Function<AreaReq, AreaResp>} 接口，
     * 框架会自动根据输入类型 {@link AreaReq} 生成 JSON Schema 供 AI 模型理解参数结构。
     * <p>
     * 注意事项：
     * <ul>
     *   <li>函数式工具的传参和返回结果必须是 POJO 或 void，不能是基本类型</li>
     *   <li>inputSchema 可省略，默认根据 inputType 自动生成</li>
     *   <li>适合逻辑简单、无需 @Tool 注解的独立工具方法场景</li>
     * </ul>
     *
     * @param msg 用户输入的消息
     * @return AI 模型结合工具返回结果生成的文本回复
     * @see NowService 函数式工具实现
     * @see AreaReq 工具输入参数（Record 类型）
     * @see AreaResp 工具返回结果（Record 类型）
     */
    @RequestMapping(path = "timeByCodeFunc")
    public String getTimeByCodeFunc(String msg) {
        // 使用函数式工具需要注意的是，传参和返回结果，要么是void，要么是POJO
        // 构建 FunctionToolCallback：指定工具名称、Function 实现类、输入类型
        ToolCallback callBack = FunctionToolCallback.builder("nowDateByArea", new NowService())
                .description("传入时区，返回对应时区的当前时间给用户")
                .inputType(AreaReq.class)
                // 下面这一行实际是可以省略的，默认就是根据 inputType 进行生成jsonSchema
                .inputSchema(JsonSchemaGenerator.generateForType(AreaReq.class))
                // returnDirect=false：工具结果先回传给 AI 模型，由模型组织最终回复
                .toolMetadata(ToolMetadata.builder().returnDirect(false).build())
                .build();
        return chatClient.prompt(msg).toolCallbacks(callBack).call().content();
    }


    /**
     * 方式五：声明式 Bean 工具引用
     * <p>
     * 最简洁的工具注册方式：直接通过 Spring Bean 名称引用工具。
     * 前提是工具类已被注册为 Spring Bean（如通过 @Component 或 @Bean），
     * 框架会自动扫描其中的 @Tool 方法并注册到工具列表中。
     * <p>
     * 此处 {@code .tools("dateTimeTools")} 中的字符串即为 Spring 容器中 Bean 的名称，
     * ChatClient 会自动从容器中查找该 Bean 并提取其工具方法。
     *
     * @param msg 用户输入的消息
     * @return AI 模型结合工具返回结果生成的文本回复
     */
    @RequestMapping(path = "timeByDeclareFunc")
    public String getTimeByDeclareFunc(String msg) {
        // 直接通过 Bean 名称 "dateTimeTools" 引用工具，框架自动从 Spring 容器查找并注册
        return chatClient.prompt(msg).tools("dateTimeTools").call().content();
    }

    /**
     * 内部工具类：提供基于注解的日期时间查询工具方法
     * <p>
     * 通过 {@link Tool} 注解标记的方法会被 Spring AI 自动识别为可供 AI 调用的工具。
     * AI 模型会根据 description 描述判断何时调用对应方法。
     * <p>
     * 该类包含两个工具方法：
     * <ul>
     *   <li>{@link #getCurrentDateTime()} - 获取当前服务器时区的时间</li>
     *   <li>{@link #getTimeByZoneId(ZoneId)} - 获取指定时区的时间并转换为本地时区显示</li>
     * </ul>
     * <p>
     * 使用 @Component 注册为 Spring Bean，使其可被方式五通过 Bean 名称直接引用。
     */
    @Component
    static class DateTimeTools {

        /**
         * 工具方法：获取当前时间（无需指定时区）
         * <p>
         * 使用服务器当前时区返回时间，适合不需要关心时区的简单场景。
         * AI 模型会在用户询问「现在几点」等不涉及特定时区的问题时调用此方法。
         *
         * @return 当前时区的时间字符串（ISO-8601 格式）
         */
        @Tool(description = "不需要关注用户时区，直接获取当前服务器时区的时间并返回给用户，适用于用户未指定具体时区的场景")
        String getCurrentDateTime() {
            // 获取当前时间并附加服务器所在时区，转为 ISO-8601 格式字符串
            String ans = LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
            System.out.println("进入获取当前时间了：" + ans);
            return ans;
        }

        /**
         * 工具方法：根据指定时区获取当前时间
         * <p>
         * 接收 AI 模型解析出的时区参数，查询该时区的当前时间，
         * 并将结果转换为服务器本地时区格式返回，便于用户理解。
         * <p>
         * AI 模型会在用户提到具体时区（如「东京时间」「纽约时间」）时调用此方法，
         * 并从用户消息中提取时区信息填入 {@code area} 参数。
         *
         * @param area 目标时区，由 AI 模型根据用户消息自动解析并传入（如 Asia/Tokyo）
         * @return 转换后的本地时区时间字符串，格式为 "yyyy-MM-dd HH:mm:ss"
         */
        @Tool(description = "根据用户指定的时区（如 Asia/Tokyo、America/New_York），查询该时区的当前时间并转换为本地时区格式返回给用户")
        String getTimeByZoneId(@ToolParam(description = "需要查询时间的目标时区标识，如 Asia/Tokyo、America/New_York 等标准时区名称") ZoneId area) {
            // 根据传入的时区，获取该时区当前的时间
            ZonedDateTime time = LocalDateTime.now().atZone(area);

            // 将目标时区的时间转换为服务器本地时区，方便统一展示
            ZonedDateTime localTime = time.withZoneSameInstant(ZoneId.systemDefault());
            // 定义输出格式：年-月-日 时:分:秒
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String ans = localTime.format(formatter);
            System.out.println("传入的时区是：" + area + "-" + ans);
            return ans;
        }
    }


    /**
     * 函数式工具实现：实现 {@link Function}{@code <AreaReq, AreaResp>} 接口
     * <p>
     * 作为 {@link FunctionToolCallback} 的执行体，接收 {@link AreaReq} 参数，
     * 返回 {@link AreaResp} 结果。逻辑与 {@link DateTimeTools#getTimeByZoneId(ZoneId)} 类似，
     * 但采用函数式编程风格，无需 @Tool 注解。
     * <p>
     * 使用 static 修饰，因为函数式工具实例不依赖外部类状态，可独立创建。
     *
     * @see AreaReq 输入参数 Record
     * @see AreaResp 输出结果 Record
     */
    public static class NowService implements Function<AreaReq, AreaResp> {
        /**
         * 函数式工具的核心执行方法
         * <p>
         * 从请求中提取时区信息，查询该时区当前时间，并转换为本地时区格式返回。
         *
         * @param req 包含目标时区的请求对象
         * @return 包含格式化时间字符串的响应对象
         */
        @Override
        public AreaResp apply(AreaReq req) {
            // 从请求 Record 中提取时区信息
            ZoneId area = req.zoneId();
            // 根据传入的时区，获取该时区当前的时间
            ZonedDateTime time = LocalDateTime.now().atZone(area);

            // 将目标时区的时间转换为服务器本地时区，方便统一展示
            ZonedDateTime localTime = time.withZoneSameInstant(ZoneId.systemDefault());
            // 定义输出格式：年-月-日 时:分:秒
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String ans = localTime.format(formatter);
            System.out.println("传入的时区是：" + area + "-" + ans);
            return new AreaResp(ans);
        }
    }

    /**
     * 函数式工具的输入参数 Record
     * <p>
     * 使用 Java Record 定义不可变数据对象，{@link ToolParam} 注解为 AI 模型提供参数描述，
     * 帮助模型理解该参数的含义并正确填充值。
     *
     * @param zoneId 目标时区标识（如 "Asia/Tokyo"、"America/New_York"），由 AI 模型从用户消息中解析
     */
    public record AreaReq(@ToolParam(description = "需要查询时间的目标时区标识，如 Asia/Tokyo、America/New_York 等标准时区名称") ZoneId zoneId) {
    }

    /**
     * 函数式工具的返回结果 Record
     * <p>
     * 封装工具执行结果，框架会自动将 Record 序列化为 JSON 返回给 AI 模型，
     * 模型据此生成最终的用户回复。
     *
     * @param time 格式化后的时间字符串，如 "2025-07-26 20:00:00"
     */
    public record AreaResp(String time) {
    }
}
