# S6: 记忆管理 API 全集

## What to build

完成 MemoryController 的全部 REST API：GET /memory/list（支持 ?type= 过滤）、GET /memory/archived（查看已归档记忆）、GET /memory/stats（各类型数量、平均 importance、总记忆数）、POST /memory/add（手动添加指定 type/content/keywords/importance 的记忆）、POST /memory/reflect（手动触发对当前会话的反思提取）、DELETE /memory/{id}（将指定记忆归档，reason='manual'）。

端到端验证：通过 API 完成记忆的增删查统计全流程 → GET /memory/stats 返回正确的统计数据。

## Acceptance criteria

- [ ] GET /memory/list 返回所有 active 记忆，支持 ?type= 可选过滤
- [ ] GET /memory/archived 返回所有已归档记忆
- [ ] GET /memory/stats 返回统计信息：总数、各类型数量、平均 importance、最近活跃记忆
- [ ] POST /memory/add 接受 {memoryType, content, title, keywords, importance} 手动写入
- [ ] POST /memory/reflect 手动触发对指定会话的反思提取（复用 MemoryExtractorService）
- [ ] DELETE /memory/{id} 将指定记忆移入归档表（reason='manual'）
- [ ] 所有 API 返回统一的 JSON 格式，包含状态码和消息
- [ ] 错误的请求参数返回合理的错误提示

## Blocked by

- S3: 记忆写入（提取 + 矛盾检测 + 自动更新）
- S4: 访问驱动衰减 + 归档表
- S5: 记忆合并 + 定时任务
