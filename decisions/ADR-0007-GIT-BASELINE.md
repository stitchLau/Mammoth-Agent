# ADR-0007: Git 与知识同步（代码基线与变更映射）

- 状态：**Accepted v1.0**（2026-11-13 负责人指示"先定 ADR-0007/0008"，按 Agent 建议定案）
- 日期：2026-11-13
- 决策人：项目负责人（2026-11-13 指示定案；决策内容由 Lead Agent 按建议执行并记录）
- 相关需求/功能：M3 实施回收与知识闭环（开发交接包、实施记录、Git 差异导入、方案偏差、As-built）
- 相关文档：`docs/03-DOMAIN-MODEL.md`（ProjectBaseline/ExecutionRecord/DeliverySnapshot）、`docs/07-WORK-ITEM-LIFECYCLE.md` §7–8、`docs/04-MVP-STRATEGY.md` Phase 3、`decisions/ADR-0001`（DSH 边界）、`ADR-0003`（库）、`ADR-0004`（Artifact）、`ADR-0010`（需求上下文基线）

## 背景与约束

- `03` 要求 `ProjectBaseline` 关联部署版本、**Git Commit**、DB 结构版本；`ExecutionRecord` 记录 Commit、改动摘要、测试与偏差；`DeliverySnapshot` 表达最终交付事实。
- `07` §7–8 要求交接包可携带、结项收集实际 Commit 并对照 Approved Design 识别偏差。M3 的退出条件是"Approved Design 不被覆盖；Actual/As-built 与其差异可追溯"。
- **现状（M2 @ `598b49f`）**：代码中没有任何 Git 调用（`services/domain` 无 `git`/`ProcessBuilder` 相关实现），项目、方案、知识均未关联 commit；`ProjectBaseline` 尚未建表。
- 单机本地优先（ADR-0002/0003/0008）；SQLite 主库；DSH 仅作开发工具/Agent Runtime（ADR-0001）。
- **DSH Worktree 不可作为基线来源**：`docs/spikes/S1-DSH-EXTENSION-SPIKE.md` F6 实测——`create_worktree` 可用，但 rc.2 起**归档会话默认清理工作树**（`retainOnArchive` 默认关），未提交改动会被丢弃。
- 安全红线：不接触生产凭据；不得读取/复制凭据文件；业务权威状态不得存 DSH storage（ADR-0001 §6.3）。

**环境证据（2026-11-13，本机只读探针，证据可复现）**：

| 探针 | 结果 |
|---|---|
| `git --version` | `2.50.1.windows.1` |
| `git rev-parse --show-toplevel` / `HEAD` / `--abbrev-ref HEAD` | 仓库根、`598b49f…`、`main` |
| `git status --porcelain=v2 --branch` | 输出 `# branch.oid`、`# branch.head`、`# branch.upstream`，porcelain 格式稳定可解析 |
| `git log -3 --format='%H|%h|%ci|%s'` | 3 行，含完整 SHA、短 SHA、ISO 时间、标题 |
| `git diff --name-status HEAD~2..HEAD` | 26 条（17 `M` + 9 `A`），可作变更映射 |
| `git diff --stat HEAD~2..HEAD` | `26 files changed, 2741 insertions(+), 25 deletions(-)` |
| `git worktree list --porcelain` | 主工作树 1 条，含 `worktree`/`HEAD`/`branch` |
| `git remote get-url origin` | `hasUserInfo=false`，可解析出 host/path |
| `git -c safe.directory=<绝对路径> rev-parse --short HEAD` | 成功（精确路径覆盖生效） |
| 探针后 `git status --porcelain` | **0 行**（只读命令未改动工作树） |

## 需要解决的问题

1. "代码基线"是什么、由谁触发、保存什么；
2. 多仓库、多分支如何表达；
3. Mammoth 是否执行 Git 写操作或网络操作；
4. 实施变更如何映射到基线与方案（粒度、内容边界）；
5. "知识同步"如何表达（知识相对代码基线是否过期）；
6. Worktree 与 DSH 的关系；
7. 凭据、路径与日志安全。

## 备选方案

- **方案 A（推荐）**：本地只读 Git + 人工捕获基线。Mammoth 只读取已注册本地仓库的元数据（commit/branch/status/diff 文件清单），**不 clone/fetch/push/checkout**，不写工作树；基线由项目负责人显式捕获并不可变保存。
- **方案 B**：Mammoth 托管 Git（clone/fetch/worktree/分支切换，甚至 push）。能力强，但引入网络、凭据、冲突与误操作风险，且与"业务状态独立于仓库"的原则相反。
- **方案 C**：不集成 Git，仅允许人工在需求/方案/实施记录里粘贴 commit 文本。实现成本最低，但无法校验、无法自动映射变更、无法回答"批准时基于哪个 commit"。
- 方案 D（未来）：托管 Git 服务 API / Webhook / CI 集成。属 M4 企业集成，不在本轮。

## 决策

采纳 **方案 A**，并明确以下 12 条：

1. **Git 只作为代码基线与变更证据来源**，不作为知识/业务状态的存储；知识权威仍在 Mammoth 关系库与 Artifact（ADR-0003/0004）。
2. **只读**：不执行 `clone`/`fetch`/`pull`/`push`/`checkout`/`commit`/`reset`/`clean`/`gc`/`prune`，不切换分支，不写工作树、不改 `.git`。所有命令限定白名单：`rev-parse`、`status --porcelain=v2 --branch`、`log --format`、`diff --name-status`、`diff --stat`、`worktree list --porcelain`、`remote get-url`（随后脱敏）。
3. **仓库注册由人工完成**：`project_repository(id, project_id, label, absolute_path, remote_url_sanitized, default_branch, status, added_by, added_at)`。路径必须为绝对路径、存在、且包含 `.git`（目录或 worktree 的 `.git` 文件）；不扫描磁盘自动发现；拒绝数据根与 DSH Home 内的路径。
4. **基线捕获是显式人工动作**：`project_baseline(id, project_id, note, captured_by, captured_at)` + `project_baseline_repo(baseline_id, repository_id, commit_sha, branch, dirty, worktree, captured_at)`。基线**不可修改**，只能新建；"当前基线"= 最近一次捕获。
5. **多仓库**：一个项目可注册 N 个仓库，基线是 (repo → commit) 的集合；允许部分捕获（`complete=false` 标记），但交付/结项流程要求全部仓库有基线。
6. **多分支**：分支只是基线元数据，不自动跟踪远程；基线可来自任意分支的任意 commit（含 detached HEAD，记录 SHA 并标记 `detached=true`）。Mammoth 不 fetch，因此"远程是否有更新"只能由用户自行同步后重新捕获。
7. **脏工作树**：基线捕获默认要求 `status --porcelain` 为空；确需包含未提交变更时，仅项目 owner 可显式覆盖，记录 `dirty=true` 与 `reproducible=false`，并写审计。实施记录（ExecutionRecord）允许脏树，但同样记录标记。
8. **Worktree**：只识别、不创建/删除。注册时若路径是 worktree（`.git` 为文件）则记录 `worktree=true`；**DSH 临时 worktree 不得注册为项目仓库**（会被归档清理）。基线只能指向普通仓库中可达的 commit（可由 worktree 检出，但基线存 commit SHA）。
9. **变更映射（M3）**：`ExecutionRecord` 记录 `from_baseline_id`、`to_commit`、`changed_files(file_path, change_type A/M/D/R, old_path)`、`diff_stat`、`diverged`（from 不是 to 的祖先时标记）。默认只取**文件清单与统计**（`--name-status` / `--stat`）；diff 正文仅在人工需要时生成，作为 Artifact 存储（有大小上限、跳过二进制），不进入数据库正文与全文索引。
10. **代码内容不进知识库**：不把仓库源码复制进数据根/FTS/向量索引；知识文档仍可引用代码路径、符号与 commit（作为证据定位），代码事实以仓库为准（`00-MASTER-GUIDE` §6 文档权威第 6 条）。
11. **知识同步 = 过期检测，不是自动改写**：知识文档/方案可关联 `applicable_baseline_id`（M3 迁移新增可空列）。当项目"最新基线"与知识绑定基线不一致时，详情与检索结果显示"可能过期（基于 vN 基线）"；**不自动**修改 `knowledge_status`，必须由人走 `needs_review → verified`（`06` §7、四眼）。旧知识保留，不物理删除。
12. **安全**：不读取 `~/.git-credentials`、`.git/config` 中的凭据；`remote get-url` 结果在使用前脱敏（去 userinfo），检测到凭据则拒绝存储并告警；日志只输出脱敏 URL 与 commit；不执行网络命令；`safe.directory` 只对已注册的**精确绝对路径**按需设置，禁止 `*`。

## 决策理由

- 只读 + 人工基线满足 M3 的可追溯性，同时把风险（凭据、网络、误操作）降到最低；与产品原则"代码是已实现事实，不自动代表正确需求"一致。
- 实测 porcelain/format 输出稳定，解析不依赖本地化文本；只读命令不改变工作树（探针后 0 行变更）。
- DSH worktree 会被归档清理（S1 F6），若作为基线会破坏可复现性，因此必须排除。
- 不复制源码进知识库，避免"知识污染"（`06` §7）与客户源码扩散；变更映射只存路径与统计即可支撑偏差分析。
- 过期检测而非自动改写，符合"AI/系统不得自动把知识升级为正式事实"的治理原则（`00` §2 原则 4、`06` §7）。

## 取舍和影响

- **正向**：无凭据面；无网络依赖；离线可用；基线可复现；变更可追溯到文件级；知识过期可见。
- **负向**：自动化程度低（需人工注册仓库、捕获基线、刷新）；不 fetch 意味着远程新提交需用户自行同步；只读意味着 Mammoth 不能替用户修复分支/工作树；多仓库完整性依赖流程约束。
- **性能**：大仓库 `diff --stat` 可能慢；适配器必须设置超时（建议 10 s，diff 可配 30 s）与输出上限（建议 1 MiB，超限截断并标记）。
- **Windows**：需固定 UTF-8 解码与 `-c core.quotepath=false`，否则中文路径显示为转义；需在 M3 测试覆盖。
- **子模块**：v1 不递归；只记录父仓库 commit（已知限制，写入交接）。
- **合并提交/分叉**：两点 diff 仍可用，但标记 `diverged=true`；不做三方合并推断。

## 安全/数据/迁移影响

- 新增 3 张表（M3 `V5` 迁移）：`project_repository`、`project_baseline`、`project_baseline_repo`；`ExecutionRecord` 表在 M3 同批建立。均为元数据，无源码正文。
- `remote_url_sanitized` 可能含内网主机名/路径：导出/截图时按 Confidential 处理；禁止进入 RAG 样例与公开文档。
- 注册路径暴露本机目录结构：API 返回与导出均可脱敏（仅 owner 可见完整路径）。
- 不触碰 `~/.dsh`、凭据文件与生产仓库；开发/测试只用合成仓库（M3 建 `fixtures/git/` 合成仓库或临时目录）。
- 回滚：功能开关关闭 Git 集成即可，业务表不受影响；已捕获基线只增不改，回滚后保留。

## 验证计划和结果

**已执行（2026-11-13）**：本仓库只读探针（见上表 9 行）全部成功，工作树保持干净；`safe.directory` 精确路径可用；remote URL 可安全解析出 `hasUserInfo`。

**M3 实现时必须补齐的验证**：

1. 非仓库路径/无 `git` 环境 → 明确错误与提示，不崩溃；
2. 脏工作树默认拒绝、owner 覆盖后 `reproducible=false`；
3. `from` 非 `to` 祖先 → `diverged=true`；
4. 中文路径（`core.quotepath=false`）与 UTF-8 输出；
5. 超时与 1 MiB 输出上限（大 diff）；
6. submodule 行为（不递归）；
7. safe.directory 失败的提示与处置；
8. 知识过期标记：基线 v1 → 知识绑定 v1 → 捕获 v2 → 详情显示"可能过期"，状态不变；
9. 只读保证：执行后 `git status --porcelain` 为空、`.git` 未被修改（可用 mtime/内容哈希断言）。

## 回滚或替代路径

- 客户环境禁止 CLI Git → 实现 `GitAdapter` 的 JGit 版本（接口不变），只读能力等价。
- 未来需要托管 Git/CI/Webhook → 新 ADR（在 ADR-0007 之上扩展），保留只读适配器作为底层读取。
- 若人工基线流程成为瓶颈 → M3.5 可增加"只读刷新预览 + 人工确认"（仍不自动提交）。

## 后续行动

1. M3 规格：`GitAdapter` 接口、3 张表（`V5`）、仓库注册/基线捕获 REST 与 UI、合成仓库测试夹具；
2. `ExecutionRecord` 与偏差分析切片：from/to、changed files、diverged、与方案设计项/任务的映射；
3. 知识过期标记：`applicable_baseline_id` 可空列 + 详情/检索提示（不改状态）；
4. 与 ADR-0010 兼容：`solution_version` 增加可空 `project_baseline_id`（M3 迁移），不修改已批准版本语义；
5. 在 `docs/dev-setup.md` 记录"客户仓库路径配置 + 只读权限最小化"的运维要求。
