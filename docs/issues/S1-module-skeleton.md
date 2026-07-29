# S1: 模块骨架与数据层

## What to build

搭建 A07-agent-memory-system 模块的完整骨架：pom.xml、启动类、application.yml（H2 文件模式 + JPA + AI 配置）、schema.sql（agent_memory 主表 + agent_memory_archive 归档表）、domain 枚举（MemoryType 五类、MemoryStatus 两态）、AgentMemory JPA 实体、两个 JPA Repository。同时在 advance-projects/pom.xml 中注册新 module。

端到端验证：`mvn spring-boot:run` 启动成功，H2 控制台可查看自动创建的表结构。

## Acceptance criteria

- [ ] pom.xml 依赖包含 spring-boot-starter-web、spring-boot-starter-data-jpa、h2、spring-ai-starter-model-zhipuai
- [ ] advance-projects/pom.xml 中新增 `<module>A07-agent-memory-system</module>`
- [ ] application.yml 配置 H2 文件模式数据源、JPA 自动建表、AI 模型 API Key
- [ ] schema.sql 包含 agent_memory 和 agent_memory_archive 两张表的 DDL
- [ ] MemoryType 枚举包含 5 个值：USER_PREFERENCE、FACT、EXPERIENCE、SKILL、EVENT
- [ ] MemoryStatus 枚举包含 2 个值：ACTIVE、ARCHIVED
- [ ] AgentMemory 实体映射 agent_memory 表所有字段
- [ ] AgentMemoryRepository 和 ArchivedMemoryRepository 继承 JpaRepository
- [ ] 项目能正常启动，H2 表自动创建

## Blocked by

None - can start immediately
