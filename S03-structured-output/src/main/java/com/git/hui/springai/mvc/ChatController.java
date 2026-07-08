package com.git.hui.springai.mvc;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 结构化输出示例控制器 —— 演示 Spring AI 中将大模型返回文本映射为 Java 结构化对象的多种实现方式。
 * <p>
 * 本控制器包含 7 个接口，分别展示以下结构化输出手段：
 * <ul>
 *     <li>{@link ChatClient} + record 自动映射（{@link #generate}）</li>
 *     <li>{@link BeanOutputConverter} 手动解析为 Java Bean（{@link #gen2}）</li>
 *     <li>{@link ChatClient} + {@link ParameterizedTypeReference} 映射为 List（{@link #genList}）</li>
 *     <li>{@link ChatClient} + {@link ParameterizedTypeReference} 映射为 Map（{@link #genMap}）</li>
 *     <li>{@link MapOutputConverter} 手动解析为 Map（{@link #genMap2}）</li>
 *     <li>{@link ChatClient} + {@link ListOutputConverter} 自动解析为 List（{@link #genList1}）</li>
 *     <li>{@link ListOutputConverter} 手动解析为 List（{@link #genList2}）</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/7/11
 * @see BeanOutputConverter
 * @see MapOutputConverter
 * @see ListOutputConverter
 */
@RestController
public class ChatController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatController.class);

    private final ZhiPuAiChatModel chatModel;

    @Autowired
    public ChatController(ZhiPuAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 演员-电影 结构化输出记录。
     * <p>
     * 使用 Java record 定义不可变数据结构，{@link JsonPropertyOrder} 注解指定 JSON 序列化时
     * 字段顺序为 actor -> movies，便于大模型按固定格式输出，降低解析失败概率。
     *
     * @param actor  演员/导演名称
     * @param movies 该演员/导演参与的电影名称列表
     */
    @JsonPropertyOrder({"actor", "movies"})
    record ActorsFilms(String actor, List<String> movies) {
    }

    /**
     * 基于 ChatClient 的高层 API 实现结构化输出（自动 Bean 映射）。
     * <p>
     * 工作流程：
     * <ol>
     *     <li>通过 {@link PromptTemplate} 构建包含 {actor} 占位符的提示词</li>
     *     <li>调用 {@link ChatClient#prompt(Prompt)} 创建 ChatClient 并发送请求</li>
     *     <li>使用 {@link ChatClient.CallResponseSpec#entity(Class)} 将大模型返回的 JSON 文本
     *         自动反序列化为 {@link ActorsFilms} record 对象</li>
     * </ol>
     * <p>
     * 该方式利用 ChatClient 内置的 {@link BeanOutputConverter} 能力，
     * 框架会自动在 Prompt 中注入 JSON Schema 格式约束，无需手动拼接格式说明。
     *
     * @param actor 演员/导演名称，请求参数名 "actor"，默认值 "周星驰"
     * @return 包含演员名及其对应五部电影名的 {@link ActorsFilms} 对象；若模型无法返回有效结构则可能抛出解析异常
     */
    @GetMapping("/ai/generate")
    public ActorsFilms generate(@RequestParam(value = "actor", defaultValue = "周星驰") String actor) {
        PromptTemplate template = new PromptTemplate("帮我返回五个{actor}导演的电影名，要求中文返回");
        Prompt prompt = template.create(Map.of("actor", actor));
        ChatClient.CallResponseSpec res = ChatClient.create(chatModel).prompt(prompt).call();
        ActorsFilms films = res.entity(ActorsFilms.class);
        return films;
    }

    /**
     * 基于 {@link BeanOutputConverter} 手动实现结构化输出。
     * <p>
     * 工作流程：
     * <ol>
     *     <li>创建 {@link BeanOutputConverter} 实例，通过 {@link BeanOutputConverter#getFormat()} 获取
     *         JSON Schema 格式约束字符串（包含目标类的字段描述与 JSON 示例）</li>
     *     <li>将格式约束 {format} 作为占位符注入到 {@link PromptTemplate} 中，引导大模型按指定 JSON 格式输出</li>
     *     <li>直接调用 {@link ZhiPuAiChatModel#call(Prompt)} 获取原始 {@link Generation} 响应</li>
     *     <li>从 Generation 中提取文本，通过 {@link BeanOutputConverter#convert(String)} 手动解析为 Java 对象</li>
     * </ol>
     * <p>
     * 与 {@link #generate} 相比，此方式更底层：需要手动构建格式提示、调用模型、解析结果，
     * 适用于需要精细控制 Prompt 内容或进行自定义后处理的场景。
     *
     * @param actor 演员/导演名称，请求参数名 "actor"，默认值 "周星驰"
     * @return 解析后的 {@link ActorsFilms} 对象；若模型返回空结果则返回 null
     */
    @GetMapping("/ai/gen2")
    public ActorsFilms gen2(@RequestParam(value = "actor", defaultValue = "周星驰") String actor) {
        BeanOutputConverter<ActorsFilms> beanOutputConverter = new BeanOutputConverter<>(ActorsFilms.class);
        String format = beanOutputConverter.getFormat();

        PromptTemplate template = new PromptTemplate("""
                    帮我返回五个{actor}导演的电影名
                    {format}
                """);
        Prompt prompt = template.create(Map.of("actor", actor, "format", format));
        Generation generation = chatModel.call(prompt).getResult();
        if (generation == null) {
            return null;
        }
        System.out.println(generation.getOutput().getText());
        return beanOutputConverter.convert(generation.getOutput().getText());
    }

    /**
     * 基于 ChatClient + {@link ParameterizedTypeReference} 实现 List 类型的结构化输出。
     * <p>
     * 使用 ChatClient 的流式 Builder API，通过 Lambda 表达式 {@code user(u -> ...)} 构建用户消息，
     * 并利用 {@link ParameterizedTypeReference} 保留泛型类型信息，将大模型返回的 JSON 数组
     * 自动反序列化为 {@code List<ActorsFilms>}。
     * <p>
     * 此接口接收两位演员/导演参数，同时返回两人的电影列表，演示了多参数场景下的集合类型映射。
     *
     * @param actor1 第一位演员/导演名称，请求参数名 "actor1"，默认值 "周星驰"
     * @param actor2 第二位演员/导演名称，请求参数名 "actor2"，默认值 "张艺谋"
     * @return 包含两位演员各自电影信息的 {@link ActorsFilms} 列表
     */
    @GetMapping("/ai/genList")
    public List<ActorsFilms> genList(@RequestParam(value = "actor1", defaultValue = "周星驰") String actor1,
                                     @RequestParam(value = "actor2", defaultValue = "张艺谋") String actor2) {
        List<ActorsFilms> actorsFilms = ChatClient.create(chatModel).prompt()
                .user(u ->
                        u.text("帮我返回五个{actor1}和{actor2}导演的电影名，要求中文返回")
                                .params(Map.of("actor1", actor1, "actor2", actor2)))
                .call()
                .entity(new ParameterizedTypeReference<List<ActorsFilms>>() {
                });
        return actorsFilms;
    }

    /**
     * 基于 ChatClient + {@link ParameterizedTypeReference} 实现 Map 类型的结构化输出。
     * <p>
     * 与 {@link #genList} 类似，利用 {@link ParameterizedTypeReference} 指定目标类型为
     * {@code Map<String, Object>}，将大模型返回的 JSON 对象自动解析为键值对结构。
     * <p>
     * 返回的 Map 中 key 为字段名（如 "actor"、"movies"），value 为对应的值，
     * 适用于不需要强类型绑定、希望以动态结构接收结果的场景。
     *
     * @param actor 演员/导演名称，请求参数名 "actor"，默认值 "周星驰"
     * @return 包含演员及电影信息的 {@link Map} 对象，key 为字段名，value 为对应值
     */
    @GetMapping("/ai/genMap")
    public Map genMap(@RequestParam(value = "actor", defaultValue = "周星驰") String actor) {
        Map<String, Object> actorsFilms = ChatClient.create(chatModel).prompt()
                .user(u ->
                        u.text("帮我返回五个{actor}导演的电影名，要求中文返回")
                                .param("actor", actor))
                .call()
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        return actorsFilms;
    }

    /**
     * 基于 {@link MapOutputConverter} 手动实现 Map 类型的结构化输出。
     * <p>
     * 工作流程：
     * <ol>
     *     <li>创建 {@link MapOutputConverter} 实例，通过 {@link MapOutputConverter#getFormat()} 获取
     *         JSON Schema 格式约束字符串</li>
     *     <li>将格式约束注入 {@link PromptTemplate}，引导大模型输出符合 Map 结构的 JSON</li>
     *     <li>调用 {@link ZhiPuAiChatModel#call(Prompt)} 获取原始响应</li>
     *     <li>通过 {@link MapOutputConverter#convert(String)} 将响应文本手动解析为 {@code Map<String, Object>}</li>
     * </ol>
     * <p>
     * 与 {@link #genMap} 的区别：此方式手动控制格式约束与解析过程，
     * 适合需要在解析前后进行自定义处理的场景。
     *
     * @param actor 演员/导演名称，请求参数名 "actor"，默认值 "周星驰"
     * @return 包含演员及电影信息的 {@link Map} 对象
     */
    @GetMapping("/ai/genMap2")
    public Map genMap2(@RequestParam(value = "actor", defaultValue = "周星驰") String actor) {
        MapOutputConverter mapOutputConverter = new MapOutputConverter();

        String format = mapOutputConverter.getFormat();
        PromptTemplate template = new PromptTemplate("""
                    帮我返回五个{actor}导演的电影名，要求中文返回
                    {format}
                """);
        Prompt prompt = template.create(Map.of("actor", actor, "format", format));
        Generation generation = chatModel.call(prompt).getResult();
        Map<String, Object> result = mapOutputConverter.convert(generation.getOutput().getText());
        return result;
    }

    /**
     * 基于 ChatClient + {@link ListOutputConverter} 实现 List 类型的结构化输出（自动解析）。
     * <p>
     * 使用 ChatClient 高层 API，通过 {@link ListOutputConverter} 作为实体转换器，
     * 将大模型返回的逗号分隔或 JSON 数组格式的文本自动解析为 {@code List<String>}。
     * <p>
     * 与 {@link #genList} 的区别：此接口返回的是简单的字符串列表（仅电影名），
     * 而非嵌套的 Bean 对象列表，适用于只需获取一维数据的场景。
     *
     * @param actor 演员/导演名称，请求参数名 "actor"，默认值 "周星驰"
     * @return 电影名称的 {@link List} 列表
     */
    @GetMapping("/ai/genList1")
    public List<String> genList1(@RequestParam(value = "actor", defaultValue = "周星驰") String actor) {
        List<String> actorsFilms = ChatClient.create(chatModel).prompt()
                .user(u ->
                        u.text("帮我返回五个{actor}导演的电影名，要求中文返回")
                                .param("actor", actor))
                .call()
                .entity(new ListOutputConverter(new DefaultConversionService()));
        return actorsFilms;
    }

    /**
     * 基于 {@link ListOutputConverter} 手动实现 List 类型的结构化输出。
     * <p>
     * 工作流程：
     * <ol>
     *     <li>创建 {@link ListOutputConverter} 实例（需传入 {@link DefaultConversionService} 用于类型转换）</li>
     *     <li>通过 {@link ListOutputConverter#getFormat()} 获取输出格式约束字符串</li>
     *     <li>将格式约束注入 {@link PromptTemplate}，引导大模型按 CSV 或 JSON 数组格式输出</li>
     *     <li>调用 {@link ZhiPuAiChatModel#call(Prompt)} 获取原始响应</li>
     *     <li>通过 {@link ListOutputConverter#convert(String)} 将响应文本手动解析为 {@code List<String>}</li>
     * </ol>
     * <p>
     * 与 {@link #genList1} 的区别：此方式手动控制格式约束与解析过程，
     * 适合需要在解析前后进行自定义处理或日志记录的场景。
     *
     * @param actor 演员/导演名称，请求参数名 "actor"，默认值 "周星驰"
     * @return 电影名称的 {@link List} 列表
     */
    @GetMapping("/ai/genList2")
    public List genList2(@RequestParam(value = "actor", defaultValue = "周星驰") String actor) {
        ListOutputConverter listOutputConverter = new ListOutputConverter(new DefaultConversionService());

        String format = listOutputConverter.getFormat();
        PromptTemplate template = new PromptTemplate("""
                    帮我返回五个{actor}导演的电影名，要求中文返回
                    {format}
                """);
        Prompt prompt = template.create(Map.of("actor", actor, "format", format));
        Generation generation = chatModel.call(prompt).getResult();
        List<String> result = listOutputConverter.convert(generation.getOutput().getText());
        return result;
    }
}