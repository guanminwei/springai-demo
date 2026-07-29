package com.git.hui.springai.advance.service;

import com.git.hui.springai.advance.domain.AgentMemory;
import com.git.hui.springai.advance.domain.MemoryStatus;
import com.git.hui.springai.advance.domain.MemoryType;
import com.git.hui.springai.advance.repository.AgentMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆合并服务 — 将同类碎片记忆合并为更完整的条目
 * <p>
 * 按 memory_type 分组，查找 keywords 重叠度高的记忆簇，
 * 调用 LLM 将碎片合并为一条更完整的记忆，旧记忆归档（reason='merge'）。
 *
 * @author YiHui
 * @date 2025/7/29
 */
@Service
public class MemoryMergeService {

    private static final Logger log = LoggerFactory.getLogger(MemoryMergeService.class);

    private final AgentMemoryRepository memoryRepository;
    private final MemoryConflictService conflictService;
    private final ChatModel chatModel;

    public MemoryMergeService(AgentMemoryRepository memoryRepository,
                              MemoryConflictService conflictService,
                              ChatModel chatModel) {
        this.memoryRepository = memoryRepository;
        this.conflictService = conflictService;
        this.chatModel = chatModel;
    }

    /**
     * 执行一轮记忆合并
     *
     * @return 合并的记忆簇数量
     */
    @Transactional
    public MergeResult runMerge() {
        List<AgentMemory> activeMemories = memoryRepository.findByStatus(MemoryStatus.ACTIVE);
        int merged = 0;

        // 按类型分组
        Map<MemoryType, List<AgentMemory>> byType = activeMemories.stream()
                .collect(Collectors.groupingBy(AgentMemory::getMemoryType));

        for (Map.Entry<MemoryType, List<AgentMemory>> entry : byType.entrySet()) {
            List<AgentMemory> memories = entry.getValue();
            if (memories.size() < 2) continue;

            // 查找 keywords 重叠的记忆簇
            List<List<AgentMemory>> clusters = findClusters(memories);

            for (List<AgentMemory> cluster : clusters) {
                if (cluster.size() >= 2) {
                    if (mergeCluster(cluster)) {
                        merged++;
                    }
                }
            }
        }

        log.info("记忆合并完成：合并 {} 个簇", merged);
        return new MergeResult(merged);
    }

    /**
     * 查找 keywords 重叠的记忆簇（简单聚类：关键词有交集的归为一组）
     */
    List<List<AgentMemory>> findClusters(List<AgentMemory> memories) {
        boolean[] visited = new boolean[memories.size()];
        List<List<AgentMemory>> clusters = new ArrayList<>();

        for (int i = 0; i < memories.size(); i++) {
            if (visited[i]) continue;

            List<AgentMemory> cluster = new ArrayList<>();
            cluster.add(memories.get(i));
            visited[i] = true;

            for (int j = i + 1; j < memories.size(); j++) {
                if (visited[j]) continue;
                if (hasKeywordOverlap(memories.get(i).getKeywords(), memories.get(j).getKeywords())) {
                    cluster.add(memories.get(j));
                    visited[j] = true;
                }
            }

            if (cluster.size() >= 2) {
                clusters.add(cluster);
            }
        }

        return clusters;
    }

    /**
     * LLM 合并一个记忆簇
     */
    boolean mergeCluster(List<AgentMemory> cluster) {
        StringBuilder contentList = new StringBuilder();
        BigDecimal maxImportance = BigDecimal.ZERO;
        String userId = cluster.get(0).getUserId();

        for (AgentMemory m : cluster) {
            contentList.append(String.format("- [%s] %s\n", m.getTitle(), m.getContent()));
            if (m.getImportance().compareTo(maxImportance) > 0) {
                maxImportance = m.getImportance();
            }
        }

        String prompt = String.format("""
                以下记忆描述了相似的内容，请将它们合并为一条更完整、更精炼的记忆。
                
                记忆列表：
                %s
                
                请返回 JSON 格式：
                {"title":"合并后的标题","content":"合并后的内容","keywords":"关键词1,关键词2"}
                """, contentList);

        try {
            ChatResponse response = chatModel.call(new Prompt(prompt));
            String result = response.getResult().getOutput().getText().trim();

            // 解析合并结果
            String title = parseField(result, "title");
            String content = parseField(result, "content");
            String keywords = parseField(result, "keywords");

            if (title == null || content == null) {
                log.warn("合并结果解析失败: {}", result);
                return false;
            }

            // 写入新记忆
            AgentMemory merged = new AgentMemory();
            merged.setUserId(userId);
            merged.setMemoryType(cluster.get(0).getMemoryType());
            merged.setTitle(title);
            merged.setContent(content);
            merged.setKeywords(keywords);
            merged.setImportance(maxImportance);
            merged.setStatus(MemoryStatus.ACTIVE);
            memoryRepository.save(merged);

            // 归档旧记忆
            for (AgentMemory old : cluster) {
                conflictService.archiveMemory(old, "merge");
            }

            log.info("合并 {} 条记忆为: {}", cluster.size(), title);
            return true;
        } catch (Exception e) {
            log.warn("记忆合并失败", e);
            return false;
        }
    }

    private String parseField(String json, String field) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    boolean hasKeywordOverlap(String keywords1, String keywords2) {
        if (keywords1 == null || keywords2 == null) return false;
        String[] k1 = keywords1.toLowerCase().split("[,，\\s]+");
        String[] k2 = keywords2.toLowerCase().split("[,，\\s]+");
        for (String a : k1) {
            for (String b : k2) {
                if (a.length() >= 2 && b.length() >= 2 && (a.contains(b) || b.contains(a))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 合并结果
     */
    public record MergeResult(int merged) {
        @Override
        public String toString() {
            return String.format("合并: %d 个簇", merged);
        }
    }
}
