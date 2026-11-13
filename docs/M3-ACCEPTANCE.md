# M3 验收记录（实施回收与知识闭环）

- **日期**：2026-11-13
- **基线**：`main` @ `1f33a44`（M3 + M3.5 补强；以 `git log -1` 为准）
- **范围**：`docs/specs/M3-PHASE3-PLAN.md` S1–S6、§12 M3.5 补强、ADR-0008 导出/导入/备份/锁清理
- **结论**：**本地验收通过**（自动化 + 浏览器 E2E + 两套真实 CLI 演练）；真实客户项目验收仍需负责人提供授权脱敏项目；剩余技术债见 `docs/specs/M3.5-BACKLOG.md`。

## 1. 验收口径

以 `docs/12-ROADMAP.md` M3 退出条件与 `docs/specs/M3-PHASE3-PLAN.md` 为准：

> 至少一个需求从批准方案到真实实施结果和知识更新闭环。

**说明**：本阶段用**脱敏/合成项目 + 真实服务 + 真实临时 Git 仓库**完成闭环（`scripts/m3-acceptance-drill.mjs`）；未接触真实客户数据。"真实客户项目"的最终业务验收仍待负责人提供授权项目。

## 2. 自动化结果（真实执行）

| 检查 | 命令 | 结果 |
|---|---|---|
| 后端 | `mvn -B -ntp -f services/domain/pom.xml verify` | **BUILD SUCCESS，89 tests** |
| 前端 | `cd apps/web; npm run build; npm test` | build 通过；**25 tests** |
| 浏览器 E2E | 空数据根 + `npm run test:e2e` | **3 passed（15.8s）**：M1/M2/M3 |
| M3 演示 | `node scripts/demo-m3.mjs` | **25/25** |
| 脱敏验收演练 | `node scripts/m3-acceptance-drill.mjs` | **18/18**（含知识发布与 supersede） |
| ADR-0008 演练 | `node scripts/admin-drill.mjs` | **9/9**（导出/导入/备份/恢复） |
| 锁清理 | CLI + `scripts/clear-data-root-lock.ps1` | 运行中拒绝、停止后清理、无锁幂等 |

## 3. 能力闭环对照

| M3 要求 | 状态 | 证据 |
|---|---|---|
| 开发交接包 | ✅（v1 Markdown；文件导出见 BG-01） | `handoff.generate`、验收演练 |
| 实施记录 / Git 差异关联 | ✅（文件级） | `ExecutionRecord`/`execution_change`、测试 |
| 方案偏差分析 | ✅（系统未完成候选 + 人工分类） | `execution_deviation`、测试 |
| 验收矩阵 | ✅（覆盖状态 + 未完成任务） | `acceptance-matrix`、E2E |
| As-built / 最终交付快照 | ✅（四眼、Artifact、不可覆盖） | `DeliveryWorkflowTest`、演示 |
| 知识更新提议与人工发布 | ✅（草稿→四眼 verify→supersede/deprecated） | `DeliveryWorkflowTest`、验收演练 |
| Approved Design 不被覆盖 | ✅ | M2 版本不可变 + 交付快照只追加 |
| 数据边界（单机/只读 Git/无源码入库） | ✅ | ADR-0007/0008、测试 |

## 4. 安全与审计

- Git 只读白名单、超时/输出上限、远程 URL 脱敏、拒绝数据根/DSH 目录；
- 交付快照/知识提议四眼；批准快照落 Artifact 且不可覆盖；
- 知识 supersede/deprecated 仅 owner/reviewer，历史保留、检索仅 verified；
- 导出不含密码哈希/会话；导入仅空库、逐条目校验；备份/恢复校验 sha256 与 `integrity_check`；
- 审计：仓库/基线/实施/偏差/交接/交付/知识状态/导入导出/备份均有记录。

## 5. 已知限制

1. **真实客户项目验收未做**（无授权脱敏项目；不能接触生产数据）；
2. 交接包仅为 Markdown API 响应，非可携带文件（BG-01）；
3. ProjectBaseline 无部署版本/DDL 版本（BG-02）；
4. 验收矩阵未连接业务目标/实施证据，偏差目标 UI 未用（BG-03）；
5. Git 映射仅文件级、重命名统计近似、子模块不递归；
6. 导出不加密、非管理员账号导入后锁定、无增量导出；
7. CI 远端未运行；E2E 在本机需完整权限（沙箱限制见证据 §4）。

## 6. 确认

- 执行：Lead Agent（2026-11-13，按负责人"完成 M3 阶段"、"补 1、2 项 + 登记 3–5 + 锁清理"授权）；
- 负责人确认：**待确认**（本记录为本地验收事实归档；真实项目验收为独立业务确认）。
## 7. UI 截图（2026-11-13 补）

- [evidence/screenshots/m3-baselines.png](evidence/screenshots/m3-baselines.png)：仓库注册与不可变基线
- [evidence/screenshots/m3-delivery-closure.png](evidence/screenshots/m3-delivery-closure.png)：交接包、实施记录、偏差、验收矩阵、As-built、知识提议
- [evidence/screenshots/m3-knowledge-supersede.png](evidence/screenshots/m3-knowledge-supersede.png)：知识替代/废弃操作

生成命令：
ode scripts/capture-m2m3-screenshots.mjs。