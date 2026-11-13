# M3 验证证据（实施回收与知识闭环）

- **日期**：2026-11-13（Asia/Shanghai）
- **范围**：M3 切片 1（运行边界加固，ADR-0008）：数据根实例锁、网络盘/云同步根拒绝、MCP 只读连接
- **规格**：`docs/specs/M3-PHASE3-PLAN.md` §4
- **结论**：**S1 通过**（后端 71 tests；浏览器 E2E 2 passed；手动集成检查 5 项通过）

## 1. 自动化结果（真实执行）

| 检查 | 命令 | 结果 |
|---|---|---|
| 后端构建 + 测试 + 打包 | `mvn -B -ntp -f services/domain/pom.xml verify` | **BUILD SUCCESS，71 tests**（M2 基线 62 + 本切片新增 9） |
| 新增测试分布 | `config` 包 | `DataRootResolverTest` 8（+4：UNC/云同步/覆盖/本地）、`DataRootBootstrapTest` 4（+2：只读空根/已有根不写）、`DataRootInstanceLockTest` 2、`ReadOnlyModeTest` 1 |
| 前端构建/单测 | 未改前端 | 沿用 M2 结果（build + 25 tests） |
| 浏览器 E2E | 空数据根 + `npm run test:e2e` | **2 passed（9.2s）**：M1 4.6s、M2 4.1s（沙箱说明见 §4） |

## 2. 手动集成检查（真实进程）

环境：Windows 10、JDK 17.0.15、jar `services/domain/target/mammoth-domain-service.jar`、临时数据根 `%TEMP%\mammoth-m3-manual-*`。

| # | 检查 | 结果 |
|---|---|---|
| 1 | **第二个写实例**（同一数据根，端口 18141） | 启动失败、exit 1，错误含 `another Mammoth instance is already using the data root ... (pid=13932;host=DESKTOP-9OP303T;startedAt=2026-11-13T00:57:51Z;role=writer)` 与锁路径 + 人工清理指引 |
| 2 | **UNC 数据根** `\\server\share\mammoth`（端口 18142） | 启动失败，`mammoth.data-root must be a local fixed disk, not a UNC path` |
| 3 | **云同步目录** `%USERPROFILE%\OneDrive\Mammoth\data`（端口 18143） | 启动失败，`must not be inside a cloud-synced directory (found 'OneDrive' ...)` |
| 4 | **MCP 只读进程**（与写实例**同一数据根、写实例持锁运行中**） | 启动成功；`tools/list` 返回 3 个工具；`search_project_knowledge` 返回 `[]`、`isError:false`；日志 `Mammoth data root ready (read-only)`、`SQLite datasource (read-only)`、`read-only instance: data root lock not acquired`；stderr **无** `Migrating schema`/`Flyway schema` 行 |
| 5 | **MCP 只读 + 未初始化数据根** | 启动失败、exit 1，`Mammoth data root is not initialized (...). Start the web/management entrypoint once ... then retry the read-only process.` |

手动检查后已停止进程；数据根位于会话临时目录，未进入仓库。

## 3. 验收标准映射（M3 S1）

| AC | 证据 |
|---|---|
| S1-AC1 双写实例被拒、释放后可重试 | `DataRootInstanceLockTest.secondWriterIsRejectedWithHolderMetadataAndReleaseAllowsRetry`；手动检查 1 |
| S1-AC2 只读实例不建锁、不迁移、不可写 | `ReadOnlyModeTest.startsReadOnlyWithoutFlywayAndRejectsWrites`（无 Flyway bean、`PRAGMA query_only=1`、INSERT 抛 SQLException）；手动检查 4 |
| S1-AC3 空根只读启动失败 | `DataRootBootstrapTest.readOnlyModeRejectsAnUninitializedRoot`；手动检查 5 |
| S1-AC4 UNC/云同步根拒绝、可覆盖 | `DataRootResolverTest.uncPathIsRejected` / `cloudSyncedDirectoryIsRejected` / `unsupportedOverrideAllowsNetworkStylePathForControlledTests`；手动检查 2/3 |
| S1-AC5 M1/M2 不回归 | 62 基线测试全通过（71 总数）；E2E 2 passed |

## 4. 环境限制与说明

- **E2E 在受限沙箱下无法直接运行**：DSH `workspace-write` 模式禁止 Node `child_process.fork` 的管道（EPERM），Playwright 无法创建 worker。本切片以一次性 `danger-full-access` 批准运行 E2E，结果 2 passed；CI（GitHub Runner）不受此限制。后续会话在本机受限沙箱中运行 E2E 需同样提权或改用普通终端。
- **MCP 只读前置**：数据根必须先由 Web/管理入口初始化（至少一次迁移）；只读入口不会创建目录、schema 或默认组织。
- **映射网络盘**：UNC 与已知远端文件系统类型可拒绝；但 Windows 映射盘可能向 JVM 报告 `NTFS`，无法 100% 识别（ADR-0008 已记录，依赖运维规范 + 覆盖开关）。
- **未覆盖**：崩溃后陈旧 holder 文件不自动清理（锁由 OS 释放，下次获取时覆盖 holder）；未做导出/导入、Git 基线（后续切片）。

## 5. 复现命令

```powershell
# 后端全量
mvn -B -ntp -f services/domain/pom.xml verify

# 手动：数据根 + 写实例
$root = Join-Path $env:TEMP "mammoth-m3-manual"
java -jar services/domain/target/mammoth-domain-service.jar --server.port=18140 --mammoth.data-root="$root"
# 另开终端：同一数据根再启一个写实例 → 应失败（another Mammoth instance）

# 手动：MCP 只读（写实例仍在运行）
$env:MAMMOTH_DATA_ROOT=$root
$env:MAMMOTH_MCP_AGENT_USER_ID='<项目成员用户 id>'
'{"jsonrpc":"2.0","id":1,"method":"initialize"}' | java -jar services/domain/target/mammoth-domain-service.jar --mcp
```
