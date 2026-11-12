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

## M4：企业集成和协同

产出：Jira/CSM、MCP、企业微信/邮件、外部 Agent 适配、多用户共享和组织管理。

## 每个里程碑的固定交付

- 已更新的产品规格/领域/架构文档；
- ADR；
- 可运行软件和自动化测试；
- 演示脚本和验收记录；
- 已知限制与下一阶段 backlog；
- Vibe Coding 学习复盘。

## 速度策略

通过缩短反馈周期、使用模板、自动化测试、AI 并行只读研究、减少重复上下文来提速；不通过取消数据权限、测试、审计、文档追溯和人工确认来提速。
