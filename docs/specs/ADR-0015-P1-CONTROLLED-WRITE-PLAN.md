# ADR-0015 P1 实施计划：受控写操作（V11 迁移 + 提案/审批/写入 API）

- 状态：**Plan（待负责人批准启动实施）**
- 日期：2026-11-16
- 依据：`decisions/ADR-0015-AGENT-CONTROLLED-WRITE-AND-SERVICE-IDENTITY.md`（Accepted v1.0，2026-11-16 负责人确认决策项 1–5）
- 范围：仅 P1（Domain 数据模型/迁移/API/权限/审计/负向测试）。P2 Approval 服务独立身份、P3 Bundle 改造、P4 负向验收、P5 远端（M5）另附。
- 非目标：不改现有业务表语义；不实现远端授权服务；不删除 Spike 适配器（P3 再替换）；不引入外部依赖（除非单独批准）。

## 1. 目标与验收（P1 退出标准）

1. Domain 新增提案/审批/写入数据模型（V11 迁移，可回滚）与 API；Agent 侧只能 propose。
2. 人类审批在 Domain 授权会话中完成；**自批 403**；**高风险类别缺第二人 403**；批准确认后 `apply` 以服务身份执行写入；重复 apply 幂等。
3. 审计三元组（propose → approve(+reviewer) → apply）完整、append-only、拒绝路径也留痕。
4. 负向测试全部通过（见 §7）；既有测试不回退；`mvn test` 全绿。
5. 现有 SPA 行为不变；MCP 只读不变；Spike 适配器仍可用（P3 前不强制删除）。

## 2. V11 数据模型（草案）

### 2.1 `agent_proposal`

| 列 | 类型 | 说明 |
|---|---|---|
| id | TEXT PK (uuid) | 提案 id |
| project_id | TEXT NOT NULL FK projects | 项目作用域 |
| proposal_type | TEXT NOT NULL | 如 `work_item.create` / `work_item.update`（v1 先只读集合） |
| payload_json | TEXT NOT NULL | 目标对象/字段变更（schema 校验） |
| summary | TEXT | 人类可读摘要（UI 列表） |
| proposer_kind | TEXT NOT NULL | `agent`（v1 固定） |
| proposer_user_id | TEXT | agent 服务/账号标识 |
| agent_run_ref | TEXT | DSH session/run 关联（过渡；V11 不含完整 agent_run 表） |
| idempotency_key | TEXT NOT NULL UNIQUE | propose 幂等 |
| status | TEXT NOT NULL | `pending`/`approved`/`rejected`/`applying`/`applied`/`failed`/`expired` |
| risk_level | TEXT NOT NULL | `normal`/`high`（决定是否需要第二人） |
| created_at / decided_at / applied_at | TEXT | 时间线 |
| failure_reason | TEXT | 失败原因（apply 失败时） |

索引：`(project_id, status)`、`(proposer_user_id)`、`(idempotency_key)`、`(created_at)`。

### 2.2 `agent_proposal_approval`

| 列 | 类型 | 说明 |
|---|---|---|
| id | TEXT PK | 记录 id |
| proposal_id | TEXT NOT NULL FK agent_proposal | 提案 |
| approver_user_id | TEXT NOT NULL | 人类账号（Domain 会话） |
| decision | TEXT NOT NULL | `approve`/`reject` |
| reason | TEXT | 理由 |
| reviewer_role | TEXT | `primary`/`second`（高风险第二人） |
| human_session_ref | TEXT | Domain 会话/请求指纹（不含凭据明文） |
| decided_at | TEXT NOT NULL | 时间 |

唯一约束：`(proposal_id, reviewer_role)`；约束：`approver_user_id <> proposer_user_id`（应用层校验 + 审计）。

### 2.3 `agent_write_audit`

| 列 | 类型 | 说明 |
|---|---|---|
| id | TEXT PK | 记录 id |
| proposal_id | TEXT NOT NULL | 关联提案 |
| stage | TEXT NOT NULL | `propose`/`approve`/`reject`/`apply`/`deny` |
| actor_kind | TEXT | `agent`/`human`/`service` |
| actor_user_id | TEXT | 身份 |
| outcome | TEXT | `ok`/`denied:reason`/`failed:reason` |
| request_fingerprint | TEXT | 请求内容哈希（含幂等键） |
| domain_object_id | TEXT | apply 成功后对象 id |
| prev_hash / entry_hash | TEXT | 应用级哈希链（防篡改） |
| at | TEXT NOT NULL | 时间 |

### 2.4 状态机

```
pending --approve(primary)--> approved --apply--> applied
pending --reject------------> rejected
pending --expire------------> expired
approved --apply fail-------> failed (可重试回 approved? v1: 仅记录失败，人工重新触发 apply)
```

高风险（`risk_level=high`）：`pending --approve(primary)--> awaiting_second --approve(second)--> approved`；v1 可用两阶段字段（`approval_required_roles`）实现，表结构见 §2.2 的 `reviewer_role`。

## 3. API 草案（最终以 OpenAPI 为准）

| 方法/路径 | 身份 | 说明 |
|---|---|---|
| `POST /api/agent/proposals` | Agent 服务身份（MCP/Host 代理，只读+propose 身份） | 创建提案（幂等键）；payload schema 校验；项目成员校验；拒绝非白名单 proposal_type |
| `GET /api/agent/proposals?projectId=` | 项目成员（人类） | 列表（工作台用）；只返回提案记录，不含原始凭据 |
| `POST /api/agent/proposals/{id}/approve` | 人类会话（Domain RBAC） | 批准；自批 403；高风险要求第二人（`reviewer_role=second`）；返回状态 |
| `POST /api/agent/proposals/{id}/reject` | 人类会话 | 拒绝（理由必填） |
| `POST /api/agent/proposals/{id}/apply` | Approval 服务身份（本地独立进程凭据） | 校验 approved + 人类批准记录 + 四眼满足 + 幂等；调用内部写服务；返回 domainObject |
| `GET /api/agent/proposals/{id}/audit` | 审计角色/项目 owner | 三元组审计链（只读） |

错误码（示例）：400 校验失败；401 未认证；403 越权/自批/四眼不足/非成员；404 不存在；409 状态冲突/重复幂等键；422 高风险缺第二人。

## 4. 权限与四眼规则（v1）

- propose：Agent 服务身份 + 项目成员（viewer 级别即可 propose；由 Domain 校验）。
- approve：项目 owner/reviewer 人类账号；**批准者 ≠ 提案者**。
- 高风险 v0 类别（`risk_level=high`，可配置）：方案定版/批准、交付结项、批量/删除类操作、需求状态重大流转、真实客户数据导出/外发类写入。
- 高风险：primary 批准后进入 `awaiting_second`；必须由**另一人类账号**（owner/reviewer 且 ≠ primary）批准后才可 apply。
- DSH 工具审批（`ctx.approval`）保留为 Client 侧附加闸门，Domain 不读取 DSH 内部状态。

## 5. 服务身份与凭据（P1 接口预留，P2 落地）

- P1 先定义"调 `apply` 的服务身份"抽象（本地进程凭据校验器），具体凭据存储/轮换在 P2 实现。
- P1 期间允许 Spike 适配器以显式 `X-Mammoth-Spike-Adapters` 兼容模式调用（仅测试 profile），不得进入产品默认配置。
- 生产默认：独立 OS 账号 + OS 凭据存储（Windows 凭据管理器/DPAPI）；DSH Host/Client 不持有写凭据。

## 6. 审计与幂等

- 每次 propose/approve/reject/apply/deny 写一条 `agent_write_audit`；`entry_hash = H(prev_hash || 关键字段)`。
- propose 幂等键：相同键重复返回既有提案（200 + 既有记录）。
- apply 幂等键：可用 `proposal_id` 天然幂等；`applying/applied` 状态重复 apply 返回既有 domainObject。
- 审计查询只读、可分页导出；不得包含凭据明文。

## 7. 负向测试矩阵（P1 必须全绿）

| # | 场景 | 期望 |
|---|---|---|
| N1 | Agent 账号调 approve/apply | 401/403 + 审计 deny |
| N2 | Agent 账号直写业务表 | 401/403（无权限）|
| N3 | 提案者自批 | 403 + 审计 deny |
| N4 | 非成员项目 propose/approve | 403 |
| N5 | 高风险仅 primary 批准后 apply | 403（四眼不足）+ 不写入 |
| N6 | 高风险 primary+second 批准后 apply | 成功 + 审计三元组 |
| N7 | 重复 propose 相同幂等键 | 返回既有提案，不重复创建 |
| N8 | 重复 apply | 返回既有结果，不重复写入 |
| N9 | 拒绝路径 | rejected + 审计，无写入 |
| N10 | DSH 审批 never | 不影响 Domain 审批要求 |
| N11 | 审批越权角色（viewer） | 403 |
| N12 | payload/type 白名单外 | 400/403 |

## 8. Bundle / UI 变更（P3，不在 P1 实施）

- Host 工具：`mammoth_propose_work_item` 改为调用 Domain `POST /api/agent/proposals`；删除可直接 approve 的路径。
- 工作台：待审核提案页调用 Domain API（人类会话）；批准按钮走人类授权；高风险提示第二人；技术信息折叠。
- 保留 Spike 适配器仅用于对照测试；P3 验收后删除写路径代码。

## 9. 里程碑切片（建议 4 个 PR/提交）

1. V11 schema + 迁移 + 模型/仓储 + 状态机单测；
2. propose/list API + 幂等 + 白名单 + 审计；
3. approve/reject API + 四眼/高风险规则 + 自批判定 + 审计；
4. apply API + 服务身份抽象 + 幂等 + 负向测试矩阵 + 文档/迁移说明。

## 10. 风险与回滚

- 风险：四眼规则与现有角色模型交互复杂；V11 迁移影响现有 audit 查询；服务身份抽象若与 P2 冲突需返工。
- 回滚：Flyway undo/反向迁移脚本（或迁移前备份 + 恢复演练）；关闭新 API 路由后 SPA/现有业务不受影响；Agent 回到只读。
- 兼容：现有 `audit_log` 保持；新审计独立表，可并存。

## 11. 待确认（启动实施前）

1. 是否按 4 个切片提交（推荐）？
2. 高风险 v0 类别是否需要调整（方案定版、交付结项、批量/删除、需求状态重大流转、真实客户数据导出/外发）？
3. P1 是否允许在独立分支/Worktree 实施（推荐 `feature/adr-0015-p1-controlled-write`），不直接改 main？

## 12. 实施状态（2026-11-16，切片 1–4 完成）

| 切片 | 状态 | 内容 |
|---|---|---|
| 1 schema+迁移 | **完成** | V11 三表（proposal/approval/write-audit，含 `seq`、`domain_object_id`）、JPA 实体/仓储、状态机、审计哈希链、迁移/备份断言、`MIN_SCHEMA_VERSION=11`、交接包表清单 |
| 2 propose/list | **完成** | `POST /api/agent/proposals`（服务 token + 配置 agent 身份、项目成员、类型白名单、payload 校验/规范化、幂等、风险服务端派生、propose 审计、deny 审计）；`GET /api/agent/proposals` 人类会话 |
| 3 approve/reject+四眼 | **完成** | `POST /{id}/approve|reject`（人类会话）；owner/editor 可批准并执行写入，owner/editor/reviewer 可拒绝；提案者≠批准者；高风险第二人须不同人类；批准/拒绝/拒绝行审计 |
| 4 apply+负向矩阵 | **完成** | `POST /{id}/apply`（服务 token，幂等）；校验批准记录/四眼；以**批准人**身份调用 `WorkItemService.create`（Domain 审计 `createdBy`=人类）；失败保留 `failed` 状态与审计；负向矩阵见 §7 与测试 |

**实现与计划的偏差/决策（已记录）**

1. P1 服务身份仍用本机服务 token + 配置的 agent 账号（ADR-0015 P2 替换为独立 OS 身份/凭据存储）。
2. 批准执行写入要求 owner/editor（apply 复用现有 `WorkItemService.create` 的角色校验）；reviewer 可拒绝。P2/P3 引入专用服务写身份后可放宽。
3. 高风险类别已在代码常量中实现（方案定版/交付结项/删除与批量/需求重大流转/数据导出），P1 仅开放 `work_item.create`（normal）；其余类型待逐个实现 payload schema 与 apply 写入。
4. 审计链按每提案单调 `seq` 排序（避免 ISO 秒级时间戳的同秒排序歧义）。
5. apply 的状态迁移拆为独立事务（markApplying/completeApplied/completeFailed），Domain 写入失败不会回滚掉失败证据。
6. 测试：域服务全量 **160 tests** 通过（含新增 agentwrite 39 例：状态机/哈希/隔离/幂等/payload/成员/角色/四眼/失败态/审计链/接口鉴权）。

**后续（P2–P4 + P3 集成）**：P2 OS 身份与凭据隔离；P3 DSH Bundle/工作台接入新 API 并删除 Spike 适配器写路径；P4 负向安全验收（含真实进程/OS 权限验证）；`work_item.update` 等类型、分页/限流、审计查询 API、迁移回滚演练。

> **P2 进展（2026-11-16）**：Domain 双 token 分离（propose/apply）+ 审批服务队列端点 + Approval 服务 CLI（单提案/watch）+ 管理员 provisioning/verify 脚本 + 测试已完成（域服务 168 tests 通过）。OS 账号/ACL/计划任务的实际创建需负责人以管理员执行，详见 `docs/specs/ADR-0015-P2-APPROVAL-SERVICE.md` §3/§4。
