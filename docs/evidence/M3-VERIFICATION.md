# M3 验证证据（实施回收与知识闭环）

- **日期**：2026-11-13（Asia/Shanghai）
- **范围**：M3 切片 1–6（运行边界加固 + Git 基线/交接/实施回收/偏差/验收矩阵/交付快照/知识闭环）
- **规格**：`docs/specs/M3-PHASE3-PLAN.md` §4
- **结论**：**M3 全部切片通过**（后端 79 tests；前端 build + 25 tests；浏览器 E2E 3 passed；API 演示 25/25；S1 手动集成 5/5）

## 1. S1 自动化结果（真实执行，2026-11-13）

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
## 7. S2–S6 验证（2026-11-13）

### 7.1 自动化

| 检查 | 命令 | 结果 |
|---|---|---|
| 后端 | mvn -B -ntp -f services/domain/pom.xml verify | **BUILD SUCCESS，79 tests**（S1 71 + GitAdapter 3 + 基线 2 + 交付闭环 3） |
| 迁移 | MigrationTest | V1–V5 幂等，32 张业务表（+9 表与 1 列） |
| 前端 | cd apps/web; npm run build; npm test | build 通过；**25 tests** |
| 浏览器 E2E | 空数据根 + 
pm run test:e2e | **3 passed（12.8s）**：M1 4.6s / M2 3.7s / M3 3.9s |
| API 演示 | 
ode scripts/demo-m3.mjs http://127.0.0.1:18150 | **25/25** |

### 7.2 M3 E2E 路径（pps/web/e2e/m1-ui.spec.ts 第 3 个测试）

注册仓库（真实临时 Git 仓库）→ 捕获基线 → 生成交接包（含基线/任务/只读约束）→ 新增实施 commit → 记录实施并勾选完成任务 → 验收矩阵显示"已覆盖 1" → 生成 As-built 草稿 → 切 reviewer 四眼批准快照 → 显示"已批准"。

### 7.3 API 演示覆盖（25 步）

仓库注册（远程 URL 脱敏）→ 基线捕获（commit/branch）→ 需求/就绪/方案四眼批准 → 交接包 → 实施记录（from→to、变更、diverged=false）→ 验收矩阵 covered → 偏差（额外实现）→ As-built 草稿 → 作者自批 403 → reviewer 批准（Artifact）→ 知识提议 → 提议者自接受 403 → reviewer 接受生成知识草稿 → 新基线后知识 stale=true 且状态仍 draft → 审计含仓库/基线/实施/交付/知识动作 → 登出 401。

### 7.4 验收标准映射（M3）

| 项 | 证据 |
|---|---|
| Approved Design 不被覆盖 | SolutionWorkflowTest（M2）保持通过；交付/知识只读引用已批准版本；DeliveryWorkflowTest 批准后快照不可再批准（409） |
| Actual/As-built 差异可追溯 | DeliveryWorkflowTest.executionRecordDrivesAcceptanceMatrixAndDeviations（变更清单、偏差、矩阵） |
| 知识更新有来源与审核 | DeliveryWorkflowTest.deliverySnapshotAndKnowledgeProposalCompleteTheLoop（Artifact 来源、草稿状态、四眼接受） |
| 知识过期提示 | KnowledgeStalenessService + 列表/详情/检索 stale 字段；演示验证 |
| Git 只读/基线不可变/脏树拒绝 | LocalGitAdapterTest（3）、BaselineWorkflowTest（2） |
| 权限/审计 | 各服务角色校验；演示审计动作；既有 PermissionIsolationTest 不回归 |

### 7.5 环境说明

- 本机受限沙箱下 Playwright worker 管道受限的问题已通过一次性提权解决（S1 记录）；本批 E2E 在 danger-full-access 会话下运行，CI Linux Runner 不受影响。
- 演示脚本会创建/删除临时 Git 仓库（os.tmpdir()），不接触真实客户仓库。
## 8. ADR-0008 补齐验证（2026-11-13）

### 8.1 自动化

| 检查 | 命令 | 结果 |
|---|---|---|
| 后端全量 | mvn -B -ntp -f services/domain/pom.xml verify | **BUILD SUCCESS，83 tests**（M3 79 + AdminPackageTest 4） |
| 导出/导入测试 | AdminPackageTest | 往返保数据、密码锁定、非空拒绝、篡改包 checksum 拒绝 |
| 备份/恢复测试 | AdminPackageTest | VACUUM INTO 备份、manifest 校验、恢复后 DB/Artifact 可用、篡改备份拒绝、非空目标拒绝 |

### 8.2 CLI 演练（scripts/admin-drill.mjs，9/9）

1. 启动源数据根 → 种子（用户/项目/Artifact/已验证知识）→ 停止；
2. --admin-export → 20 行导出包；
3. --admin-import --admin-username=drill-owner → 新数据根，20 行；
4. 启动导入后的服务 → 项目/已验证知识/Artifact 哈希一致，审计含 dmin.import；
5. --admin-backup → 备份目录（含 db + artifacts + manifest）；
6. --admin-restore --restore-to=<空目录> → 恢复；
7. 启动恢复后的服务 → 数据可用；
8. 对非空数据根再次导入 → 退出码非 0 且提示 not empty。

### 8.3 限制

- 导出不加密；除指定管理员外账号在导入后锁定（无密码重置功能，需管理员重建）。
- 备份/恢复要求写实例停止；恢复目标必须为空目录。
- manifest 为 properties 格式（与 ADR 的 json 字段等价）；实例锁清理脚本未自动化。
## 9. M3.5 补强验证（2026-11-13）

| 检查 | 命令 | 结果 |
|---|---|---|
| 后端全量 | mvn -B -ntp -f services/domain/pom.xml verify | **BUILD SUCCESS，89 tests**（+2 knowledge supersede/deprecate、+4 lock cleanup） |
| 迁移 | MigrationTest | V1–V6 幂等，目标版本 6 |
| 前端 | cd apps/web; npm run build; npm test | build 通过；25 tests |
| 脱敏项目验收演练 | 
ode scripts/m3-acceptance-drill.mjs | **18/18**：批准方案→交接→实施回收→矩阵→As-built→知识发布→supersede→检索只返回新知识→审计→登出 |
| 实例锁清理 | CLI + PS 脚本（真实进程） | 写实例运行中 **拒绝**（含 pid/host）；停止后删除成功；无锁时幂等 |
| E2E | 空数据根 + 
pm run test:e2e | 3 passed（M1/M2/M3，UI 仅增加知识状态操作面板，回归通过） |

**限制**：演练项目为脱敏/合成数据（未接触真实客户数据）；"真实客户项目"验收仍需负责人提供授权脱敏项目。剩余技术债见 docs/specs/M3.5-BACKLOG.md。