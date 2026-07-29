package com.git.hui.springai.advance.repository;

import com.git.hui.springai.advance.domain.AgentMemory;
import com.git.hui.springai.advance.domain.ArchivedMemory;
import com.git.hui.springai.advance.domain.MemoryStatus;
import com.git.hui.springai.advance.domain.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository 层集成测试 - 验证记忆存储、检索、归档的核心行为
 */
@DataJpaTest
@ActiveProfiles("test")
class AgentMemoryRepositoryTest {

    @Autowired
    private AgentMemoryRepository memoryRepository;

    @Autowired
    private ArchivedMemoryRepository archivedRepository;

    private AgentMemory sampleMemory;

    @BeforeEach
    void setUp() {
        archivedRepository.deleteAll();
        memoryRepository.deleteAll();

        sampleMemory = new AgentMemory();
        sampleMemory.setUserId("user001");
        sampleMemory.setConversationId("conv-001");
        sampleMemory.setMemoryType(MemoryType.FACT);
        sampleMemory.setTitle("Spring Boot 版本");
        sampleMemory.setContent("项目使用 Spring Boot 3.5 + Java 17");
        sampleMemory.setKeywords("Spring Boot,Java 17,版本");
        sampleMemory.setImportance(new BigDecimal("0.80"));
        sampleMemory.setSourceMessageIds("msg-001,msg-002");
    }

    @Nested
    @DisplayName("记忆写入与基本查询")
    class WriteAndQuery {

        @Test
        @DisplayName("写入一条记忆后，按用户和状态能查到")
        void saveAndFindByUserAndStatus() {
            memoryRepository.save(sampleMemory);

            List<AgentMemory> found = memoryRepository.findByUserIdAndStatus("user001", MemoryStatus.ACTIVE);

            assertThat(found).hasSize(1);
            assertThat(found.get(0).getTitle()).isEqualTo("Spring Boot 版本");
            assertThat(found.get(0).getMemoryType()).isEqualTo(MemoryType.FACT);
        }

        @Test
        @DisplayName("不同用户的记忆互相隔离")
        void userIsolation() {
            sampleMemory.setUserId("user001");
            memoryRepository.save(sampleMemory);

            AgentMemory other = new AgentMemory();
            other.setUserId("user002");
            other.setMemoryType(MemoryType.EXPERIENCE);
            other.setTitle("YAML 坑");
            other.setContent("YAML 中文截断会导致 Maven 构建失败");
            other.setKeywords("YAML,Maven,编码");
            other.setImportance(new BigDecimal("0.90"));
            memoryRepository.save(other);

            assertThat(memoryRepository.findByUserIdAndStatus("user001", MemoryStatus.ACTIVE)).hasSize(1);
            assertThat(memoryRepository.findByUserIdAndStatus("user002", MemoryStatus.ACTIVE)).hasSize(1);
            assertThat(memoryRepository.findByUserIdAndStatus("user001", MemoryStatus.ACTIVE)
                    .get(0).getTitle()).isEqualTo("Spring Boot 版本");
        }

        @Test
        @DisplayName("按记忆类型过滤查询")
        void findByType() {
            sampleMemory.setMemoryType(MemoryType.FACT);
            memoryRepository.save(sampleMemory);

            AgentMemory pref = new AgentMemory();
            pref.setUserId("user001");
            pref.setMemoryType(MemoryType.USER_PREFERENCE);
            pref.setTitle("构造器注入偏好");
            pref.setContent("用户偏好使用构造器注入而非字段注入");
            pref.setKeywords("构造器,注入,DI");
            pref.setImportance(new BigDecimal("0.70"));
            memoryRepository.save(pref);

            List<AgentMemory> facts = memoryRepository.findByUserIdAndStatusAndMemoryType(
                    "user001", MemoryStatus.ACTIVE, MemoryType.FACT);
            assertThat(facts).hasSize(1);
            assertThat(facts.get(0).getMemoryType()).isEqualTo(MemoryType.FACT);
        }

        @Test
        @DisplayName("幂等检查：相同 sourceMessageIds 能查到已有记忆")
        void idempotencyCheck() {
            memoryRepository.save(sampleMemory);

            List<AgentMemory> existing = memoryRepository.findBySourceMessageIds("msg-001,msg-002");
            assertThat(existing).hasSize(1);
        }
    }

    @Nested
    @DisplayName("SQL 硬过滤检索候选集")
    class CandidateRetrieval {

        @BeforeEach
        void populate() {
            // 写入 3 条不同关键词的记忆
            saveMemory("Spring Boot 版本", "Spring Boot,Java,版本", "0.80");
            saveMemory("MCP Server 配置", "MCP,Server,SSE", "0.60");
            saveMemory("Spring AI Advisor", "Spring AI,Advisor,日志", "0.70");
        }

        @Test
        @DisplayName("关键词 'Spring' 能匹配到 2 条含 Spring 的记忆")
        void keywordFilterMatches() {
            List<AgentMemory> candidates = memoryRepository.findCandidatesByKeyword("user001", "Spring");
            assertThat(candidates).hasSize(2);
        }

        @Test
        @DisplayName("关键词 'MCP' 只匹配 1 条")
        void keywordFilterSingleMatch() {
            List<AgentMemory> candidates = memoryRepository.findCandidatesByKeyword("user001", "MCP");
            assertThat(candidates).hasSize(1);
            assertThat(candidates.get(0).getTitle()).isEqualTo("MCP Server 配置");
        }

        @Test
        @DisplayName("候选集按 importance 降序排列")
        void candidatesOrderedByImportance() {
            List<AgentMemory> candidates = memoryRepository.findCandidatesByKeyword("user001", "Spring");
            assertThat(candidates.get(0).getImportance())
                    .isGreaterThanOrEqualTo(candidates.get(1).getImportance());
        }

        private void saveMemory(String title, String keywords, String importance) {
            AgentMemory m = new AgentMemory();
            m.setUserId("user001");
            m.setMemoryType(MemoryType.FACT);
            m.setTitle(title);
            m.setContent("内容: " + title);
            m.setKeywords(keywords);
            m.setImportance(new BigDecimal(importance));
            memoryRepository.save(m);
        }
    }

    @Nested
    @DisplayName("访问计数刷新")
    class AccessCountRefresh {

        @Test
        @DisplayName("批量更新访问计数后，access_count +1 且 last_accessed_at 更新")
        void incrementAccessCount() {
            AgentMemory m1 = memoryRepository.save(sampleMemory);

            AgentMemory m2 = new AgentMemory();
            m2.setUserId("user001");
            m2.setMemoryType(MemoryType.SKILL);
            m2.setTitle("压缩技能");
            m2.setContent("压缩对话时 keepRecentCount 必须为偶数");
            m2.setKeywords("压缩,对话,偶数");
            m2.setImportance(new BigDecimal("0.75"));
            m2 = memoryRepository.save(m2);

            LocalDateTime before = m1.getLastAccessedAt();

            memoryRepository.incrementAccessCount(List.of(m1.getId(), m2.getId()));
            memoryRepository.flush();

            AgentMemory updated1 = memoryRepository.findById(m1.getId()).orElseThrow();
            assertThat(updated1.getAccessCount()).isEqualTo(1);
            assertThat(updated1.getLastAccessedAt()).isAfterOrEqualTo(before);
        }
    }

    @Nested
    @DisplayName("归档记忆")
    class Archive {

        @Test
        @DisplayName("ArchivedMemory.from 能正确复制活跃记忆并设置归档原因")
        void archiveFromActive() {
            AgentMemory saved = memoryRepository.save(sampleMemory);

            ArchivedMemory archived = ArchivedMemory.from(saved, "decay");
            archivedRepository.save(archived);

            assertThat(archivedRepository.findByArchiveReason("decay")).hasSize(1);
            ArchivedMemory result = archivedRepository.findByArchiveReason("decay").get(0);
            assertThat(result.getOriginalId()).isEqualTo(saved.getId());
            assertThat(result.getTitle()).isEqualTo("Spring Boot 版本");
            assertThat(result.getArchiveReason()).isEqualTo("decay");
        }

        @Test
        @DisplayName("归档后主表记录应被删除")
        void archiveAndDelete() {
            AgentMemory saved = memoryRepository.save(sampleMemory);

            // 归档
            ArchivedMemory archived = ArchivedMemory.from(saved, "conflict");
            archivedRepository.save(archived);
            memoryRepository.delete(saved);

            assertThat(memoryRepository.findByUserIdAndStatus("user001", MemoryStatus.ACTIVE)).isEmpty();
            assertThat(archivedRepository.findByUserId("user001")).hasSize(1);
        }
    }

    @Nested
    @DisplayName("统计查询")
    class Statistics {

        @BeforeEach
        void populate() {
            saveOfType(MemoryType.FACT, "0.80");
            saveOfType(MemoryType.FACT, "0.60");
            saveOfType(MemoryType.USER_PREFERENCE, "0.90");
            saveOfType(MemoryType.EXPERIENCE, "0.50");
        }

        @Test
        @DisplayName("按类型分组统计数量正确")
        void countByType() {
            List<Object[]> counts = memoryRepository.countByType(MemoryStatus.ACTIVE);

            assertThat(counts).hasSize(3);
            // 找到 FACT 类型的统计
            long factCount = counts.stream()
                    .filter(row -> row[0] == MemoryType.FACT)
                    .mapToLong(row -> (Long) row[1])
                    .findFirst().orElse(0);
            assertThat(factCount).isEqualTo(2);
        }

        @Test
        @DisplayName("平均重要度计算正确")
        void avgImportance() {
            Double avg = memoryRepository.avgImportance(MemoryStatus.ACTIVE);
            // (0.80 + 0.60 + 0.90 + 0.50) / 4 = 0.70
            assertThat(avg).isCloseTo(0.70, org.assertj.core.data.Offset.offset(0.01));
        }

        private void saveOfType(MemoryType type, String importance) {
            AgentMemory m = new AgentMemory();
            m.setUserId("user001");
            m.setMemoryType(type);
            m.setTitle("标题-" + type.name());
            m.setContent("内容-" + type.name());
            m.setKeywords("测试," + type.name());
            m.setImportance(new BigDecimal(importance));
            memoryRepository.save(m);
        }
    }
}
