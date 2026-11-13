# Mammoth Agent 文档索引

> 仓库级常驻指令见 [`AGENTS.md`](../AGENTS.md)；产品总指导见 [`docs/00-MASTER-GUIDE.md`](00-MASTER-GUIDE.md)。文档冲突时按 Master Guide 的优先级处理。

## 当前状态（2026-11-13）

- **M1 / M1.5 / M2 / M3 / M4 已完成**（本机验证；M4 S6 文档化延期）；下一阶段 **M4.5 生产集成与运营启用**。
- M4 收敛口径：单节点团队协作 + 只读外部集成 + 站内通知 + 外部 Agent 只读交付；生产 Jira/CSM API、真实邮件/企微启用、PostgreSQL 多实例、远程 Agent 网关列入 **M4.5/M5**。
- **M4 规格/计划**：[`specs/M4-PHASE4-PLAN.md`](specs/M4-PHASE4-PLAN.md)（S1–S5 完成）、[`specs/M4.5-PHASE45-PLAN.md`](specs/M4.5-PHASE45-PLAN.md)、[`specs/M5-PHASE5-PLAN.md`](specs/M5-PHASE5-PLAN.md)、[`specs/M4-THREAT-MODEL.md`](specs/M4-THREAT-MODEL.md)、ADR-0011/0013（Accepted，M4 范围）、ADR-0012（Proposed）、ADR-0006 v1.1/ADR-0008 增补。
- **M4 验收/证据**：[`M4-ACCEPTANCE.md`](M4-ACCEPTANCE.md) · [`evidence/M4-VERIFICATION.md`](evidence/M4-VERIFICATION.md)。
- **M3.5 技术债**：[`specs/M3.5-BACKLOG.md`](specs/M3.5-BACKLOG.md)（BG-01/BG-03 已关闭；BG-02 留 M5）。
- 交接入口：[`handoffs/SESSION-HANDOFF-2026-11-13-M4.md`](handoffs/SESSION-HANDOFF-2026-11-13-M4.md) · S2：[`handoffs/SESSION-HANDOFF-2026-11-13-M4-S2.md`](handoffs/SESSION-HANDOFF-2026-11-13-M4-S2.md) · S1：[`handoffs/SESSION-HANDOFF-2026-11-13-M4-S1.md`](handoffs/SESSION-HANDOFF-2026-11-13-M4-S1.md) · S0：[`handoffs/SESSION-HANDOFF-2026-11-13-M4-S0.md`](handoffs/SESSION-HANDOFF-2026-11-13-M4-S0.md)
- M3 规格：[`specs/M3-PHASE3-PLAN.md`](specs/M3-PHASE3-PLAN.md) · 证据：[`evidence/M3-VERIFICATION.md`](evidence/M3-VERIFICATION.md) · 过程：[`process/M3-TIMELINE.md`](process/M3-TIMELINE.md) · 缺陷：[`quality/M3-BUGS.md`](quality/M3-BUGS.md) · 验收：[`M3-ACCEPTANCE.md`](M3-ACCEPTANCE.md)
- M2 规格：[`docs/specs/M2-PHASE2-PLAN.md`](specs/M2-PHASE2-PLAN.md) · 证据：[`docs/evidence/M2-VERIFICATION.md`](evidence/M2-VERIFICATION.md) · 过程：[`docs/process/M2-TIMELINE.md`](process/M2-TIMELINE.md) · 缺陷：[`docs/quality/M2-BUGS.md`](quality/M2-BUGS.md) · 验收：[`docs/M2-ACCEPTANCE.md`](M2-ACCEPTANCE.md)
- 历史记录：[`docs/process/M1-TIMELINE.md`](process/M1-TIMELINE.md) · [`docs/evidence/M1-VERIFICATION.md`](evidence/M1-VERIFICATION.md) · [`docs/quality/M1-BUGS.md`](quality/M1-BUGS.md)

## 产品与领域（先读）

| 文档 | 内容 |
|---|---|
| [`00-MASTER-GUIDE.md`](00-MASTER-GUIDE.md) | 产品原则、文档权威、阶段门禁 |
| [`01-PRODUCT-CHARTER.md`](01-PRODUCT-CHARTER.md) | 产品定位与目标 |
| [`02-PRODUCT-BOUNDARIES.md`](02-PRODUCT-BOUNDARIES.md) | 边界（不做什么） |
| [`03-DOMAIN-MODEL.md`](03-DOMAIN-MODEL.md) | 领域模型 |
| [`04-MVP-STRATEGY.md`](04-MVP-STRATEGY.md) | 阶段策略（M4 范围参考） |
| [`05-ARCHITECTURE-BASELINE.md`](05-ARCHITECTURE-BASELINE.md) | 架构基线 |
| [`06-KNOWLEDGE-AND-RAG.md`](06-KNOWLEDGE-AND-RAG.md) | 知识与检索 |
| [`07-WORK-ITEM-LIFECYCLE.md`](07-WORK-ITEM-LIFECYCLE.md) | 工作项生命周期 |
| [`08-SECURITY-AND-TRUST.md`](08-SECURITY-AND-TRUST.md) | 安全与信任 |
| [`09-QUALITY-AND-TESTING.md`](09-QUALITY-AND-TESTING.md) | 质量与测试策略 |
| [`12-ROADMAP.md`](12-ROADMAP.md) / [`13-OPEN-DECISIONS.md`](13-OPEN-DECISIONS.md) | 路线图与开放决策 |

## 规格、计划与决策

| 文档 | 内容 |
|---|---|
| [`specs/M1-PHASE1-PLAN.md`](specs/M1-PHASE1-PLAN.md) | M1 计划与 DoD |
| [`specs/M2-PHASE2-PLAN.md`](specs/M2-PHASE2-PLAN.md) | M2 计划、状态机、就绪规则、一致性规则与实施记录 |
| [`specs/M3-PHASE3-PLAN.md`](specs/M3-PHASE3-PLAN.md) | M3 计划、切片顺序与 S1–S6/M3.5 实施记录 |
| [`specs/M4-PHASE4-PLAN.md`](specs/M4-PHASE4-PLAN.md) | **M4 计划**：S1–S5 完成；S6 文档化延期；数据模型、权限矩阵、通知/导入/Agent 设计、验收 |
| [`specs/M4.5-PHASE45-PLAN.md`](specs/M4.5-PHASE45-PLAN.md) | **M4.5 落地计划**：Jira 只读 API、CSM、通知启用、远程 Agent、团队服务器试点；PG 多实例归 M5 |
| [`specs/M5-PHASE5-PLAN.md`](specs/M5-PHASE5-PLAN.md) | **M5 阶段计划**：PostgreSQL/多实例/HA、共享 Artifact、共享会话/限速、任务并发、迁移/备份/回滚 |
| [`specs/M4-THREAT-MODEL.md`](specs/M4-THREAT-MODEL.md) | M4 威胁模型与安全测试清单 |
| [`specs/M3.5-BACKLOG.md`](specs/M3.5-BACKLOG.md) | M3.5 技术债与延期清单（BG-01/BG-03 已关闭；BG-02 留 M5） |
| [`M1-ACCEPTANCE.md`](M1-ACCEPTANCE.md) / [`M2-ACCEPTANCE.md`](M2-ACCEPTANCE.md) / [`M3-ACCEPTANCE.md`](M3-ACCEPTANCE.md) / [`M4-ACCEPTANCE.md`](M4-ACCEPTANCE.md) | 阶段验收记录（口径、结果、AC 映射、残留） |
| [`specs/UI-SPEC.md`](specs/UI-SPEC.md) | UI 规格（路线 A）与 M1.5 实施记录 |
| [`decisions/DECISION-LOG.md`](decisions/DECISION-LOG.md) | M1–M4 决策补充日志（D-001–D-042） |
| [`../decisions/`](../decisions/) | ADR-0001 – ADR-0008、ADR-0010 + ADR-0011/0013（Accepted，M4 范围）、ADR-0012（Proposed）、ADR-0009 仍 OPEN；应用内 LLM 待另立 |

## 过程、质量与证据

| 文档 | 内容 |
|---|---|
| [`process/M1-TIMELINE.md`](process/M1-TIMELINE.md) / [`process/M2-TIMELINE.md`](process/M2-TIMELINE.md) / [`process/M3-TIMELINE.md`](process/M3-TIMELINE.md) / [`process/M4-TIMELINE.md`](process/M4-TIMELINE.md) | 提交级执行时间线（M4 S0–S2 已记录） |
| [`quality/M1-BUGS.md`](quality/M1-BUGS.md) / [`quality/M2-BUGS.md`](quality/M2-BUGS.md) / [`quality/M3-BUGS.md`](quality/M3-BUGS.md) / [`quality/M4-BUGS.md`](quality/M4-BUGS.md) | 缺陷台账（根因/修复/回归） |
| [`evidence/M1-VERIFICATION.md`](evidence/M1-VERIFICATION.md) / [`evidence/M2-VERIFICATION.md`](evidence/M2-VERIFICATION.md) / [`evidence/M3-VERIFICATION.md`](evidence/M3-VERIFICATION.md) / [`evidence/M4-VERIFICATION.md`](evidence/M4-VERIFICATION.md) | 验证命令、结果、E2E 覆盖、截图（M4 S0–S5） |
| [`handoffs/`](handoffs/) | 会话交接（M0/M1.5/M2/M3-S1 历史快照 + M3/M3.5 + M4 S0/S1/S2 + M4 完成） |

## 运行与集成

| 文档 | 内容 |
|---|---|
| [`dev-setup.md`](dev-setup.md) | 构建/启动/接口/数据根（ADR-0008 锁与只读模式）/CI/E2E |
| [`dsh-mcp-integration.md`](dsh-mcp-integration.md) | DSH ↔ Domain Service MCP 接入（含 `get_requirement_context`，MCP 强制只读） |
| [`demos/M1-DEMO.md`](demos/M1-DEMO.md) / [`demos/M2-DEMO.md`](demos/M2-DEMO.md) / [`demos/M3-DEMO.md`](demos/M3-DEMO.md) / [`demos/M4-DEMO.md`](demos/M4-DEMO.md) | 端到端演示脚本说明 |
| [`../integrations/dsh/`](../integrations/dsh/) | DSH bundle 模板 |

## 方法、角色与提示词

- [`10-VIBE-CODING-PLAYBOOK.md`](10-VIBE-CODING-PLAYBOOK.md)、[`11-DEEPSEEK-HARNESS-GUIDE.md`](11-DEEPSEEK-HARNESS-GUIDE.md)、[`AGENT-USAGE-QUICKSTART.md`](AGENT-USAGE-QUICKSTART.md)
- [`agents/`](agents/)（角色定义与编排）、[`prompts/`](prompts/)（任务提示词，M2 启动用 [`prompts/09-M2-KICKOFF.md`](prompts/09-M2-KICKOFF.md)、M4 启动用 [`prompts/10-M4-KICKOFF.md`](prompts/10-M4-KICKOFF.md)）、[`checklists/`](checklists/)（DoD/安全/发布）、[`templates/`](templates/)（ADR/Spec/Review/Handoff）
- [`spikes/`](spikes/)（S1–S7 Spike 报告）、[`project-discovery.md`](project-discovery.md)（初始勘察）

## 新会话推荐阅读顺序

1. `AGENTS.md` → `docs/00-MASTER-GUIDE.md`
2. 本索引 → M4 完成交接 `docs/handoffs/SESSION-HANDOFF-2026-11-13-M4.md` → M3 交接
3. 按 M4 读 `docs/specs/M4-PHASE4-PLAN.md`、`docs/specs/M4.5-PHASE45-PLAN.md`、`docs/specs/M5-PHASE5-PLAN.md`、`docs/specs/M4-THREAT-MODEL.md`、`docs/04`、`docs/12`、`docs/13`、ADR-0011/0012/0013、ADR-0006/0008 增补
4. 需要实现细节时读 `docs/dev-setup.md`、`docs/evidence/M3-VERIFICATION.md`、代码与测试
