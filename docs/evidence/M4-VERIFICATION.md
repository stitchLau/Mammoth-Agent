# M4 验证证据

- **日期**：2026-11-13（Asia/Shanghai）
- **范围**：M4 S0–S2（S0 文档；S1 组织/用户；S2 BG-01/Agent/MCP 审计）；S3–S6 实施后追加
- **规格**：`docs/specs/M4-PHASE4-PLAN.md`
- **结论**：S0 文档检查通过；S1/S2 自动化、浏览器 E2E 与 CLI 演练通过（本机）；CI 远端未验证。

## 0. S0（2026-11-13）

| 检查 | 命令/方法 | 结果 |
|---|---|---|
| 工作树 | `git status --short` | 仅预期文档新增/修改；无代码/迁移改动 |
| 空白/EOF | `git diff --check` | OK（已修正 4 处 EOF 空行） |
| 本地链接 | 对新增/修改文档提取 `](...)` 并解析相对路径 | OK（修正 `AuthController.java` 路径） |
| 凭据模式 | grep `119.145.133.146`、`password=`、`webhook-url=` | 仅目标地址/占位符；无真实口令/token/webhook |
| 代码构建/测试 | 未运行 `mvn`/`npm`/E2E | 无代码变更；S0 不适用；不把“未运行”表述为通过 |
| 外部调用 | 无 | 未访问 Jira/CSM/SMTP/企微 |

**说明**：S0 的产出是 ADR-0011/0012/0013（Proposed）、ADR-0006/0008 增补、M4/M4.5/M5/威胁模型与文档同步；代码与迁移从 S1 开始。

## 1. S1（2026-11-13，已完成）

| 检查 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `mvn -B -ntp -f services/domain/pom.xml verify` | **BUILD SUCCESS，103 tests**（89 + S1 14） |
| S1 新增测试 | `OrgAdminTest` 4、`UserLifecycleTest` 4、`PasswordResetTest` 1、`UserDirectoryTest` 1、`PasswordResetAccessTest` 2、`AccountSettingsTest` +1、`MigrationTest` V7/回填 | 全部通过 |
| 迁移 | `MigrationTest` | V1–V7 幂等；`app_user`/`session` 新列；`role_assignment` 唯一索引；最早 active 用户回填 `org_admin` |
| 前端 | `cd apps/web; npm run build; npm test` | build 通过；**25 tests** |
| 浏览器 E2E | 空数据根 + `npm run test:e2e` | **4 passed（15.1s）**：M1/M2/M3 + M4 S1（管理员建用户→重置口令→临时会话强制改密→新口令登录） |
| M3 验收演练 | `node scripts/m3-acceptance-drill.mjs` | **18/18** |
| ADR-0008 演练 | `node scripts/admin-drill.mjs` | **9/9**（schema v7） |
| M3 API 演示 | `node scripts/demo-m3.mjs` | **25/25** |

**S1 关键验证**：非 admin 建用户/停用/重置/主数据 → 403；停用撤销旧会话并阻止登录；Agent 账号禁登；最后 active org_admin 不可降级；临时口令会话仅能 `/api/auth/{me,password,logout,csrf}`；改密清除标记并重新登录。真实 HTTP 门禁用 `PasswordResetAccessTest`（RANDOM_PORT）验证。

## 2. S2（2026-11-13，已完成）

| 检查 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `mvn -B -ntp -f services/domain/pom.xml verify` | **BUILD SUCCESS，109 tests**（S1 103 + S2 6） |
| S2 新增测试 | `DeliveryWorkflowTest` +2（zip/manifest/sha256/审计、秘密拒绝）、`AgentAuditJournalTest` 2、`AgentAuditIngestTest` 1、`McpServerMainTest` +1、`MigrationTest` V8、`AdminPackageTest` schema 8 | 全部通过 |
| 迁移 | `MigrationTest` | V1–V8 幂等；`agent_call` 表/索引；V7 回填仍通过 |
| 前端 | `cd apps/web; npm run build; npm test` | build 通过；**25 tests** |
| 浏览器 E2E | 空数据根 + `npm run test:e2e` | **4 passed（15.8s）**：M1/M2/M3（含交接包 ZIP 生成/下载链接）+ M4 S1 |
| M3 验收演练 | `node scripts/m3-acceptance-drill.mjs` | **18/18** |
| ADR-0008 演练 | `node scripts/admin-drill.mjs` | **9/9**（schema v8，32 表） |
| M3 API 演示 | `node scripts/demo-m3.mjs` | **25/25** |

**S2 关键验证**：zip 含 `manifest.properties`（每个文件 sha256）、方案/任务/验收/基线/README/SECURITY；秘密模式命中 `password=...` 时拒绝；`handoff.export` 审计；MCP journal 写入不含参数/正文；writer 入库 `agent_call` + `audit_log` 幂等；损坏 journal 行跳过；`read-only=true` 时调度配置禁用。

## 3. S3–S6（待填）

| 切片 | 检查 | 结果 |
|---|---|---|
| S3 | 导入框架/幂等/dry-run 测试 | 待执行 |
| S4 | Jira 文件映射/BG-03 | 待执行 |
| S5 | 通知/任务/本地 SMTP/HTTP stub | 待执行 |
| S6 | 团队服务器预案（条件） | 待执行 |

## 4. 环境与口径

- Windows PowerShell 5.1；JDK 17.0.15；Maven 3.9.11；Node/npm 按仓库；SQLite(WAL)。
- 测试使用临时数据根与合成数据；禁止真实客户数据/生产凭据/生产环境。
- CI 远端仍未验证；本机通过不等于 CI 通过。
