# Session Handoff — 2026-11-13（M3 完成）

- **时间**：2026-11-13（Asia/Shanghai）
- **当前分支 / Commit**：`main`，M3 S2–S6 提交（`git log -1` 为准；此前 `86abb59` = M3 S1）
- **当前任务**：M3（实施回收与知识闭环）已完成并通过本地验证；下一阶段 **M4（企业集成与协同）**
- **会话目的**：让全新会话在无历史上下文时独立恢复 M4/后续工作

## 已确认事实

- 后端 `mvn verify` → **79 tests**；前端 build + **25 tests**；Playwright **3 passed（12.8s）**；`demo-m3.mjs` **25/25**。
- M3 能力：只读 Git 仓库注册与不可变基线（多仓库、脏树拒绝/owner 覆盖、URL 脱敏）、交接包、实施回收（from/to、变更清单、diverged）、任务完成与人工偏差、验收矩阵（covered/partial/unverified + 未完成候选）、As-built 快照（四眼、Artifact、不可覆盖）、知识更新提议（接受生成知识草稿）、知识过期提示。
- 决策：ADR-0007/0008 Accepted；M3 迁移 `V5`；Git 只读白名单；源码不进入数据库/检索。

## 已做变更

- **数据**：`V5__m3_delivery.sql`（9 表 + `knowledge_document.applicable_baseline_id`）。
- **后端**：`git/LocalGitAdapter`、`baseline/*`、`execution/*`、`delivery/*`、`knowledge/KnowledgeStalenessService`、`web/BaselineController`、`web/DeliveryController`、Knowledge 响应新增 `applicableBaselineId/stale`。
- **前端**：`BaselinesPage`、`DeliveryPage`、需求详情"交付与知识闭环"入口、知识列表/详情/检索"可能过期"标记、侧栏"代码基线"。
- **测试**：`LocalGitAdapterTest` 3、`BaselineWorkflowTest` 2、`DeliveryWorkflowTest` 3、`MigrationTest` V5、E2E 第 3 场景。
- **文档**：M3 规格 §9–§10、验证证据 §7、时间线 §5、缺陷 B-M3-006–008、ADR-0007 落地记录、演示说明、README/ROADMAP/CHANGELOG/13-OPEN-DECISIONS、本交接。

## 运行过的命令和结果

```powershell
mvn -B -ntp -f services/domain/pom.xml verify        # BUILD SUCCESS, 79 tests
cd apps/web; npm run build; npm test                 # build ok, 25 tests
cd apps/web; npm run test:e2e                        # 3 passed (12.8s), 空数据根 18153
node scripts/demo-m3.mjs http://127.0.0.1:18150      # 25/25
```

## 当前 Git 状态

- 分支 `main`；M3 代码与文档同批提交（`git log` 为准）并推送 origin。
- 工作树应干净。

## 未解决问题

- **ADR-0008 后续**：导出/导入包、实例锁清理脚本、备份/恢复演练自动化。
- **ADR-0009**：文档解析仍 OPEN（证据=原始资料+人工摘录）。
- **M4 范围**：Jira/CSM、企业微信/邮件、外部 Agent 适配、服务端运行、组织权限细化、TLS/PG 方言。
- **CI 远端未运行**（所有新测试仅本机）。
- **限制**：Git 变更映射仅文件级；重命名 numstat 近似；子模块不递归；Windows 映射网络盘检测依赖启发式；交接包当前为 Markdown 响应而非可下载文件。

## 尚未验证的假设

- 真实客户仓库（大仓库/中文路径/子模块）下的超时与性能；已用小型临时仓库验证。
- 交付快照 Markdown 作为知识草稿正文的长期可读性（可能过长，后续可拆分为结构化知识条目）。
- 多仓库实施记录在验收矩阵中的聚合是否符合真实项目习惯（当前按任务完成度聚合）。

## 下一步最小行动（M4 或 M3 补强）

1. 若要补强 M3/ADR-0008：先做导出/导入包 v1（manifest + sha256 + 空库导入 + 审计）与备份恢复演练脚本；
2. 若进入 M4：先定外部系统集成边界（Jira/CSM 只读导入 vs 双向同步）与凭据管理方案，避免把生产凭据带入本地；
3. 任何新切片沿用"规格→实现→测试→证据→交接"，并保持 M1–M3 全量回归。

## 新会话必须先阅读

- `AGENTS.md`、`docs/00-MASTER-GUIDE.md`、`docs/README.md`、本交接；
- `docs/specs/M3-PHASE3-PLAN.md`（§9 设计、§10 实施记录）、`docs/evidence/M3-VERIFICATION.md`、`docs/quality/M3-BUGS.md`；
- `decisions/ADR-0007-GIT-BASELINE.md`、`decisions/ADR-0008-WORKSPACE.md`；
- 代码：`services/domain/src/main/java/com/mammoth/domain/{git,baseline,execution,delivery}`、`apps/web/src/pages/{BaselinesPage,DeliveryPage}.tsx`。

## 风险提醒

- Git 集成必须保持只读；不要为"方便"增加 fetch/checkout/commit。
- 批准后的方案/交付快照不可覆盖，变更只能新建版本。
- 知识更新提议接受只生成**草稿**，不得直接 verified；仍需四眼审核。
- 演示/测试使用的仓库必须是合成临时仓库，禁止指向真实客户仓库。
