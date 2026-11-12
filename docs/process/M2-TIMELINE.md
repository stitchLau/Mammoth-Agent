# M2 执行时间线（提交级）

- **日期**：2026-11-12（Asia/Shanghai）；时间为本地粗粒度记录，用于复盘而非审计
- **会话**：新会话 Lead Agent，负责人授权"过程中的疑问均按建议执行"（见 `docs/decisions/DECISION-LOG.md` D-010 起）

## 1. 提交

| 提交 | 内容 |
|---|---|
| `742f69d` | `docs(m2)`：M2 计划/验收标准/状态机/就绪规则/一致性规则（`docs/specs/M2-PHASE2-PLAN.md`）；ADR-0010 上下文基线；README/13-OPEN-DECISIONS/DECISION-LOG 更新 |
| `7e4ea30` | `feat(m2)`：V4 迁移 9 表；需求工作项/证据/评估/快照；就绪规则引擎；统一 SolutionModel/一致性/双视图/评审版本；REST API；MCP `get_requirement_context`；测试 62 |
| `e6f0cb6` | `feat(ui)`：需求池、需求详情（证据/11 维评估/就绪/方案版本）、方案编辑与双视图、评审动作；前端测试 25；E2E 增加 M2 场景 |
| 本文件所在提交 | `docs(m2)`：验证证据、时间线、缺陷台账、演示说明、交接、路线图/CHANGELOG |

## 2. 阶段

| 阶段 | 内容 | 结果 |
|---|---|---|
| 阅读与澄清 | AGENTS/README/交接/Master Guide/04/12/13/01/03/06/07/08/09、M1 计划、决策日志、源码基线核对 | 输出 M2 方案 + 9 项拍板建议（Q1–Q9） |
| S0 | 规格 + ADR-0010 + 索引/决策校正 | 先文档后代码 |
| S1 | V4 迁移、WorkItem/Evidence 领域与 REST、审计 | `RequirementWorkflowTest` 6 通过 |
| S2 | 11 维评估、就绪摘要、快照、确认门槛 | 同上 |
| S3 | SolutionVersion/DesignItem、一致性规则、双视图渲染 | `SolutionWorkflowTest` 7 通过（含混合层级修正） |
| S4 | 任务/验收标准/追溯矩阵 | 同上；追溯由树结构派生 |
| S5 | 提交/批准/要求修改/新版本/superseded/四眼 | 同上 |
| MCP | `get_requirement_context` 只读工具 | 工具描述 + 服务测试通过 |
| S6 | 前端页面/路由/单元测试；demo-m2；E2E | 25 tests；demo 26/26；E2E 2 passed |

## 3. 关键验证节点

1. 后端首次全量测试：61 tests 中 1 失败（追溯矩阵：技术设计项为根导致任务不在业务目标子树）→ 引入"混合层级 + 视图根规则"，改为 61 全通过。
2. 演示脚本首跑：19/26（reviewer 执行了 owner-only 动作）→ 调整脚本角色顺序，26/26 通过。
3. E2E 首跑：M2 就绪保存仅 2 个维度落库 → 定位 `reload()` 重置草稿的竞态 → 前端只初始化一次草稿，E2E 等待保存完成；二跑 → 严格模式定位歧义（验收文本同时出现在渲染 Markdown）→ `.first()`；三跑 **2 passed**。

## 4. 未做但已登记

- 应用内 LLM / Agent 草案写入（ADR-0011 待立，Q-005）；
- 文档解析（ADR-0009 OPEN）、Git 基线（ADR-0007 OPEN）、共享工作区（ADR-0008 OPEN）；
- 生产化（TLS/共享会话/PG 方言/后端分页）；
- M2 截图与人工视觉验收（建议 M3 前补一次）。
