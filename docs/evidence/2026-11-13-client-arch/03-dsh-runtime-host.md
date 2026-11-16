# DSH Runtime / Host / 会话 / 权限 事实级证据报告

> 任务：为 Mammoth Agent 客户端架构三路线评估提供 DSH 0.2.2-rc.2（桌面安装版）Runtime/宿主/会话/权限的**事实级**证据。
> 性质：只读研究。未写产品代码、未安装/启停插件、未修改 Profile、未读取 `~/.dsh` 凭据或业务数据（仅核对环境变量与版本清单）。
> 时间：2026-11-13。报告范围仅为本文档。

---

## 0. 取证环境、路径约定与证据等级

### 0.1 已核实环境

| 事实 | 证据 |
|---|---|
| DSH CLI 版本 `0.2.2-rc.2`，包名 `@deepseek-ai/dsh`，license MIT，`engines.node >= 24.9.0` | `[PKG]/dsh/package.json:4,25,182-184` |
| Desktop 外壳包 `@deepseek-ai/dsh-desktop` 0.2.2-rc.2（Electron），构建 commit `a6b1b85f…` | `[ASAR]/package.json:2-5,28-29` |
| 运行时 Profile = `desktop`；`DSH_INSTALL_DIR` 指向 ASAR 内 CLI 目录；`DSH_NODE_EXECUTABLE` = Electron 可执行文件（Node 模式 24.21.0）；`DSH_HOME` = 用户 DSH 主目录（本机 `<user home>\.dsh`），`DSH_PROFILE_DIR` = `$DSH_HOME\profiles\desktop` | 会话环境变量实测；`[ASAR]/lib/main.js:11550-11558` 设置 `ELECTRON_RUN_AS_NODE=1` |
| 安装形态：`app.asar` 内含 `lib/main.js`（Electron 主进程，848 KB）、`preload-*.cjs`、`dsh/`（CLI+runtime+node_modules）、`renderer/`（welcome/更新弹窗静态资源） | ASAR 目录列举实测 |
| 公开文档交叉核对 | GitHub master `docs/architecture.zh.md`、官方站点 `reference/subsystems/web-client.md` 与本地 0.2.2-rc.2 文档**实质一致**（详见 §10） |

### 0.2 路径约定（正文缩写）

- `[ASAR]` = `D:\Tools\DeepSeek Harness\resources\app.asar`
- `[PKG]` = `[ASAR]\dsh\node_modules\@deepseek-ai`
- `[DOCS]` = `[PKG]\dsh\docs`
- `[HOME]` = `$DSH_HOME`（本机为 `<user home>\.dsh`）
- 行号均为本次实际读取到的行号；`README.md:NN` 指向该包 README（多数为英文 README，中文版为 `README.zh.md`）。

### 0.3 证据等级

- **[已确认]**：本次直接读取安装包源码/文档/清单得到。
- **[需实测]**：源码/文档存在但需要在 Mammoth 目标场景跑通验证（如跨进程 SDK 全流程、审批人接入）。
- **[推断]**：由已确认事实推出，但未直接读到官方声明（如"sandbox 不约束 Host 进程"）。
- **[未知]**：本次未找到证据，不做结论。

### 0.4 取证限制（方法学）

- PowerShell 直接 `Get-Content`/`[IO.File]::ReadAllText` 读 ASAR 路径被沙箱拒绝（`[sandbox: file access denied under workspace-write mode]`）；本报告一律用 Host 的 `read` 工具读 ASAR 内文件，用 `DSH_NODE_EXECUTABLE` + `ELECTRON_RUN_AS_NODE=1` 执行 JS 做目录列举与有界 grep（与 `dsh-doc` 技能 `locate-docs.md` 给出的配方一致）。
- 因此**未对整个包树做穷尽扫描**；"未发现"类结论只表示在指定文档/包内未发现，不等于全仓不存在。

---

## 1. DSH Desktop 的真实架构

### 1.1 分层（Electron 壳 / 私有 Host / Web 应用 / Runtime）

**[已确认]** 官方架构文档把桌面端描述为四层：Electron 应用携带"精确匹配的 dsh 生产运行时"、拥有保留 Profile `$DSH_HOME/profiles/desktop`；Electron 以 **Electron Node 模式启动私有的 Desktop Host**；Host 调用共享 CLI profile runner 与**完整 Web 应用**；窗口先加载打包 Web 资源、等待启动注入后再在同一文档内激活客户端插件；Web 负责 RPC 与流，桌面载体把本地页面连接到已认证 Host；Node IPC 承载启动注入、就绪、致命错误与关闭（`[DOCS]/architecture.md:51-53`；中文对照 `[DOCS]/architecture.zh.md:51-53`）。

**[已确认]** 代码与文档一致，且比文档更具体：

1. Electron 主进程 `spawn` 子进程：
   - 入口 `[ASAR]\dsh\node_modules\@deepseek-ai\dsh-desktop-host\lib\index.js`；
   - 参数 `--expose-internals <entry> <runtimeDir> <projectDir> <primaryRuntime> [pnpm, nodeBin]`；
   - `cwd = projectDir`，`stdio: ['ignore','pipe','pipe','ipc']`；
   - 环境由 `desktopNodeEnvironment()` 注入 `ELECTRON_RUN_AS_NODE=1`（`[ASAR]/lib/main.js:11784-11802`、`11550-11558`）。
2. Host 侧启动流程：
   - `loadProfileDirectory("dsh", projectDir, installAnchor)` 加载 Profile（Desktop 专用加载函数，"不通过 CLI profile 查找暴露"，`[PKG]/dsh-app-boot/README.md:52`）；
   - `runProfile({ environment, profile: "desktop", resolvedProfile, patchFiles: [], args: ["--no-open","--host","127.0.0.1","--port","0"], ... packageManager })`；
   - `[PKG]/dsh-desktop-host/lib/index.js:350-380`。
3. Host 就绪后回传：
   - `url = ctx.connection.authenticatedUrl(`${ctx.webServer.protocol}//127.0.0.1:${ctx.webServer.port}`)`；
   - `injections = ctx.webServer.collectIndexInjections(ctx.connection.browserPeer)`；
   - `process.send({ type: "ready", url, injections })`（`[PKG]/dsh-desktop-host/lib/index.js:515-522`）。
4. IPC 消息集合：`starting` / `ready{url,injections}` / `platform-session` / `shutdown-complete` / `fatal` / 控制类响应（`update-tasks`、`quit-inspection`、`file-drag-resource`），主进程侧解析见 `[ASAR]/lib/main.js:11809-11837`、`20517-20531`；Host 侧见 `[PKG]/dsh-desktop-host/lib/index.js:341-409,499-537`。

### 1.2 桌面端与 `dsh web` 的关系

**[已确认]** 桌面 Host 启动的就是 **Web 应用组合**（`dsh-web-app` 的 profile 组合 + `--host 127.0.0.1 --port 0`），因此：

- 桌面端没有独立的"桌面 RPC 协议"，它复用 Web 的 `ctx.webServer`（HTTP）、Connection（`/api` + 浏览器认证）、API Gateway（`/api/remote.mux` WebSocket 逻辑流）与完整前端 bundle。
- 差异只在**载体**：`dsh --profile web` 让浏览器直接访问 loopback HTTP/WS；Desktop 由 Electron 主进程承担认证与转发（见 §1.3）。
- 同一套 Runtime 还可由 `dsh --profile headless|sdk|acp|web` 启动，五者是同一 launcher 的不同 Profile 组合（`[DOCS]/architecture.md:17,43`；`[PKG]/dsh-app-boot/README.md:52`）。

### 1.3 Web GUI 的 HTTP/WS 服务是谁提供的

**[已确认]** `dsh-host-webserver` 提供 `ctx.webServer`：单个 `node:http`（配置 TLS 时 `node:https`）服务，带具名路由表、可选 gzip、index.html 注入与唯一 fallback；它**只服务浏览器**，且"另一个插件注册所有功能路由，包括 `/api` 桥、插件 bundle、HMR 事件流"（`[DOCS]/subsystems/web-server.md:5`）。

- `/api` 路由、浏览器认证、Host/Origin fence、精确 Fetch 路由由 `dsh-client-connection` Host 侧提供（`[PKG]/dsh-client-connection/README.md:34,41-49`）。
- Remote 方法通过 `@Remote/@RemoteScope` 声明，Host 构建期生成 descriptor/codec，Client 调 `connection.rpc.call('/api','<ns>/<method>',…)` → `POST /api/<ns>/<method>`；流式方法走 `/api/remote.mux` WebSocket（`[DOCS]/api-gateway.md:9-19,60,126-134`）。
- **Desktop 的实际载体（重要，与文档表述有偏差）**：Electron 注册自定义协议 `dsh-app`（`[ASAR]/lib/main.js:1478`），窗口加载 `dsh-app://app/`（`[ASAR]/lib/main.js:20470`）；主进程用 Node `fetch` 向 Host 的 `http://127.0.0.1:<port>` 转发请求并注入认证 Cookie，剥离 `set-cookie` 等连接级头（`[ASAR]/lib/main.js:15266-15305,15348-15379`）。**注意**：`[DOCS]/subsystems/web-server.md:5` 说"Electron 通过 `file://` 加载构建产物并走 IPC 桥"，而 0.2.2-rc.2 代码实际是"`dsh-app://` 自定义协议 + 主进程 fetch 转发 + IPC 传递启动注入"，并非 `file://`。以代码为准，`[已确认]`，文档属滞后概述。
- 桌面窗口在 index.html 注入 `__DSH_BOOT_READY__` promise（`[ASAR]/lib/main.js:15290`），启动注入经 IPC 进入渲染进程；Web 端等价机制是 `window.__DSH_BOOT__` 引导图（`[DOCS]/subsystems/web-client.md:24-26`）。

### 1.4 默认端口与启动方式

- **Web profile**：`dsh --profile web`；启动打印带 fresh process token 的 `dsh web:` URL 并默认打开浏览器；默认端口 **3080**，可 `--port` 改；`--no-open`、`--host`、`--tls-cert/--tls-key`、`--public-url`、`--trusted-host`（`[PKG]/dsh-web-app/README.md:38-44,53-59,69-87`）。
- **Desktop**：默认监听**系统分配端口**（`--port 0`，见 §1.1 代码），Profile 配置可覆盖（`[DOCS]/architecture.md:53`）。
- **唯一受支持的启动器**：受支持的 Node 应用都通过具名 `dsh` profile 启动；"vendored CLI、仅构建/测试可执行文件、**进程内直接挂载插件**、私有 WebWorker 预览都不属于 Harness 应用启动器"，仓库校验脚本会拒绝绕过 `dsh` 的 Node 应用入口（`[DOCS]/architecture.md:43-45`）。

### 1.5 `dsh-desktop-host` 的真实职责

**[已确认]** 包声明：`"description": "Private Node-mode host process for the Electron desktop application"`、`"private": true`、无 README、`files: ["lib/index.js","lib/cli.js"]`（`[PKG]/dsh-desktop-host/package.json:2-12`）。其依赖包括 `@deepseek-ai/dsh`（精确 0.2.2-rc.2）、`dsh-app-boot`、`dsh-client-connection`、`dsh-host-webserver`、`dsh-workspace`、`dsh-plugin-manager` 等（同文件 `13-26`）。

职责（由代码归纳，`[已确认]`）：

1. 读取 Desktop 保留 Profile 目录并调用共享 `runProfile` 启动完整 Web 应用；
2. 用 `--port 0` 绑定 loopback，生成**带启动 token 的认证 URL**，并把 boot injections 经 Node IPC 交给 Electron；
3. 安装 Desktop 专属控制面：更新事务前后的任务检查/准入锁、退出检查、平台账号会话发布、文件拖拽资源授权、插件操作诊断转发（`[PKG]/dsh-desktop-host/lib/index.js:503-522`；主进程侧 `[ASAR]/lib/main.js:11852-11926,20517-20531`）；
4. `lib/cli.js` 是 Desktop 内置 CLI：用 Desktop 携带的 pnpm 执行 `runCli({ manageDesktopProfile: true, packageManager: … })`（`[PKG]/dsh-desktop-host/lib/cli.js:42-70`）。

结论：**`dsh-desktop-host` 是"官方私有的、无产品级文档的完整 in-process 宿主组装器"**；它使用的 `profile-boot`（`runProfile`）是 `@deepseek-ai/dsh` 的公开导出，`dsh-app-boot` 也公开说明其 boot helpers 面向 lower-level embedders，但**认证 URL、boot injections、`dsh-app://` 载体与 Electron 编排这一整套组装**不是给第三方的产品级契约。它正好是路线 B-2 想做的事，但不是给第三方的 SDK。

---

## 2. Runtime 可嵌入 / 可独立运行性（路线 B 关键）

### 2.1 官方支持的"运行 DSH Runtime"路径

| 路径 | 公开性 | 形态 | 能力边界 | 证据 |
|---|---|---|---|---|
| **Profile 启动（web）** | 公开文档 | `dsh --profile web` 起 loopback HTTP/WS，自带官方 Web GUI；可反向代理对外 | 完整官方 Client；自定义 UI 需走内部 Remote API 或替换前端 | `[PKG]/dsh-web-app/README.md:38-44`；`[DOCS]/user/guide/public-deployments.md` |
| **headless** | 公开文档 | 一次性任务，stdout 最终答案 / `--json` NDJSON 事件流，`--session-id` 续跑；不监听端口 | 单任务无交互、无审批响应通道 | `[PKG]/dsh-headless/README.md:12,28-51,77-104,124-136,183-192` |
| **SDK（stdio JSON-RPC）** | 公开文档（TS/Python SDK 文档） | `dsh --profile sdk` / `sdk-minimal`，stdout 仅 NDJSON JSON-RPC 帧 | 见 §2.2，能力**很窄** | `[PKG]/dsh-sdk-app/README.md:12,26-38,65-72`；`[PKG]/dsh-sdk-protocol/README.md:40-58`；`[DOCS]/user/guide/python-sdk.md` |
| **ACP（stdio，标准协议）** | 公开文档 + 标准协议 | `dsh --profile acp`，ACP v1；支持多会话、模型选择、MCP 挂载、取消、权限请求 | 仅标准自动化面，无 DSH 展示数据；无 fork/删除/额外目录等 | `[PKG]/dsh-acp-app/README.md:12,27-38`；`[PKG]/dsh-acp/README.md:12,60-82` |
| **进程内嵌入（runProfile/profile-boot）** | **部分公开/无产品级配方**：`@deepseek-ai/dsh` 公开导出 `./profile-boot`；`dsh-app-boot` 为 public 包并面向 "lower-level embedders and tests"；完整 Desktop Host 组装仅私有 `dsh-desktop-host`（`private:true`、无 README） | 像 `dsh-desktop-host` 那样 `runProfile({profile:'desktop'|'web', args:['--host','127.0.0.1','--port','0']})` | 等于自己重造 Desktop Host：Web 应用 + Connection 认证 + boot injections 全要自己接；无第三方产品级稳定性承诺 | `[PKG]/dsh-desktop-host/lib/index.js:341-380,515-522`；`[PKG]/dsh-desktop-host/package.json:2-8`；`[PKG]/dsh/package.json`（exports `./profile-boot`）；`[PKG]/dsh-app-boot/README.md:12,34` |
| **Python SDK 发行版** | 公开文档 | `deepseek-harness-sdk` wheel 内含打包的 `dsh` CLI，默认起 `dsh --profile sdk`，可显式 `dsh_home`/`profile`/`patches` | 与 SDK 协议同边界；`dsh web` 是另一个独立 CLI 应用，"不能服务 Python SDK 客户端" | `[DOCS]/architecture.md:47`；`[DOCS]/user/guide/python-sdk.md:94-121,148-150,196` |
| **Cordis host runner** | 部分公开 | 进程内动态 Cordis 包注册表/vm 沙箱（`define/run/stop/undefine`），不是应用启动器 | 用于运行时扩展，不是产品级嵌入方案 | `[PKG]/dsh-cordis-host-runner/README.md:12,52-62` |

**关键判断 [已确认/需区分]**：官方文档化的**产品形态**是"**子进程 + 具名 Profile**"与"**协议（SDK/ACP/headless/JSON 事件）**"；`architecture.md:43-45` 明确把"绕过 `dsh` 的 Node 应用入口 / 进程内直接挂载插件"排除在应用启动器之外，仓库用 `verify-application-entrypoints` 强制这一边界。但"进程内嵌入"并非完全没有公开面：`@deepseek-ai/dsh` 公开导出 `./profile-boot`，`dsh-app-boot` 为 public 包且 README 说明其 boot helpers 面向 "lower-level embedders and tests"。**真正私有/无产品级文档的是完整 Desktop Host 组装**（`authenticatedUrl`、boot injections、`dsh-app://` 载体、Electron 进程编排），唯一实现者是私有 `dsh-desktop-host`。因此路线 B 若采用"子进程 + 协议"可走官方文档路径；若复刻完整宿主则属镜像私有实现。

### 2.2 SDK JSON-RPC 协议暴露的能力（能否完全替换官方 Client？）

**[已确认]** 方法集（`[PKG]/dsh-sdk-protocol/README.md:40-58`）：

- client→server：`initialize`、`session/prompt`、`session/query`、`session/resume`、`session/wait`、`session/working-directory/get`、`session/working-directory/set`、`shutdown`。
- server→client：`approval/request`（需 `InitializeParams.approvalRequests: true`）、`user-question/ask`、`user-question.cancelled`、`session.event`（**全部 session 未过滤**）、`session.status`、`session.query.completed`、`subagent.started/finished`。

**[已确认]** 明确缺失与限制：

- **没有显式会话列表/创建/命名/fork/删除/取消方法**；只有"按 `sessionId` 打开或 resume 持久会话"。注意：新 `sessionId` 首次使用会**隐式创建 agent**（`[PKG]/dsh-sdk-jsonrpc-server/README.md:60`）；`session/working-directory/set` 可 "create an unknown Session without starting a model turn"（同 README `:56`）；Python SDK `harness.run(..., session_id="...")` 也是首次使用即创建（`[DOCS]/user/guide/python-sdk.md:113-116`）。但**没有会话枚举、标题、删除**，客户端无法像官方 Client 那样管理会话目录（`[PKG]/dsh-sdk-protocol/README.md:140-142`；`[PKG]/dsh-sdk-jsonrpc-server/README.md:216-219`）。
- **没有取消/会话关闭方法**——客户端只能关闭整个 runtime 进程放弃一轮；`shutdown` 会 dispose 整个根运行时并 exit 0（`[PKG]/dsh-sdk-protocol/README.md:141`；`[PKG]/dsh-sdk-jsonrpc-server/README.md:87,216`）。
- **没有 MCP 配置/管理方法**；工具集由 Profile 组合决定（SDK server README:68；`[PKG]/dsh-sdk-app/README.md:34` 说明 provider/model/cwd 由 initialize 传入）。
- **模型选择只在 initialize 生效**：provider/model/reasoningEffort（+ maxTokens），之后不可切换；`/model` 不是 SDK 命令（`[PKG]/dsh-sdk-protocol/README.md:80`；`[PKG]/dsh-sdk-jsonrpc-server/README.md:38-40`）。
- **无标题、无 UI 展示数据**：SDK 组合甚至禁用模型生成标题（`[PKG]/dsh-sdk-app/README.md:28`）。
- **审批只有 one-shot 机器决策**：`allowed-once|rejected|cancelled`，fail-closed；没有 `approvalRequests` 客户端时审批服务返回 `unavailable` 并拒绝操作（`[PKG]/dsh-sdk-protocol/README.md:30`；`[PKG]/dsh-sdk-jsonrpc-server/README.md:30`）。
- **无协议版本协商**：handshake 只有 `serverInfo.version`（`0.0.1`，客户端不校验），"pre-release stance, no compatibility promise"（`[PKG]/dsh-sdk-protocol/README.md:140`）。
- 只支持一个用户/单 runtime，stdout 纯净性由部署保证；无图形客户端（`[PKG]/dsh-sdk-app/README.md:65-72`）。

**结论 [已确认]**：SDK 协议**不能**完整替代官方 Client。它适合"自有 UI + 自建会话目录/标题/项目模型 + 只把 Agent 轮次交给 DSH"的嵌入方式，但会话生命周期、MCP、模型切换、取消、展示元数据都要 Mammoth 自己在外面补。

### 2.3 ACP 协议暴露的能力

**[已确认]** `initialize`（ACP v1 + `session/list`、`session/resume`、`session/close`、Streamable HTTP MCP）、`authenticate`（立即成功、无鉴权）、`session/new`（绝对 workspace + stdio/HTTP MCP，发布前校验）、`session/list`（持久根会话分页、可按 cwd 过滤）、`session/resume`、`session/close`（静默取消/排空/后代 dispose/flush）、`session/set_config_option`（`model` / `reasoning_effort`）、`session/prompt`、`session/cancel`、`session/update`（committed assistant/thought、工具生命周期、配置变更、上下文用量）、`session/request_permission`（一次性 allow/reject，可自动应答）（`[PKG]/dsh-acp/README.md:60-78`）。

**不支持**：`session/load`、删除、fork、额外目录、SSE/ACP-transport MCP、modes、commands、plans、terminals、client filesystem 操作、elicitation（`[PKG]/dsh-acp/README.md:82`）。ACP 是**自动化协议**，故意不带 DSH 展示数据/计划/标题/待办/终端视图（同文件 `:32`）。

**结论 [已确认]**：ACP 比 SDK 更适合"外部程序驱动持久 Agent"（多会话、list/resume/close、模型选择、MCP、权限请求、取消），但仍是**自动化面**，不足以承载 Mammoth 的完整产品 UI（无 fork、无标题、无计划、无终端、无展示卡片）。

### 2.4 Web 侧内部 API（能力最全，但不是公开契约）

**[已确认]** 官方 Web Client 的 Host 侧由 `packages/api/*-controller` 组成并通过 Typert Remote 暴露，例如：

- `dsh-api-session-controller`：`ctx.sessionController` + 生成的 Client `session`/`skills`/`fileReferences` 命名空间；会话 list/search/create/fork/rename/resume/prompt/queue/cancel/history/follow/control/model 选择（`[PKG]/dsh-api-session-controller/README.md:13,38,50,69,97`）。
- `dsh-api-workspace-controller`：Workspace 增删改排序、Session 归档/取消归档、`follow`（`[PKG]/dsh-api-workspace-controller/README.md:11,25`）。
- `dsh-api-workspace-files`（只读文件预览/监视/搜索，无 mutation）、`dsh-api-terminal-controller`（交互终端）、`dsh-api-settings-controller`（settings/credentials 脱敏读写）、jobs、account 等（各自 README Summary）。
- 客户端组装：`api-remotes` 在**构建期**选定哪些 Remote contribution 进入应用；Host 暴露给某个 Client 的方法"仅限生成期选定的 Remote 方法"（`[DOCS]/api-gateway.md:80-82`）。Remote descriptor/codec 是该包 `lib/typert.host.js` / `lib/typert.remote-client.js` 的生成产物，**不是独立的版本化公开 schema 文件**（`[DOCS]/api-gateway.md:108-118`）。

**结论 [已确认]**：能力最全的是**官方 Web Client 的内部契约**；它没有"公开 API/兼容承诺"，并且 0.2.2 的升级指南里 Remote/Client API 破坏性变更是常态（见 §8）。

### 2.5 自有 UI 替换官方 Client 的可行组合（供路线 B 设计参考）

- **B1：自研壳 + SDK/ACP 子进程 + 自研 UI**（官方文档化的进程边界，最稳）：用 SDK（窄）或 ACP（较宽）驱动 `dsh` 子进程；Mammoth 自己实现项目/会话目录/审批 UI/展示；需自建会话元数据（标题、归属项目）。
- **B2：自研 Electron 壳 + 内嵌 Web 应用（复刻 Desktop Host）**（能力等价官方客户端，但非公开契约）：自研进程内 `runProfile`+`--port 0`+`authenticatedUrl`+boot injections；Electron 主进程转发认证请求。工作量与升级风险都最高。
- **B3：自研壳 + `dsh --profile web` 子进程 + 自有前端**：官方 Web GUI 由 DSH 提供（iframe/窗口），自有 UI 走内部 Remote API（需自己实现 Connection 认证+生成的 Remote 客户端，且每次 rc 重新对齐）。
- **B4：仅 headless/SDK 做"后端 Agent 服务"，完全自研 UI**：DSH 退化为 Agent Runtime，不提供会话/项目/审批界面。

---

## 3. 会话管理

### 3.1 模型与生命周期

**[已确认]**

- Session = **append-only `SessionEvent` 日志**（唯一事实来源），LLM 消息历史由日志派生；`turn/start`、`step/start`、`user/message`、`system/message`、`assistant/message|attempt`、`tool/call|result`、`request/header|context` 等是持久事件（`[DOCS]/subsystems/session.md:5-7,27-150`）。
- **创建**：`session.create`；指定 `sessionId` 时，仅当它仍 blank（无 turn 开始）、属于请求的目录、是请求 Workspace 的未归档成员且无其他写者时才复用，否则新建；同一身份的创建按到达顺序串行（`[PKG]/dsh-api-session-controller/README.md:50`）。
- **恢复**：冷会话自动 resume、并发 resume 去重、subagent 路由身份拒绝（`[DOCS]/api-gateway.md:132`）；SDK `session/resume` 会修不完整末轮并保留原目录（`[PKG]/dsh-sdk-jsonrpc-server/README.md:32`）；ACP `session/resume` 校验 canonical workspace（`[PKG]/dsh-acp/README.md:68`）。
- **fork**：把 `atSeq` 选定的**闭区间事件前缀**复制为独立 Session，**不带 `parentSession`**；省略 `atSeq` 取最近完成轮及其 tail；不存在的 event 拒绝；底层 API 为 `ctx.agents.create({ sessionId, seed, inheritedEventCount, meta:{isSeeded:true} })`（`[PKG]/dsh-api-session-controller/README.md:69`；`[DOCS]/architecture.md:167`）。
- **命名/标题**：`rename` 是 Session controller 的会话操作之一（`[PKG]/dsh-api-session-controller/README.md:38`）；标题以 `session/title` 事件持久化，导出时取最新标题（`[PKG]/dsh-session-log-export/README.md:46`）。
- **归档而非删除**：Workspace 层提供 `archiveSession`/`unarchiveSession`；归档可要求停止活动工作（`stopActivity`），并以后台方式停止 turn/subagent/job/schedule（`[PKG]/dsh-api-workspace-controller/README.md:11,25`）；归档行保留排序位、置灰、需 unarchive 才能打开（`[DOCS]/subsystems/web-client.md:52`）。
- **删除**：`[已确认-未发现]` 本次在 Session controller README/typert 类型与 `docs/` 中**未发现** `session/delete` 或 `deleteSession` 公开方法；`[未知]` 是否存在未文档化的删除/清理路径（如手动删文件、Workspace remove 不删 Session 历史——`[PKG]/dsh-api-workspace-controller/README.md:11` 说明 remove Workspace 不删文件夹/文件/会话，`[PKG]/dsh-workspace/README.md:12`）。评估时应按"DSH 没有正式会话删除 API，只有归档"处理。

### 3.2 存储位置与格式版本

**[已确认]**

- 持久化根：`dsh-base` patch 里 `session-persistence-jsonl` 的 `root: !!js dshHomePath('sessions')` → `$DSH_HOME/sessions`（默认 `~/.dsh/sessions`）（`[PKG]/dsh-base/cordis.patch.yml:138-141`；`dshHomePath` 语义见 `[PKG]/dsh-anonymous-user-id/README.md:12` 对 `$DSH_HOME` 的说明）。SDK 部署使用显式 `dsh_home`，session 日志在其 `sessions/` 下（`[DOCS]/user/guide/python-sdk.md:94`）。
- 目录布局：`<root>/--<normalized-cwd>--/<encoded-id>/session.vN.jsonl[.zstd]`；每个规范 generation 以物理 header 开始，header 版本=文件名版本；v0 用 `session.jsonl[.zstd]`，v1+ 用 `session.vN.jsonl[.zstd]`；已提交 generation 路径**绝不重命名/替换/删除**；默认 `compression: 'zstd'`（checksummed frames），可 `'none'` 得纯文本行（`[PKG]/dsh-session-persistence-jsonl/README.md:12,43-70`；`[DOCS]/architecture.md:128`）。
- **当前逻辑格式 = V4**：安装内含 `dsh-session-format-v0-to-v1 … v3-to-v4` 迁移包；`v3-to-v4` 文档标题即"Restore supported released V3 Sessions as V4…then separates native V4 admission"，格式目录 `historicalSessionFormatCatalog` 负责恢复 V0–V3（`[PKG]/dsh-session-format-v3-to-v4/README.md:12`；`[PKG]/dsh-session-format-catalog/README.md:50,73`）。**文档滞后点**：`dsh-session-persistence-jsonl/README.md:57-70` 的磁盘布局示例只列到 `session.v3`（标注"released v3/current；later versions use vN"），与 V4 迁移包并存——按"当前 V4、物理代 = 版本号命名"理解，`[已确认-存在文档版本差异]`。
- 迁移策略：只读 open 用内存结果不发布后继；写 open 先编码校验再排他发布"最终版本命名"的后继；读取拒绝未来版本；JSONL provider 只做物理 framing/压缩/代选择/排他发布（`[DOCS]/architecture.md:128`）。

### 3.3 并发与外部管理

**[已确认]**

- 同一 Host 可并发多会话；ACP 明确"一个连接可同时跑多个相互独立的会话"，每会话单条 in-flight prompt（`[PKG]/dsh-acp/README.md:60-78`）；Web 端多 Session 并行、follow/control 流按会话（`[DOCS]/subsystems/web-client.md:42-52`）。
- **外部管理能力**：SDK 仅"用已知 id 打开/续跑/等待/改工作目录"（§2.2）；ACP 有 create/list/resume/close（§2.3）；Web Remote API 有 list/search/create/fork/rename/archive 等（§2.4）。**没有公开的"会话管理 REST/CLI"**；`dsh` CLI 没有会话子命令（本次未发现；`dsh` 命令面见 `[PKG]/dsh/package.json` bin `lib/bin.js`）。
- **日志导出**：Web 端 Session Header 菜单 `Copy Session as Markdown` / `Download session log` → `dsh-session-<id>.zip`（含子会话与附件），或 `/export` 命令；后端另有纯函数 `formatSessionMarkdown(events, sourcePath?)`（`[PKG]/dsh-session-log-export/README.md:12,28-46`）。持久化 seam 本身要求实现 `create/open/stat/list/export`（`[DOCS]/architecture.md:168`）。
- **读取/检索**：`ctx.sessionQuery` 提供 list/filter/readSession/readSurface/readEvent/trace/search（全文检索需 SQLite 后端）（`[PKG]/dsh-session-query/README.md:12,32-51`）。

---

## 4. 项目上下文注入（workspace / AGENTS.md / skills / MCP）

### 4.1 workspace / cwd 如何确定

**[已确认]**

- Session 记录**原始项目目录**（header 的 cwd）与**有效工作目录**（projection）；`working_directory` 工具只改后续操作目录，不改变原项目标识、沙箱写根或已有进程目录（`[PKG]/dsh-working-directory/README.md:12,44`；`[DOCS]/architecture.md:134`）。
- 各入口的 cwd 来源：SDK `initialize` 的 workspace cwd（`[PKG]/dsh-sdk-app/README.md:34`）；ACP `session/new` 的绝对 `cwd`（`[PKG]/dsh-acp/README.md:66`）；headless 用 `fs.resolve('.')`/进程 cwd，续跑时比对记录 cwd（`[PKG]/dsh-headless/README.md:77`）；**Desktop Host 以 `cwd: projectDir` 启动**（`[ASAR]/lib/main.js:11794` + `[PKG]/dsh-desktop-host/lib/index.js:350-354`）。
- 沙箱写根 = 会话不可变 cwd（`[DOCS]/subsystems/sandbox.md:43`）。
- Web/Desktop 的"工作区"实体是 `ctx.workspaceRegistry`：**命名目录 + 会话成员关系**的有序持久列表，可建/改名/排序/移除（移除不删目录、文件、会话），对模型不可见（`[PKG]/dsh-workspace/README.md:12,28,49-60`）。

### 4.2 AGENTS.md 指令链

**[已确认]** `dsh-agent-instructions`：首个请求注入一条持久 baseline 消息，内容为 user-global 链（`$DSH_HOME/AGENTS.md` → `<agentsHome>/AGENTS.md`）+ project 链（项目根到 session 工作目录的每一级，broad→specific）；默认候选 `AGENTS.md`/`CLAUDE.md` + 本地 overlay `AGENTS.local.md`/`CLAUDE.local.md`；项目根由 `.git` 标记探测；两条链各受 `maxBytes` 限制（base 默认 65,536 B/链）；成功 `read/write/edit` 发现更深目录会插入新指令文件，变更/删除产生替换/移除通知；`dsh-base` 默认启用（`[PKG]/dsh-agent-instructions/README.md:12,30-42,64-68`；其中 `:12`/`:30` 明确 `dsh-base` 默认启用该插件并给 65,536 B/链预算）。

### 4.3 Skills 加载

**[已确认]** `dsh-skill-filesystem` 按 rank 扫描根：`<projectRoot>/.dsh/skills`(100)、`<projectRoot>/.agents/skills`(200)、`customSkillDirs`(300)、`<dshHome>/skills`(400)、`<agentsHome>/skills`(500)、bundled(600)；项目根 = 最近含 `.git` 的祖先，否则 cwd；skill = `<name>/SKILL.md` 或 `<name>.md`（不递归发现嵌套 SKILL.md），YAML frontmatter 需 `name`/`description`，可选 `whenToUse`/`metadata`/`disable-model-invocation`/`user-invocable`；发现只解析 frontmatter，加载时重读正文；目录被 watch，无需重启（`[PKG]/dsh-skill-filesystem/README.md:12,34-56`）。打包技能使用独立自定义根，同名项目技能可覆盖（`[PKG]/dsh-web-app/README.md:99`）。

### 4.4 MCP servers

**[已确认]** 每个 server 一条 `dsh-mcp-client` 配置行，`transport: stdio | streamable-http`；默认**不启用任何 server**；工具名 `mcp__<serverName>__<tool>`；`dsh-base` 只挂一次 `dsh-mcp-resources`（资源发现/读取），客户端条目按需配置；server instructions 作为字面文本进入 system prompt；MCP prompt templates 不支持；stdio 有临时探测进程；可配 `failOnStartupError`、`reconnect`、超时（`[PKG]/dsh-mcp-client/README.md:12,28-78`；`[PKG]/dsh-base/README.md:68`）。另有配置式 MCP bundle 模板（仅配置、无代码）可参考 `[PKG]/dsh-agent-preset/skills/dsh-doc/references/mcp-bundle.md`（技能内文档，未展开）。

### 4.5 插件能否为每个会话注入 Mammoth 项目上下文？

**[已确认]** 可以，官方扩展点齐全：

1. **system prompt section**：`ctx.systemPrompt.section({name, order, text|resolve})`，section 按 order 拼接；agent 级注册（`agent.ctx`）只影响该 agent 并遮蔽同名全局（`[PKG]/dsh-system-prompt/README.md:12,28,54-60`）；全局 persona 前缀/后缀与 toolOrder 由配置控制（同文件 `44-52`）。
2. **agent.inject()**：把上下文落到下一次获批请求，登录为 sourced `user/message`（`[DOCS]/architecture.md:161`；`[DOCS]/subsystems/session.md:48-55` 说明 `agent.inject()` 属 user-role synthetic context）。
3. **workspace 指令文件**：每个会话自动加载 AGENTS.md 链（§4.2），插件只需分层放置文件。
4. **skills**：按项目根放置 `.dsh/skills` / `.agents/skills`（§4.3），或由自定义 provider 提供（`dsh-skill` registry 接受任意 provider）。
5. **MCP resources / tools**：`dsh-mcp-resources` + `dsh-mcp-client`（§4.4）。
6. **per-session 能力集合**：agent preset 声明 `plugins`（工具/persona/prompt section/policy），preset 在 Host 或 profile 层挂载并可按会话选择（`[PKG]/dsh-agent-preset/skills/dsh-doc/references/compositions.md:9,28-47,73-75`）。

**[已确认-重要缺口]** DSH **没有跨会话持久的"项目（Project）业务模型"**：`workspace` 只是"命名目录+会话成员"的 UI 分组（§4.1），Session 只记 cwd；`AGENTS.md` 由目录层级决定而非业务项目实体。Mammoth 的"客户/产品/项目/版本"必须由 Domain Service 自建；DSH 侧只能通过（a）把项目目录映射为 workspace/cwd 与 AGENTS.md/skills 注入，（b）用 MCP/工具把 Domain Service 接入，（c）用 systemPrompt/agent.inject 注入项目上下文。这是**产品级缺口而非实现细节**。

---

## 5. 工具与审批/权限边界

### 5.1 工具注册与 schema

**[已确认]** 工具 = `ToolDefinition`：模型可见字段（name/description/parameters）+ 强制 `output`（canonical JSON schema + render）+ `execute(args, exec)`；host-only 元数据（`timeoutMs`、`isConcurrencySafe`、`presentCall/presentResult`）被 `schemas()` 白名单排除，绝不进入模型请求；插件用 `defineTool` + 统一 JSON value schema DSL（string/number/integer/boolean/null/array/object/json/oneOf 等）声明参数与输出（`[DOCS]/subsystems/tools.md:5-12,25-120`）。注册即声明：把工具注册到 `ctx.tools`，其 schema 进入 prompt 组装（`[DOCS]/architecture.md:151`；`[PKG]/dsh-system-prompt/README.md:28`）。

### 5.2 审批与权限预设

**[已确认]**

- **审批 outcome 闭集且 fail-closed**：`allowed-once | rejected | cancelled | unavailable`；`unavailable` 一律按拒绝（`[DOCS]/subsystems/approval.md:11-29`）。
- **每会话审批策略**：`ask`（默认，交给 answerer 链）/`never`（确定性拒绝、不分发）；有效值取会话日志最后一条 `approval/policy` 事件，`setApprovalPolicy` 是唯一写路径（`[DOCS]/subsystems/approval.md:31-49`）。
- **审计**：`ctx.approval.request()` 要求会话处于打开的 turn 内，写入 `approval/asked` 与 `approval/decided` **log-only** 事件对（不进模型 transcript）（`[DOCS]/subsystems/approval.md:84-88`）。
- **answerer 来源**：Web/Desktop UI；ACP 一次性机器决策；SDK `approval/request`（需显式开启）；没有 answerer 时 fail closed（`[DOCS]/subsystems/approval.md:5`；`[PKG]/dsh-sdk-jsonrpc-server/README.md:30`；`[PKG]/dsh-acp/README.md:74`）。
- **权限预设**把两个独立强制旋钮打包：sandbox mode + approval policy；默认表 `workspace-write`(=workspace-write+ask) 与 `danger-full-access`(=danger-full-access+never)，`custom`/`auto` 保留（`[DOCS]/subsystems/permission-presets.md:5,11,45-55,73-75`）。preset 变更写 log-only `permission/preset` + 各自旋钮事件（同文件 `71-75`）。

### 5.3 沙箱策略如何生效

**[已确认]**

- 模式：`read-only` / `workspace-write` / `danger-full-access`；**只管文件效果**（网络与进程可见性不在词汇表内）；`danger-full-access` 直接 spawn、不经过 `ctx.sandbox`（`[DOCS]/subsystems/sandbox.md:9-23`）。
- 强制完整性是**上报事实**：`full` / `partial`；Windows ACL 后端存在"hard-link、unconfined-read、AppContainer-ACL"边界，属 current partial；Windows 子进程忽略 extra writable roots，但 harness 文件工具独立强制执行（`[DOCS]/subsystems/sandbox.md:25-43,60-62`；`[DOCS]/upgrade-guide/README.md:74` 的 v0.2.1-rc.4 条目亦确认"Windows processes ignore additional writable roots"）。
- 每次调用**按调用**解析策略：`ctx.sandboxPolicy.resolve()` 以调用会话的不可变 cwd 为 workspace root，可带 `additionalWritableRoots`，审批后的升级重试作为新调用携带更宽策略（`[DOCS]/subsystems/sandbox.md:41-102`）。
- 平台后端：Linux bwrap/Landlock、macOS Seatbelt、Windows ACL restricted token（`[DOCS]/subsystems/sandbox.md:5`）；文件工具另有 `dsh-fs-sandbox`、`dsh-pwsh-sandbox`、`dsh-sandbox-windows-acl` 等包。
- 工具流水线扩展点：`tool/call → tools/pre-execute → tools/execute → tools/post-execute → tool/result`，其中三个 `tools/*` 是 waterfall（监听器必须 `next()` 才委托），可用于策略/拦截（`[DOCS]/architecture.md:101,108`）。

### 5.4 企业级安全承载判断（领域写操作）

- **[已确认]** DSH 的"权限"只有**文件沙箱 + 工具级审批 + 每会话预设**；**没有字段级/对象级业务权限、没有用户身份与 RBAC、没有领域规则引擎**。审批是"这个人是否允许这次工具调用"，不是"这个业务动作是否合规"。
- **[已确认]** 沙箱不对 Host 插件进程生效——插件是 Host 进程内代码（官方文档：安装 bundle 会在 Host 进程执行插件代码，安装需要 Full access 或审批，`[PKG]/dsh-agent-preset/skills/dsh-doc/references/compositions.md:69`；插件模型见 `[DOCS]/architecture.md:9-11`）。因此"把领域写操作放进 Mammoth 插件"= 绕过文件沙箱，只能靠插件自身实现业务授权。
- **[推断]** 因此领域写操作必须走 Mammoth Domain Service（HTTP API/MCP），由服务端做身份、权限、审计与业务校验；DSH 只作为"调用方 + 展示 + 审批（人）"层。这与仓库 AGENTS.md 的"业务状态/权限/正式数据变更必须由领域服务实施"一致，也是证据支撑的结论而非产品偏好。
- **审计读取点**：会话日志中的 `tool/call`、`tool/result`（含 `error.name/code/reason`）、`approval/asked/decided`、`turn/*` 等持久事件（`[DOCS]/subsystems/session.md:89-150`；`[DOCS]/subsystems/approval.md:86-88`）；读取用 `ctx.sessionQuery`/导出（§3.3）；另有可选 OTel 会话遥测（`[PKG]/dsh-base/README.md:50`）与 Desktop 侧插件操作诊断日志（`[ASAR]/lib/main.js:20524-20531`）。**注意**：没有独立"安全审计服务"，审计=会话事件日志。

---

## 6. 本地数据与 Domain Service

### 6.1 插件/Host 能否访问 localhost HTTP 与 stdio？

**[已确认-官方配置路径]** 可以，通过 MCP 客户端：

- `transport: streamable-http` + `url: http://localhost:3000/mcp` + `headers` 是文档示例（`[PKG]/dsh-mcp-client/README.md:45-53`），MCP 客户端由 Host 插件在 Host 进程内实现连接；本地服务须事先运行（中文版 `[DOCS]/user/guide/mcp-memory.zh.md:11` 明确 DSH 不监管独立 HTTP 服务）。
- `transport: stdio` 启动本地子进程，随插件生命周期启动/停止（`[PKG]/dsh-mcp-client/README.md:59-60`；`[DOCS]/user/guide/mcp-memory.md:11`）。
- `web_fetch` 自 v0.2.1-rc.2 起**不再拒绝非公开目的地**（loopback/私网可访问）（`[DOCS]/upgrade-guide/README.md:129`；`[PKG]/dsh-base/README.md:50` 亦说明 "Web fetch runs without per-call approval and can reach loopback and private-network hosts"）。
- **[推断]** 插件 Host 代码本身运行在 Node Host 进程，可直接 `fetch`/`node:http`；DSH 没有对 Host 进程出网的策略层（沙箱只约束 spawn 的子进程与文件工具）。此点无官方明文，按架构事实推断，标 `[需实测]` 若 Mammoth 依赖它。

### 6.2 storageDomain 的语义

**[已确认]** `ctx.storageDomain`（= `ctx.storage.domain`）是 **schema 校验的 KV domain**：owner 用 `defineDomain`（name/version/zod tables）声明，消费方 `open` 后同步读、写成功即持久并保序发 `domain/changed`；数据是 **host-side 状态**，"不添加工具、prompt 或 session 事件，对模型和 agent loop 不可见"；官方明确"数据若属于会话事件日志，应交给 session persistence"（`[PKG]/dsh-storage-domain/README.md:12,28-32,36-58`；`[DOCS]/subsystems/storage.md:5,24`）。
- 用途定位：workspace 记录、session sidecar metadata 等非权威/辅助状态（`[PKG]/dsh-storage-domain/README.md:28`）；**不是**业务主数据存储，也不提供事务/多租户/服务端授权。Mammoth 的客户/项目/工作项等**权威数据不应放这里**。

### 6.3 本地文件工作区与桌面文件能力

**[已确认]**

- `ctx.fs` 文件系统 seam（provider 可换），工具层 `dsh-tool-fs`、`dsh-tool-fs-search`、`dsh-tool-str-replace-editor`、`dsh-fs-sandbox` 等（包清单）。
- Web GUI 文件服务：`dsh-api-workspace-files` 只读（分页读文本、字节窗口、元数据、文件/目录 watch、按名/相对路径搜索；目录列举/搜索限 workspace 内），**无 mutation**（`[PKG]/dsh-api-workspace-files/README.md:12`）。
- 终端：`dsh-api-terminal-controller` 从 Web 侧栏在会话 cwd 打开 shell，重连既有进程，agent 工具也能开终端（受会话沙箱策略，首个人工输入后交给用户）（`[PKG]/dsh-api-terminal-controller/README.md:12`）。
- 桌面文件能力：Electron 侧 `dsh-app://` 转发 + 文件拖拽资源授权（Host 用组合文件系统实际读操作授权 `dsh-resource://file/session/<id>/<path>`，`[PKG]/dsh-desktop-host/lib/index.js:108-120,63-106`；主进程 `file-drag-resource` 控制请求 `[ASAR]/lib/main.js:11884-11890`）。

---

## 7. 多用户 / B/S 演进

### 7.1 公开部署形态（官方文档）

**[已确认]** `docs/user/guide/public-deployments.md` 定义的公开部署 = **单进程、loopback 监听 + 反向代理**：

- `dsh --profile web --public-url https://app.example/ui/ --trusted-host app.example`；`--public-url` 只影响"打印/打开的启动 URL、`DSH_WEB_URL`、agent 运行时上下文里的 GUI URL"，不授予信任；`--trusted-host` 才是信任的 authority（`[DOCS]/user/guide/public-deployments.md:5-9,13-25`）。
- 代理要求：转发原始 `Host`、剥离挂载前缀、转发所有 WebSocket upgrade、重写 cookie Path/加 Secure、`/ui/` 必须是唯一入口（打开 token 换 cookie）（同文件 `15-21`）。
- 认证：**launch token + 签名 session cookie**；"printed URL 携带进程凭据，只分享给目标用户"（同文件 `25,35`）；TLS 可在代理/监听器/两者终止，DSH 不管证书（`29-33`）。
- 单租户：`dsh --host` 拒绝 wildcard；非 loopback 无 TLS 会告警（`[PKG]/dsh-web-app/README.md:69-71`；`[DOCS]/subsystems/web-server.md:102`）。

### 7.2 认证/鉴权模型

**[已确认]**（`[PKG]/dsh-client-connection/README.md:41-51,80-83`）

- 每个 Host 进程生成随机 launch token；`GET /`（或 `/connect` API 入口）用 token 换**绑定 authority 的签名 cookie**（HttpOnly/SameSite=Strict/默认 30 天），cookie 签名密钥存在 owner 专属 credential 记录（本地 provider → `$DSH_HOME/.credentials.yaml`）。
- 每个请求先是 **Host/Origin fence**（loopback / bind IP / `trustedHosts`；`Origin` 必须同 authority；`sec-fetch-site: cross-site` 拒绝），再是 **cookie 认证**；两者都"绝不建立身份"。
- 所有请求代表一个 `Peer`：`principal` 分 `owner` 与多种"非 owner principal kind"（业务包可为某个 kind 注册 policy）；`ctx.connection.browserPeer` 是**共享的 owner principal**（认证浏览器连接共用）；API 调用者通过 `/connect?token=...&interactions=answer|none` 建立独立 Peer，但仍是 **owner principal**。
- **多用户相关能力**：`dsh-api-remote-access` 为**非 owner principal** 提供方法级 RBAC（role/action/resource + binding/condition），支持列表过滤、每 Peer access snapshot；设计场景是"liveshare 观察者/受限页面"（`[PKG]/dsh-api-remote-access/README.md:12,27-48,68`）。owner 永远不检查。
- **没有**：账号体系、登录/登出（"There is no logout operation"，只能清 cookie 或删 owner credential 重启）、按用户隔离的会话库、多租户、管理员/角色管理、用户级审计。`dsh-authorization` 是**模型凭据获取流程**（OAuth/API key），与用户鉴权无关（`[PKG]/dsh-authorization/README.md:12,28-32`）。`dsh-anonymous-user-id` 是匿名安装标识，仅用于遥测/反馈/DeepSeek 请求关联，**不是身份**（`[PKG]/dsh-anonymous-user-id/README.md:12,28-37`）。
- ACP `authenticate` 立即成功、服务端无鉴权（`[PKG]/dsh-acp/README.md:65`）；SDK 无鉴权层（stdio 信任边界由调用方进程隔离）。

### 7.3 "未来做独立 B/S Agent 平台"：DSH 能给什么、不能给什么

**能给（[已确认]）**

1. 完整 Agent Runtime：模型适配器、工具流水线、会话日志/恢复/fork、审批、沙箱、压缩、子代理/Team、schedule、技能、MCP（§1-§5）。
2. Web 应用与部署形态：loopback + 反向代理 + TLS + 单进程凭据（§7.1）。
3. 可编程边界：headless NDJSON、SDK stdio、ACP stdio（§2）。
4. 可替换前端的技术可能：Web Client 是插件化 Cordis 应用（Client Modules/Slots/Conversation，`[DOCS]/subsystems/web-client.md`），插件可注册 Slot/UI 包——但这是"在官方 Client 内扩展"，不是"公开 UI SDK"。

**不能给（[已确认]）**

1. 多用户账号/隔离/RBAC/SSO/租户；每 Host 一个 `DSH_HOME`、一个 owner。
2. 业务领域模型、业务权限、领域审计（DSH 审计=会话事件）。
3. 长期稳定的公开 API/SDK：SDK/ACP 是 pre-release 且能力窄，Web Remote 是内部生成契约（§2、§8）。
4. 服务化部署形态（无 K8s/多副本/水平扩展语义；会话文件在本地 `$DSH_HOME`；SQLite 查询后端为本机文件）。

**演进建议（证据驱动，不是偏好）**：若 Mammoth 要做多用户 B/S，DSH 适合作为**每用户/每租户一个 Runtime 进程（或每会话一个）的 Agent 执行单元**，通过 ACP/SDK 由 Mammoth 服务端编排；账号、项目、领域权限、审计、数据必须落在 Mammoth 自己的服务端。DSH 侧的 `workspace`/`storageDomain` 不足以承担业务主数据（§6.2、§4.5）。

---

## 8. 上游升级耦合

### 8.1 版本与依赖耦合

**[已确认]**

- Desktop 携带"精确匹配的 dsh 生产运行时"；Desktop 与 npm CLI 共享产品数据但**包、启用选择、锁文件保持独立**；Desktop 内置 CLI 管理其已初始化插件（`[DOCS]/architecture.md:51`）。
- `@deepseek-ai/dsh` 的运行时依赖几乎全部**精确 pin** 到 `0.2.2-rc.2`（`[PKG]/dsh/package.json:26-117`）；`dsh-desktop-host` 亦 pin `@deepseek-ai/dsh: 0.2.2-rc.2`（`[PKG]/dsh-desktop-host/package.json:16`）。
- 插件/ bundle 兼容性：先读 `engines.dsh`（存在则**只有它**决定），否则校验所有 `@deepseek-ai/dsh-*` peerDependencies；prerelease 参与范围匹配；无声明则无约束；空/非法范围（含 `engines.dsh: workspace:`）视为不兼容；**不是**恶意代码沙箱（`[PKG]/dsh-app-boot/README.md:54`）。
- 例外机制：profile 的 `compatibility.json` 按 `package-name@version → DSH runtime versions` 精确豁免；插件升级或 DSH 升级都不继承；文件损坏则只读并要求人工修复（`[PKG]/dsh-app-boot/README.md:58`；`[PKG]/dsh-official-bundles/README.md:35-44` 说明官方/按需/退役 bundle 列表）。
- Profile 模板：`web/headless/acp/sdk/sdk-minimal` 由 `PROFILE_TEMPLATES` 定义（`[PKG]/dsh-official-bundles/README.md:31`）；`dsh-base` 为共享第一层（`[PKG]/dsh-base/README.md:12`）。

### 8.2 rc 节奏与破坏性变更密度

**[已确认]** 本安装自带完整升级指南（`docs/upgrade-guide/` 每个版本一节，v0.1.7-rc.2 → v0.2.2-rc.1）：

- **v0.2.2-rc.1 一节列了 19 条**需改动项，涉及：账号通知 API、`BundleInfo.update`、`Session.append` 重载、运行时上下文 reminder 记录、内置配置关闭 `interrupt_agent`、终端 bundle 侧栏、Forge diff/失败码、web search 计费、窗口拖拽区域等（`[DOCS]/upgrade-guide/README.md:7-29`）。
- **v0.2.1-rc.2 一节列了 ~40 条**，含：Node 24 强制、`engines.dsh` 读取顺序、Session 模型选择生效时机、Approval 依赖 Projection registry、条件工具按 step 准入、`web_fetch` 可达私网、SDK 桌面工具、schedule Host API 等（同文件 `94-131`）。
- 语义化承诺缺位：SDK 协议"no protocol-version negotiation、pre-release stance、no compatibility promise"（`[PKG]/dsh-sdk-protocol/README.md:140-142`）。
- 官方文档本身是**随包生成**的：`upgrade-guide/README.md` 由 `@deepseek-ai/dsh` 打包时生成（同文件 `3`）；`config-catalog.md`、`tool-catalog.md`、`persistence-catalog.md` 也由脚本生成（多份文档头注）。

### 8.3 两条路线的升级风险差异

| 维度 | 路线 A：以 DSH Desktop 为宿主（bundle/插件扩展） | 路线 B：自研壳嵌入 DSH Runtime |
|---|---|---|
| 升级由谁推送 | DeepSeek 桌面自动更新（`electron-updater`，`[ASAR]/package.json:13`；更新渠道 `nightly`，`21-27`）；运行时随应用更新 | Mammoth 自己决定何时升级 DSH 包 |
| 契约面 | 插件/bundle API + Profile patch + Client Slot/UI 包 + preset（官方文档面） | "子进程 + 协议"面公开但 pre-release；完整进程内组装（`runProfile` 组合/`dsh-desktop-host`/boot injections/Remote 生成契约）无产品级稳定性承诺 |
| 破坏性变更暴露 | 每次 rc 的升级指南条目直接命中插件/UI 包（§8.2 列表里多数是插件面） | Web Remote/Client API 破坏性变更是常态，且没有"第三方契约"文档；SDK/ACP 同样 pre-release |
| 兼容闸门 | `engines.dsh` + `compatibility.json` + 启动期拒绝（`[PKG]/dsh-app-boot/README.md:54-58`） | 自己实现兼容闸门；公开低层 boot helper 无产品级 API 文档，完整组装只能看源码或镜像 desktop-host |
| 回滚 | 回滚桌面版本/禁用 bundle；profile 层 patch 可回退 | 自己控制包版本，但需重建镜像宿主代码 |
| 不可控点 | Desktop 保留 Profile `profiles/desktop` 由壳管理（`[DOCS]/architecture.md:51`）；插件安装/供应链风险（安装执行 Host 代码） | 需持续跟踪私有实现（`dsh-desktop-host` 每次随 runtime 变化，`[PKG]/dsh-desktop-host/package.json:4`） |

**[已确认]** 官方把第三方扩展的**推荐姿势**放在 bundle/插件/Profile 层（"新行为附加到已文档化的扩展点"、`verify-application-entrypoints` 禁止绕过 launcher 的 Node 应用入口，`[DOCS]/architecture.md:43-45,144-171`）。这构成路线 A 的结构性优势，也构成路线 B 的结构性风险。

---

## 9. 路线 A/B/C 可行性要点

> 说明：本节按任务上下文默认三路线理解为：**A = 以 DSH Desktop 为宿主（bundle/插件 + 可选 Domain API）**；**B = 自研 Electron 壳集成 DSH Runtime（自有 UI）**；**C = 独立 B/S Agent 平台（DSH 作为可替换 Runtime 或仅借鉴）**。若定义不同，结论按对应小节证据调整。

### 9.1 路线 A（DSH Desktop 为宿主）

**可行性：高（官方唯一文档化的产品化扩展面）[已确认]**

支撑证据：
- 扩展点完备：bundle patch、Host 插件（服务/工具/prompt/fs/sandbox/审批）、preset（per-session 能力）、Client UI Slots/Conversation 扩展、MCP、skills（§4.5、§5.1；`compositions.md:73-75`；`[DOCS]/subsystems/web-client.md`）。
- 官方安全默认：workspace-write + ask（`[DOCS]/subsystems/permission-presets.md:11`）；审批/审计日志在会话事件里（§5.2）。
- 桌面更新与运行时由 DeepSeek 维护（`[DOCS]/architecture.md:51-53`）。

风险/限制：
- 与 Desktop 更新节奏强绑定：插件/UI 面每 rc 有破坏性变更（§8.2），必须持续跟踪 `engines.dsh` 与升级指南。
- "项目"不是 DSH 概念，需要 Mammoth 在插件层自建 Domain Service + UI（§4.5）；业务数据不能放 `storageDomain`（§6.2）。
- 插件代码在 Host 进程执行、安装即执行 Host 代码（供应链/授权风险，`[PKG]/dsh-agent-preset/skills/dsh-doc/references/compositions.md:69`）。
- 多用户/租户/账号能力缺失（§7.3），若未来演进 B/S 会被迫改造。
- Desktop 保留 Profile 与插件管理由壳拥有，Mammoth bundle 的安装/启用体验受官方插件管理器约束（`[DOCS]/architecture.md:51-53`；`[PKG]/dsh-official-bundles/README.md:40`）。

### 9.2 路线 B（自研 Electron 壳集成 DSH Runtime，自有 UI）

**可行性：技术上可行；"被官方能力支持"的层面是"子进程 + 具名 Profile + 协议"；完整进程内宿主组装无第三方产品级文档/稳定性承诺（公开低层 boot helper 存在，但不构成产品级嵌入契约）[已确认/部分推断]**

- **被支持的部分**：
  - 以子进程跑 `dsh --profile sdk|acp|headless|web` 是官方应用架构（`[DOCS]/architecture.md:43-47`；`[PKG]/dsh-app-boot/README.md:52`）。
  - Python SDK 是官方公开的嵌入先例：进程启动 + stdio 协议（`[DOCS]/user/guide/python-sdk.md`）。
  - `dsh --profile web` + 反向代理是官方部署路径（`[DOCS]/user/guide/public-deployments.md`），可被自有壳以 iframe/BrowserView 承载。
- **不被公开支持的部分**：
  - 进程内 `runProfile` 的**完整宿主组装**：`dsh-app-boot` 的 "lower-level embedders" 表述（`[PKG]/dsh-app-boot/README.md:12,34`）与 `@deepseek-ai/dsh/profile-boot` 公开导出说明低层嵌入并非全无公开面，但自定义 boot injections/认证转发/`dsh-app://` 载体 + Electron 编排目前唯一实现者是**私有** `dsh-desktop-host`（`private:true`、无 README、`[PKG]/dsh-desktop-host/package.json:2-8`），且 `architecture.md:43-45` 把"绕过 launcher 的进程内直接挂载"排除在应用启动器之外；第三方没有产品级配方与稳定性承诺。
  - 官方 Client 的内部 Remote API 不是版本化公开契约（§2.4、§8）。
- **能力落点**：
  - 若用 SDK/ACP：Mammoth 必须自建会话目录/标题/项目绑定/审批 UI/展示；SDK 无 list/create/fork/delete/cancel、无 MCP 管理、模型仅 initialize 选定（§2.2）；ACP 有 list/resume/close/new + 模型 + MCP + 取消 + 权限请求，但无 fork/标题/计划/终端/展示数据（§2.3）。
  - 若复刻 Desktop Host（进程内 Web 应用 + `dsh-app://` 转发 + 注入）：能力等价官方客户端，但需要镜像私有实现并承担 §8.3 的契约风险。
- **建议的 B 路线收敛形态（供 ADR 参考）**：B1（SDK/ACP 子进程 + 自有 UI）成本最低、边界最清晰；Mammoth 的 Project/Domain 模型放在自己的服务端，DSH 只作为"Agent 执行单元 + 工具审批"；避免依赖私有完整宿主组装。

### 9.3 路线 C（独立 B/S Agent 平台）

**可行性：DSH 可作为 Agent Runtime 提供方，但不能直接充当平台骨架 [已确认]**

- 能用的：headless/SDK/ACP 作为执行协议；未来若 DSH 提供更稳定协议再替换适配层。
- 不能用的：多用户/账号/租户/RBAC/领域数据/服务化部署（§7.3、§6.2）。
- 关键决策点：Mammoth 必须自己定义"会话协议适配层"（把 DSH SDK/ACP 事件映射到 Mammoth 的 Run/Step/ToolCall/Approval 领域事件），并把 DSH 版本升级隔离在适配层内；DSH 的 `workspace`/`AGENTS.md`/skills 只能作为**上下文注入手段**，不能作为项目模型。
- `[未知]` DSH 未来是否提供稳定公开协议/多用户能力：本次只能看到 rc 阶段"无兼容承诺"与单 owner 模型，无法预测。

---

## 10. 官方公开文档交叉核对（版本差异）

- GitHub master `docs/architecture.zh.md` 与本地 `[DOCS]/architecture.zh.md` 在 profile/bundle、应用启动、桌面应用、核心包、事件、轮次流程、会话日志、能力 seam、扩展点表上**实质一致**（逐段对照，未发现与 0.2.2-rc.2 本地文档冲突的实质差异）。来源：<https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/architecture.zh.md>。
- 官方站点 `reference/subsystems/web-client.md`（<https://deepseek-harness.github.io/deepseek-harness/reference/subsystems/web-client.md>）与本地 `[DOCS]/subsystems/web-client.md` 一致（Host/Transport/Client model/UI adapter 分层、`window.__DSH_BOOT__`、Remote/Typert、Session/Workspace 流、重连语义）。
- 本地安装版比公开站点多出：完整 `docs/upgrade-guide/*`（逐版本破坏性变更）、`docs/cookbook`（包/工具/LLM 适配器/设置页）、`docs/persistence-catalog.md`、`config-catalog.md` 等生成文档；这些是判断"升级耦合"的主要证据（§8）。
- 公开文档中的 `packages/...` 相对链接在 ASAR 内不可用，但对应包 README/源码在 `[PKG]` 下真实存在（本报告已按包核对）。
- 未发现公开文档承诺"第三方 Electron/自研壳进程内嵌入"；章节措辞仍是"实现共享 launcher 或嵌入其更低层 boot helpers"（`[PKG]/dsh-app-boot/README.md:12,34`）与"仅支持的 Node 应用通过具名 profile"，可交叉印证 §2.1 结论。

---

## 11. 未知 / 需实测清单

| # | 事项 | 状态 | 说明 |
|---|---|---|---|
| 1 | SDK（Python/TS）完整跑通 Mammoth 场景：流式事件、审批人、工作目录切换、resume | [需实测] | 本机未安装插件/未起 SDK 进程；协议边界已在源码确认，端到端需实测 |
| 2 | ACP 作为 Mammoth 外部驱动协议的稳定性/多会话长跑 | [需实测] | 协议面已确认；跨版本行为仅能靠升级指南 |
| 3 | TypeScript SDK 客户端 `@deepseek-ai/dsh-sdk-client` | [未知] | 协议文档引用它，但**不在本 Desktop ASAR 的已安装包列表中**；只确认 Python SDK 为公开文档路径 |
| 4 | 私有 `dsh-desktop-host` 的完整组装（`runProfile` + 认证/注入/载体）在未来 rc 是否稳定 | [未知] | 公开 `profile-boot` 无产品级 API 文档；完整组装只能随版本读私有源码 |
| 5 | "会话删除"是否存在于未文档化路径（UI/内部 API） | [未知] | 本次未发现 `session/delete`；归档替代；建议按"无删除 API"设计 |
| 6 | Host 插件直接出网（非 MCP/web_fetch）是否受任何策略约束 | [推断]/[需实测] | 沙箱只管文件效果；Host 进程无网络策略层（架构推断） |
| 7 | 桌面端默认端口被 Profile 覆盖的具体配置键 | [已确认-未找到显式键] | 代码 `--port 0`，文档称可覆盖；`--port` 属 webStartup 参数，Profile 覆盖形式需实测 |
| 8 | 本机 Desktop 更新渠道 `nightly` 对生产可用性的影响 | [已确认] 事实 / [推断] 风险 | `[ASAR]/package.json:21-27` 显示 `dshDesktopUpdateChannel: "nightly"`、强制更新策略 origin `https://harness.deepseek.com`、authentication `anonymous`；对企业交付的稳定性需评估 |

---

## 12. 关键证据索引（展开路径）

| 主题 | 文件 |
|---|---|
| 桌面架构总述 | `[DOCS]/architecture.md`（Desktop: 51-53；应用启动: 43-47；事件/扩展点: 59-66,136-171） |
| Web Client 架构 | `[DOCS]/subsystems/web-client.md`；官方 <https://deepseek-harness.github.io/deepseek-harness/reference/subsystems/web-client.md> |
| HTTP/WS 载体 | `[DOCS]/subsystems/web-server.md`；`[PKG]/dsh-client-connection/README.md` |
| API Gateway / Typert | `[DOCS]/api-gateway.md` |
| 公开部署/认证 | `[DOCS]/user/guide/public-deployments.md`；`[PKG]/dsh-client-connection/README.md:41-51,80-83` |
| Electron 主进程 | `[ASAR]/lib/main.js`（spawn: 11784-11802；IPC: 11809-11837；自定义协议/转发: 1478,15266-15379,20460-20474；桌面后端: 20517-20543） |
| Desktop Host | `[PKG]/dsh-desktop-host/lib/index.js`（boot: 341-380；ready/URL/injections: 502-522；CLI: `lib/cli.js:42-70`）；`package.json:2-12` |
| Profile/bundle 启动 | `[PKG]/dsh-app-boot/README.md`（profiles: 47-56；兼容性: 54-58）；`[PKG]/dsh-official-bundles/README.md` |
| SDK 协议/服务端/应用 | `[PKG]/dsh-sdk-protocol/README.md`；`[PKG]/dsh-sdk-jsonrpc-server/README.md`；`[PKG]/dsh-sdk-app/README.md` |
| ACP | `[PKG]/dsh-acp/README.md`；`[PKG]/dsh-acp-app/README.md` |
| headless | `[PKG]/dsh-headless/README.md` |
| 会话日志/持久化/查询/导出 | `[DOCS]/subsystems/session.md`；`[PKG]/dsh-session-persistence-jsonl/README.md`；`[PKG]/dsh-session-query/README.md`；`[PKG]/dsh-session-log-export/README.md`；`[PKG]/dsh-base/cordis.patch.yml:138-141` |
| 会话控制器/Workspace | `[PKG]/dsh-api-session-controller/README.md`；`[PKG]/dsh-api-workspace-controller/README.md`；`[PKG]/dsh-workspace/README.md` |
| 上下文注入 | `[PKG]/dsh-agent-instructions/README.md`；`[PKG]/dsh-skill-filesystem/README.md`；`[PKG]/dsh-system-prompt/README.md`；`[PKG]/dsh-mcp-client/README.md`；`[PKG]/dsh-working-directory/README.md` |
| 审批/权限/沙箱 | `[DOCS]/subsystems/approval.md`；`[DOCS]/subsystems/permission-presets.md`；`[DOCS]/subsystems/sandbox.md`；`[DOCS]/subsystems/tools.md` |
| 存储 | `[PKG]/dsh-storage-domain/README.md`；`[DOCS]/subsystems/storage.md` |
| 非 owner 访问控制 | `[PKG]/dsh-api-remote-access/README.md` |
| 升级耦合 | `[DOCS]/upgrade-guide/README.md`；`[PKG]/dsh/package.json`；`[DOCS]/user/guide/python-sdk.md` |
| 官方公开文档 | <https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/architecture.zh.md> |

---

## 13. 给 Lead 的三句话结论

1. **Desktop 的真实形态**是"Electron 壳 + 私有 Node Host（`dsh-desktop-host`，用 `runProfile` 起完整 Web 应用于 `127.0.0.1:0`）+ `dsh-app://` 自定义协议转发 + 签名 cookie 认证"，不是独立的桌面 RPC；官方 Web GUI 的 HTTP/WS 由 `dsh-host-webserver` + `dsh-client-connection` + `dsh-api-gateway` 提供。
2. **路线 B 被官方"能力"支持的部分是子进程 + 具名 Profile + 协议（SDK/ACP/headless/web）**；**完整进程内宿主组装**（复刻桌面 Host：认证 URL/boot injections/`dsh-app://` 载体/Electron 编排）没有第三方产品级文档与稳定性承诺（完整组装仅私有 `dsh-desktop-host`；公开的 `profile-boot`/`app-boot` 只是低层 boot helper；`architecture.md` 明确排除绕过 launcher 的进程内直接挂载作为应用启动器）。
3. **最大不确定性**：SDK/ACP 是否覆盖 Mammoth 产品所需的会话管理与展示（SDK 无会话枚举/标题/删除，ACP 缺 fork/标题/计划/终端/展示数据），以及完整宿主组装/`profile-boot` 公开面/boot injections/Remote 生成契约在 rc 阶段能否稳定跨版本复用——这决定路线 B 是"薄协议适配"还是"镜像私有实现"。
