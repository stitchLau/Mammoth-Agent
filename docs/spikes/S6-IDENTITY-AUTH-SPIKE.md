# S6 — 身份与授权 Spike（ADR-0006 证据）

- **版本**：0.1
- **日期**：2026-11-12
- **目标**：为 ADR-0006 提供证据——M1 单机最小认证、会话机制、项目级 RBAC 与越权防护。
- **环境**：Windows 10 x64；JDK 17.0.15；Maven 3.9.11；SQLite 3.54；Spring Security Crypto 7.1.2（BCrypt）；commons-logging 1.3.5。
- **方法**：`spike/adr-0006-auth/` 一次性探针——成本对比（PBKDF2 vs BCrypt）、服务端会话与无状态 HMAC 令牌对比、10 条项目级授权用例（含跨项目/无令牌/角色升级）。结果 UTF-8 JSON。

> 分类：**已验证**（实际运行并读到结果）/ **未验证**。一次性验证代码，不是产品基线。

## 1. 口令哈希

| 指标 | PBKDF2-HMAC-SHA256（210,000 次） | BCrypt（strength 12） |
|---|---|---|
| 哈希耗时 | 226 ms | 214 ms |
| 校验耗时 | **56 ms** | 205 ms |
| 存储长度 | 83 字符（`pbkdf2$iters$salt$hash`） | 60 字符（标准 bcrypt 串） |
| 错误口令拒绝 | ✅ | ✅ |
| 依赖 | JDK 内置 | Spring Security Crypto（+ commons-logging） |

## 2. 会话与令牌

| 机制 | 结果 |
|---|---|
| 服务端不透明令牌 | 32 字节随机（256 bit）；DB 仅存 SHA-256（64 hex）；登录 212 ms（含 BCrypt 校验）；校验 1,697 µs（探针每次新开 SQLite 连接，属上限）；**撤销后立即失效** ✅ |
| 无状态 HMAC 令牌 | 签名 2,411 µs（首次含 Mac 初始化）；校验 **58 µs**；篡改即拒绝 ✅ |
| 存储 | 认证库 40,960 B（3 用户 + 会话表） |

## 3. 项目级 RBAC（10 条用例，全部通过）

角色：`owner` / `editor` / `reviewer` / `viewer`；动作：`project.read` / `artifact.write` / `knowledge.create` / `knowledge.review` / `member.manage`。

通过的关键负向用例：

- owner 对**未加入**的项目 P2 读取 → 拒绝（跨项目隔离）；
- reviewer 尝试 `artifact.write`、`member.manage` → 拒绝（角色边界）；
- viewer 尝试 `knowledge.review`、读取 P2 → 拒绝；
- 无令牌访问 → 拒绝；
- 令牌撤销后访问 → 拒绝。

结果：`authzPassed=10, authzFailed=0`。

## 4. 建议（写入 ADR-0006）

1. **口令哈希：BCrypt（Spring Security，strength 12）** 为产品默认；PBKDF2-JDK 作为无依赖备选。校验成本 ~205 ms → 必须配登录限速/锁定，防暴力破解与 DoS。
2. **会话：服务端不透明令牌**（256-bit 随机、DB 存 SHA-256、8 h TTL、可撤销）+ HttpOnly Cookie。理由：可即时吊销（登出全部设备/禁用用户）、无签名密钥轮换问题；无状态 HMAC 保留给未来 API 令牌场景（校验 58 µs）。
3. **授权：默认拒绝、项目级 RBAC**，在服务层统一 `AuthorizationService` 判定（禁止只在 UI 隐藏）；角色矩阵见上；M1 不引入复杂策略引擎。
4. **首次启动**：无默认账号，通过 setup 流程创建组织与 owner；禁止内置测试口令。
5. **审计**：登录成功/失败、授权拒绝、角色变更、用户禁用必须落审计（与 ADR-0003 的 `audit_log` 对齐）。
6. **迁移路径**：`org_id` 已在用户/项目模型内；未来接 OIDC/SSO 时把外部 subject 映射到 `app_user`，会话机制不绑定 Provider。

## 5. 风险与未验证

| 项 | 说明 | 后续 |
|---|---|---|
| HTTP 层未实现 | Cookie 属性（HttpOnly/SameSite）、CSRF、登录页、限速均未实现（本 Spike 只验证模型与算法） | M1 T4 用 Spring Security 落地并补集成测试 |
| 本地无 TLS | 单机 localhost 明文 HTTP；若未来局域网访问必须 TLS | ADR-0008 部署形态时决定 |
| 限速/锁定未测 | 仅提示需要；未实现计数/锁定 | M1 T4 |
| 口令重置/找回 | M1 无邮件；仅 owner 重置 | M1 范围确认 |
| 会话存储 | M1 存 SQLite；多实例/服务端阶段需换集中存储 | 服务端阶段 |
| PBKDF2 vs BCrypt 选择 | 两者均可用；选择 BCrypt 主要因 Spring Security 生态内建 | ADR 批准后固定 |
