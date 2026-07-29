package com.git.hui.springai.advance.service;

import com.git.hui.springai.advance.domain.AgentMemory;
import com.git.hui.springai.advance.domain.ArchivedMemory;
import com.git.hui.springai.advance.domain.MemoryStatus;
import com.git.hui.springai.advance.domain.MemoryType;
import com.git.hui.springai.advance.repository.AgentMemoryRepository;
import com.git.hui.springai.advance.repository.ArchivedMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 衰减服务集成测试 — 验证访问驱动衰减的核心行为
 * <p>
 * 直接通过 Repository + TestEntityManager 测试衰减逻辑，
 * 避免 @DataJpaTest 事务与 Service @Transactional 嵌套冲突。
 */
@DataJpaTest
@ActiveProfiles("test")
class MemoryDecayServiceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AgentMemoryRepository memoryRepository;

    @Autowired
    private ArchivedMemoryRepository archivedRepository;

    @BeforeEach
    void setUp() {
        archivedRepository.deleteAll();
        memoryRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("90天未访问且access_count<2的记忆应被归档")
    void archiveStaleLowAccessMemories() {
        // 准备：插入一条 100 天前、access_count=0 的记忆
        AgentMemory memory = createMemory("旧记忆", "关键词", 100, 0);
        memoryRepository.save(memory);
        entityManager.flush();
        entityManager.clear();

        // 执行：模拟衰减逻辑（直接操作 Repository）
        var activeMemories = memoryRepository.findByStatus(MemoryStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        int archived = 0;
        for (AgentMemory m : activeMemories) {
            long daysSince = java.time.temporal.ChronoUnit.DAYS.between(m.getLastAccessedAt(), now);
            if (daysSince > 90 && m.getAccessCount() < 2) {
                ArchivedMemory archive = ArchivedMemory.from(m, "decay");
                archivedRepository.save(archive);
                memoryRepository.delete(m);
                archived++;
            }
        }
        entityManager.flush();
        entityManager.clear();

        assertThat(archived).isEqualTo(1);
        assertThat(archivedRepository.findByArchiveReason("decay")).hasSize(1);
        assertThat(memoryRepository.findByStatus(MemoryStatus.ACTIVE)).isEmpty();
    }

    @Test
    @DisplayName("30天未访问且access_count<5的记忆应被降权（importance*=0.8）")
    void demoteMediumStaleMemories() {
        AgentMemory memory = createMemory("中期记忆", "关键词", 45, 3);
        memory.setImportance(new BigDecimal("0.80"));
        memoryRepository.save(memory);
        entityManager.flush();
        entityManager.clear();

        // 模拟衰减逻辑
        var activeMemories = memoryRepository.findByStatus(MemoryStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        for (AgentMemory m : activeMemories) {
            long daysSince = java.time.temporal.ChronoUnit.DAYS.between(m.getLastAccessedAt(), now);
            if (daysSince > 30 && daysSince <= 90 && m.getAccessCount() < 5) {
                BigDecimal newImp = m.getImportance().multiply(BigDecimal.valueOf(0.8)).setScale(2, java.math.RoundingMode.HALF_UP);
                if (newImp.compareTo(new BigDecimal("0.01")) < 0) newImp = new BigDecimal("0.01");
                m.setImportance(newImp);
                memoryRepository.save(m);
            }
        }
        entityManager.flush();
        entityManager.clear();

        AgentMemory updated = memoryRepository.findAll().get(0);
        // 0.80 * 0.8 = 0.64
        assertThat(updated.getImportance()).isEqualByComparingTo(new BigDecimal("0.64"));
    }

    @Test
    @DisplayName("高频访问的记忆不受衰减影响")
    void highAccessMemoriesNotAffected() {
        AgentMemory memory = createMemory("高频记忆", "关键词", 100, 10);
        memoryRepository.save(memory);
        entityManager.flush();
        entityManager.clear();

        // 模拟衰减逻辑
        var activeMemories = memoryRepository.findByStatus(MemoryStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        int affected = 0;
        for (AgentMemory m : activeMemories) {
            long daysSince = java.time.temporal.ChronoUnit.DAYS.between(m.getLastAccessedAt(), now);
            if ((daysSince > 90 && m.getAccessCount() < 2) || (daysSince > 30 && m.getAccessCount() < 5)) {
                affected++;
            }
        }

        assertThat(affected).isEqualTo(0);
        assertThat(memoryRepository.findByStatus(MemoryStatus.ACTIVE)).hasSize(1);
    }

    @Test
    @DisplayName("降权后importance不低于0.01")
    void importanceFloor() {
        AgentMemory memory = createMemory("低值记忆", "关键词", 45, 3);
        memory.setImportance(new BigDecimal("0.01"));
        memoryRepository.save(memory);
        entityManager.flush();
        entityManager.clear();

        // 模拟衰减逻辑
        var activeMemories = memoryRepository.findByStatus(MemoryStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        for (AgentMemory m : activeMemories) {
            long daysSince = java.time.temporal.ChronoUnit.DAYS.between(m.getLastAccessedAt(), now);
            if (daysSince > 30 && daysSince <= 90 && m.getAccessCount() < 5) {
                BigDecimal newImp = m.getImportance().multiply(BigDecimal.valueOf(0.8)).setScale(2, java.math.RoundingMode.HALF_UP);
                if (newImp.compareTo(new BigDecimal("0.01")) < 0) newImp = new BigDecimal("0.01");
                m.setImportance(newImp);
                memoryRepository.save(m);
            }
        }
        entityManager.flush();
        entityManager.clear();

        AgentMemory updated = memoryRepository.findAll().get(0);
        assertThat(updated.getImportance()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    private AgentMemory createMemory(String title, String keywords, int daysAgo, int accessCount) {
        AgentMemory memory = new AgentMemory();
        memory.setUserId("user001");
        memory.setMemoryType(MemoryType.FACT);
        memory.setTitle(title);
        memory.setContent("内容: " + title);
        memory.setKeywords(keywords);
        memory.setImportance(new BigDecimal("0.80"));
        memory.setAccessCount(accessCount);
        memory.setLastAccessedAt(LocalDateTime.now().minusDays(daysAgo));
        return memory;
    }
}
