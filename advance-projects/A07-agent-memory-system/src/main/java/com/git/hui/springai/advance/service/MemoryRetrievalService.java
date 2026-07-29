package com.git.hui.springai.advance.service;

import com.git.hui.springai.advance.domain.AgentMemory;
import com.git.hui.springai.advance.domain.MemoryStatus;
import com.git.hui.springai.advance.repository.AgentMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆检索服务 - SQL 硬过滤 + LLM 语义选择
 * <p>
 * 检索流程分两步：
 * <ol>
 *     <li>SQL 硬过滤：按 user_id + status='active' + keywords LIKE 缩小候选集到 ≤20 条</li>
 *     <li>LLM 选择：将候选索引（id + title + keywords）交给 LLM，选出 top-N 最相关的记忆</li>
 * </ol>
 * 被选中的记忆会刷新 access_count 和 last_accessed_at。
 *
 * @author YiHui
 * @date 2025/7/29
 */
@Service
public class MemoryRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(MemoryRetrievalService.class);

    private final AgentMemoryRepository memoryRepository;
    private final ChatModel chatModel;

    @Value("${app.memory.retrieval-max-candidates:20}")
    private int maxCandidates;

    @Value("${app.memory.retrieval-top-n:5}")
    private int topN;

    public MemoryRetrievalService(AgentMemoryRepository memoryRepository, ChatModel chatModel) {
        this.memoryRepository = memoryRepository;
        this.chatModel = chatModel;
    }

    /**
     * 检索与用户查询最相关的长期记忆
     *
     * @param userId 用户标识
     * @param query  用户当前的查询内容
     * @return 格式化的记忆注入文本（带时间警告），若无相关记忆则返回空字符串
     */
    @Transactional
    public String retrieveAndFormat(String userId, String query) {
        List<AgentMemory> candidates = findCandidates(userId, query);
        if (candidates.isEmpty()) {
            return "";
        }

        List<AgentMemory> selected = selectTopN(candidates, query);
        if (selected.isEmpty()) {
            return "";
        }

        // 刷新访问计数
        List<Long> ids = selected.stream().map(AgentMemory::getId).collect(Collectors.toList());
        memoryRepository.incrementAccessCount(ids);

        return formatMemories(selected);
    }

    /**
     * Step 1: SQL 硬过滤 — 从用户查询中提取关键词，查找候选记忆
     */
    List<AgentMemory> findCandidates(String userId, String query) {
        // 从查询中提取关键词（简单分词：按空格、标点分割）
        List<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            // 无有效关键词时，返回该用户所有活跃记忆（按 importance 排序）
            List<AgentMemory> all = memoryRepository.findByUserIdAndStatus(userId, MemoryStatus.ACTIVE);
            return all.stream().limit(maxCandidates).collect(Collectors.toList());
        }

        // 对每个关键词做 LIKE 查询，合并去重
        Set<Long> seen = new HashSet<>();
        List<AgentMemory> candidates = new ArrayList<>();

        for (String keyword : keywords) {
            List<AgentMemory> matches = memoryRepository.findCandidatesByKeyword(userId, keyword);
            for (AgentMemory m : matches) {
                if (seen.add(m.getId()) && candidates.size() < maxCandidates) {
                    candidates.add(m);
                }
            }
        }

        // 按 importance 降序排序
        candidates.sort(Comparator.comparing(AgentMemory::getImportance).reversed());
        return candidates.stream().limit(maxCandidates).collect(Collectors.toList());
    }

    /**
     * Step 2: LLM 语义选择 — 从候选集中选出最相关的 top-N 条记忆
     */
    List<AgentMemory> selectTopN(List<AgentMemory> candidates, String query) {
        if (candidates.size() <= topN) {
            return candidates;
        }

        // 构建候选索引文本
        StringBuilder indexText = new StringBuilder();
        for (AgentMemory m : candidates) {
            indexText.append(String.format("[%d] %s (类型:%s, 关键词:%s)\n",
                    m.getId(), m.getTitle(), m.getMemoryType(), m.getKeywords()));
        }

        String prompt = String.format("""
                你是一个记忆选择助手。根据用户的问题，从以下候选记忆中选出最相关的 %d 条。
                只返回选中的记忆ID，用逗号分隔的数字格式（如：3,7,12）。
                如果没有相关记忆，返回"none"。
                
                用户问题: %s
                
                候选记忆:
                %s
                """, topN, query, indexText);

        try {
            ChatResponse response = chatModel.call(new Prompt(prompt));
            String result = response.getResult().getOutput().getText().trim();

            if ("none".equalsIgnoreCase(result)) {
                return Collections.emptyList();
            }

            // 解析返回的 ID 列表
            Set<Long> selectedIds = Arrays.stream(result.split("[,，\\s]+"))
                    .filter(s -> !s.isBlank())
                    .map(Long::parseLong)
                    .collect(Collectors.toSet());

            return candidates.stream()
                    .filter(m -> selectedIds.contains(m.getId()))
                    .limit(topN)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("LLM 选择失败，回退到按 importance 取 top-{}", topN, e);
            return candidates.stream().limit(topN).collect(Collectors.toList());
        }
    }

    /**
     * 格式化记忆为注入 Prompt 的文本（带时间警告）
     */
    String formatMemories(List<AgentMemory> memories) {
        StringBuilder sb = new StringBuilder("\n## 历史记忆\n");
        sb.append("以下是从历史对话中检索到的相关记忆，请在回答时参考：\n\n");

        for (AgentMemory m : memories) {
            long daysSinceCreated = ChronoUnit.DAYS.between(m.getCreatedAt(), java.time.LocalDateTime.now());
            String timeWarning = daysSinceCreated > 7
                    ? String.format("[记忆于%d天前创建, 可能已过时]", daysSinceCreated)
                    : String.format("[记忆于%d天前创建]", daysSinceCreated);

            sb.append(String.format("- %s (importance:%.2f, 类型:%s)\n  %s\n\n",
                    timeWarning,
                    m.getImportance().doubleValue(),
                    m.getMemoryType(),
                    m.getContent()));
        }

        return sb.toString();
    }

    /**
     * 从查询文本中提取关键词（简单实现）
     */
    List<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        // 按空格和常见标点分割，过滤短词
        return Arrays.stream(query.split("[\\s,，。！？、；;：:!?()（）\\[\\]{}]+"))
                .map(String::trim)
                .filter(s -> s.length() >= 2)
                .distinct()
                .limit(5) // 最多取 5 个关键词
                .collect(Collectors.toList());
    }
}
