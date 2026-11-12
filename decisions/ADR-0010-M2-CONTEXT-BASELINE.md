# ADR-0010: 需求上下文基线与方案版本绑定

- 状态：Accepted
- 日期：2026-11-12
- 决策人：负责人（2026-11-12 授权"过程中的疑问均按 Agent 建议执行"）
- 相关需求/功能：M2 需求与方案（`docs/specs/M2-PHASE2-PLAN.md`）

## 背景与约束

`docs/03-DOMAIN-MODEL.md` §2 要求"SolutionVersion 必须绑定需求基准版本和项目基线"；`docs/07-WORK-ITEM-LIFECYCLE.md` §6 要求方案版本保留"作者、输入基线、变更摘要、评审意见、批准人、批准时间"。

现状（证据）：

- M1 未实现 `ProjectBaseline`（无表、无实体），Git 代码基线仍未定案（ADR-0007 OPEN）；
- 需求证据、知识文档引用、Artifact 都是不可变或可追溯对象，但**没有"某一时刻需求上下文"的稳定引用**；
- ADR-0007/0008/0009 在 M2 保持 OPEN，M2 不得依赖 Git、共享工作区或文档解析。

约束：M2 必须能在单机、无 Git 集成的前提下记录"方案基于什么上下文生成"，且未来接入 `ProjectBaseline` 时不需要重写已批准的方案版本。

## 需要解决的问题

1. 方案版本如何证明"依据哪一版需求与证据"？
2. 需求上下文本身如何版本化（证据会持续追加、评估会变）？
3. 与未来 Git/部署基线如何兼容？

## 备选方案

### 方案 A：轻量上下文快照（需求级 JSON 快照）

新增 `requirement_snapshot`，内容为需求字段、证据 ID/批次、11 维度评估、引用 Artifact ID、维度目录版本；`work_item.context_snapshot_id` 指向当前基线，`solution_version.context_snapshot_id` 指向创建/提交时的快照。

### 方案 B：现在就实现完整 `ProjectBaseline`（Git Commit + 部署版本 + DDL 版本）

按 `03` 的最终形态建表并接入 Git 读取。

### 方案 C：不记录基线，仅在方案正文写明"依据当前需求"

零实现成本。

## 决策

采用**方案 A**；方案 B 的 `ProjectBaseline` 在 ADR-0007 定案后以**新增列**方式挂到 `solution_version`（如 `project_baseline_id`），不修改已批准版本的语义。

## 决策理由

- 方案 B 依赖 Git 与项目代码基线，ADR-0007 未定案，且 M2 不需要代码差异即可验证"方案可追溯"；
- 方案 C 无法满足 `03` §2 与 `07` §6 的"绑定基线"要求，也无法回答"批准时依据哪批证据"；
- 方案 A 复用已有不可变对象（Artifact/证据），快照只存引用与评估，不复制客户正文，体积小、可重建、可校验。

## 取舍和影响

- 正向：方案批准可复现；证据追加不改变历史版本依据；JSON payload 含目录版本（`dimension_catalog_version = 1`），未来维度调整可识别；
- 负向：快照是"应用级"事实，不含源码/数据库版本；若客户要求"代码基线"级证明，需等 ADR-0007；
- 数据：新增 1 表（`requirement_snapshot`），`work_item`/`solution_version` 各 1 个外键列；
- 不含任何凭据、客户敏感正文副本（payload 中证据只存 ID 与类型，正文留在 `requirement_evidence`）。

## 安全/数据/迁移影响

- 快照只读（无更新端点）；生成动作落 `audit_log`（`work_item.readiness_confirm`、`solution.submit`）；
- 迁移 V4 一次性建立，SQLite/PostgreSQL 兼容（TEXT/INTEGER）；
- 回滚：删除 M2 表即可（M1 表不受影响），但会丢失 M2 数据，需备份。

## 验证计划和结果

- `MigrationTest`：V4 应用、幂等、表存在；
- `ReadinessWorkflowTest`：确认就绪生成快照，版本号递增，payload 含证据 ID；
- `SolutionWorkflowTest`：提交时快照 ID 固定，批准后再次新建版本引用新快照；
- 结果：见 `docs/evidence/M2-VERIFICATION.md`。

## 回滚或替代路径

- 短期替代：若快照机制成为负担，可降级为"仅记录 `work_item.updated_at` + 证据数量"，但会失去可复现性；
- 长期替代：ADR-0007 定案后，`solution_version` 增加 `project_baseline_id`，`requirement_snapshot` 保留为"需求侧基线"。

## 后续行动

1. M3 启动前评估是否将 `ProjectBaseline`（Git commit、部署版本、DDL 版本）并入方案版本；
2. 若引入应用内 LLM，草案同样必须记录输入快照 ID（ADR-0011 草案时引用本 ADR）。
