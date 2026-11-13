# 12 — 路线图与里程碑

## M0：工程启动与 DSH Spike

产出：Git 仓库、指令链生效证明、项目构建基线、最小 DSH 插件 Spike、ADR-0001、开发环境安全检查。

退出条件：可重复安装/运行；项目文档可被 DSH 读取；明确插件扩展边界和主要技术风险。

**状态：已完成（2026-11-12）** — 证据：

- 勘察与能力清单：`docs/project-discovery.md`
- 扩展 Spike 与 ADR-0001（Accepted v1.0）：`docs/spikes/S1-DSH-EXTENSION-SPIKE.md`
- MCP 边界与 Java 桥接：`docs/spikes/S2-DOMAIN-API-MCP-BOUNDARY.md`
- 交接记录：`docs/handoffs/SESSION-HANDOFF-2026-11-12-M0.md`
- 未完成/未验证项（UI 渲染、进程重启、权限负向测试）见交接记录，不阻塞 M1 规划。

## M1：项目知识底座

产出：客户/产品/项目档案、资料导入、项目代码基线、知识文档模型、知识审核、检索与证据引用。

退出条件：一个项目的资料可导入、知识可发布、用户按权限检索并得到可核验证据。

## M2：需求和方案

产出：需求池、证据追加、缺口/冲突分析、需求就绪门槛、统一方案模型、业务/技术方案、任务拆解、评审版本。

退出条件：模糊工单可逐步澄清并输出一致、可评审、可追溯的方案。

**状态：已完成（2026-11-12）** — 证据：

- 规格与验收：`docs/specs/M2-PHASE2-PLAN.md`（AC-1–AC-8、状态机、就绪规则、一致性规则）
- 决策：`decisions/ADR-0010-M2-CONTEXT-BASELINE.md`（Accepted）；ADR-0007/0008/0009 保持 OPEN
- 验证：`docs/evidence/M2-VERIFICATION.md`（后端 62 tests、前端 25 tests、E2E 2 passed、演示 26/26）
- 过程/缺陷：`docs/process/M2-TIMELINE.md`、`docs/quality/M2-BUGS.md`
- 交接：`docs/handoffs/SESSION-HANDOFF-2026-11-12-M2.md`
- 限制：CI 远端未运行；应用内 LLM/文档解析/生产化未做（已登记）

## M3：实施回收与知识闭环

产出：开发交接包、实施记录、Git diff 关联、方案偏差分析、As-built 文档、知识更新。

退出条件：至少一个需求从批准方案到真实实施结果和知识更新闭环。

**前置（2026-11-13 已解除）**：ADR-0007（本地只读 Git + 人工不可变基线）与 ADR-0008（单机单写者 + 导出/导入、MCP 只读连接）已 Accepted。M3 首个切片建议顺序：

1. **S1（已完成 2026-11-13）**：数据根实例锁 + 网络盘/云同步拒绝 + MCP 只读连接（ADR-0008）；71 tests、手动 5 项、E2E 2 passed，见 `docs/evidence/M3-VERIFICATION.md`；
2. **已完成（2026-11-13）**：仓库注册 + 基线捕获 + 只读 `GitAdapter`（ADR-0007，`V5` 迁移）；
3. **已完成（2026-11-13）**：开发交接包（Approved Design 快照 + 任务 + 验收 + 基线）；
4. **已完成（2026-11-13）**：`ExecutionRecord`（from/to commit、变更清单、diverged）与偏差分析；
5. **已完成（2026-11-13）**：验收矩阵与 As-built/交付快照；
6. **已完成（2026-11-13）**：知识更新提议 + 人工发布（四眼）+ 过期提示 + supersede/deprecated。

约束：Git 只读、不 clone/fetch/push；代码内容不进知识库；Jira/CI 集成不在 M3。

**状态：已完成（2026-11-13）** — 证据：`docs/specs/M3-PHASE3-PLAN.md` §10–§12、`docs/evidence/M3-VERIFICATION.md` §7–§9、`docs/handoffs/SESSION-HANDOFF-2026-11-13-M3.md`；后端 **89 tests**、前端 25 tests、E2E 3 passed、M3 演示 25/25、验收演练 18/18、ADR-0008 演练 9/9。M3.5 补强：知识发布后半段 + supersede/deprecated、脱敏项目验收演练、实例锁清理 CLI/脚本。剩余技术债见 `docs/specs/M3.5-BACKLOG.md`（交接包文件导出、Baseline 部署/DDL 版本、矩阵证据链接）。

## M4：企业集成和协同

产出：Jira/CSM、MCP、企业微信/邮件、外部 Agent 适配、多用户共享和组织管理。

**状态：S0 草案（2026-11-13）** — 负责人已确认收敛口径：单节点团队协作 + 只读外部集成 + 站内通知 + 外部 Agent 只读交付；生产 API/真实外发/远程网关/多实例延后。

- 计划：[`docs/specs/M4-PHASE4-PLAN.md`](specs/M4-PHASE4-PLAN.md)（S1 组织/用户 → S2 BG-01/Agent/MCP 审计 → S3 导入框架 → S4 Jira 文件/BG-03 → S5 通知/任务/邮件企微 → S6 团队服务器预案）
- 生产集成：[`docs/specs/M4.5-PHASE45-PLAN.md`](specs/M4.5-PHASE45-PLAN.md)（Jira 只读 API、CSM、渠道启用、远程 Agent、团队服务器试点）
- 威胁模型：[`docs/specs/M4-THREAT-MODEL.md`](specs/M4-THREAT-MODEL.md)
- 决策：ADR-0011（外部集成/凭据）、ADR-0012（服务端/PG=M5）、ADR-0013（通知/外发）**Proposed**；ADR-0006/0008 增补
- M3.5：BG-01 → M4 S2、BG-03 → M4 S4、BG-02 → M4 S6/M5
- 约束：M4 不调用负责人提供的 Jira 地址；邮件/企微完整实现但默认关闭；PG 多实例归 M5

## M4.5：生产集成与运营启用

产出：Jira 只读 API（确认版本/TLS/只读账号后）、CSM 适配器（端点确认后）、SMTP/企微渠道启用、可选远程 Agent 网关/团队服务器试点、CI 远端与真实脱敏项目验收。

**状态：规划（2026-11-13）** — 见 [`docs/specs/M4.5-PHASE45-PLAN.md`](specs/M4.5-PHASE45-PLAN.md)。仍单节点 SQLite；不接触生产凭据/数据。

## M5：团队服务端规模化（PostgreSQL/多实例）

产出：PostgreSQL + 多实例/HA、共享限速/会话、任务并发领取、`pg_dump/pg_restore` 备份恢复、TLS/部署/监控/回滚；多组织/SSO 评估；双向同步/应用内 LLM 另立 ADR。

**状态：未开始** — **PG 多实例明确在 M5 完成，不在 M4/M4.5**；触发条件与前置见 [`docs/specs/M4.5-PHASE45-PLAN.md`](specs/M4.5-PHASE45-PLAN.md) §6 与 ADR-0012。

## 每个里程碑的固定交付

- 已更新的产品规格/领域/架构文档；
- ADR；
- 可运行软件和自动化测试；
- 演示脚本和验收记录；
- 已知限制与下一阶段 backlog；
- Vibe Coding 学习复盘。

## 速度策略

通过缩短反馈周期、使用模板、自动化测试、AI 并行只读研究、减少重复上下文来提速；不通过取消数据权限、测试、审计、文档追溯和人工确认来提速。
