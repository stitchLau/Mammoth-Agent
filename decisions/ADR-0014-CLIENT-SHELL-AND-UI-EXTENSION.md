# ADR-0014: 产品形态与客户端宿主（DSH Desktop 宿主 + Mammoth Bundle，保留 B/S 演进）

- 状态：**Accepted（v1.1 定稿，2026-11-16；Spike A 技术可行性通过，S-A7 条件通过；待项目负责人签署）**
- 日期：2026-11-13（草案）/ 2026-11-16（定稿）
- 决策人：项目负责人（待签署）；Lead Agent 依据 Spike 证据与独立评审整改定稿
- 相关需求/功能：客户端架构重估（阶段编号待定）、Desktop 优先、Agent 主要工作入口；关联 M5/B-S 演进
- 相关文档：`docs/reports/2026-11-13-CLIENT-ARCHITECTURE-REEVALUATION.md`（完整评估）、`docs/evidence/2026-11-13-client-arch/01|02|03`（证据附件）、`docs/evidence/2026-11-13-client-arch/spike-A/SPIKE-REPORT.md`（Spike A 验证报告，含独立评审整改记录）、`decisions/ADR-0001-DSH-INTEGRATION.md`、`decisions/ADR-0002-TECH-STACK.md`、`docs/00-MASTER-GUIDE.md`、`docs/01-PRODUCT-CHARTER.md`、`docs/02-PRODUCT-BOUNDARIES.md`、`docs/05-ARCHITECTURE-BASELINE.md`、`docs/12-ROADMAP.md`、`docs/13-OPEN-DECISIONS.md`

## 背景与约束

1. **产品愿景**：Mammoth 是以项目为核心、以 Agent 为主要工作入口的领域化 AI 工作平台，预期形态接近 ChatGPT Codex Desktop / DeepSeek Harness Desktop；对话与项目工作区是主交互界面，客户/产品/知识/需求池/方案/交付是围绕 Agent 的业务模块（负责人 2026-11-13 研究前提；`docs/01`、`docs/05:13`）。
2. **现状**：独立 React SPA + Spring Boot Domain Service（SQLite/WAL、Flyway、39 表、107 个 `/api` 端点、124 tests）+ DSH 只读 MCP（3 个工具）。SPA 无 Agent 会话/LLM/写操作入口；产品交互形态实质是"业务管理后台 + 只读 Agent 旁路"（证据：`docs/evidence/2026-11-13-client-arch/01-repo-archaeology.md`）。
3. **既有决策**：
   - ADR-0001（Accepted v1.0）：out-of-tree Bundle/Plugin 扩展 DSH，不 Fork；业务权威状态独立于 DSH；Domain API↔DSH 推荐边界为 MCP。
   - ADR-0002（Accepted v1.0）：Spring Boot 4.1.2 + Java 17 + Maven；UI 采用 **B1 独立 SPA**，M1 不嵌入 DSH Client slot；并明确"UI 如需嵌入 DSH → **另立 ADR 变更 B1 为 B2**"。
   - ADR-0006/0008/0012：身份/工作区/团队服务器边界不变。
4. **决策空白**：ADR-0001 只决定集成方式，ADR-0002 只决定技术栈与 UI 实现方式；**最终产品形态（浏览器后台 / 桌面工作台 / DSH 内嵌）从未被单独 ADR 决策**（`docs/00-MASTER-GUIDE.md:127` 仍列待决）。
5. **新证据（2026-11-13，DSH 0.2.2-rc.2 + desktop profile）**：
   - 0.2.2-rc.2 共 114 个 Client Slot；`main`（keyed, root）+ `sidebar.panellist`（list, root）可新增"左侧栏顶级入口 + 主内容区整页业务页面"，官方 `dsh-client-ui-plugin-manager` 为同款范例；`tool.call.toolview` 可按工具名渲染业务结果卡片；`conversation.chat.node`/`conversation.view`/`rightbar`/`settings.section` 等可扩展；bundle 的 patch 机制允许组合 Host 行、Client 行、MCP 行与 preset 片段（各有官方样例，但**尚无官方 bundle 同时包含全部四类**；四合一组合需 Spike S-A0b 实测）。
   - 官方实践禁止 iframe 嵌入 Host HTML、禁止导入官方 Client UI 组件库、禁止替换 app root、禁止第三方新增 Session 事件类型。
   - 第三方 Client→Domain 数据通道、第三方 Typert Remote 生成、会话创建/绑定等项目**需本地实测**（主报告 §6.2/§6.3 S-A2–S-A4；`docs/evidence/2026-11-13-client-arch/02-dsh-ui-extension.md` §11 待实测清单）。
   - 路线 B（自研 Electron 壳）：官方支持仅"子进程 + 具名 Profile + 协议（web/headless/sdk/acp）"；SDK 显式能力窄（无 session 枚举/重命名/fork/删除/取消，新 `sessionId` 首次使用可隐式创建 Agent；无 MCP 管理；模型仅 initialize 选定；无版本协商）；ACP 仍缺 fork/标题/计划/终端/展示数据；**完整进程内组装（认证 URL、boot injections、`dsh-app://` 载体、Electron 编排）仅有私有 `dsh-desktop-host` 实现**——公开包仅提供 `profile-boot`/`app-boot` 低层嵌入入口，官方架构只承认具名 Profile 启动，公开低层 boot helper 不构成第三方产品级嵌入契约（`03-dsh-runtime-host.md` §2、§9）。
   - 路线 C（独立 B/S）：DSH 可作外部执行单元，但不提供多用户/账号/租户/RBAC/服务化部署；自研范围最大。
   - DSH 处于 rc，0.2.1→0.2.2-rc.2 已有多起 Client slot/API 破坏性变更；`engines.dsh` 只做版本闸门，不保证 API 兼容。

## 需要解决的问题

1. Mammoth v1 的产品形态与主客户端是什么（Desktop 宿主 / 自研桌面壳 / B/S）？
2. Agent 会话与业务工作台如何同屏、如何关联项目上下文？
3. 业务权威状态、权限、审计放在哪里，客户端/插件边界如何划？
4. 现有 SPA、Domain Service、MCP、测试资产如何复用与演进？
5. 如何保留未来独立 B/S 版本的可能性？
6. 如何控制在 DSH 预发布期的版本耦合与升级风险？

## 备选方案

### 方案 A（推荐）：DSH Desktop 宿主 + Mammoth 外部 Bundle（**2026-11-16 Spike A 已通过**）

Mammoth 作为 out-of-tree bundle 安装进 DSH Desktop：Host 插件（工具/项目上下文/会话↔项目映射）+ Client UI 插件（`dsh.client`，工作台 `main[mammoth]`、`sidebar.panellist` 入口、业务卡片、设置分区）+ MCP 配置（现有只读工具）+ 可选 agent preset。Domain Service 保持独立进程与权威存储。现有 SPA 保留为团队 Web 管理台/后备客户端。

### 方案 B：自研 Electron 桌面壳 + 集成 DSH Runtime

Mammoth 自建桌面应用（现有 SPA 可直接作 renderer），通过官方子进程协议（优先 ACP，其次 SDK/headless）集成 DSH Runtime；Domain Service 作为本地 sidecar 或远端服务。聊天/会话/工具卡/审批 UI 全部自研。

### 方案 C：独立完整 B/S Agent 工作平台

Mammoth 自建 Web 平台（多用户、服务端权威），自研或经 ACP/SDK 编排 Agent Runtime；DSH 作为外部执行单元。现有 SPA 演进为管理台/Web 客户端。

## 决策

> 以下为 **Accepted v1.1 定稿决策**（2026-11-16），生效条件：项目负责人签署 + "Spike A 执行结果"中列出的条件项闭合。
> 历史草案状态的旧表述（"拟采纳/未通过前保持 Proposed"）已被本版本取代。

1. **产品形态**：Mammoth v1 为 **Desktop 优先**的产品：DSH Desktop 中的"项目智能工作台"。DSH Conversation 是主入口；Mammoth 工作台是 DSH 主内容区中的全局面板；两者同屏、共享当前项目上下文。
2. **主客户端路线**：采纳**方案 A**。Mammoth 以单个外部 bundle 分发（Host 插件 + Client 插件 + MCP 配置 + 可选 preset；`dsh.bundle.patch` 支持多 patch，各类内容有官方样例；**四合一组合由 Spike S-A0b 验证**，若受限则改为"主 bundle + 独立 MCP/preset bundle"）。不采用方案 B 作为当前主路线；方案 C 作为长期演进保留。
3. **UI 组成**（Client 插件）：
   - `sidebar.panellist` 注册 Mammoth 入口（首版 1 个"工作台"入口；后续可加需求池/知识中心快捷入口）。
   - `main['mammoth']` 注册整页业务工作台，内部自建二级导航（工作台首页/项目空间/客户/产品/需求池/知识中心/交付/组织设置）。
   - `tool.call.toolview` 注册 Mammoth 工具结果卡片（需求/就绪/方案/知识引用/交付）。
   - `conversation.session.header.actions` 或 `conversation.header.leading` 注册当前项目徽章与项目会话切换。
   - `conversation.composer.dock` 展示当前引用上下文（项目/需求/知识）。
   - `settings.section` 注册 Mammoth 设置（Domain Service 地址/登录、数据根、导入、通知、审计入口）。
   - 可选：`conversation.view` 会话内 Mammoth 项目视图、`conversation.chat.turnTail` 保存动作、右侧栏知识预览。
4. **业务边界与权威数据**：
   - Mammoth Domain Service 是唯一业务权威：客户/产品/项目/知识/工作项/方案/交付/审计。
   - DSH storage/会话日志只允许非权威本地状态（如当前项目、UI 偏好、会话↔项目映射的过渡实现）；不得写入业务权威数据。
   - Agent 只能通过 Domain Service 的受控 API/MCP/工具读写；**不得直连业务数据库**；正式状态变更必须经 Domain Service 授权、审计与人工审批（四眼）。
5. **Agent 参与方式**：
   - Host 插件提供 `mammoth_*` 工具（读 + propose 写）；保留现有 3 个 MCP 只读工具。
   - 会话绑定项目后，Host 插件通过 `systemPrompt.section`/`agent.inject` 注入项目上下文（项目卡、客户/产品、状态、基线、知识范围）；工具按会话绑定项目解析，降低模型传错 projectId 的风险。
   - Agent 的输出以工具卡片/提议卡呈现；写操作先 `propose`（草稿/提议），由人在工作台确认；DSH 工具级审批（ask）与 Domain 侧人工审批组合为**目标形态**。**Spike 实测**：DSH 审批闸门可 fail-closed/`allowed-once`，工作台批准后可执行 Domain 写入并留审计；但"人类亲自批准"与"Domain 双人四眼"尚未由正式实现证明，列为条件项（见"Spike A 执行结果"与 `SPIKE-REPORT.md` §15）。
6. **身份与权限**：
   - 桌面单机模式：Mammoth 本地账号登录 Domain Service；凭据走 DSH `ctx.credentials` 或 OS 凭据，不入库/不入日志/不入提示词。**Spike 例外（已记录）**：为验证链路，Host/适配器的合成账号凭据暂存于仓库外的机器本地文件与进程环境；正式实现必须迁移到 DSH 凭据服务/OS 存储，且不得由 Agent 可读。
   - 团队模式：Desktop 作为客户端连接团队 Domain Service（TLS/token）；多用户/多实例按 ADR-0012/M5。
   - Agent 使用专用 `account_type=agent` 账号（不可登录、项目 viewer、服务身份）；MCP 身份从临时 env var 向服务令牌演进（另立 ADR）。
   - DSH 的"权限"只作为工具/文件审批层，不作为 Mammoth 业务权限来源；业务 RBAC 始终由 Domain Service 强制。
7. **现有 SPA 处置**：**不删除、不冻结**。定位为：(a) 团队 Web 管理台/后备 Web 客户端；(b) 页面信息结构与业务逻辑的迁移参考；(c) 现有 E2E 与验收基座。迁移按模块渐进，禁止"大爆炸重写"，禁止为 Desktop 化裁剪业务模块。
8. **ADR-0002 修订**：将 UI 宿主决策从"B1 独立 SPA 为正式产品 UI 基座"修订为"**DSH 宿主为主 + 独立 SPA 保留为管理台/后备 Web**"；Spring Boot/Java/Flyway/数据根等后端决策不变。此修订援引 ADR-0002 自身条款"UI 如需嵌入 DSH → 另立 ADR 变更 B1 为 B2"。**时序**：负责人批准的是"方向"；在 ADR-0014 转为 Accepted（即 Spike 通过）之前，ADR-0002 保持有效，不提前修改其状态；状态更新以 Spike 通过 + 本 ADR Accepted 为前置。
9. **版本与升级**：
   - Mammoth bundle 声明 `engines.dsh` 锁定到目标 DSH 版本（Spike 基线 `0.2.2-rc.2`）。本 bundle 刻意保持**零 DSH 运行时依赖**（Host 为普通 ESM、Client 只 `require('react')`），因此不声明 `peerDependencies`；若后续改为依赖 DSH Host 包，必须同时声明 peer/dev 依赖并按 DSH 的解析规则安装。
   - DSH 升级流程：读 upgrade-guide → 对照新 slot catalog → 重建 Client bundle → 跑契约回归 → 灰度/回滚；所有 slot/Client API 使用收敛到 `dsh-adapter` 层。
   - 仅按官方模板使用 `window.__ModuleLoader__.load` 入口；不依赖 combo URL、`__DSH_BOOT__`、模块图内部协议等模板外细节；不替换 shipped UI；不镜像私有宿主实现。
10. **多用户/B-S 演进**：
    - 保持 Domain API 为产品核心、Runtime Independence（原则 7）；未来独立 B/S 客户端以 Domain API 为基座演进（现有 SPA + 规划中的 Web 客户端）。
    - 若要服务端 Agent：Mammoth 服务端编排 DSH 实例（每用户/每会话一个 runtime，经 ACP/SDK），DSH 不充当平台骨架。
    - 不把 Mammoth 业务模型迁入 DSH workspace/storage；DSH workspace 仅作执行目录。
11. **产品边界不缩减**：本次客户端变更不删除任何已确认业务模块；客户档案、产品档案、项目↔产品关联、跨项目需求池、审计 UI 等当前缺口必须在迁移计划中补齐（见评估报告 §4、§7）。
12. **决策门槛（重新评估触发条件）**：
    - Spike 证明 Client→Domain 数据通道不可行，或
    - Spike 证明完整业务工作台在 DSH slot 内不可接受（性能/交互/主题限制），或
    - DSH rc 期破坏性变更导致升级成本超出可接受范围，或
    - 负责人确认"独立应用品牌/多窗口/完全自有 UI"为硬需求
    → 暂停本 ADR 的实施，提交修订，重新评估方案 B/C（不得静默继续或私自简化产品边界）。

## 与 ADR-0001 的关系（边界扩展，必须显式记录）

ADR-0001 §6.3 将 **MCP 定为 Domain API↔DSH 的推荐边界**（配置型 bundle + `dsh-mcp-client`，零 DSH 依赖）。本 ADR：

- **保留** MCP 作为只读边界不变（现有 3 个只读工具、verified 知识、项目成员、审计）。
- **新增** Host 插件经 HTTP 调用 Domain Service 的通道（`mammoth_*` 工具读 + propose 写），这是对 ADR-0001 §6.3 的**边界扩展**，不是否定。
- 该写通道（认证/服务身份、工具风险分级、审批、审计、四眼、回滚）必须由**"Agent 受控写操作与服务身份"独立 ADR** 覆盖，并在该 ADR 中说明是否修订 ADR-0001 §6.3；在独立 ADR 批准前，Spike 只允许在隔离 stub/分支验证写路径，不进入产品代码。
- 若独立 ADR 不批准 HTTP 写通道，则退回"MCP 只读 + 人工在 Domain Service 页面写"的最小边界，客户端 UI 与工作台不受影响。

## 决策理由

1. **产品形态命中**：只有方案 A 同时提供 Desktop 原生 Agent 会话与完整业务工作台同屏，且无需自研 Agent UX；B/C 需自研聊天/会话/工具卡/审批（B 还要自研壳），是重复建设。
2. **官方支持路径**：A 是 DSH 官方文档化的第三方扩展面（bundle/插件/slot/MCP/preset）；B 的完整进程内组装无公开产品级契约（公开低层 boot helper 不等于产品级嵌入契约），SDK/ACP 不足以替换官方 Client；C 自研范围最大。
3. **资产复用**：Domain Service/DB/权限/审计/MCP/后端测试高复用；SPA 的 API 客户端、权限/格式化逻辑、信息结构与页面组件可迁移；UI 重写范围是"壳 + 页面组件"，不是业务逻辑。
4. **风险可控/可回滚**：外部 bundle 可卸载、Profile 可备份、旧 Desktop 可回退；SPA 保留为后备；Domain API 保持独立，避免锁死 DSH。
5. **不违反既有决策**：ADR-0001 的"不 Fork/业务状态独立"不变；MCP 只读边界保留，HTTP 写通道由独立 ADR 决定（见"与 ADR-0001 的关系"）；ADR-0002 明确预留了 UI 宿主变更条款；后端技术栈/数据边界不变。
6. **接受的反方事实**：现有 SPA 在 A 下不能 iframe、需要迁移（附件 01 认为 B 与资产最连续）。本 ADR 承认该成本，但判断 A 的"有边界页面迁移"小于 B 的"整套 Agent UX + 壳 + 私有宿主组装"，且 A 获取产品能力的路径最短、风险最清晰。

## 取舍和影响

**正向**

- 获得 DSH 原生 Agent 能力（会话/工具/审批/沙箱/子代理/skills/模型路由），与 Agent-first 愿景一致。
- 业务工作台与对话同屏，项目上下文可注入，业务结果可卡片化回流。
- 服务端领域资产完整保留，B/S 演进路径清晰。
- 通过官方扩展面实现，避免 Fork 与私有镜像。

**负向/成本**

- 现有 SPA 页面需按 DSH 规则迁移为 Client 插件组件（约 4.6k 行页面 + 样式），不能直接内嵌；工作量见评估报告 §7.4。
- 插件工程新增：Client bundle 构建、slot 适配、数据通道、主题/locale、插件分发。
- 与 DSH rc 版本强耦合：每次升级需回归/重建；需锁定版本与升级窗口。
- 外部 bundle 安装执行 Host 代码：供应链/授权风险，需要安装审批与回滚。
- 没有独立应用级路由/多窗口：`main` 单 keyed cell、页面状态 reload 重置、无浏览器 URL；需 Mammoth 自建内部导航。
- Desktop 单用户与 DSH Web 单 owner 限制：团队多用户仍依赖 Domain Service 与后续 Web/B-S 客户端。

**兼容性**

- Domain Service/API 无需大规模改动即可支撑 Spike；后续可能需要：CORS/桌面 token 认证、propose 写 API、AgentRun/session link（V11，另立 ADR）。
- 现有 SPA 继续可用；现有 MCP bundle 可保留/并入 Mammoth bundle。
- 现有测试与 CI 继续有效；新增插件构建/回归流水线。

## 安全/数据/迁移影响

- **数据主权**：业务权威数据仍在 Domain Service；DSH storage/会话不承载权威业务数据（延续 ADR-0001/0008）。
- **权限**：业务 RBAC/org_admin 由 Domain Service 强制；DSH 工具审批只是附加人工闸门。**四眼/双人审批**在本 Spike 仅由"提案者=agent 身份、批准侧=人类账号"的身份区分与适配器检查近似实现，Domain Service 当前对 work-item 创建没有"双人"规则——正式四眼必须由"受控写操作与服务身份"ADR 定义（服务身份、Domain 侧审批、人类独占通道、审计）。
- **Agent 身份**：专用 agent 账号 + 项目 viewer；MCP env 身份为过渡，服务令牌/网关另立 ADR。
- **凭据**：只走 `ctx.credentials`/环境变量/OS 存储；不写入 DSH storage、日志、提示词、仓库；继续禁止读取 `~/.dsh` 凭据与业务数据。Spike 例外见 §5（合成账号凭据仅在仓库外机器本地文件/进程环境，且其残余风险在 `SPIKE-REPORT.md` §10/§15 明确记录）。
- **插件信任**：安装/启用/卸载 bundle 属于运行环境实质变更，需负责人批准、记录来源/版本/`engines.dsh`、提供回滚；不安装未审计第三方 bundle。
- **迁移**：本轮无数据库迁移；AgentRun/session link/propose 写 API 需后续 ADR + V11，不得在客户端 Spike 中夹带。
- **合规**：客户资料按 Confidential；导出/交接包继续按 ADR-0008 管理；不把真实客户数据用于 Spike（合成/脱敏）。

## 验证计划和结果

**前置**：负责人批准本 ADR 进入 Spike；批准 bundle 安装（Host 代码执行/Profile 变更）。

**Spike（详见评估报告 §6）**：

| 阶段 | 验证 | 通过标准 |
|---|---|---|
| S-A0 | 安装 Mammoth bundle（最小 Host 工具） | `application: applied`；工具出现/卸载消失；可回滚 |
| S-A0b | 4 合 1 bundle（Host 工具 + Client UI + MCP 配置 + preset）组装与安装 | 四类内容均生效或可解释；卸载可回滚；`engines.dsh` 负向拒绝可用 |
| S-A1 | 注册 Mammoth 导航与工作入口 | 左栏入口 + `main` 页面真实打开；主题/locale 正常；卸载无残留 |
| S-A2 | 完整项目内容面板 + 数据通道三选一（C1 Client 直连 REST / C2 Host 桥（projection `wire.view`、`ctx.commands`、既有 Remote）/ C3 工具结果卡片降级） | 真实数据、权限正确、无 CORS/凭据错误；形成通道选型结论；自定义 Remote 可行性单独给结论 |
| S-A3 | 选择项目并建立项目上下文 | 新会话首轮包含项目上下文；切项目不串；非成员不可选 |
| S-A4 | 创建/关联 DSH 会话 | 会话可创建/复用并绑定项目/工作项；头部徽章正确 |
| S-A5 | Agent 使用 Mammoth 项目知识工具 | 3 个只读工具端到端；权限拒绝；审计入库 |
| S-A6 | 渲染 Mammoth 业务结果卡片 | 三阶段渲染正确、失败态可见、跳转可用、不 blank slot |
| S-A7 | 受控写操作与审批边界 | DSH 审批 + Domain 提议/四眼串通；拒绝路径无写入；Agent 不能自批；审计完整 |
| S-A8 | 桌面文件与工作区 | 目录选择/上传合成资料可用；不读 `~/.dsh`、不碰真实数据 |
| S-A9 | DSH 升级回归（可延后） | 形成升级 SOP 与回归清单 |
| S-A10 | 启动性能与插件包体测量 | 首帧增量/体积在负责人确认阈值内，或形成优化清单 |

**Spike 未通过时（历史条款，已由 2026-11-16 定稿取代）**：若未来任一决策门槛被触发，本 ADR 回到 Proposed 修订流程；不得把未验证能力写入产品承诺。

## Spike A 执行结果（2026-11-16，Accepted 依据）

**总体结论（经独立评审整改）**：路线 A **技术可行性可接受**——S-A0/S-A0b/S-A1–S-A3 通过、S-A4/S-A6 部分通过、S-A5 通过（含负向）、S-A2 数据通道 **Go（C2 Host 桥）**；**S-A7 条件通过**（机械链路与审计已证，但"人工批准"未由独立人类验证、适配器不构成同 OS 用户的安全边界）；未触发任何"技术不可行"决策门槛。完整报告、截图、调用记录与整改记录见
`docs/evidence/2026-11-13-client-arch/spike-A/SPIKE-REPORT.md` 与同目录 `README.md`（17 张真实 Desktop 截图 + JSON/JSONL 证据 + SHA-256 清单）。

| 阶段 | 结果 | 关键证据 |
|---|---|---|
| S-A0 | 通过 | `link:` 安装/激活/工具可用；**卸载→入口消失/profile 恢复→重装→恢复**往返已执行（`plugin-lifecycle-evidence.md`、截图 16/17） |
| S-A0b | 通过 | 单包同时生效：Host 行 + MCP 行（3/3 工具）+ preset 行 + `dsh.client` 浏览器半；Plugins 页图标/标题/详情；**`engines.dsh: >=9.0.0` 安装被 `incompatible-version` 拒绝**（负向） |
| S-A1 | 通过 | 左栏 Mammoth 入口 + `main` 全页工作台（截图 07/08） |
| S-A2 | **通过（Go）** | 真实 Domain 数据（截图 01–04）；Client 页面 origin `dsh-app://app/`，经 shell fetch 桥同源访问 Host 受鉴权路由（截图 12/13）；无 CORS、无客户端凭据 |
| S-A3 | 通过 | 选择项目 → 新会话自动绑定（2 个 `reason: session-created` 样本）→ `systemPrompt` 项目卡注入（截图 15；`host-plugin-state.json`） |
| S-A4 | 部分通过 | 会话头部项目徽章（截图 06）+ 右栏项目上下文页（截图 05）；"一键新建并进入会话"未实现 |
| S-A5 | 通过 | 3 个只读 MCP 工具端到端；本轮补负向：非成员项目 `ApiException: no access to project`（截图 14） |
| S-A6 | 部分通过 | 知识成功卡片（截图 11）+ MCP 失败态卡片（截图 14，不 blank slot）；提案结构化结果见 09/10；卡片跳转未实现 |
| S-A7 | **条件通过（真人批准已补齐）** | 机械链路：DSH 审批（`allowed-once`；`never` 时 fail-closed）→ 工作台批准 → 适配器以人类 Domain 账号写入。**2026-11-16 11:13 项目负责人本人在工作台批准提案 `db238b78` → Domain `REQ-0003`**（`audit_log.actorUserId=spike_admin`，agent 401、不能自批；截图 18/19）。剩余条件：适配器与 Agent 同 OS 用户的边界（独立写路径 ADR）+ 负责人签署 |
| S-A8 | 未执行（后续项） | 任务书 §四 未列；不影响路线 A 技术结论 |
| S-A9 | 未执行（可延后） | 后续首个 DSH 升级窗口前置任务 |
| S-A10 | 未执行（后续项） | 当前 Client 单文件 ~65KB、仅 `require('react')`；未测量首帧增量 |

**相对草案的定稿修订（必须随 Accepted 生效）**：

1. **右栏扩展点修订**：0.2.2-rc.2 的 `rightbar` / `rightbar.session` 是已被 ui-sidebar-right 占用的 single slot，第三方**不得替换**；项目上下文右栏改用官方扩展席位 `ctx.sidebarRightTabs.register()` + keyed body `sidebar.right.pane.tab`（Spike 截图 05 验证）。原文 §3 中"rightbar / rightbar.session"的表述按此修订。
2. **数据通道定稿**：采用 **C2 Host 桥**，具体实现为 Host 插件 `ctx.webServer.register()` 注册受 `ctx.connection.requestRejection` 鉴权的同源路由，Client 在 `dsh-app://app/` 页面内用相对 `fetch` 访问；不使用 C1（浏览器直连 Domain，CORS/凭据不可接受），C3（工具/卡片）保留为降级路径。自定义 Typert Remote 不作为第三方通道（安装版无 codegen/装配工具链）。
3. **会话上下文定稿**：Host 按 DSH Session 绑定项目（`session/created` + 首条 `user/message`），以 `ctx.systemPrompt.section` 注入项目卡；映射先以非权威本地状态过渡，正式数据模型等待 V11。
4. **写路径定稿（边界不变；机械链路 + 真人批准均已验证）**：Agent 只能 `propose`（待审核提案）；目标形态是正式写经 DSH 工具审批 + 工作台人工确认 + Domain 权限/审计（以及独立 ADR 定义的 Domain 双人审批）；Agent 不能自批、不能直写。**Spike 已验证**：负责人本人于 2026-11-16 在工作台完成一次真人批准并产生 Domain `REQ-0003`；DSH 审批 `allowed-once`、fail-closed。**仍未证明/未闭合**：Domain 双人四眼；且隔离适配器与 Agent 同 OS 用户，不构成安全边界——后者必须由"Agent 受控写操作与服务身份"独立 ADR 闭合，"人工闸门已验证"不得替代该 ADR。正式 propose 写 API 与服务身份由该 ADR 决定。
5. **版本策略**：bundle 锁定 `engines.dsh`（零 DSH 运行时依赖，故不声明 peerDependencies）；每次升级按升级 SOP 回归；所有 Slot/Client API 使用收敛到 `dsh-adapter` 层。
6. **安全补充**：工具 `parameters` 必须走注册前本地 schema 校验（raw JSON Schema 子集；`required` 只能是根级数组），并保留 `scripts/check-tool-schemas.mjs` 作为安装/重载门禁（2026-11-13 P0 事故防再发）。
7. **已接受的产品/工程代价**：会话↔项目映射为过渡实现；`conversation.chat.node`、`composer.dock`、卡片跳转、S-A8/S-A9/S-A10 为后续项；DSH rc 期升级风险由版本锁定与 SOP 管理。

**签署**：本版本为 2026-11-16 定稿，**待项目负责人签署后生效**。定稿依据：Spike A 证据（技术可行性 + S-A2 Go + S-A7 机械链路与**负责人本人真人批准** `REQ-0003`）与独立评审整改记录（`SPIKE-REPORT.md` §15）。生效条件：①负责人签署；②"Agent 受控写操作与服务身份"独立 ADR 关闭适配器同 OS 用户边界（真人批准已由负责人于 2026-11-16 完成，见"Spike A 执行结果"S-A7）；③"S-A8–A10/后续项"不阻塞本 ADR 的架构方向。若负责人变更任一决策项，本 ADR 回到 Proposed 修订流程。

## 回滚或替代路径

- **回滚**：`plugin_manager remove_bundle`；恢复升级前 `$DSH_PROFILE_DIR` 备份；必要时回退 DSH Desktop 版本；保留现有 SPA 继续作为产品 UI（当前形态可运行）。
- **替代**（注意：本 ADR 的"路线 B"指自研 Electron 壳，与 ADR-0002 中的 UI 子方案 B1/B2 不是同一组编号）：
  - 路线 B-1：自研 Electron 壳 + ACP 子进程 + 现有 SPA（资产连续性最高，但需自研 Agent UX）。
  - 路线 B-2（不推荐）：镜像私有 `dsh-desktop-host` 完整组装做进程内嵌入（无第三方产品级契约，风险最高）。
  - 路线 C：独立 B/S 平台，Mammoth 服务端编排 DSH 实例（长期）。
- 回滚不得删除业务代码/数据；Domain Service/API 契约保持稳定。

## 后续行动

1. ~~负责人批准本 ADR 草案与 Spike（§0.2 D1–D4）~~（已完成：Spike 已批准并执行）。
2. ~~执行 Spike S-A0b、S-A1–S-A8、S-A10~~（已完成 S-A0b、S-A1–S-A7；S-A8/S-A9/S-A10 见下）；证据入 `docs/evidence/2026-11-13-client-arch/spike-A/`。
3. **后续项**：S-A8（桌面文件/目录/artifact 上传）、S-A9（升级 SOP 演练）、S-A10（启动性能/包体测量）；`conversation.chat.node` 自定义节点、`conversation.composer.dock` 上下文引用、从工作台一键新建并进入会话。
4. 同步修订 `docs/05`、`docs/13`、`docs/12`、`docs/specs/UI-SPEC.md`；更新 ADR-0002 状态/补充说明（UI 宿主变更，后端不变）。
5. 建立 `integrations/dsh/mammoth-bundle/` 正式工程（Spike 分支 `spike/dsh-desktop-integration` 已提供骨架）；CI 增加 Client 预构建、`check-tool-schemas`、契约冒烟回归。
6. 另立 ADR：Agent 受控写操作/审批/服务身份、AgentRun/agent_session_link（V11 迁移）、MCP 写工具或 Host 工具边界（含是否修订 ADR-0001 §6.3）。
7. 按评估报告 §4 IA 与 §7 复用计划分阶段迁移；保留 SPA 为管理台/后备 Web；在 `docs/12-ROADMAP.md` 新增客户端架构阶段（编号待负责人确认）。
8. 每次 DSH 升级执行升级 SOP 并记录；维护 `engines.dsh`/peer 版本矩阵与升级回归证据。
9. 环境治理：开发中的 `link:` bundle 优先使用**专用 dev profile**；共享日常 profile 时必须按生产变更处理（备份 → 自检 → 重载 → 最小请求验证）。
10. **生效条件（阻塞项）**：①项目负责人签署本 Accepted v1.1；②~~由负责人亲自完成一次"提案 → 工作台人工批准 → Domain 写入"的真人复验并留痕~~（**已完成**：2026-11-16 11:13 负责人本人批准提案 `db238b78` → Domain `REQ-0003`，截图 18/19、`domain-after-approval.json`）；③"Agent 受控写操作与服务身份"ADR 定义隔离身份/Domain 授权/人类独占通道，关闭"适配器与 Agent 同 OS 用户"的边界缺口。
