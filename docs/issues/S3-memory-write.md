# S3: 记忆写入（提取 + 矛盾检测 + 自动更新）

## What to build

实现会话结束时的记忆提取完整链路：MemoryExtractorService（轻量规则预筛对话内容 → LLM 结构化抽取五类记忆 → 幂等写入）、MemoryConflictService（对新记忆查找同类型+keywords交集的旧记忆 → LLM判断是否矛盾 → 矛盾则归档旧记忆）。会话结束通过 @Async 异步触发。

端到端验证：进行一轮包含偏好表达的对话 → 会话结束后查看 agent_memory 表出现新记忆 → 再聊一次表达矛盾偏好 → 旧记忆移入归档表（reason='conflict'），新记忆正常写入。

## Acceptance criteria

- [ ] MemoryExtractorService 实现轻量规则预筛：检查对话内容是否包含偏好/事实/经验类关键词
- [ ] 通过规则预筛的对话调用 LLM 结构化抽取，输出 [{type, title, content, keywords, importance}] JSON
- [ ] 使用 Spring AI 结构化输出（BeanOutputConverter 或类似机制）
- [ ] source_message_ids 字段保证幂等，相同消息不重复写入
- [ ] MemoryConflictService 查找同 memory_type + keywords 有交集的旧记忆
- [ ] LLM 判断新旧记忆是否矛盾，矛盾时旧记忆移入 agent_memory_archive（reason='conflict'）
- [ ] 会话结束时通过 @Async 异步触发提取流程
- [ ] 对话中包含偏好表达后，agent_memory 表自动出现新记忆

## Blocked by

- S2: 长期记忆检索 + 双层 Advisor 对话
