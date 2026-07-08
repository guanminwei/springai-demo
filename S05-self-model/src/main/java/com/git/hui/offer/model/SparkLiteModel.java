package com.git.hui.offer.model;

import com.git.hui.offer.util.JsonUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Consumer;

/**
 * 自定义 ChatModel 实现：对接讯飞星火 Spark Lite 大模型
 * <p>
 * 本类是 S05 模块的核心，通过实现 Spring AI 的 {@link ChatModel} 接口，
 * 将讯飞星火 Spark Lite API 接入 Spring AI 框架体系。
 * 实现后，上层应用（如 ChatClient、Advisor）可以像使用 OpenAI 一样透明地调用星火大模型。
 * </p>
 * <p>
 * <b>核心职责：</b>
 * <ol>
 *     <li>初始化 HTTP 客户端（RestClient），配置 API 地址和鉴权信息</li>
 *     <li>提供默认模型配置（{@link #getDefaultOptions}）</li>
 *     <li>实现同步调用逻辑（{@link #call}）：将 Prompt 转换为星火 API 请求，调用 API，并将响应转换为 ChatResponse</li>
 * </ol>
 * </p>
 * <p>
 * <b>调用链路：</b>
 * <pre>
 *   ChatClient.call(prompt)
 *     → SparkLiteModel.call(prompt)           // 本类
 *       → POJOConvert.toReq(prompt, model)    // 将 Prompt 转换为星火 JSON 请求体
 *       → RestClient.post().body(...).retrieve() // HTTP POST 调用星火 API
 *       → JsonUtil.fromStr(res, ...)          // 反序列化响应 JSON
 *       → POJOConvert.generationList(...)     // 将星火响应转换为 Spring AI Generation
 *       → new ChatResponse(generations, metadata) // 组装最终响应
 * </pre>
 * </p>
 *
 * @author YiHui
 * @date 2025/7/21
 */
@Component
public class SparkLiteModel implements ChatModel {
    private final static Logger log = LoggerFactory.getLogger(SparkLiteModel.class);

    /** 讯飞星火 Spark API 的 HTTP 接口地址（OpenAI 兼容风格） */
    private final static String URL = "https://spark-api-open.xf-yun.com/v1/chat/completions";

    /** Spring 的 RestClient，用于发送 HTTP 请求，在 @PostConstruct 中初始化 */
    private RestClient restClient;

    /** 星火 API 的鉴权密钥，从配置文件 spring.ai.spark.api-key 读取 */
    @Value("${spring.ai.spark.api-key:}")
    private String apiKey;

    /** 默认使用的模型名称，从配置文件 spring.ai.spark.chat.options.model 读取，默认值为 "lite" */
    @Value("${spring.ai.spark.chat.options.model:lite}")
    private String model;

    /**
     * 初始化 RestClient，配置 API 基础地址和鉴权请求头
     * <p>
     * 使用 {@link PostConstruct} 确保在 Spring Bean 初始化完成后执行，
     * 此时 @Value 注入的 apiKey 已可用。
     * </p>
     */
    @PostConstruct
    public void init() {
        // 定义请求头配置：设置 Bearer Token 鉴权和 Content-Type
        Consumer<HttpHeaders> authHeaders = (h) -> {
            h.setBearerAuth(apiKey);                        // Bearer Token 鉴权
            h.setContentType(MediaType.APPLICATION_JSON);   // 请求体格式为 JSON
        };

        // 构建 RestClient：设置基础 URL 和默认请求头
        this.restClient = RestClient.builder()
                .baseUrl(URL)
                .defaultHeaders(authHeaders)
                .build();
    }

    /**
     * 返回默认的 ChatOptions 配置
     * <p>
     * 当调用方未显式指定模型参数时，Spring AI 框架会使用此处返回的默认配置。
     * 当前仅配置了模型名称，其他参数（如 temperature、top_p 等）使用星火 API 的默认值。
     * </p>
     *
     * @return 包含默认模型名称的 ChatOptions 对象
     */
    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder()
                .model(model)  // 使用配置文件中的模型名称（默认 "lite"）
                .build();
    }

    /**
     * 同步调用星火大模型 API，返回 ChatResponse
     * <p>
     * 实现步骤：
     * <ol>
     *     <li>记录请求发起时间，用于后续构建 ChatResponseMetadata</li>
     *     <li>确定使用的模型名称（Prompt 指定 > 默认配置）</li>
     *     <li>通过 {@link POJOConvert#toReq} 将 Prompt 转换为星火 API 的 JSON 请求体</li>
     *     <li>通过 RestClient 发送 HTTP POST 请求，获取响应字符串</li>
     *     <li>通过 {@link JsonUtil#fromStr} 将响应 JSON 反序列化为 {@link SparkPOJO.ChatCompletionChunk}</li>
     *     <li>通过 {@link POJOConvert#generationList} 将星火响应转换为 Spring AI Generation 列表</li>
     *     <li>组装并返回 {@link ChatResponse}</li>
     * </ol>
     * </p>
     *
     * @param prompt Spring AI 的 Prompt 对象，包含用户消息和可选的生成参数
     * @return Spring AI 标准的 ChatResponse 对象，包含生成结果和元数据
     */
    @Override
    public ChatResponse call(Prompt prompt) {
        // 1. 记录请求发起时间戳（毫秒）
        Long reqTime = System.currentTimeMillis();

        // 2. 确定模型名称：优先使用 Prompt 中指定的模型，否则使用默认配置
        String model = (prompt.getOptions() == null || prompt.getOptions().getModel() == null)
                ? this.model : prompt.getOptions().getModel();

        // 3. 发送 HTTP POST 请求到星火 API
        //    POJOConvert.toReq: 将 Spring AI Prompt 转换为星火 API 的 JSON 请求体
        //    restClient.post().body(...).retrieve().body(String.class): 发送请求并获取响应字符串
        String res = restClient.post()
                .body(POJOConvert.toReq(prompt, model))
                .retrieve()
                .body(String.class);

        // 4. 将响应 JSON 反序列化为星火 API 的响应对象
        SparkPOJO.ChatCompletionChunk chatCompletionChunk = JsonUtil.fromStr(res, SparkPOJO.ChatCompletionChunk.class);

        // 5. 将星火响应转换为 Spring AI 的 Generation 列表
        List<Generation> generations = POJOConvert.generationList(chatCompletionChunk);

        // 6. 组装 ChatResponse：包含生成结果列表和响应元数据
        ChatResponse response = new ChatResponse(generations, POJOConvert.from(reqTime, model, chatCompletionChunk));
        return response;
    }
}
