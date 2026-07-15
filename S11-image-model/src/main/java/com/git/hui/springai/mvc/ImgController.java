package com.git.hui.springai.mvc;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * 图像生成控制器 —— 基于 Spring AI 的 ImageModel 抽象，调用智谱 AI CogView-3 模型生成图片。
 * <p>
 * 本控制器演示 Spring AI 图像模型（ImageModel）的典型用法：
 * <ol>
 *     <li><b>提示词自动优化</b> —— 在图像生成前，先通过 {@link ChatModel}（GLM-4.7-Flash）
 *         将用户简短的中文描述自动转化为详细、生动的英文提示词，显著提升图像生成质量</li>
 *     <li>通过 {@link ImagePrompt} 封装优化后的文本提示词与图像生成参数（尺寸、模型、风格、格式），
 *         参数均从 {@code application.yml} 外部化配置注入，避免硬编码</li>
 *     <li>调用 {@link ImageModel#call(ImagePrompt)} 向远端模型发送生成请求</li>
 *     <li>从 {@link ImageResponse} 中提取生成图片的 URL，以流式转发方式直接返回给客户端，
 *         避免全量加载到内存，显著降低内存占用</li>
 * </ol>
 * <p>
 * <b>提示词优化降级机制：</b>若 ChatModel 调用异常（网络超时、API 限流等），
 * 系统会自动降级使用用户原始提示词继续生成，确保主流程不中断。
 * <p>
 * <b>注意：</b>图像模型名称必须显式指定（配置项 {@code app.image.generate.model}），
 * 因为智谱 AI SDK 存在已知问题：不传 model 时会被默认值 {@code cogview-3} 覆盖，
 * 而非使用配置文件中设置的 {@code CogView-3-Flash}。
 *
 * @author YiHui
 * @date 2025/8/4
 * @see ImageModel
 * @see ChatModel
 */
@RestController
public class ImgController {

    /**
     * Spring AI 图像模型接口，由智谱 AI 自动配置注入。
     * <p>
     * {@code ImageModel} 是 Spring AI 对文生图能力的统一抽象，屏蔽了不同厂商 API 的差异，
     * 核心方法 {@link ImageModel#call(ImagePrompt)} 接收提示词与选项，返回生成结果。
     */
    private final ImageModel imgModel;

    /**
     * 带日志 Advisor 的 ChatClient 实例，用于提示词优化阶段。
     * <p>
     * 基于 Spring AI 自动注入的 {@link ChatModel}（GLM-4.7-Flash）在构造方法中创建，
     * 通过 {@link SimpleLoggerAdvisor} 自动记录每次请求/响应的详细信息，
     * 便于调试和监控提示词优化过程。
     */
    private final ChatClient chatClient;

    /** 输出图片宽度（像素），从配置 {@code app.image.generate.width} 读取，默认 1024 */
    @Value("${app.image.generate.width:1024}")
    private int width;

    /** 输出图片高度（像素），从配置 {@code app.image.generate.height} 读取，默认 1024 */
    @Value("${app.image.generate.height:1024}")
    private int height;

    /**
     * 文生图模型名称，从配置 {@code app.image.generate.model} 读取。
     * <p>
     * 注意：由于智谱 AI SDK 存在已知 bug（不传 model 时会被默认值 "cogview-3" 覆盖），
     * 此处必须显式指定为 "CogView-3-Flash"。
     */
    @Value("${app.image.generate.model:CogView-3-Flash}")
    private String model;

    /** 返回图片格式，从配置 {@code app.image.generate.response-format} 读取，默认 "png" */
    @Value("${app.image.generate.response-format:png}")
    private String responseFormat;

    /** 图像风格，从配置 {@code app.image.generate.style} 读取，"natural"（自然）或 "vivid"（生动） */
    @Value("${app.image.generate.style:natural}")
    private String style;

    /**
     * 构造器注入图像模型与聊天模型实例。
     * <p>
     * 构造器参数均使用接口类型（{@link ImageModel}、{@link ChatModel}），
     * 遵循依赖倒置原则，降低对具体厂商实现的编译期耦合。
     * Spring Boot 自动配置会根据 classpath 中的智谱 AI Starter 依赖创建 Bean 并注入此处。
     * <ul>
     *     <li>{@code imgModel} —— 用于调用 CogView-3-Flash 文生图模型</li>
     *     <li>{@code chatModel} —— 用于调用 GLM-4.7-Flash 聊天模型，在图像生成前优化提示词</li>
     * </ul>
     *
     * @param imgModel   图像模型接口实例，由 Spring 容器自动装配
     * @param chatModel  聊天模型接口实例，由 Spring 容器自动装配，用于提示词自动优化
     */
    public ImgController(ImageModel imgModel, ChatModel chatModel) {
        this.imgModel = imgModel;
        // 基于 ChatModel 构建带 SimpleLoggerAdvisor 的 ChatClient，自动打印请求/响应日志
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor(
                        req -> ("[request] " + req),     // ← 这就是 requestToString
                        res -> ("[response] " + res),
                        0))
                .build();
    }

    /**
     * 使用 ChatClient（含 {@link SimpleLoggerAdvisor}）对用户原始提示词进行自动优化/增强。
     * <p>
     * <b>工作原理：</b>通过 {@link ChatClient} 链式调用构造系统指令与用户描述，
     * 让 GLM-4.7-Flash 将简短的中文描述转化为详细、生动的英文图像提示词，
     * 补充画面细节（光线、色彩、构图、氛围等），从而提升 CogView 图像生成模型的理解效果和出图质量。
     * {@link SimpleLoggerAdvisor} 会自动记录每次请求/响应的详细日志，便于调试和监控。
     * <p>
     * <b>降级策略：</b>若 ChatClient 调用过程中发生任何异常（网络超时、API 限流、模型返回空等），
     * 本方法会捕获异常并静默降级，返回用户原始提示词，确保图像生成主流程不中断。
     *
     * @param originalPrompt 用户原始提示词（通常为简短的中文描述）
     * @return 优化后的英文提示词；若优化失败则降级返回原始提示词
     */
    private String optimizePrompt(String originalPrompt) {
        try {
            // 系统指令：指导 ChatModel 扮演"图像提示词工程师"角色，
            // 将用户简短描述转化为 CogView 更易理解的详细英文提示词
            String systemHint = "你是一个图像提示词优化专家。请将用户输入的简短描述转化为" +
                    "更详细、更具画面感的英文提示词，适合用于AI图像生成模型。" +
                    "只输出优化后的提示词，不要解释。";

            // 通过 ChatClient（含 SimpleLoggerAdvisor）调用，自动记录请求/响应日志
            String optimized = chatClient.prompt()
                    .user(systemHint + "\n用户输入：" + originalPrompt)
                    .call()
                    .content();

            // 空值防护：ChatModel 可能返回 null 或空白内容，此时降级使用原始提示词
            if (optimized != null && !optimized.isBlank()) {
                return optimized.trim();
            }
        } catch (Exception e) {
            // 优化失败，静默降级：使用原始提示词继续生成，不阻断主流程
            // 常见异常原因：网络超时、API Key 额度耗尽、模型服务不可用等
        }
        return originalPrompt;
    }

    /**
     * 根据文本提示词生成图片，并以流式转发方式直接返回给浏览器。
     * <p>
     * <b>完整处理流程：</b>
     * <ol>
     *     <li><b>入参校验</b> —— 检查提示词非空，无效时直接返回 400</li>
     *     <li><b>提示词自动优化</b> —— 调用 {@link #optimizePrompt(String)} 通过 ChatModel
     *         将用户简短描述转化为详细的英文提示词，失败时自动降级使用原始提示词</li>
     *     <li><b>构建图像提示词</b> —— 将优化后的文本与从配置文件注入的
     *         图像生成参数（尺寸、模型、风格、格式）封装为 {@link ImagePrompt} 对象</li>
     *     <li><b>调用模型生成</b> —— 通过 {@link ImageModel#call(ImagePrompt)} 向智谱 AI 发送请求，
     *         模型返回包含图片 URL 的 {@link ImageResponse}（含逐层空值防护）</li>
     *     <li><b>流式转发图片</b> —— 从响应中提取图片 URL，通过 {@link InputStream#transferTo}
     *         将远程图片字节流直接 pipe 到 HTTP 响应输出流，避免全量加载到内存</li>
     * </ol>
     *
     * @param msg 文本提示词，描述期望生成的图片内容，例如 "一只在草地上奔跑的金毛犬"
     * @param res HTTP 响应对象，用于设置响应头并写入图片二进制流
     * @throws IOException 当远程图片下载失败或响应流写入异常时抛出
     */
    @GetMapping(path = "/genImg", produces = "image/png")
    public void genImg(String msg, HttpServletResponse res) throws IOException {
        // 0. 入参校验：拦截空/空白提示词，避免无效的远端 API 调用
        if (msg == null || msg.isBlank()) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "提示词不能为空");
            return;
        }

        // 1. 提示词自动优化：通过 ChatModel 将用户简短描述转化为详细英文提示词，提升出图质量
        //    优化失败时自动降级为原始提示词，确保主流程不中断
        String optimizedMsg = optimizePrompt(msg);

        // 2. 构建 ImagePrompt：将优化后的提示词与图像生成选项（从配置注入）组合
        ImageResponse response = imgModel.call(new ImagePrompt(optimizedMsg,
                ImageOptionsBuilder.builder()
                        .height(height)              // 输出图片高度（像素），来自配置
                        .width(width)                // 输出图片宽度（像素），来自配置
                        .model(model)                // 模型名称，来自配置（SDK bug 要求必须显式指定）
                        .responseFormat(responseFormat) // 返回图片格式，来自配置
                        .style(style)  // 图像风格，来自配置
                        .build())
        );

        // 3. 空指针防护：模型可能返回空结果
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            res.sendError(HttpServletResponse.SC_BAD_GATEWAY, "图像模型返回空结果");
            return;
        }
        Image img = response.getResult().getOutput();

        // 4. 空指针防护：URL 字段可能缺失
        if (img.getUrl() == null || img.getUrl().isBlank()) {
            res.sendError(HttpServletResponse.SC_BAD_GATEWAY, "图像模型未返回有效的图片地址");
            return;
        }

        // 5. 流式转发远程图片：直接 pipe 字节流到响应，避免 BufferedImage 全量加载（~4MB → ~8KB 内存）
        res.setContentType("image/png");
        try (InputStream in = URI.create(img.getUrl()).toURL().openStream()) {
            in.transferTo(res.getOutputStream());
            res.getOutputStream().flush();
        }
    }
}
