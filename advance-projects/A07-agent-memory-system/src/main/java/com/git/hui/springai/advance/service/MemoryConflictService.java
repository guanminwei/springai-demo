package com.git.hui.springai.advance.service;

import com.git.hui.springai.advance.domain.AgentMemory;
import com.git.hui.springai.advance.domain.ArchivedMemory;
import com.git.hui.springai.advance.domain.MemoryStatus;
import com.git.hui.springai.advance.repository.AgentMemoryRepository;
import com.git.hui.springai.advance.repository.ArchivedMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 矛盾检测服务 — 检测新旧记忆冲突并自动归档旧记忆
 * <p>
 * 当新提取的记忆与已有记忆在类型和关键词上重叠时，
 * 调用 LLM 判断两者是否矛盾。若矛盾，旧记忆移入归档表（reason='conflict'）。
 *
 * @author YiHui
 * @date 2025/7/29
 */
@Service
public class MemoryConflictService {

    private static final Logger log = LoggerFactory.getLogger(MemoryConflictService.class);

    private final AgentMemoryRepository memoryRepository;
    private final ArchivedMemoryRepository archivedRepository;
    private final ChatModel chatModel;

    public MemoryConflictService(AgentMemoryRepository memoryRepository,
                                 ArchivedMemoryRepository archivedRepository,
                                 ChatModel chatModel) {
        this.memoryRepository = memoryRepository;
        this.archivedRepository = archivedRepository;
        this.chatModel = chatModel;
    }

    /**
     * 检测并解决矛盾 — 新记忆写入前调用
     *
     * @param newMemory 待写入的新记忆
     * @return 被归档的旧记忆数量
     */
    @Transactional
    public int detectAndResolve(AgentMemory newMemory) {
        if (newMemory.getKeywords() == null || newMemory.getKeywords().isBlank()) {
            return 0;
        }

        // 查找同类型 + 关键词有交集的旧记忆
        List<AgentMemory> candidates = memoryRepository.findByUserIdAndStatusAndMemoryType(
                newMemory.getUserId(), MemoryStatus.ACTIVE, newMemory.getMemoryType());

        int archived = 0;
        for (AgentMemory oldMemory : candidates) {
            if (hasKeywordOverlap(newMemory.getKeywords(), oldMemory.getKeywords())) {
                if (isConflicting(newMemory, oldMemory)) {
                    archiveMemory(oldMemory, "conflict");
                    archived++;
                    log.info("矛盾检测：归档旧记忆 [id={}, title={}]", oldMemory.getId(), oldMemory.getTitle());
                }
            }
        }
        return archived;
    }

    /**
     * LLM 判断新旧记忆是否矛盾
     */
    boolean isConflicting(AgentMemory newMemory, AgentMemory oldMemory) {
        String prompt = String.format("""
                判断以下两条记忆是否矛盾（即不能同时为真）。
                
                旧记忆: %s
                新记忆: %s
                
                如果矛盾（例如用户之前偏好A，现在偏好B），返回 "true"。
                如果不矛盾（两者可以共存），返回 "false"。
                只返回 true 或 false，不要解释。
                """, oldMemory.getContent(), newMemory.getContent());

        try {
            ChatResponse response = chatModel.call(new Prompt(prompt));
            String result = response.getResult().getOutput().getText().trim().toLowerCase();
            return result.contains("true");
        } catch (Exception e) {
            log.warn("LLM 矛盾判断失败，保守处理：不归档", e);
            return false;
        }
    }

    /**
     * 将记忆移入归档表
     */
    @Transactional
    public void archiveMemory(AgentMemory memory, String reason) {
        ArchivedMemory archived = ArchivedMemory.from(memory, reason);
        archivedRepository.save(archived);
        memoryRepository.delete(memory);
    }

    /**
     * 检查两个关键词集合是否有交集
     */
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
}
