# S2: 长期记忆检索 + 双层 Advisor 对话

## What to build

实现长期记忆检索链路和双层 Advisor 对话入口：MemoryRetrievalService（SQL 硬过滤缩小候选集 → LLM 从候选中选 top-N → 加载完整内容 → 刷新访问计数）、LongTermMemoryAdvisor（CallAroundAdvisor，在 aroundCall 中检索并注入长期记忆到 System Prompt，带时间警告格式）、ChatClientConfig（装配 MessageChatMemoryAdvisor order=100 + LongTermMemoryAdvisor order=200）、ChatController（POST /ai/generate）。

端到端验证：手动往 agent_memory 表插几条记忆 → 调用 /ai/generate 对话 → AI 回复中能体现对历史记忆的理解。

## Acceptance criteria

- [ ] MemoryRetrievalService 实现 SQL 硬过滤：按 user_id + status='active' + keywords LIKE 查 ≤20 条候选
- [ ] MemoryRetrievalService 实现 LLM 选择：将候选索引（id+title+keywords）交给 LLM，返回 top-5 id 列表
- [ ] 被选中的记忆 access_count++ 且 last_accessed_at 更新为当前时间
- [ ] LongTermMemoryAdvisor 实现 CallAroundAdvisor 接口，在 aroundCall 中执行检索注入
- [ ] 注入格式带时间警告：`[记忆于X天前创建, 可能已过时] (importance:0.9)\n{content}`
- [ ] ChatClientConfig 装配双层 Advisor：MessageChatMemoryAdvisor(100) + LongTermMemoryAdvisor(200)
- [ ] ChatController 提供 POST /ai/generate 接口，支持 user 和 msg 参数
- [ ] 手动插入记忆后调用对话接口，AI 回复能引用历史记忆

## Blocked by

- S1: 模块骨架与数据层
