package com.git.hui.offer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 讯飞星火 Spark API 响应数据模型定义
 * <p>
 * 使用 Java Record（JDK 16+）定义不可变数据载体，并通过 Jackson 注解实现与星火 API JSON 字段的精确映射。
 * 该接口作为星火 API 响应数据的 POJO 层，供 {@link POJOConvert} 进行对象转换时使用。
 * </p>
 * <p>
 * 星火 API 官方文档：
 * <a href="https://www.xfyun.cn/doc/spark/HTTP%E8%B0%83%E7%94%A8%E6%96%87%E6%A1%A3.html">星火 HTTP 调用文档</a>
 * </p>
 *
 * @author YiHui
 * @date 2025/7/21
 */
public interface SparkPOJO {

    /**
     * 星火 API 聊天响应主体（对应 JSON 根对象）
     * <p>
     * 字段说明：
     * <ul>
     *     <li>code    - 错误码，0 表示请求成功，非 0 表示出错</li>
     *     <li>message - 错误码对应的描述信息</li>
     *     <li>sid     - 本次请求的唯一标识，可用于日志追踪和问题排查</li>
     *     <li>choices - 大模型返回的候选结果列表（通常只有一个）</li>
     *     <li>usage   - 本次请求的 Token 消耗信息</li>
     * </ul>
     * </p>
     *
     * @param code    错误码，0 表示成功
     * @param message 错误描述信息
     * @param sid     请求唯一 ID
     * @param choices 大模型返回的候选结果列表
     * @param usage   Token 消耗统计
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)  // 序列化时忽略 null 字段，减少无效数据传输
    @JsonIgnoreProperties(ignoreUnknown = true) // 反序列化时忽略未知字段，增强向前兼容性
    record ChatCompletionChunk(
            Integer code,
            String message,
            String sid,
            List<Choice> choices,
            Usage usage) {
    }

    /**
     * 大模型返回的单个候选结果
     *
     * @param index   候选结果的索引位置（通常为 0）
     * @param message 大模型生成的消息内容
     */
    record Choice(Integer index, SparkMsg message) {
    }

    /**
     * 大模型生成的消息体
     *
     * @param role    角色标识，通常为 "assistant"
     * @param content 大模型生成的文本内容
     */
    record SparkMsg(String role, String content) {
    }


    /**
     * Token 消耗统计信息
     * <p>
     * 实现了 Spring AI 的 {@link org.springframework.ai.chat.metadata.Usage} 接口，
     * 使得星火 API 的用量数据能够无缝集成到 Spring AI 的 ChatResponseMetadata 体系中。
     * </p>
     * <p>
     * 通过 {@link JsonProperty} 注解将下划线命名风格的 JSON 字段（如 completion_tokens）
     * 映射到驼峰命名风格的 Java 字段（如 completionTokens）。
     * </p>
     *
     * @param completionTokens 大模型生成内容消耗的 Token 数
     * @param promptTokens     用户输入（Prompt）消耗的 Token 数
     * @param totalTokens      总 Token 消耗数（promptTokens + completionTokens）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(Integer completionTokens, Integer promptTokens,
                 Integer totalTokens) implements org.springframework.ai.chat.metadata.Usage {

        /**
         * 带 Jackson 注解的构造函数，将 JSON 下划线字段映射到 record 参数
         *
         * @param completionTokens 生成内容 Token 数（JSON 字段: completion_tokens）
         * @param promptTokens     输入 Prompt Token 数（JSON 字段: prompt_tokens）
         * @param totalTokens      总 Token 数（JSON 字段: total_tokens）
         */
        public Usage(@JsonProperty("completion_tokens") Integer completionTokens,
                     @JsonProperty("prompt_tokens") Integer promptTokens,
                     @JsonProperty("total_tokens") Integer totalTokens) {
            this.completionTokens = completionTokens;
            this.promptTokens = promptTokens;
            this.totalTokens = totalTokens;
        }

        /** 获取生成内容消耗的 Token 数 */
        @JsonProperty("completion_tokens")
        public Integer completionTokens() {
            return this.completionTokens;
        }

        /** 获取输入 Prompt 消耗的 Token 数 */
        @JsonProperty("prompt_tokens")
        public Integer promptTokens() {
            return this.promptTokens;
        }

        /** 获取总 Token 消耗数 */
        @JsonProperty("total_tokens")
        public Integer totalTokens() {
            return this.totalTokens;
        }

        /** 实现 Spring AI Usage 接口：获取输入 Token 数 */
        @Override
        public Integer getPromptTokens() {
            return promptTokens;
        }

        /** 实现 Spring AI Usage 接口：获取生成 Token 数 */
        @Override
        public Integer getCompletionTokens() {
            return completionTokens;
        }

        /**
         * 实现 Spring AI Usage 接口：获取原始用量数据
         * <p>
         * 返回一个 Map 包含所有 Token 消耗指标，可用于日志记录或自定义监控。
         * </p>
         *
         * @return 包含 promptTokens、completionTokens、totalTokens 的 Map
         */
        @Override
        public Object getNativeUsage() {
            Map<String, Integer> usage = new HashMap<>();
            usage.put("promptTokens", this.promptTokens());
            usage.put("completionTokens", this.completionTokens());
            usage.put("totalTokens", this.totalTokens());
            return usage;
        }
    }
}
