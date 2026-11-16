# Mammoth DSH Desktop 集成 Spike（Bundle 详情）

本 bundle 是 **Mammoth × DeepSeek Harness Desktop 集成 Spike** 的验证夹具（`MAMMOTH-DSH-DESKTOP-SPIKE`），用于证明路线 A 的最小可行性；它不是产品发布物。业务权威数据始终在独立运行的 Mammoth Domain Service（Spring Boot + SQLite）中。

## 它提供什么

| 组合部分 | 实现 |
|---|---|
| **Host 插件** | `index.js`：Host 侧 HTTP 读取 Domain Service（Node 侧无 CORS 问题）、按 DSH Session 绑定 Mammoth 项目、`systemPrompt` 项目上下文注入、3 个模型工具（项目列表 / 项目上下文 / 创建待审核提案）、`/mammoth/api/*` 受 DSH 浏览器 Cookie 鉴权的同源数据通道。 |
| **Client 插件** | `client.js`：左栏 `Mammoth` 入口 + `main` 全页工作台（项目/客户/产品/需求池/知识/提案/诊断）、Conversation 头部项目徽章、右栏 `sidebarRightTabs` 项目上下文标签页、`tool.call.toolview` 业务结果卡片。纯 React 原生 Slot，无 iframe，不导入官方 Client 组件库。 |
| **MCP 配置** | `cordis.patch.yml` 中的 `@deepseek-ai/dsh-mcp-client` 行：以 stdio 启动现有 Mammoth Domain MCP server（`java -jar … --mcp`，只读），提供 `mcp__mammoth_domain__search_project_knowledge|get_knowledge_document|get_requirement_context`。 |
| **Agent Preset** | `cordis.patch.yml` 中的 `preset-mammoth` 行：带 Mammoth 领域人设与最小工具集的会话预设，可在新会话中选择。 |

## 安全边界

- Agent 只能创建**待审核提案**（`mammoth_propose_work_item`），不会直接写 Domain Service。DSH 工具审批闸门
  采用 fail-closed（只有 `allowed-once` 才继续）；正式写入由隔离的 Proposal 适配器在**人工批准后**以
  **人类 Domain 账号**调用 Domain REST 完成（提案者 = agent 身份，批准侧 = 人类账号）。
- Host→Domain 的读取凭据（合成测试账号）保存在仓库外的机器本地文件，不进入 Client、不进入提示词、不进入会话日志。
- Client → Host 的数据通道是 DSH 同源、经 DSH 浏览器 Peer 鉴权的路由；Client 不持有 Domain 凭据、不直连 Domain。
- **Spike 限制（重要）**：Proposal 适配器与 Agent Runtime 运行在同一 OS 用户下，其 token/人类 Domain 凭据
  对具有任意 shell 的 Agent 可读，因此适配器**不构成安全边界**；正式写路径必须由独立 ADR 定义隔离服务身份、
  Domain 侧授权与人类独占审批。Spike 中"人工批准"点击由验证者 UI 自动化完成，不代表真人治理已验收。
- 仅使用合成数据；不接触真实客户数据、`~/.dsh` 凭据或生产环境。

## 已知限制

- 本 Spike 不做正式写 API（需独立 ADR）、不做多用户/RBAC UI、不做全部业务模块迁移。
- 工作台是**最小验证界面**：提案页主视图为业务字段，提案 ID/DSH 审批/Domain 原始响应在「技术信息」折叠区；
  正式迁移按 `docs/specs/UI-SPEC.md` 重新设计，不复用该临时布局。
- 右栏通过官方 `sidebarRightTabs` 扩展席位实现，未替换官方 `rightbar`/`rightbar.session` 单槽。
- 会话↔项目映射保存在 Host 插件本地 JSON（过渡实现，非权威、非 Session 事件）。
- DSH 处于 `0.2.2-rc.2` 预发布期，Slot/Client API 可能随版本破坏性变更；升级需按升级 SOP 回归。
- **工具 schema 是全局运行面**：本 bundle 以 `link:` 接入 `desktop` profile，3 个工具注册为全局工具。
  DSH 0.2.2-rc.2 的 `ctx.tools.register()` 只校验 `output.schema`，不校验 `parameters`；非法
  `parameters` 会被 Provider 在每次请求时拒绝，影响所有会话。本 bundle 因此内置
  `tool-schema.js`（注册时 fail-fast）与 `scripts/check-tool-schemas.mjs`（安装/重载前自检）。
  2026-11-13 的 `required: true` 事故与修复记录见仓库 `docs/quality/INCIDENT-2026-11-13-DSH-TOOL-SCHEMA.md`。

## 安装与卸载

见 `README.md`。安装/卸载属于对 DSH Profile 的运行环境变更，需负责人批准并保留回滚（`plugin_manager remove_bundle` + Profile 备份）。

## ADR-0015 P3 integration (2026-11-16)

- The Host holds only the **propose** token (`config.agentWrite.token`); it creates proposals via
  `POST /api/agent/proposals` and lists its own proposals via `GET /api/agent/proposals/mine`.
- There is no Host-side approve/reject/apply path and no proposal stub/adapter. A human approves
  in the Domain browser page `GET /approval` (human session + CSRF); the isolated approval service
  (`java -jar ... --approval-service --watch ...`, apply token only) applies approved proposals.
- Verified end-to-end in the real DSH Desktop: Host tool propose -> `/approval` human click ->
  approval service auto-apply -> Domain `REQ-0006/0007/0008` with `createdBy` = human.
- Known follow-ups: expose proposal `title` in the API response; add the `/approval` link in the
  workbench proposals page; retire the legacy Spike adapter files and P2.1 dedicated task account.
