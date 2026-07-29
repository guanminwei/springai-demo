package com.git.hui.springai.advance.domain;

/**
 * Agent 记忆类型枚举 - 五类记忆分类
 * <p>
 * 基于认知科学的功能分类，兼顾直觉性和通用性：
 * <ul>
 *     <li>{@link #USER_PREFERENCE} - 用户偏好：用户的习惯、喜好、风格要求</li>
 *     <li>{@link #FACT} - 项目/环境事实：技术栈、配置信息、客观状态</li>
 *     <li>{@link #EXPERIENCE} - 经验教训：踩过的坑、成功的修复方案</li>
 *     <li>{@link #SKILL} - 程序性技能：可复用的操作流程和最佳实践</li>
 *     <li>{@link #EVENT} - 情景事件：特定时间、场景下发生的具体事件</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/7/29
 */
public enum MemoryType {
    /** 用户偏好：用户的习惯、喜好、风格要求 */
    USER_PREFERENCE,
    /** 项目/环境事实：技术栈、配置信息、客观状态 */
    FACT,
    /** 经验教训：踩过的坑、成功的修复方案 */
    EXPERIENCE,
    /** 程序性技能：可复用的操作流程和最佳实践 */
    SKILL,
    /** 情景事件：特定时间、场景下发生的具体事件 */
    EVENT
}
