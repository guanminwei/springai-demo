package com.git.hui.springai.advance.controller;

import com.git.hui.springai.advance.domain.AgentMemory;
import com.git.hui.springai.advance.domain.ArchivedMemory;
import com.git.hui.springai.advance.domain.MemoryStatus;
import com.git.hui.springai.advance.domain.MemoryType;
import com.git.hui.springai.advance.repository.AgentMemoryRepository;
import com.git.hui.springai.advance.repository.ArchivedMemoryRepository;
import com.git.hui.springai.advance.service.MemoryConflictService;
import com.git.hui.springai.advance.service.MemoryDecayService;
import com.git.hui.springai.advance.service.MemoryExtractorService;
import com.git.hui.springai.advance.service.MemoryMergeService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆管理控制器 — 提供记忆系统的完整管理 API
 * <p>
 * 所有接口均用于教学演示和调试，展示记忆系统的内部运作机制。
 *
 * @author YiHui
 * @date 2025/7/29
 */
@RestController
@RequestMapping("/memory")
public class MemoryController {

    private final AgentMemoryRepository memoryRepository;
    private final ArchivedMemoryRepository archivedRepository;
    private final MemoryExtractorService extractorService;
    private final MemoryDecayService decayService;
    private final MemoryMergeService mergeService;
    private final MemoryConflictService conflictService;

    public MemoryController(AgentMemoryRepository memoryRepository,
                            ArchivedMemoryRepository archivedRepository,
                            MemoryExtractorService extractorService,
                            MemoryDecayService decayService,
                            MemoryMergeService mergeService,
                            MemoryConflictService conflictService) {
        this.memoryRepository = memoryRepository;
        this.archivedRepository = archivedRepository;
        this.extractorService = extractorService;
        this.decayService = decayService;
        this.mergeService = mergeService;
        this.conflictService = conflictService;
    }

    /**
     * 查看活跃记忆（支持按类型过滤）
     *
     * @param type 可选的记忆类型过滤
     * @return 活跃记忆列表
     */
    @GetMapping("/list")
    public Map<String, Object> listActive(@RequestParam(required = false) String type) {
        List<AgentMemory> memories;
        if (type != null && !type.isBlank()) {
            try {
                MemoryType mt = MemoryType.valueOf(type.toUpperCase());
                memories = memoryRepository.findByUserIdAndStatus("default", MemoryStatus.ACTIVE);
                memories = memories.stream().filter(m -> m.getMemoryType() == mt).collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                return Map.of("error", "无效的记忆类型: " + type);
            }
        } else {
            memories = memoryRepository.findByStatus(MemoryStatus.ACTIVE);
        }
        return Map.of("count", memories.size(), "memories", memories);
    }

    /**
     * 查看已归档记忆
     */
    @GetMapping("/archived")
    public Map<String, Object> listArchived() {
        List<ArchivedMemory> memories = archivedRepository.findAll();
        return Map.of("count", memories.size(), "memories", memories);
    }

    /**
     * 记忆统计信息
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        long totalActive = memoryRepository.count();
        long totalArchived = archivedRepository.count();
        Double avgImportance = memoryRepository.avgImportance(MemoryStatus.ACTIVE);
        List<Object[]> typeCounts = memoryRepository.countByType(MemoryStatus.ACTIVE);

        Map<String, Long> byType = new LinkedHashMap<>();
        for (Object[] row : typeCounts) {
            byType.put(row[0].toString(), (Long) row[1]);
        }

        return Map.of(
                "totalActive", totalActive,
                "totalArchived", totalArchived,
                "avgImportance", avgImportance != null ? String.format("%.2f", avgImportance) : "0.00",
                "byType", byType
        );
    }

    /**
     * 手动添加记忆
     */
    @PostMapping("/add")
    public Map<String, Object> addMemory(@RequestBody Map<String, String> body) {
        AgentMemory memory = new AgentMemory();
        memory.setUserId(body.getOrDefault("userId", "default"));
        memory.setMemoryType(MemoryType.valueOf(body.getOrDefault("memoryType", "FACT").toUpperCase()));
        memory.setTitle(body.getOrDefault("title", "手动添加"));
        memory.setContent(body.getOrDefault("content", ""));
        memory.setKeywords(body.getOrDefault("keywords", ""));
        memory.setImportance(new BigDecimal(body.getOrDefault("importance", "0.50")));
        memory.setStatus(MemoryStatus.ACTIVE);

        AgentMemory saved = memoryRepository.save(memory);
        return Map.of("message", "记忆添加成功", "memory", saved);
    }

    /**
     * 手动触发反思提取
     */
    @PostMapping("/reflect")
    public Map<String, Object> reflect(@RequestBody Map<String, String> body) {
        String userId = body.getOrDefault("userId", "default");
        String conversationId = body.getOrDefault("conversationId", "manual-reflect");
        String messages = body.getOrDefault("messages", "");

        List<AgentMemory> extracted = extractorService.extract(userId, conversationId, messages);
        return Map.of(
                "message", String.format("反思完成，提取 %d 条记忆", extracted.size()),
                "extracted", extracted.size(),
                "memories", extracted
        );
    }

    /**
     * 手动触发衰减计算
     */
    @PostMapping("/decay")
    public Map<String, Object> decay() {
        MemoryDecayService.DecayResult result = decayService.runDecay();
        return Map.of("message", "衰减计算完成", "result", result.toString(),
                "archived", result.archived(), "demoted", result.demoted());
    }

    /**
     * 手动触发记忆合并
     */
    @PostMapping("/merge")
    public Map<String, Object> merge() {
        MemoryMergeService.MergeResult result = mergeService.runMerge();
        return Map.of("message", "合并完成", "result", result.toString(), "merged", result.merged());
    }

    /**
     * 删除（归档）指定记忆
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteMemory(@PathVariable Long id) {
        Optional<AgentMemory> opt = memoryRepository.findById(id);
        if (opt.isEmpty()) {
            return Map.of("error", "记忆不存在: " + id);
        }

        AgentMemory memory = opt.get();
        conflictService.archiveMemory(memory, "manual");
        return Map.of("message", "记忆已归档", "id", id);
    }
}
