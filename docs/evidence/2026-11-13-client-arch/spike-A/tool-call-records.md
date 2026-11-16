# Spike A — Agent / MCP / Domain 调用记录（脱敏，合成数据）

时间均为 Asia/Shanghai；数据全部为合成数据。调用发生在真实安装的 DSH Desktop 会话
`session-0583b86e-1bcb-4ecb-93da-61b43d2dcc82`（"Desktop集成专项"，2026-11-16 09:32 启动的 DSH 实例）。

## 1. Host 插件工具（`@mammoth/dsh-bundle` 注册到 `ctx.tools`）

### 1.1 `mammoth_list_projects`

输入：`{ "limit": 20 }`

输出（render 文本，经 Host → Domain REST 读取）：

```
Mammoth projects (1):
- SPIKE1 Spike Demo Project [active] customer=Spike Demo Customer verifiedKnowledge=1 workItems=1 (active)
```

对应 Domain 请求：`GET /api/projects` + `/api/customers` + `/api/projects/{id}/members` +
`/api/projects/{id}/knowledge` + `/api/projects/{id}/work-items`（Host 侧 Node HTTP，无 CORS 限制）。

### 1.2 `mammoth_get_project_context`

输入：`{ "projectId": "a899ed37-7e3a-488c-9c1b-16c97bfe0b11" }`

输出：

```
Mammoth project SPIKE1 Spike Demo Project [active]
projectId: a899ed37-7e3a-488c-9c1b-16c97bfe0b11
customer: Spike Demo Customer
members: Spike Admin:owner, Spike Agent:viewer, Spike Reviewer:reviewer
knowledge: verified=1 draft=1
work items: 1 (draft=1)
verified knowledge titles:
- Spike Agent Knowledge Contract (architecture)
Use mcp__mammoth_domain__search_project_knowledge with projectId a899ed37-... to read knowledge.
```

副作用：若 Session 尚未绑定项目，则以 `tool` 原因绑定该项目（`host-plugin-state.json` 中
`boundAt: 2026-11-16T01:49:28Z`）。

### 1.3 `mammoth_propose_work_item`（负向路径：会话权限策略为 `never`）

输入：`{ projectId, title: "Spike 验收：新增需求冒烟", summary, priority: "high", rationale }`

输出：

```
No proposal was created (rejected_by_approval_policy): The DSH approval gate rejected this call,
so no proposal was created. Ask the user to review and retry.
```

结论：**fail-closed**。会话审批策略为 `never` 时，`ctx.approval.request()` 返回 `rejected`，Host 不创建提案、
不触达 Domain、不写任何数据。

### 1.4 `mammoth_propose_work_item`（正向路径：审批策略切到 `ask` 后）

同一输入（标题 `Spike 验收：新增需求冒烟（人工审批链路）`）。DSH 审批 UI 交给用户确认，返回
`allowed-once`；随后 Host 调用隔离 Proposal 适配器 `POST /proposals`。

输出：

```
Pending proposal 50b44ed0-7ffd-476f-8e1d-d6c2711ee43a created for SPIKE1 Spike Demo Project:
"Spike 验收：新增需求冒烟（人工审批链路）" (high).
No Domain Service write happened. A human must approve it in the Mammoth workbench (Proposals)
before the formal write.
```

Host 审计（`host-plugin-state.json` → `audit`）：

```json
{"action":"proposal.created","detail":{"proposalId":"50b44ed0-...","dshApproval":"allowed-once"}}
{"action":"proposal.approve","detail":{"proposalId":"50b44ed0-...","approver":"local-desktop-human","result":"approved"}}
```

## 2. MCP 工具（`@deepseek-ai/dsh-mcp-client` → 现有 Mammoth Domain MCP server，只读 stdio）

工具名验证（工作台诊断页）：`mcp__mammoth_domain__search_project_knowledge`、
`mcp__mammoth_domain__get_knowledge_document`、`mcp__mammoth_domain__get_requirement_context` —— **3/3 registered**。

### 2.1 `search_project_knowledge`

输入：`{ projectId: "a899ed37-...", query: "verified knowledge contract", topK: 3 }`

输出（截断）：

```json
[
  {"documentId":"8800bafc-a7a0-4d53-b2e0-ad657e06ad30","title":"Spike Agent Knowledge Contract",
   "chunkId":"8572ae6d-...","ordinal":0,"text":"# Spike Knowledge ...","score":-5.29,"sourceRefCount":1},
  {"documentId":"8800bafc-...","title":"Spike Agent Knowledge Contract","chunkId":"616adea1-...",
   "ordinal":1,"text":"... The agent proposal write path requires human approval before the domain API writes requirements. ...",
   "score":-0.46,"sourceRefCount":1}
]
```

第二次调用（`query: "agent proposal human approval"`）同样返回 2 条，用于截图 11 的自定义业务卡片。

### 2.2 `get_requirement_context`

输入：`{ projectId: "a899ed37-...", workItemId: "fa17c5d2-345d-4ea7-860d-14879e6b89c8" }`

输出（截断）：

```json
{"workItemId":"fa17c5d2-...","itemNo":"REQ-0001","title":"Spike synthetic requirement",
 "status":"draft","priority":"high","readinessOk":false,
 "blocking":["background: missing on a critical dimension","goals: missing on a critical dimension", "... 共 11 条"],
 "evidence":[],"assessments":[{"dimension":"background","critical":true,"state":"missing"}, "...11 维"]}
```

## 3. Domain Service 复核（Host 之外、独立人类账号）

### 3.1 审批后读取（见 `domain-after-approval.json`）

```
GET /api/projects/a899.../work-items  → 2 条
  REQ-0001 Spike synthetic requirement                       createdBy=3b3e0c00-...（spike_admin）
  REQ-0002 Spike 验收：新增需求冒烟（人工审批链路）            createdBy=3b3e0c00-...（spike_admin）
```

### 3.2 审计（`/api/audit/me`，spike_admin 会话）

```
at=2026-11-16T02:07:18Z actor=3b3e0c00-284d-4fdb-bd94-a0eb29400aac
  action=work_item.create object=work_item/c5145f6d-7abc-41a7-82c0-e4e6b194a7be
```

> `c5145f6d-...` 即 `REQ-0002`。写入发生在工作台人工批准（02:07:18Z）之后，由隔离适配器以
> `spike_admin` 人类会话调用 Domain 现有写端点完成；agent 账号 `fb228729-...` 不是 actor。

### 3.3 负向：agent 账号不能登录 Domain

```
POST /api/auth/login {"username":"spike_agent","password":"..."} → HTTP 401（预期；agent 账号永不发会话）
```

## 4. Proposal 适配器审计（`proposal-stub-audit.jsonl`）

```json
{"at":"2026-11-16T02:05:30.844Z","action":"proposal.created","detail":{"id":"50b44ed0-...","projectId":"a899...","proposer":"fb228729-d64c-46ec-9345-d69e966069f3"}}
{"at":"2026-11-16T02:07:18.021Z","action":"proposal.approved","detail":{"id":"50b44ed0-...","approver":"local-desktop-human","domainObject":{"id":"c5145f6d-...","itemNo":"REQ-0002","status":"draft"}}}
```

## 6. 整改阶段补充调用（2026-11-16 10:20–10:35）

### 6.1 MCP 非成员项目负向

输入：`search_project_knowledge { projectId: "11111111-1111-1111-1111-111111111111", query: "non member project negative test" }`

输出：

```
Error: ApiException: no access to project 11111111-1111-1111-1111-111111111111
```

真实会话中该结果由自定义卡片渲染为错误态（截图 14，未 blank slot）。结论：MCP 侧项目级权限校验生效。

### 6.2 DSH 审批策略 `never` 下提案 fail-closed

输入：`mammoth_propose_work_item { projectId: "a899ed37-...", title: "Spike 负向验证：策略 never 下提案应被拒绝", summary, priority: "low" }`

输出：

```
No proposal was created (rejected_by_approval_policy): The DSH approval gate rejected this call,
so no proposal was created. Ask the user to review and retry.
```

Host 审计：`proposal.blocked`（`approval: "rejected"`）。结论：DSH 审批不可用时不会触达 Domain、不会创建提案。

### 6.3 会话自动绑定（S-A3）

工作台当前项目已设置后，新建会话（含 2026-11-16 02:11:47Z / 02:16:31Z 两个样本），`host-plugin-state.json` 中记录：

```json
"2afaa516-ac2a-4eee-a3b7-f3aa93a8ba45": { "projectId": "a899ed37-...", "reason": "session-created" },
"session-589056fa-eaca-4a14-a1ad-ad79b5c9408b": { "projectId": "a899ed37-...", "reason": "session-created" }
```

结论：`session/created` 时自动绑定当前项目并注入系统提示（新会话页面截图 15）。

### 6.4 适配器鉴权负向（整改后）

`proposal-stub-auth-negative.json`：

```json
{ "unauthenticatedProposals": 401, "unauthenticatedApprove": 401, "corsHeader": null }
```

结论：`/health` 之外全部要求 `x-mammoth-stub-token`；不再返回 `Access-Control-Allow-*`；审批必须回显提案 id。
（残留风险：同 OS 用户可读取 token/凭据，见报告 §10/§15，正式版必须由独立 ADR 解决。）

### 6.5 安装生命周期

卸载 → 入口消失 / profile 恢复 → 重装 → 入口恢复；`engines.dsh: ">=9.0.0"` 负向安装被 `incompatible-version` 拒绝。
详见 `plugin-lifecycle-evidence.md` 与截图 16/17。

### 6.6 负责人真人批准（2026-11-16 11:13，Asia/Shanghai）

负责人在 Mammoth 工作台「待审核提案」亲自点击 **批准并写入**（非 Agent 自动化），批准提案
`db238b78-7834-4dcb-a86b-092f8451980d`（"急料维护列表新增'供应商名称'列"）：

- 提案状态：`approved`；`approver: local-desktop-human`；`dshApproval: allowed-once`；
- Domain 返回：`REQ-0003`（`8052479a-2bc3-4e2b-9114-a14b8a1ff7cf`，draft）；
- Domain `work_items=3`；`REQ-0003.createdBy = 3b3e0c00-…`（`spike_admin` 人类账号）；
- Domain 审计：新增一条 `work_item.create`，`actorUserId=spike_admin`；
- 证据截图：`18-proposal-approved-raw-json-before-fix.png`（修复前原始 JSON）、
  `19-proposals-human-readable-after-fix.png`（修复后可读页面）。

> 结论：**真人批准链路已由负责人本人完成**；此前 `REQ-0002` 仅为 Lead Agent UI 自动化的机械链路证据。

### 6.7 期间修复的阻断缺陷

- 工作台批准返回 `MAMMOTH_STUB_HTTP_ERROR … HTTP 400 {"error":"confirm-required"}`：Host 校验 `confirm` 后
  未把它转发给适配器，已修复（`10efdea`）并由负责人在修复后成功批准（`REQ-0003`）。
- 提案页把 `domainObject` 等后端字段以原始 JSON 直出：已改为业务字段主视图 + 技术信息折叠
  （client-only，HMR 生效；截图 18→19）。

```json
{
  "location": "dsh-app://app/",
  "transportPresent": true,
  "streamBaseUrl": "http://127.0.0.1:3802",
  "apiBase": "dsh-app://app/mammoth/api",
  "apiBaseError": null,
  "diagnostics": {
    "errors": [],
    "notes": ["apply entered", "sidebar.panellist injected", "main injected"],
    "startedAt": "2026-11-16T02:00:49.876Z"
  }
}
```

结论：Desktop 的 Client 插件运行在 `dsh-app://app/` 载体上；对 Host 注册路由的相对同源 `fetch` 经 shell
fetch 桥成功（`errors: []`，真实数据渲染完成），无需浏览器直连 Domain、无需 CORS、Client 不持有凭据。
