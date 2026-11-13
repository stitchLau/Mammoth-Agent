# M3 执行时间线

- **日期**：2026-11-13（Asia/Shanghai）；时间为本地粗粒度记录

## 1. 提交

| 提交 | 内容 |
|---|---|
| `7632cf3` | `feat(m3)`：数据根锁 / 根校验 / 只读 MCP（S1） |
| `86abb59` | `docs(m3)`：S1 规格/证据/时间线/缺陷/交接 |

## 2. 切片 1（S1）过程

| 阶段 | 内容 | 结果 |
|---|---|---|
| 规格 | `docs/specs/M3-PHASE3-PLAN.md`：阶段目标、非目标、切片顺序、S1 详细规格与 AC | 先文档后代码 |
| 实现 | `DataRootResolver`（UNC/云同步/远端类型拒绝 + 覆盖）、`DataRootBootstrap`（只读校验）、`DataRootInstanceLock`（FileChannel 独占锁 + holder 元数据）、`DataSourceConfig`（只读数据源 + schema 门槛 + 条件 Flyway）、`McpServerMain`（强制只读）、`OrganizationService`（可选 Flyway 依赖 + 只读不写） | — |
| 首次测试 | 71 tests：`DataRootInstanceLockTest` 因 Windows 强制锁无法读取锁文件失败；`ReadOnlyModeTest` 因 `@DependsOn("flyway")` 缺失 + 默认组织写入失败；`DataRootBootstrapTest` 断言消息不符；`McpServerMainTest` Mockito 自附加被沙箱拒绝 | 见 `docs/quality/M3-BUGS.md` |
| 修正 | 锁元数据改旁路 holder 文件；`OrganizationService` 用 `ObjectProvider<Flyway>` 并只读时校验默认组织存在；修正测试断言；协议测试改为传 `null` 而非 Mockito mock | 71 tests 全通过 |
| 手动验证 | 双写实例拒绝、UNC/云同步根拒绝、MCP 只读与写实例并存、未初始化根失败 | 5/5 通过（`docs/evidence/M3-VERIFICATION.md` §2） |
| 回归 | M1/M2 E2E（2 tests） | 2 passed（需一次性 `danger-full-access`，见证据 §4） |

## 3. 关键判断

- 只读进程**不取写锁**：ADR-0008 允许写实例与只读 MCP 并存；实测只读连接在写连接打开时可读、写入被拒。
- `application.yml` 中的 `data-root.read-only=false` 会覆盖 `SpringApplicationBuilder.properties()` 默认值 → MCP 改用 **System property** 强制只读，并在启动参数中拒绝 `=false`。
- 只读数据源增加 `PRAGMA query_only=ON`（每连接包装），即使打开模式异常也无法写。

## 4. 下一步

- S2：仓库注册 + 基线捕获 + 只读 `GitAdapter`（ADR-0007，`V5` 迁移）；
- 进入 S2 前需确认：Git 只读命令白名单的 Windows/中文路径测试夹具、脏树拒绝与 owner 覆盖的 API 形态、`project_baseline` 是否绑定 `solution_version.project_baseline_id`。
## 5. 切片 2–6（2026-11-13）

| 阶段 | 内容 | 结果 |
|---|---|---|
| 规格 | docs/specs/M3-PHASE3-PLAN.md §9：V5 数据模型、GitAdapter 只读边界、交接/实施/矩阵/交付/知识闭环设计 | 先文档后代码 |
| S2 | git/LocalGitAdapter（白名单、超时、输出上限、中文路径、safe.directory、URL 脱敏）、aseline/*（仓库注册、不可变基线、脏树拒绝/owner 覆盖） | LocalGitAdapterTest 3 + BaselineWorkflowTest 2 |
| S3 | DeliveryService.handoff（批准方案渲染 + 任务 + 验收 + 基线；无批准方案 409） | demo handoff-generated |
| S4 | execution/*（from/to、变更清单、diverged、任务完成）、DeliveryController | DeliveryWorkflowTest 3 |
| S5 | 验收矩阵（covered/partial/unverified + 未完成任务候选）与人工偏差分类 | 同上 |
| S6 | As-built 快照（批准时落 Artifact、四眼、不可覆盖）、知识提议（接受生成知识草稿）、KnowledgeStalenessService | 同上；E2E 第 3 场景 |
| 前端 | 代码基线页、交付闭环页、知识"可能过期"标记、路由/导航 | build + 25 tests |
| 回归 | mvn verify 79 tests；E2E 3 passed；demo-m3.mjs 25/25 | 全绿 |

**过程中修正**：测试仓库误建在数据根内被服务正确拒绝（改为独立临时仓库根）；E2E 基线 POST 因立即导航被中止（等待响应 + 唯一文案断言）；实施 commit 文本在表格与下拉框重复导致严格模式冲突（.first()）。详见 docs/quality/M3-BUGS.md。