# Prompt 08 — AgentTeam 任务规划

你是 Mammoth 本轮工作的 Lead。先阅读：

- `AGENTS.md`
- `docs/00-MASTER-GUIDE.md`
- `docs/agents/AGENT-ORCHESTRATION.md`
- `docs/11-DEEPSEEK-HARNESS-GUIDE.md`

## 目标

将【填写目标】拆为适合并行执行的独立子任务。

## 开始前检查

1. 确认本轮 DSH Profile 是否实际提供 AgentTeam 任务工具和 SubAgent 创建/消息工具。
2. 确认根 Session 是否已启用 Team mode。
3. 如果缺少任何能力，不要假设可用；改用普通 SubAgent 或串行计划。
4. 确认 AgentTeam 的所有成员共享同一 checkout；不得把任务板当作文件隔离机制。

## 任务拆分要求

每个任务必须包含：

- task title / ID；
- 角色文件；
- 自包含任务描述；
- 输入资料与版本；
- 明确 writeScopes（默认只读）；
- 依赖任务；
- 输出文件/报告；
- 完成定义；
- 是否需要独立 Worktree。

## 约束

- 只读研究可以并行。
- 编码并行必须使用独立 Git Worktree/分支，并验证实际工作目录；若不能隔离则串行。
- 相同文件、共享 DTO、数据库迁移、根配置和公共契约不能由多个写入任务同时负责。
- 不要一次拆成大量极小任务；协调成本必须低于并行收益。
- AgentTeam 文件重叠提示不构成阻断，也不授予写权限。

## 输出

先给出任务依赖 DAG 和工作目录/分支计划，再创建任务板任务。任务创建后复核 owner、依赖、writeScopes 和状态。完成时汇总任务状态、冲突和集成验证。
