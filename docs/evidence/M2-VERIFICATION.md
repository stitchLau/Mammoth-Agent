# M2 验证证据（需求与方案）

- **日期**：2026-11-12（Asia/Shanghai）
- **基线**：`main` @ `e6f0cb6`（S0 `742f69d` → 后端 `7e4ea30` → 前端 `e6f0cb6`）
- **范围**：`docs/specs/M2-PHASE2-PLAN.md` §3 AC-1–AC-8、§12 DoD
- **结论**：**通过**（本地自动化：后端 62 tests、前端 build + 25 tests、Playwright 2 passed、API 演示 26/26）

## 1. 自动化验收结果（真实执行）

| 检查 | 命令 | 结果 |
|---|---|---|
| 后端构建 + 测试 + 打包 | `mvn -B -ntp -f services/domain/pom.xml verify` | **BUILD SUCCESS，62 tests**（M1 48 + 需求 6 + 方案 7 + MCP 1） |
| Flyway 迁移 | 同上（`MigrationTest`） | V1–V4 应用成功、目标版本 4、二次执行 0、23 张业务表存在 |
| 前端类型检查 + 构建 | `cd apps/web; npm run build` | 通过（tsc --noEmit + vite build，63 modules） |
| 前端单元测试 | `cd apps/web; npm test` | **25 tests passed**（format 12 / permissions 10 / pagination 3） |
| 浏览器 E2E | 空数据根启动后端 + `cd apps/web; npm run test:e2e` | **2 passed（8.8s）**：M1 流程 4.6s、M2 流程 3.6s |
| API 端到端演示 | `node scripts/demo-m2.mjs http://127.0.0.1:18132` | **26/26 步骤通过** |

环境：Windows 10、PowerShell 5.1、JDK 17.0.15、Maven 3.9.11、Node（npm.cmd）；后端测试端口 18131–18135，数据根均为 `%TEMP%` 下新建临时目录。

## 2. 浏览器 E2E（`apps/web/e2e/m1-ui.spec.ts`）

| 测试 | 覆盖 |
|---|---|
| `M1 UI end-to-end` | M1.5 回归：首次设置 → 项目 → 资料 → 知识草稿 → 提交 → 不可自审 → 改密 → reviewer 审核 → 检索引用（保持通过） |
| `M2 requirement to approved solution` | 创建需求 → 追加证据 → 11 维度就绪检查（**每维保存后等待响应与按钮恢复**）→ 负责人确认就绪 → 新建方案 → 业务/技术/共享设计项 + 验收标准 + 任务 → 一致性通过 → 双视图同时可见 → 提交评审 → 作者不可自批 → reviewer 批准 → 需求详情显示"当前批准版本" |

> 说明：CI 的 `e2e` job 只启动一个空数据根后端，因此 M2 测试复用同一文件内 M1 测试留下的账号/项目（模块级 `shared`），并在未先运行 M1 时 `test.skip`；这是本轮为单后端约束做的取舍，已记录为限制。

## 3. API 演示（`scripts/demo-m2.mjs`，26 步）

1. health / csrf / 首个管理员 bootstrap / 登录
2. 项目 + reviewer 成员 / 资料上传（SHA-256）
3. 需求创建（`REQ-0001`、`draft`）
4. 两批证据追加（`batchNo` 1→2，第二批引用 Artifact）
5. 首证据后自动 `collecting`；就绪检查 11 项阻断
6. 10 维 `confirmed` + 非关键 `exceptions` 以"假设 + 决策责任人"通过
7. 负责人确认就绪 → `ready_for_analysis` + 快照
8. 新建方案 v1 → 需求 `analyzing`
9. 空方案提交 **409**（一致性阻断）
10. 业务/技术（挂在业务目标下）/共享设计项 + 验收标准 + 任务
11. 一致性通过；业务/技术双视图由同一模型渲染，追溯矩阵 1 行
12. 提交评审 → `in_review`；作者自批 **403**
13. reviewer 批准 → `approved`，需求 `approved`
14. 批准版本编辑 **409**（不可覆盖）
15. `new-version` → v2 草稿，需求回到 `analyzing`
16. v2 提交 + reviewer 批准 → v1 `superseded`、v2 `approved`
17. 快照 3 个（就绪 1 + 两次提交）；owner/reviewer 审计动作可见；登出后 **401**

## 4. 验收标准映射

| AC | 证据 |
|---|---|
| AC-1 模糊需求 → 证据批次 → 未确认项 | `RequirementWorkflowTest.evidenceMovesDraftToCollectingAndKeepsBatchesImmutable`；demo 步骤 3–5 |
| AC-2 关键缺口阻断 + 假设放行 + 负责人确认 + 快照 | `readinessGateBlocksCriticalGapsAndAcceptsDocumentedAssumptions`、`confirmReadinessCreatesSnapshotAndRequiresCollectingState`；demo 6–7 |
| AC-3 同一模型双视图 | `SolutionWorkflowTest.dualViewsRenderFromTheSameModelAndTraceabilityIsDerived`；E2E 双视图断言；demo 11 |
| AC-4 一致性 6 规则阻断评审 | `consistencyGateBlocksIncompleteModelAndAcceptsCompleteOne`、`goalWithoutAcceptanceIsReportedAsIssue`、`structureGuardsRejectCrossVersionAndUntracedData`；demo 9 |
| AC-5 四眼 + 批准不可覆盖 + 新版本 superseded | `authorCannotApproveAndApprovedVersionsAreImmutable`、`requestChangesRequiresCommentAndReopensTheDraft`；demo 12–16 |
| AC-6 追溯双向可达 | `SolutionRenderer.traceability` + 渲染追溯矩阵；`SolutionWorkflowTest.dualViews...` 断言行内容；UI 双视图 |
| AC-7 权限边界 | `viewerCannotWriteAndOutsiderCannotRead`、`viewerCannotEditSolution`；`PermissionIsolationTest` 保持通过 |
| AC-8 审计与不可篡改 | 各写路径 `auditService.record`；`AuditImmutabilityTest` 保持通过；demo 17 |

## 5. 未覆盖/已知限制（如实登记）

- **CI 远端仍未运行**（无远端执行环境）：新增测试与 E2E 只在本机验证。
- **真实 DSH 会话调用**：`get_requirement_context` 只有服务级/工具描述测试；未在本轮真实 DSH 会话中重新调用（M1 已实测 `search_project_knowledge`，MCP 传输层未改动）。
- **截图**：本轮未生成 M2 截图（M1.5 截图脚本未扩展）；UI 由 Playwright 真实浏览器流程验证。
- **视觉/品牌**：仍沿用中性色基线（Q-001 未关闭）。
- **生产化**：TLS、共享会话、PostgreSQL 方言、后端分页未做（M2 明确非目标）。
- **PDF/Office 解析**：证据为原始资料 + 人工摘录（ADR-0009 仍 OPEN）。

## 6. 复现命令

```powershell
# 后端
mvn -B -ntp -f services/domain/pom.xml verify
# 前端
cd apps/web; npm run build; npm test
# 浏览器 E2E（空数据根）
java -jar services/domain/target/mammoth-domain-service.jar --server.port=18135 `
  --mammoth.data-root="$env:TEMP\mammoth-m2-e2e"
cd apps/web; $env:MAMMOTH_E2E_BASE='http://127.0.0.1:18135'; npm run test:e2e
# API 演示（另一个空数据根）
java -jar services/domain/target/mammoth-domain-service.jar --server.port=18132 `
  --mammoth.data-root="$env:TEMP\mammoth-m2-demo"
node scripts/demo-m2.mjs http://127.0.0.1:18132
```
