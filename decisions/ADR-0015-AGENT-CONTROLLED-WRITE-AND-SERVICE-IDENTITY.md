# ADR-0015: Agent 受控写操作与服务身份（Proposal → 人工审批 → Domain 写入边界）

- 状态：**Proposed（2026-11-16 负责人批准另立并起草；决策内容待负责人批准）**
- 日期：2026-11-16
- 决策人：项目负责人（批准另立；决策项待批准）
- 相关需求/功能：客户端架构阶段（ADR-0014 Accepted v1.1）后的正式写路径与安全边界
- 相关文档：`decisions/ADR-0014-CLIENT-SHELL-AND-UI-EXTENSION.md`、`decisions/ADR-0001-DSH-INTEGRATION.md`、`decisions/ADR-0006-IDENTITY-AUTHORIZATION.md`、`decisions/ADR-0008-WORKSPACE.md`、`decisions/ADR-0011-EXTERNAL-INTEGRATION.md`、`decisions/ADR-0012-TEAM-SERVER.md`、`docs/evidence/2026-11-13-client-arch/spike-A/SPIKE-REPORT.md`（§8/§10/§15）、`docs/08-SECURITY-AND-TRUST.md`

## 背景与约束

1. **ADR-0014 已 Accepted v1.1**：Mammoth 客户端形态为 DSH Desktop 宿主 + 外部 Bundle（Host/Client/MCP/Preset），Agent 以对话为主入口、工作台承载业务页面。**业务权威在独立 Domain Service**。
2. **Spike A 已验证机械链路**：Agent 工具只能创建待审核提案；DSH 工具审批可 fail-closed/`allowed-once`；负责人在工作台亲自批准后，隔离适配器以人类 Domain 账号调用现有 Domain 写端点，产生 `REQ-0002`（自动化链路）与 `REQ-0003`（真人批准）并留审计。
3. **Spike A 暴露的未闭合边界（本 ADR 要解决）**：
   - 临时 Proposal 适配器与 Agent Runtime **同一 OS 用户**，其令牌与人类 Domain 凭据对"能执行任意 shell 的 Agent"可读 → 适配器**不是安全边界**；
   - Domain Service 目前**没有 Agent 可用的 propose/审批/写 API**，Agent 账号被设计为不可登录、只读；
   - "四眼/双人审批"在 Spike 中仅以"提案者=agent 身份、批准侧=人类账号"近似，Domain 未强制双人；
   - 人工批准是 DSH 工作台按钮 + 适配器的本地断言，缺少可验证的人类身份/职责记录与不可篡改审计链；
   - 凭据仍放在仓库外本机文件/进程环境（Spike 例外），不满足 ADR-0014 §5 的目标形态（DSH `ctx.credentials`/OS 存储）。
4. **既有约束**：不 Fork DSH、不改 DSH 上游；Domain Service 保持权威；不把业务逻辑写入 Client；Agent 不得直连数据库或绕过 Domain 校验；不把 DSH 权限当作 Mammoth 业务 RBAC。

## 需要解决的问题

1. Agent 的"写"能力如何被限制为 **propose-only**，且无法自批、无法直写？
2. 人类审批如何成为**可验证、可审计、不可绕过**的 Domain 行为，而不是客户端/适配器的一行断言？
3. 服务之间（DSH Host / Approval 服务 / Domain Service）的**身份与凭据**如何隔离，使 Agent 的 shell 权限无法取得写权限或人类凭据？
4. "四眼"如何落地（提案者 ≠ 批准者；高风险变更是否要求第二人）？
5. 审计如何做到提案、批准、写入三元组可追溯且防篡改？如何与现有 `audit_log`/MCP 审计共存？
6. 本地单机与团队模式（ADR-0012/M5）如何共用同一模型？

## 备选方案

### 方案 A（推荐）：独立 Approval 服务身份 + Domain 受控写入 API

- **信任域拆分**：Agent Runtime（只读 + propose） / **Approval 服务**（独立进程与 OS 身份，持有服务凭据，执行"已批准提案"的写入） / Domain Service（权威、权限、审计）。
- Domain 新增**提案与审批 API** 与数据模型（`agent_run`、`proposal`、`proposal_approval`、`agent_write_audit`）：Agent 只能 `propose`（只写提案表）；批准由人类在 Domain 授权下完成；仅"已批准 + 人类批准记录"的提案可触发正式写。
- 人类审批使用**用户自己的 Domain 会话/RBAC**（UI 登录态），Approval 服务不得伪造人类身份，只校验/执行已批准结果；服务身份与人类身份分离。
- 审批服务以**独立进程/OS 账号**运行，凭据走 OS 凭据存储（Windows DPAPI/凭据管理器），DSH Host 与 Client 不接触写凭据；Agent shell 无法读取。

### 方案 B（最小边界）：保持只读，人工在 Domain 官方 UI 写

- Agent 与 MCP 全部只读；正式写入仍由人工在现有 SPA（或未来管理台）完成。
- 优点：零新写 API、边界最清；缺点：Desktop 工作台无法完成"批准即写入"的闭环，产品体验退化。

### 方案 C（团队/长期）：远端授权服务 + mTLS/委派令牌

- 面向 M5+ 团队部署：Approval/写服务独立部署，使用 mTLS 或 OAuth 风格委派令牌；本机模式复用 A 的接口。
- 作为 A 的演进形态，不作为第一阶段实现。

## 决策（拟采纳，待负责人批准）

> 采用 **A 为主、C 为演进形态**；B 作为回退选项（若 A 的 Domain API 变更未获批准）。以下为**待批准的决策项**。

1. **权限模型（propose-only）**
   - Agent 只能创建 `proposal`（草稿），**没有任何** approve/写业务表权限；Agent 账号继续不可登录（仅服务/MCP 只读身份）。
   - 只有"人类批准记录 + Domain 授权校验"通过的提案，才能被 Approval 服务/ Domain 内部执行器写入正式业务表。
2. **Domain 受控写 API 与数据模型（V11 迁移，另立迁移方案）**
   - 新增统一提案入口（示例，最终以 OpenAPI 为准）：`POST /api/agent/proposals`（agent 服务身份或 Host 代理）、`GET /api/agent/proposals`、`POST /api/proposals/{id}/approve|reject`（人类会话，强制四眼）。
   - 写入执行的唯一入口：`POST /api/proposals/{id}/apply`（仅 Approval 服务身份；校验 approved 状态 + 人类批准记录 + 幂等键）。
   - 表：`proposal`（类型/目标/载荷/状态/幂等键/提交者）、`proposal_approval`（批准人/时间/决定/理由）、`agent_write_audit`（提案→批准→写入三段关联、请求指纹、结果、失败原因）。
   - 迁移与回滚：独立 Flyway 版本；旧 `audit_log` 保留；不允许 Agent 直接写业务表。
3. **服务身份与凭据隔离**
   - Approval 服务以**独立 OS 账号/独立进程**运行（本地单机模式），持有服务凭据（OS 凭据存储；禁用明文配置文件）；写凭据**不进入** DSH Host、Client、提示词、仓库、日志、截图。
   - Agent Runtime 与 Approval 服务的凭据分离；同用户 shell 不得读取写凭据（以 OS 身份隔离/ACL 验证）。
   - 凭据轮换与吊销：服务凭据可轮换；失败关闭（fail-closed）。
4. **人类独占审批与四眼**
   - 审批动作只能由**人类**在 Domain 授权会话中完成（DSH 工作台按钮 → 服务端校验人类会话/RBAC；不得由 Client/Agent/适配器代为断言）。
   - 规则：批准者 ≠ 提案者（proposal 的提交身份）；高风险变更（可配置类别）要求第二人复核；自批一律 403 并审计。
   - DSH 工具审批（`allowed-once`）保留为**附加人工闸门**，但不作为 Mammoth 业务权限来源。
5. **审计**
   - 提案、批准、写入三段记录同一关联链，append-only；包含身份（人类/服务/agent）、时间、请求指纹、结果。
   - 关键拒绝路径（自批、越权、重放、凭据失败）必须留审计；审计查询只读、可导出。
6. **幂等/重放/失败恢复**
   - 提案与 apply 均带幂等键；重复 apply 返回既有结果；部分失败可重试但不得重复写入。
   - 写入失败保留 approved 提案状态并记录失败原因，人工可重新触发或拒绝。
7. **回退边界**
   - 若本 ADR 决策未获批准或 V11 迁移风险过高：回退方案 B（Agent 只读 + 人工在 Domain 官方 UI 写），Desktop 工作台仅展示提案与跳转，不执行写。

## 威胁模型与缓解（摘要）

| # | 威胁 | 缓解 |
|---|---|---|
| T1 | Agent shell 读取适配器/服务凭据并调用写端点 | 写服务独立 OS 身份 + 凭据仅 OS 存储；Agent 进程不可读；写入需人类批准记录 |
| T2 | Agent 伪造"人类批准"或自批 | 审批必须有 Domain 人类会话/RBAC；服务端四眼；自批 403 + 审计 |
| T3 | Client/Host 插件被篡改后直接写 | Domain 只信任人类会话/服务身份，不信任插件断言；写入口仅 Approval 服务 |
| T4 | 重放已批准请求 | 幂等键 + 提案一次性状态机 + 绑定批准记录 |
| T5 | 审计篡改/缺失 | append-only 审计链（应用级哈希链/不可变存储），拒绝路径也记录 |
| T6 | 越权提案（非成员项目） | Domain 在 propose/approve/apply 三阶段都校验项目成员与角色 |
| T7 | 凭据泄漏到日志/截图/仓库 | 凭据不落明文；日志脱敏；负向测试 |
| T8 | DSH 审批策略被设为 never 后绕过 | 业务审批独立于 DSH 策略；提案状态机要求人类批准记录，DSH 只是附加闸门 |

## 接口与状态机（草案，最终以 OpenAPI/迁移方案为准）

```
proposal: draft -> submitted(待审) -> approved(rejected) -> applying -> applied | failed
                                    \-> cancelled/expired
approval: {proposalId, approverUserId, decision, reason, decidedAt, humanSessionTrace}
apply:    需要 approved + approver≠proposer (+ 高风险第二人)；幂等键；返回 domainObject
```

## 实施阶段（建议）

1. **P0 决策与规格**：负责人批准本 ADR 决策项；产出提案/审批/写入 OpenAPI 草案与威胁模型评审。
2. **P1 Domain 数据与 API（V11 迁移）**：新增表/状态机/权限/审计；负向测试（agent 不能 approve/写、自批 403、非成员 403、重放幂等）。
3. **P2 Approval 服务（独立身份）**：本地进程 + OS 凭据；凭据隔离验证（同用户 shell 不能读）；健康/轮换。
4. **P3 DSH Bundle 改造**：Host 工具仅 propose；删除 Spike 适配器写路径；工作台审批走 Domain API（人类会话）；提案页按 UI-SPEC 重做信息层级。
5. **P4 验收与回归**：正向（真人批准→写入）、负向（Agent/同用户 shell 无法写/自批）、审计三元组、回滚演练。
6. **P5 团队模式**：按 ADR-0012/M5 评估远端 Approval 服务（方案 C）。

## 验收标准

- 正向：Agent propose → 人类在 Domain 授权下批准 → 正式写入；`proposal_approval` 有批准人；`agent_write_audit` 三元组完整；业务表写入记录 `createdBy=人类账号`。
- 负向（必须全部通过）：
  - Agent 账号/工具无法 approve、无法直写业务表（401/403 + 审计）；
  - 同 OS 用户的 Agent shell 无法读取写服务凭据（凭据 ACL/OS 存储验证）；
  - 提案者自批 403；高风险无第二人复核 403；
  - 重放/重复 apply 不重复写入；越权项目 403；DSH 策略 `never` 不改变业务审批要求。
- 回滚：关闭 Approval 服务/回退 V11 迁移后，Agent 恢复只读，Desktop 工作台退回"提案展示 + 人工在 SPA 写"。

## 取舍和影响

**正向**：形成可审计、可验证的受控写边界；服务身份与人类身份分离；团队模式可复用；符合 ADR-0001 边界扩展要求。
**成本/风险**：Domain API 与数据模型变更（V11）、Approval 服务运维、OS 凭据与 ACL 验证、四眼策略的产品配置；迁移与文档成本。
**兼容性**：现有 SPA/API 保持；MCP 只读不变；ADR-0001 §6.3 的本 ADR 需明确是否修订（拟：保留 MCP 只读边界，新增受控写 API 并限定服务身份）。

## 需要负责人确认的开放问题（批准前）

1. **服务身份形式**：本机独立 OS 账号/独立进程（推荐）vs 只用回环令牌 vs 远端授权服务先做？
2. **四眼范围**：所有正式写都要求"提案者≠批准者"即可，还是高风险类别（如方案定版、交付结项）额外要求第二人？
3. **人类审批身份来源**：使用 Domain 登录会话/RBAC（推荐）还是绑定本机 OS 用户（单机便捷但团队不可复用）？
4. **DSH 工具审批定位**：是否保留为附加闸门（推荐）还是仅依赖 Domain 审批？
5. **团队/远端模式优先级**：是否随 M5 一起做（方案 C），还是先完成本机 A 后再演进？

## 关联与后续

- 本 ADR 批准后：更新 ADR-0001（§6.3 边界扩展说明）、ADR-0014（写路径条件项）、`docs/05`、`docs/08`、`docs/13`、V11 迁移方案与 M5 计划。
- 在批准前，Agent 的正式写入仍仅限 Spike 隔离环境，不得进入产品代码/真实数据。
