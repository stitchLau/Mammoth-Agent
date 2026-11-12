# M1 验收记录（项目知识底座）

- **日期**：2026-11-12
- **基线**：`main`（提交见 `git log -1`）
- **范围**：M1 Phase 1 计划（`docs/specs/M1-PHASE1-PLAN.md`）T1–T12 + 正式认证
- **结论**：**通过**（API/服务级闭环 + UI 功能实现并由后端托管）；浏览器点击级 UI 验证受环境限制未执行，已如实登记；TLS/多实例/PostgreSQL 方言属生产化后续项。

## 1. 验收口径

以 `docs/specs/M1-PHASE1-PLAN.md` §2 演示脚本与 §10 DoD 为准：

> 一个项目完成资料导入、生成知识、检索回答，并给出可核验引用；换一个无权限用户不能越权检索。

## 2. 自动化验收结果（真实执行）

| 检查 | 命令 | 结果 |
|---|---|---|
| 后端构建 + 单元/集成测试 | `mvn -B -ntp -f services/domain/pom.xml verify` | **BUILD SUCCESS，43 tests** |
| 前端类型检查 + 构建 | `npm run build`（apps/web） | 通过（tsc --noEmit + vite build） |
| 前端单元测试 | `npm test` | **7 tests passed** |
| SPA 由后端托管 | `GET /`、`GET /assets/*.js` | **200**（index 含标题、JS 235,600 B） |
| 端到端演示 | `node scripts/demo-m1.mjs <base>` | **14/14 步骤通过**（见第 3 节） |

## 3. 端到端演示步骤（scripts/demo-m1.mjs）

1. `health` → up
2. `csrf-token` → XSRF-TOKEN cookie 下发
3. `bootstrap-first-user` → 首个管理员账号（仅空库开放）
4. `login` → 服务端会话 Cookie 签发
5. `session-auth` → `/api/auth/me` 返回当前用户
6. `anonymous-access-blocked` → 无 Cookie 访问项目 **401**
7. `users` → 登录后创建 reviewer/outsider
8. `master-data` → 客户/产品/项目创建（项目绑定客户）
9. `membership` → reviewer 加入项目（reviewer 角色）
10. `artifact-upload` → 上传 + 同内容去重（同一 artifact id）
11. `knowledge-draft` → 创建知识草稿（含 sourceRefs）
12. `verify-before-review-blocked` → 未提交审核直接 verify **409**
13. `submit-review` → needs_review
14. `self-review-blocked` → 创建者自审 **403**
15. `human-review` → reviewer 审核通过 → verified（reviewer 记录正确）
16. `search-with-citations` → 检索命中 1 条且含引用数
17. `read-document` → 成员读取文档与来源引用
18. `audit-query` → `/api/audit/me` 返回本人审计记录
19. `logout-revokes-session` → 注销后访问 **401**

> 实际执行输出为 14 个 step 的 JSON（部分检查合并计数）；上表为语义步骤清单。

## 4. 安全验收

| 项 | 证据 |
|---|---|
| 口令哈希 | BCrypt(12)，库内仅存哈希（`AuthenticationServiceTest` / `userCreationHashesPassword`） |
| 会话 | 服务端不透明令牌，DB 存 SHA-256；注销/撤销立即失效（测试 + 演示） |
| CSRF | 登录忽略；其余变更需 `X-XSRF-TOKEN`（演示通过；无 CSRF 时 403） |
| 登录限速 | 5 次失败后第 6 次 **429**（`AuthenticationServiceTest.repeatedFailuresAreThrottled`） |
| 越权 | 非成员 401/403、跨项目 404 不泄漏、草稿不进入检索（`PermissionIsolationTest`） |
| 审计不可篡改 | `audit_log` UPDATE/DELETE 被数据库触发器拒绝（`AuditImmutabilityTest`） |
| 草稿不外泄给 Agent | MCP 工具仅暴露 verified（`McpToolServiceTest`） |

## 5. DSH 集成验收（T9）

- 配置型 bundle `integrations/dsh/mammoth-domain-mcp/` 装入 desktop profile → `application: applied`；
- DSH 会话真实调用 `mcp__mammoth_domain__search_project_knowledge` 与 `get_knowledge_document`，返回中文与引用结构；
- 修复 MCP 输出平台编码缺陷（GBK → UTF-8）并加回归测试；
- 验证后已卸载，环境已还原。接入步骤见 `docs/dsh-mcp-integration.md`。

## 6. 已知限制（不声称已完成）

1. **前端**：已实现登录、客户/产品/项目创建、资料上传、知识文档创建/提交审核/审核通过/来源引用查看、已验证知识检索；SPA 由后端托管并通过构建/类型检查；**浏览器点击级验证未执行**（本环境无浏览器控制），仅以构建、单元测试与 API 端到端演示作为证据。
2. **TLS**：本地 localhost HTTP；局域网/服务端部署必须 TLS（部署阶段处理）。
3. **多用户生产化**：登录限速与 MCP agent 身份为单进程/环境变量实现；多实例需共享存储与正式账号体系。
4. **PostgreSQL 方言迁移**：ADR-0003 计划在服务端阶段补充 `db/migration-pg`，当前仅 SQLite。
5. **审计查询范围**：目前提供"本人审计"与"项目审计"；跨对象统一检索与导出未实现。
6. **大文件**：上传上限 50MB，GB 级流式写入未实现。
7. **MCP**：崩溃重连、超时策略、多会话并发未专项验证。

## 7. M1 DoD 对照

| DoD 项 | 状态 |
|---|---|
| 端到端演示脚本可重复执行 | ✅ 14/14 |
| UI 功能可用（登录/项目/资料/知识/检索）并由后端托管 | ✅ 已实现 + `/` 与 assets 200；浏览器点击未实测（环境限制） |
| 迁移可重复/幂等 | ✅ 3 个迁移 + 幂等测试 |
| 权限隔离与审计测试 | ✅ 权限套件 + 审计不可篡改 |
| MCP 工具真实可用、引用可核验 | ✅ DSH 实测 |
| 文档更新（规格/ADR/领域/架构/交接） | ✅ 本轮及历次提交 |
| 已知限制与 backlog | ✅ 第 6 节 |

## 8. 建议下一步

1. 生产化加固：TLS、共享会话/限速存储、MCP 专用只读账号与凭据；
2. 在浏览器中对 UI 做一次人工点击验收（登录/项目/资料/知识审核/检索），补齐真实交互证据；
3. 进入 **M2（需求与方案）** 的规划与 ADR。
