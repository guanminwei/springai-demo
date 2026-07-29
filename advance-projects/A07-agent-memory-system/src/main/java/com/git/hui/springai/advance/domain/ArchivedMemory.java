package com.git.hui.springai.advance.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 归档记忆实体 - 对应 agent_memory_archive 表
 * <p>
 * 存储已淘汰的长期记忆，用于审计和历史追溯。
 * 与 {@link AgentMemory} 结构一致，额外增加归档时间和归档原因。
 *
 * @author YiHui
 * @date 2025/7/29
 */
@Entity
@Table(name = "agent_memory_archive")
public class ArchivedMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 原始记忆 ID（归档前的主表 ID） */
    @Column(name = "original_id")
    private Long originalId;

    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "memory_type", length = 32, nullable = false)
    private MemoryType memoryType;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "title", length = 256, nullable = false)
    private String title;

    @Column(name = "keywords", length = 512)
    private String keywords;

    @Column(name = "importance", precision = 3, scale = 2, nullable = false)
    private BigDecimal importance;

    @Column(name = "access_count", nullable = false)
    private int accessCount = 0;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Column(name = "source_message_ids", length = 512)
    private String sourceMessageIds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 归档时间 */
    @Column(name = "archived_at", nullable = false)
    private LocalDateTime archivedAt;

    /** 归档原因：decay/conflict/merge/manual */
    @Column(name = "archive_reason", length = 128, nullable = false)
    private String archiveReason;

    @PrePersist
    protected void onCreate() {
        if (this.archivedAt == null) {
            this.archivedAt = LocalDateTime.now();
        }
    }

    /**
     * 从活跃记忆构建归档记忆
     *
     * @param memory 原始活跃记忆
     * @param reason 归档原因
     * @return 归档记忆实例
     */
    public static ArchivedMemory from(AgentMemory memory, String reason) {
        ArchivedMemory archived = new ArchivedMemory();
        archived.setOriginalId(memory.getId());
        archived.setConversationId(memory.getConversationId());
        archived.setUserId(memory.getUserId());
        archived.setMemoryType(memory.getMemoryType());
        archived.setContent(memory.getContent());
        archived.setTitle(memory.getTitle());
        archived.setKeywords(memory.getKeywords());
        archived.setImportance(memory.getImportance());
        archived.setAccessCount(memory.getAccessCount());
        archived.setLastAccessedAt(memory.getLastAccessedAt());
        archived.setSourceMessageIds(memory.getSourceMessageIds());
        archived.setCreatedAt(memory.getCreatedAt());
        archived.setUpdatedAt(memory.getUpdatedAt());
        archived.setArchiveReason(reason);
        return archived;
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOriginalId() { return originalId; }
    public void setOriginalId(Long originalId) { this.originalId = originalId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public MemoryType getMemoryType() { return memoryType; }
    public void setMemoryType(MemoryType memoryType) { this.memoryType = memoryType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public BigDecimal getImportance() { return importance; }
    public void setImportance(BigDecimal importance) { this.importance = importance; }

    public int getAccessCount() { return accessCount; }
    public void setAccessCount(int accessCount) { this.accessCount = accessCount; }

    public LocalDateTime getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(LocalDateTime lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }

    public String getSourceMessageIds() { return sourceMessageIds; }
    public void setSourceMessageIds(String sourceMessageIds) { this.sourceMessageIds = sourceMessageIds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }

    public String getArchiveReason() { return archiveReason; }
    public void setArchiveReason(String archiveReason) { this.archiveReason = archiveReason; }
}
