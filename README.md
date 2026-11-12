# Mammoth Agent — Vibe Coding Starter Kit

**版本：V0.2**  ·  **更新日期：2026-11-12**

> 这是 Mammoth Agent 的产品定义与 Vibe Coding 工程资料包。它不是一份“让 AI 一次性把项目写完”的超级提示词，而是一套让人和 Agent 在长期项目中协作、交付、验证和积累知识的工程规则。

## 从哪里开始

1. **先读 [`docs/00-MASTER-GUIDE.md`](docs/00-MASTER-GUIDE.md)**：唯一总指导文档。
2. 阅读根目录 [`AGENTS.md`](AGENTS.md)：DSH 项目常驻指令入口。首次使用必须确认当前 Profile 实际加载了它。
3. 阅读 [`docs/AGENT-USAGE-QUICKSTART.md`](docs/AGENT-USAGE-QUICKSTART.md)：了解怎样在 DSH 中使用本资料包。
4. 阅读 [`docs/agents/README.md`](docs/agents/README.md) 与 [`docs/agents/AGENT-ORCHESTRATION.md`](docs/agents/AGENT-ORCHESTRATION.md)：理解角色定义和委派规则。
5. 按产品、架构和任务需要阅读 `docs/01`–`09`。
6. 进行 Vibe Coding 时使用 `docs/10-VIBE-CODING-PLAYBOOK.md`、`docs/11-DEEPSEEK-HARNESS-GUIDE.md` 和 `docs/templates/`。
7. 从 `docs/prompts/` 选择本轮任务提示词；提示词文件通过路径交给 DSH 读取，不要求每次手动复制全文。

## 文件地图

- `AGENTS.md`：仓库级常驻指令；不是完整产品文档。
- `CHANGELOG.md`：资料包版本变化。
- `docs/00-MASTER-GUIDE.md`：产品原则、文档权威、工作方法和阶段门禁。
- `docs/01`–`09`：产品、领域、架构、知识、需求、安全、质量基线。
- `docs/10`–`13`：Vibe Coding、DSH、路线图和待决事项。
- `docs/agents/`：六种可复用的 Vibe Coding 角色定义，以及 Lead/SubAgent 编排规范。
- `docs/AGENT-USAGE-QUICKSTART.md`：DSH 使用速查。
- `docs/templates/`：Feature Spec、任务、ADR、会话交接、评审报告模板。
- `docs/prompts/`：仓库勘察、Spike、规划、实施、评审、结项、SubAgent 委派提示词。
- `docs/checklists/`：需求就绪、DoD、安全、发布和 SubAgent 使用检查表。
- `decisions/`：正式架构决策记录。

## 非常重要：Role Markdown 与 DSH SubAgent 的关系

`docs/agents/*.md` 是角色提示词定义，不是 DSH 插件配置，不会自动创建常驻 Agent。Lead Agent 需要读取角色文件，并把角色职责与当前任务背景组合成自包含 prompt，再调用当前 Profile 中实际提供的 `subagent` / `subagent_fork` 等工具。若当前 Profile 没有相应委派工具，可将角色文件用于独立会话的人工委派。

AgentTeam 是可选的会话级协调能力。它的任务板不提供文件系统隔离；团队成员可能共享同一 checkout。并行编码必须使用独立 Worktree/分支，否则串行实施。

## 当前资料的性质

- **已确定的产品原则**：不得为了短期演示随意删除或弱化。
- **建议的初始方案**：可通过 ADR 验证、调整。
- **待验证的技术选择**：明确标注为 OPEN，不应被 Agent 擅自当成既定事实。
- **DSH 版本行为**：上游可能快速变化。以用户实际安装版本、Profile 和 Tool Catalog 为准。

## 建议的仓库起步方式

先将本目录内容复制到 Mammoth 自己的 Git 仓库根目录。不要直接把本资料包覆盖到 DeepSeek Harness 上游源码仓库中。Mammoth 产品代码与 DSH 上游代码应保持清晰边界。

首次启动时，在 DSH 中选择 Mammoth 仓库根目录为 Workspace，先执行 `docs/prompts/00-BOOTSTRAP-AND-REPO-DISCOVERY.md`，只做仓库勘察和计划，不要立刻生成应用代码。随后按照 `docs/AGENT-USAGE-QUICKSTART.md` 验证指令和委派能力。
