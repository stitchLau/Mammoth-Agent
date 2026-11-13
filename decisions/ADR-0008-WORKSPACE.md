# ADR-0008: 项目工作区与正式知识空间（单机本地 / 共享演进）

- 状态：**Accepted v1.0**（2026-11-13 负责人指示"先定 ADR-0007/0008"，按 Agent 建议定案）
- 日期：2026-11-13
- 决策人：项目负责人（2026-11-13 指示定案；决策内容由 Lead Agent 按建议执行并记录）
- 相关需求/功能：M3 实施回收、团队阶段演进、备份与迁移
- 相关文档：`decisions/ADR-0002`（技术栈/数据根）、`ADR-0003`（SQLite/PG、备份）、`ADR-0004`（Artifact）、`ADR-0006`（身份授权）、`ADR-0007`（Git 只读基线）、`ADR-0010`（上下文基线）、`docs/02-PRODUCT-BOUNDARIES.md`

## 背景与约束

- M1/M2 交付形态为**单机 localhost**：Domain Service + SPA + SQLite(WAL) + 本地内容寻址 Artifact；数据根默认 `%LOCALAPPDATA%\Mammoth\data`（`DataRootResolver`）。
- `DataRootResolver` 现有规则：可配置（`MAMMOTH_DATA_ROOT`/`mammoth.data-root`）；拒绝 `~/.dsh`、源码 checkout；启动时创建 `db/`、`artifacts/`、`cache/` 并做写读探测（`DataRootBootstrap`）。
- 现状缺口（代码证据，2026-11-13）：**没有实例锁**（`config` 包无 lock 相关实现），**没有网络盘/云同步目录检测**；MCP 入口（`McpServerMain`）启动完整 Spring 上下文，会执行 Flyway 迁移（即 MCP 进程也会写库）。
- `ADR-0003` 已定：SQLite 用**在线备份**（`VACUUM INTO`/backup API），禁止热复制 `.db`；团队阶段切 PostgreSQL。
- `ADR-0004`：Artifact 字节在数据根 `artifacts/<sha256>`，元数据入关系库；跨项目去重但按项目授权。
- `ADR-0007`：客户代码仓库只读注册，源码不进入 Mammoth 数据根。
- 安全：数据根不得位于 DSH Home/源码目录；导出物含客户资料，属 Confidential。

**环境证据（2026-11-13，本机探针）**：

| 探针 | 结果 |
|---|---|
| 数据根默认位置 | `C:\Users\Lau\AppData\Local\Mammoth\data`（`LOCALAPPDATA`，未落在 OneDrive 路径） |
| `Win32_LogicalDisk C:` | `DriveType=3`（本地固定盘）、`FileSystem=NTFS` |
| SQLite `VACUUM INTO`（WAL、写连接打开） | 首次失败：`SQLITE_ERROR: cannot VACUUM - SQL statements in progress`；改为关闭语句、autocommit、专用 Statement 后 **成功**，副本打开读回 **1 行**（`restored_rows=1`） |
| 只读连接（写连接同时打开） | 读到 **1 行**；写尝试被拒（`readonly_write_blocked=true`），JDBC `open_mode=65`（READONLY 模式） |

> 注意：`VACUUM INTO` 要求无进行中的语句/事务；实现必须使用专用连接与已关闭的语句，不能与业务事务混用。

## 需要解决的问题

1. "本地执行空间"与"可共享的正式知识空间"的边界；
2. 多 PC 同步、冲突与权限；
3. 离线能力；
4. 备份/恢复与迁移；
5. 网络盘/云同步盘风险；
6. 多实例（Web + MCP + 用户重复启动）对同一数据根的并发写；
7. 向团队/服务端形态演进的路径。

## 备选方案

- **方案 A（推荐）**：**单机本地单写者 + 显式导出/导入包**。正式知识空间 = 本地数据根；代码工作区 = 只读注册的本地仓库路径；跨机通过导出/导入包人工搬运；单写者用实例锁保证。
- **方案 B**：共享网络盘数据根（多 PC 直接打开同一目录）。看似省事，但 SQLite WAL 在 SMB/NFS 上的锁语义不可靠，多机并发写有损坏风险；且无法做跨机实例协调。**拒绝**。
- **方案 C**：客户端-服务端（服务端权威存储 + 多终端）。团队阶段目标（PostgreSQL + TLS + 共享会话 + 服务端 Artifact），但需生产化与运维投入，不在 M3 前。
- 方案 D：把数据根放进 OneDrive/坚果云等同步盘，靠云同步"共享"。**拒绝**：文件级同步与 SQLite 事务/WAL 冲突，可能产生静默损坏。

## 决策

采纳 **方案 A**，并明确以下 13 条：

1. **v1 形态：单机本地、单写者**。正式知识空间 = Mammoth 数据根（`db/` + `artifacts/`）；`cache/` 为可删除派生数据（索引可重建）。
2. **数据根必须位于本地固定磁盘**（Windows NTFS/APFS/ext4 等）。启动时拒绝：UNC 路径、映射网络驱动器、已知云同步目录（OneDrive/Dropbox/Google Drive/iCloud 等路径特征）。测试或受控环境可用显式开关（如 `MAMMOTH_ALLOW_UNSUPPORTED_ROOT=true`）覆盖，但必须记录警告，不得作为默认。
3. **单写者实例锁**：数据根下 `.mammoth-instance.lock`（独占 OS 文件锁）；持有者元数据（PID、主机名、启动时间、角色）写入旁路 `.mammoth-instance-holder`，供被阻塞进程读取（Windows 强制锁下被锁文件不可读）。Web/写实例启动时独占；已有活动实例则**拒绝启动**并给出提示。陈旧锁只能人工确认后清理，**不自动抢锁**（OS 在进程退出时已释放锁，残留文件可删除或覆盖）。
4. **MCP/只读进程不写库**：MCP 入口以**只读连接**打开 SQLite（`SQLiteConfig.setReadOnly(true)`，等价 `mode=ro` + `PRAGMA query_only=ON`），**不执行 Flyway**、不写业务表；迁移只由 Web/管理入口执行。若数据根尚未初始化（无 schema），MCP 给出可操作错误并提示先启动 Web/管理入口完成迁移，**不得自行迁移**。实测只读连接在写连接打开时可读、写入被拒（见证据表）。
5. **代码工作区不是 Mammoth 数据**：客户代码留在其本地仓库路径（ADR-0007 只读注册），不复制进数据根、FTS 或向量索引；Mammoth 只存仓库路径、commit 与变更清单。
6. **跨机同步 v1 = 导出/导入包**：`<数据根>/exports/<时间戳>-mammoth-export.zip`，含 `manifest.json`（schema 版本、应用版本、表计数、每个文件 sha256）与业务数据；**默认不含**用户密码哈希、会话、DSH 数据、任何凭据。
7. **导入约束**：仅允许导入**空库**（或全新数据根）；不提供双向合并/冲突自动解决；导入由项目 owner 执行并审计。同一项目不允许多地同时编辑，需要共享时先汇出、再在唯一权威数据根导入。
8. **冲突策略**：v1 通过"单写者 + 单权威数据根"从源头规避冲突；出现两个数据根副本时，以 owner 指定的权威根为准，另一份只作归档，不做自动合并。
9. **权限**：操作系统文件 ACL（仅当前用户）+ 应用层 RBAC（ADR-0006）双层；完整仓库路径、导出包仅授权角色可见；导出/导入动作落 `audit_log`。
10. **导出物安全**：导出包视为 Confidential（含客户资料/知识正文），不得提交到代码仓库、不得放入公开网盘/共享目录；v1 不加密（避免伪安全），加密与密钥管理另立 ADR。
11. **离线**：全部功能离线可用；无云依赖；Git 仅本地只读（ADR-0007）。
12. **备份/恢复**：
    - SQLite：`VACUUM INTO`（专用连接、无进行中语句、目标不存在）或 SQLite backup API；**禁止**运行中直接复制 `.db`（ADR-0003）；
    - Artifact：目录复制 + 逐文件 sha256 校验（字节不可变，可安全复制）；
    - 恢复：停止写实例（及只读进程）→ 还原数据根 → 启动校验（Flyway schema 版本、Artifact 校验、实例锁重建）；
    - 演练记录与脚本纳入 M3 交付。
13. **演进路径**：团队/服务端阶段切 PostgreSQL + 服务端 Artifact + TLS + 共享会话（ADR-0003/0006）；服务端为权威，客户端离线写**不在** v1 承诺内；多 PC 实时协作属 M4。v1 的导出/导入格式需可迁移到服务端导入工具。

## 决策理由

- 单机单写者与当前技术栈（SQLite WAL、本地 Artifact）一致，避免用共享盘/同步盘制造静默损坏。
- 实例锁把"同一数据根两个写进程"这一真实风险（Web + 用户误启第二个实例）变成显式失败；MCP 只读连接消除了"MCP 进程也跑迁移"的隐患（证据：只读连接可读、写入被拒）。
- 导出/导入包是最小可用的跨机方案，且不引入网络/账号/加密复杂度；等团队阶段再用服务端权威存储替换。
- `VACUUM INTO` 实测可行，但"语句进行中"会失败，因此把它写成明确的实现约束而非"随手一调"。

## 取舍和影响

- **正向**：数据一致性边界清晰；无网络依赖；备份/迁移可演练；与 ADR-0003/0004/0007 一致。
- **负向**：单实例（同一数据根不能同时开两个写服务）；多 PC 需手工导出/导入；不加密导出包；无实时协作；云盘/网盘不可用（对部分用户是限制）。
- **运维成本**：需要 lock 清理指引、备份计划、导出/导入操作手册；MCP 只读模式需单独启动参数/校验。
- **性能**：`VACUUM INTO` 与仓库大小成正比；大库建议低峰执行并记录耗时。
- **兼容**：M1/M2 已有数据根无需迁移；新增 lock 文件与启动校验；导出格式需版本号（v1）。
- **未验证**：SQLite 只读连接在 Windows 上的长时并发（MCP 常驻）与 `VACUUM INTO` 并发读的组合；导入大包的耗时与磁盘余量；云同步目录检测的误报率。

## 安全/数据/迁移影响

- 新增启动校验（网络盘/云同步目录、实例锁）；无数据库迁移；导出/导入引入新的数据出口，必须审计与访问控制。
- 导出包不含密码哈希/会话/凭据（若未来需要账号迁移，另立加密迁移 ADR）。
- 只读 MCP 连接降低写面，但仍需项目成员授权（ADR-0006）与只读工具白名单。
- 网络盘/云同步拒绝是**安全默认**；覆盖开关必须记录并仅限测试/受控迁移。
- 数据根搬迁：停机后整体复制即可（路径无关）；迁移后需校验 schema 版本与 Artifact 校验和。

## 验证计划和结果

**已执行（2026-11-13）**：

1. 数据根默认路径在本地固定盘（`DriveType=3`、NTFS），未在 OneDrive 路径；
2. `VACUUM INTO` 在 WAL + 写连接打开时成功（专用语句、autocommit），副本读回 1 行；失败模式（语句进行中）已复现；
3. 只读连接与写连接并存：可读 1 行，写被拒（`open_mode=65`）。

**M3 必须补齐的验证**：

1. 实例锁：双进程启动第二个被拒；进程崩溃后陈旧锁提示与人工清理；锁文件内容与心跳；
2. 网络盘/UNC/云同步路径拒绝与覆盖开关；
3. 导出/导入往返：空库导入、计数与 sha256 校验、凭据不含、审计记录；
4. 备份/恢复演练：`VACUUM INTO` + artifacts 复制 + 恢复后 Flyway/schema 与检索可用；
5. MCP 只读模式：无 Flyway、无写操作、工具可用；与 Web 写实例并发读取；
6. Windows 路径/中文文件名/长路径。

## 回滚或替代路径

- 若单实例锁在真实使用中造成困扰（如 MCP 与 Web 并发场景）→ 保留"只读进程不取锁"的规则，仅扩展只读进程角色；
- 若团队阶段提前到来 → 按方案 C 切服务端权威存储（ADR-0003 PostgreSQL），数据根降级为本地导入/缓存；导出格式作为迁移输入；
- 若必须支持云盘 → 需新 ADR 评估 WAL 一致性与冲突，不得直接放开。

## 后续行动

1. M3 首个切片：数据根实例锁 + 网络盘/云同步拒绝 + 启动校验测试；
2. M3：MCP 入口改只读连接（不跑迁移）、Web/管理入口独占迁移；
3. M3：导出/导入包 v1（manifest + sha256 + 空库导入 + 审计）；
4. M3：备份/恢复脚本与演练记录（`VACUUM INTO` + Artifact 校验）；
5. 把本 ADR 的约束写入 `docs/dev-setup.md`（运维/备份/迁移）与 `docs/08-SECURITY-AND-TRUST.md`（导出物分级）。

## 2026-11-13 S1 落地记录（M3）

M3 切片 1 已实现本 ADR 的以下部分（证据：`docs/evidence/M3-VERIFICATION.md`、`docs/specs/M3-PHASE3-PLAN.md` §8）：

- 决策 2（路径校验）：UNC、已知云同步目录、已知远端文件系统类型（SMB/CIFS/NFS/AFPFS/WebDAV/FUSE-SSHFS）在启动时拒绝；`mammoth.data-root.allow-unsupported=true` 可覆盖（仅测试/受控迁移）。
- 决策 3（单写者）：`DataRootInstanceLock` 独占锁 + `.mammoth-instance-holder` 元数据；第二写实例启动失败并显示持有者。
- 决策 4（只读进程）：MCP 入口强制只读（System property 覆盖配置文件、拒绝 `--mammoth.data-root.read-only=false`）；不取锁、不建目录、不执行迁移；只读连接 + `PRAGMA query_only=ON`；数据根未初始化时提示先用 Web 入口初始化。
- 测试：`DataRootResolverTest`（8）、`DataRootBootstrapTest`（4）、`DataRootInstanceLockTest`（2）、`ReadOnlyModeTest`（1）；手动集成 5 项（双写拒绝、UNC/云同步拒绝、MCP 只读并存、未初始化根失败）。

**偏差**：holder 元数据从锁文件内改为旁路文件（Windows 强制锁），详见 `docs/quality/M3-BUGS.md` B-M3-001。

**仍未实现**：导出/导入包、实例锁清理脚本、备份/恢复演练、Windows 映射网络盘检测（JVM 限制）。
## 2026-11-13 补齐：导出/导入与备份/恢复（M3 后）

- **实现位置**：com.mammoth.domain.admin（ExportService/ImportService/BackupService/RestoreService/AdminRunner），通过 java -jar mammoth-domain-service.jar --admin-* 运行；admin 参数在构建 Spring 环境前被过滤，避免口令进入配置属性。
- **导出**：<dataRoot>/exports/<时间戳>-mammoth-export.zip；内容为 data/<table>.sql（31 张业务表，跳过 session 与 lyway_schema_history）、rtifacts/<sha256> 原始字节、manifest.properties（formatVersion、createdAt、appVersion、schemaVersion、每表行数、每个条目 sha256）。pp_user.password_hash 一律替换为 !mammoth-import-locked。
- **导入**：仅接受**空库**（逐表计数为 0）；校验 schema 版本一致、每个 zip 条目都在 manifest 中有 sha256 且匹配、每表行数一致、artifact 行与字节大小一致；单事务插入（PRAGMA defer_foreign_keys=ON 处理自引用外键）；--admin-username 对应账号设置新的 BCrypt 口令，其余账号保持锁定（无法登录，需管理员重建/后续密码重置）；不导入会话；写 dmin.import 审计。
- **备份**：VACUUM INTO（专用连接、无进行中语句）+ 按 rtifact.storage_path 复制 Artifact + 校验哈希；manifest.properties 含 db sha256/size、schema 版本、artifact 哈希。备份需在写实例停止时执行（实例锁保证）。
- **恢复**：纯文件操作，不启动服务；校验备份 db 的 sha256 与 PRAGMA integrity_check、schema 版本、每个 artifact 的 sha256；目标必须是空目录；写 estore.properties 与 ackup-manifest.properties 供追溯。
- **偏差**：manifest 使用 **key-value properties** 而非 ADR 原文的 manifest.json（避免引入 JSON 解析依赖，字段等价）；导出不加密（与决策 10 一致）；导入不做合并/增量（决策 7）；映射网络盘仍按 S1 的限制处理。
- **验证**：AdminPackageTest 4 tests（往返、口令锁定、篡改/非空拒绝、备份恢复、篡改备份拒绝）；scripts/admin-drill.mjs **9/9** 真实 CLI 演练（种子→导出→导入→启动校验→备份→恢复→再启动→非空拒绝）；后端全量 **83 tests**。
- **实例锁清理（2026-11-13 补齐）**：`--admin-clear-lock --data-root=<root>` + `RestoreService` 同风格纯文件操作；仅在无活动写实例时删除锁与 holder，运行中拒绝并显示持有者；`scripts/clear-data-root-lock.ps1` 为运维封装；`LockCleanupServiceTest` 4 tests。
- **未实现（后续）**：导出包加密与密钥管理、账号批量重置流程、增量导出。

## 2026-11-13 M4 S0 增补：新表分类、Agent 审计与团队服务器边界（Accepted，M4 S2/S3/S5 实施）

> 负责人 2026-11-13 确认：M4 仍单节点单写者 + SQLite；团队服务器/证书暂不处理；PG 多实例归 M5。本增补细化 ADR-0008 在 M4 的落地口径。

### 1. 新表分类（权威 vs 运营）

| 表 | 分类 | 是否进导出/导入 | 说明 |
|---|---|---|---|
| `external_source` | 权威业务元数据 | 是 | 项目外部来源（Jira/CSM/通用），不含凭据 |
| `import_batch` | 权威业务元数据 | 是 | 导入批次、原始 Artifact、校验/应用状态 |
| `external_record` | 权威证据索引 | 是 | 外部记录/版本/映射/哈希，不含源码正文 |
| `agent_call` | 权威审计（Agent 调用） | 是 | MCP 调用事件从本地 journal 入库，唯一 event_id |
| `notification` | 运营/用户数据 | 否（默认） | 可由业务事件重建；不进入跨机导出 |
| `notification_delivery` | 运营 | 否 | 外发重试/死信；含目标渠道状态，不含秘密 |
| `server_job` | 运营 | 否 | 任务队列；跨机导入会产生重复执行风险 |

- `AdminTables` 只加入“是”的表；`MigrationTest`/`admin-drill` 覆盖新增表。
- `MIN_SCHEMA_VERSION` 每次迁移递增；只读 MCP 的 schema 门槛同步。
- 导出包仍不含密码哈希（锁定值）、会话、SMTP/企微/API 凭据。

### 2. Agent 审计落点（MCP 只读）

- MCP 进程只读，**不能写 `audit_log`**；采用本地 append-only JSONL（`<dataRoot>/audit/agent-audit.jsonl`）+ writer 侧入库：
  - MCP 每次 `tools/call` 写一行：`eventId`、`at`、`agentUserId`、`tool`、`projectId`、`resultCount`、`durationMs`、`error`（不含正文/参数原文）；
  - writer 侧任务读取新行，写入 `agent_call`（唯一 `event_id`）并追加 `audit_log`（`action=agent.tool_call`，`object_type=agent_call`，`object_id=eventId`）；
  - MCP 不创建目录、不取锁；目录由 writer 启动时创建；文件不可写时降级为 stderr 并告警；
  - 该机制为过渡方案；HTTP Agent Gateway/服务令牌在 M4.5 评估。
- JSONL 文件路径、轮转/保留策略、损坏行处理写入 M4 规格与 `dev-setup`。

### 3. 团队服务器/单写者

- 团队服务器仍是**单节点单写者**；后台任务 worker 只在 writer 进程运行，`read-only=true` 的 MCP 进程不跑任务、不发通知、不迁移。
- 反向代理 TLS 是部署层能力，见 ADR-0012；数据根仍必须本地固定盘，不允许网络盘/云同步根。
- PostgreSQL 多实例属于 M5，不在 ADR-0008 v1 范围；届时另立/修订 ADR-0003 与迁移/备份方案。

### 4. 验证

- 导出/导入包含 `external_source`/`import_batch`/`external_record`/`agent_call`，不含通知/任务/凭据；
- `agent_call.event_id` 唯一，重复导入 JSONL 不重复；
- MCP 只读进程无 `audit_log` 写入、无任务、无通知；
- `MIN_SCHEMA_VERSION` 与 `MigrationTest` 同步。
