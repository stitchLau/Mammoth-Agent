# S1 — DSH 扩展能力 Spike（只读研究 + 最小 bundle 实测）

- **版本**：0.1（Working Draft）
- **日期**：2026-11-12
- **对应任务**：`docs/prompts/01-DSH-EXTENSION-SPIKE.md`
- **分支**：`spike/dsh-extension`
- **状态**：研究完成；最小 bundle 已实测安装并激活；**ADR-0001 仍未批准**

> 本文区分三类内容：**已验证**（本轮实际执行并观察到结果）、**文档证据**（读本机安装版文档/源码）、**未验证**（明确标注）。不把前两类合并，也不把未运行的验证写成已验证。

## 1. 环境基线

| 项 | 值 | 证据 |
|---|---|---|
| DSH 桌面外壳 | `@deepseek-ai/dsh-desktop@0.2.2-rc.2` | `app.asar\package.json` |
| DSH payload / CLI | `@deepseek-ai/dsh@0.2.2-rc.2` | `app.asar\dsh\node_modules\@deepseek-ai\dsh\package.json` |
| build commit | `a6b1b85f44891ee1484bed6a9fc08acaae777d90` | `app.asar\package.json` |
| Profile | `desktop`；最终 bundles = `dsh-base`、`dsh-web-app`、`@local/mammoth-spike`、`@deepseek-ai/dsh-experimental-tool-worktree` | `~/.dsh/profiles/desktop/package.json` |
| 更新渠道 | `nightly`（`app-update.yml`：generic，`https://download.deepseek.com/dsh-desk/feeds/win-x64/`） | 本机安装文件 |
| 模型路由 | `deepseek-account / deepseek-flash / reasoning high` | `cordis.patch.yml` |
| Managed shell | Windows PowerShell 5.1，**Low 完整性级别** | `$PSVersionTable`、`whoami /groups` |

本机文档即 rc.2 文档（打包在 rc.2 的 `@deepseek-ai/dsh` 内），因此结论只对 `0.2.2-rc.2 + desktop` 有效。

## 2. 研究问题与结论（Prompt 01 的 7 问）

| # | 问题 | 结论 | 分类 |
|---|---|---|---|
| 1 | 推荐的 Bundle/Plugin 开发方式 | 在工作区写普通 npm 包（`dsh.bundle.patch`），用 `plugin_manager` `install_bundle` 安装；不手改 profile 的 `package.json`/`cordis.patch.yml`，不在 profile 目录跑 pnpm | 文档证据（`plugin-development.md:3,5`、`publish.md:33-66`）+ **已验证**（安装成功） |
| 2 | 可用的公共扩展点 | (a) 后端 service：`export function apply(ctx, config)` / 默认导出 service class；(b) Agent tool：`ctx.tools.register(...)`；(c) context：`dsh-agent-instructions`（AGENTS.md）、`ctx.systemPrompt.section()`；(d) UI：`dsh.client` + `ctx.slots.inject/register` | 文档证据（`host-plugin.md:60-65`、`dsh-tools/README.md:30-62`、`practices.md:36`）；tool 与 slot **部分实测**（工具已实测；slot 未做视觉验证） |
| 3 | 持久化扩展点与限制 | 官方只有 Host 侧 KV：`ctx.storage`（backend 路由）+ `ctx.storageDomain`（`defineDomain` + zod 记录 schema，`kv` 是唯一形态）。定位是 Host 应用数据，模型不可见；`session log is the only source of truth`，业务权威数据不应放进来 | 文档证据（`dsh-storage/README.md:12,130`、`dsh-storage-domain/README.md:12,28-58`、`practices.md:7`） |
| 4 | 安装/启用/停用/卸载/升级 | `install_bundle` / `set_plugin,set_bundle` / `remove_bundle`；配置文件在 `$DSH_PROFILE_DIR`，由 plugin_manager 维护；替换已装包需重启；安装需 Full access 或审批 | 文档证据 + **已验证**（install/remove/set_bundle 各执行成功） |
| 5 | pre-stable / 破坏性 API | 本机文档无 "pre-stable" 字样，改用逐版本 `docs/upgrade-guide/v<T>/` 记录破坏面；rc.1→rc.2 共 20 项（如 `BundleInfo.updatable→update`、Session 事件追加 API、DeepSeek Search 计费/凭据来源、Team 中断工具默认关闭、归档会话默认清理 worktree）；**无官方降级文档** | 文档证据（`upgrading.md:3,5,12`、`docs/upgrade-guide/README.md`） |
| 6 | 独立 Domain API 如何安全连接 | 官方文档化答案 = **MCP**：配置型 bundle 向 `dsh-mcp-client` 插入 `serverName + transport(streamable-http/stdio)` 一行；`typert/api-gateway` 只是 Host↔Client 内部 typed RPC；外部进程另有 `dsh-sdk-jsonrpc-server`、ACP | 文档证据（`mcp-bundle.md:3,5`、`api-gateway/README.md:12,27`、`packages.md:380`） |
| 7 | 如何证明真实 Profile Composition 生效 | `install_bundle` 返回的 `application`/`warnings`；`list_bundles` 的 `enabled`/`fiberPhase`；实际调用（**本轮采用**：安装后工具 schema 实时注入当前会话并真实调用成功） | **已验证** |

## 3. 最小 bundle 实测记录

被测包：`spike/dsh-extension/`（`@local/mammoth-spike@0.1.0`，`engines.dsh: >=0.2.2-rc.2 <0.3.0`）。

### 3.1 三次安装迭代（真实结果）

| 轮次 | 结果 | 诊断 |
|---|---|---|
| 1 | 安装成功，`application: failed` | `ERR_MODULE_NOT_FOUND: Cannot find package '@deepseek-ai/dsh-tools' imported from ...spike\dsh-extension\index.js` |
| 2 | 安装成功，`application: failed` | `JsonSchemaError: schema.properties.plugin.required is not supported on type "string"`（4 处） |
| 3 | `application: applied`，`warnings: []` | 工具 schema 立即注入本会话：`mammoth_spike_probe` |

### 3.2 工具实测

```
mammoth_spike_probe({note:"first call"})  → persistence=plugin-json-file count=1
mammoth_spike_probe({note:"second call"}) → persistence=plugin-json-file count=2
```

`C:\Users\Lau\.dsh\cache\mammoth-spike\mammoth-spike-probe.json` 实际内容：
`{"count":2,"updatedAt":"2026-11-12T09:53:38.009Z"}`

### 3.3 Worktree 可选 bundle

`plugin_manager set_bundle(@deepseek-ai/dsh-experimental-tool-worktree, enabled=true)` → `application: applied`、`warnings: []`；
`list_bundles` 显示 `enabled: true`，rows = `worktree` + `tool-worktree`；
当前会话立即出现 `create_worktree` 工具（参数 `name`、`from`）。

### 3.4 最终 profile 组成

`~/.dsh/profiles/desktop/package.json`：bundles 依次为 `dsh-base`、`dsh-web-app`、`@local/mammoth-spike`、`@deepseek-ai/dsh-experimental-tool-worktree`；dependencies 含 `"@local/mammoth-spike": "link:<workspace>/spike/dsh-extension"`。

## 4. 关键发现

- **F1（已验证）外部 bundle 可安装并激活**：`install_bundle` 用 pnpm 把工作区目录 link 进 profile，HMR 激活；新装 bundle **不需要重启**，工具 schema 在本轮就注入现有会话（`host-plugin.md:75` 的 "can activate through HMR" 得到实测支持；本轮未观察到 "existing sessions keep plugin revision" 的阻断）。
- **F2（已验证，最重要）外部 bundle 无法直接 `import '@deepseek-ai/*'`**：插件模块从**自身 checkout 路径**解析 Node 包，解析链不经过 DSH 安装目录，因此 `dsh-tools` 等包必须由插件自己声明并安装依赖（或构建成自包含产物）。这直接约束 Mammoth 的插件工程化方案与依赖策略。**S1b 结论：** 声明精确 `@deepseek-ai/*` 依赖并在插件目录内安装后，`defineTool` / `storageDomain` / `zod` 均可正常导入（见 §10）。
- **F3（已验证）直连 `ctx.tools.register` 需要原生 JSON Schema**：工具 DSL（`required: true` 写在属性上）只被 `defineTool` 接受；不使用 `defineTool` 时 `output.schema` 必须是合法 JSON Schema（`required` 放根数组）。F2 使外部 bundle 难以使用 `defineTool`，故 Mammoth 插件更适合「构建期打包 + 使用官方 DSL」。
- **F4（S1b 已解决）Host 侧持久化**：S1b 中 `ctx.storageDomain` 成功打开 schema 校验域并落盘（`~/.dsh/storages/mammoth_spike.json`，`tables.counters.probe.count=2`），**官方存储缝已验证**；S1 早期用插件自有 JSON 文件的回退方案不再是当前实现。
- **F5（已验证）UI slot 未做视觉验证**：`client.js` 通过 `window.__ModuleLoader__` 向 `conversation.composer.dock` 注册装饰组件，注册代码无报错，但本轮无浏览器控制，**未确认实际渲染**。
- **F6（已验证）Worktree 能力可用**：`create_worktree` 工具已注入；注意 rc.2 起归档会话默认清理干净工作树（`retainOnArchive` 默认关），忽略文件/`skip-worktree` 改动会被丢弃，需归档前提交或 `git worktree lock`。
- **F7（已验证）沙箱 shell 完整性级别限制**：shell 以 Low IL 运行，只能写带 Low 标签的对象；工作区根目录被 DSH 打 Low 标签，但既有子目录（`.git`、`docs`）仍是 Medium → shell 写这些目录被拒（Git 提交需完全权限）；宿主文件工具不受此限制。

## 5. ADR-0001 草案（按 `docs/templates/ADR.md`）

### ADR-0001: DeepSeek Harness 集成策略（Spike 结论草案）

- 状态：**Accepted（v1.0，2026-11-12 负责人批准；适用边界见第 6 节未验证项）**
- 日期：2026-11-12
- 决策人：（待填）
- 相关需求/功能：M0

#### 背景与约束

- 两种角色分开：DSH 是 Mammoth 的 Vibe Coding 工具（已可用），也是候选 Agent Runtime（需 Spike）。
- 实测环境：`0.2.2-rc.2`（外壳 + payload），build commit `a6b1b85`，Profile `desktop`，更新渠道 `nightly`，Managed shell = PowerShell 5.1（Low IL）。
- 不 Fork 上游；业务权威状态留给独立 Domain API；插件需声明 `engines.dsh`；安装插件需 Full access 或审批；rc 阶段破坏性变更按 `upgrade-guide/v<T>/` 处理。

#### 需要解决的问题

能否用 out-of-tree Bundle 承载 Mammoth 的 UI 入口、Agent 工具、后端服务与（非权威）持久化，同时把领域权威状态留在独立 Domain API？

#### 备选方案

- 方案 A：重度 Fork DSH。
- 方案 B：out-of-tree Bundle/Plugin + 独立 Domain API（本 Spike 主体）。
- 方案 C：Mammoth 独立桌面应用，DSH 仅作外部 Runtime。

#### 决策（已批准）

采纳 B，并明确其**已验证 / 未验证边界**：

- 已验证：bundle 安装与激活、Agent tool 注册与真实调用、Host 侧本地持久化往返、Worktree 可选能力启用、profile 组成可核对。
- 未验证：官方 `ctx.storageDomain` 缝在外部 bundle 中可用（被依赖解析阻断）；UI slot 实际渲染；跨重启行为；外部 Domain API 经 MCP 的安全连接。
- 方案 C 作为扩展点不足时的保留路径；方案 A 不批准。

#### 决策理由

1. bundle 是官方一等扩展路径（`plugin-development.md:3`），实测可安装、可激活、可卸载。
2. 工具注册与调用链在本轮端到端跑通（F1、3.2）。
3. Host 持久化有可用缝（`ctx.storageDomain`），但需要插件自带 DSH 依赖；产品化必须解决依赖打包（F2/F3）。
4. 外部 Domain API 的官方连接口是 MCP（`mcp-bundle.md:3,5`），与"业务状态独立"原则兼容。
5. Worktree 可作为并行编码隔离手段，但 rc.2 的归档清理策略需要显式保留。

#### 取舍和影响

- 收益：无 Fork 维护成本；升级跟随上游；UI/工具/存储/外部集成均有官方缝；领域与 Runtime 解耦。
- 代价：插件层是 TypeScript/Cordis，需解决「外部 bundle 依赖 DSH 包」的工程问题（打包或本地安装精确版本）；团队 Java 主力栈需通过 MCP/REST 边界桥接；rc 阶段破坏性变更频繁。
- 未能据此宣称"扩展能力已验证"：F2/F4 表明最小可行插件仍需额外工程决策。

#### 安全 / 数据 / 迁移影响

- 凭据：插件凭据走 `ctx.credentials`；MCP stdio 会 scrub 环境凭据；禁止明文写入 patch/对话。
- 数据主权：DSH storage 只放非权威数据；权威业务数据在 Domain API/关系库。
- Worktree：归档默认清理 → 需要 `retainOnArchive`/`git worktree lock` 或归档前提交。
- 迁移：bundle 声明 `engines.dsh`；更新已装包先 `remove_bundle` 再 `install_bundle`；升级前备份 `$DSH_PROFILE_DIR` 与 session 存储。

#### 验证计划和结果

已完成：见 §2、§3。待执行（S1b）：
1. 用"声明依赖并安装"或"构建为自包含产物"两种方案之一，让外部 bundle 能用 `defineTool` 与 `ctx.storageDomain`，验证官方 storage 缝；
2. UI slot 视觉验证（按 `references/verification.md`）；
3. 重启 DSH 后验证工具与持久化仍然可用；
4. MCP 连接的端到端验证（可用 Java 侧最小 MCP server）。

#### 回滚或替代路径

- 回滚：`plugin_manager remove_bundle('@local/mammoth-spike')`；如需关闭 Worktree：`set_bundle(..., enabled=false)`；profile 由 plugin_manager 维护，不手改。
- 替代：方案 C（独立应用 + MCP/SDK 边界）；Fork 仅在扩展点不足且重评后考虑。

#### 后续行动

1. ~~负责人批准本草案~~ → **2026-11-12 已批准（Accepted v1.0）**。
2. S1b 已执行：官方 `ctx.storageDomain` 缝已验证（见 §10）；剩余未验证项见 §6。
3. 结果驱动 ADR-0002（技术栈）与 ADR-0007（Git/Worktree 使用策略）。

## 6. 未验证项（明确列表）

1. ~~`ctx.storageDomain` 在外部 bundle 中可用（依赖解析阻断）~~ → **S1b 已验证**（见 §10）。
2. UI slot 实际渲染效果。
3. 跨 DSH 重启后的工具可用性与持久化读回。
4. `engines.dsh` 不满足时安装被拒绝的负向测试。
5. MCP 连接、权限/审批策略（`tools/pre-execute`、guard、restrict）在真实插件中的行为。
6. bundle 卸载后 Client 侧资源释放。

## 7. 清理与回滚

```powershell
# 卸载验证包（保留 Worktree 能力）
# 在 DSH 会话中调用：plugin_manager action=remove_bundle target=@local/mammoth-spike
# 关闭 Worktree：
# plugin_manager action=set_bundle target=@deepseek-ai/dsh-experimental-tool-worktree enabled=false
```

验证包自建状态文件：`C:\Users\Lau\.dsh\cache\mammoth-spike\mammoth-spike-probe.json`（可安全删除）。

## 8. 技术栈桥接方向（Lead 决策建议，待 ADR-0002 确认）

负责人授权本轮自主决定 Domain API 与 DSH 的桥接方向。建议：

- **边界采用 MCP 优先**：Java Domain Service 以 MCP server 暴露领域操作，DSH 侧用**配置型 bundle**（唯一 name/version + `dsh.bundle.patch`）插入 `dsh-mcp-client` 一行即可接入，**不需要外部 bundle 导入任何 `@deepseek-ai/*` 包**，正好规避本轮发现的 F2 问题。
- **工具命名可预期**：模型侧工具名固定为 `mcp__<serverName>__<toolName>`，便于权限与审计配置。
- **备选**：`dsh-sdk-jsonrpc-server`（外部进程 JSON-RPC）或 ACP；Host 插件内直接 HTTP 仅作为原型手段，文档未提供带认证的契约。
- **约束**：MCP server 的启动方式、stdio 环境凭据清理、连接超时与断连重试需在 S1b 实测；Java 侧需选一个 MCP SDK 并在 ADR-0002 记录版本与许可。
- **不预设**：具体前端框架、关系库、RAG 组件仍按 ADR-0002/0003/0005 逐项 Spike 决定，本建议只固定"Domain API ↔ DSH 的协议边界"。

## 9. 证据路径

- 探针包：`spike/dsh-extension/`（`package.json`、`cordis.patch.yml`、`index.js`、`client.js`、`locale/*.json`、`icon.svg`、`README.md`）
- Profile 组成：`C:\Users\Lau\.dsh\profiles\desktop\{package.json,cordis.patch.yml}`
- 安装日志：`C:\Users\Lau\.dsh\profiles\desktop\.plugin-manager\logs\operation-*\pnpm.log`
- 安装返回值：`install_bundle` → `application: applied`；`list_bundles` → `@local/mammoth-spike enabled=true installed=true availability=profile source=link:...`
- Worktree：`list_bundles` → `@deepseek-ai/dsh-experimental-tool-worktree enabled=true optional=true`

## 10. S1b — 依赖方案 a 与官方存储缝验证（2026-11-12 追加）

### 10.1 做法

`spike/dsh-extension/package.json` 增加精确依赖并做版本升级到 `0.2.0`：

```json
"dependencies": {
  "@deepseek-ai/dsh-storage-domain": "0.2.2-rc.2",
  "@deepseek-ai/dsh-tools": "0.2.2-rc.2",
  "zod": "4.6.6"
}
```

在**插件目录内**（不是 profile 目录）执行 `pnpm install`：解析 21 个包、下载 21 个（含 `@deepseek-ai/cordis` 等 peer 自动安装），pnpm 自动生成 `pnpm-lock.yaml` 与带 `minimumReleaseAgeExclude` 的 `pnpm-workspace.yaml`。

`index.js` 改为官方 API：

- `import { defineTool } from '@deepseek-ai/dsh-tools'`
- `import { defineDomain, domainTable } from '@deepseek-ai/dsh-storage-domain'` + `import { z } from 'zod'`
- `export const inject = ['tools', 'storageDomain']`；`apply` 中 `await ctx.storageDomain.open(spec)`，`ctx.effect` 负责 `domain.close()`
- 输出 schema 回到工具 DSL 形态（`required: true` 写在属性上），由 `defineTool` 编译

### 10.2 迭代与结果

| 轮次 | 结果 | 诊断 / 处置 |
|---|---|---|
| S1b-1 | `application: failed` | `Error: domain name 'mammoth-spike' must match /^[a-z][a-z0-9_]*$/` → 域名改为 `mammoth_spike` |
| S1b-2 | **`application: applied`**，`warnings: []` | 工具 schema 立即注入当前会话 |

真实调用（两次）：

```
mammoth_spike_probe({note:"S1b first"})  → persistence=storage-domain count=1
mammoth_spike_probe({note:"S1b second"}) → persistence=storage-domain count=2
```

落盘证据（官方 JSON storage backend）：

```json
{ "unit": { "name": "mammoth_spike", "version": 1 },
  "global": null,
  "tables": { "counters": { "probe": { "count": 2 } } } }
```

文件：`C:\Users\Lau\.dsh\storages\mammoth_spike.json`。

### 10.3 结论

- **官方存储缝在 out-of-tree bundle 中可用**，前提是插件自带精确版本的 DSH 依赖并在自身目录安装（方案 a 成立）。
- 代价：插件体积与依赖树显著变大（21 包），且与 DSH 版本**强耦合**（必须逐版本同步 `0.2.2-rc.2`）；这使"声明依赖 + 锁定版本"成为产品化必须的工程约束，也意味着升级 DSH 时必须同步升级插件依赖。
- 仍未验证：跨重启读回（未重启 DSH）；UI slot 视觉渲染；`engines.dsh` 负向拒绝；MCP 端到端。

