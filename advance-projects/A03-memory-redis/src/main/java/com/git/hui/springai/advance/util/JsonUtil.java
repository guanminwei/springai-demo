package com.git.hui.springai.advance.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.ai.chat.messages.Message;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

/**
 * JSON 序列化工具类 - 专为 Spring AI Message 对象设计
 * <p>
 * 本工具类封装了 Jackson ObjectMapper，提供对象与 JSON 字符串之间的转换能力。
 * 针对 Spring AI 的 {@link Message} 接口类型，注册了自定义反序列化器 {@link MessageDeserializer}，
 * 以正确处理 UserMessage、AssistantMessage、SystemMessage 等多态类型。
 * <p>
 * 配置说明：
 * <ul>
 *     <li>自动发现并注册 Jackson 模块（如 Java 8 时间模块等）</li>
 *     <li>注册 Message 自定义反序列化器，解决接口类型无法直接实例化的问题</li>
 *     <li>禁用 FAIL_ON_UNKNOWN_PROPERTIES，忽略 JSON 中未映射的字段（增强兼容性）</li>
 * </ul>
 * <p>
 * 使用场景：
 * <ul>
 *     <li>Redis 存储对话消息时的序列化/反序列化</li>
 *     <li>日志输出、调试打印等辅助场景</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/7/14
 * @see MessageDeserializer
 */
public class JsonUtil {
    private static ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.findAndRegisterModules();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Message.class, new MessageDeserializer());
        mapper.registerModule(module);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    }

    /**
     * 对象转字符串
     *
     * @param o 对象
     * @return 字符串
     */
    public static String toStr(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * JSON 字符串转对象
     *
     * @param s     JSON 字符串
     * @param clazz 目标类型
     * @param <T>   泛型类型参数
     * @return 反序列化后的对象
     */
    public static <T> T toObj(String s, Class<T> clazz) {
        try {
            return mapper.readValue(s, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
