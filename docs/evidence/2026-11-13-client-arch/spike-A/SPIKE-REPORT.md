# Mammoth × DSH Desktop 集成 Spike — 验证报告（Spike A）

- **日期**：2026-11-16
- **关联**：`docs/reports/2026-11-13-CLIENT-ARCHITECTURE-REEVALUATION.md` §6、`decisions/ADR-0014-CLIENT-SHELL-AND-UI-EXTENSION.md`
- **Spike 分支**：`spike/dsh-desktop-integration`（Worktree `E:\MyProj\MommothAgent\code\Mammoth-Agent-spike`）
- **Bundle**：`integrations/dsh/mammoth-bundle/`（Host + Client + MCP 配置 + Agent Preset 四合一单包）
- **证据目录**：`docs/evidence/2026-11-13-client-arch/spike-A/`（索引见该目录 `README.md`）
- **结论**：**路线 A（DSH Desktop 宿主 + Mammoth 外部 Bundle）在真实安装的 DSH Desktop 0.2.2-rc.2 上可行**；建议 ADR-0014 转 Accepted（附本报告列出的修订项与后续条件）。最大风险项 H3（Client→Domain 数据通道）已实测闭环。

> 安全声明：Spike 全程使用合成数据与隔离 data root；未接触真实客户数据、生产凭据或 `~/.dsh` 凭据/业务数据；未修改 DSH 上游源码；未删除现有 React SPA、未重写 Domain Service、未变更数据库结构。

---

## 1. 任务理解与边界

**目标**：证明路线 A 的最小真实闭环——外部 bundle 能在真实 DSH Desktop 安装；Mammoth 获得左栏入口与主业务面板；面板读取真实 Domain Service 数据；可选择项目并让会话携带项目上下文；Agent 通过现有 MCP 读取项目知识、输出结构化业务卡片；Agent 只能创建待审核提案，人工确认后才由 Domain Service 正式写入。

**非目标**（本轮不做）：全部业务模块 Desktop 化；正式写 API/服务身份（另立 ADR）；多用户/多租户；现有 SPA 迁移；DSH 升级回归（S-A9）；启动性能测量（S-A10，记录为后续项）。

**工作方式**：独立 Git 分支 + Worktree；只读调研（DSH 0.2.2-rc.2 安装包源码/官方文档/官方 bundle 范例）先行；Host/Client/MCP/Preset 组合在真实 Desktop 中验证；每步留截图/日志/JSON 证据；所有变更可回滚。

---

## 2. 实施前置核查（任务书 §三）

| 检查项 | 结果 | 证据 |
|---|---|---|
| Git 分支与工作区状态 | 从 `main@fcc851c` 建 `spike/dsh-desktop-integration` + 独立 Worktree；main 上未提交的 ADR/报告/证据文件未被覆盖 | `git worktree list`、分支提交记录 |
| ADR-0014 与评估报告 | 已读 ADR-0014 全文与报告 §4–§7；Spike 范围以任务书 §四 的 10 项为准（S-A0b/S-A1–S-A7 为核心，S-A8–S-A10 记为后续） | ADR-0014、报告 §6 |
| 当前 DSH 安装版本 | `0.2.2-rc.2`（Desktop，`desktop` profile）；bundle `engines.dsh: ">=0.2.2-rc.2 <0.3.0"` 兼容 | `plugin_manager list_bundles` 版本字段 |
| 当前版本的 npm 包与官方文档 | 逐一核对安装包内 `docs/`（`slots.md`、`sidebar-right.md`、`conversation.md`、`client-modules.md`、`host-webserver`/`client-connection`/`api-gateway` README 等）与 `src/` 类型声明；**未使用其他 DSH 版本的 API 示例** | 调研报告 + bundle 代码注释中的 file:line |
| 官方 Bundle 范例 | `dsh-client-ui-plugin-manager`（左栏入口 + main 页面 + locale/icon 单包）、`dsh-web-app`（多 patch 文件、preset 声明）、`dsh-mcp-client`（MCP 行）、`dsh-host-open-in-app`（受信 web 路由） | 见调研记录 |
| 四部分组合与依赖 | 单包可同时声明 Host 入口 `exports["."]`、Client 入口 `exports["./client"]`、`dsh.bundle.patch`（可混合 Host 行 + MCP 行 + preset 行）、`dsh.client`、locale/icon 导出；官方无"四合一"单包先例，**由本 Spike 实测组合** | 安装结果、截图 00、工具与页面同时生效 |
| 已确认 vs 需实测的 API/Slot | 已确认：`sidebar.panellist`、`main`、`conversation.session.header.actions`、`tool.call.toolview`、`sidebarRightTabs` + `sidebar.right.pane.tab`、`ctx.connection.requestRejection`、`ctx.webServer.register`、`ctx.systemPrompt.section`、`ctx.approval.request`、Host 工具 + raw JSON Schema 约束。需实测：Desktop 下 Client→Host 同源 fetch 的载体与鉴权、`main` 页面真实渲染、右栏扩展席位、会话绑定、审批串联。**以上需实测项均已在本 Spike 实测** | 本报告 §3–§8 |

---

## 3. 验收矩阵（对照 ADR-0014 / 报告 §6）

| # | 验证内容 | 结果 | 证据 |
|---|---|---|---|
| S-A0 | 安装最小 bundle、工具出现、可卸载回滚 | **通过**。单包以 `link:` 装入 `desktop` profile；执行了完整的 **卸载 → 验证入口/工具消失、profile 清单恢复 → 重装 → 验证三行重新 active** 往返；安装/激活/工具真实可用 | 截图 16（卸载后左栏无 Mammoth）、17（重装后恢复）；profile `package.json` 前后对比见 `plugin-lifecycle-evidence.md` |
| S-A0b | Host + Client + MCP + Preset 四合一 | **通过**。三个 Loader 行均 active；Client 半经 `dsh.client` 扫描并在真实 Desktop 渲染；显示元数据/图标/详情文档正常；**`engines.dsh` 负向验证通过**：对 `engines.dsh: ">=9.0.0"` 的测试 bundle，安装被 `incompatible-version` 拒绝且未安装 | 截图 00、01、12；`plugin-lifecycle-evidence.md` |
| S-A1 | 左栏入口 + `main` 整页 + 主题/locale | **通过**。Mammoth 左栏入口出现；点击打开全页工作台；沿用宿主主题 token；中英文文案均已注册（`ctx.locale`） | 截图 07、08、01 |
| S-A2 | 完整项目面板 + 数据通道选型 | **通过（Go）**。面板读取真实 Domain Service 数据（项目/客户/成员/verified 知识/需求）；通道选型 = **C2 Host 桥**：Client 页面 origin 为 `dsh-app://app/`，经 Desktop shell fetch 桥对 Host 注册的同源路由发起相对 `fetch`，由 `ctx.connection.requestRejection` 认证；未使用浏览器直连 Domain（C1 不可行且不需要），无 CORS、无客户端凭据 | 截图 01–04、12、13；`tool-call-records.md` |
| S-A3 | 选择项目并建立会话上下文 | **通过**。工作台「设为当前项目」→ Host 记录 activeProjectId；新建 Session 时自动绑定（`host-plugin-state.json` 中 2 个 `reason: session-created` 样本）并注入项目卡；切换项目不串会话 | 截图 01、15；`host-plugin-state.json` |
| S-A4 | 会话与项目关联可见 | **部分通过**。会话头部项目徽章 + 右栏项目上下文页（绑定状态、客户、成员、知识、工作项）；未实现"从工作台一键新建并进入会话" | 截图 05、06 |
| S-A5 | Agent 使用 Mammoth 项目知识工具 | **通过**。现有 Domain MCP 的 3 个只读工具全部可用（3/3）；Agent 在真实会话中读取 verified 知识/来源计数/需求 11 维就绪；**本轮补做负向**：非成员项目返回 `ApiException: no access to project`（MCP 侧拒绝） | `tool-call-records.md` §2；截图 14 |
| S-A6 | 渲染 Mammoth 业务结果卡片 | **部分通过**。知识卡片（成功态）与 MCP 错误卡片（失败态，不 blank slot）在真实会话渲染；提案工具卡片为结构化结果（截图 09/10 的提案数据）；卡片内"跳转工作台"动作未实现 | 截图 11、14 |
| S-A7 | 受控写与审批边界 | **条件通过（真人批准已补齐）**。机械链路：DSH 工具审批（策略 `ask` 时 `allowed-once`；`never` 时 fail-closed）→ 工作台批准 → 适配器以人类 Domain 账号写入。**2026-11-16 11:13 负责人亲自在工作台批准提案 `db238b78` → Domain 写入 `REQ-0003`**（`audit_log.actorUserId=spike_admin`；agent 账号 401、不能自批）；此前 `REQ-0002` 为 Lead Agent UI 自动化的机械链路证据。**剩余条件**：适配器与 Agent 同 OS 用户，不构成安全边界（需独立写路径 ADR）；负责人签署 ADR。 | 截图 09、10、13、14、18、19；`domain-after-approval.json`；`proposal-stub-*`；`proposal-stub-auth-negative.json` |
| S-A8 | 桌面文件/目录/artifact 上传 | **未执行**（任务书 §四 未列；记为后续项，不影响路线 A 结论） | — |
| S-A9 | DSH 升级回归 SOP | **未执行**（可延后；建议作为首个升级窗口的前置任务） | — |
| S-A10 | 启动性能与包体测量 | **未执行**（记为后续项；当前 Client 为单文件 ~65KB，仅 `require('react')`，未观察到可感知首帧影响，但未测量） | — |

**总体判定（按独立评审整改后）**：路线 A 的**技术可行性可接受**——S-A0、S-A0b、S-A1–S-A3 通过，S-A4/S-A6 部分通过，S-A2 数据通道 Go，S-A5 通过（含负向）。**S-A7 为条件通过**：机械链路与审计已端到端验证，但"人工批准"未由独立人类完成、适配器不构成同用户 shell 的安全边界；这两点必须由"受控写操作与服务身份"独立 ADR 解决，并在正式版用真人审批复验。未触发"路线 A 技术不可行"的决策门槛；ADR-0014 以**定稿待负责人签署 + S-A7 条件**的形式交付。

---

## 4. 组合结构与安装（四合一）

### 4.1 单包结构

```
@mammoth/dsh-bundle
├── package.json          dsh.bundle.patch → cordis.patch.yml；dsh.client(platform web, immediately)；exports "."/"./client"/locale/icon
├── index.js              Host 插件（零第三方依赖）
├── client.js             预构建 lazy-CJS 单文件（window.__ModuleLoader__.load；仅 require('react')）
├── tool-parameters.js    3 个工具的 raw JSON Schema 单一来源
├── tool-schema.js        本地 raw schema 子集校验（注册前 fail-fast）
├── scripts/check-tool-schemas.mjs   安装/重载前自检 + 事故回归夹具
├── scripts/configure.mjs            渲染机器本地 cordis.patch.yml 与 local/config.json
├── locale/{zh,en}.json   Plugins 页显示元数据（title/description/detail）
├── docs/DETAIL.md        Plugins 页详情文档
└── icon.svg
```

### 4.2 Loader 行（同一 patch 文件内三类行）

| 行 id | 模块 | 说明 |
|---|---|---|
| `mammoth-host` | `@mammoth/dsh-bundle` | Host 插件：`ctx.tools`（3 个工具）、`ctx.systemPrompt.section`、`ctx.on('session/created'/'session/event')`、`ctx.webServer.register('/mammoth/api')`、`ctx.approval.request` |
| `mammoth-domain-mcp` | `@deepseek-ai/dsh-mcp-client` | stdio 启动现有 Mammoth Domain MCP server（`java -jar … --mcp`，只读身份）|
| `preset-mammoth` | `@deepseek-ai/dsh-agent-preset` | Mammoth persona（项目上下文/证据/写入纪律）+ 最小工具集（pwsh、fs、fs-search、web、todo、ask-user、present 等）|

Client 半不开 Loader 行：`dsh.client` 由 `dsh-client-modules` 扫描包清单并注入 `__DSH_BOOT__`。

### 4.3 安装/卸载

- 安装：`plugin_manager` `install_bundle`，target 为本 bundle 绝对目录（`link:` 安装，pnpm 只建 profile 依赖）；
- 卸载：`remove_bundle`（清理 profile 依赖与 selection；link 源目录保留）；
- 机器本地配置（`scripts/configure.mjs` 生成、git-ignore）：`cordis.patch.yml`、`local/config.json`、`<stateDir>/credentials.json`、`<stateDir>/state.json`；
- 安装/重载前必须：备份 profile 清单 → `node --check index.js client.js` → `node scripts/check-tool-schemas.mjs` → 重载 → 发一条最小请求验证工具 schema 可被 Provider 接受。**该流程已写入 bundle README 与 AGENTS.md 顶部最高优先级规则。**

---

## 5. Client → Domain 数据通道结论（H3，原最大风险）

### 5.1 实测结果

- **Desktop 渲染页面 origin = `dsh-app://app/`**（不是 loopback HTTP）。Client 插件对同源相对路径 `mammoth/api/*` 的 `fetch` 由 Desktop shell 的 fetch 桥转发到 Host `webServer` 前缀路由，并以浏览器 Peer 身份通过 `ctx.connection.requestRejection`；Host 再以机器本地的合成人类只读会话访问 Domain REST。
- 实测诊断（截图 13）：`apiBase: "dsh-app://app/mammoth/api"`、`transportPresent: true`、`apiBaseError: null`、`clientDiagnostics.errors: []`；工作台拿到真实项目/客户/知识/需求数据（截图 01–04）。
- 对照：直接 `http://127.0.0.1:3802/mammoth/api/*` 的无 Cookie 探测被 401 拒绝（未认证 Peer），说明路由本身没有放弃鉴权；不需要也不应该为浏览器直连 Domain 开 CORS。
- Domain Service 保持独立进程与权威存储；Client 不保存任何 Domain 凭据；Host 的读取凭据只在仓库外机器本地文件，且只用于 Host 侧 HTTP。

### 5.2 三方案结论

| 方案 | 结论 | 原因 |
|---|---|---|
| C1 Client 直连 Domain REST | **不采用** | Domain 无 CORS，跨源 Cookie/预检不通过；需要改产品代码（CORS）且把业务权限暴露给浏览器；Client 还要持有身份 |
| C2 Host 插件桥（本 Spike 采用） | **采用** | 官方 `ctx.webServer.register` + `ctx.connection.requestRejection` 是官方插件公开用法（参考 `dsh-host-open-in-app`）；同源、Cookie 鉴权、无 CORS、无客户端凭据；Host 可做投影/合并/缓存 |
| C3 只读工具/卡片降级 | 保留为降级路径 | 仅能承载单次工具结果，不能承载工作台列表/详情；本 Spike 已同时实现 MCP 只读工具 + 卡片 |

> 备注：官方自定义 Typert Remote 需要仓库内 codegen 与 `api-remotes` 装配（安装版不提供该工具链），**不作为第三方通道**；`ctx.connection.rpc.handle` 亦为服务端内部面。Host 受鉴权 web 路由是本次实测最稳且文档化的第三方通道。

---

## 6. 会话 ↔ 项目上下文

- **当前项目**：工作台选择 → `POST /mammoth/api/context` → Host 本地状态（非业务权威）+ 项目卡缓存；
- **会话绑定**：Host 监听 `session/created` 与 `session/event`（首条 `user/message`），把新 Session 绑定到当时的 activeProjectId；绑定只写 Host 本地 JSON，不新增 Session 事件类型；
- **系统提示注入**：`ctx.systemPrompt.section({ text: assemble => projectPromptText(assemble.agent?.session?.id) })` 以同步缓存生成项目卡（客户/成员/知识标题/工作项/工具使用规范/写入纪律），实测出现在模型请求中；
- **可见性**：会话头部 `conversation.session.header.actions` 徽章 + 右栏 `sidebarRightTabs` 项目上下文页（绑定状态、客户、成员、知识、工作项）；
- **切项目不串**：绑定在创建/首条消息时固化，后续切 activeProject 不影响既有会话。

**偏差记录**：任务书与 ADR 提到 `rightbar` / `rightbar.session` 作为右栏扩展点；官方 0.2.2-rc.2 中这两个是已被 ui-sidebar-right 占用的 single slot，第三方正确扩展位为 `sidebarRightTabs` + `sidebar.right.pane.tab`。本 Spike 按官方扩展席位实现，效果等价（截图 05），建议 ADR 修订表述。

---

## 7. 结构化业务结果卡片

- 注册：`ctx.slots.inject('tool.call.toolview', () => ctx.slots.register({ name:'tool.call.toolview', key:<wire tool name> }, Card))`；
- 已注册 key：`mammoth_propose_work_item`、`mcp__mammoth_domain__search_project_knowledge`、`mcp__mammoth_domain__get_knowledge_document`、`mcp__mammoth_domain__get_requirement_context`；
- 数据来源：工具 `output.presentationMeta`（提案卡）或结果 content 的 JSON 文本（MCP 卡）；解析失败/未命中均渲染安全兜底行，不 blank slot；
- 实测：MCP 知识检索卡片在真实会话正确渲染（截图 11）；卡片使用 `--dsw-alias-*` 主题 token，样式作为组件局部 `<style>` 元素随卡片卸载移除。

**未实现**：`conversation.chat.node` 自定义节点（官方要求搭配 `ConversationNodeDefinition`/事件注册，最小实现需谨慎避免重复渲染与破坏会话装配）。本轮在源码/文档层面确认其扩展位与约束，**未在本 Spike 渲染**；列为后续项（见 §10）。

---

## 8. Proposal 与人工审批（S-A7）

### 8.1 链路

```
Agent 工具 mammoth_propose_work_item
  → ① DSH 工具审批闸门 ctx.approval.request()（策略 ask：人工在 DSH 审批 UI 确认→allowed-once；
       策略 never：fail-closed→rejected，不创建任何提案）
  → Host 调用隔离 Proposal 适配器 POST /proposals（pending，记录 proposer=agent 身份）
  → ② 人工在 Mammoth 工作台「待审核提案」点击「批准并写入」
  → 适配器以人类 Domain 账号登录 + CSRF，调用 Domain 现有写端点 POST /api/projects/{p}/work-items
  → Domain 权限校验、审计、生成 REQ-0002（createdBy=人类账号）
  → 回写提案 approved + domainObject；工作台/会话上下文显示新需求
```

### 8.2 证据链（全部实测）

| 断言 | 证据 |
|---|---|
| 策略 `never` 时 Agent 无法创建提案（fail-closed） | 工具返回 `rejected_by_approval_policy`（`tool-call-records.md` §1.3）|
| 策略 `ask` 时 DSH 审批闸门返回一次授权 | 提案创建成功；Host 审计 `dshApproval: "allowed-once"`（截图 13）|
| 审批前 Domain 无写入 | 提案 pending 截图（09）；`work_items` 仍为 1 条（调用记录）|
| 人工批准动作可追溯 | 提案记录 `approver: "local-desktop-human"`，`decidedAt=2026-11-16T02:07:18Z` |
| 正式写入由 Domain Service 执行 | `POST /api/projects/{p}/work-items` 返回 `REQ-0002`；Domain `audit_log`：`action=work_item.create, actorUserId=3b3e0c00...`（人类 admin）|
| Agent 不能自批/不能直写 | Agent 账号 `account_type=agent` 被 Domain 登录层硬拒（401）；bundle 无 approve 工具；Agent 无 Domain 写凭据 |
| 四眼 | proposer=agent 身份（`fb228729...`），approver=human（`local-desktop-human` → Domain `spike_admin`），适配器拒绝 approver==proposer |

### 8.3 边界声明（独立评审 + 真人批准后修订）

- **真人批准已发生**：2026-11-16 11:13 由项目负责人本人在工作台点击「批准并写入」，提案 `db238b78` → Domain `REQ-0003`（`createdBy=spike_admin`）。此前 `REQ-0002` 的批准点击由 Lead Agent UI 自动化完成，只证明机械链路；**真人治理以 `REQ-0003` 为准**。正式产品仍需人类身份/签署/职责记录的显式设计。
- 适配器与 Agent Runtime 运行在**同一 OS 用户**下：token 与人类 Domain 凭据可被具有任意 shell 的 Agent 读取，因此**适配器不是安全边界**。本 Spike 已做加固（DSH 审批 fail-closed 只接受 `allowed-once`、适配器 token 鉴权、移除 CORS `*`、审批必须回显提案 id、无鉴权列表接口关闭），但真正的边界必须来自：隔离服务身份/独立进程与权限、Domain 侧授权与审计、以及人类独占的审批通道（正式写路径 ADR）。
- DSH 工具审批与 Domain 审批的组合行为需在正式实现中继续保留"缺一不可"；DSH 的 `never` 策略不能被视为业务审批的替代。

---

## 9. 安全边界（本 Spike 实际遵守）

1. **业务权威**：客户/项目/知识/需求/审计只在 Domain Service（SQLite）。DSH 侧只存非权威本地状态（activeProject、Session↔项目映射、提案镜像），不新增 Session 事件类型、不写业务数据。
2. **凭据**：Host 读取账号密码只在仓库外机器本地文件（`%TEMP%\mammoth-spike\credentials.json`）；MCP 用 agent 账号环境变量；Proposal 适配器的人类密码只在其进程环境。均未进入 Client、提示词、会话日志、仓库或截图。
3. **权限**：所有 Domain 读写经 Domain Service 鉴权与 CSRF；Agent 只读（MCP 只读 + Host 读取账号为项目成员），正式写只发生在人工批准后的适配器人类会话中。
4. **客户端**：Client 不持有 Domain 凭据、不直连 Domain；Client→Host 通道经 DSH 浏览器 Peer 鉴权；`local/config.json` 不含秘密。
5. **插件信任**：安装会执行 Host 代码，安装前已备份 profile 清单并保留 `remove_bundle` + 清单恢复 + 数据清理回滚路径；bundle 已提交 Git（`dec48c4` 起），本地改动不再是无追踪的全局运行时。
6. **环境**：DSH 为 `0.2.2-rc.2` 预发布版；每次修改已 link 进 profile 的 bundle 前执行 `node --check` + `check-tool-schemas.mjs`，避免再次出现 2026-11-13 的工具 schema 全局事故。

---

## 10. 已知限制与偏差

1. **DSH 预发布耦合**：`0.2.2-rc.2` 的 Slot/Client API 与 `commands.execute` 等 Remote 面可能破坏性变化；本 bundle 依赖官方文档化扩展面，仍需升级回归（S-A9）。
2. **右栏实现方式偏差**：未使用（也不能使用）`rightbar`/`rightbar.session` 单槽，改用官方 `sidebarRightTabs` 扩展席位（见 §6）；ADR 表述需修订。
3. **`conversation.chat.node` 未实现**：只完成源码/文档级契约确认；自定义节点需配套 `ConversationNodeDefinition` 与事件映射，最小实现尚未完成（后续项）。
4. **`conversation.composer.dock` 未使用**：当前项目上下文通过头部徽章 + 右栏呈现；composer 引用 dock 可在后续补足。
5. **会话↔项目映射是过渡实现**：Host 本地 JSON，非 Session 事件、非业务权威；重装/清理本地状态会丢失；正式实现需要 AgentRun/session link 数据模型（V11，另立 ADR）。
6. **Preset 未做可视化选择验证**：`preset-mammoth` 行已 active（`plugin_manager list_plugins`），但未在新建会话 UI 中实际选择并截图；建议后续补一次"选择 Mammoth 预设 → 会话能力差异"验证。
7. **审批点击自动化**：见 §8.3；生产必须真人。
8. **未验证**：S-A8（桌面文件/目录/artifact 上传）、S-A9（升级回归）、S-A10（启动性能/包体阈值）；本地单机环境，未覆盖团队模式、多用户、网络/代理场景。
9. **`/api/meta` 公开暴露本机路径**（Domain 既有行为）；Host 未把其暴露给模型，但建议在正式版评估收敛。
10. **客户端激活错误不可见**是 DSH 0.2.2-rc.2 的 UX 空档（仅 console/boot audit）；本 bundle 内置诊断可缓解，长期需上游改进或统一插件健康页。
11. **审批适配器不是安全边界（Spike 级）**：与 Agent 同 OS 用户，token/人类凭据对任意 shell 可读；本轮已加固（fail-closed、token、去 CORS、confirm 回显、关列表接口），但正式版必须由"受控写操作与服务身份"ADR 用隔离身份/Domain 授权/人类独占审批解决。
12. **"人工批准"治理**：`REQ-0003` 已由项目负责人**亲自**在工作台批准（2026-11-16 11:13），补齐了"真人点击"证据；`REQ-0002` 为 Lead Agent UI 自动化的机械链路证据。正式产品仍需显式的真人身份/签署/职责记录设计（人事治理）。
13. **证据历史性缺口**：早期提案记录 `dshApproval` 为 null（当时 Host 尚未把该字段传给适配器，已在加固提交中修复；Host 审计同一提案有 `allowed-once`）；截图 14–20 为整改/真人批准阶段补采。
14. **提案页可读性缺陷（已修复）**：修复前提案卡片把 `domainObject` 等后端字段以原始 JSON 直接展示（截图 18）；现已改为业务字段主视图 + 技术信息折叠 + 需求跳转（截图 19）。该问题属 Spike 演示页简化缺陷，正式迁移按 `UI-SPEC` 设计，不复用此临时布局。
15. **审批确认转发 bug（已修复，`10efdea`）**：Host 校验 `confirm` 后未转发给适配器，导致工作台批准返回 `confirm-required`；修复后负责人批准成功。

---

## 15. 独立评审与整改记录（2026-11-16）

独立评审子代理（只读）对本 Spike 的结论为**有条件接受**：技术可行性可接受，但 S-A7 的人工治理结论与 ADR Accepted 文本不可按原样接受。Lead 已按评审意见整改，并保留未满足项：

| 评审发现 | 级别 | 整改 | 残留 |
|---|---|---|---|
| "人工批准"由 Lead Agent UI 自动化完成，却被其他文档写成已证实的人类批准 | BLOCKER | 报告/ADR/README 统一改为"机械链路演示；人工闸门未验证"，S-A7 降为条件通过 | **已补齐**：2026-11-16 11:13 负责人本人在工作台批准 `db238b78` → `REQ-0003`（截图 18/19） |
| 适配器 `approve` 无鉴权、CORS `*`、任意 `approver` 字符串、Host 接受任意 approver | BLOCKER | 适配器 token 鉴权（`/health` 外全部 401）、移除 CORS、approve/reject 必须回显提案 id、approver 固定为 `local-desktop-human`、Host 拒绝非 confirm 请求；负向测试见 `proposal-stub-auth-negative.json` | 同 OS 用户仍可读 token/凭据 → 正式 ADR 解决 |
| DSH 审批闸门对 `unavailable`/未知结果 fail-open | MAJOR | 改为仅 `allowed-once` 才创建提案，否则 fail-closed | — |
| S-A0 卸载未执行、`engines.dsh` 负向未测 | MAJOR | 补做卸载→验证→重装往返与 `engines.dsh >=9.0.0` 拒绝测试（`plugin-lifecycle-evidence.md`、截图 16/17） | — |
| S-A3 自动绑定无证据 | MAJOR | 新建会话复验：`host-plugin-state.json` 出现 2 条 `reason: session-created` 绑定 | — |
| S-A5 缺少本轮权限拒绝证据 | MAJOR | 补做非成员项目 MCP 调用：`ApiException: no access to project`（截图 14） | — |
| S-A6 缺失败态/未实现跳转 | MAJOR | 补采失败态卡片（截图 14）；跳转动作仍为后续项 | 卡片跳转 |
| 证据文件命名/内容错位、中文乱码 | MAJOR | 截图 sidecar 统一移入 `screenshots-metadata/`；运行时 JSON 用 Node 以 UTF-8 重新生成（`domain-after-approval.json` 等）；新增 `screenshot-manifest.json`（SHA-256） | — |
| 适配器未记录 `dshApproval` | MINOR | Host 已把该字段传给适配器；历史记录保留 null 并在 `proposal-stub-state.json` 说明 | 历史记录 |
| ADR 自相矛盾（Accepted vs "待负责人批准/保持 Proposed"）、Lead Agent 签署、四眼表述不准确、凭据例外未记录、peerDependencies 缺失 | MAJOR | ADR 已改为"定稿待负责人签署 + S-A7 条件"；删除矛盾表述；四眼改为"提案者身份=agent、批准侧=人类账号；Domain 双人审批待独立 ADR"；记录 Spike 明文凭据例外；说明 bundle 零 DSH 依赖故不声明 peerDependencies | 负责人签署 |
| raw schema 本地 guard 对 object/array 上的 enum/const 存在防御缺口；自检脚本有激活副作用 | MINOR | 已记录为后续加固项（当前 3 个 schema 合法且 gate 有效） | 后续加固 |
| 提案页把 `domainObject` 等后端字段以原始 JSON 直接渲染（本轮用户发现） | MINOR（UI 缺陷） | 已修复：业务字段主视图 + 技术信息折叠 + 「在需求池查看 REQ-xxxx」入口（截图 18→19） | 正式迁移按 UI-SPEC 重新设计 |
| 工作台批准报 `confirm-required`（Host 未转发 confirm） | MAJOR（阻断操作） | 已修复 `10efdea`；负责人随后成功批准（`REQ-0003`） | — |

**整改后结论（2026-11-16 最终）**：路线 A 技术可行性可接受；S-A7 真人批准已由负责人亲自完成（`REQ-0003`）；**ADR-0014 已由项目负责人签署（Accepted v1.1）**；写路径/服务身份边界已另立 **ADR-0015（Proposed，2026-11-16 负责人批准另立并起草）** 承接，待其决策项批准与实施后关闭。

---

## 11. 期间发现的事故与防护（重要）

1. **2026-11-13 P0：raw JSON Schema `parameters` 使用 author DSL 的 `required: true`，导致 Provider 拒绝所有请求。** 已按 `docs/quality/INCIDENT-2026-11-13-DSH-TOOL-SCHEMA.md` 恢复；本 bundle 已加 `tool-schema.js` 注册前校验（非法 schema 让插件激活失败而不是污染全局工具面）+ `scripts/check-tool-schemas.mjs` 回归夹具（提交 `dec48c4`）。本 Spike 期间该防护有效：每次重载前后均执行自检。
2. **Client 插件激活抛错在 Desktop UI 无可见反馈**：首版 `sidebarRightTabs.register` 的 `guide` 形状错误（应为数组）导致整个 `apply` 中断、左栏入口消失，而 Host/MCP 正常。修复：入口/页面先注册，其余 surface 逐个 try/catch，工作台内置 `clientDiagnostics` 展示错误（提交 `c5fa625`）。
3. **沙箱/权限环境**：验证过程中 Windows workspace-write 沙箱曾因 `docs/`、`docs/evidence/` 缺失 WRITE_OWNER 而拒绝写入；已按 DSH 官方 `diagnose-windows-sandbox-acl` 流程修复并留存备份/回滚脚本（`.acl-recovery/`），未改文件内容与所有者。

---

## 12. 后续正式迁移计划（建议）

### 12.1 决策与文档（Spike 后立即）

1. ADR-0014 → **Accepted**（附本报告修订项：右栏扩展席位、数据通道结论、审批边界、版本策略）；
2. 修订 `docs/05`（UI 宿主与扩展面）、`docs/13`（数据通道结论、版本耦合）、`docs/12`（新增客户端架构阶段）；
3. 修订 `docs/02`/`UI-SPEC` 的宿主假设（保留 SPA 为管理台/后备 Web）；
4. 新增/更新独立 ADR：**Agent 受控写操作与服务身份**（propose 写 API、四眼、审计、服务令牌）、**AgentRun/session link（V11）**、**MCP 写边界/是否修订 ADR-0001 §6.3**。

### 12.2 工程化（按顺序）

1. **Bundle 工程化**：`integrations/dsh/mammoth-bundle/` 纳入正式构建（Client 预构建产物 + 版本号 + `engines.dsh`/peer 矩阵）；CI 增加 `node --check` + `check-tool-schemas.mjs` + 契约冒烟；
2. **数据通道层**：把 Host 桥封装为 `dsh-adapter`（路由前缀、错误映射、超时、缓存、访问控制），Client 只依赖适配层；
3. **领域桥接**：Host 侧 Domain 客户端（cookie/CSRF/重试/超时/审计标签）+ 只读投影（项目卡、知识列表、需求列表）；正式写 API 另行 ADR 后接入；
4. **页面迁移**：按评估报告 §4 IA 增量迁移（工作台首页/项目空间/需求池/知识中心为主批次；客户/产品/方案/交付/审计随后）；复用现有 SPA 的 API 客户端/格式化/权限逻辑；
5. **审批与审计**：DSH 工具审批 + Domain 四眼 + 审计链路固化；UI 提供提案详情/批准/拒绝/回滚入口；
6. **会话绑定**：用 V11 数据模型替换 Host 本地 JSON；提供"从项目/需求一键新建并进入会话"；
7. **安全与运维**：专用 dev profile / 构建产物安装（避免开发工作树直连日常 profile）、升级 SOP（读 upgrade index → slot catalog 对照 → 重建 → 契约回归 → 灰度/回滚）、性能预算（S-A10）。

### 12.3 决策门槛（保持 ADR 原有条款）

若后续 DSH 版本升级造成以下任一情况：Host 受鉴权 web 路由不可用、`dsh.client` 第三方扫描/加载受限、`main`/`sidebar` 扩展面被移除、`sidebarRightTabs` 扩展席位变更、或工具审批不可用 → 暂停迁移并提交 ADR 修订，重新评估路线 C（独立 B/S）/B（自研壳）。

---

## 13. ADR-0014 Accepted 建议（决策要点）

建议在 ADR-0014 中固化以下**已被本 Spike 证实**的决策与修订：

1. 采纳路线 A：DSH Desktop 宿主 + Mammoth 外部 Bundle（Host + Client + MCP + Preset 四合一单包，已验证）；
2. 产品形态：DSH Conversation 为 Agent 主入口；Mammoth 工作台为 `main` 全局面板；二者同屏并共享项目上下文；
3. **数据通道**：Client→Host 走 Host 注册的受鉴权同源路由（C2 Host 桥）；Client 不直连 Domain、不持有凭据；Domain / 浏览器均无需 CORS 改造；
4. **右栏扩展**：使用官方 `sidebarRightTabs` + `sidebar.right.pane.tab` 席位（而非占用 `rightbar` 单槽），并在 ADR 中记录该修订；
5. **会话上下文**：Host 按 Session 绑定项目并注入系统提示；映射数据先以非权威本地状态过渡，正式迁移等待 V11 数据模型 ADR；
6. **写路径**：Agent 只能创建待审核提案；正式写入必须经 Domain Service 权限校验 + 人工批准（DSH 工具审批 + 工作台人工确认 + 四眼），正式写 API/服务身份由独立 ADR 决定；
7. **版本策略**：bundle 锁定 `engines.dsh` 与 peer 版本，升级按升级 SOP 回归；Schema/ Slot 使用收敛在适配层；
8. **保留 SPA**：不删除、不冻结现有 React SPA，作为管理台/后备 Web 与迁移参考。

**残留待批**：正式写路径 ADR、V11 数据模型、团队模式/多用户、插件分发与签名策略。以上不影响路线 A 的可行性结论。

---

## 13.1 决策落定（2026-11-16，追加）

1. **ADR-0014 Accepted v1.1 已由项目负责人签署**：路线 A（DSH Desktop 宿主 + Mammoth 外部 Bundle）正式生效；S-A7 的真人批准条件由负责人本人批准提案 `db238b78` → `REQ-0003` 补齐。
2. **ADR-0015 已 Accepted v1.0（2026-11-16 负责人确认决策项 1–5）**：`decisions/ADR-0015-AGENT-CONTROLLED-WRITE-AND-SERVICE-IDENTITY.md`。
   - 已确认：本机独立 OS 账号/独立进程 + 凭据只存 OS 存储；远端随 M5；**全员四眼（提案者≠批准者）+ 高风险类别第二人复核**；人类审批走 Domain 会话/RBAC；DSH 工具审批保留为附加闸门；
   - P1 实施计划：`docs/specs/ADR-0015-P1-CONTROLLED-WRITE-PLAN.md`（V11 模型/API/负向测试/回滚，待批准启动）。
3. **文档同步**：ADR-0002 补充说明（UI 宿主修订、后端不变）、`docs/00-MASTER-GUIDE.md` §11、`docs/05-ARCHITECTURE-BASELINE.md`、`docs/12-ROADMAP.md`（M4.5 后客户端阶段）、`docs/13-OPEN-DECISIONS.md`、`docs/specs/UI-SPEC.md` 已更新。
4. **后续**：ADR-0015 决策批准与实施（V11 迁移/Approval 服务/负向安全验收）；客户端正式迁移阶段编号；S-A8/S-A9/S-A10；`conversation.chat.node`、`composer.dock`、卡片跳转、Preset UI 验证。

## 14. 附录

- **证据索引**：`docs/evidence/2026-11-13-client-arch/spike-A/README.md`
- **调用记录**：`docs/evidence/2026-11-13-client-arch/spike-A/tool-call-records.md`
- **bundle 源码/安装说明**：`spike/dsh-desktop-integration` 分支 → `integrations/dsh/mammoth-bundle/README.md`
- **事故与防护**：`docs/quality/INCIDENT-2026-11-13-DSH-TOOL-SCHEMA.md`
- **回滚**：`remove_bundle` + 恢复 profile 清单备份（`%TEMP%\mammoth-spike\profile-backup`）+ 停止 18090/18091 进程 + 删除 `%TEMP%\mammoth-spike*`；Windows 权限修复按 `.acl-recovery/` 脚本恢复。
