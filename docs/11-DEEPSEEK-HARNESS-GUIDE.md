# 11 — DeepSeek Harness 使用与集成指引

**资料核对日期：2026-11-12。** DSH 上游变化较快，本文件描述的是核对日期可见的文档行为；实际使用时必须以已安装版本、Commit、Profile 和 Tool Catalog 为准。

## 1. 两个不同角色，必须分清

**角色 A：DSH 是 Mammoth 的 Vibe Coding 工具。** 你通过 DSH Desktop 对 Mammoth 仓库进行需求澄清、计划、编码、测试和 Review。此路径可以先开始。

**角色 B：DSH 是 Mammoth 的候选 Agent Runtime/宿主。** Mammoth 可能以 DSH Bundle/Plugin 扩展形式运行，也可能通过独立 Domain API 与 DSH 集成。此路径必须通过 Spike 验证。

不要因为你用 DSH 写 Mammoth，就假设 Mammoth 必须 Fork DSH；也不要因为 DSH 有插件能力，就假设所有 Mammoth 业务状态都应存进 Harness 内部。

## 2. 当前上游与版本管理

V0.1 资料编制时记录的最新版本是 `dsh-v0.2.2-rc.2`（2026-11-11，预发布）。本资料 V0.2 不把某个浮动上游版本当成你本机已安装版本。

首次开始实际开发时必须记录：

- DSH Desktop / CLI 版本；
- 上游版本 tag 与 commit（如可获取）；
- 当前 Profile 名称及启用的 Bundle/Plugin；
- 模型 Provider/Model；
- Tool Catalog 中实际可用的 SubAgent、Team、Worktree、文件和终端工具；
- 当前执行权限模式。

不要在未验证的情况下照抄上游 `master` 文档里的工具名称、配置字段或 Profile 结构。开发预览 API 可能发生破坏性变化。

## 3. AGENTS.md 加载

上游 `dsh-agent-instructions` 为 Agent 提供用户级和项目级 `AGENTS.md` 指令链；上游 `dsh-base` 默认包含该能力，但 Profile 可以禁用。指令在首次请求时加载，并按 Workspace/工作目录形成适用链；文件外部变更的刷新行为并非简单的实时 watcher。

Mammoth 仓库根目录放置 `AGENTS.md`。首次会话必须让 Agent 明确报告实际收到的指令文件路径和摘要，确认加载生效。不要只凭文件存在就假定生效。

建议全局 `AGENTS.md` 只放跨项目通用偏好；Mammoth 产品规则放仓库级 `AGENTS.md` 和具体文档。不要将长篇产品文档全文复制进常驻指令，以免上下文预算被占满。

## 4. Role Markdown 与 SubAgent 的关系

`docs/agents/*.md` 是**角色定义 / Role Prompt**，不是 DSH 插件配置，也不会自动创建、注册或常驻运行 Agent。

正确使用方式：

1. Lead Agent 根据任务选择角色；
2. 读取对应 `docs/agents/<ROLE>.md`；
3. 将角色职责与本轮任务背景、输入、权限、文件范围和完成定义组合成完整 prompt；
4. 调用当前 Profile 实际提供的 SubAgent 工具；
5. Lead 核验子 Agent 结果并形成最终结论。

如果当前会话没有委派工具，可以把角色文件用于另一个独立 DSH 会话，或先研究如何在当前 Profile 启用官方 SubAgent 插件组合。不要假设所有 Profile 都提供相同工具。

## 5. Fresh / Spawn 与 Fork

当前上游 SubAgent 文档区分本地 Spawn 与 Fork 等 Provider：

- **Spawn / Fresh：** 创建独立上下文，不带入父会话已完成对话。适合独立研究、第二意见、测试设计、独立评审。必须在 prompt 中提供完整背景。
- **Fork：** 以父会话已完成的轮次作为子会话种子，适合需要上下文连续性的追问或补充分析。

需要注意：

- Fork 的“继承上下文”不代表继承父 Agent 的所有权限、工具能力、运行状态或未完成工具调用。
- Spawn/Fork 创建子会话不等于创建独立 Git Worktree。
- 子 Agent 的具体能力取决于实际 Provider、Profile Composition 和工具配置。
- 名称是树中用于消息路由的引用，不是安全授权边界。

委派时至少传递：角色定义、任务目标、必要背景、输入路径/版本、问题清单、读写范围、禁止事项、输出格式和验收标准。

## 6. AgentTeam

AgentTeam 在 SubAgent 树上增加持久化共享任务板与任务依赖协调。它适用于多个可独立执行、需要共享任务状态的任务；简单单次委派用普通 SubAgent 即可。

核对日期的上游文档说明：

- 新 Session（包括普通 Fork）默认 Team mode 关闭；
- 可在根 Session 的模型/推理菜单中启用 `Use Agent Teams`（以实际版本 UI 为准）；
- 启用后，团队成员可创建/领取/完成任务，并声明任务依赖；
- 成员共享同一个 checkout；AgentTeam 不创建隔离工作目录，也不会协调跨进程工作；
- 文件路径重叠提示是警告，不是写入锁或权限控制。

**Mammoth 工程规则：** AgentTeam 只用来协调任务，不作为并行写入安全机制。多个编码 Agent 必须使用独立 Git Worktree/分支；不能隔离时串行执行。共享 DTO、数据库迁移、根配置、公共契约同一时间只能有一个写入 owner。

## 7. Git Worktree 与并行编码

使用 Worktree 前先确认当前 DSH Profile 是否提供对应工具/Bundle，以及工具能否按当前权限创建独立目录和分支。创建后必须核对：

- Worktree 真实路径；
- 分支名与起始 Commit；
- `git status`；
- 任务允许修改的文件范围；
- 是否与其他任务共享文件。

不要只因为界面出现了 Worktree 选项，就认为所有 Agent 自动运行在不同 Worktree。

## 8. DSH 会话推荐配置与操作

- Workspace：选 Mammoth Git 仓库根目录。
- 初期使用受限工作区权限，不启用 `danger-full-access` 作为默认。
- 使用独立开发分支；并行写入时使用独立 Worktree。
- 先让 Agent 只读探索，再允许写入。
- 每次会话开头指定本次任务和对应规格；结束时生成 SESSION-HANDOFF。
- SubAgent 优先用于独立只读调查和独立验证。
- 大型任务按功能/领域边界分会话，不按“一个聊天连续写完整个平台”。

## 9. Mammoth 与 DSH 的扩展策略

优先顺序：

1. 先使用标准 DSH Desktop/Profile 完成 Mammoth 编码工作。
2. 用 Spike 证明 out-of-tree Bundle/Plugin 是否能满足 UI、工具、服务、上下文和持久化扩展。
3. 若可行，先将 Mammoth 做为可独立安装/卸载的扩展，并将核心领域服务隔离。
4. 只有公开扩展点确实不足时才考虑 Fork，并提交 ADR 与维护计划。

DSH 的 Profile、Bundle 和 Plugin 是不同概念。按当前上游文档操作，不要手工篡改 DSH Home 中由 CLI/桌面端管理的 Profile 文件。

## 10. 安全限制

DSH 属于快速演进的开发工具。不要仅依赖 Harness 工具权限作为企业安全边界：

- 使用专用开发机/VM 或隔离工作区；
- 不挂载生产目录、生产数据库凭据或未经授权的客户真实数据；
- 先审查第三方插件和工具；
- Shell、文件、网络、终端权限遵循最小权限；
- 破坏性操作和外部发送必须人工审批；
- 重要工作前后做 Git Commit/备份；
- Role Prompt 中的“只读”不是操作系统权限控制，需同时使用 DSH/OS 权限。

## 11. 推荐的首轮会话顺序

1. `docs/prompts/00-BOOTSTRAP-AND-REPO-DISCOVERY.md`
2. `docs/prompts/01-DSH-EXTENSION-SPIKE.md`
3. 阅读 `docs/AGENT-USAGE-QUICKSTART.md`，核实 SubAgent 能力。
4. 必要时执行 `docs/prompts/07-ORCHESTRATE-SUBAGENTS.md` 进行一次只读委派演练。
5. 形成 ADR-0001 后再确定 DSH 集成策略。
6. 才进入具体 Phase 1 实现任务。

## 12. 官方参考（核对日期：2026-11-12）

- DSH 仓库：<https://github.com/deepseek-ai/deepseek-harness>
- 架构说明：<https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/architecture.md>
- SubAgent 子系统：<https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/subsystems/subagent.md>
- SubAgent 工具包：<https://github.com/deepseek-ai/deepseek-harness/tree/master/packages/subagent/tool-subagent>
- AgentTeam 工具包：<https://github.com/deepseek-ai/deepseek-harness/tree/master/packages/team/agent-team>
- Workspace Instructions / AGENTS.md：<https://github.com/deepseek-ai/deepseek-harness/tree/master/packages/context/agent-instructions>
- Tool Catalog：<https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/tool-catalog.md>
- Harness 安全说明：<https://github.com/deepseek-ai/deepseek-harness/blob/master/SAFETY.md>
- Profile / Bundle 文档：<https://github.com/deepseek-ai/deepseek-harness/tree/master/docs/user/develop>
- Releases：<https://github.com/deepseek-ai/deepseek-harness/releases>
