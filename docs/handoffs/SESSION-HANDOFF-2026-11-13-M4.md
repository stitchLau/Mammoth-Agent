# Session Handoff — M4 完成

- 时间：2026-11-13（Asia/Shanghai）
- 当前分支 / Commit：`main`；S3–S5 改动待提交（以实际 `git log` 为准；上一基线 `d8e791b`）
- 当前任务：M4 完成
- 会话目的：在 M4 S0–S2 基础上完成 S3–S5，S6 文档化延期，形成 M4 验收

## 已确认事实

- M4-core = 单节点团队协作 + 只读外部集成 + 站内通知 + 外部 Agent 只读交付；生产 Jira/CSM API、真实外发、PG 多实例、远程网关在 M4.5/M5。
- S1/S2 已完成（V7/V8，109 tests 基线）；本会话完成 S3/S4/S5（V9/V10，schema 10）。
- 负责人提供 Jira Data Center 8.16.1 中文 CSV、企微 webhook、CSM 地址/账号；按安全边界**未使用真实令牌/webhook/密码**，未复制真实 74 行 CSV 进仓库。
- S6 负责人决定“服务器先不要考虑”；本机/Docker 文档化，BG-02 留 M5。

## 已做变更

### S3

- V9 `external_source`/`import_batch`/`external_record`；`MIN_SCHEMA_VERSION` 最终 10。
- `ExternalImportParser`（Commons CSV 1.14.1、UTF-8/GBK/BOM、中文 Jira profile、JSON）、`ExternalImportService`、`ExternalImportController`、`ImportsPage`。
- `/imports/preview` 零落库；`/imports` 事务应用；幂等/版本；`map` 生成 `jira`/`external` 证据；`reject`。
- `ExternalImportTest` 4。

### S4

- `ExecutionService.MatrixRow` 增加 `goal/designItemId/executionRecordIds/changeCount/evidenceRefs/deviationIds`；`Analysis.workItemEvidenceRefs`；`DeliveryController` 响应扩展。
- `DeliveryPage` 矩阵列/证据链接/偏差目标选择；`DeliveryWorkflowTest` 断言。

### S5

- V10 `notification`/`notification_delivery`/`server_job`；`NotificationService`、`EmailNotificationChannel`、`WecomNotificationChannel`、`NotificationJobWorker`、通知/管理/任务控制器。
- 事件接入 `SolutionService`、`DeliveryService`、`KnowledgeService`、`ExternalImportController`；站内默认，外部默认关闭；`NotificationProperties` 校验。
- `NotificationsPage`、AppShell 未读数、Settings 渠道状态/测试发送/任务重试、`api.ts` 扩展。
- `NotificationWorkflowTest` 3、`NotificationChannelTest` 3、`NotificationJobWorkerTest` 1。
- E2E 增加 M2 通知步骤 + M4 S3 导入/映射场景，最终 5 passed。

### 文档

- `docs/specs/M4-PHASE4-PLAN.md` §18、`docs/evidence/M4-VERIFICATION.md` §3、`docs/M4-ACCEPTANCE.md`、`docs/dev-setup.md` S3/S4/S5 配置、ADR-0011/0013 Accepted、DECISION-LOG D-039–D-042、ROADMAP/CHANGELOG/M3.5。

## 运行过的命令和结果

- `mvn -B -ntp -f services/domain/pom.xml test` → **120 tests，0 failures/errors**（本机）。
- `pnpm --dir apps/web run build` → 通过。
- `pnpm --dir apps/web run test` → **25 tests**。
- 空数据根 + 后端 18130 + `pnpm --dir apps/web run test:e2e` → **5 passed（19.4s）**。
- `node scripts/m3-acceptance-drill.mjs` → **18/18**。
- `node scripts/admin-drill.mjs` → **9/9**（schema 10，35 张导出表，22 rows）。
- `node scripts/demo-m3.mjs`（先启动 8080 后端）→ **25/25**。
- `node scripts/demo-m4.mjs`（全新 8080 数据根）→ **8/8**。

## 当前 Git 状态

- 工作树含 S3–S5 源码/测试/迁移/文档改动；未提交前不要假设已推送。
- 提交前需 `git diff --check`、审阅完整 diff、确认无真实凭据/真实 CSV。

## 未解决问题 / 限制

- 真实 Jira 只读 API、SMTP、企微测试群、CSM 导出/接口、服务器/证书 → M4.5。
- 邮件测试为 Mock `JavaMailSender`，不是 SMTP wire-level sink；需真实配置联调。
- CSM 无 API；需负责人提供导出文件或确认人工导入。
- `server_job` 为 SQLite 单写者简单 claim；PG/多实例在 M5。
- CI 远端未运行。

## 尚未验证的假设

- Jira 真实 CSV 的自定义字段/编码可能因项目配置不同；当前 profile 基于负责人样例表头 + 合成数据。
- 企微真实 webhook 的 `errcode`/限流行为未实测（仅本地 stub）。
- 管理员浏览器中的测试发送需要先配置渠道与收件人；未做真实发送验收。

## 下一步最小行动

1. 负责人复核 `docs/M4-ACCEPTANCE.md`。
2. 按 `docs/specs/M4.5-PHASE45-PLAN.md` 第二轮提供：轮换后的 Jira 只读令牌（环境变量）、SMTP 配置、企微测试群、CSM 导出样例、脱敏项目资料、本机/Docker 信息。
3. M4.5-1 先做 Jira 只读 API（HTTP 风险已接受，仍建议 HTTPS/内网）；M4.5-2 CSM 仅文件/导出；M4.5-3 渠道启用。

## 新会话必须先阅读

- `AGENTS.md`、`docs/00-MASTER-GUIDE.md`
- `docs/M4-ACCEPTANCE.md`、`docs/evidence/M4-VERIFICATION.md` §3
- `docs/specs/M4-PHASE4-PLAN.md` §18、`docs/specs/M4.5-PHASE45-PLAN.md`
- `services/domain/src/main/java/com/mammoth/domain/external/`、`notification/`
- `services/domain/src/main/resources/db/migration/V9__external_import.sql`、`V10__notifications_jobs.sql`
- `apps/web/src/pages/ImportsPage.tsx`、`NotificationsPage.tsx`、`DeliveryPage.tsx`
- `docs/dev-setup.md` M4 S3–S5 配置

## 风险提醒

- 不要把负责人提供的 Jira 令牌、企微 webhook、CSM 密码写入任何文件/日志/测试；建议轮换后通过环境变量/OS 凭据提供。
- 不要把真实 74 行 Jira CSV 复制进仓库；测试用合成夹具。
- 不要在 M4.5 之前启用真实外发；默认关闭是验收的一部分。
- 提交前检查 `git diff --check`、秘密扫描、迁移/导出/演练；不要强推。
