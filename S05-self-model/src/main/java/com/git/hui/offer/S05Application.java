package com.git.hui.offer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * S05 自定义大模型接入 模块启动类
 * <p>
 * 本模块演示如何不依赖 Spring AI 内置的模型适配器（如 OpenAI、Ollama 等），
 * 而是通过实现 Spring AI 的 {@link org.springframework.ai.chat.model.ChatModel} 接口，
 * 手动对接第三方大模型 API（此处为讯飞星火 Spark Lite），完成自定义大模型接入。
 * </p>
 * <p>
 * 核心流程：
 * <pre>
 *   用户请求 → ChatController → ChatClient → SparkLiteModel（自定义 ChatModel）
 *                                         → POJOConvert（请求/响应对象转换）
 *                                         → SparkPOJO（星火 API 数据模型）
 *                                         → RestClient（HTTP 调用星火 API）
 * </pre>
 * </p>
 *
 * @author YiHui
 * @date 2025/7/11
 */
@SpringBootApplication
public class S05Application {
    public static void main(String[] args) {
        SpringApplication.run(S05Application.class, args);
    }
}