# S4: 访问驱动衰减 + 归档表

## What to build

实现访问驱动的衰减机制：MemoryDecayService 扫描所有 active 记忆，对每条计算 daysSinceAccess（距最后访问天数）。90天未访问且 access_count<2 的记忆归档（reason='decay'）；30天未访问且 access_count<5 的记忆 importance 乘以 0.8 降权。提供 POST /memory/decay 手动触发 API。

端到端验证：插入一条 access_count=0、last_accessed_at 为 100 天前的记忆 → 调用 /memory/decay → 该记忆出现在 agent_memory_archive 中（reason='decay'）。

## Acceptance criteria

- [ ] MemoryDecayService 扫描所有 status='active' 的记忆
- [ ] 90天未被访问 且 access_count < 2 → 移入归档表，archive_reason='decay'
- [ ] 30天未被访问 且 access_count < 5 → importance *= 0.8（降权但不归档）
- [ ] importance 降权后不低于 0.01（防止浮点下溢）
- [ ] 提供 POST /memory/decay API，手动触发衰减计算，返回归档/降权的记忆数量
- [ ] 衰减计算不阻塞主线程（@Async 或事务内批量操作）

## Blocked by

- S2: 长期记忆检索 + 双层 Advisor 对话
