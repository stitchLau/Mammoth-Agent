# Session Handoff — 2026-11-13（M4 S1：组织/用户生命周期与权限收紧）

- **时间**：2026-11-13（Asia/Shanghai）
- **当前分支 / Commit**：`main`，S1 提交（见 `git log -1`；M4 S0 `4258eed`、M5 计划 `9dc38d7` 之后）
- **当前任务**：M4 S1 已完成并通过本地验证；下一阶段 **S2（BG-01 + Agent 账号 + MCP 审计）**
- **会话目的**：让新会话在无历史上下文时独立恢复 M4 S2 及后续切片

## 已确认事实

- M1–M3（含 M3.5）已完成；M4 S0（ADR/规格/威胁模型）已提交。
- 负责人 2026-11-13 批准进入 S1；S1 口径见 `docs/specs/M4-PHASE4-PLAN.md` §6。
- S1 结果（真实执行）：后端 **103 tests**；前端 build + **25 tests**；Playwright **4 passed（15.1s）**；`m3-acceptance-drill` **18/18**；`admin-drill` **9/9**；`demo-m3.mjs` **25/25**。
- `V7` 已将 schema 升到 7；`MIN_SCHEMA_VERSION=7`。

## 已做变更

- **数据**：`V7__org_admin_user_lifecycle.sql`——`app_user.email/account_type/password_reset_required`、`session.password_reset_required`、`role_assignment` 唯一索引、最早 active 用户回填 `org_admin`。
- **后端**：`RoleAssignment`/`RoleAssignmentRepository`；`AuthorizationService.isOrgAdmin/requireOrgAdmin`；`UserService.createAsAdmin/disable/enable/resetPassword/setOrgAdmin/directory`；`SessionService.validateSession` + 临时口令会话；`SecurityConfig` 强制改密白名单；`AuthController` email/accountType/passwordResetRequired/orgAdmin；`CatalogService` 主数据 org_admin 门禁；`UserController` 生命周期 API。
- **前端**：`ForcePasswordChangePage` + `RequireAuth` 门禁；`SettingsPage` 邮箱/组织成员管理/停用启用/重置口令/Agent 账号；`MembersPage` 最小目录 + org_admin 建用户限制；`ProjectsPage` 客户/产品仅 org_admin。
- **测试**：`OrgAdminTest` 4、`UserLifecycleTest` 4、`PasswordResetTest` 1、`UserDirectoryTest` 1、`PasswordResetAccessTest` 2（真实 HTTP）、`AccountSettingsTest` +1、`MigrationTest` V7/回填；E2E 新增 M4 S1 场景。
- **文档**：M4 计划 §6.6、M4 验证证据 §1、M4 时间线 §4、ADR-0006 v1.1、13-OPEN-DECISIONS、README、CHANGELOG、本交接。

## 运行过的命令和结果

```powershell
mvn -B -ntp -f services/domain/pom.xml verify          # BUILD SUCCESS, 103 tests
cd apps/web; npm run build; npm test                    # build ok, 25 tests
# 空数据根 18164 + npm run test:e2e                      # 4 passed (15.1s)
node scripts/m3-acceptance-drill.mjs                    # 18/18
node scripts/admin-drill.mjs                            # 9/9 (schema v7)
node scripts/demo-m3.mjs http://127.0.0.1:18165         # 25/25
```

## 当前 Git 状态

- 分支 `main`；S1 提交后应干净并推送 origin；以 `git log -1`/`git status` 为准。

## 未解决问题

- S2–S6 未实现：BG-01 交接包 zip、Agent 账号/MCP 审计、导入框架、Jira 文件/BG-03、通知/任务、团队服务器预案。
- ADR-0011/0012/0013 仍 **Proposed**；ADR-0006 增补为 Accepted v1.1。
- `UserService.create` 仍是内部/测试 provisioning primitive；HTTP 走 `createAsAdmin`。
- `orgAdmin` 用户列表 `isOrgAdmin` 为 N+1 查询；小团队可接受，后续可批量查询。
- 未做邮件找回/SSO/多组织；Jira/CSM/企微/SMTP 尚未接入；CI 远端仍未验证。

## 尚未验证的假设

- 团队规模下 org_admin 列表 N+1 的性能（需真实数据量）。
- 强制改密会话在浏览器多标签/并发请求下的体验（当前门禁 + 服务端白名单已测）。
- 真实 SMTP/企微配置后的发送（S5 实现，本地 stub 验证）。
- 真实 Jira 导出格式差异（S4 夹具验证）。

## 下一步最小行动

1. 进入 **S2**：BG-01 交接包 zip（manifest+sha256+批准方案/任务/验收/基线；无凭据/源码；审计 `handoff.export`）；专用 `account_type=agent` 账号；MCP 本地 JSONL 审计 + writer 入库 `agent_call`。
2. 先写测试（zip 内容/哈希/越权、Agent 禁登、JSONL 解析/重复/损坏），再实现。
3. 保持 M1–S1 全量回归。

## 新会话必须先阅读

- `AGENTS.md`、`docs/00-MASTER-GUIDE.md`、`docs/README.md`、本交接；
- `docs/specs/M4-PHASE4-PLAN.md`（§6 S1 §6.6 实施记录、§7 S2 设计）、`docs/specs/M4.5-PHASE45-PLAN.md`、`docs/specs/M5-PHASE5-PLAN.md`、`docs/specs/M4-THREAT-MODEL.md`；
- `decisions/ADR-0006`（v1.1 增补）、`ADR-0008` 增补、`ADR-0011/0012/0013`（Proposed）；
- 代码：`services/domain/src/main/java/com/mammoth/domain/{security,service,web,persistence}`、`apps/web/src/{pages/SettingsPage,MembersPage,ForcePasswordChangePage,App.tsx,lib/api.ts}`。

## 风险提醒

- 强制改密会话只能访问 `/api/auth/{me,password,logout,csrf}`；新增白名单接口需同步 S1 测试。
- `POST /api/users` 为 bootstrap 保留 permitAll，但控制器必须 `requireFullSession`；不要移除。
- 客户/产品主数据仅 org_admin；项目创建仍允许 active 用户。
- 不要把 Agent 账号当人类账号使用；Agent 账号禁登。
- 不得调用 Jira/CSM/企微/SMTP 真实端点；本阶段无外部调用。
- 不要强推/重置分支；不覆盖未提交改动。
