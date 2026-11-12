# ADR-0003: 关系数据库、迁移与访问层

- 状态：**Accepted v1.0**（2026-11-12 负责人批准）
- 日期：2026-11-12
- 决策人：项目负责人（2026-11-12 批准）
- 相关需求/功能：M1 项目知识底座；ADR-0002（Spring Boot 技术栈）

## 背景与约束

- M1 为本地/单机优先；未来需演进到团队/服务端多用户。
- ADR-0002 已定 Java 17 + Spring Boot 4.1.2 + Maven（Proposed）。
- 结构化业务状态必须由关系库承载；Artifact 原始文件另存（ADR-0004）。
- 检索必须是可替换适配器（`docs/06-KNOWLEDGE-AND-RAG.md` §8），不让业务服务依赖特定引擎。
- 安全：真实客户数据与生产凭据不得进入开发环境；迁移脚本可重复、可回滚/可恢复。

## 需要解决的问题

1. M1 主库选型（SQLite vs PostgreSQL）；
2. 迁移工具与脚本组织；
3. 数据访问层（ORM/DAO）；
4. 中文全文检索的最小可行方案与未来路径；
5. 备份/恢复策略。

## 备选方案

- 方案 A：SQLite（WAL）+ Flyway + JPA；检索走 FTS5 trigram 适配器。
- 方案 B：PostgreSQL 17（Docker/本机）+ Flyway + JPA；检索走 pg_trgm（后续评估中文分词扩展）。
- 方案 C：双库并行支持（同一套域模型、两套方言迁移脚本），M1 默认 SQLite，团队环境用 PG。

## 决策

采纳 **方案 C（默认 A）**：

1. **M1 主库：SQLite（WAL 模式）+ Flyway 13.12.1**；数据文件位于 Mammoth 数据根目录（见 ADR-0002），不放入 DSH Home。
2. **团队/服务端阶段：PostgreSQL 17 + Flyway**；域模型与表/列语义一致，迁移脚本按方言分目录（`db/sqlite`、`db/pg`），禁止业务代码依赖方言专有语法。
3. **迁移工具统一 Flyway 13.12.1**，版本脚本 `V<n>__<desc>.sql`；禁止手工改库；启动时迁移，失败即 fail fast。
4. **访问层：Spring Data JPA** 承载聚合 CRUD；**检索通过独立 `SearchAdapter` + 原生 SQL**（SQLite FTS5 trigram / PG pg_trgm），检索实现可替换、可重建。
5. **备份策略**：SQLite 用在线备份（`VACUUM INTO` 或 backup API，禁止热复制 `.db`）；PostgreSQL 用 `pg_dump -Fc` + `pg_restore`。

## 决策理由

- 实测（`docs/spikes/S4-DATABASE-SPIKE.md`）：Flyway 对两库均支持，迁移幂等；SQLite 体积 56 KB / 迁移 474 ms / FTS5 trigram 中文子串 2 命中 0 ms；PG 迁移 345 ms / pg_trgm 2 命中 / tsvector `simple` 对中文 **0 命中**。
- 本地单机优先 + 零运维：SQLite 免安装、单文件、备份简单，符合 M1 交付形态。
- 双库可行性已被同一探针验证，未来切 PG 不需要重写域模型；差异被限制在迁移脚本与检索适配器。
- JPA 提升 CRUD 开发效率；把全文检索隔离在适配器，避免 ORM 与搜索引擎互相污染。

## 取舍和影响

- SQLite 并发写受限：M1 单用户可接受；多人并发前必须切 PG（ADR-0006/0008 联动）。
- 维护两套迁移脚本：用 CI 对两库各跑一次迁移 + 结构断言，防止漂移。
- PostgreSQL 中文分词需额外扩展（未决策，属 ADR-0005）；M1 中文检索以 trigram/LIKE 为准。
- 未验证：既有数据上的增量迁移、失败迁移修复、并发写、WAL checkpoint 行为。

## 安全/数据/迁移影响

- 数据文件位置受 ADR-0002 数据根目录规则约束（禁止安装目录/`~/.dsh`/源码目录）。
- 迁移脚本纳入代码评审；生产/客户数据不得进入开发库；测试数据必须合成或脱敏。
- 加密与访问控制：M1 由操作系统文件权限 + 应用层认证覆盖；静态加密待 ADR-0006 评估。
- 备份恢复演练在 M1 T3 内执行并记录。

## 验证计划和结果

已执行（2026-11-12）：Flyway 双库迁移/幂等、中文检索对比、SQLite 文件备份恢复、PG `pg_dump`/`pg_restore`（8,669 B / 161 ms / 恢复 3 行）。证据：`docs/spikes/S4-DATABASE-SPIKE.md`。

待补充：CI 双库迁移测试、失败迁移修复演练、WAL 在线备份验证、并发写基准。

## 回滚或替代路径

- 若 SQLite 在 M1 实际负载下不可接受 → 切换 PostgreSQL：域模型不变，替换迁移目录与 `SearchAdapter` 实现；数据迁移用导出/导入脚本。
- 若团队选择统一 PG（方案 B），删除 SQLite 方言目录即可，本 ADR 其余约束不变。

## 后续行动

1. ~~负责人批准~~ → 2026-11-12 已批准（Accepted v1.0）。
2. 执行 ADR-0004（Artifact Storage）与 ADR-0006（身份授权）Spike。
3. M1 T3 落地：Flyway 目录、CI 双库迁移检查、备份恢复脚本与演练记录。
