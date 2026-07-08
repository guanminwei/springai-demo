package com.git.hui.offer.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 序列化工具类
 * <p>
 * 基于 Jackson 的 ObjectMapper 封装，提供 Java 对象与 JSON 字符串之间的相互转换能力。
 * 在 Spring AI 自定义大模型接入场景中，常用于：
 * <ul>
 *     <li>将 Prompt 对象序列化为 JSON 字符串，用于构造 HTTP 请求体</li>
 *     <li>将大模型返回的 JSON 响应反序列化为 Java 对象，便于后续业务处理</li>
 * </ul>
 * </p>
 *
 * <b>注意事项：</b>
 * <ul>
 *     <li>每次调用均会创建新的 ObjectMapper 实例，适用于低频调用场景；
 *         若用于高频场景，建议将 ObjectMapper 抽取为单例 Bean 以提升性能</li>
 *     <li>序列化/反序列化失败时，统一抛出 RuntimeException，调用方需按需捕获处理</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/7/21
 */
public class JsonUtil {

    /**
     * 将 Java 对象序列化为 JSON 字符串
     * <p>
     * 内部使用 Jackson ObjectMapper 的 writeValueAsString 方法，
     * 将任意 Java 对象（如 Map、POJO、List 等）转换为标准的 JSON 格式字符串。
     * </p>
     *
     * @param prompt 待序列化的 Java 对象，通常为构建好的请求参数对象（如 Prompt、Message 等）
     * @return JSON 格式字符串；若对象为 null，则返回 "null"
     * @throws RuntimeException 当序列化过程发生异常时（如对象包含不可序列化的字段），
     *                          包装原始异常后抛出
     */
    public static String toStr(Object prompt) {
        // 创建 ObjectMapper 实例，它是 Jackson 的核心入口类，负责 JSON 的读写操作
        ObjectMapper mapper = new ObjectMapper();
        try {
            // writeValueAsString: 将 Java 对象转换为 JSON 字符串
            return mapper.writeValueAsString(prompt);
        } catch (Exception e) {
            // 将受检异常（JsonProcessingException）包装为运行时异常，简化调用方的异常处理
            throw new RuntimeException(e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型的 Java 对象
     * <p>
     * 内部使用 Jackson ObjectMapper 的 readValue 方法，
     * 将 JSON 格式的字符串解析为目标 Class 对应的 Java 对象实例。
     * </p>
     *
     * @param str   JSON 格式字符串，通常来自大模型 API 的响应体
     * @param clazz 目标类型的 Class 对象，用于指定反序列化的目标类型（如自定义 POJO、Map 等）
     * @param <T>   泛型参数，表示返回值类型，由 clazz 参数决定
     * @return 反序列化后的 Java 对象实例
     * @throws RuntimeException 当反序列化过程发生异常时（如 JSON 格式错误、字段类型不匹配等），
     *                          包装原始异常后抛出
     */
    public static <T> T fromStr(String str, Class<T> clazz) {
        // 创建 ObjectMapper 实例
        ObjectMapper mapper = new ObjectMapper();
        try {
            // readValue: 将 JSON 字符串按照指定 Class 反序列化为对应的 Java 对象
            return mapper.readValue(str, clazz);
        } catch (Exception e) {
            // 将受检异常（JsonProcessingException / IOException）包装为运行时异常
            throw new RuntimeException(e);
        }
    }
}
