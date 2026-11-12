# Mammoth Agent 文档索引

> 仓库级常驻指令见 [`AGENTS.md`](../AGENTS.md)；产品总指导见 [`docs/00-MASTER-GUIDE.md`](00-MASTER-GUIDE.md)。文档冲突时按 Master Guide 的优先级处理。

## 当前状态（2026-11-12）

- **M1 与 M1.5 已完成**（本地验证通过）；下一阶段 **M2（需求与方案）**。
- 交接入口：[`docs/handoffs/SESSION-HANDOFF-2026-11-12-M1.5.md`](handoffs/SESSION-HANDOFF-2026-11-12-M1.5.md)
- 过程记录：[`docs/process/M1-TIMELINE.md`](process/M1-TIMELINE.md) · 证据：[`docs/evidence/M1-VERIFICATION.md`](evidence/M1-VERIFICATION.md) · 缺陷：[`docs/quality/M1-BUGS.md`](quality/M1-BUGS.md)

## 产品与领域（先读）

| 文档 | 内容 |
|---|---|
| [`00-MASTER-GUIDE.md`](00-MASTER-GUIDE.md) | 产品原则、文档权威、阶段门禁 |
| [`01-PRODUCT-CHARTER.md`](01-PRODUCT-CHARTER.md) | 产品定位与目标 |
| [`02-PRODUCT-BOUNDARIES.md`](02-PRODUCT-BOUNDARIES.md) | 边界（不做什么） |
| [`03-DOMAIN-MODEL.md`](03-DOMAIN-MODEL.md) | 领域模型 |
| [`04-MVP-STRATEGY.md`](04-MVP-STRATEGY.md) | 阶段策略（M2 范围参考） |
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
| [`M1-ACCEPTANCE.md`](M1-ACCEPTANCE.md) | M1 验收记录 |
| [`specs/UI-SPEC.md`](specs/UI-SPEC.md) | UI 规格（路线 A）与 M1.5 实施记录 |
| [`decisions/DECISION-LOG.md`](decisions/DECISION-LOG.md) | M1/M1.5 决策补充日志 |
| [`../decisions/`](../decisions/) | ADR-0001 – ADR-0009 |

## 过程、质量与证据

| 文档 | 内容 |
|---|---|
| [`process/M1-TIMELINE.md`](process/M1-TIMELINE.md) | 提交级执行时间线 |
| [`quality/M1-BUGS.md`](quality/M1-BUGS.md) | 缺陷台账（根因/修复/回归） |
| [`evidence/M1-VERIFICATION.md`](evidence/M1-VERIFICATION.md) | 验证命令、结果、E2E 覆盖、截图 |
| [`handoffs/`](handoffs/) | 会话交接（M0 历史快照 + M1.5 当前快照） |

## 运行与集成

| 文档 | 内容 |
|---|---|
| [`dev-setup.md`](dev-setup.md) | 构建/启动/接口/数据根/CI/E2E |
| [`dsh-mcp-integration.md`](dsh-mcp-integration.md) | DSH ↔ Domain Service MCP 接入 |
| [`demos/M1-DEMO.md`](demos/M1-DEMO.md) | 端到端演示脚本说明 |
| [`../integrations/dsh/`](../integrations/dsh/) | DSH bundle 模板 |

## 方法、角色与提示词

- [`10-VIBE-CODING-PLAYBOOK.md`](10-VIBE-CODING-PLAYBOOK.md)、[`11-DEEPSEEK-HARNESS-GUIDE.md`](11-DEEPSEEK-HARNESS-GUIDE.md)、[`AGENT-USAGE-QUICKSTART.md`](AGENT-USAGE-QUICKSTART.md)
- [`agents/`](agents/)（角色定义与编排）、[`prompts/`](prompts/)（任务提示词）、[`checklists/`](checklists/)（DoD/安全/发布）、[`templates/`](templates/)（ADR/Spec/Review/Handoff）
- [`spikes/`](spikes/)（S1–S7 Spike 报告）、[`project-discovery.md`](project-discovery.md)（初始勘察）

## 新会话推荐阅读顺序

1. `AGENTS.md` → `docs/00-MASTER-GUIDE.md`
2. 本索引 → `docs/handoffs/SESSION-HANDOFF-2026-11-12-M1.5.md`
3. 按 M2 任务读 `docs/04`、`docs/13`、`docs/12` 与相关领域文档
4. 需要实现细节时读 `docs/dev-setup.md`、`docs/evidence/M1-VERIFICATION.md`、代码与测试
