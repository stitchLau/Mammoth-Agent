# M3 Phase 3 计划 — 实施回收与知识闭环

- **状态**：In Progress（2026-11-13；切片 1 实施中，其余切片待负责人按序确认）
- **前置**：M2 完成（`598b49f`）；**ADR-0007/0008 Accepted v1.0（2026-11-13，`d91cb2e`）**
- **对应路线图**：`docs/12-ROADMAP.md` M3
- **依据**：`docs/04-MVP-STRATEGY.md` Phase 3、`docs/07-WORK-ITEM-LIFECYCLE.md` §7–8、`docs/03-DOMAIN-MODEL.md`、`docs/08-SECURITY-AND-TRUST.md`、`decisions/ADR-0007`、`ADR-0008`、`ADR-0010`

## 1. 阶段目标与验收

由已批准方案出发，产出**开发交接包**，回收**实际实施记录**（Git 变更），做**方案偏差分析**与**验收矩阵**，形成 **As-built/交付快照**，并生成**知识更新提议**（人工发布）。M3 退出条件：至少一个需求从批准方案到真实实施结果和知识更新闭环；Approved Design 不被覆盖，Actual/As-built 差异可追溯。

## 2. 非目标

- Jira/CSM/CI/Webhook 集成（M4）；
- Mammoth 托管 Git（clone/fetch/push/checkout）——ADR-0007 明确禁止；
- 代码内容进入知识库/向量索引；
- 多 PC 实时协作、服务端权威存储（M4/后续）；
- 文档解析管线（ADR-0009 仍 OPEN）；
- 生产化（TLS/共享会话/PG 方言）。

## 3. 切片划分与顺序

| 片 | 内容 | 主要验收 | 状态 |
|---|---|---|---|
| **S1** | **运行边界加固（ADR-0008）**：数据根实例锁、网络盘/云同步根拒绝、MCP 只读连接（不跑 Flyway、不写库） | 双写实例被拒；UNC/云同步根被拒（可覆盖）；只读进程可读、写入被拒、无 Flyway；M2 全量测试不回归 | **已完成（2026-11-13）** |
| S2 | 仓库注册 + 基线捕获 + 只读 `GitAdapter`（ADR-0007，`V5`） | 只读命令白名单；基线不可变；脏树默认拒绝；多仓库/多分支；中文路径与超时 | 待启动 |
| S3 | 开发交接包（Approved Design 快照 + 任务 + 验收 + 基线） | 交接包可导出、可校验、不含凭据；批准版本不被覆盖 | 待启动 |
| S4 | `ExecutionRecord`：from/to commit、变更清单、diverged、与设计项/任务映射 | 变更可追溯到基线与方案；`from` 非祖先时标记 diverged | 待启动 |
| S5 | 偏差分析 + 验收矩阵 | 符合/偏离/额外/未实现可分类；每条验收有任务/证据 | 待启动 |
| S6 | As-built/交付快照 + 知识更新提议与人工发布 + 过期提示 | 知识更新有来源与审核；旧方案/旧知识保留 | 待启动 |

## 4. S1 详细规格

### 4.1 数据根实例锁

- 写实例（Web/管理入口）启动时获取 `<dataRoot>/.mammoth-instance.lock` 的**独占文件锁**（`FileChannel.tryLock`），进程存活期间持有；`@PreDestroy` 释放。
- 锁文件只承载独占锁；持有者元数据（`pid=`、`host=`、`startedAt=`、`role=writer`）写入旁路文件 `.mammoth-instance-holder`，供被阻塞进程读取（Windows 强制锁下被锁文件不可读）。
- 已有活动实例时**拒绝启动**，错误信息包含现有持有者元数据与锁路径，并提示"若进程已崩溃，删除锁文件后重试"。
- 进程崩溃时 OS 自动释放锁；残留锁文件不阻塞下次启动（获取后可覆盖）。
- 只读实例不获取锁、不创建锁文件。

### 4.2 数据根位置校验

- 拒绝 **UNC 路径**（`\\server\share\...` 或 `//server/share`）。
- 拒绝 **已知云同步目录**（路径片段精确匹配：OneDrive/Dropbox/Google Drive/iCloud Drive/Box Sync 等）。
- 拒绝 **已知网络文件系统类型**（`FileStore.type()` ∈ SMB/CIFS/NFS/NFS4/AFPFS/WebDAV/FUSE-SSHFS）；无法判定的类型不拒绝（避免误伤 tmpfs/overlay）。
- 覆盖开关：`mammoth.data-root.allow-unsupported` / `MAMMOTH_ALLOW_UNSUPPORTED_ROOT=true`，仅限测试/受控迁移，启动日志 WARN。
- 保留既有规则：拒绝 `~/.dsh`、源码 checkout。

### 4.3 MCP 只读连接

- MCP 入口以 `mammoth.data-root.read-only=true` 启动：不获取实例锁、不创建目录/写探测、**不执行 Flyway**、以只读模式打开 SQLite（`SQLiteConfig.setReadOnly(true)` + `PRAGMA query_only`）。
- 数据根必须已由 Web/管理入口初始化；否则启动失败并提示"先启动 Web/管理入口完成迁移"。
- 只读模式要求 schema ≥ 4（当前最新迁移），版本不足时给出可操作错误。
- 只读属性作为兜底：任何误写都会以 SQL 错误失败，而不是静默写库。

### 4.4 验收（自动化）

| ID | Given/When/Then |
|---|---|
| S1-AC1 | 同一数据根已有写实例持锁 → 第二个写实例启动失败，错误含 pid/host 与锁路径；释放后可重新获取 |
| S1-AC2 | 只读实例启动 → 不创建锁文件、不执行迁移；连接 `PRAGMA query_only=1`；INSERT 被拒 |
| S1-AC3 | 数据根为空 → 只读实例启动失败，提示先用 Web/管理入口初始化 |
| S1-AC4 | 数据根为 UNC / 云同步目录 → 拒绝启动；`allow-unsupported=true` 时放行并记录 WARN |
| S1-AC5 | 既有 M1/M2 测试全通过（62 tests 基线不回归） |

## 5. 测试策略（S1）

- Unit：路径校验（UNC/云同步/覆盖开关）、实例锁（双获取、释放重试、只读不建文件）、只读 bootstrap（空根失败）。
- Integration：只读 Spring 上下文（无 Flyway bean、只读连接、写入被拒）；writer 模式迁移保持（既有 `MigrationTest`）。
- Security：只读连接写拒绝；锁文件不含敏感信息。
- Regression：M1/M2 全量。

## 6. 风险与缓解

| 风险 | 缓解 |
|---|---|
| Windows 映射网络盘可能报告 NTFS，JVM 无法可靠识别 | UNC/云同步/已知远端类型三层检测 + 覆盖开关 + 文档说明；不宣称 100% 覆盖 |
| 只读模式与 JPA/Hibernate 的写假设冲突 | 只读连接 + 不跑迁移 + 测试断言写入被拒；只读服务不写 |
| 实例锁在异常退出后的清理 | OS 文件锁自动释放；锁文件残留可覆盖；错误信息给人工清理指引 |
| 现有测试大量新建数据根 | 每测试独立临时根，锁在上下文关闭时释放；不共享 |
| MCP 与 Web 同时启动 | 只读进程不取锁；Web 持锁；两者可并存（只读连接实测可读） |

## 7. S1 DoD

- 代码 + 单测/集成测试通过；`mvn verify` 全绿且 ≥62 tests；
- `docs/dev-setup.md`、`docs/evidence/M3-VERIFICATION.md`、`docs/process/M3-TIMELINE.md`、交接更新；
- ADR-0008 进度记录 S1 落地证据；
- 提交并推送；无已知回归。

## 8. S1 实施记录（2026-11-13）

| 项 | 结果 |
|---|---|
| 后端 | `mvn -B -ntp -f services/domain/pom.xml verify` → **BUILD SUCCESS，71 tests**（M2 基线 62 + 新增 9） |
| 新增测试 | `DataRootResolverTest` +4、`DataRootBootstrapTest` +2、`DataRootInstanceLockTest` 2、`ReadOnlyModeTest` 1 |
| 手动集成 | 双写实例拒绝（含 holder 元数据）、UNC 拒绝、云同步目录拒绝、MCP 只读与写实例并存（3 工具、查询 `[]`、无迁移）、未初始化根失败（5/5） |
| 回归 | Playwright E2E 2 passed（需一次性 `danger-full-access`，沙箱限制见证据 §4） |

**实现位置**：`config/DataRootResolver`（根路径校验）、`config/DataRootBootstrap`（只读校验）、`config/DataRootInstanceLock`（独占锁 + holder）、`config/DataSourceConfig`（只读数据源 + schema 门槛 + 条件 Flyway + `query_only` 包装）、`mcp/McpServerMain`（强制只读）、`service/OrganizationService`（`ObjectProvider<Flyway>` + 只读不写）。

**与计划的偏差**：

1. 持有者元数据从"锁文件内"改为旁路 `.mammoth-instance-holder`（Windows 强制锁使被锁文件不可读，见 `docs/quality/M3-BUGS.md` B-M3-001）；
2. 只读模式的强制手段使用 System property + 参数拒绝（`application.yml` 的默认值会覆盖 `SpringApplicationBuilder.properties()`，见 B-M3-003）；
3. 只读连接的可写拦截以 `PRAGMA query_only=ON` 断言（不依赖 JDBC `isReadOnly()` 的驱动实现）。

**未完成（后续切片）**：导出/导入包、实例锁清理脚本、备份/恢复演练脚本、Git 基线（S2）。
