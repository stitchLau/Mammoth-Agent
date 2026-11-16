# Session Handoff — Mammoth × DSH Desktop 集成 Spike A

- **时间**：2026-11-16（Asia/Shanghai）
- **当前分支 / Commit**：
  - 主工作区：`main`（本地领先 origin/main 1 个提交 `f570c24`，另有未跟踪交付物：ADR-0014、评估报告、`docs/evidence/2026-11-13-client-arch/spike-A/`）
  - Spike 代码分支：`spike/dsh-desktop-integration` @ `6ad7ddc`（Worktree：`E:\MyProj\MommothAgent\code\Mammoth-Agent-spike`）
- **当前任务**：Mammoth Desktop Integration Spike（任务书：路线 A 真实可行性验证）
- **会话目的**：在真实安装的 DSH Desktop 0.2.2-rc.2 中验证四合一 Mammoth 外部 Bundle、真实 Domain 数据、会话项目上下文、MCP 知识、业务卡片、Proposal 人工审批写边界；产出 ADR-0014 Accepted 依据。

## 已确认事实

1. 四合一单包 `@mammoth/dsh-bundle` 可在 `desktop` profile 安装并同时生效：Host 行 + MCP 行（3/3 工具）+ preset 行 + `dsh.client` 浏览器半（截图 00–13）。
2. **Desktop 渲染页面 origin 是 `dsh-app://app/`**；Client 对同源相对路由 `mammoth/api/*` 的 `fetch` 经 Desktop shell fetch 桥被认证为浏览器 Peer，访问 Host `ctx.webServer` 路由；Client 不直连 Domain、不需要 CORS、不持有凭据（截图 12/13）。
3. 会话↔项目上下文：工作台选择项目 → Host 绑定 Session → `ctx.systemPrompt.section` 注入项目卡；会话头部徽章 + 右栏 `sidebarRightTabs` 项目上下文页可见（截图 05/06）。
4. 现有 Mammoth Domain MCP 3 个只读工具在真实会话可用；Agent 拿到 verified 知识与来源数（`tool-call-records.md` §2）。
5. Proposal 链路三段闸门实测：DSH 工具审批 `allowed-once`（策略 `never` 时 fail-closed）→ 工作台人工批准 `local-desktop-human` → 隔离适配器以人类 Domain 账号写入 `REQ-0002`；Domain `audit_log.actorUserId=spike_admin`；agent 账号登录 Domain 401（截图 09/10/13、`domain-after-approval.json`）。
6. 2026-11-13 P0（raw `parameters` 的 `required:true`）防护已在本 bundle 落地：注册前本地 schema 校验 + `check-tool-schemas.mjs`，本轮每次重载均通过。
7. Client 注册在 Desktop 中抛错**无可见反馈**；本轮修复为"入口/页面先注册 + 逐面隔离 + 工作台内置诊断"，并修正 `sidebarRightTabs.guide` 必须为数组（提交 `c5fa625`）。

## 已做变更

- **Spike 分支（已提交）**：
  - `dec48c4` — bundle 主体 15 文件（Host/Client/MCP/Preset 模板、locale/icon/docs、schema 自检、configure 脚本）+ 工具 schema 注册前防护；
  - `74128cf` — 隔离 Proposal 适配器 `spike/dsh-desktop-integration/proposal-stub.mjs`（Spike-only，非产品代码）；
  - `c5fa625` — Client UI 修复（入口优先注册/逐面隔离、guide 数组、内置诊断）；
  - `70f2e5a` — bundle README 记录 Spike 实测结论与纪律；
  - `c7da93a` + `6ad7ddc` — Spike A 证据包（24 文件：截图/JSON/JSONL/报告）。
- **主工作区（未跟踪/修改，需负责人审阅后提交）**：
  - `decisions/ADR-0014-CLIENT-SHELL-AND-UI-EXTENSION.md` — 状态改为 **Accepted v1.0（2026-11-16）**，新增"Spike A 执行结果"与定稿修订、后续行动更新；
  - `docs/evidence/2026-11-13-client-arch/spike-A/` — 证据索引/报告/调用记录/截图/JSON；
  - `docs/reports/2026-11-13-CLIENT-ARCHITECTURE-REEVALUATION.md` — §6 增加"执行结果"指引；
  - `docs/handoffs/SESSION-HANDOFF-2026-11-16-SPIKE-A.md` — 本文件。
- **环境**：`desktop` profile 仍安装 `@mammoth/dsh-bundle`（`link:` 到 spike worktree）；Domain writer（18090）、Proposal 适配器（18091）运行中；profile 清单备份在 `%TEMP%\mammoth-spike\profile-backup`；Windows 权限修复备份在 `.acl-recovery/`。

## 独立评审与整改（2026-11-16，已整合）

独立评审子代理结论为**有条件接受**（技术可行性可接受；S-A7 人工治理结论与 ADR 文本不可原样接受）。已整改：

1. **S-A7 降级为"条件通过"**：机械链路 + 审计已证；但"人工批准"点击由 Lead Agent UI 自动化完成（非独立人类），
   且适配器与 Agent 同 OS 用户 **不是安全边界** → 报告/ADR/README 统一改为条件表述，ADR 定稿为"待负责人签署 +
   条件生效"。
2. **适配器加固**：DSH 审批 fail-closed（仅 `allowed-once`）；适配器 token 鉴权（`/health` 外 401）、移除 CORS `*`、
   approve/reject 必须回显提案 id、approver 固定、关闭无鉴权列表；Host 拒绝非 confirm 请求。负向证据见
   `proposal-stub-auth-negative.json`。
3. **补齐证据**：卸载→重装往返 + `engines.dsh` 负向门禁（`plugin-lifecycle-evidence.md`、截图 16/17）；
   会话自动绑定（2 条 `session-created`）；MCP 非成员拒绝与工具卡片失败态（截图 14）；
   截图 sidecar 归入 `screenshots-metadata/`，运行时 JSON 以 UTF-8 重生成，新增 SHA-256 清单。
4. **ADR 修正**：删除"Accepted vs 待批准"矛盾；四眼/凭据/peerDependencies 表述改为与事实一致；签署改为
   待负责人签署；生效条件加入"真人审批复验 + 写路径 ADR"。

**仍未闭合（阻塞 ADR 生效）**：①~~负责人签署 ADR~~（**已完成**：2026-11-16 负责人签署 ADR-0014 Accepted v1.1）；
②~~"Agent 受控写操作与服务身份"ADR~~（**已完成**：ADR-0015 于 2026-11-16 经负责人确认决策项转为 Accepted v1.0；P1 实施计划 `docs/specs/ADR-0015-P1-CONTROLLED-WRITE-PLAN.md` 待批准启动）；
③S-A8/S-A9/S-A10、卡片跳转、`conversation.chat.node`、`composer.dock`、preset UI 验证为后续项。

**ADR-0015 决策确认（2026-11-16）**：本机独立 OS 账号/独立进程 + OS 凭据存储；全员四眼（提案者≠批准者）+ 高风险类别（方案定版/交付结项/批量删除类/需求重大流转/真实客户数据导出）第二人复核；人类审批走 Domain 会话/RBAC；DSH 工具审批保留为附加闸门；远端方案随 M5。
（原"负责人真人复验"条件**已闭合**：2026-11-16 11:13 负责人本人在工作台批准 `db238b78` → Domain `REQ-0003`，
截图 18（修复前）/19（修复后）、`domain-after-approval.json`、`proposal-stub-state.json`。）

**2026-11-16 11:00–11:30 追加修复与验证**：

- 工作台批准失败 `MAMMOTH_STUB_HTTP_ERROR … confirm-required`：Host 校验 `confirm` 后未转发给适配器 →
  已修复（`10efdea`），负责人随后成功批准；
- 提案页把 `domainObject` 等后端字段以原始 JSON 直出（用户发现）：判定为 **Spike 演示页 UI 缺陷**（非产品设计），
  已修复为业务字段主视图 + 「技术信息」折叠 + 「在需求池查看 REQ-xxxx」入口（client-only，HMR 生效；
  截图 18 修复前 / 19 修复后），并记入报告 §10/§15 与 `docs/DETAIL.md` 已知限制；
- 真人批准补齐后，S-A7 从"机械链路演示"升级为"负责人本人真人批准已验证"，剩余条件收敛为 ADR 签署与写路径 ADR。

## 运行过的命令和结果

- `node --check index.js / client.js / tool-schema.js / tool-parameters.js` → 全部 0；
- `node scripts/check-tool-schemas.mjs` → 全部 PASS（含 2026-11-13 事故回归夹具与 apply 激活捕获）；
- `plugin_manager install_bundle` → 安装成功（`link:`），三次 Loader 行 active；客户端经 client-hmr 热更新后渲染；
- Domain 隔离实例 `MAMMOTH_DATA_ROOT=%TEMP%\mammoth-spike-data\root MAMMOTH_SERVER_PORT=18090 java -jar services/domain/target/mammoth-domain-service.jar` → Flyway v10，健康；
- MCP 只读调用 3 工具成功；提案审批后 Domain `work_items=2`、`audit_log` actor=spike_admin；
- 真实 Desktop 截图 14 张（Win32 PrintWindow，1296×828）。

## 当前 Git 状态

- 主工作区 `main`：本地领先 origin/main 1；未跟踪 `decisions/ADR-0014...`、`docs/evidence/2026-11-13-client-arch/`、`docs/reports/`；本轮修改上述 ADR/报告/新增 handoff（均未提交）。
- Spike Worktree：clean，`spike/dsh-desktop-integration` @ `6ad7ddc`，40 files / +4406 相对基线 `fcc851c`。
- `E:\MyProj\MommothAgent\code\Mammoth-Agent\.acl-recovery\`（未跟踪，含 ACL 备份与回滚脚本）。

## 未解决问题

1. S-A8（桌面文件/目录/artifact 上传）、S-A9（升级 SOP 演练）、S-A10（首帧/包体测量）未执行；
2. `conversation.chat.node` 自定义节点、`conversation.composer.dock` 上下文引用、从工作台"一键新建并进入会话"未实现；
3. Preset 未在新建会话 UI 中实际选择并截图（`preset-mammoth` 行 active 已确认）；
4. 正式写路径（propose API、服务身份/令牌、四眼强制、审计）仍需独立 ADR；会话↔项目映射仍为 Host 本地 JSON（等待 V11 数据模型）；
5. DSH 为 `0.2.2-rc.2` 预发布版：升级回归与版本矩阵尚未建立；
6. `docs/05`、`docs/12`、`docs/13`、`docs/specs/UI-SPEC.md` 的 ADR 同步修订未做（ADR 后续行动第 4 项）；
7. 独立评审子代理 `spike-reviewer` 的结论尚未整合（若其发现 BLOCKER，需先修复再提交）。

## 尚未验证的假设

- 团队模式 / 多用户 / 远端 Domain Service（TLS/token）未验证；
- 网络代理、公司安全软件、Windows 混合 DPI 多屏下的截图/交互稳定性未系统验证；
- `main` slot 页面在切换会话/重启后的状态恢复语义（当前 reload 会重置工作台内部导航）；
- 性能影响（首帧增量、包体）未测量。

## 下一步最小行动

1. 等待/整合 `spike-reviewer` 结论；若有 BLOCKER 修复后复跑 `check-tool-schemas` 与冒烟；
2. 负责人审阅并提交主工作区的 ADR/报告/证据/handoff（按仓库惯例提交到 main 或先提交到 spike 分支再合并文档）；
3. 若继续：先做 S-A9（升级 SOP + slot catalog 对照）与 S-A10（性能/包体），再做 `conversation.chat.node` 与 preset UI 验证；
4. 另立 ADR：Agent 受控写操作/服务身份、AgentRun/session_link（V11）；
5. 环境清理或保留：见 `rollback-and-environment.md`。

## 新会话必须先阅读

1. `docs/quality/INCIDENT-2026-11-13-DSH-TOOL-SCHEMA.md`（最高优先级规则与防护）
2. `AGENTS.md` 顶部"DSH 工具 schema 与全局 Profile 变更"
3. `docs/evidence/2026-11-13-client-arch/spike-A/README.md` 与 `SPIKE-REPORT.md`
4. `decisions/ADR-0014-CLIENT-SHELL-AND-UI-EXTENSION.md`（Accepted v1.0）
5. Spike 分支 `integrations/dsh/mammoth-bundle/README.md` 与 `docs/DETAIL.md`
6. `docs/evidence/2026-11-13-client-arch/spike-A/rollback-and-environment.md`

## 风险提醒

- **不要**在未备份、未跑 schema 自检的情况下修改已 `link:` 进 `desktop` profile 的 bundle：源码即全局运行时，坏工具 schema 会让所有会话请求失败（2026-11-13 P0）。
- 开发中的 `link:` bundle 优先用**专用 dev profile**；共享日常 profile 必须按生产变更处理。
- 不要替换官方 `rightbar`/`rightbar.session` 单槽；第三方右栏用 `sidebarRightTabs` + `sidebar.right.pane.tab`。
- 不得把 Domain 凭据（读取账号/approver 账号）写入 Client、提示词、日志、仓库或截图；Spike 的合成密码也不得出现在交付文档中。
- 不得把 `~/.dsh` 的会话/存储/凭据数据导入知识库或交付物；本轮证据仅来自合成数据与 ADR/报告。
- Agent 不能自批/直写业务数据；正式写必须经 DSH 工具审批 + 人工工作台批准 + Domain 权限/审计/四眼。
- DSH 为 rc 版本：升级前读 upgrade guide、重建 Client 产物、跑契约回归并保留回滚。
