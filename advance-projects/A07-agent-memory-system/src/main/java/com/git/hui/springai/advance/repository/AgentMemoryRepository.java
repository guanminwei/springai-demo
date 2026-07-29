package com.git.hui.springai.advance.repository;

import com.git.hui.springai.advance.domain.AgentMemory;
import com.git.hui.springai.advance.domain.MemoryStatus;
import com.git.hui.springai.advance.domain.MemoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Agent 记忆主表 Repository
 * <p>
 * 提供活跃记忆的 CRUD 操作，支持按用户/类型/状态/关键词的多维度查询。
 *
 * @author YiHui
 * @date 2025/7/29
 */
@Repository
public interface AgentMemoryRepository extends JpaRepository<AgentMemory, Long> {

    /**
     * 按用户 ID 和状态查询活跃记忆
     */
    List<AgentMemory> findByUserIdAndStatus(String userId, MemoryStatus status);

    /**
     * 按用户 ID、状态和记忆类型查询
     */
    List<AgentMemory> findByUserIdAndStatusAndMemoryType(String userId, MemoryStatus status, MemoryType memoryType);

    /**
     * 查询所有活跃记忆
     */
    List<AgentMemory> findByStatus(MemoryStatus status);

    /**
     * 按来源消息 ID 查询（幂等检查）
     */
    List<AgentMemory> findBySourceMessageIds(String sourceMessageIds);

    /**
     * SQL 硬过滤：按用户 ID + 关键词 LIKE 查询活跃记忆
     * 用于检索链路的第一步——缩小候选集
     *
     * @param userId   用户标识
     * @param keyword  关键词（会做 LIKE 模糊匹配）
     * @return 候选记忆列表（仅含索引字段）
     */
    @Query("SELECT m FROM AgentMemory m WHERE m.userId = :userId AND m.status = 'ACTIVE' " +
           "AND LOWER(m.keywords) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY m.importance DESC, m.lastAccessedAt DESC")
    List<AgentMemory> findCandidatesByKeyword(@Param("userId") String userId,
                                              @Param("keyword") String keyword);

    /**
     * 批量更新访问计数和最近访问时间
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AgentMemory m SET m.accessCount = m.accessCount + 1, m.lastAccessedAt = CURRENT_TIMESTAMP WHERE m.id IN :ids")
    void incrementAccessCount(@Param("ids") List<Long> ids);

    /**
     * 按记忆类型分组统计数量
     */
    @Query("SELECT m.memoryType, COUNT(m) FROM AgentMemory m WHERE m.status = :status GROUP BY m.memoryType")
    List<Object[]> countByType(@Param("status") MemoryStatus status);

    /**
     * 计算指定状态下记忆的平均重要度
     */
    @Query("SELECT AVG(m.importance) FROM AgentMemory m WHERE m.status = :status")
    Double avgImportance(@Param("status") MemoryStatus status);
}
