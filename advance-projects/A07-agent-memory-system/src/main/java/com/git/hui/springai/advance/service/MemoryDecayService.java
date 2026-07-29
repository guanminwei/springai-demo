package com.git.hui.springai.advance.service;

import com.git.hui.springai.advance.domain.AgentMemory;
import com.git.hui.springai.advance.domain.MemoryStatus;
import com.git.hui.springai.advance.repository.AgentMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 记忆衰减服务 — 访问驱动的衰减机制
 * <p>
 * 衰减规则：
 * <ul>
 *     <li>90天未被访问 且 access_count < 2 → 归档（reason='decay'）</li>
 *     <li>30天未被访问 且 access_count < 5 → importance *= 0.8（降权但不归档）</li>
 * </ul>
 * importance 降权后不低于 0.01，防止浮点下溢。
 *
 * @author YiHui
 * @date 2025/7/29
 */
@Service
public class MemoryDecayService {

    private static final Logger log = LoggerFactory.getLogger(MemoryDecayService.class);
    private static final BigDecimal MIN_IMPORTANCE = new BigDecimal("0.01");

    private final AgentMemoryRepository memoryRepository;
    private final MemoryConflictService conflictService;

    @Value("${app.memory.archive-after-days:90}")
    private int archiveAfterDays;

    @Value("${app.memory.demote-after-days:30}")
    private int demoteAfterDays;

    @Value("${app.memory.archive-min-access:2}")
    private int archiveMinAccess;

    @Value("${app.memory.demote-min-access:5}")
    private int demoteMinAccess;

    @Value("${app.memory.demote-factor:0.8}")
    private double demoteFactor;

    public MemoryDecayService(AgentMemoryRepository memoryRepository,
                              MemoryConflictService conflictService) {
        this.memoryRepository = memoryRepository;
        this.conflictService = conflictService;
    }

    /**
     * 执行一轮衰减计算
     *
     * @return 衰减结果统计：{archived: N, demoted: M}
     */
    @Transactional
    public DecayResult runDecay() {
        List<AgentMemory> activeMemories = memoryRepository.findByStatus(MemoryStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();

        int archived = 0;
        int demoted = 0;

        for (AgentMemory memory : activeMemories) {
            long daysSinceAccess = ChronoUnit.DAYS.between(memory.getLastAccessedAt(), now);

            // 规则1: 长期未访问 + 低访问次数 → 归档
            if (daysSinceAccess > archiveAfterDays && memory.getAccessCount() < archiveMinAccess) {
                conflictService.archiveMemory(memory, "decay");
                archived++;
                continue;
            }

            // 规则2: 中期未访问 + 中访问次数 → 降权
            if (daysSinceAccess > demoteAfterDays && memory.getAccessCount() < demoteMinAccess) {
                BigDecimal newImportance = memory.getImportance()
                        .multiply(BigDecimal.valueOf(demoteFactor))
                        .setScale(2, RoundingMode.HALF_UP);

                // 防止下溢
                if (newImportance.compareTo(MIN_IMPORTANCE) < 0) {
                    newImportance = MIN_IMPORTANCE;
                }

                memory.setImportance(newImportance);
                memoryRepository.save(memory);
                demoted++;
            }
        }

        log.info("衰减计算完成：归档 {} 条，降权 {} 条", archived, demoted);
        return new DecayResult(archived, demoted);
    }

    /**
     * 衰减结果
     */
    public record DecayResult(int archived, int demoted) {
        @Override
        public String toString() {
            return String.format("归档: %d 条, 降权: %d 条", archived, demoted);
        }
    }
}
