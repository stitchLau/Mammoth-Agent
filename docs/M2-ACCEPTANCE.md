# M2 验收记录（需求与方案）

- **日期**：2026-11-12
- **基线**：`main` @ `598b49f`（M2 文档收尾；代码提交 `7e4ea30`/`e6f0cb6`）
- **范围**：`docs/specs/M2-PHASE2-PLAN.md` S1–S6、AC-1–AC-8、§12 DoD
- **结论**：**本地验收通过**（自动化 + API 演示 + 浏览器 E2E）；CI 远端未运行、真实项目未验证（由 M3 脱敏演练部分承接）；范围与残留项见第 6 节。

## 1. 验收口径

以 `docs/specs/M2-PHASE2-PLAN.md` §3 为准：

> 由不完整客户描述开始，补充材料后得到可审核、双视图一致、任务可追溯的方案。

## 2. 自动化结果（真实执行）

| 检查 | 命令 | 结果 |
|---|---|---|
| 后端 | `mvn -B -ntp -f services/domain/pom.xml verify` | **BUILD SUCCESS，62 tests**（M1 48 + 需求 6 + 方案 7 + MCP 1） |
| 前端 | `cd apps/web; npm run build; npm test` | build 通过；**25 tests** |
| 浏览器 E2E | 空数据根 + `npm run test:e2e` | **2 passed**（M1 4.6s、M2 3.6s；后续 M3 扩展为 3 场景） |
| API 演示 | `node scripts/demo-m2.mjs http://127.0.0.1:18132` | **26/26** |

## 3. 验收标准映射

| AC | 证据 |
|---|---|
| AC-1 模糊需求 → 证据批次 → 未确认项 | `RequirementWorkflowTest`；demo 步骤 3–5 |
| AC-2 关键缺口阻断 + 假设放行 + 负责人确认 + 快照 | `RequirementWorkflowTest`（readiness/confirm）；demo 6–7 |
| AC-3 同一模型双视图 | `SolutionWorkflowTest.dualViews...`；E2E 双视图断言；demo 11 |
| AC-4 一致性规则阻断评审 | `SolutionWorkflowTest`（consistency/goal/style）；demo 9 |
| AC-5 四眼 + 批准不可覆盖 + superseded | `SolutionWorkflowTest.authorCannotApprove...`；demo 12–16 |
| AC-6 追溯矩阵 | 渲染追溯矩阵 + E2E 双视图；`SolutionWorkflowTest` 行内容 |
| AC-7 权限边界 | `RequirementWorkflowTest`、`SolutionWorkflowTest`、`PermissionIsolationTest` |
| AC-8 审计与不可篡改 | 各写路径审计 + `AuditImmutabilityTest`；demo 17 |

## 4. 安全与权限

- 需求/方案读写按 owner/editor/reviewer/viewer 分层，服务层与控制器双重校验；
- 四眼：方案作者不可批准自己的方案；`approved` 版本不可编辑；
- 审计：状态/版本/证据/评估/评审动作均落 `audit_log`，V3 触发器禁止改删；
- 证据仅追加，无更新/删除端点；跨项目访问 404 不泄漏。

## 5. 已知限制（M2 范围）

1. 无应用内 LLM；AI 参与仅 MCP 只读工具（ADR-0011 未立）；
2. CI 远端未运行；
3. 交付/实施/知识闭环属 M3；M2 只到 `approved`；
4. 需求/方案列表客户端分页；无富文本编辑；
5. 交接包/矩阵等后续增强在 M3 与 M3.5 backlog。

## 6. 残留与承接

- 真实项目验证 → M3 脱敏验收演练（`scripts/m3-acceptance-drill.mjs`）；
- 交接包文件导出、Baseline 部署/DDL 版本、矩阵证据链接 → `docs/specs/M3.5-BACKLOG.md`；
- 视觉品牌规范（Q-001）、知识编辑形态（Q-002）、生产认证（Q-004）仍待决。

## 7. 确认

- 执行：Lead Agent（2026-11-12，按负责人授权"按建议执行"）；
- 负责人确认：**待确认**（本记录为本地验收事实归档，不代替负责人签署）。
## 8. UI 截图（2026-11-13 补）

- [evidence/screenshots/m2-requirements-list.png](evidence/screenshots/m2-requirements-list.png)
- [evidence/screenshots/m2-requirement-readiness.png](evidence/screenshots/m2-requirement-readiness.png)
- [evidence/screenshots/m2-solution-dual-view.png](evidence/screenshots/m2-solution-dual-view.png)

生成命令：
ode scripts/capture-m2m3-screenshots.mjs（脱敏数据 + 真实后端，非模拟）。