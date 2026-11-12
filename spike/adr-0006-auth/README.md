# ADR-0006 身份与授权 Spike（一次性）

验证 M1 单机最小认证与项目级 RBAC：

- 口令哈希：JDK PBKDF2-HMAC-SHA256（210k 次）vs Spring Security BCrypt（strength 12），比较 hash/verify 耗时与错误口令拒绝；
- 会话：服务端不透明令牌（32 字节随机、DB 只存 SHA-256、8 小时 TTL、可撤销）vs 无状态 HMAC 令牌，比较签名/校验耗时与篡改拒绝；
- 授权：owner/editor/reviewer/viewer × `project.read`/`artifact.write`/`knowledge.create`/`knowledge.review`/`member.manage`，含跨项目、无令牌、角色升级等负向用例。

## 运行

```powershell
mvn -B -q -f spike/adr-0006-auth/pom.xml -DskipTests package
$cp = "spike\adr-0006-auth\target\classes;spike\adr-0006-auth\target\lib\*"
java -cp $cp com.mammoth.authspike.AuthSpike `
  spike/adr-0006-auth/target/work spike/adr-0006-auth/target/result-auth.json
```

## 说明

- 一次性验证代码，不是产品基线；结果写入 `docs/spikes/S6-IDENTITY-AUTH-SPIKE.md`。
- 不涉及真实用户/凭据；测试口令与用户均为合成数据。
- 本探针只验证模型与算法成本，不包含 HTTP 层（Cookie/CSRF/登录页）实现，后者由 Spring Security 在 M1 T4 落地。
