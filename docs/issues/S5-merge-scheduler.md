# S5: 记忆合并 + 定时任务

## What to build

实现同类记忆合并和定时任务自动化：MemoryMergeService 按 memory_type 分组，通过 SQL 查找 keywords 重叠度高的记忆簇（同一 type 下 keywords 有共同词的记忆），调用 LLM 将碎片合并为一条更完整的记忆，旧记忆归档（reason='merge'）。MemoryScheduledTasks 配置定时任务自动执行衰减和合并。提供 POST /memory/merge 手动触发 API。

端到端验证：插入 3 条 type=FACT、keywords 都包含 "Spring Boot" 的记忆 → 调用 /memory/merge → 3 条合并为 1 条更完整的记忆，旧 3 条移入归档表。

## Acceptance criteria

- [ ] MemoryMergeService 按 memory_type 分组查找 keywords 重叠的记忆簇
- [ ] 每个簇调用 LLM 合并为一条更完整的记忆，保留最高的 importance 值
- [ ] 合并后的新记忆写入 agent_memory，旧记忆移入 agent_memory_archive（reason='merge'）
- [ ] MemoryScheduledTasks 配置 @Scheduled 定时执行衰减（每 6 小时）和合并（每天）
- [ ] 定时任务可通过配置项开关（默认开启）
- [ ] 提供 POST /memory/merge API，手动触发合并，返回合并的记忆数量
- [ ] 定时任务不阻塞其他 API 请求

## Blocked by

- S4: 访问驱动衰减 + 归档表
