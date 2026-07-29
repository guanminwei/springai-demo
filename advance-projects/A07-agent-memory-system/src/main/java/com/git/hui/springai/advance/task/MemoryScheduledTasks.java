package com.git.hui.springai.advance.task;

import com.git.hui.springai.advance.service.MemoryDecayService;
import com.git.hui.springai.advance.service.MemoryMergeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 记忆系统定时任务 — 自动执行衰减和合并
 * <p>
 * 通过 {@code app.memory.scheduled-enabled} 配置项控制开关（默认开启）。
 * <ul>
 *     <li>衰减任务：每 6 小时执行一次，清理长期未使用的记忆</li>
 *     <li>合并任务：每天执行一次，合并同类碎片记忆</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/7/29
 */
@Component
@ConditionalOnProperty(name = "app.memory.scheduled-enabled", havingValue = "true", matchIfMissing = true)
public class MemoryScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(MemoryScheduledTasks.class);

    private final MemoryDecayService decayService;
    private final MemoryMergeService mergeService;

    public MemoryScheduledTasks(MemoryDecayService decayService, MemoryMergeService mergeService) {
        this.decayService = decayService;
        this.mergeService = mergeService;
    }

    /**
     * 定时衰减：每 6 小时执行一次
     */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000) // 6 hours
    public void scheduledDecay() {
        log.info("定时衰减任务开始");
        try {
            MemoryDecayService.DecayResult result = decayService.runDecay();
            log.info("定时衰减任务完成: {}", result);
        } catch (Exception e) {
            log.error("定时衰减任务失败", e);
        }
    }

    /**
     * 定时合并：每天凌晨 3 点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledMerge() {
        log.info("定时合并任务开始");
        try {
            MemoryMergeService.MergeResult result = mergeService.runMerge();
            log.info("定时合并任务完成: {}", result);
        } catch (Exception e) {
            log.error("定时合并任务失败", e);
        }
    }
}
