# Session Handoff — 2026-11-12（M2 完成）

- **时间**：2026-11-12（Asia/Shanghai）
- **当前分支 / Commit**：`main` @ `e6f0cb6`（M2 代码与 S0 文档已提交；本交接与收尾文档随下一个 `docs(m2)` 提交）
- **当前任务**：M2（需求与方案）已完成并通过本地自动化验收；下一阶段 **M3（实施回收与知识闭环）**
- **会话目的**：让全新会话在无历史上下文时独立恢复工作

## 已确认事实

- 后端 **62 tests**（M1 48 + 需求 6 + 方案 7 + MCP 1）全通过；前端 build + **25 tests**；Playwright **2 passed**；API 演示 **26/26**（见 `docs/evidence/M2-VERIFICATION.md`）。
- M2 能力：需求工作项（`REQ-000n`）、证据只追加批次、11 维就绪检查与负责人确认门槛、上下文快照（ADR-0010）、统一 SolutionModel 双视图渲染、6 条一致性规则、任务/验收/追溯矩阵、四眼评审、批准版本不可覆盖与 `superseded`、MCP 只读 `get_requirement_context`。
- 决策：ADR-0007/0008/0009 保持 OPEN；新增 ADR-0010 Accepted；M2 不接应用内 LLM；不新增角色；状态机止于 `approved`（修订经 `new-version` 回到 `analyzing`）。见 `docs/specs/M2-PHASE2-PLAN.md` §8 与 `docs/decisions/DECISION-LOG.md` D-010–D-017。

## 已做变更

- **数据**：`V4__m2_requirements.sql`（9 表 + 2 索引）；`work_item`/`requirement_*`/`solution_*`/`acceptance_criterion`/`approval_record`。
- **后端**：`com.mammoth.domain.workitem`（状态机/维度目录/就绪引擎/快照）、`com.mammoth.domain.solution`（模型/一致性/渲染/版本）、`WorkItemController`、`SolutionController`、`McpToolService.getRequirementContext`、`JsonLite`。
- **前端**：`RequirementsPage`、`RequirementDetailPage`、`SolutionPage`；`api.ts`/`format.ts`/`permissions.ts` 扩展；路由 `/requirements`、`/requirements/:id`、`/requirements/:id/solutions/:versionId`；侧栏"需求与方案"。
- **测试**：`RequirementWorkflowTest`（6）、`SolutionWorkflowTest`（7）、`McpToolServiceTest` +1、`MigrationTest` V4、前端 9 个新单测、E2E M2 场景。
- **文档**：`docs/specs/M2-PHASE2-PLAN.md`、`decisions/ADR-0010-*`、`docs/evidence/M2-VERIFICATION.md`、`docs/process/M2-TIMELINE.md`、`docs/quality/M2-BUGS.md`、`docs/demos/M2-DEMO.md`、本交接、README/12/13/CHANGELOG。

## 运行过的命令和结果

```powershell
mvn -B -ntp -f services/domain/pom.xml verify        # BUILD SUCCESS, 62 tests
cd apps/web; npm run build; npm test                 # build ok, 25 tests
cd apps/web; npm run test:e2e                        # 2 passed (8.8s), 后端空数据根 18135
node scripts/demo-m2.mjs http://127.0.0.1:18132      # 26/26
```

## 当前 Git 状态

- 已提交：`742f69d`（S0 文档）、`7e4ea30`（后端）、`e6f0cb6`（前端）。
- 待提交（本交接所在提交）：`docs(m2)` 验证/过程/缺陷/演示/交接 + README/12/CHANGELOG + 规格 §13 实施记录。
- 分支 `main`；工作树应保持干净。

## 未解决问题

- **CI 远端未运行**：所有新测试仅本机验证。
- **真实 DSH 会话**：`get_requirement_context` 未在真实 DSH 会话复测（服务级已测；MCP 传输未改）。
- **M2 截图/视觉验收**：未生成截图，建议 M3 前补一次人工视觉验收。
- **Q-005 应用内 LLM/Agent 草案写入**：ADR-0011 未立，M2 明确不做。
- **ADR-0009 文档解析**：证据仍为原始资料 + 人工摘录。

## 尚未验证的假设

- "approved 后仅允许 `new-version` 回到 analyzing" 是否满足客户"批准后小改"流程——当前以版本替代表达，需真实项目检验。
- 混合层级设计项（技术挂在业务目标下）在复杂方案中的可用性；若真实方案需要显式"目标—技术项"多对多映射，需新增关联表（当前未做）。
- M2 E2E 依赖同一文件内 M1 测试留下的账号（单后端空数据根约束）；若未来拆分测试文件，需要独立数据根或凭据注入。

## 下一步最小行动（M3 启动）

1. 读 `docs/03/07` 结项与实施回收章节、`docs/specs/M2-PHASE2-PLAN.md`、本交接；
2. 先定 ADR-0007（Git/代码基线）与 ADR-0008（工作区），M3 的 `ExecutionRecord`/diff 关联依赖它们；
3. 规划 M3 切片：开发交接包 → 实施记录/Git diff → 偏差分析 → 验收矩阵 → As-built → 知识更新提议（人工发布）；每片沿用 M2 的"规格→实现→测试→证据→交接"。
4. 可选：补 M2 UI 截图与人工视觉验收，再进入 M3。

## 新会话必须先阅读

- `AGENTS.md`、`docs/00-MASTER-GUIDE.md`、`docs/README.md`、本交接
- `docs/specs/M2-PHASE2-PLAN.md`（含 §13 实施记录）
- `docs/evidence/M2-VERIFICATION.md`、`docs/quality/M2-BUGS.md`、`docs/process/M2-TIMELINE.md`
- 代码：`services/domain/src/main/java/com/mammoth/domain/workitem/`、`.../solution/`、`apps/web/src/pages/RequirementDetailPage.tsx`、`SolutionPage.tsx`
- 决策：`decisions/ADR-0010-M2-CONTEXT-BASELINE.md`、`docs/13-OPEN-DECISIONS.md`

## 风险提醒

- 不要把 M2 的 `approved` 当成"需求关闭"——M3 才接 `in_development` 之后。
- 不要绕过领域服务直接改 `work_item`/`solution_*` 表；状态机、四眼与版本规则都在服务层。
- 证据不可更新/删除是产品约束，不是遗漏。
- 仍未做生产化（TLS/共享会话/PG 方言）；不要把本地演示当部署就绪。
- `docs/evidence/M2-VERIFICATION.md` 中的数字对应提交 `e6f0cb6`；后续改动需更新证据。

## 2026-11-13 更新：ADR-0007/0008 已定案（M3 前置解除）

负责人指示"先定 ADR-0007/0008"。已按建议定案并记录（决策人：项目负责人，2026-11-13）：

- **ADR-0007（Accepted v1.0）**：`decisions/ADR-0007-GIT-BASELINE.md`
  - 本地**只读** Git + **人工捕获不可变基线**；不 clone/fetch/pull/push/checkout/commit/reset；不写工作树；
  - 只读命令白名单：`rev-parse`、`status --porcelain=v2 --branch`、`log --format`、`diff --name-status`、`diff --stat`、`worktree list --porcelain`、`remote get-url`（脱敏）；
  - 基线 = 项目 + 仓库集合 → commit；多仓库/多分支；脏树默认拒绝（owner 覆盖记 `reproducible=false`）；
  - 代码内容不复制进知识库；变更映射只存文件清单/统计（diff 正文仅作为 Artifact，限大小）；
  - DSH 临时 worktree **不得**注册为项目仓库（rc.2 归档会清理，S1 F6）；
  - 知识"过期"只做提示，不自动改状态（仍走人工四眼）。
- **ADR-0008（Accepted v1.0）**：`decisions/ADR-0008-WORKSPACE.md`
  - v1 单机本地**单写者**；数据根必须本地固定盘，拒绝 UNC/网络映射盘/云同步目录；
  - 数据根实例锁（`.mammoth-instance.lock`，M3 实现）；陈旧锁人工清理；
  - **MCP 入口改只读连接**（`query_only`，不跑 Flyway、不写库），迁移只由 Web/管理入口执行（M3 实现，当前 MCP 仍跑完整上下文）；
  - 跨机仅**导出/导入包**（不含凭据/密码哈希；仅空库导入；owner 执行并审计）；不做双向合并；
  - 备份用 `VACUUM INTO`/backup API + Artifact 目录校验（已实测 WAL 下成功；需专用连接且无进行中语句）。

**验证证据（2026-11-13，本机只读探针）**：git 2.50.1 的只读命令全部成功且工作树保持干净；`safe.directory` 精确路径可用；SQLite `VACUUM INTO` 在 WAL 下成功（副本读回 1 行），失败模式"statements in progress"已复现；只读连接与写连接并存可读、写入被拒；数据根默认在本地固定盘（DriveType=3/NTFS）。

**尚未实现（仅决策）**：实例锁、网络盘/云同步拒绝、MCP 只读连接、导出/导入、`GitAdapter`/基线表、知识过期字段。均列入 M3 切片（见 `docs/12-ROADMAP.md` M3）。

**下一步**：进入 M3 规格（建议先做 ADR-0008 的实例锁 + 只读 MCP，再做 ADR-0007 的仓库注册/基线捕获），每片沿用"规格→实现→测试→证据→交接"。
