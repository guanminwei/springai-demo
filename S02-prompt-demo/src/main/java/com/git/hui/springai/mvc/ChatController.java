package com.git.hui.springai.mvc;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

/**
 * @author YiHui
 * @date 2025/7/11
 */
@RestController
public class ChatController {

    private final ZhiPuAiChatModel chatModel;

    // 从 classpath 加载外部模板文件 system-message.st
    // 使用 .st 后缀表示 StringTemplate 格式，模板内容与代码完全解耦
    // 修改提示词时无需重新编译代码，适合需要频繁调优 prompt 的场景
    @Value("classpath:/prompts/system-message.st")
    private Resource systemResource;

    @Autowired
    public ChatController(ZhiPuAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * temperature 参数作用：
     * 用于控制生成文本的随机性或创造性。
     * 数值范围一般在 0.0 到 1.0 之间（有时也可超出该范围）：
     * 当 temperature 接近 0.0 时，输出会趋于确定性和保守，通常选择概率最高的词；
     * 当 temperature 接近 1.0 或更高时，输出更具多样性和创造性，可能会选择低概率但更有趣的词。
     * 示例中设置为 0.7d，表示适度平衡确定性与多样性。
     * <p>
     * user 参数作用：
     * 用于标识请求的发起者，通常是用户的唯一标识符（如用户名、ID 等）。
     * 主要用途包括：
     * 日志记录和审计：便于追踪哪个用户触发了此次 AI 调用；
     * 配额管理：某些平台依据 user 字段进行使用量统计与限制；
     * 行为分析：用于后续的数据分析或个性化推荐等场景。
     * 示例中设置为 "一灰灰"，可能代表当前请求来源的用户身份标识。
     *
     * @param message
     * @return
     */
    @GetMapping("/ai/generate")
    public Map generate(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        // 1. 构建 Prompt 对象：将用户消息文本与模型配置选项绑定
        //    - model: 指定使用智谱 GLM-4.7-Flash 轻量模型
        //    - temperature(0.7): 控制输出随机性，0.7 在确定性与创造性之间取平衡
        //    - user("一灰灰"): 标识请求来源用户，用于日志审计与配额管理
        Prompt prompt = new Prompt(message,
                ZhiPuAiChatOptions.builder()
                        .model(ZhiPuAiApi.ChatModel.GLM_4_Flash.getValue())
                        .temperature(0.7d)
                        .user("一灰灰")
                        .build()
        );

        // 2. 调用大模型同步接口，获取第一个生成结果（Generation）
        Generation generation = chatModel.call(prompt).getResult();
        // 3. 将 AI 生成的文本内容以 Map 形式返回，key 为 "generation"
        return Map.of("generation", generation == null ? "" : generation.getOutput().getText());
    }

    /**
     * 面向儿童的聊天助手接口。
     * 通过 SystemMessage 设定 AI 角色为「专注于给3-5岁儿童聊天的助手」，
     * 结合用户消息构建多消息 Prompt，使模型在回答时遵循该角色设定。
     *
     * @param message 用户输入的消息，默认值为 "Tell me a joke"
     * @return 包含 AI 生成回复的 Map
     */
    @GetMapping("/ai/childGenerate")
    public Map childJokeGenerate(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        // 1. 构建包含两条消息的 Prompt：
        //    - SystemMessage: 设定 AI 的角色为「专注于给3-5岁儿童聊天的助手」，
        //      模型会据此调整语言风格（用词简单、语气温柔、内容适合幼儿）
        //    - UserMessage: 用户实际发送的消息内容
        //    多消息列表方式是将系统指令和用户输入显式组装，适合角色设定较简短的场景
        Prompt prompt = new Prompt(
                Arrays.asList(new SystemMessage("你现在是一个专注于给3-5岁儿童聊天的助手"), new UserMessage(message)),
                ZhiPuAiChatOptions.builder()
                        .model(ZhiPuAiApi.ChatModel.GLM_4_Flash.getValue())
                        .temperature(0.7d)
                        .user("一灰灰")
                        .build()
        );
        // 2. 调用智谱大模型，获取生成结果
        Generation generation = chatModel.call(prompt).getResult();

        // 注意：此行代码构建了一个未使用的 Prompt 对象，属于示例演示代码（演示另一种角色设定写法），不影响返回结果
        Prompt p = new Prompt(new SystemMessage("你现在扮演一个可爱的女朋友角色，我扮演你的男朋友"), new UserMessage("你好"));
        // 3. 返回 AI 生成的回复文本
        return Map.of("generation", generation == null ? "" : generation.getOutput().getText());
    }


    /**
     * 角色扮演聊天接口（基础版）。
     * 使用 SystemPromptTemplate 动态填充角色设定参数（性格、AI角色、用户角色），
     * 生成系统消息后与用户消息组合成 Prompt 发送给模型。
     *
     * @param personality AI 扮演角色的性格特征，默认 "温柔"
     * @param aiRole      AI 扮演的角色名称，默认 "女朋友"
     * @param myRole      用户扮演的角色名称，默认 "男朋友"
     * @param msg         用户发送的消息，默认 "最近心情不好"
     * @return AI 生成的角色回复文本
     */
    @GetMapping(path = "/ai/roleChat")
    public String roleChat(@RequestParam(value = "personality", defaultValue = "温柔") String personality,
                           @RequestParam(value = "aiRole", defaultValue = "女朋友") String aiRole,
                           @RequestParam(value = "myRole", defaultValue = "男朋友") String myRole,
                           @RequestParam(value = "msg", defaultValue = "最近心情不好") String msg) {
        // 1. 创建 SystemPromptTemplate，模板中使用 {占位符} 语法定义动态参数
        //    默认使用 '{{' '}}' 作为定界符，此处简写为 {personality} 等形式
        SystemPromptTemplate promptTemplate = new SystemPromptTemplate("我们现在开始角色扮演的聊天，你来扮演{personality}的{aiRole}, 我来扮演{myRole}");
        // 2. 通过 createMessage() 将 Map 中的参数值填充到模板，生成 SystemMessage
        //    例如: personality="温柔", aiRole="女朋友" → "你来扮演温柔的女朋友"
        Message systemMsg = promptTemplate.createMessage(Map.of("personality", personality, "aiRole", aiRole, "myRole", myRole));

        // 3. 将系统消息（角色设定）与用户消息（对话内容）组合为完整 Prompt
        Prompt prompt = new Prompt(systemMsg, new UserMessage(msg));

        // 4. 调用模型并返回 AI 基于角色设定生成的回复
        Generation generation = chatModel.call(prompt).getResult();
        return generation == null ? "" : generation.getOutput().getText();
    }

    /**
     * 角色扮演聊天接口（自定义模板渲染器版）。
     * 使用 StTemplateRenderer 指定 '<' 和 '>' 作为变量定界符（替代默认的 '{{' '}}'），
     * 通过 PromptTemplate.builder() 链式构建模板并渲染系统消息，
     * 展示 Spring AI 中自定义模板渲染器的用法。
     *
     * @param personality AI 扮演角色的性格特征，默认 "温柔"
     * @param aiRole      AI 扮演的角色名称，默认 "女朋友"
     * @param myRole      用户扮演的角色名称，默认 "男朋友"
     * @param msg         用户发送的消息，默认 "最近心情不好"
     * @return AI 生成的角色回复文本
     */
    @GetMapping(path = "/ai/roleChatV2")
    public String roleChatV2(@RequestParam(value = "personality", defaultValue = "温柔") String personality,
                             @RequestParam(value = "aiRole", defaultValue = "女朋友") String aiRole,
                             @RequestParam(value = "myRole", defaultValue = "男朋友") String myRole,
                             @RequestParam(value = "msg", defaultValue = "最近心情不好") String msg) {
        // 1. 使用 PromptTemplate.builder() 链式构建模板，核心区别在于自定义了模板渲染器
        //    StTemplateRenderer 基于 StringTemplate 库，将变量定界符设为 '<' 和 '>'
        //    因此模板中的变量必须写成 <personality> 形式，而非 {personality}
        //    渲染器严格按照定界符匹配占位符，不会自动适配花括号语法
        //    自定义渲染器的意义在于：当模板内容包含大量 '{' '}' 字符（如 JSON 示例）时，使用其他定界符可避免冲突
        PromptTemplate promptTemplate = PromptTemplate.builder().renderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<').endDelimiterToken('>').build())
                .template("我们现在开始角色扮演的对话，你来扮演<personality>的<aiRole>, 我来扮演<myRole>")
                .build();
        // 2. render() 将参数 Map 填充到模板中，返回渲染后的纯文本字符串
        String text = promptTemplate.render(Map.of("personality", personality, "aiRole", aiRole, "myRole", myRole));
        // 3. 将渲染后的文本包装为 SystemMessage，与用户消息组合成 Prompt
        Prompt prompt = new Prompt(new SystemMessage(text), new UserMessage(msg));

        // 4. 调用模型获取角色化回复
        Generation generation = chatModel.call(prompt).getResult();
        return generation == null ? "" : generation.getOutput().getText();
    }

    /**
     * 角色扮演聊天接口（外部模板文件版）。
     * 从 classpath 下的 system-message.st 文件加载系统提示词模板，
     * 通过 SystemPromptTemplate 解析 Resource 并填充动态参数，
     * 实现模板与代码分离，便于独立维护和修改提示词内容。
     * 修改提示词时无需重新编译代码，适合需要频繁调优 prompt 的场景
     *
     * @param personality AI 扮演角色的性格特征，默认 "温柔"
     * @param aiRole      AI 扮演的角色名称，默认 "女朋友"
     * @param myRole      用户扮演的角色名称，默认 "男朋友"
     * @param msg         用户发送的消息，默认 "最近心情不好"
     * @return AI 生成的角色回复文本
     */
    @GetMapping(path = "/ai/roleChatV3")
    public String roleChatV3(@RequestParam(value = "personality", defaultValue = "温柔") String personality,
                             @RequestParam(value = "aiRole", defaultValue = "女朋友") String aiRole,
                             @RequestParam(value = "myRole", defaultValue = "男朋友") String myRole,
                             @RequestParam(value = "msg", defaultValue = "最近心情不好") String msg) {
        // 1. 使用注入的 Resource 创建 SystemPromptTemplate
        //    模板内容来自 src/main/resources/prompts/system-message.st 文件
        //    文件中同样包含 {personality}、{aiRole}、{myRole} 等占位符
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
        // 2. createMessage() 读取模板文件内容并将参数值填充到占位符，生成 SystemMessage
        Message text = systemPromptTemplate.createMessage(Map.of("personality", personality, "aiRole", aiRole, "myRole", myRole));
        // 3. 系统消息（来自外部模板文件）+ 用户消息 → 完整 Prompt
        Prompt prompt = new Prompt(text, new UserMessage(msg));

        // 4. 调用模型并返回回复
        Generation generation = chatModel.call(prompt).getResult();
        return generation == null ? "" : generation.getOutput().getText();
    }
}