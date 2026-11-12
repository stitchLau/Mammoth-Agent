# ADR-0003 数据库/迁移 Spike（一次性）

验证 SQLite 与 PostgreSQL 在 Mammoth M1 场景下的最小可用性：

- Flyway 迁移往返（V1 建表、V2 变更、重复执行幂等）；
- 中文检索能力（SQLite FTS5 trigram vs PostgreSQL pg_trgm / tsvector 'simple'）；
- 备份/恢复（SQLite 文件复制；PostgreSQL 用 `pg_dump`/`pg_restore`，由脚本执行）；
- 迁移与查询耗时、数据库文件大小。

## 运行

```powershell
# 构建（含依赖复制到 target/lib，避免 shaded jar 覆盖 service 文件）
mvn -B -q -f spike/adr-0003-db/pom.xml -DskipTests package

$cp = "spike\adr-0003-db\target\classes;spike\adr-0003-db\target\lib\*"

# SQLite
java -cp $cp com.mammoth.dbspike.DbSpike `
  sqlite spike/adr-0003-db/target/mammoth.db spike/adr-0003-db/target/result-sqlite.json

# PostgreSQL（容器：postgres:17-alpine，127.0.0.1:55432/mammoth，用户 postgres/spike）
java -cp $cp com.mammoth.dbspike.DbSpike `
  pg jdbc:postgresql://127.0.0.1:55432/mammoth postgres spike spike/adr-0003-db/target/result-pg.json
```

## 说明

- 一次性验证代码，不是产品基线；结果写入 `docs/spikes/S4-DATABASE-SPIKE.md`。
- 不读写真实业务数据；样例文档为合成内容。
- PostgreSQL 容器：`docker run -d --name mammoth-pg-spike -e POSTGRES_PASSWORD=spike -e POSTGRES_DB=mammoth -p 55432:5432 postgres:17-alpine`。
- 不要用 shaded/fat jar 运行：它会覆盖 `META-INF/services`，导致 JDBC 驱动与 Flyway 数据库插件加载失败。
