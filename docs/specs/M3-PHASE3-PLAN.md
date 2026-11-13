# M3 Phase 3 计划 — 实施回收与知识闭环

- **状态**：**Completed（2026-11-13）**（S1–S6 全部完成；见 §10 实施记录）
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
| S2 | 仓库注册 + 基线捕获 + 只读 `GitAdapter`（ADR-0007，`V5`） | 只读命令白名单；基线不可变；脏树默认拒绝；多仓库/多分支；中文路径与超时 | **已完成（2026-11-13）** |
| S3 | 开发交接包（Approved Design 快照 + 任务 + 验收 + 基线） | 交接包可导出、可校验、不含凭据；批准版本不被覆盖 | **已完成（2026-11-13）** |
| S4 | `ExecutionRecord`：from/to commit、变更清单、diverged、与设计项/任务映射 | 变更可追溯到基线与方案；`from` 非祖先时标记 diverged | **已完成（2026-11-13）** |
| S5 | 偏差分析 + 验收矩阵 | 符合/偏离/额外/未实现可分类；每条验收有任务/证据 | **已完成（2026-11-13）** |
| S6 | As-built/交付快照 + 知识更新提议与人工发布 + 过期提示 | 知识更新有来源与审核；旧方案/旧知识保留 | **已完成（2026-11-13）** |

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

## 9. S2–S6 设计要点（2026-11-13，实施依据）

### 9.1 数据模型（`V5__m3_delivery.sql`）

| 表 | 关键列 | 说明 |
|---|---|---|
| `project_repository` | id, project_id, label, absolute_path, remote_url（脱敏）, default_branch, status, added_by, added_at | 项目代码仓库注册（ADR-0007），项目内路径唯一 |
| `project_baseline` | id, project_id, note, complete, captured_by, captured_at | 不可变基线；"当前基线"=最近一次 |
| `project_baseline_repo` | id, baseline_id, repository_id, commit_sha, branch, dirty, detached, worktree, captured_at | 每仓库的 commit 快照 |
| `execution_record` | id, project_id, work_item_id, solution_version_id, baseline_id, repository_id, from_commit, to_commit, diverged, summary, tests_note, recorded_by, recorded_at | 实施回收，一条记录对应一个仓库的一段 commit 范围 |
| `execution_change` | id, execution_record_id, file_path, change_type(A/M/D/R), old_path, additions, deletions | 文件级变更（不含源码正文） |
| `execution_task` | id, execution_record_id, task_id, status(done/partial/skipped), note | 任务完成情况（用于验收矩阵） |
| `execution_deviation` | id, execution_record_id, deviation_type(conform/extra/missing/changed), target_type(design_item/task/criterion), target_id, description, created_by, created_at | 人工偏差分类；"未完成候选"由系统按任务计算 |
| `delivery_snapshot` | id, project_id, work_item_id, solution_version_id, baseline_id, title, body_markdown, status(draft/approved), artifact_id, created_by, created_at, approved_by, approved_at | As-built 快照；批准后不可覆盖，markdown 落 Artifact |
| `knowledge_update_proposal` | id, project_id, work_item_id, delivery_snapshot_id, title, body_markdown, status(draft/accepted/rejected), knowledge_document_id, created_by, created_at, decided_by, decided_at | 知识更新提议；接受后生成知识草稿 |
| `knowledge_document.applicable_baseline_id` | 新增可空列 | 知识绑定的代码基线；与最新基线不一致时提示"可能过期"，不改状态 |

### 9.2 GitAdapter（只读）

- 接口：`inspect(path)`、`diff(path, from, to)`、`isAncestor(path, from, to)`。
- 实现 `LocalGitAdapter`（原生 git CLI）：只读白名单 `rev-parse`、`status --porcelain=v2 --branch`、`diff --name-status`、`diff --numstat`、`merge-base --is-ancestor`、`remote get-url`；**禁止** clone/fetch/pull/push/checkout/commit/reset/clean/gc/prune。
- 安全：`GIT_TERMINAL_PROMPT=0`、`--no-optional-locks`、`-c core.quotepath=false`、`-c core.fsmonitor=false`、对已注册绝对路径按需 `-c safe.directory=<path>`；10 s 超时、1 MiB 输出上限；commit 参数先正则校验并用 `--` 隔离；remote URL 去 userinfo。
- 错误语义：非仓库/路径不存在 → 400；git 缺失/超时 → 409/500 可操作错误。

### 9.3 交接包（S3）

`GET .../handoff` 生成（不落库）：项目/客户、需求、**批准方案**的业务/技术渲染（`SolutionRenderer`）、任务清单、验收标准、基线 commit 集合、上下文快照 ID；要求存在 `approved` 方案版本（否则 409）。输出 Markdown + 结构化 JSON；审计 `handoff.generate`。不含凭据、源码正文。

### 9.4 实施记录与偏差（S4/S5）

- `POST .../executions`：`{repositoryId, baselineId, toCommit, summary, testsNote, completedTaskIds[]}`；校验仓库属于项目、基线包含该仓库、`toCommit` 可解析；`from=baselineRepo.commit`；`diverged=!isAncestor(from,to)`；落 `execution_change`（`--name-status` + `--numstat` 合并）。
- `POST .../executions/{id}/deviations`：人工分类（符合/额外/未实现/变更），可关联设计项/任务/验收标准。
- `GET .../acceptance-matrix`：以**当前批准方案**的验收标准为行；覆盖状态 = 关联任务（直接或经设计项）中被标记 `done` 的比例 → `covered/partial/unverified`；同时返回"未完成任务候选"（系统计算）与偏差列表。

### 9.5 交付快照与知识闭环（S6）

- `POST .../deliveries`：基于批准方案 + 最新实施记录生成 As-built Markdown（draft）；`approve` 需 owner/reviewer 且非创建者（四眼），批准时把 Markdown 写入 Artifact 并固定 `artifact_id`；批准后不可编辑，只能新建。
- `POST .../knowledge-proposals`：只能基于**已批准**交付快照；`accept` 需 owner/reviewer 且非提议者，创建 `KnowledgeDocument`（draft，`docType=delivery`，`sourceRefs=[快照 Artifact]`，`applicableBaselineId=快照基线`），之后走既有四眼审核发布；`reject` 记录理由。
- 知识过期：列表/详情返回 `stale`（`applicableBaselineId != 最新基线 id`），UI 显示"可能过期"；不自动改状态。

### 9.6 权限

| 动作 | owner | editor | reviewer | viewer |
|---|---|---|---|---|
| 注册仓库 | ✓ | — | — | — |
| 捕获基线（含脏树覆盖） | ✓ | ✓（脏树覆盖仅 owner） | — | — |
| 生成交接包 | ✓ | ✓ | ✓ | ✓ |
| 记录实施/偏差 | ✓ | ✓ | — | — |
| 创建交付快照 | ✓ | ✓ | — | — |
| 批准交付快照 | ✓（≠作者） | — | ✓（≠作者） | — |
| 创建/接受知识提议 | 接受需 owner/reviewer（≠提议者）；创建 owner/editor | | | |
## 10. S2–S6 实施记录（2026-11-13）

| 项 | 结果 |
|---|---|
| 后端 | mvn verify → **BUILD SUCCESS，79 tests**（S1 基线 71 + 本批 8：GitAdapter 3、基线 2、交付闭环 3） |
| 迁移 | V5__m3_delivery.sql：9 张表 + knowledge_document.applicable_baseline_id；迁移测试 5 个版本幂等 |
| 前端 | build 通过、**25 tests**；新增代码基线页、交付闭环页、知识"可能过期"标记 |
| 浏览器 E2E | **3 passed（12.8s）**：M1 / M2 / M3（注册仓库→捕获基线→交接包→实施回收→验收矩阵→快照四眼批准） |
| API 演示 | 
ode scripts/demo-m3.mjs → **25/25**（含远程 URL 脱敏、快照/提议四眼、知识过期标记、审计） |

**实现位置**：git/LocalGitAdapter（只读白名单/超时/输出上限/中文路径/safe.directory）、aseline/*（仓库注册、不可变基线）、execution/*（实施记录、变更、任务完成、偏差、验收矩阵）、delivery/*（As-built、四眼批准、知识提议与接受）、knowledge/KnowledgeStalenessService、web/BaselineController、web/DeliveryController、前端 BaselinesPage/DeliveryPage。

**与计划的偏差**：

1. 多仓库基线为"一条基线包含多个仓库 commit"；实施记录按仓库逐条回收（一个基线可对应多条记录），验收矩阵按批准方案任务聚合多条记录。
2. 偏差分析采用"系统计算未完成任务候选 + 人工分类（符合/额外/未实现/变更）"，不尝试从 diff 自动推断语义偏差。
3. 重命名（R）在 --name-status 中保留 old_path；--numstat 对重命名可能显示 0/0（文件级统计为近似），正文不落库。
4. 交付快照批准时才写入 Artifact（内容寻址、只读），草稿不落字节；知识提议接受后生成**草稿**知识，仍需既有四眼审核发布。
5. 导出/导入包与实例锁清理脚本仍在 ADR-0008 后续范围，不属于本阶段退出条件。

**已知限制**：git 缺失/超时返回可操作错误；Windows 映射网络盘检测依赖路径/类型启发式；代码符号级映射未做（变更映射到文件级）。