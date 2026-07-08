package com.git.hui.offer.model;

import com.git.hui.offer.util.JsonUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI 对象与讯飞星火 API 对象之间的转换层
 * <p>
 * 在自定义大模型接入场景中，Spring AI 框架内部统一使用 {@link Prompt}、{@link Generation}、
 * {@link ChatResponse} 等标准对象，而第三方大模型 API 有自己独立的请求/响应格式。
 * 本类负责在两套对象体系之间进行双向转换：
 * <ul>
 *     <li><b>请求方向</b>：Spring AI Prompt → 星火 API JSON 请求体（{@link #toReq}）</li>
 *     <li><b>响应方向</b>：星火 API 响应 → Spring AI Generation / ChatResponseMetadata（{@link #generationList}、{@link #from}）</li>
 * </ul>
 * </p>
 * <p>
 * 官方文档请求参数说明：
 * <a href="https://www.xfyun.cn/doc/spark/HTTP%E8%B0%83%E7%94%A8%E6%96%87%E6%A1%A3.html#_3-%E8%AF%B7%E6%B1%82%E8%AF%B4%E6%98%8E">星火 HTTP 调用文档</a>
 * </p>
 *
 * @author YiHui
 * @date 2025/7/21
 */
public class POJOConvert {

    /**
     * 将星火 API 响应中的 choices 列表转换为 Spring AI 的 Generation 列表
     * <p>
     * 每个 {@link SparkPOJO.Choice} 会被映射为一个 {@link Generation} 对象，
     * 其中包含大模型生成的文本内容和元数据信息（如请求 ID、角色、结束原因等）。
     * </p>
     *
     * @param completionChunk 星火 API 返回的完整响应对象
     * @return Spring AI Generation 列表，每个元素对应一个候选结果
     */
    public static List<Generation> generationList(SparkPOJO.ChatCompletionChunk completionChunk) {
        return completionChunk.choices().stream().map(choice -> {
            // 构建每个 Generation 的元数据，包含请求 ID、角色、索引和结束原因
            Map<String, Object> metadata = Map.of(
                    "id", completionChunk.sid(),                          // 请求唯一标识
                    "role", choice.message().role(),                      // 角色（通常为 "assistant"）
                    "index", choice.index(),                              // 候选结果索引
                    "finishReason", completionChunk.code() == 0 ? "over" : "error" // 根据错误码判断结束原因
            );
            return buildGeneration(choice, metadata);

        }).toList();
    }

    /**
     * 根据星火 API 的 Choice 和元数据构建 Spring AI 的 Generation 对象
     * <p>
     * Generation 是 Spring AI 中表示大模型单次生成结果的标准对象，包含：
     * <ul>
     *     <li>{@link AssistantMessage} - 大模型生成的消息内容</li>
     *     <li>{@link ChatGenerationMetadata} - 生成过程的元数据（如结束原因）</li>
     * </ul>
     * </p>
     *
     * @param choice   星火 API 返回的单个候选结果
     * @param metadata 附加的元数据信息
     * @return Spring AI Generation 对象
     */
    public static Generation buildGeneration(SparkPOJO.Choice choice, Map<String, Object> metadata) {
        // 构建 AssistantMessage：包含大模型生成的文本内容和元数据属性
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(choice.message().content())  // 大模型生成的文本内容
                .properties(metadata)                 // 附加元数据，可通过 getProperties() 获取
                .build();
        // 构建生成元数据：主要包含结束原因（finishReason），用于判断生成是否正常完成
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason((String) metadata.get("finishReason"))
                .build();
        // 组装并返回 Generation 对象
        return new Generation(assistantMessage, generationMetadata);
    }

    /**
     * 构建 Spring AI 的 ChatResponseMetadata，封装响应级别的元信息
     * <p>
     * ChatResponseMetadata 包含请求 ID、模型名称、Token 消耗等全局信息，
     * 与具体的生成内容（Generation）分离，符合 Spring AI 的分层设计。
     * </p>
     *
     * @param reqTime 请求发起时间戳（毫秒），用于记录响应创建时间
     * @param model   使用的模型名称（如 "lite"）
     * @param result  星火 API 返回的完整响应对象
     * @return Spring AI ChatResponseMetadata 对象
     */
    public static ChatResponseMetadata from(Long reqTime, String model, SparkPOJO.ChatCompletionChunk result) {
        Assert.notNull(result, "SparkLite ChatCompletionResult must not be null");
        return ChatResponseMetadata.builder()
                .id(result.sid() != null ? result.sid() : "")  // 请求唯一 ID，若为 null 则使用空字符串
                // Token 消耗信息：若星火 API 未返回 usage，则使用 EmptyUsage 占位
                .usage((Usage) (result.usage() != null ? result.usage() : new EmptyUsage()))
                .model(model)                                   // 模型名称
                .keyValue("created", reqTime)                   // 请求创建时间戳
                .build();
    }

    /**
     * 将 Spring AI 的 Prompt 对象转换为星火 API 的 JSON 请求体字符串
     * <p>
     * 转换过程：
     * <ol>
     *     <li>提取模型名称（优先使用 Prompt 中指定的模型，否则使用默认模型）</li>
     *     <li>提取 ChatOptions 中的生成参数（temperature、top_p、top_k 等）</li>
     *     <li>遍历 Prompt 中的消息列表，转换为星火 API 要求的 messages 格式</li>
     *     <li>使用 {@link JsonUtil} 将 Map 序列化为 JSON 字符串</li>
     * </ol>
     * </p>
     *
     * @param prompt       Spring AI 的 Prompt 对象，包含用户消息和生成参数
     * @param defaultModel 默认模型名称，当 Prompt 中未指定模型时使用
     * @return 星火 API 请求体的 JSON 字符串
     */
    public static String toReq(Prompt prompt, String defaultModel) {
        Map<String, Object> map = new HashMap<>();

        // 1. 设置模型名称：优先使用 Prompt 中指定的模型，否则使用默认值
        map.put("model", (prompt.getOptions() == null || prompt.getOptions().getModel() == null)
                ? defaultModel : prompt.getOptions().getModel());
        // 2. 设置是否为流式响应（当前实现为同步调用，设为 false）
        map.put("stream", false);

        // 3. 提取 ChatOptions 中的生成参数
        ChatOptions options = prompt.getOptions();
        if (options != null) {
            // 核采样阈值，取值范围 [0, 2]，默认值 1.0
            // 控制生成结果的多样性，值越大结果越随机
            map.put("temperature", options.getTemperature());

            // 概率阈值，取值范围 (0, 1]，默认值 1
            // 仅保留累积概率大于等于该值的最小 token 集合作为候选集
            map.put("top_p", options.getTopP());

            // 随机采样数，取值范围 [1, 6]，默认值 4
            // 从 k 个候选 token 中随机选择一个（非等概率）
            map.put("top_k", options.getTopK());

            // 重复词惩罚值，取值范围 [-2.0, 2.0]，默认 0
            // 正值鼓励生成新内容，负值允许重复
            map.put("presence_penalty", options.getPresencePenalty());

            // 频率惩罚值，取值范围 [-2.0, 2.0]，默认 0
            // 根据词频进行惩罚，减少高频词的重复出现
            map.put("frequency_penalty", options.getFrequencyPenalty());

            // 最大生成 Token 数
            map.put("max_tokens", options.getMaxTokens());

            // todo 等待补齐 function tool 的能力支持
        }


        // 4. 将 Spring AI 的消息列表转换为星火 API 要求的 messages 格式
        List<Map> msgs = new ArrayList<>();
        for (Message message : prompt.getInstructions()) {
            // 每条消息转换为 {role, content} 格式的 Map
            // role: 消息角色（system/user/assistant），转为小写
            // content: 消息文本内容
            Map msg = Map.of(
                    "role", message.getMessageType().getValue().toLowerCase(),
                    "content", message.getText()
            );
            msgs.add(msg);
        }
        map.put("messages", msgs);

        // 5. 序列化为 JSON 字符串
        String body = JsonUtil.toStr(map);
        System.out.println("请求参数:" + body);
        return body;
    }
}
