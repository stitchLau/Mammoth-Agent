# ADR-0006: 身份认证、会话与项目级授权

- 状态：**Accepted v1.0**（2026-11-12 负责人批准）
- 日期：2026-11-12
- 决策人：项目负责人（2026-11-12 批准）
- 相关需求/功能：M1 项目知识底座（权限贯穿、审计）；未来组织级认证

## 背景与约束

- M1 是本地/单机优先，但验收要求"换一个无权限用户不能越权检索"——必须有多用户与项目级授权。
- 权限必须贯穿 UI、API、检索、工具（`docs/08-SECURITY-AND-TRUST.md` §3）；不能只在 UI 隐藏。
- 业务状态由关系库承载（ADR-0003 SQLite/Flyway）；凭据不得明文入库/入日志。
- 未来需演进到组织级认证（OIDC/SSO）与服务端多用户；模型不能绑死单机形态。

## 需要解决的问题

1. 口令如何存储与校验；
2. 会话用服务端会话还是无状态令牌；
3. 授权模型与角色矩阵，如何防越权；
4. 首次启动与账号生命周期；
5. 迁移到组织级认证的路径。

## 备选方案

- 口令：A PBKDF2-HMAC-SHA256（JDK） / B BCrypt（Spring Security）。
- 会话：C 服务端不透明令牌（DB 存储哈希、可撤销） / D 无状态 HMAC 令牌。
- 授权：E 项目级 RBAC（owner/editor/reviewer/viewer） / F 细粒度策略引擎（更复杂，M1 不需要）。
- 认证来源：G 本地账号 / H 外部 IdP（服务端阶段）。

## 决策

1. **口令哈希：BCrypt（Spring Security Crypto，strength 12）**；PBKDF2-JDK 作为无依赖备选。
2. **会话：服务端不透明令牌**（32 字节随机、DB 只存 SHA-256、8 小时 TTL、可撤销），通过 HttpOnly + SameSite=Lax Cookie 传递；未来 API 令牌可用无状态 HMAC。
3. **授权：默认拒绝 + 项目级 RBAC**，在服务层统一 `AuthorizationService` 判定；角色 `owner`/`editor`/`reviewer`/`viewer`；动作 `project.read`/`artifact.write`/`knowledge.create`/`knowledge.review`/`member.manage`。
4. **账号生命周期**：首次启动无默认账号，setup 创建组织与 owner；禁用用户立即失效其全部会话；口令重置仅由 owner 执行（M1 无邮件找回）。
5. **审计**：登录成功/失败、授权拒绝、角色变更、用户禁用写入 `audit_log`（与 ADR-0003 对齐）。
6. **迁移路径**：`org_id` 已进入用户/客户/产品/项目模型；未来接 OIDC/SSO 时外部 subject 映射到 `app_user`，会话与授权模型不变。

## 决策理由

- 实测（`docs/spikes/S6-IDENTITY-AUTH-SPIKE.md`）：BCrypt(12) 哈希 214 ms / 校验 205 ms，错误口令拒绝；PBKDF2 210k 为 226/56 ms。BCrypt 与 Spring Security 集成更省胶水代码。
- 服务端令牌可即时撤销（撤销后立即失效，实测通过），安全性优于无状态令牌；后者 58 µs 校验更快，但吊销/轮换复杂，留作 API 令牌。
- RBAC 10 条用例全部通过，覆盖跨项目、无令牌、角色升级；策略引擎对 M1 过重。
- 审计与"权限贯穿"是产品原则与安全文档的硬要求，必须进 M1。

## 取舍和影响

- 205 ms 校验成本 → 必须登录限速/锁定，否则可被用于 DoS/爆破。
- 本地单机 M1 走 HTTP（localhost），无 TLS；未来局域网/服务端必须 TLS。
- 口令找回 M1 不提供自助流程，需 owner 重置；作为已知限制记录。
- 交付前须完成 HTTP 层（Cookie/CSRF/限速/登录页），本 ADR 只固定模型。

## 安全/数据/迁移影响

- 凭据：仅存哈希；禁止日志/提示词/仓库出现口令；会话令牌不落明文库。
- 越权：授权在服务层判定，检索结果按项目过滤（与 ADR-0005 联动）；M1 T10 用真实请求验证。
- 数据迁移：用户/组织/成员表由 Flyway 迁移；未来 IdP 接入需为外部 subject 增加映射列/表。
- 审计日志只追加，不物理覆盖。

## 验证计划和结果

已执行（2026-11-12）：口令哈希成本与错误拒绝；服务端会话登录/校验/撤销；HMAC 令牌签名/校验/篡改拒绝；10 条 RBAC 用例全通过。证据：`docs/spikes/S6-IDENTITY-AUTH-SPIKE.md`。

待补充：Spring Security HTTP 集成（Cookie/CSRF/登录页/限速）、会话并发与锁定、审计落库、OIDC 映射。

## 回滚或替代路径

- 若 Spring Security 集成成本过高 → 自研最小认证层（模型与表结构不变，接口不变）。
- 若需要无状态部署 → 切换 HMAC 令牌（需设计吊销名单）。
- 若客户强制 SSO → 提前引入 OIDC，作为独立 Spike 与 ADR 修订。

## 后续行动

1. ~~负责人批准~~ → 2026-11-12 已批准（Accepted v1.0）。
2. 执行 ADR-0005（检索/RAG）Spike。
3. M1 T4/T10 落地：Spring Security 接入、登录/登出/限速、授权服务、越权测试与审计。

## 2026-11-13 M4 S0 增补：组织角色、用户生命周期与 Agent 账号（Proposed）

> 本增补只扩展 ADR-0006 的授权/账号模型，不改变“默认拒绝 + 项目级 RBAC + 服务端会话”的基线。负责人 2026-11-13 已确认“组织/用户生命周期与权限收紧按建议执行”，详细内容待 S0 评审后随 ADR-0006 v1.1 生效。

### 背景

- 代码现状：授权服务只有项目级 `owner/editor/reviewer/viewer`；`role_assignment` 表存在但未使用；`POST /api/users` 对**任意已登录用户**开放；`GET /api/users` 返回全部用户；`app_user.status` 只有 `active`；`AccountService` 只支持本人改密，**无管理员停用/重置**。
- ADR-0006 决策 4 已承诺“禁用用户立即失效其全部会话；口令重置仅由 owner 执行”，但 M1–M3 未实现。M4 必须补齐，否则团队服务器/外部 Agent 账号无法安全治理。

### 决策

1. **组织级角色 `org_admin`**：复用 `role_assignment`（`scope_type='organization'`, `scope_id=<org_id>`, `role='org_admin'`）；首个用户（bootstrap）自动成为 `org_admin`；迁移 V7 为已有组织回填最早创建的 active 用户。组织角色与项目角色分离：`org_admin` 不自动拥有项目写权限，项目权限仍按 `project_member` 判定。
2. **用户生命周期**：`app_user.status` 使用 `active`/`disabled`；停用立即撤销该用户全部会话（`session.revoked_at`），后续 API 401；启用不恢复旧会话。新增 `account_type`（`human`/`agent`），Agent 账号**禁止 Web 交互登录**，只能作为 MCP/服务身份。
3. **管理员口令重置**：仅 `org_admin` 可重置他人口令；设置临时口令 + `password_reset_required=true`；撤销全部会话；用户下次登录后只允许改密/登出/查看自己（服务端强制，不能只靠 UI）；改密后清除标记并重新登录。
4. **用户创建/目录权限**：`POST /api/users` 仅 `org_admin`（空库 bootstrap 例外）；`GET /api/users`（含状态/创建时间）仅 `org_admin`；新增 `GET /api/users/directory` 给已登录用户返回最小字段（id/displayName/username），用于项目 owner 添加成员。
5. **主数据权限细化**：客户/产品创建改为 `org_admin`；项目创建仍允许任何 active 用户（创建者成为项目 owner）；项目成员管理仍 owner-only。
6. **审计**：`user.create`、`user.disable`、`user.enable`、`user.password_reset`、`user.org_role_change`、`user.session_revoke`、`auth.login_failed`、`auth.password_change` 落 `audit_log`；审计不含口令/临时口令。
7. **迁移与兼容**：V7 增加 `app_user.email`（可空、格式校验）、`account_type`（默认 `human`）、`password_reset_required`（默认 0）；`role_assignment` 增加唯一索引 `(user_id, role, scope_type, scope_id)`；已有用户默认 `human`，不回填 email。
8. **Agent 账号**：`account_type=agent` 的账号不能登录、不能改密；由 `org_admin` 创建，项目 owner 加为 `viewer`；MCP 仍用 `MAMMOTH_MCP_AGENT_USER_ID` 指向该账号（M4 S2 起）。专用服务令牌/远程 Agent 网关不在本增补，放 M4.5。

### 影响/验证

- 影响：团队服务器、通知收件人、外部 Agent 身份、审计读取；不改变项目级四眼/角色规则。
- 验证：`OrgAdminTest`（bootstrap/角色/权限）、`UserLifecycleTest`（停用/启用/会话撤销/Agent 禁登）、`PasswordResetTest`（临时口令/强制改密/旧会话失效）、`UserDirectoryTest`（最小字段/非 admin 不可建用户）、迁移 V7 幂等/回填。
- 回滚：关闭 `org_admin` 校验可回到“任意登录用户可建用户”的旧行为，但不建议；数据列可保留，回滚不丢数据。
