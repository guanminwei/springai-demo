package com.git.hui.springai.advance.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agent 记忆实体 - 对应 agent_memory 主表
 * <p>
 * 存储活跃的长期记忆条目，每条记忆包含类型、内容、关键词、重要度等元数据，
 * 支持基于访问驱动的衰减机制和 LLM 语义检索。
 *
 * @author YiHui
 * @date 2025/7/29
 */
@Entity
@Table(name = "agent_memory", indexes = {
        @Index(name = "idx_memory_user_status", columnList = "user_id, status"),
        @Index(name = "idx_memory_type", columnList = "memory_type")
})
public class AgentMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 来源会话 ID */
    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    /** 用户标识 */
    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    /** 记忆类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "memory_type", length = 32, nullable = false)
    private MemoryType memoryType;

    /** 记忆内容 */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 记忆标题/摘要（用于索引常驻） */
    @Column(name = "title", length = 256, nullable = false)
    private String title;

    /** 逗号分隔关键词（用于 SQL 硬过滤） */
    @Column(name = "keywords", length = 512)
    private String keywords;

    /** 重要度 0.00~1.00 */
    @Column(name = "importance", precision = 3, scale = 2, nullable = false)
    private BigDecimal importance;

    /** 被检索命中次数 */
    @Column(name = "access_count", nullable = false)
    private int accessCount = 0;

    /** 最近一次被命中时间 */
    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    /** 状态：active / archived */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private MemoryStatus status = MemoryStatus.ACTIVE;

    /** 来源消息 ID（幂等 Key） */
    @Column(name = "source_message_ids", length = 512)
    private String sourceMessageIds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.lastAccessedAt == null) {
            this.lastAccessedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public MemoryStatus getStatus() { return status; }
    public void setStatus(MemoryStatus status) { this.status = status; }

    public String getSourceMessageIds() { return sourceMessageIds; }
    public void setSourceMessageIds(String sourceMessageIds) { this.sourceMessageIds = sourceMessageIds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
