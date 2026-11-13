# M4 验收记录（企业集成与协作）

- **日期**：2026-11-13（Asia/Shanghai）
- **范围**：M4-core（单节点团队协作 + 只读外部集成 + 站内通知 + 外部 Agent 只读交付）
- **规格**：`docs/specs/M4-PHASE4-PLAN.md`
- **证据**：`docs/evidence/M4-VERIFICATION.md`
- **结论**：**S1–S5 本机验收通过；S6 按负责人决定仅文档化并延期；M4 完成（CI 远端未验证）。**

## 1. 验收结论

| 编号 | 验收项 | 结果 | 证据 |
|---|---|---|---|
| M4-AC-01 | org_admin 可创建/停用/启用用户、重置口令；临时会话强制改密；Agent 账号不可登录 | **通过** | `OrgAdminTest`、`UserLifecycleTest`、`PasswordResetAccessTest`、E2E M4 S1 |
| M4-AC-02 | 最后 active org_admin 不可降级；停用撤销旧会话；非 admin 不能管理用户/主数据 | **通过** | `OrgAdminTest`、`PermissionIsolationTest` |
| M4-AC-03 | BG-01 交接包 ZIP 含 manifest/每文件 sha256；秘密命中拒绝；审计 `handoff.export` | **通过** | `DeliveryWorkflowTest` +2、E2E M3 |
| M4-AC-04 | MCP `tools/call` 写 journal；writer 幂等入库 `agent_call`+`audit_log`；只读不建目录/不调度 | **通过** | `AgentAuditJournalTest`、`AgentAuditIngestTest`、`McpServerMainTest` |
| M4-AC-05 | S3 来源/预览/应用/批次/外部记录；Jira 中文 CSV（GBK）可导入；重复幂等、版本追加；映射为需求证据 | **通过** | `ExternalImportTest`、E2E M4 S3 |
| M4-AC-06 | S4 Jira 文件映射不调用真实 API；BG-03 矩阵含业务目标/执行记录/变更/证据/偏差；偏差目标可选设计项/任务/标准 | **通过** | `DeliveryWorkflowTest`、前端构建/E2E |
| M4-AC-07 | S5 站内通知默认启用、收件人正确、去重、排除操作者、未读/已读；E2E 可打开收件箱 | **通过** | `NotificationWorkflowTest`、E2E M2 通知步骤 |
| M4-AC-08 | 邮件/企微完整实现但默认关闭；默认零外发/零 `server_job`；启用缺参启动失败；本地 stub 可测 | **通过** | `NotificationChannelTest`、`NotificationWorkflowTest` |
| M4-AC-09 | 服务端任务幂等、退避、死信、org_admin 可见/重试；只读 MCP 不跑任务 | **通过** | `NotificationJobWorkerTest`、`Schema V10`、`SchedulingConfig` |
| M4-AC-10 | V9/V10 迁移、导出/备份/恢复兼容；120 后端 tests、25 前端 tests、5 E2E、18/18、9/9、25/25 | **通过** | `MigrationTest`、`AdminPackageTest`、`admin-drill`、`demo-m3`、`demo-m4` |
| M4-AC-11 | 文档/ADR/交接/CHANGELOG 同步；M4.5 真实联调清单明确 | **通过** | 本文件、`M4-PHASE4-PLAN.md` §18、`M4-VERIFICATION.md` |
| M4-AC-12 | S6 团队服务器 TLS/证书/反向代理 | **条件项未执行** | 负责人决定“服务器先不要考虑”；仅本机/Docker 文档，BG-02 留 M5 |

## 2. 回归证据（本机）

- 后端：`mvn -B -ntp -f services/domain/pom.xml test` → **120 tests，0 failures/errors**。
- 前端：`pnpm --dir apps/web run build` 通过；`pnpm --dir apps/web run test` → **25 tests**。
- E2E：空数据根 + 后端 18130 + `pnpm --dir apps/web run test:e2e` → **5 passed（19.4s）**。
- M3 验收：`node scripts/m3-acceptance-drill.mjs` → **18/18**。
- ADR-0008：`node scripts/admin-drill.mjs` → **9/9**（schema 10；35 张导出表；22 rows）。
- M3 API 演示：`node scripts/demo-m3.mjs` → **25/25**（需先跑 8080 后端）。
- M4 API 演示：`node scripts/demo-m4.mjs` → **8/8**（需先跑 8080 后端，全新数据根）。

## 3. 安全与边界

- 未调用真实 Jira/CSM/SMTP/企微；未使用负责人提供的令牌/webhook/密码；未写入仓库/日志/测试。
- 真实 74 行 Jira 样例仅本地核对表头；仓库测试夹具为合成数据。
- 邮件/企微默认关闭；启用需环境变量/OS 凭据；状态接口不回显秘密。
- 外部导入不改需求/方案，映射由 owner/editor 显式操作；冻结需求 409。
- 通知/任务表不进管理导出；`agent_call`/外部导入表保留在权威导出。
- 未验证 CI 远端；本机通过不等于 CI 通过。

## 4. 已知限制与二轮输入

- Jira 只读 API、真实 SMTP、企微测试群、CSM 导出/接口、真实项目资料、服务器/证书 → **M4.5**。
- 邮件测试为 Mock `JavaMailSender`，不是 SMTP wire-level sink；M4.5 用真实配置验收。
- CSM 无 API，未自动登录；需负责人提供导出 CSV/Excel 或确认人工导入。
- Windows Playwright 在受限沙箱可能 EPERM；本次在普通终端通过。

## 5. 签署

- 本机验收：Lead Agent，2026-11-13。
- 负责人复核：待确认；第二轮按 `docs/specs/M4.5-PHASE45-PLAN.md` 提供真实配置/资料后执行。