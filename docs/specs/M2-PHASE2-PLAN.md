# M2 Phase 2 计划 — 需求与方案（第二垂直切片）

- **状态**：Approved（2026-11-12 负责人授权"过程中的疑问均按建议执行"后进入实施；实施记录见 §13）
- **日期**：2026-11-12
- **前置**：M1 / M1.5 完成（`docs/M1-ACCEPTANCE.md`、`docs/handoffs/SESSION-HANDOFF-2026-11-12-M1.5.md`）；ADR-0001/0002/0003/0004/0005/0006 Accepted；ADR-0010 Accepted（本阶段新增）
- **对应路线图**：`docs/12-ROADMAP.md` M2
- **依据**：`docs/03-DOMAIN-MODEL.md`、`docs/04-MVP-STRATEGY.md` Phase 2、`docs/07-WORK-ITEM-LIFECYCLE.md`、`docs/06-KNOWLEDGE-AND-RAG.md`、`docs/08-SECURITY-AND-TRUST.md`、`docs/09-QUALITY-AND-TESTING.md`、`docs/02-PRODUCT-BOUNDARIES.md`

## 1. 目标

由**不完整客户描述**开始，经过多批次证据追加、缺口/冲突识别与人工就绪确认，产出**单一 SolutionModel** 驱动的业务方案、技术方案、任务拆解与追溯矩阵，并完成**人工评审与版本记录**：

1. 需求工作项（仅 `requirement` 类型落地，其他类型预留扩展点）；
2. 证据追加（不可改写，可引用 Artifact 或粘贴原文/纪要）；
3. 版本化上下文（就绪评估、方案版本绑定可复现快照）；
4. 11 维度缺口/冲突识别 + 需求就绪门槛 + 负责人显式确认；
5. 统一 SolutionModel → 业务/技术双视图渲染（同一模型，无第二份可编辑副本）；
6. 任务拆解 + 验收标准 + 追溯矩阵；
7. 方案评审与版本（approved 不可覆盖、四眼、审计）。

## 2. 非目标（本阶段不做，入 backlog）

| 延期项 | 用户价值 | 延期原因 | 恢复条件 | 目标阶段 |
|---|---|---|---|---|
| Jira/CSM 集成与外部工单同步 | 减少手工录入 | 无授权测试实例；M2 用手工创建验证闭环 | 有隔离测试实例与授权 | M4 |
| Git diff / 实施回收 / As-built | 方案与实际对照 | M2 时无代码基线（ADR-0007 已定，M3 实现） | ADR-0007 + M3 实现 | M3 |
| PDF/Office/音频解析入证据 | 减少人工摘录 | ADR-0009 OPEN；解析管线与安全评审未定 | ADR-0009 定案 | M2.5/M3 |
| 应用内 LLM 调用（自动缺口识别/方案草稿） | 提升起草效率 | 无凭据注入与可测口径；见 §7 | ADR-0011 + 凭据方案 | M2.5/M3 |
| 向量/混合检索 | 语义召回 | ADR-0005 已定"全文优先，向量延后" | 真实 embedding Spike | 后续 |
| 通知/邮件、密码重置、用户停用 | 运营便利 | 无真实运营对象 | 有部署对象 | 生产化 |
| 多组织/多租户深化、SSO | 企业化 | ADR-0006 后续 | 有客户需求 | M4 |
| 富文本编辑 | 排版 | Q-002 OPEN；XSS 评审未做 | Q-002 定案 | 后续 |
| `in_development` 及之后状态 | 实施跟踪 | 属 M3 实施回收 | M3 启动 | M3 |
| TLS/共享会话/PG 方言/后端分页 | 生产可用 | 单机 localhost 交付，无部署对象 | 有部署需求 | 生产化切片 |

## 3. 验收标准

| ID | Given / When / Then |
|---|---|
| AC-1 | Given 项目成员在项目中；When 用一句模糊描述创建需求并追加 2 批证据（1 批引用 Artifact）；Then 需求处于 `collecting`，证据按批次不可变留存，就绪面板列出 11 个维度的未确认项 |
| AC-2 | Given 存在关键维度 `missing`/`conflict`；When 请求 `ready_for_analysis`；Then 409 + 阻断原因清单；补齐或按"非关键维度 + 假设 + 决策责任人"标注后，负责人显式确认 → 状态变 `ready_for_analysis`，并生成上下文快照 |
| AC-3 | Given 一个方案版本；When 请求 `view=business` 与 `view=technical`；Then 两个视图由同一 `solution_design_item` 集合渲染，共享项出现在两个视图，且均含验收标准与基线版本号 |
| AC-4 | Given 方案缺技术设计项 / 某业务目标无验收覆盖 / 某任务无追溯；When 校验或提交评审；Then 返回逐条错误并阻止 `solution_review` |
| AC-5 | Given 方案在 `in_review`；When 作者尝试批准 → 403；When owner/reviewer 批准 → `approved`，记录 approver/approvedAt/快照；Then 该版本不可编辑（409），再次变更必须新建版本，旧批准版本转 `superseded` |
| AC-6 | Given 已批准方案；When 查看追溯矩阵；Then 业务目标→设计项→任务→验收标准双向可达，API 与 UI 一致 |
| AC-7 | Given viewer/非成员；When 写需求/证据/方案或跨项目读取；Then 403（非成员）/404（跨项目不泄漏） |
| AC-8 | Given 任一状态/版本动作；Then `audit_log` 有对应记录，且 V3 不可篡改触发器仍生效 |

## 4. 领域与数据模型

### 4.1 聚合与表（V4 迁移）

| 表 | 关键列 | 说明 |
|---|---|---|
| `work_item` | id, project_id, item_type, item_no, title, summary, status, priority, source_note, owner_user_id, created_by, prior_status, ready_confirmed_by/at, context_snapshot_id, approved_solution_version_id, created_at, updated_at | 工作项主对象；`item_no` 项目内 `REQ-0001` 递增且唯一 |
| `requirement_evidence` | id, work_item_id, batch_no, evidence_type, text, artifact_id, locator, captured_at, added_by, created_at | 追加写、无更新/删除端点；`(work_item_id, batch_no)` 唯一 |
| `requirement_assessment` | id, work_item_id, dimension, state, note, assumption_note, decision_owner, updated_by, updated_at | 每需求每维度一行；upsert |
| `requirement_snapshot` | id, work_item_id, version, payload_json, created_by, created_at | 版本化上下文基线（ADR-0010） |
| `solution_version` | id, work_item_id, version, status, context_snapshot_id, title, change_summary, created_by, submitted_at, approver_user_id, approved_at, created_at, updated_at | `(work_item_id, version)` 唯一 |
| `solution_design_item` | id, solution_version_id, item_kind, parent_id, title, body, ordinal | 单一 SolutionModel；`business`/`technical`/`shared` |
| `acceptance_criterion` | id, solution_version_id, design_item_id, text, ordinal | 验收标准必须挂设计项 |
| `solution_task` | id, solution_version_id, design_item_id, acceptance_criterion_id, title, description, done_when, depends_on_task_id, ordinal | 任务至少挂设计项或验收标准之一 |
| `approval_record` | id, object_type, object_id, action, actor_user_id, comment, at | 业务语义审批记录（区别于 audit_log） |

> 追溯矩阵由外键派生，不建冗余表；`solution_chunk`/检索不在 M2 范围。SQLite 方言，类型只用 TEXT/INTEGER，保持 PostgreSQL 可移植（ADR-0003）。
>
> 设计项层级可**混合类型**：技术设计项可以挂在它实现的业务目标下（`parent_id` 指向 business 项），这样"业务目标 → 设计项 → 任务 → 验收"的追溯由树结构直接表达；渲染时按视图过滤类型，父项在被过滤掉时子项成为该视图的根。每个业务根目标必须在其子树内有验收标准（§4.4 规则 4）。

### 4.2 需求状态机

```
draft ──evidence──> collecting ──confirm-readiness──> ready_for_analysis
                       │                                  │
                       │                                  └──create solution──> analyzing
                       │                                                          │
                       │                                            submit ───────┘
                       │                                                          ▼
                       │                                                   solution_review
                       │                                                    │          │
                       │                                          approve ──┘          └── request_changes ──> analyzing
                       ▼
                    approved（M2 终点）──new-version──> analyzing（方案修订，旧批准版本转 superseded）

副状态：on_hold / blocked（记录 prior_status，可恢复到原状态）
终止：withdrawn（创建人/owner 撤回）、rejected（owner/reviewer 拒绝，需理由）
```

- 所有迁移由 `WorkItem.transitionTo` 白名单驱动，非法迁移 409；
- `draft` 首次追加证据自动进入 `collecting`；
- `approved` 版本本身不可编辑（409）；方案修订只能通过 `new-version` 从批准版本复制出新草稿，并把需求重新打开到 `analyzing`。

### 4.3 就绪规则（11 维度）

维度：`background`、`goals`、`current_process`、`target_process`、`business_rules`、`roles_and_scope`、`exceptions`、`data_and_metrics`、`integrations`、`acceptance`、`constraints`。

状态：`missing`、`partial`、`confirmed`、`inferred`、`conflict`（在 `07` §4 的"缺失/冲突/已确认/推断"上增加 `partial`，用于表达部分覆盖；对就绪判断而言 `partial` 与 `missing` 同为未确认）。

关键维度（critical）：`background`、`goals`、`target_process`、`acceptance`。

阻断规则（`ReadinessService`）：

| 条件 | 结果 |
|---|---|
| 任一维度 `conflict` | 阻断 |
| 关键维度 `missing`/`partial` | 阻断 |
| 非关键维度 `missing`/`partial`，且缺 `assumption_note` 或 `decision_owner` | 阻断 |
| 任一端 `inferred`，且缺 `decision_owner` | 阻断 |
| 其余 | 就绪（可确认） |

负责人确认后生成 `requirement_snapshot`（payload：需求字段、证据 ID/批次、评估、引用 Artifact ID、维度目录版本），写入 `work_item.context_snapshot_id`。

### 4.4 方案一致性规则（阻断 `solution_review`）

1. 版本至少 1 个 `business` 设计项、1 个 `technical` 设计项、1 个验收标准、1 个任务；
2. 每个验收标准必须引用**同版本**设计项；
3. 每个任务必须引用**同版本**设计项或验收标准；
4. 每个根 `business` 设计项（含自身或后代）至少被 1 条验收标准覆盖；
5. `parent_id`/`design_item_id`/`acceptance_criterion_id`/`depends_on_task_id` 必须属于同版本；
6. 设计项层级最深 5 层，禁止环（新增时校验父子链）。

### 4.5 版本与四眼

- `draft → in_review`（提交）需一致性通过，生成方案快照（复用 `requirement_snapshot` 机制，记录方案内容摘要与基线）；
- `in_review → approved`：owner/reviewer，且批准人 ≠ 版本作者；写入 `approval_record(action=approve)`；
- `in_review → draft`（request_changes）记录 `approval_record(action=request_changes)`；
- 已批准版本不可编辑；`new-version` 从指定版本复制结构（version+1，draft），批准旧版本转 `superseded`；
- 同一时刻最多 1 个 `approved` 版本。

## 5. 权限矩阵

| 动作 | owner | editor | reviewer | viewer |
|---|---|---|---|---|
| 查看需求/方案/渲染 | ✓ | ✓ | ✓ | ✓ |
| 创建/编辑需求、追加证据、填写评估 | ✓ | ✓ | — | — |
| 确认就绪 / 拒绝需求 | ✓ | — | ✓ | — |
| 创建/编辑方案结构、提交评审 | ✓ | ✓ | — | — |
| 批准方案 / 要求修改 | ✓（≠作者） | — | ✓（≠作者） | — |

服务层与控制器双重校验；非成员一律 403，跨项目资源 404。

## 6. API 契约（M2）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/projects/{p}/work-items` | 创建需求（title, summary, priority, sourceNote） |
| GET | `/api/projects/{p}/work-items?status=` | 列表 |
| GET | `/api/projects/{p}/work-items/{id}` | 详情（含证据、评估、就绪摘要、方案版本） |
| PUT | `/api/projects/{p}/work-items/{id}` | 编辑标题/摘要/优先级/负责人（approved 后 409） |
| POST | `/api/projects/{p}/work-items/{id}/evidence` | 追加证据批次 |
| PUT | `/api/projects/{p}/work-items/{id}/assessments/{dimension}` | 填写维度评估 |
| GET | `/api/projects/{p}/work-items/{id}/readiness` | 就绪摘要 |
| POST | `/api/projects/{p}/work-items/{id}/confirm-readiness` | 负责人确认就绪（阻断时 409 + 原因） |
| POST | `/api/projects/{p}/work-items/{id}/transitions` | on_hold/blocked/resume/withdrawn/rejected/analyzing |
| POST | `/api/projects/{p}/work-items/{id}/solutions` | 新建方案版本 |
| GET | `/api/projects/{p}/work-items/{id}/solutions` | 版本列表 |
| GET | `/api/projects/{p}/work-items/{id}/solutions/{v}` | 版本详情（结构 + 校验报告） |
| POST | `/api/projects/{p}/work-items/{id}/solutions/{v}/design-items` | 新建设计项 |
| PUT/DELETE | `.../design-items/{itemId}` | 编辑/删除设计项（draft 且无引用） |
| POST | `.../acceptance-criteria` | 新增验收标准 |
| DELETE | `.../acceptance-criteria/{id}` | 删除验收标准（draft） |
| POST | `.../tasks` | 新增任务 |
| DELETE | `.../tasks/{id}` | 删除任务（draft） |
| GET | `.../render?view=business\|technical` | 双视图渲染（Markdown + 结构化摘要素） |
| POST | `.../submit` | 提交评审（一致性通过） |
| POST | `.../approve` | 批准（四眼） |
| POST | `.../request-changes` | 要求修改 |
| POST | `.../new-version` | 基于该版本创建新草稿版本 |

错误语义沿用 M1：400 参数、401 未认证、403 越权、404 不存在/跨项目、409 状态冲突。

## 7. Agent 参与边界

- **M2 不引入应用内 LLM 调用**（无凭据注入、无可测输出口径）；缺口/冲突识别为"结构化评估 + 人工判断"；
- 保留 MCP 只读能力扩展：`get_requirement_context` 返回需求字段、证据、评估与最近批准方案摘要，供 DSH Agent 在会话中辅助分析（仍受项目成员与 only-verified-knowledge 约束）；
- AI 生成内容进入正式方案必须走"草案 → 人工接受"（ADR-0011，M2.5/M3）；M2 不存在 Agent 直写路径。

## 8. 决策记录（Q1–Q9，2026-11-12）

| Q | 结论 |
|---|---|
| Q1 | ADR-0007/0008/0009 **保持 OPEN**；新增 ADR-0010（上下文基线与方案版本绑定）。**2026-11-13 更新：ADR-0007/0008 已定案（Accepted v1.0），ADR-0009 仍 OPEN** |
| Q2 | 首切片 S1（需求与证据），S0 规格先行 |
| Q3 | **不先补生产化**；只做与新表相关的必要加固（迁移幂等、审计、权限负向测试） |
| Q4 | 本轮不接应用内 LLM；仅 MCP 只读工具 |
| Q5 | 不新增角色，沿用 owner/editor/reviewer/viewer + 四眼 |
| Q6 | 双视图只读渲染 + 结构层编辑（单模型） |
| Q7 | 需求状态机止于 `approved` |
| Q8 | 关系表 + 服务端 Markdown 渲染 |
| Q9 | 第一个提交只含 S0 文档 |

## 9. 任务拆解

| 切片 | 内容 | 验收 | 状态 |
|---|---|---|---|
| S0 | 本文件 + ADR-0010 + 索引/决策表校正 | 文档一致、ADR Accepted | 已完成 |
| S1 | V4 迁移、WorkItem/Evidence 领域与 REST、审计 | AC-1、AC-7、AC-8 | 已完成 |
| S2 | 11 维度评估、就绪摘要、快照、确认门槛 | AC-2 | 已完成 |
| S3 | SolutionVersion/DesignItem、双视图渲染、一致性校验 | AC-3、AC-4 | 已完成 |
| S4 | 任务/验收标准/追溯矩阵 | AC-4、AC-6 | 已完成 |
| S5 | 提交/批准/要求修改/新版本/superseded/四眼 | AC-5 | 已完成 |
| S6 | 前端页面、演示脚本、Playwright E2E、证据与交接 | 全量回归 | 已完成 |

## 10. 测试策略

- Unit（服务级，SpringBootTest NONE）：状态机白名单、就绪阻断矩阵、一致性 6 条规则、四眼与版本不可覆盖、权限负向；
- Integration：Flyway V4 幂等 + 表存在；审计追加写仍不可改；
- Contract：REST DTO 字段（前端 `api.ts` 与控制器保持一致）；
- E2E：需求创建 → 证据 → 就绪确认 → 方案结构 → 提交 → 另一账号批准 → 双视图与追溯矩阵；
- Regression：M1 全量测试（48）必须保持通过。

## 11. 风险

| 风险 | 缓解 |
|---|---|
| 9 张新表一次迁移 | 单次 V4 + 迁移测试幂等；列名与 `03` 语义对齐 |
| 状态机蔓延（其他工作项类型流程不同） | M2 只实现 requirement，类型字段预留（`02` §4） |
| 双视图漂移 | 单一模型 + 渲染器 + 一致性测试；不提供视图级编辑 |
| 方案结构自由度过高导致校验噪音 | 校验规则限定 6 条、逐条错误信息 |
| UI 复杂度 | 需求详情与方案详情分页，结构编辑最小字段集 |
| approved 后需求仍须变更 | `new-version` + `superseded`，保留历史 |

## 12. Definition of Done（M2）

- §3 AC-1–AC-8 全部有自动化证据；
- 后端全量 `mvn verify` 通过且 M1 用例不回归；前端 `build` + `npm test` 通过；E2E 1 场景通过；
- `scripts/demo-m2.mjs` 实跑通过并记录输出；
- 文档更新：本文件实施记录、`docs/evidence/M2-VERIFICATION.md`、`docs/process/M2-TIMELINE.md`、`docs/quality/M2-BUGS.md`、交接、README/ROADMAP/DECISION-LOG/13-OPEN-DECISIONS；
- 已知限制与 backlog 登记完整。

## 13. 实施记录（2026-11-12）

### 13.1 结果

| 项 | 结果 |
|---|---|
| 后端 | `mvn -B -ntp -f services/domain/pom.xml verify` → **BUILD SUCCESS，62 tests**（M1 48 不回归 + 需求 6 + 方案 7 + MCP 1） |
| 迁移 | `V4__m2_requirements.sql`，V1–V4 幂等，23 张业务表 |
| 前端 | build 通过；`npm test` → **25 tests** |
| 浏览器 E2E | 空数据根 + `npm run test:e2e` → **2 passed（8.8s）** |
| API 演示 | `node scripts/demo-m2.mjs` → **26/26** |
| 证据 | `docs/evidence/M2-VERIFICATION.md`、`docs/process/M2-TIMELINE.md`、`docs/quality/M2-BUGS.md` |

### 13.2 实现位置

- 后端：`services/domain/src/main/java/com/mammoth/domain/workitem/`（WorkItem/状态机/11 维目录/就绪引擎/评估/证据/快照）、`.../solution/`（版本/设计项/任务/验收/一致性/渲染/审批）、`.../web/WorkItemController.java`、`.../web/SolutionController.java`、`.../mcp/McpToolService.java`、`support/JsonLite.java`、`db/migration/V4__m2_requirements.sql`。
- 前端：`apps/web/src/pages/RequirementsPage.tsx`、`RequirementDetailPage.tsx`、`SolutionPage.tsx`；`lib/api.ts`、`lib/format.ts`、`lib/permissions.ts`；`App.tsx` 路由；`e2e/m1-ui.spec.ts` 新增 M2 场景。
- 演示：`scripts/demo-m2.mjs`、`docs/demos/M2-DEMO.md`。

### 13.3 与计划的偏差（均已记录在文档）

1. **就绪状态增加 `partial`**：为表达"部分覆盖"，但仍视为未确认；`07` §4 的四种状态为其子集。
2. **`approved` 可经 `new-version` 回到 `analyzing`**：满足"批准后修订"，批准版本本身仍不可编辑。
3. **设计项支持混合层级**：技术项可挂在业务目标下，使"目标→设计项→任务→验收"由树结构表达（承接 `SolutionConsistency` 规则 4）。
4. **E2E 单文件两测试**：CI 单后端空数据根约束下，M2 场景复用 M1 账号；单独运行会 `test.skip`（见 `docs/evidence/M2-VERIFICATION.md` §2）。
5. **MCP 工具落地**：`get_requirement_context` 只读实现并测试；未在本轮真实 DSH 会话复测。

### 13.4 未完成/后续

- 应用内 LLM 与 Agent 草案写入（ADR-0011 待立）；
- 需求评论/讨论、通知、后端分页；
- 真实 DSH 会话复测 M2 工具、M2 截图与人工视觉验收；
- ADR-0009 仍未定案；ADR-0007/0008 已于 2026-11-13 定案，相关实现归 M3（见 `docs/12-ROADMAP.md` M3 前置）。
