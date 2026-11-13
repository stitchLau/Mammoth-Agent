# M5 阶段计划：团队服务端规模化（PostgreSQL/多实例）

- **状态**：Planning（2026-11-13；未开始，触发条件满足后启动）
- **日期**：2026-11-13
- **基线**：`main` @ M4 S0 提交（以 `git log -1` 为准）
- **定位**：把 M4/M4.5 的**单节点、单写者、SQLite** 形态，演进为**PostgreSQL + 多实例/HA + 共享 Artifact** 的团队服务端形态。
- **依据**：`decisions/ADR-0003`（SQLite/PG、备份）、`ADR-0004`（Artifact 存储）、`ADR-0005`（检索）、`ADR-0006`（身份/会话）、`ADR-0008`（单机单写者/导出导入）、`ADR-0012`（PG 多实例=M5）、`docs/specs/M4.5-PHASE45-PLAN.md` §6
- **原则**：仍不接触真实客户生产数据/生产凭据/生产环境；迁移与切换必须先演练、可回滚；不把“多实例”简化为“把 SQLite 放共享盘”。

## 1. 结论

- **PostgreSQL + 多实例 + HA + 共享 Artifact 在 M5 完成**，不在 M4/M4.5。
- M5 不是“必做”阶段：如果单节点 SQLite 满足团队规模，M5 可以长期不启动；一旦触发条件满足，按本计划执行。
- M5 的核心不是“换数据库”，而是消除以下**单节点假设**：
  1. `DataRootInstanceLock` 单写者；
  2. SQLite 专用 SQL/FTS5/触发器；
  3. 本地文件系统 Artifact；
  4. 进程内 `LoginRateLimiter`；
  5. 单进程任务 worker；
  6. 本地 `.db` 备份/恢复路径。
- M5 仍**不做**：双向同步、应用内 LLM/AgentRun、多组织/多租户、面向客户的外部群发；这些另立 ADR/里程碑。

## 2. 触发条件（满足任一即启动 M5 评估）

| 触发 | 说明 |
|---|---|
| 多实例/HA | 需要 2 个以上应用实例、滚动升级、故障切换 |
| 写入瓶颈 | 单节点 SQLite 写入/任务队列成为实测瓶颈 |
| 数据规模 | 数据量/备份窗口/报表需求超出 SQLite 可接受范围 |
| 跨机共享 | 需要多台机器共享同一权威库或只读副本 |
| 团队规模 | 并发用户/项目数/导入量超过单节点容量假设（需实测） |
| 运维要求 | 需要集中备份、监控、审计归档、独立报表 |

> 未满足上述条件时，继续使用单节点 SQLite + 反向代理 TLS + 备份演练是**正确选择**，不是技术债。

## 3. 范围与非目标

### 3.1 范围内

| 编号 | 切片 | 目标 |
|---|---|---|
| M5-0 | 设计与 ADR | PG 版本/拓扑、Artifact 共享存储、迁移/切换、可观测性、回滚 |
| M5-1 | PostgreSQL schema/迁移 | `db/migration-pg` 与 SQLite 双跑；方言/类型/触发器/索引 |
| M5-2 | 检索适配 | PostgreSQL `pg_trgm`（或等价）中文子串检索；与 FTS5 行为对齐 |
| M5-3 | Artifact 共享存储 | `ArtifactStore` 的对象存储/共享文件系统实现 + 迁移/校验 |
| M5-4 | 多实例身份/会话/限速 | DB 会话、共享登录限速、代理/TLS/Cookie、实例健康 |
| M5-5 | 多实例任务队列 | `SELECT ... FOR UPDATE SKIP LOCKED`、租约/心跳、幂等、死信 |
| M5-6 | 部署/备份/恢复/回滚 | 滚动升级、`pg_dump/pg_restore`、Artifact 快照、RPO/RTO、灰度/回滚 |
| M5-7 | 迁移演练与性能/安全验收 | SQLite→PG 演练、负载/并发、跨项目隔离、审计完整性 |

### 3.2 非目标（M5 不做）

- 双向同步、Jira/CSM 写回（另立 ADR）；
- 应用内 LLM/AgentRun（另立 ADR）；
- 多组织/多租户、跨组织数据共享（需产品/合规 ADR）；
- SSO/OIDC 的完整实现（M5 可做评估/Spike，落地另立 ADR）；
- 公网暴露、WAF、DDoS、生产证书采购；
- 真实客户生产数据迁移（只做脱敏/合成数据演练）。

## 4. M5 前置 ADR/决策

| 事项 | 需要的决策 | 关联 |
|---|---|---|
| PostgreSQL 版本/托管 | 17（ADR-0003 Spike 已验证）还是客户指定版本；自建/托管；连接池/参数 | ADR-0003 更新 |
| Artifact 共享存储 | 对象存储（S3/MinIO）优先，还是共享 POSIX 文件系统；一致性/原子写/权限 | ADR-0004 更新或新 ADR |
| 部署拓扑 | 单区域多实例 + 反向代理；是否 HA/只读副本；滚动/蓝绿 | ADR-0012 更新或新 ADR |
| 会话/限速 | 会话已在 DB；登录限速改 DB/共享存储；是否需要验证码 | ADR-0006 更新 |
| 数据迁移/切换 | 停机窗口、导出/导入工具、校验、回滚点、RPO/RTO | 本文件 §6 |
| 检索 | `pg_trgm` 方案、索引/参数、中文效果、性能基线 | ADR-0005 更新 |
| 审计 | PG 上重建 append-only 触发器；审计归档/保留 | ADR-0006/0008 |

> M5-0 必须先完成上述 ADR/设计，再写迁移/代码。

## 5. 切片详情

### M5-0 设计与 ADR

- 产出：PG/Artifact/部署/迁移/可观测性设计；ADR-0003/0004/0006/0012 更新或新 ADR；
- 明确 RPO/RTO、停机窗口、回滚点、容量假设、并发目标；
- 确认 Artifact 共享存储方案（对象存储优先；共享 FS 需评估原子写/锁/权限）；
- 退出：负责人批准 ADR/设计；不写业务代码。

### M5-1 PostgreSQL schema 与迁移

- 新增 `services/domain/src/main/resources/db/migration-pg`（按 ADR-0003/现有 SQLite V1–V10 对齐）；
- 类型映射：TEXT/INTEGER/BOOLEAN/TIMESTAMP、主键/外键/唯一索引；
- 重建 append-only 审计触发器（V3 的 SQLite 触发器在 PG 上需等价实现）；
- Flyway 配置按 profile/dialect 选择 location；`MIN_SCHEMA_VERSION` 与只读入口检查更新；
- 双跑测试：同一迁移序列在 SQLite 与 PG 上幂等；表/索引/约束清单一致；
- 退出：迁移幂等、schema parity、CI（含 PG service container）通过。

### M5-2 PostgreSQL 检索适配

- 现状：`SqliteKnowledgeSearchAdapter` 使用 FTS5 trigram；ADR-0005 Spike 显示 PG `pg_trgm` 中文子串可用，`tsvector('simple')` 对中文 0 命中；
- 实现 `PostgresKnowledgeSearchAdapter`（`pg_trgm` + 相似度/ILIKE/索引；具体查询以 Spike/基准为准）；
- 行为对齐：scope 过滤、仅 verified、topK、引用片段、无跨项目泄漏；
- 性能基线：中文查询 recall/延迟；必要时加 GIN/GiST 索引；
- 退出：检索契约测试在 PG 上通过；与 FTS5 结果差异有记录；无泄漏。

### M5-3 Artifact 共享存储

- 现状：本地内容寻址 `artifacts/<sha256>`；多实例必须共享；
- 方案 A（推荐）：S3 兼容对象存储（MinIO 等），`ArtifactStore` 新实现；内容寻址 key；校验 sha256；写一次/不覆盖；
- 方案 B：共享 POSIX 文件系统（NFS/SMB），要求原子 rename、只读属性/权限、无就地覆盖；需评估锁/故障；
- 迁移：现有 Artifact 上传/校验/回读；失败回滚；
- 退出：两实例读写同一 Artifact；哈希/大小校验；删除/保留策略；备份/恢复覆盖。

### M5-4 多实例身份、会话、限速与 TLS

- 会话已 DB-backed；确保多实例共享同一 DB；Cookie `Secure`/`HttpOnly`/`SameSite`；
- 登录限速从进程内 `LoginRateLimiter` 改为 DB/共享存储（按 IP/用户名/时间窗）；防爆破/DoS；
- 反向代理 TLS、`forward-headers`、只信任配置代理；默认不直接暴露应用端口；
- 健康检查/就绪探针；优雅停机；会话撤销跨实例生效；
- 退出：两实例登录/登出/撤销一致；限速跨实例生效；代理头伪造测试。

### M5-5 多实例任务队列

- 任务领取：`SELECT ... FOR UPDATE SKIP LOCKED`（或等价）；租约/心跳/超时回收；
- 幂等键、重试/退避、死信；任务不重复执行；失败可见；
- 通知投递/Agent 审计入库/导入任务在任一实例执行一次；
- 退出：并发 worker 压测无重复；实例崩溃后任务可回收；死信可重试。

### M5-6 部署、备份、恢复与回滚

- 部署：容器/进程 + 反向代理；配置/密钥管理；滚动/蓝绿；版本/健康检查；
- 备份：`pg_dump`/`pg_restore`（或托管快照）+ Artifact 存储快照；一致性点；校验；
- 恢复：空环境恢复、schema/行数/哈希校验、检索可用、会话失效策略；
- 回滚：应用版本回滚 + 数据回滚点；迁移不可逆时的预案；
- 退出：备份/恢复演练；RPO/RTO 达标；回滚演练。

### M5-7 SQLite→PG 迁移演练、性能与安全验收

- 迁移工具：复用/扩展现有导出格式，或专用 ETL；表顺序/外键/序列；行数/哈希校验；
- 演练：合成/脱敏数据；停机窗口；失败回滚；切换后 smoke；
- 性能：并发用户/检索/导入/任务吞吐；错误率/延迟阈值；
- 安全：跨项目隔离、越权、审计完整性、密钥/日志脱敏、TLS/Cookie；
- 退出：迁移演练报告、性能报告、安全测试通过；负责人验收。

## 6. 数据迁移与切换（草案）

1. **冻结写入**（维护窗口）→ 最终备份 SQLite + Artifact；
2. **导出**：按现有导出/专用 ETL 导出业务表（不含会话/凭据）；
3. **导入 PG**：按外键顺序/序列；校验行数、主键、关键哈希；
4. **Artifact 迁移**：复制/上传到共享存储；逐文件 sha256 校验；
5. **切换配置**：数据源/存储/Flyway location；启动新实例；健康检查；
6. **Smoke**：登录、项目、知识检索、需求/方案、交付、通知、MCP 只读；
7. **回滚点**：保留 SQLite 备份 + 旧版本应用；失败时切回（明确停机/数据丢失窗口）；
8. **观察期**：审计/错误/性能；确认后退役旧数据根。

> 迁移必须先在合成/脱敏数据上演练；禁止直接对真实客户生产库做首次演练。

## 7. 验收标准（M5）

| AC | 标准 |
|---|---|
| M5-AC1 | PG 17（或批准版本）迁移幂等；SQLite/PG schema/约束/索引 parity |
| M5-AC2 | 中文检索在 PG 上可用；scope/verified/无泄漏；性能基线达标 |
| M5-AC3 | 两个以上实例共享 DB/Artifact；无本地单点 Artifact |
| M5-AC4 | 会话/撤销/登录限速跨实例一致；TLS/Cookie/代理头安全 |
| M5-AC5 | 任务队列并发无重复；崩溃可回收；死信可处理 |
| M5-AC6 | 备份/恢复/回滚演练通过；RPO/RTO 达标 |
| M5-AC7 | SQLite→PG 演练通过（行数/哈希/smoke）；有回滚方案 |
| M5-AC8 | 性能/并发/安全测试通过；审计/权限无回归 |
| M5-AC9 | 文档/证据/交接/运维手册完整；无生产凭据/数据进入仓库 |

## 8. 测试策略

- **Contract/Integration**：SQLite/PG 双跑迁移、JPA 映射、检索、Artifact、审计触发器；
- **Multi-instance**：2+ 实例会话/限速/任务/健康/优雅停机；
- **Migration**：SQLite→PG 全量/增量、失败回滚、校验；
- **Performance**：并发用户、检索、导入、任务吞吐、长事务；
- **Security**：跨项目、越权、TLS/Cookie、代理头、审计、密钥脱敏；
- **Regression**：M1–M4 全量 + E2E + 演练；
- **CI**：PG service container；迁移/契约测试；不在本机假装 CI。

## 9. 风险与缓解

| 风险 | 缓解 |
|---|---|
| FTS5→pg_trgm 中文效果差异 | 先 Spike/基准；保留 `SearchAdapter` 抽象；记录 recall/延迟 |
| Artifact 共享存储一致性 | 内容寻址 + 写一次 + sha256；对象存储优先；共享 FS 需原子写评估 |
| 任务重复执行 | `SKIP LOCKED` + 租约/心跳 + 幂等键；压测 |
| 登录限速跨实例 | DB/共享存储；窗口/键设计；压测 |
| 迁移停机/回滚 | 演练、备份、回滚点、维护窗口；不做不可逆迁移 |
| PG 运维能力 | 托管/最小参数/监控/备份手册；不假设 DBA |
| SQLite 专有 SQL/触发器 | 迁移审计、双跑、代码审查；避免业务层方言 |
| 审计触发器差异 | PG 等价触发器 + 不可更新/删除测试 |
| 多实例 Artifact 本地路径 | 移除本地单点；配置校验；启动自检 |
| 范围膨胀 | M5 只做平台规模化；双向同步/LLM/多租户另立 ADR |

## 10. 未知/待确认

- PG 版本/托管方式/连接池/参数；
- Artifact 共享存储选型（对象存储 vs 共享 FS）；
- 部署拓扑/HA/只读副本/区域；
- 目标并发/数据量/RPO/RTO/停机窗口；
- 是否需要 SSO/OIDC（M5 只评估）；
- 是否有多组织需求（另立 ADR）；
- 真实迁移数据来源与授权。

## 11. 与 M4/M4.5/后续阶段的边界

| 能力 | M4 | M4.5 | M5 |
|---|---|---|---|
| 单节点 SQLite | ✓ | ✓ | —（迁移后退役/归档） |
| Jira 只读 API | 文件导入 | ✓（只读 API） | 维护 |
| 邮件/企微 | 完整实现、默认关闭 | 配置启用 | 维护 |
| 远程 Agent 网关 | — | 可选 | 可扩展 |
| 团队服务器单节点 | 预案 | 可选试点 | ✓（多实例） |
| PostgreSQL/多实例/HA | — | — | ✓ |
| Artifact 共享存储 | — | — | ✓ |
| 双向同步/应用内 LLM/多租户 | — | — | 另立 ADR/里程碑 |

## 12. 交付物

- M5 ADR/设计（ADR-0003/0004/0006/0012 更新或新 ADR）；
- `db/migration-pg`、PG 检索适配、Artifact 共享存储、任务队列、部署/备份脚本；
- 迁移/回滚/备份恢复演练脚本；
- `docs/evidence/M5-VERIFICATION.md`、`docs/M5-ACCEPTANCE.md`、`docs/process/M5-TIMELINE.md`、`docs/quality/M5-BUGS.md`；
- 运维手册与交接。

## 13. 参考

- `decisions/ADR-0003-DATABASE-MIGRATION.md`、`ADR-0004-ARTIFACT-STORAGE.md`、`ADR-0005-RETRIEVAL-RAG.md`、`ADR-0006-IDENTITY-AUTHORIZATION.md`、`ADR-0008-WORKSPACE.md`、`ADR-0012-TEAM-SERVER.md`
- `docs/spikes/S4-DATABASE-SPIKE.md`、`S5-ARTIFACT-STORAGE-SPIKE.md`、`S7-RETRIEVAL-SPIKE.md`
- `docs/specs/M4.5-PHASE45-PLAN.md` §6
- `docs/12-ROADMAP.md` M5
