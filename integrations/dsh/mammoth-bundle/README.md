# Mammoth DSH Desktop bundle（Spike）

`@mammoth/dsh-bundle` 是 Mammoth 在 DeepSeek Harness Desktop 中的外部 Bundle（MoA：
Host 插件 + Client 插件 + MCP 配置 + Agent Preset 四合一）。本目录同时是安装单元。

> 定位：**Spike 验证夹具**，不是产品代码；不修改 DSH 上游、不删除现有 SPA、不重写 Domain Service。
> 目标 DSH 版本：`0.2.2-rc.2`（`engines.dsh: >=0.2.2-rc.2 <0.3.0`）。

## 前置

1. 已安装并可运行 DSH Desktop（当前实测 `0.2.2-rc.2`，`desktop` profile）。
2. 已构建的 Mammoth Domain Service jar：`services/domain/target/mammoth-domain-service.jar`。
3. 一个**隔离的合成数据** Domain data root（不要使用真实数据根），Domain 以 writer 模式运行在
   `127.0.0.1:18090`（示例），MCP 只读进程使用同一 data root 与 agent 用户 ID。
4. Bundle 的四个部分全部就绪：Host 源码、Client 源码、MCP 配置行、Preset 行。

## 0. 实测结论（2026-11-16，DSH 0.2.2-rc.2 Desktop）

本 bundle 已在真实安装的 DSH Desktop 中完成 Spike A 验证（四合一安装、左栏入口、整页工作台、真实
Domain 数据、会话项目上下文、右栏项目上下文、MCP 知识读取、业务卡片、提案人工审批 + Domain 写入）。
完整证据：`docs/evidence/2026-11-13-client-arch/spike-A/`（14 张真实窗口截图 + JSON/JSONL 记录）。

**Desktop 数据通道（重要）**：Desktop 渲染页面的 origin 是 `dsh-app://app/`（不是 loopback HTTP）。
本 bundle 的 Client 半使用**同源相对 `fetch('mammoth/api/...')`**，由 Desktop shell 的 fetch 桥转发到
Host 注册的 `ctx.webServer` 前缀路由，并以浏览器 Peer 通过 `ctx.connection.requestRejection` 鉴权。
因此：Client 不直连 Domain、不需要 CORS、不持有 Domain 凭据；不要改成绝对 `http://127.0.0.1:...` 地址。

**Client 注册纪律**：Client 插件在 `apply` 中抛错时，Desktop UI 没有可见错误（仅 console/boot audit），
表现为"入口消失"。本 bundle 因此先注册 `sidebar.panellist` + `main`，其余 surface 逐个 try/catch，并把
`clientDiagnostics` 显示在工作台「设置与诊断」页。新增 surface 时保持这个顺序。

**官方 Slot 约束**：`rightbar` / `rightbar.session` 是已被 ui-sidebar-right 占用的 single slot，不可替换；
第三方右栏使用 `ctx.sidebarRightTabs.register()` + keyed body `sidebar.right.pane.tab`。`sidebarRightTabs`
的 `guide` 字段是**数组**（元素含 `id/order/title()`）。

## 1. 渲染机器本地配置

Bundle 的 `cordis.patch.yml` 含机器路径，由脚本生成（git-ignore）：

```powershell
cd integrations/dsh/mammoth-bundle
$env:MAMMOTH_READ_PASSWORD = '<合成人类读取账号密码>'   # 不要写进仓库/日志
node scripts/configure.mjs `
  --java "D:\Tools\Java\jdk-17.0.15\bin\java.exe" `
  --jar "<repo>\services\domain\target\mammoth-domain-service.jar" `
  --data-root "C:\Users\<user>\AppData\Local\Temp\mammoth-spike-data\root" `
  --agent-user-id "<agent 用户 ID（Domain 项目 viewer 成员）>" `
  --domain-base "http://127.0.0.1:18090" `
  --read-username "<合成人类读取账号>" `
  --stub-url "http://127.0.0.1:18091" `
  --state-dir "C:\Users\<user>\AppData\Local\Temp\mammoth-spike"
```

生成：

- `cordis.patch.yml`（Host 行 + MCP 行 + Preset 行）
- `local/config.json`（bundle 运行配置）
- `<state-dir>/credentials.json`（Domain 读取账号，机器本地，不入库/不入提示词）
- `<state-dir>/state.json`（当前项目、会话绑定、提案镜像；非业务权威）

## 2. 启动隔离的 Proposal 适配器

Agent 只能创建**待审核提案**；批准后由该适配器以**人类 Domain 账号**执行正式写入：

```powershell
$env:MAMMOTH_DOMAIN_BASE = 'http://127.0.0.1:18090'
$env:MAMMOTH_APPROVER_USERNAME = '<合成人类 approver>'
$env:MAMMOTH_APPROVER_PASSWORD = '<合成人类 approver 密码>'
$env:MAMMOTH_STUB_STATE = 'C:\Users\<user>\AppData\Local\Temp\mammoth-spike\proposals.json'
node <repo>/spike/dsh-desktop-integration/proposal-stub.mjs
```

## 3. 安装到 DSH Desktop

通过 DSH 的 `plugin_manager` 工具（或 Plugins 页 → Add plugin，选择本目录）：

```json
{ "action": "install_bundle", "target": "<repo>/integrations/dsh/mammoth-bundle" }
```

安装会以 `link:` 方式把本目录加入 `desktop` profile，并执行 Host 代码。安装前请：

- 备份 `$DSH_PROFILE_DIR`（`package.json`、`cordis.patch.yml`、`pnpm-lock.yaml` 等清单）；
- 运行 `node --check index.js` 与 `node scripts/check-tool-schemas.mjs`，两者都必须通过；
- 确认 `engines.dsh` 与当前 DSH 匹配；
- 记录回滚方式（见 §5）。

> **工具 schema 变更（2026-11-13 事故后强制）**：本 bundle 以 `link:` 方式接入全局
> `desktop` profile，源码即运行时。任何 `index.js` / `tool-parameters.js` 修改都可能影响
> **所有会话**。DSH 0.2.2-rc.2 的 `ctx.tools.register()` 只校验 `output.schema`，不校验
> `parameters`；一个非法 schema 会让 Provider 拒绝每一次模型请求。因此：
> 1. 原始 JSON Schema 的 `required` 必须是根级字符串数组；
> 2. `required: true` 只允许出现在 `defineTool()` 的 author DSL 中，绝不能出现在
>    直接传给 `ctx.tools.register()` 的 raw schema 里；
> 3. 修改后先运行 `pnpm check`（或 `node scripts/check-tool-schemas.mjs`），通过后再让
>    profile 重载；`registerTool()` 也会在激活时 fail-fast，避免坏 schema 进入全局工具面。

## 4. 验证

1. 左栏出现 **Mammoth** 入口；点击打开工作台整页（项目/客户/产品/需求池/知识/提案/诊断）。
2. 在工作台选择一个项目 → 「设为当前项目」。
3. 新建 DSH 会话：会话头部出现项目徽章；右栏可打开「Mammoth 项目上下文」。
4. 在会话中让 Agent 调用 `mcp__mammoth_domain__search_project_knowledge` 读取项目知识（projectId 由系统提示注入）。
5. 让 Agent 调用 `mammoth_propose_work_item` 创建提案（DSH 审批闸门 → Pending）。
6. 在工作台「待审核提案」人工批准 → 适配器以人类账号调用 Domain `POST /api/projects/{p}/work-items` → 返回 `REQ-xxxx`。
7. 诊断页确认：Domain 读取身份、MCP 工具注册状态、提案审批记录。

## 5. 回滚

```json
{ "action": "remove_bundle", "target": "@mammoth/dsh-bundle" }
```

- 恢复 Profile 备份（若需要）；
- 删除机器本地配置与合成数据（`local/`、`<state-dir>`、隔离 data root）；
- 关闭 Proposal 适配器与 Domain Service。

## 6. 工程说明

- 零第三方运行时依赖、无构建步骤：Host 为普通 ESM `.js`；Client 为单个预构建
  lazy-CJS 模块（`window.__ModuleLoader__.load`），只 `require('react')`。
- 不导入官方 Client 组件库、不替换 app root、不使用 iframe、不新增 Session 事件类型。
- 样式只使用 `--dsw-alias-*` 主题 token；文本走 `ctx.locale`。
- 已知限制与安全边界见 `docs/DETAIL.md` 与 Spike 报告（`docs/evidence/2026-11-13-client-arch/spike-A/SPIKE-REPORT.md`）。
- **环境纪律**：开发中的 `link:` bundle 应优先安装到**专用 dev profile**；若必须共享日常 `desktop` profile，
  按生产变更处理（备份 → `node --check` → `check-tool-schemas.mjs` → 重载 → 最小请求验证），并保留可追溯的
  Git 提交（本 bundle 的源码必须提交，不接受长期未跟踪的全局运行时）。
- 版本耦合：`0.2.2-rc.2` 为预发布版，Slot/Client API 可能破坏性变化；升级前先读 DSH upgrade guide 与
  slot catalog，重建 Client 产物并跑契约回归，保留回滚版本。
