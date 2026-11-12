# Mammoth Vibe Coding — Agent Role Library

**版本：** 0.2  
**用途：** 定义在使用 DeepSeek Harness（DSH）开发 Mammoth 时，可由 Lead Agent 按需委派的标准工作角色。

## 1. 先理解：这些文件不是自动注册的永久 Agent

本目录中的 Markdown 是**角色定义（Role Prompt）**，不是 DSH 插件配置，也不会因为文件存在就自动生成一个常驻 SubAgent。

它们的工作方式是：

1. Lead Agent 判断任务是否需要委派。
2. Lead Agent 读取对应角色文件。
3. Lead Agent 将角色文件中的完整职责与本次任务的自包含说明组合起来。
4. Lead Agent 使用 DSH 当前 Profile 中实际可用的 SubAgent 工具创建子 Agent。
5. 子 Agent 返回报告，Lead Agent 对结果负责并进行整合。

如果 DSH 当前会话没有提供 `subagent` / `subagent_fork` 等委派工具，角色文件仍可作为人工提示词使用：新开一个 DSH 会话，明确其角色并提供任务材料。不要假设某个工具必然已安装。

## 2. 角色目录

| 文件 | 角色 | 默认权限/职责 |
|---|---|---|
| `01-PRODUCT-ANALYST.md` | Product & Requirements Analyst | 需求/规格分析；默认只读，输出规格草案 |
| `02-RESEARCH-ANALYST.md` | Research Analyst | 仓库、依赖、官方文档和技术事实调查；只读 |
| `03-ARCHITECTURE-REVIEWER.md` | Architecture Reviewer | 架构方案独立评估；只读，输出 ADR 建议 |
| `04-IMPLEMENTATION-AGENT.md` | Implementation Agent | 按批准任务编码；仅限明确写入范围 |
| `05-TEST-DESIGNER.md` | Test Designer | 测试策略与用例设计；默认只读 |
| `06-INDEPENDENT-REVIEWER.md` | Independent Reviewer | 对指定 diff/commit 独立评审；只读，不自行修复 |

Lead Agent 是根会话，不是本目录中的子 Agent。Lead Agent 负责任务拆分、授权、冲突解决、结果整合和最终汇报。

## 3. 角色调用规则

- **Fresh / Spawn 子 Agent：** 用于需要独立判断、独立调查、避免被主会话结论锚定的任务。必须在 prompt 中提供完整背景，因为它通常不会看到主会话历史。
- **Fork 子 Agent：** 仅当子任务确实需要主会话已完成的上下文时使用。Fork 继承的是已完成的对话上下文，不代表继承主 Agent 的全部工具权限或授权。
- **外部执行 Agent：** 只有当当前 DSH Profile 已配置并显示相应外部运行时工具时才使用。不要把外部 Codex/Claude 运行时当作默认可用能力。
- **AgentTeam：** 仅当有多项相互依赖、需要共享任务状态的工作时启用。它是任务协调能力，不是文件隔离能力。

## 4. 通用委派信息

每一次委派都必须给子 Agent 一个自包含任务包：

- 角色文件路径与版本；
- 任务背景和要回答的问题；
- 相关仓库路径、文档、Commit 或 diff；
- 允许使用的工具和允许写入的文件范围；
- 明确禁止的操作；
- 预期输出格式；
- 完成定义与截止边界（不是时间承诺，而是任务边界）。

不要只写“帮我看看这个问题”。

## 5. 默认文件权限策略

| 角色 | 默认只读 | 允许写入 |
|---|---|---|
| Product Analyst | 是 | 仅在 Lead 明确授权后写 Feature Spec 草案 |
| Research Analyst | 是 | 默认不写；可经授权写调查报告 |
| Architecture Reviewer | 是 | 默认不写；可经授权创建 ADR 草案 |
| Implementation Agent | 否 | 仅限任务卡明确列出的范围 |
| Test Designer | 是 | 默认只输出测试设计；经授权可新增测试文件 |
| Independent Reviewer | 是 | 不得自行修改被审查实现 |

角色定义不替代操作系统权限、DSH Profile 权限或人工审批。Prompt 说“只读”不能代替真正的文件系统/工具权限限制。

## 6. 维护规则

- 修改角色定义时更新本文件的职责说明和 `CHANGELOG.md`。
- 角色定义中的规则不得与 `AGENTS.md`、Master Guide 和已批准 ADR 冲突。
- 遇到产品或架构决策，子 Agent 只能提出建议，不得擅自定案。
- 角色文件应保持任务导向，不要堆积长篇项目背景；背景由每次委派任务包提供。
