# Session Handoff — 2026-11-13（M4 S2：BG-01 + Agent 账号 + MCP 审计）

- **时间**：2026-11-13（Asia/Shanghai）
- **当前分支 / Commit**：`main`，S2 提交（见 `git log -1`；S1 `1194f5f` 之后）
- **当前任务**：M4 S2 已完成并通过本地验证；下一阶段 **S3（通用只读导入框架）**
- **会话目的**：让新会话在无历史上下文时独立恢复 M4 S3 及后续切片

## 已确认事实

- M1–M3（含 M3.5）、M4 S0/S1 已完成；S1 基线 `1194f5f`。
- S2 结果（真实执行）：后端 **109 tests**；前端 build + **25 tests**；Playwright **4 passed（15.8s，含交接包 ZIP）**；`m3-acceptance-drill` **18/18**；`admin-drill` **9/9**（schema v8）；`demo-m3.mjs` **25/25**。
- `V8` 已将 schema 升到 8；`MIN_SCHEMA_VERSION=8`；`agent_call` 加入导出/导入。

## 已做变更

- **BG-01**：`HandoffPackageService`；`GET /api/projects/{p}/work-items/{w}/handoff/package`；zip 含 `manifest.properties`（需求/方案/基线/上下文 + 每个文件 sha256）、`solution-business.md`、`solution-technical.md`、`tasks.md`、`acceptance.md`、`baseline.md`、`handoff.md`、`README.md`、`SECURITY.md`；秘密模式扫描；Artifact（`handoff_package`）；审计 `handoff.export`；前端“生成交接包 ZIP”+下载链接。
- **MCP 审计**：`AgentAuditJournal`（MCP 只读进程写 `<数据根>/audit/agent-audit.jsonl`）；`AgentAuditIngestService`（writer-only 定时/`ingestNow`）写 `agent_call` + `audit_log(agent.tool_call)`；`event_id` 幂等；损坏行跳过；`SchedulingConfig` 在只读模式禁用；`DataRootBootstrap` 创建 `audit/`；`McpServerMain` 在 `tools/call` 写 journal。
- **数据**：`V8__agent_call_audit.sql`；`AdminTables` 加入 `agent_call`；`MIN_SCHEMA_VERSION=8`。
- **Agent 账号**：复用 S1 `account_type=agent`（org_admin 创建、禁登、项目 viewer）；`docs/dsh-mcp-integration.md` 更新。
- **测试**：`DeliveryWorkflowTest` +2、`AgentAuditJournalTest` 2、`AgentAuditIngestTest` 1、`McpServerMainTest` +1、`MigrationTest` V8、`AdminPackageTest` schema 8；E2E M3 增加 ZIP 步骤。
- **文档**：M4 计划 §17、M4 证据 §2、M4 时间线 §5、`docs/dsh-mcp-integration.md`、`docs/dev-setup.md`、M3.5 BG-01 关闭、README/ROADMAP/13-OPEN-DECISIONS/DECISION-LOG/CHANGELOG、本交接。

## 运行过的命令和结果

```powershell
mvn -B -ntp -f services/domain/pom.xml verify          # BUILD SUCCESS, 109 tests
cd apps/web; npm run build; npm test                    # build ok, 25 tests
# 空数据根 18168 + npm run test:e2e                      # 4 passed (15.8s)
node scripts/m3-acceptance-drill.mjs                    # 18/18
node scripts/admin-drill.mjs                            # 9/9 (schema v8)
node scripts/demo-m3.mjs http://127.0.0.1:18169         # 25/25
```

## 当前 Git 状态

- 分支 `main`；S2 提交后应干净并推送 origin；以 `git log -1`/`git status` 为准。

## 未解决问题

- S3–S6 未实现：导入框架、Jira 文件/BG-03、通知/任务、团队服务器预案。
- ADR-0011/0012/0013 仍 **Proposed**；ADR-0006 v1.1/ADR-0008 增补已落地。
- MCP journal 采用“全文件读取 + event_id 去重”，大文件需轮转；无专门审计 UI；zip 不加密。
- 真实 Jira/CSM/企微/SMTP 未接入；CI 远端仍未验证。

## 尚未验证的假设

- 大 journal/长期运行的 ingest 性能（当前测试为小文件）。
- 真实客户对交接包内容/大小/可读性的接受度。
- 真实 Jira 导出格式（S4 夹具验证）。
- 多实例下 journal/ingest 的并发（M5 前不承诺）。

## 下一步最小行动

1. 进入 **S3**：通用只读导入框架（CSV/JSON + 映射、`external_source`/`import_batch`/`external_record`、幂等、dry-run、审计；V9）。
2. 先写测试（解析/幂等/dry-run/跨项目/审计），再实现；保持 S1/S2 全量回归。
3. S4 Jira 文件/BG-03；S5 通知/任务；S6 条件。

## 新会话必须先阅读

- `AGENTS.md`、`docs/00-MASTER-GUIDE.md`、`docs/README.md`、本交接；
- `docs/specs/M4-PHASE4-PLAN.md`（§8 S3、§17 S2 记录）、`docs/specs/M4.5-PHASE45-PLAN.md`、`docs/specs/M5-PHASE5-PLAN.md`、`docs/specs/M4-THREAT-MODEL.md`；
- `decisions/ADR-0008` 增补、`ADR-0011`（Proposed）；
- 代码：`services/domain/src/main/java/com/mammoth/domain/{delivery,mcp,config,admin,web}`、`apps/web/src/pages/{DeliveryPage,SettingsPage}.tsx`。

## 风险提醒

- MCP 只读进程不能写 `audit_log`；审计必须走 journal + writer 入库，不要为了“方便”改成 MCP 写库。
- `agent_call` 是权威审计表，导出/导入需保留；通知/任务等运营表不要混入。
- 交接包不得包含凭据/源码；秘密扫描命中要拒绝，不要只警告。
- 仍不得调用 Jira/CSM/企微/SMTP 真实端点。
- 不要强推/重置分支；不覆盖未提交改动。
