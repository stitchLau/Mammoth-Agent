# M1 Phase 1 计划 — 项目知识底座（第一垂直切片）

- **状态**：Draft（待负责人批准范围与选型）
- **日期**：2026-11-12
- **前置**：ADR-0001 Accepted（`decisions/ADR-0001-DSH-INTEGRATION.md` §6）；S2 MCP 边界已验证
- **对应路线图**：`docs/12-ROADMAP.md` M1
- **依据**：`docs/03-DOMAIN-MODEL.md`、`docs/04-MVP-STRATEGY.md`、`docs/06-KNOWLEDGE-AND-RAG.md`、`docs/08-SECURITY-AND-TRUST.md`、`docs/09-QUALITY-AND-TESTING.md`

## 1. 目标与非目标

### 目标（M1 验收的最低完整闭环）

用**一个合成/脱敏项目**完成：建立组织/客户/产品/项目档案 → 导入原始资料（Artifact，不可变）→ 生成并人工审核知识文档（KnowledgeDocument）→ 按项目 scope 检索 → 返回带可核验引用的答案 → **跨项目/越权检索被拒绝**。

### 非目标（本阶段明确不做，进入 backlog）

- 真实客户数据、生产凭据、生产环境；
- 完整逆向工程（代码/数据库自动扫描）——M1 后续切片；
- 向量检索的生产化与调优（先用可替换接口 + 全文检索）；
- 多租户组织隔离深度、SSO、企业目录；
- CSM/Jira/MES 集成、外部 Agent 调度；
- 工作项/需求/方案能力（M2）。

## 2. 界定的端到端演示脚本（Slice-1）

1. 启动 Mammoth 本地服务；浏览器打开 Mammoth UI（独立页面，不嵌入 DSH 客户端）。
2. 创建组织 → 客户 `ACME` → 产品 `MES` → 项目 `ACME-MES-2026`（绑定客户与产品）。
3. 上传 3 份合成资料（Markdown/TXT）：需求纪要、现场流程说明、历史问题清单。
4. 系统留存 Artifact（哈希、来源、项目 scope、上传人、时间），内容不可被后续步骤覆盖。
5. 生成 2 条知识文档（类型：业务流程、已知问题），状态 `draft`。
6. 审核人将其中 1 条发布为 `verified`，另 1 条保持 `needs_review`。
7. 提问："ACME-MES-2026 的现场收货流程有哪些异常处理？" → 返回答案 + 引用（文档 ID、片段、来源 Artifact、版本）。
8. 用无项目权限的第二用户重复检索 → **403 / 空结果 + 审计记录**。
9. DSH 会话内通过 MCP 工具 `search_project_knowledge` 请求同一问题 → 返回同样的引用结构（Agent 只能走受控工具）。

## 3. 架构基线（在 ADR-0001 之上）

```
[浏览器 SPA]  --HTTPS/JSON-->  [Mammoth Domain Service (Java)]
                                     ├─ Domain 层（聚合/规则/状态机）
                                     ├─ Application API（REST + 授权 + 审计）
                                     ├─ Persistence（关系库 + 迁移）
                                     ├─ Artifact Storage（本地不可变目录）
                                     ├─ Search Adapter（全文优先，向量可插拔）
                                     └─ MCP Adapter（stdio JSON-RPC，暴露只读 Agent 工具）
[DSH desktop profile] --MCP(stdio)-->  MCP Adapter（配置型 bundle，无 Host 代码）
```

关键约束：

- **UI 与 Domain Service 独立**（Runtime Independence）：M1 UI 不由 DSH client plugin 承载；DSH 仅作 Agent Runtime 与 MCP 客户端。
- **数据根目录独立于 `~/.dsh`**（安全红线）：默认 `<Mammoth 数据根>`（待定，建议 `%LOCALAPPDATA%\Mammoth`），原始资料按内容哈希存储，业务元数据入关系库。
- **Agent 只通过 MCP 工具读知识**，不得直连数据库；写操作在 M1 由人工 UI 完成。
- 会话记录不是知识：知识必须经过 `KnowledgeDocument` 状态机与人工审核。

## 4. 最小数据模型（V1 迁移草案）

| 表 | 关键列 |
|---|---|
| `organization` | id, name, created_at |
| `app_user` | id, org_id, email/username, display_name, status, password_hash/凭据引用 |
| `role_assignment` | id, user_id, role, scope_type(org/project), scope_id |
| `customer` | id, org_id, name, status |
| `product` | id, org_id, name, status |
| `project` | id, org_id, customer_id, code, name, status, owner_user_id |
| `project_product` | project_id, product_id |
| `project_member` | project_id, user_id, role |
| `artifact` | id, project_id, kind, original_name, content_hash, storage_path, size, mime, source_note, uploaded_by, created_at（不可变） |
| `knowledge_document` | id, scope_type, scope_id, doc_type, title, body, status(draft/needs_review/verified/superseded/deprecated), version, created_by, reviewer, reviewed_at |
| `knowledge_source_ref` | knowledge_document_id, artifact_id, locator(页/章节/片段偏移), note |
| `knowledge_chunk` | id, knowledge_document_id, ordinal, text, index_version（派生，可重建） |
| `audit_log` | id, actor_user_id, action, object_type, object_id, at, detail_json |

> ERD 与聚合边界在 ADR-0003 前用一次设计评审固定；列名可调整，但 `scope`/`source_refs`/`status` 语义必须保留。

## 5. 必须先定的 ADR / Spike（顺序即建议执行顺序）

| 顺序 | ADR | 需要决定 | Spike 方法（最小） |
|---|---|---|---|
| 1 | **ADR-0002 技术栈** | Java 框架（Spring Boot / Quarkus / 纯 JDK）、构建工具、UI 形态与前端栈 | 两个候选各起最小服务，验证 REST + MCP stdio 同进程/同工程可行性 |
| 2 | **ADR-0003 数据库/迁移** | PostgreSQL vs SQLite（单机优先）、迁移工具（Flyway/Liquibase）、访问层（JPA / jOOQ / JDBC） | 建 3 表 + 迁移往返 + 中文全文检索对比；备份恢复演练 |
| 3 | **ADR-0004 Artifact Storage** | 本地内容寻址目录、命名与去重、配额、备份 | 上传/哈希/去重/只读校验最小实现 |
| 4 | **ADR-0006 身份授权** | 单机最小认证（本地账号/token）、角色矩阵、项目级授权 | 两个用户 + 一个越权用例 |
| 5 | **ADR-0005 RAG** | 全文 vs 向量、中文分词、切分与索引版本 | 10 份样例文档的召回对比；接口抽象与重建 |

未完成上述 ADR 前，不进入 M1 Slice-1 编码（Spike 可以编码，但必须是临时代码）。

## 6. 任务拆解（Slice-1，草案）

| 任务 | 内容 | 验收 |
|---|---|---|
| T1 工程骨架 | Java 17 + 构建工具 + 目录约定（`apps/`、`services/`、`docs/`）+ `.env.example` + 最小 CI（build+test） | 一条命令构建与测试通过；CI 绿 |
| T2 配置与数据根 | 配置化数据根（禁止 `~/.dsh`）、启动自检 | 冷启动创建目录；误配到 DSH home 时启动失败 |
| T3 迁移基线 | Flyway V1 建第 4 节表 | 空库迁移→回滚演练；重复执行幂等 |
| T4 主数据与项目 | Customer/Product/Project 的 REST + 领域校验（项目必须绑定客户） | 创建/查询/约束违规用例 |
| T5 Artifact 导入 | 上传/登记、SHA-256、不可变、原始名保留 | 重复上传去重；篡改检测；元数据完整 |
| T6 知识文档 | draft→needs_review→verified 状态机、source_refs 必填 | 非法状态跳转被拒；AI 生成内容不能直接 verified |
| T7 人工审核与审计 | 审核动作记录 reviewer/at；所有写操作落 `audit_log` | 审计可追溯；不可篡改（追加写） |
| T8 检索 | scope + 权限过滤 + 全文检索 + 片段定位 | 3 份资料中命中目标片段；越权空结果 |
| T9 Agent 只读工具 | MCP `search_project_knowledge`、`get_project_facts`，输出含引用结构 | DSH 会话真实调用；引用可回到 Artifact/文档版本 |
| T10 权限隔离 | 越权检索、跨项目引用、未授权 Artifact 访问 | 403/空结果 + 审计；安全测试通过 |
| T11 演示与样例 | 合成样例项目 + 演示脚本 + 验收记录 | 第 2 节脚本可重复执行 |
| T12 文档与交接 | 更新产品/领域/架构文档、ADR、Session Handoff | DoD 通过 |

**Slice-1 最小子集建议**：T1→T2→T3→T4→T5→T6→T8→T9→T10→T11（T7 审计先以最小实现纳入 T4/T6）。其余进入 M1 后续切片。

## 7. 测试策略（来自 docs/09）

- Unit：领域约束（项目必须绑定客户）、状态机、权限判断、哈希/去重。
- Integration：迁移、关系库、Artifact 存储、检索。
- Contract：REST DTO、MCP 工具 schema、引用结构。
- E2E：第 2 节演示脚本自动化（除人工审核步骤外）。
- Security：跨项目检索、未授权文件、MCP 工具越权。
- Regression：固定样例项目的检索命中与引用结构回归。

## 8. 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 选型未定即编码 | 返工 | 先 ADR-0002/0003 Spike，Spike 代码不进主分支 |
| 单机 SQLite 后续迁移到 PostgreSQL | 迁移成本 | 访问层抽象 + Flyway，避免 SQLite 专有语法 |
| 中文全文检索质量 | 检索不可用 | ADR-0005 前用 10 份样例做召回对比 |
| 数据根目录误放 `~/.dsh` | 违反安全边界 | T2 启动自检 + 文档约束 |
| MCP 工具暴露面过大 | 越权/幻觉引用 | 只读工具 + 项目 scope 参数强制校验 + 引用必须回源 |
| 范围蔓延到 M2 | 阶段失控 | 本计划的非目标清单 + backlog 记录延期 |

## 9. 需要负责人批准的点

1. 本计划的范围与非目标（尤其"UI 独立于 DSH 客户端"）。
2. 执行顺序：先 ADR-0002 技术栈 Spike，还是先 ADR-0003 数据库 Spike。
3. Mammoth 数据根目录位置与命名（建议 `%LOCALAPPDATA%\Mammoth`）。
4. 样例项目来源（必须合成/脱敏）。
5. 是否允许在本仓库内直接建立 `apps/`/`services/` 产品代码目录（同一仓库决定已批准，但目录约定需确认）。

## 10. Definition of Done（M1 Phase 1）

- 第 2 节 9 步演示脚本可重复执行并通过；
- 迁移可重复、可回滚；备份恢复演练记录；
- 权限隔离与审计测试通过；
- MCP 工具在真实 DSH 会话中可用且引用可核验；
- 文档（SPEC/ADR/领域/架构）与交接记录已更新；
- 已知限制与 backlog 已登记。
