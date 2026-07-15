package com.git.hui.springai.mvc;

import cn.hutool.http.HttpUtil;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多模态图片识别控制器
 * <p>
 * 基于 Spring AI 的多模态能力，接收远程图片 URL 并下载为二进制数据，
 * 将图片字节流封装为 {@link Media} 对象，与文本提示词一起组装成 {@link UserMessage}，
 * 发送给支持多模态的大模型（如智谱 GLM-4V）进行图片内容识别与结构化输出。
 * </p>
 * <p>
 * 提供以下接口：
 * <ul>
 *     <li>{@link #recognition} — 远程图片 URL，纯文本返回</li>
 *     <li>{@link #recognitionAndOutput} — 远程图片 URL，结构化返回 {@link FoodDetail} JSON</li>
 *     <li>{@link #localUploadRecognition} — 本地图片上传，纯文本返回</li>
 *     <li>{@link #localUploadRecognitionAndOutput} — 本地图片上传，结构化返回 {@link FoodDetail} JSON</li>
 * </ul>
 * </p>
 *
 * @author YiHui
 * @date 2025/8/4
 * @see org.springframework.ai.content.Media
 * @see org.springframework.ai.chat.messages.UserMessage
 */
@RestController
public class ImgRecognitionController {

    /**
     * Spring AI ChatClient 实例，封装了与 ChatModel 的交互逻辑。
     * 通过构造器注入 {@link ChatModel}，并挂载 {@link SimpleLoggerAdvisor}
     * 以在控制台输出请求/响应的日志，便于调试。
     */
    private final ChatClient chatClient;

    /**
     * 构造器：接收 Spring 容器自动注入的 {@link ChatModel}，
     * 使用 {@link ChatClient.Builder} 构建 ChatClient，
     * 并注册 {@link SimpleLoggerAdvisor} 作为默认 Advisor，
     * 实现请求和响应内容的自动日志打印。
     *
     * @param chatModel Spring AI 聊天模型实例（由 spring-ai-zhipuai 等自动配置提供）
     */
    public ImgRecognitionController(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }


    /**
     * 图片内容识别接口（纯文本返回）
     * <p>
     * 处理流程：
     * <ol>
     *     <li>通过 Hutool {@link HttpUtil#downloadBytes} 下载远程图片为字节数组</li>
     *     <li>使用 {@link PromptTemplate} 将用户传入的提示词填充到模板中，生成最终文本指令</li>
     *     <li>将图片字节流封装为 {@link Media}（MIME 类型为 image/png），
     *         与文本一起构建 {@link UserMessage}，形成多模态消息</li>
     *     <li>将 UserMessage 包装为 {@link Prompt}，通过 {@link ChatClient} 发送给大模型</li>
     *     <li>返回模型生成的纯文本识别结果</li>
     * </ol>
     * </p>
     *
     * @param imgUrl 远程图片的 URL 地址，支持 PNG/JPG 等常见图片格式
     * @param msg    用户自定义的附加提示词，用于引导模型的识别方向（如 "请描述这道菜的做法"）
     * @return 大模型返回的图片识别纯文本结果
     */
    @RequestMapping(path = "recognition")
    public String recognition(@RequestParam(name = "imgUrl") String imgUrl,
                              @RequestParam(name = "msg") String msg) {
        // 1. 通过 HTTP 下载远程图片，获取图片的原始字节数据
        byte[] imgs = HttpUtil.downloadBytes(imgUrl);

        // 2. 使用 PromptTemplate 将用户提示词渲染到模板中，生成最终的文本指令
        String text = new PromptTemplate("{msg}, 请将图片内容进行识别，并返回结果").render(Map.of("msg", msg));

        // 3. 构建多模态 Media 对象：指定 MIME 类型为 PNG，数据为图片字节数组
        Media media = Media.builder()
                .mimeType(MimeTypeUtils.IMAGE_PNG)
                .data(imgs)
                .build();

        // 4. 将文本指令和图片 Media 合并为一条 UserMessage（多模态消息）
        Message userMsg = UserMessage.builder().text(text).media(media).build();

        // 5. 将 UserMessage 封装为 Prompt，通过 ChatClient 发送给大模型并获取文本响应
        Prompt prompt = new Prompt(userMsg);
        return chatClient.prompt(prompt).call().content();
    }

    /**
     * 图片内容识别接口（结构化 JSON 返回）
     * <p>
     * 与 {@link #recognition} 的前半段逻辑一致（下载图片 → 构建多模态 Prompt），
     * 区别在于调用 {@code chatClient.prompt(prompt).call().entity(FoodDetail.class)}，
     * Spring AI 会自动将模型返回的 JSON 文本反序列化为 {@link FoodDetail} 对象，
     * 实现 <b>结构化输出（Structured Output）</b>。
     * </p>
     * <p>
     * 该接口适用于需要程序化消费识别结果的场景（如前端表格展示、卡路里统计等），
     * 模型会按照 {@link FoodDetail} 和 {@link FoodItem} 的字段定义及
     * {@link JsonPropertyDescription} 描述来组织输出结构。
     * </p>
     *
     * @param imgUrl 远程图片的 URL 地址
     * @param msg    用户自定义的附加提示词
     * @return 结构化的食物详情对象，包含图片描述、总卡路里、食材列表等
     * @see FoodDetail
     * @see FoodItem
     */
    @RequestMapping(path = "recognitionAndOutput")
    public FoodDetail recognitionAndOutput(@RequestParam(name = "imgUrl") String imgUrl,
                                           @RequestParam(name = "msg") String msg) {
        // 1. 下载远程图片字节数据（与 recognition 方法逻辑相同）
        byte[] imgs = HttpUtil.downloadBytes(imgUrl);

        // 2. 渲染提示词模板：明确要求模型以 JSON 格式逐项列出每种食材，
        //    并给出 itemList 的示例结构，引导模型正确填充嵌套列表
        String text = new PromptTemplate("{msg}\n\n" +
                "请识别图片中的食物，按以下 JSON 格式返回结果：\n" +
                "{\n" +
                "  \"desc\": \"图片整体描述\",\n" +
                "  \"totalCalorie\": 总卡路里数值,\n" +
                "  \"calorieDesc\": \"卡路里计算说明\",\n" +
                "  \"itemList\": [\n" +
                "    {\"food\": \"食材名\", \"desc\": \"卡路里占比\", \"cnt\": 数量, \"minCalorie\": 最小值, \"maxCalorie\": 最大值}\n" +
                "  ]\n" +
                "}\n" +
                "注意：itemList 必须包含图片中识别到的每一种食材，不能为空数组。").render(Map.of("msg", msg));

        // 3. 构建图片 Media 对象，MIME 类型指定为 PNG
        Media media = Media.builder()
                .mimeType(MimeTypeUtils.IMAGE_PNG)
                .data(imgs)
                .build();

        // 4. 组装多模态 UserMessage：文本指令 + 图片数据
        Message userMsg = UserMessage.builder().text(text).media(media).build();

        // 5. 封装为 Prompt 并通过 ChatClient 调用大模型；
        //    .entity(FoodDetail.class) 指示 Spring AI 将模型返回的 JSON 自动反序列化为 FoodDetail 对象
        Prompt prompt = new Prompt(userMsg);
        return chatClient.prompt(prompt).call().entity(FoodDetail.class);
    }

    /**
     * 本地图片上传识别接口（纯文本返回）
     * <p>
     * 与 {@link #recognition} 逻辑一致，区别在于图片来源为本地上传的 {@link MultipartFile}，
     * 无需远程下载，直接读取文件字节数据构建 {@link Media} 对象。
     * MIME 类型根据上传文件的 Content-Type 自动推断。
     * </p>
     *
     * @param file 本地上传的图片文件，支持 PNG/JPG/JPEG/WebP 等常见图片格式
     * @param msg  用户自定义的附加提示词，用于引导模型的识别方向
     * @return 大模型返回的图片识别纯文本结果
     * @throws IOException 读取上传文件字节数据失败时抛出
     */
    @RequestMapping(path = "localUploadRecognition")
    public String localUploadRecognition(@RequestParam(name = "file") MultipartFile file,
                                         @RequestParam(name = "msg") String msg) throws IOException {
        // 1. 直接读取上传文件的字节数据，无需远程下载
        byte[] imgs = file.getBytes();

        // 2. 渲染提示词模板（与 recognition 接口相同）
        String text = new PromptTemplate("{msg}, 请将图片内容进行识别，并返回结果").render(Map.of("msg", msg));

        // 3. 根据上传文件的 Content-Type 推断 MIME 类型，构建 Media 对象
        MimeType mimeType = resolveMimeType(file.getContentType());
        Media media = Media.builder()
                .mimeType(mimeType)
                .data(imgs)
                .build();

        // 4. 组装多模态 UserMessage 并通过 ChatClient 调用大模型
        Message userMsg = UserMessage.builder().text(text).media(media).build();
        Prompt prompt = new Prompt(userMsg);
        return chatClient.prompt(prompt).call().content();
    }

    /**
     * 本地图片上传识别接口（结构化 JSON 返回）
     * <p>
     * 与 {@link #recognitionAndOutput} 逻辑一致，图片来源为本地上传的 {@link MultipartFile}。
     * 通过 {@code .entity(FoodDetail.class)} 实现结构化输出，
     * 模型返回的 JSON 自动反序列化为 {@link FoodDetail} 对象。
     * </p>
     *
     * @param file 本地上传的图片文件
     * @param msg  用户自定义的附加提示词
     * @return 结构化的食物详情对象，包含图片描述、总卡路里、食材列表等
     * @throws IOException 读取上传文件字节数据失败时抛出
     * @see FoodDetail
     * @see FoodItem
     */
    @RequestMapping(path = "localUploadRecognitionAndOutput")
    public FoodDetail localUploadRecognitionAndOutput(@RequestParam(name = "file") MultipartFile file,
                                                      @RequestParam(name = "msg") String msg) throws IOException {
        // 1. 读取上传文件字节数据
        byte[] imgs = file.getBytes();

        // 2. 渲染结构化输出提示词模板（与 recognitionAndOutput 接口相同）
        String text = new PromptTemplate("{msg}\n\n" +
                "请识别图片中的食物，按以下 JSON 格式返回结果：\n" +
                "{\n" +
                "  \"desc\": \"图片整体描述\",\n" +
                "  \"totalCalorie\": 总卡路里数值,\n" +
                "  \"calorieDesc\": \"卡路里计算说明\",\n" +
                "  \"itemList\": [\n" +
                "    {\"food\": \"食材名\", \"desc\": \"卡路里占比\", \"cnt\": 数量, \"minCalorie\": 最小值, \"maxCalorie\": 最大值}\n" +
                "  ]\n" +
                "}\n" +
                "注意：itemList 必须包含图片中识别到的每一种食材，不能为空数组。").render(Map.of("msg", msg));

        // 3. 根据上传文件 Content-Type 推断 MIME 类型，构建 Media 对象
        MimeType mimeType = resolveMimeType(file.getContentType());
        Media media = Media.builder()
                .mimeType(mimeType)
                .data(imgs)
                .build();

        // 4. 组装多模态 UserMessage 并调用大模型，自动反序列化为 FoodDetail
        Message userMsg = UserMessage.builder().text(text).media(media).build();
        Prompt prompt = new Prompt(userMsg);
        return chatClient.prompt(prompt).call().entity(FoodDetail.class);
    }

    /**
     * 根据 Content-Type 字符串推断 {@link MimeType}
     * <p>
     * 优先使用文件实际的 Content-Type；若为空则降级为 {@link MimeTypeUtils#IMAGE_PNG}。
     * </p>
     *
     * @param contentType 上传文件的 Content-Type 头（如 "image/jpeg"、"image/png"）
     * @return 解析后的 MimeType 实例
     */
    private MimeType resolveMimeType(String contentType) {
        if (contentType != null && !contentType.isEmpty()) {
            return MimeType.valueOf(contentType);
        }
        return MimeTypeUtils.IMAGE_PNG;
    }

    /**
     * 食物详情结构化输出模型
     * <p>
     * 用于 {@link #recognitionAndOutput} 接口的结构化返回。
     * Spring AI 会根据各字段上的 {@link JsonPropertyDescription} 注解描述，
     * 引导大模型按对应语义生成 JSON，再自动反序列化为该对象实例。
     * </p>
     * <p>
     * 注意：使用 Class（而非 Record）并为 itemList 设置默认空列表，
     * 可避免部分模型（如 GLM-4V-Flash）在生成嵌套 {@code $ref} 列表时返回 null 的问题。
     * </p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FoodDetail {
        @JsonPropertyDescription("整张图片的描述")
        public String desc;
        @JsonPropertyDescription("总的卡路里")
        public Double totalCalorie;
        @JsonPropertyDescription("卡路里计算方式说明")
        public String calorieDesc;
        @JsonPropertyDescription("图片中的食材列表，必须包含每种食材的详细信息")
        public List<FoodItem> itemList = new ArrayList<>();
    }

    /**
     * 单项食材信息模型
     * <p>
     * 作为 {@link FoodDetail#itemList} 的元素类型，
     * 描述图片中识别到的每一种食材及其卡路里信息。
     * {@link JsonPropertyDescription} 注解为模型提供字段语义提示，
     * 确保输出的 JSON 结构与字段含义一致。
     * </p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FoodItem {
        @JsonPropertyDescription("食材名称，如番茄、鸡蛋")
        public String food;
        @JsonPropertyDescription("该食材的卡路里占用描述，如约占总卡路里的30%")
        public String desc;
        @JsonPropertyDescription("食材数量")
        public Integer cnt;
        @JsonPropertyDescription("最小卡路里含量（千卡）")
        public Double minCalorie;
        @JsonPropertyDescription("最大卡路里含量（千卡）")
        public Double maxCalorie;
    }
}
