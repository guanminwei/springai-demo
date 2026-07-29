package com.git.hui.springai.advance.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Spring AI Message 自定义反序列化器
 * <p>
 * 由于 {@link Message} 是接口类型，Jackson 默认无法直接反序列化。本类通过解析 JSON 中的
 * {@code messageType} 字段判断消息类型，并创建对应的 Message 实现类实例。
 * <p>
 * 反序列化策略：
 * <ul>
 *     <li>纯文本节点 → 默认创建 UserMessage</li>
 *     <li>对象节点 → 根据 messageType 字段（USER/SYSTEM/ASSISTANT）创建对应消息</li>
 *     <li>未知类型或缺失类型 → 降级为 UserMessage 并记录警告日志</li>
 * </ul>
 * <p>
 * 注意事项：
 * <ul>
 *     <li>当前实现将所有类型统一映射为 UserMessage（简化演示）</li>
 *     <li>生产环境建议根据 messageType 分别创建 UserMessage、SystemMessage、AssistantMessage</li>
 *     <li>消息内容从 JSON 的 {@code text} 字段提取，若不存在则使用整个节点的 toString</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/8/7
 * @see Message
 * @see JsonUtil
 */
public class MessageDeserializer extends JsonDeserializer<Message> {
    private static final Logger logger = LoggerFactory.getLogger(MessageDeserializer.class);

    // 消息类型枚举
    public enum MessageType {
        USER, SYSTEM, ASSISTANT
    }

    // 工厂映射，支持后续添加新类型
    private final Map<MessageType, Function<String, Message>> factoryMap = new EnumMap<>(MessageType.class);

    public MessageDeserializer() {
        // 注册默认类型（可改为注入或外部配置）
        factoryMap.put(MessageType.USER, UserMessage::new);
        factoryMap.put(MessageType.SYSTEM, SystemMessage::new);
        factoryMap.put(MessageType.ASSISTANT, AssistantMessage::new);
        // 还可添加其它类型...
    }

    @Override
    public Message deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        // 1. 纯文本 → UserMessage
        if (node.isTextual()) {
            return new UserMessage(node.asText());
        }

        // 2. 非对象结构 → 报错
        if (!node.isObject()) {
            throw new InvalidFormatException(p, "Expected JSON Object or Text, got " + node.getNodeType(), node, Message.class);
        }

        // 3. 提取类型和内容（可配置字段名）
        String typeStr = extractField(node, "messageType");
        String content = extractField(node, "text");

        // 若内容缺失，可降级为使用整个节点的字符串表示（但可能不精确，此处选择使用空字符串）
        if (content == null) {
            logger.warn("Message content field 'text' missing, using empty string");
            content = "";
        }

        // 4. 解析类型并获取工厂
        MessageType messageType = resolveMessageType(typeStr);
        Function<String, Message> factory = factoryMap.get(messageType);
        if (factory == null) {
            // 理论上不会发生，因为 resolveMessageType 已保证非 null
            throw new IllegalStateException("No factory registered for " + messageType);
        }

        return factory.apply(content);
    }

    /**
     * 从节点中提取字段值，若字段不存在或为空节点则返回 null
     */
    private String extractField(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        if (!fieldNode.isTextual()) {
            logger.warn("Field '{}' is not textual, converting to string", fieldName);
            return fieldNode.toString();
        }
        return fieldNode.asText();
    }

    /**
     * 解析类型字符串，未知类型降级为 USER 并记录警告
     */
    private MessageType resolveMessageType(String typeStr) {
        if (typeStr == null) {
            logger.warn("Message type missing, defaulting to USER");
            return MessageType.USER;
        }
        try {
            return MessageType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown message type '{}', defaulting to USER", typeStr);
            return MessageType.USER;
        }
    }

    // 若需支持动态注册新类型，可提供注册方法
    public void registerFactory(MessageType type, Function<String, Message> factory) {
        factoryMap.put(type, factory);
    }
}