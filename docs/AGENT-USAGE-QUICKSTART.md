# DSH + Mammoth Agent 使用速查

这份指南面向项目负责人，说明如何在 DSH Desktop 中把本仓库的指导文件和 Agent Role Library 用起来。

## 1. 首次准备

1. 将资料包解压到 Mammoth Git 仓库根目录。
2. 确认根目录存在 `AGENTS.md` 和 `docs/`。
3. 在 DSH Desktop 中将 Mammoth 仓库根目录设置为 Workspace。
4. 开启新会话，先执行“指令加载确认”提示词。
5. 确认当前 Profile 已启用项目指令加载、文件读取和需要的 SubAgent 工具。

不要因为文件在磁盘上就假设已加载。DSH 上游的 Workspace Instructions 机制会根据 Profile、Workspace 和工作目录加载适用的指令链；Profile 可能禁用该能力，且外部编辑后的刷新行为有具体规则。

## 2. 第一轮：指令加载确认

在 DSH 输入：

```text
请先不要修改任何文件。
1. 说明你实际收到/读取的 AGENTS.md 文件路径；
2. 总结其中最重要的 8 条约束；
3. 阅读 docs/00-MASTER-GUIDE.md，并总结产品原则和文档权威顺序；
4. 检查当前 Git 分支、Commit、git status；
5. 指出当前会话是否实际提供 subagent、subagent_fork、list_agents、send_message、AgentTeam 任务工具；
6. 未确认的能力标记为未确认，不要假设存在。
全程只读。
```

## 3. 运行一个只读 Research SubAgent

当主 Agent 已确认有 `subagent` 工具时，在 DSH 输入：

```text
请委派一个 Fresh SubAgent 执行只读研究。
角色定义：docs/agents/02-RESEARCH-ANALYST.md
任务：调查当前仓库是否已经有适合实现“项目资料导入”的基础模块。
输入：读取 docs/03-DOMAIN-MODEL.md、docs/05-ARCHITECTURE-BASELINE.md，并检查当前代码。
写入权限：只读，不得修改任何文件。
输出：严格按角色定义的 Research Report 格式，给出文件路径、符号和证据。
请使用唯一名称 research-import。
主会话继续整理已知约束，收到报告后再整合。
```

Lead Agent 应把角色文件内容和完整任务要求放入子 Agent 的 `prompt`，不能只把文件路径交给一个没有文件读取能力的子 Agent。

## 4. 角色调用速查

| 你要做的事 | 让 Lead 读取 | 委派建议 |
|---|---|---|
| 把一个模糊想法整理成需求规格 | `01-PRODUCT-ANALYST.md` | 高影响需求可 Fresh |
| 查仓库/库/官方文档 | `02-RESEARCH-ANALYST.md` | Fresh、只读 |
| 审核技术架构 | `03-ARCHITECTURE-REVIEWER.md` | Fresh、只读 |
| 按批准任务写代码 | `04-IMPLEMENTATION-AGENT.md` | 只在任务明确后委派 |
| 设计测试场景 | `05-TEST-DESIGNER.md` | Fresh、只读；必要时授权写测试 |
| 审核实现 diff | `06-INDEPENDENT-REVIEWER.md` | Fresh、只读 |

## 5. Fresh 与 Fork 如何选择

- **Fresh / `subagent`：** 新上下文，适合独立研究和独立评审。必须在 prompt 里重述任务背景、项目路径、文件和问题。
- **Fork / `subagent_fork`：** 子 Agent 需要父会话已完成的对话内容时使用。不要默认它继承所有工具授权。
- **外部 Agent：** 仅在当前工具列表中看见相应外部 Provider 工具时使用。先核对当前 Profile 的工具目录和能力。

如果当前工具名不同，以当前安装的 DSH 版本和 Tool Catalog 为准，不要硬套本手册。

## 6. 让主 Agent 组织 AgentTeam

当任务被拆成多个可并行子任务，且需要共享任务板时，可以在根 Session 的模型/推理菜单中查找并启用 **Use Agent Teams**（具体位置以安装版本 UI 为准）。之后请 Lead：

```text
当前任务拆分为三个独立工作流：
A. 只读调查现有数据模型；
B. 只读研究 DSH 插件扩展边界；
C. 设计该功能的测试矩阵。
请先读取 docs/agents/AGENT-ORCHESTRATION.md 和对应角色文件。
建立共享任务板、设置依赖和明确 writeScopes。
本轮三个子任务全部只读，不得修改代码。
请确认 Team mode 和对应任务工具实际可用；若不可用，改用普通 SubAgent 或串行执行。
```

注意：AgentTeam 有共享任务板，但官方文档说明成员共享同一个 checkout。文件路径提示是冲突警告，不是锁。并行编码必须使用真正隔离的 Git Worktree；无法隔离就串行。

## 7. 功能开发的标准操作

1. 用户描述目标。
2. Lead 读取 Master Guide 和相关资料。
3. Product Analyst（必要时）协助澄清需求。
4. Research Analyst 调查现状。
5. Architecture Reviewer（高风险变更时）评估设计。
6. Lead 生成 Feature Spec 和 Task。
7. 负责人批准方案。
8. Implementation Agent 实施一个任务。
9. Test Designer 设计/补充覆盖。
10. Independent Reviewer 使用 Fresh 上下文审查 diff。
11. Lead 整合结果，负责人验收，更新文档和交接记录。

简单任务可减少角色数量，不必每次走完全部步骤。

## 8. 常用会话结束指令

```text
请按 docs/templates/SESSION-HANDOFF.md 生成会话交接。
记录 Git 状态、Commit、修改文件、实际运行的检查、失败项、开放决策、
当前任务状态和下一会话必须阅读的文件。不要依赖本聊天历史来描述下一步。
```

## 9. 常见误区

- Role Markdown 不等于已注册 DSH Agent。
- `AGENTS.md` 存在不等于当前 Profile 已加载它。
- 创建 SubAgent 不等于创建独立 Git Worktree。
- AgentTeam 任务板不等于文件锁或权限系统。
- Fork 继承对话不等于继承父 Agent 所有工具和授权。
- Reviewer 不应该顺手修复自己发现的问题。
- Lead 不能把子 Agent 报告直接拼起来当最终结论。
