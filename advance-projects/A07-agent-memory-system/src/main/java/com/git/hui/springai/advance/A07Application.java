package com.git.hui.springai.advance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * A07 模块启动类 - Agent 记忆系统
 * <p>
 * 本模块演示完整的 Agent 记忆系统设计，包括：
 * <ul>
 *     <li>五类记忆分类：用户偏好、事实、经验、技能、事件</li>
 *     <li>双层 Advisor 架构：短期记忆（MessageChatMemoryAdvisor）+ 长期记忆（LongTermMemoryAdvisor）</li>
 *     <li>记忆生命周期：提取 → 存储 → 检索 → 衰减 → 合并 → 归档</li>
 *     <li>时间感知注入：带时间警告的记忆注入格式</li>
 *     <li>矛盾检测与自动更新：新旧记忆冲突时自动归档旧记忆</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/7/29
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class A07Application {
    public static void main(String[] args) {
        SpringApplication.run(A07Application.class, args);
    }
}
