# Session Handoff — 2026-11-13（M3 切片 1 完成）

- **时间**：2026-11-13（Asia/Shanghai）
- **当前分支 / Commit**：`main`，含 M3 S1 提交（`git log -1` 为准；前一提交 `d91cb2e` = ADR-0007/0008）
- **当前任务**：M3（实施回收与知识闭环）切片 1 已完成；下一片 **S2：仓库注册 + 基线捕获 + 只读 GitAdapter**
- **会话目的**：让全新会话在本机沙箱环境下独立恢复 M3 工作

## 已确认事实

- 后端 `mvn verify` → **71 tests**（M2 基线 62 + S1 新增 9）；前端未改动（沿用 build + 25 tests）；Playwright E2E **2 passed**（9.2s）。
- S1 能力：写实例数据根独占锁（第二写实例失败并显示 holder）；UNC / 云同步目录 / 已知远端文件系统拒绝（可显式覆盖）；MCP 强制只读（System property、不取锁、不建目录、不跑 Flyway、`query_only` 只读连接），未初始化数据根 fail fast。
- 手动集成 5/5：双写拒绝、UNC 拒绝、OneDrive 拒绝、MCP 只读与写实例并存（3 工具、查询 `[]`、无迁移行）、未初始化根失败。见 `docs/evidence/M3-VERIFICATION.md`。

## 已做变更

- 新增：`config/DataRootInstanceLock`（`.mammoth-instance.lock` + `.mammoth-instance-holder`）、`DataSourceConfig.ReadOnlySqliteDataSource`、`ReadOnlyModeTest`、`DataRootInstanceLockTest`、`DataRootResolverTest` 扩展、`DataRootBootstrapTest` 扩展。
- 修改：`DataRootResolver`（UNC/云同步/远端 FS 类型 + `allow-unsupported`）、`DataRootBootstrap`（只读校验）、`DataSourceConfig`（只读数据源 + schema≥4 门槛 + 条件 Flyway）、`McpServerMain`（强制只读）、`OrganizationService`（`ObjectProvider<Flyway>` + 只读不写）、`application.yml`、`.env.example`、`McpServerMainTest`（去 Mockito）。
- 文档：`docs/specs/M3-PHASE3-PLAN.md`、`docs/evidence/M3-VERIFICATION.md`、`docs/process/M3-TIMELINE.md`、`docs/quality/M3-BUGS.md`、`docs/dev-setup.md`、`decisions/ADR-0008-WORKSPACE.md`（S1 记录）、`docs/13-OPEN-DECISIONS.md`、`docs/12-ROADMAP.md`、`docs/README.md`、`CHANGELOG.md`、本交接。

## 运行过的命令和结果

```powershell
mvn -B -ntp -f services/domain/pom.xml verify        # BUILD SUCCESS, 71 tests
cd apps/web; npm run test:e2e                        # 2 passed (9.2s) — 需 danger-full-access
```

手动：同数据根第二写实例 → exit 1 + holder 元数据；UNC/OneDrive 根 → 拒绝；MCP 只读 → 3 工具、`[]`、无迁移；空根 MCP → exit 1。

## 当前 Git 状态

- 工作树应干净；S1 代码与文档在同一/相邻提交（以 `git log` 为准）。
- 分支 `main`；推送后与 origin 同步。

## 未解决问题

- **S2 未开始**：仓库注册/基线捕获/`GitAdapter`；`V5` 迁移尚未建。
- Windows **映射网络盘**可能报告 NTFS，JVM 无法可靠识别（已记录，依赖运维规范）。
- 实例锁清理脚本、导出/导入包、备份/恢复演练脚本未实现（M3 后续）。
- E2E 在本机 `workspace-write` 沙箱下会因 Playwright worker 管道 `EPERM` 失败；需普通终端、CI 或一次性提权。
- CI 远端仍未运行。

## 尚未验证的假设

- 只读 MCP 长期运行（> 小时级）与写实例并发、WAL checkpoint 的行为未做长时压测；
- 多项目/大仓库下 `GitAdapter` 超时与输出上限（S2 验证）；
- 用户接受"数据根必须本地固定盘"的运维约束（暂按 ADR-0008 执行）。

## 下一步最小行动（S2）

1. 读 `decisions/ADR-0007-GIT-BASELINE.md`、`docs/specs/M3-PHASE3-PLAN.md` §3、`docs/03` ProjectBaseline；
2. 写 S2 规格：`project_repository`/`project_baseline`/`project_baseline_repo`（`V5`）、`GitAdapter` 白名单命令、基线捕获 API/UI、脏树与多仓库规则；
3. 先建合成 Git 仓库夹具（临时目录或 `fixtures/git/`），覆盖中文路径、detached HEAD、from 非祖先、超时与输出上限；
4. 实现后用 `mvn verify` + demo/手工脚本验证，再更新 evidence/timeline/bugs/handoff。

## 新会话必须先阅读

- `AGENTS.md`、`docs/00-MASTER-GUIDE.md`、`docs/README.md`、本交接；
- `decisions/ADR-0007-GIT-BASELINE.md`、`decisions/ADR-0008-WORKSPACE.md`（含 S1 落地记录）；
- `docs/specs/M3-PHASE3-PLAN.md`、`docs/evidence/M3-VERIFICATION.md`、`docs/quality/M3-BUGS.md`；
- 代码：`services/domain/src/main/java/com/mammoth/domain/config/`（Resolver/Bootstrap/InstanceLock/DataSourceConfig）、`mcp/McpServerMain`。

## 风险提醒

- 不要在受限沙箱直接跑 Playwright（EPERM）；不要以 `danger-full-access` 为默认。
- 不要绕过 `GitAdapter` 直接用 `ProcessBuilder` 调任意 git 命令（ADR-0007 白名单）。
- 只读 MCP 不会创建数据根；首次使用必须先启动 Web 入口。
- 修改数据根相关逻辑时同步更新 `dev-setup.md` 与 ADR-0008 记录；改动迁移后同步 bump `DataSourceConfig.MIN_SCHEMA_VERSION`。
