# S4 — 数据库与迁移 Spike（ADR-0003 证据）

- **版本**：0.1
- **日期**：2026-11-12
- **目标**：为 ADR-0003 提供证据——M1「项目知识底座」的关系库选型、迁移工具、中文全文检索能力、备份恢复。
- **方法**：`spike/adr-0003-db/`（一次性 Java 探针）对两库执行同一流程：Flyway 迁移 V1/V2 → 重复迁移（幂等）→ 插入 3 条合成中文知识 → 中文检索 → 备份/恢复。输出 UTF-8 JSON 作为证据（`target/result-*.json`）。

> 分类：**已验证**（实际运行并读到结果）/ **文档证据** / **未验证**。不是产品代码。

## 1. 实测结果

| 指标 | SQLite 3.54 | PostgreSQL 17 |
|---|---|---|
| Flyway 支持 | ✅ `ok:applied=2,total=2`（Flyway 13.12.1 原生支持 SQLite） | ✅ `ok:applied=2,total=2` |
| 迁移版本记录 | `["1","2"]` | `["1","2"]` |
| 重复迁移（幂等） | ✅ `applied=0,total=0`（11 ms） | ✅ `applied=0,total=0`（40 ms） |
| 迁移总耗时 | 474 ms | 345 ms |
| 存储体积 | **57,344 B（约 56 KB）** | 7.9 MB（含系统目录/扩展；空库基数） |
| 中文检索（子串"收货流程"） | **FTS5 trigram：2 命中，0 ms** | **pg_trgm ILIKE：2 命中，0 ms** |
| 中文检索（`tsvector('simple')`，"收货流程 异常"） | — | ❌ **0 命中**（无中文分词，整段连续汉字成为单 token） |
| 相关性排序 | — | `similarity()` 排序正确（"收货流程异常处理" 首位） |
| 备份 | 文件复制：删除后 0 行 → 恢复后 **3 行** | `pg_dump -Fc`：**8,669 B / 161 ms**；`pg_restore`：**128 ms**；恢复后 **3 行** |

## 2. 关键结论

1. **Flyway 13.12.1 同时支持 SQLite 与 PostgreSQL**，迁移往返、幂等重跑均可用；无需为 SQLite 自建迁移器。
2. **中文检索**：两库用"子串/trigram"路线都可用（各 2 命中、0 ms）；**PostgreSQL 的 `tsvector('simple')` 对中文无效**（0 命中），若未来需要 PG 的分词检索，必须额外引入中文分词扩展（如 zhparser/pg_jieba）并评估运维成本。SQLite FTS5 的 `trigram` tokenizer 在不引入分词器的前提下即可做中文子串检索。
3. **体积与运维**：SQLite 单文件 56 KB vs PG 容器/实例 7.9 MB + 常驻进程；M1 单机优先场景 SQLite 明显更轻。
4. **备份恢复**：SQLite 用文件级恢复（需考虑 WAL 一致性，见风险）；PG `pg_dump/pg_restore` 成熟且快（本例毫秒级）。
5. **双库迁移脚本不能共享同一份 SQL**：类型/自增/扩展语法不同（`timestamptz` vs `TEXT`、`bigserial` vs `AUTOINCREMENT`、`CREATE EXTENSION`）。本次按 `db/sqlite/*.sql` 与 `db/pg/*.sql` 分目录维护，表/列语义保持一致。

## 3. 建议（写入 ADR-0003）

- **M1 主库：SQLite（WAL 模式）+ Flyway**；理由：零运维、单文件、备份简单、中文子串检索可用、满足本地单机。
- **团队/服务端阶段目标：PostgreSQL 17 + Flyway**；同一域模型与表/列语义，按方言维护迁移脚本；检索用 pg_trgm（中文分词扩展作为后续 ADR-0005 议题）。
- **迁移工具：Flyway 13.12.1**，迁移脚本按数据库方言分目录，禁止业务代码依赖方言专有语法（除检索适配器）。
- **访问层**：Spring Data JPA 承载聚合 CRUD；检索走独立 `SearchAdapter` + 原生 SQL；避免 ORM 与全文检索耦合。
- **备份策略**：SQLite 用在线备份（`VACUUM INTO`/backup API）而非热文件复制；PG 用 `pg_dump -Fc`。

## 4. 风险与教训

| 项 | 说明 | 缓解 |
|---|---|---|
| SQLite 并发 | 单写者；M1 单用户可接受，多人并发需 PG | ADR-0006/0008 决策时评估；预留切换 |
| 备份一致性 | 运行中直接复制 `.db` 文件可能拿到不一致快照（WAL） | 用 `VACUUM INTO` 或 SQLite backup API；文档写入运维说明 |
| 迁移脚本分叉 | 双份脚本可能漂移 | 表/列命名与语义保持一致；CI 对两库各跑一次迁移 + 结构断言 |
| 中文分词 | PG `simple` 不切分中文（实测 0 命中） | trigram 方案（已实测可用）；分词扩展开销另评估 |
| 未测试 | 既有数据上的增量迁移、失败迁移修复（`flyway repair`）、并发写、SQLite WAL checkpoint 行为 | M1 T3 中补测试 |

## 5. 工程教训（本次实际踩坑）

- **不要用 shaded/fat jar 运行 JDBC + Flyway**：会覆盖 `META-INF/services`，导致 JDBC 驱动与 Flyway 数据库插件加载失败。改用 `mvn dependency:copy-dependencies` + `java -cp "classes;lib/*"`。
- Flyway 失败时若不记录具体异常，会表现为"表不存在"的次生错误；探针应把 `tryFlyway` 的返回字符串写入结果。
- 本地运行需显式 `Class.forName` 注册两个 JDBC 驱动（或依赖 service 文件完整）。
