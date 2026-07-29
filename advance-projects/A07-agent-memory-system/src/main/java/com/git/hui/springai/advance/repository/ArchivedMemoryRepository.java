package com.git.hui.springai.advance.repository;

import com.git.hui.springai.advance.domain.ArchivedMemory;
import com.git.hui.springai.advance.domain.MemoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 归档记忆 Repository
 * <p>
 * 提供已归档记忆的查询接口，用于审计、历史追溯和统计分析。
 *
 * @author YiHui
 * @date 2025/7/29
 */
@Repository
public interface ArchivedMemoryRepository extends JpaRepository<ArchivedMemory, Long> {

    /**
     * 按用户 ID 查询归档记忆
     */
    List<ArchivedMemory> findByUserId(String userId);

    /**
     * 按用户 ID 和记忆类型查询归档记忆
     */
    List<ArchivedMemory> findByUserIdAndMemoryType(String userId, MemoryType memoryType);

    /**
     * 按归档原因查询
     */
    List<ArchivedMemory> findByArchiveReason(String archiveReason);
}
