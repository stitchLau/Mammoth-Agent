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
