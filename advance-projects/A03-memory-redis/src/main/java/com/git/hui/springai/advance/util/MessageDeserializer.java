package com.git.hui.springai.advance.util;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
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

    private final Map<String, Function<String, Message>> msgFactor = Map.of(
            "USER", UserMessage::new,
            "SYSTEM", UserMessage::new,
            "ASSISTANT", UserMessage::new
    );

    @Override
    public Message deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        JsonNode node = p.getCodec().readTree(p);
        // If node is plain text, create a UserMessage by default
        if (node.isTextual()) {
            return new UserMessage(node.asText());
        }

        // Extract message type
        String type = extractMessageType(node);

        // Extract content
        String content = extractContent(node);

        // Create corresponding message object based on type
        return Optional.ofNullable(type).map(String::toUpperCase).map(msgFactor::get).orElseGet(() -> {
            if (type == null) {
                logger.warn("Message type not found, defaulting to USER");
            } else {
                logger.warn("Unknown message type: {}, defaulting to USER", type);
            }
            return msgFactor.get("USER");
        }).apply(content);
    }

    /**
     * 获取消息类型
     */
    private String extractMessageType(JsonNode node) {
        return Optional.ofNullable(node.get("messageType"))
                .map(JsonNode::asText)
                .orElse(null);
    }

    /**
     * 获取消息内容
     */
    private String extractContent(JsonNode node) {
        return Optional.ofNullable(node.get("text"))
                .map(JsonNode::asText)
                .orElseGet(node::toString);
    }
}
