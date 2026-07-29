package com.git.hui.springai.advance.domain;

/**
 * 记忆状态枚举
 * <ul>
 *     <li>{@link #ACTIVE} - 活跃状态，参与检索和注入</li>
 *     <li>{@link #ARCHIVED} - 已归档状态，不参与检索，仅供审计和历史追溯</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/7/29
 */
public enum MemoryStatus {
    /** 活跃状态：参与检索和注入 */
    ACTIVE,
    /** 已归档状态：不参与检索，仅供审计 */
    ARCHIVED
}
