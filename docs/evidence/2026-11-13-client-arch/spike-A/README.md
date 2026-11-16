# Spike A 证据索引 — Mammoth × DSH Desktop 集成

- **日期**：2026-11-16（关联 2026-11-13 客户端架构评估与 ADR-0014）
- **对应 ADR**：`decisions/ADR-0014-CLIENT-SHELL-AND-UI-EXTENSION.md`
- **Spike 分支**：`spike/dsh-desktop-integration`（Worktree：`E:\MyProj\MommothAgent\code\Mammoth-Agent-spike`）
- **Bundle 源码**：`integrations/dsh/mammoth-bundle/`（该分支，已提交；见下"提交记录"）
- **DSH 版本**：`0.2.2-rc.2`（Desktop，`desktop` profile；`plugin_manager list_bundles` 返回的安装版本）
- **Domain Service**：Spring Boot 4.1.2 / JDK 17，隔离 data root `%TEMP%\mammoth-spike-data\root`，端口 18090
- **Proposal 适配器（Spike-only）**：`spike/dsh-desktop-integration/proposal-stub.mjs`，127.0.0.1:18091
- **数据**：全部为合成数据（`SPIKE1` 项目、`REQ-0001/REQ-0002`、合成知识文档、`spike_admin/spike_reviewer/spike_agent` 合成账号）

## 1. 结论摘要

| 问题 | 结论 | 置信度 / 依据 |
|---|---|---|
| 外部 bundle 能否以 Host + Client + MCP + Preset 四合一安装并在一台真实 DSH Desktop 中生效 | **能**。单包 `@mammoth/dsh-bundle` 以 `link:` 装入 `desktop` profile；三个 Loader 行（`mammoth-host` / `mammoth-domain-mcp` / `preset-mammoth`）与浏览器半同时生效 | 高；`plugin_manager list_bundles`、工具可用性、截图 00–13 |
| 左栏入口 + `main` 整页业务面板 | **能**。官方同款机制（`sidebar.panellist` + `main` keyed slot） | 高；截图 01/07/08 |
| Client → 真实 Domain Service 数据通道 | **能，且走 Host 桥**。Client 页面运行在 Desktop shell 载体 `dsh-app://app/`，对 Host 注册的同源路由 `/mammoth/api/*` 的相对 `fetch` 经 shell fetch 桥以浏览器 Peer 身份鉴权；Client 不持有 Domain 凭据 | 高；截图 12/13 的 `apiBase: dsh-app://app/mammoth/api`、`transportPresent: true`、`errors: []`；截图 01–04 的真实数据 |
| 会话 ↔ 项目上下文 | **能**。Host 插件按 DSH Session 记录当前项目；`systemPrompt.section` 注入项目卡；会话头部项目徽章 + 右栏项目上下文页 | 高；`host-plugin-state.json`、本会话系统提示注入、截图 05/06 |
| Agent 经现有 MCP 读取项目知识 | **能**。`mcp__mammoth_domain__search_project_knowledge / get_knowledge_document / get_requirement_context` 全部可用（3/3） | 高；`tool-call-records.md`、截图 11/12 |
| Agent 输出自定义结构化业务结果卡片 | **能**。`tool.call.toolview` 按工具 wire name 注册；Mammoth 知识/需求/提案卡片渲染成功 | 高；截图 11；提案卡片见 09/10 页面侧 |
| Agent 创建待审核提案 + 人工批准后正式写入 | **条件通过（真人批准已由负责人亲自完成；剩余适配器安全边界）**。负责人本人于 2026-11-16 亲自批准提案 `db238b78` → Domain 写入 `REQ-0003`（`audit_log.actorUserId=spike_admin`）；DSH 审批闸门 `allowed-once`；agent 账号 401、不能自批。**仍未闭合**：适配器与 Agent 同 OS 用户，不构成安全边界（需独立 ADR） | 高（链路+真人操作）；`domain-after-approval.json`、`proposal-stub-*`、截图 09/10/13/18/19 |
| 安装/卸载/重装与 `engines.dsh` 负向门禁 | **通过**。卸载后 profile 清单恢复、左栏入口消失；重装后恢复；`engines.dsh: >=9.0.0` 安装被 `incompatible-version` 拒绝 | 高；`plugin-lifecycle-evidence.md`、截图 16/17 |
| 升级回归（S-A9）/ 启动性能（S-A10）/ 桌面文件目录（S-A8） | 未执行（不在本轮任务书 §四 范围；记为已知限制与后续项） | — |

## 2. 文件清单

### 2.1 截图（全部为真实安装运行的 DSH Desktop 窗口，PrintWindow 采集；窗口 1296×828）

| 文件 | 内容 | 对应验收 |
|---|---|---|
| `00-plugin-manager-bundle-detail.png` | DSH Plugins 页中的 Mammoth bundle 详情（图标、标题、版本、DETAIL 文档渲染） | S-A0b 显示元数据 |
| `01-workbench-home-active-project.png` | Mammoth 工作台首页：真实项目表、当前项目、诊断（Domain 身份/MCP 3/3） | S-A1/S-A2/S-A3 |
| `02-project-detail.png` | 项目详情：客户、状态、projectId、成员与角色 | S-A2 |
| `03-project-requirements.png` | 项目需求池：`REQ-0001` | S-A2 |
| `04-project-knowledge.png` | 项目知识中心：verified + draft 两篇知识、来源计数 | S-A2 |
| `05-conversation-rightbar-project-context.png` | Conversation + 右栏「Mammoth 项目上下文」：绑定项目、客户、成员、知识、工作项 | 六-A / S-A3 |
| `06-conversation-header-badge.png` | Conversation 头部项目徽章 `◆ SPIKE1 Spike Demo Project` | S-A3/S-A4 |
| `07-workbench-entry-open.png` | 从左侧 Mammoth 入口打开的工作台首页 | S-A1 |
| `08-sidebar-entry-mammoth.png` | 左栏 Mammoth 独立入口（局部特写） | S-A1 |
| `09-proposal-pending.png` | 工作台「待审核提案」：agent 提案 pending + 批准/拒绝按钮 | S-A7 |
| `10-proposal-approved-domain-write.png` | 批准后：提案 approved、approver、`domainObject=REQ-0002`（Domain 返回） | S-A7 |
| `11-tool-card-mcp-knowledge.png` | Conversation 中 MCP 知识检索的自定义业务卡片（2 chunks、来源数、正文片段） | S-A6 |
| `12-diagnostics-host-route.png` | 工作台诊断页：Host 网关健康、Domain 读取身份、MCP 工具 3/3、页面地址 | S-A2 |
| `13-client-runtime-and-approval-audit.png` | 客户端运行时诊断（`dsh-app://app/`、same-origin `apiBase`、0 错误）+ 插件审计（`dshApproval: allowed-once`、approve 记录） | S-A2/S-A7 |
| `14-tool-cards-failure-states.png` | 工具卡片失败态（MCP 非成员项目拒绝渲染为错误卡片；不 blank slot）；含 DSH 审批 `never` 下提案被拒的卡片字段 | S-A5/S-A6 |
| `15-new-session-bound.png` | 新建会话页面（会话自动绑定当前项目，见 `host-plugin-state.json` 的 `session-created` 记录） | S-A3 |
| `16-after-remove-bundle.png` | `remove_bundle` 后：左栏 Mammoth 入口消失、工作台不可达 | S-A0 |
| `17-after-reinstall.png` | 重装后：入口恢复、bundle 行重新 active | S-A0 |
| `18-proposal-approved-raw-json-before-fix.png` | 负责人在工作台亲自批准后，提案页把 `domainObject` 原始 JSON 直接铺开（缺陷复现） | S-A7 / UI 缺陷 |
| `19-proposals-human-readable-after-fix.png` | 修复后：主视图为业务字段（需求编号/状态/优先级/审批人/摘要/依据），技术信息折叠 | S-A6 / UI 修复 |

> **真人批准记录（2026-11-16 11:13，Asia/Shanghai）**：负责人在 `desktop` profile 中亲自点击「批准并写入」，批准提案
> `db238b78-7834-4dcb-a86b-092f8451980d`（标题"急料维护列表新增供应商名称列"）→ Domain Service 生成 `REQ-0003`
> （`createdBy=spike_admin` 人类账号，`audit_log.actorUserId=spike_admin`）。第三方 API 复核：提案 `status=approved`、
> `dshApproval=allowed-once`、`domainObject.itemNo=REQ-0003`；项目工作项 3 条。**这补齐了独立评审指出的"人工批准未由独立人类验证"缺口**。

### 2.2 机器可读证据

| 文件 | 说明 |
|---|---|
| `domain-after-approval.json` | Domain REST 复核：项目工作项 2 条（含 `REQ-0002`）、admin 审计（`work_item.create` actor=spike_admin） |
| `host-plugin-state.json` | Host 插件的非权威本地状态：activeProjectId、Session↔项目绑定、提案镜像（approved + domainObject） |
| `proposal-stub-state.json` | 隔离适配器的提案记录（pending→approved、approver、domainObject） |
| `proposal-stub-audit.jsonl` | 适配器原始审计日志（proposal.created / proposal.approved） |
| `proposal-stub-audit.json` | 适配器审计的结构化读取（token 鉴权） |
| `proposal-stub-auth-negative.json` | 适配器鉴权负向：无 token 的列表/审批均 401；无 CORS 头 |
| `plugin-lifecycle-evidence.md` | 安装/卸载/重装/`engines.dsh` 负向门禁证据 |
| `screenshot-manifest.json` | 全部截图的 SHA-256 与采集方式（可校验完整性） |
| `screenshots-metadata/` | `capture-dsh.ps1` 的采集 sidecar（窗口/DPI/方法元数据，非运行时数据） |
| `tool-call-records.md` | 本轮 Agent/MCP/Domain 真实调用记录摘要 |

## 4.2 决策落定（2026-11-16，Spike 后）

- **ADR-0014 已由项目负责人签署**：状态 **Accepted v1.1（2026-11-16）**；S-A7 真人批准条件已由负责人本人完成（`REQ-0003`）。
- **ADR-0015 已由负责人确认全部决策项并转为 Accepted v1.0**：本机独立 OS 账号/独立进程的 Approval 服务身份、凭据只存 OS 存储、Domain 提案/审批/写入 API、人类独占审批（全员提案者≠批准者，高风险类别加第二人复核）、审计三元组；远端方案随 M5。
- **P1 实施计划已起草**：`docs/specs/ADR-0015-P1-CONTROLLED-WRITE-PLAN.md`（V11 数据模型、API 草案、四眼规则、负向测试矩阵、切片与回滚；待负责人批准启动）。
- **文档同步**：ADR-0002（UI 宿主修订补充说明）、`docs/00`、`docs/05`、`docs/12`、`docs/13`、`docs/specs/UI-SPEC.md` 已按 ADR-0014/0015 更新。
- **未完成**：ADR-0015 P1–P4 实施与负向安全验收、客户端正式迁移阶段编号、S-A8/S-A9/S-A10。
| `README.md`（本文件） | 索引、环境、组合结构、复现与回滚 |

## 3. 四合一组合结构（实际安装形态）

Bundle 根：`integrations/dsh/mammoth-bundle/`

```
package.json                 # dsh.bundle.patch + dsh.client + exports("." "./client" locale icon)
cordis.patch.template.yml    # 提交的模板（机器无关）
cordis.patch.yml             # scripts/configure.mjs 生成（git-ignore；含本机 Java/jar/data root）
index.js                     # Host 插件（Domain 桥、工具、会话上下文、提案闸门、受鉴权 web 路由）
client.js                    # Client 插件（预构建 lazy-CJS，单文件；只 require('react')）
tool-parameters.js           # 3 个工具 raw JSON Schema 单一来源（事故后防护）
tool-schema.js               # raw schema 子集本地校验 + 注册前 fail-fast
scripts/check-tool-schemas.mjs  # 安装/重载前自检（含事故回归夹具）
scripts/configure.mjs        # 渲染本机 cordis.patch.yml + local/config.json
locale/{en,zh}.json          # 插件管理显示元数据
docs/DETAIL.md               # Plugins 页详情文档
icon.svg
spike/dsh-desktop-integration/proposal-stub.mjs   # Spike-only 隔离适配器（非 bundle 内）
```

安装后的 Loader 行（`plugin_manager list_bundles` 的 `@mammoth/dsh-bundle` 条目）：

| 行 | 模块 | 作用 | 状态 |
|---|---|---|---|
| `mammoth-host` | `@mammoth/dsh-bundle` | Host 插件：工具、上下文、`/mammoth/api/*` | active |
| `mammoth-domain-mcp` | `@deepseek-ai/dsh-mcp-client` | stdio 启动现有 Mammoth MCP server（只读） | active；工具 3/3 |
| `preset-mammoth` | `@deepseek-ai/dsh-agent-preset` | Mammoth 会话预设（persona + 最小工具集） | active |
| （浏览器半） | package.json `dsh.client` | `client.js` 经 client-modules 扫描加载 | 已渲染（截图） |

> 说明：DSH 的 `rightbar` / `rightbar.session` 是**已被官方单槽占用**的 slot，第三方正确扩展位是
> `ctx.sidebarRightTabs.register(...)` + keyed body `sidebar.right.pane.tab`（官方文档与源码证实）。本 Spike
> 因此按官方扩展席位实现右栏项目上下文，而不是替换官方 `rightbar` 槽。

## 4. 关键实测事实（写报告/ADR 时引用）

1. **Desktop 渲染页面的 origin 是 `dsh-app://app/`**，不是 loopback HTTP；Client 插件对该 origin 的相对
   `fetch('mammoth/api/...')` 经 Desktop shell 的 fetch 桥到达 Host 的 `webServer` 前缀路由，并被认证为浏览器
   Peer（`ctx.connection.requestRejection` 放行）。直接对 `http://127.0.0.1:3802/mammoth/api/*` 的无 Cookie
   探测会被 401 拒绝（未认证）。**这是 Client↔Host 数据通道的选型结论：C2（Host 桥），不是 C1（浏览器直连）。**
2. **Client 插件激活失败在 Desktop UI 中没有可见错误**（只有浏览器 console / boot audit）。Spike 中首版
   `sidebarRightTabs.register` 因把 `guide` 传成对象（官方要求 guide **数组**，元素含 `id/order/title()`）而抛错，
   导致整个 client `apply` 中断、左栏入口消失，但 Host/MCP/其它插件完全正常。修复方式：先注册入口与页面、每个
   可选 surface 独立 try/catch，并把诊断显示在工作台内（`clientDiagnostics`）。已提交 `c5fa625`。
3. **raw JSON Schema 的 `parameters` 不校验**（2026-11-13 P0 事故）：本 bundle 已加注册前本地校验与 fail-fast，
   并有 `scripts/check-tool-schemas.mjs` 回归夹具（`dec48c4`）。
4. **DSH 工具审批闸门与工作台人工审批串联可用**：`allowed-once` + `local-desktop-human` + Domain 侧
   `actorUserId=spike_admin` 三段证据齐全；agent 账号无法登录 Domain（401），无法自批。
5. **MCP 只读身份与 Host 读取身份分离**：MCP 用 `MAMMOTH_MCP_AGENT_USER_ID`（agent 账号，只读 DB）；
   Host 读取用合成人类项目成员账号（`spike_reviewer`），只存在于仓库外机器本地文件。

## 4.1 独立评审后的修正（2026-11-16）

- **S-A7 人工批准已由负责人亲自完成**：2026-11-16 11:13 负责人亲自点击「批准并写入」，提案 `db238b78` → `REQ-0003`
  （Domain 审计 actor=spike_admin）。此前"由 Lead Agent UI 自动化批准"的 `REQ-0002` 仍保留为机械链路证据，但**真人治理
  以 `REQ-0003` 为准**。
- **已知 UI 缺陷已修复（提案页技术字段可读性）**：原提案页把 `domainObject` 等后端字段以原始 JSON 直接铺开；已改为
  业务字段主视图 + 「技术信息」折叠 + 「在需求池查看 REQ-xxxx」入口（截图 18 为修复前、19 为修复后）。
  该问题属 Spike 演示页的简化缺陷，不是产品交互设计，已记录为 UI 债务（正式迁移按 `docs/specs/UI-SPEC.md` 设计）。
- **审批链加固**（DSH 审批只接受 `allowed-once`、适配器 token 鉴权、去 CORS、审批回显提案 id、修复 Host→适配器
  `confirm` 转发 bug `10efdea`）；负向测试见 `proposal-stub-auth-negative.json`。
- **证据补齐**：卸载/重装与 `engines.dsh` 负向门禁（`plugin-lifecycle-evidence.md`、截图 16/17）；
  会话自动绑定（2 条 `session-created`）；MCP 非成员拒绝（截图 14）；工具卡片失败态（截图 14）；
  截图 sidecar 统一移入 `screenshots-metadata/`，并新增 `screenshot-manifest.json`（SHA-256）。
- **更正**：此前版本把 11/12/13 的截图 sidecar 误当作运行时 JSON、把中文乱码归因于数据（实为 PowerShell 5.1
  读取编码问题）；现已用 UTF-8 重新生成运行时 JSON。

## 5. 复现与回滚

复现步骤、安装/卸载、机器本地配置、Proposal 适配器启动方式见：
`integrations/dsh/mammoth-bundle/README.md`（spike 分支）。

回滚：

1. `plugin_manager` → `remove_bundle`（target `@mammoth/dsh-bundle`），或恢复
   `%TEMP%\mammoth-spike\profile-backup\` 下的 profile 清单；
2. 停止 Domain writer / Proposal 适配器；
3. 删除 `%TEMP%\mammoth-spike`、`%TEMP%\mammoth-spike-data`、bundle `local/`；
4. 如修改过 Windows 文件权限：按 `.acl-recovery/` 下脚本恢复（本次仅修复 `docs/`、`docs/evidence/` 的
   WRITE_OWNER 缺失，未改文件内容/所有者）。

## 6. 提交记录（spike 分支）

- `f570c24`（main）— 工具 schema P0 事故记录与防再发规则（本分支基线之上）
- `dec48c4` — Mammoth bundle 主体 + 工具 schema 注册前防护 + 自检脚本（15 files，+3014）
- `74128cf` — 隔离 Proposal 适配器（Spike-only）
- `c5fa625` — 客户端 UI 修复：入口优先注册/逐面隔离、guide 数组、内置诊断
- `70f2e5a` — bundle README 记录 Spike 实测结论与纪律
- `c7da93a` / `6ad7ddc` — 证据包（截图/JSON/报告）
- `5ea4d34` — 环境与回滚说明
- `aea1d49` — 独立评审整改：审批 fail-closed + 适配器鉴权/去 CORS/confirm、证据补齐、ADR 改为条件定稿待签
- `10efdea` — 修复 Host→适配器 `confirm` 转发缺陷（工作台批准 400 的根因）
- `aa5d6b2` — 提案卡片可读性修复 + 负责人真人批准记录（`REQ-0003`）
- `46af060` — 证据 sidecar 整理与交接文档更新
