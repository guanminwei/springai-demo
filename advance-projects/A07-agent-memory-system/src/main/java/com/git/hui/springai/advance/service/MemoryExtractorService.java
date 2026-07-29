package com.git.hui.springai.advance.service;

import com.git.hui.springai.advance.domain.AgentMemory;
import com.git.hui.springai.advance.domain.MemoryType;
import com.git.hui.springai.advance.domain.MemoryStatus;
import com.git.hui.springai.advance.repository.AgentMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记忆提取服务 — 从对话中 LLM 结构化抽取长期记忆
 * <p>
 * 工作流程：
 * <ol>
 *     <li>轻量规则预筛：检查对话内容是否包含偏好/事实/经验类关键词</li>
 *     <li>LLM 结构化抽取：输出 [{type, title, content, keywords, importance}] JSON</li>
 *     <li>矛盾检测：委托 {@link MemoryConflictService} 检查新旧记忆冲突</li>
 *     <li>幂等写入：通过 sourceMessageIds 避免重复写入</li>
 * </ol>
 *
 * @author YiHui
 * @date 2025/7/29
 */
@Service
public class MemoryExtractorService {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractorService.class);

    /** 触发提取的关键词集合 */
    private static final Set<String> TRIGGER_KEYWORDS = Set.of(
            "喜欢", "偏好", "习惯", "prefer", "like", "want",
            "项目", "技术栈", "框架", "版本", "使用",
            "问题", "错误", "bug", "坑", "经验", "教训",
            "记住", "记忆", "以后", "总是", "从不"
    );

    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[.*]", Pattern.DOTALL);

    private final AgentMemoryRepository memoryRepository;
    private final ChatModel chatModel;
    private final MemoryConflictService conflictService;

    public MemoryExtractorService(AgentMemoryRepository memoryRepository,
                                  ChatModel chatModel,
                                  MemoryConflictService conflictService) {
        this.memoryRepository = memoryRepository;
        this.chatModel = chatModel;
        this.conflictService = conflictService;
    }

    /**
     * 异步提取记忆 — 会话结束时调用
     *
     * @param userId         用户标识
     * @param conversationId 会话 ID
     * @param messages       对话消息列表（格式化的文本）
     */
    @Async
    @Transactional
    public void extractAsync(String userId, String conversationId, String messages) {
        try {
            extract(userId, conversationId, messages);
        } catch (Exception e) {
            log.error("记忆提取失败 [userId={}, conversationId={}]", userId, conversationId, e);
        }
    }

    /**
     * 同步提取记忆
     */
    @Transactional
    public List<AgentMemory> extract(String userId, String conversationId, String messages) {
        if (messages == null || messages.isBlank()) {
            return Collections.emptyList();
        }

        // Step 1: 轻量规则预筛
        if (!shouldExtract(messages)) {
            log.debug("对话内容不触发记忆提取 [userId={}]", userId);
            return Collections.emptyList();
        }

        // Step 2: LLM 结构化抽取
        List<AgentMemory> newMemories = extractWithLLM(userId, conversationId, messages);
        if (newMemories.isEmpty()) {
            return Collections.emptyList();
        }

        // Step 3: 矛盾检测 + 写入
        List<AgentMemory> saved = new ArrayList<>();
        for (AgentMemory memory : newMemories) {
            // 幂等检查
            if (memory.getSourceMessageIds() != null) {
                List<AgentMemory> existing = memoryRepository.findBySourceMessageIds(memory.getSourceMessageIds());
                if (!existing.isEmpty()) {
                    log.debug("记忆已存在，跳过写入: {}", memory.getTitle());
                    continue;
                }
            }

            // 矛盾检测
            conflictService.detectAndResolve(memory);
            saved.add(memoryRepository.save(memory));
        }

        log.info("为用户 [{}] 提取并写入 {} 条记忆", userId, saved.size());
        return saved;
    }

    /**
     * 轻量规则预筛：检查对话是否包含可提取记忆的关键词
     */
    boolean shouldExtract(String messages) {
        String lower = messages.toLowerCase();
        for (String keyword : TRIGGER_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        // 对话长度超过阈值也触发（可能包含隐含的偏好/事实）
        return messages.length() > 200;
    }

    /**
     * LLM 结构化抽取记忆
     */
    List<AgentMemory> extractWithLLM(String userId, String conversationId, String messages) {
        String prompt = String.format("""
                你是一个记忆提取助手。从以下对话中提取值得长期记住的信息。
                
                提取规则：
                1. 只提取有价值的信息（用户偏好、项目事实、经验教训、技能规则、重要事件）
                2. 每条记忆必须包含：type（类型）、title（标题）、content（内容）、keywords（关键词，逗号分隔）、importance（重要度0.0-1.0）
                3. type 只能是以下之一：USER_PREFERENCE, FACT, EXPERIENCE, SKILL, EVENT
                4. 不要提取假设性或临时性的信息
                5. 如果没有值得记住的信息，返回空数组 []
                
                对话内容：
                %s
                
                请以 JSON 数组格式返回，每条记忆格式：
                {"type":"FACT","title":"标题","content":"内容","keywords":"关键词1,关键词2","importance":0.8}
                """, messages);

        try {
            ChatResponse response = chatModel.call(new Prompt(prompt));
            String result = response.getResult().getOutput().getText().trim();
            return parseMemories(userId, conversationId, messages, result);
        } catch (Exception e) {
            log.warn("LLM 记忆提取失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析 LLM 返回的 JSON 为 AgentMemory 列表
     */
    List<AgentMemory> parseMemories(String userId, String conversationId, String sourceMessages, String jsonResult) {
        List<AgentMemory> memories = new ArrayList<>();

        // 提取 JSON 数组部分
        Matcher matcher = JSON_ARRAY_PATTERN.matcher(jsonResult);
        if (!matcher.find()) {
            return memories;
        }

        String jsonArray = matcher.group();
        if ("[]".equals(jsonArray.trim())) {
            return memories;
        }

        // 简单解析（生产环境应使用 Jackson）
        // 按 {" 分割每条记录
        String[] records = jsonArray.split("\\{");
        String sourceIds = conversationId + ":" + Math.abs(sourceMessages.hashCode());

        for (String record : records) {
            if (!record.contains("\"type\"")) continue;

            try {
                AgentMemory memory = new AgentMemory();
                memory.setUserId(userId);
                memory.setConversationId(conversationId);
                memory.setSourceMessageIds(sourceIds);

                // 解析各字段
                memory.setMemoryType(parseEnum(record, "type", MemoryType.FACT));
                memory.setTitle(parseString(record, "title"));
                memory.setContent(parseString(record, "content"));
                memory.setKeywords(parseString(record, "keywords"));
                memory.setImportance(parseDecimal(record, "importance", new BigDecimal("0.50")));
                memory.setStatus(MemoryStatus.ACTIVE);

                if (memory.getTitle() != null && memory.getContent() != null) {
                    memories.add(memory);
                }
            } catch (Exception e) {
                log.warn("解析单条记忆失败: {}", record, e);
            }
        }

        return memories;
    }

    private MemoryType parseEnum(String record, String field, MemoryType defaultVal) {
        String value = parseString(record, field);
        if (value == null) return defaultVal;
        try {
            return MemoryType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultVal;
        }
    }

    private String parseString(String record, String field) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(record);
        return m.find() ? m.group(1) : null;
    }

    private BigDecimal parseDecimal(String record, String field, BigDecimal defaultVal) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*([0-9.]+)");
        Matcher m = p.matcher(record);
        if (m.find()) {
            try {
                return new BigDecimal(m.group(1));
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }
        return defaultVal;
    }
}
