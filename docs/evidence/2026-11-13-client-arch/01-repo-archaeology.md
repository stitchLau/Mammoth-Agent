# 01 — 现有真实架构还原与代码资产复用评估（只读考古）

- **角色**：Research Analyst（只读架构考古），由 Lead Agent `lead` 委派，代理名 `repo-auditor`
- **日期**：2026-11-13（Asia/Shanghai）
- **审计对象**：`E:\MyProj\MommothAgent\code\Mammoth-Agent`，`main` @ `fcc851c`（2026-11-13 15:56:14 +0800，`docs(m4.5): record HSG import, agent hooks and Docker verification`）；开始时 `git status --porcelain=v2 --branch` 无文件改动
- **任务性质**：只读研究，不写产品代码。**唯一写入**：本报告文件；`mvn test`（任务明确允许的可选命令）只写 `services/domain/target/` 构建产物，不触及源码/配置/数据库/迁移，执行前后 `git status` 均为 clean。
- **证据等级**：`[事实]`＝代码/文档/命令可直接复核；`[推断]`＝由事实外推并给出依据；`[待验证]`＝需运行/交互才能确认。
- **环境核对**（只读，未读 `~/.dsh` 凭据或业务数据）：本机 DSH 安装为 `@deepseek-ai/dsh-desktop@0.2.2-rc.2`、`@deepseek-ai/dsh@0.2.2-rc.2`（`app.asar/package.json:4`、`app.asar/dsh/node_modules/@deepseek-ai/dsh/package.json:4`），Profile `desktop`，与仓库 M0 记录的 rc.2 一致。

---

## 0. 结论摘要（先看这里）

1. **现状是"业务管理系统 + 外部只读 Agent 旁路"，不是"以 Agent 为主要工作入口的桌面工作台"**。SPA 侧有 19 个页面、全部是项目上下文的列表/详情/表单型管理界面；没有任何聊天/Agent 会话路由或 LLM 调用；Agent 只能由一个**独立安装的 DSH** 通过 MCP stdio 调用 3 个只读工具（`apps/web/src/App.tsx:44-71`、`services/domain/src/main/java/com/mammoth/domain/mcp/McpServerMain.java:128-166`）。
2. **这套架构是 ADR 决策链的产物，不是失控**：`ADR-0001`（方案 B：out-of-tree Bundle + 独立 Domain API）→ Spike S1（外部 bundle 依赖耦合 F2、UI slot 未做视觉验证）→ Spike S2（MCP 边界可行、配置型 bundle 零 DSH 依赖）→ `ADR-0002`（方案 B + **B1 独立 SPA**，M1 不嵌入 DSH Client slot）。`docs/05` §4 原建议中的"DSH Bundle/Plugin UI"这一半被 ADR-0002 显式替换为独立 SPA；"Agent Tools"这一半以只读 MCP 落地。`[事实]`
3. **代码资产规模与质量足以支撑重估**：后端 171 个 Java 主文件 / 16,800 LOC / 38 个测试类 / 124 tests（最近一次正常终端 run：0 failures/errors）；前端 19 pages / 6,599 LOC（非测试）/ 25 个 vitest 用例 / 5 个 Playwright E2E；10 个 Flyway 迁移、39 张表（35 张导出表）、107 个 `/api` 端点映射。`[事实]`
4. **产品交互形态确实发生偏移且被"批准链"合法化了**：M1 定独立 SPA（ADR-0002），M2 明确"不引入应用内 LLM"，M4 明确"应用内 LLM/AgentRun 另立 ADR"，M5 仍不做；`docs/05` §2 曾把"Agent 会话入口"写进 UI 层，但从未实现。偏移不是违规，而是**产品形态从未被单独决策**的结果。`[事实] + [推断]`
5. **Agent 侧已有 5 类"种子"但都停在只读/契约层**：专用 `account_type=agent` 账号、3 个只读 MCP 工具 + 环境变量身份、MCP 调用审计（JSONL→`agent_call`）、BG-01 交接包 ZIP、`agent.task.*` Hook 通知；**不存在** `AgentRun`/`AgentTask` 表、应用内会话、Agent 写操作、工具审批。`[事实]`
6. **复用评估（技术复用，不含产品价值）**：Domain Service / SQLite schema / 权限 / MCP / 后端测试 = **高**；检索 = **高但只到全文**；SPA 页面 = 在"浏览器窗口 / Electron 壳"形态下**高**，在"DSH Host 内嵌页面"形态下**低-中（必须按 DSH 官方规则重写为 React slot 组件，不能 iframe）**。三路线中**路线 B（自研 Electron 桌面壳 + 集成 DSH Runtime）与现有资产最连续**；路线 A 的 UI 层是重写；路线 C 新增 Agent Runtime 最多。`[推断]`
7. **DSH 官方实践明确禁止 iframe 嵌入 Host HTML**（本机安装版 rc.2 的 dsh-doc 引用文档 `references/practices.md:33`）：插件页面必须作为 slot 中的 React 组件渲染，以便继承 Host 主题 token、明暗切换与 `ctx.locale`。现有 SPA 是自带 `index.html`、`react-router`、自有 CSS 的独立应用，**不能直接作为 DSH 内嵌页复用**；但"现有 SPA 作为外部浏览器窗口 / 桌面壳 renderer"不受此限制。`[事实] + [推断]`

---

## 1. 审计范围、方法与事实基线

### 1.1 读过/核过什么

| 类别 | 对象 |
|---|---|
| 必读文档 | `AGENTS.md`、`docs/00`～`13`、`dev-setup.md`、`dsh-mcp-integration.md`、`docs/M1`～`M4-ACCEPTANCE.md` |
| ADR | `decisions/` 全部 12 个文件（0001–0008、0010–0013；**无 ADR-0009 文件，ADR-0009 仍 OPEN**） |
| Spike | `docs/spikes/S1-DSH-EXTENSION-SPIKE.md`、`S2-DOMAIN-API-MCP-BOUNDARY.md`、`S3-TECH-STACK-SPIKE.md`（S4–S7 结论经 ADR/13 转引核对） |
| 规格 | `docs/specs/M1`～`M4-PHASE4-PLAN.md`、`M4.5`、`M5`、`M3.5-BACKLOG`、`UI-SPEC`、`M4-THREAT-MODEL` |
| 代码 | `apps/web/src` 全部 30 个 TS/TSX、`styles.css`、`package.json`、`e2e/m1-ui.spec.ts`、`playwright.config.ts`、`vite.config.ts` |
| 代码 | `services/domain/pom.xml`、`src/main/java` 全部 171 个 Java、`resources/db/migration/V1–V10`、`application.yml`、`src/test` 39 个 Java |
| 集成 | `integrations/dsh/mammoth-domain-mcp/` 全部 5 个文件 |
| DSH 安装版官方文档 | `app.asar/.../dsh-agent-preset/skills/dsh-doc/references/{practices,ui-plugin,locate-docs}.md`、`templates/decoration/{package.json,client.js}`、DSH 安装包 `package.json`（只读，未读取 `~/.dsh` 凭据/业务数据） |
| 命令 | `git log/status/ls-files`、PowerShell 统计（文件数/LOC/端点/表/@Test）、`mvn -B -ntp -f services/domain/pom.xml test`（任务允许的可选复跑）、读取既有 `surefire-reports` |

### 1.2 规模基线（可复现）

| 指标 | 数值 | 证据/方法 |
|---|---|---|
| Git HEAD / 分支 | `fcc851c` / `main`，与 `origin/main` 同步（ahead/behind 0/0） | `git status --porcelain=v2 --branch` |
| 跟踪文件 / Markdown | 442 / 117（其中 `docs/**.md` 95、`decisions/*.md` 12） | `git ls-files` |
| 后端主代码 | **171 个 .java，16,800 LOC** | `Get-ChildItem src/main -Include *.java` + 行数求和 |
| 后端测试 | **39 个 .java（38 个测试类 + 1 个 fixture），5,203 LOC，124 tests** | `surefire-reports`（审计开始时 38 个 XML：124/0/0）；`@Test` 精确计数需排除 `@TestConfiguration/@TestInstance`，以 surefire 为准 |
| 前端代码 | **30 个非测试 TS/TSX，6,599 LOC**；19 个 page 共 4,617 行；`styles.css` 693 行 | PowerShell 行数求和 |
| 前端测试 | 25 个 vitest 用例（format 12 + permissions 10 + pagination 3，220 LOC）；5 个 Playwright E2E（单文件 528 行） | `src/lib/*.test.ts`、`apps/web/e2e/m1-ui.spec.ts` |
| REST 端点 | **20 个 Controller，108 个方法级映射 = 107 个 `/api` 端点 + 1 个 SPA fallback** | `@(Get|Post|Put|Patch|Delete)Mapping` 计数 + `@RequestMapping` 前缀核对 |
| 数据模型 | **V1–V10 共 10 个迁移；39 张表（含 `session`）；35 张导出表** | 10 个迁移的 `CREATE TABLE` 计数 + `MigrationTest.EXPECTED_TABLES`（39）+ `AdminTables.TABLES`（35） |
| 持久层 | 37 个 JPA Repository | Spring 启动日志 `Found 37 JPA repository interfaces` |
| 演示/演练脚本 | 8 个（`demo-m1..m4`、`m3-acceptance-drill`、`admin-drill`、截图/锁清理） | `scripts/` |
| 迁移/方言 | Flyway **13.12.1**、SQLite JDBC **3.54.1.0**、Hibernate community SQLiteDialect；**无 `db/migration-pg`** | `pom.xml:20-27,50-58`、`application.yml:11` |

> 与文档的差异：`docs/05` §6A 写"schema v10（38 表，35 张导出表）"。按迁移与 `MigrationTest` 实测为 **39 张 CREATE TABLE**（含 `session`）；35 张导出表与文档一致。差异=1，属文档口径未含 `session` 或计数遗漏，不影响结论。`[事实]`

### 1.3 测试复跑的诚实记录

- `[事实]` 审计开始前 `services/domain/target/surefire-reports` 的既有汇总（38 个 XML，最后写入 2026-11-13 15:54）为 **124 tests / 0 failures / 0 errors / 0 skipped**。
- `[事实]` 本审计在 DSH 沙箱中执行任务允许的 `mvn -B -ntp -f services/domain/pom.xml test`：**Tests run: 124, Failures: 0, Errors: 2**，`BUILD FAILURE`。两个错误都在 `NotificationChannelTest`，根因是：
  `Could not initialize inline Byte Buddy mock maker ... Could not self-attach to current VM using external process`（`surefire-reports/com.mammoth.domain.notification.NotificationChannelTest.txt`）。
  即 **DSH 沙箱禁止 Mockito 需要的自附加外部进程，不是代码缺陷**。`[事实]`
- `[待验证]` 在普通终端/CI 复跑应回到 124/0/0（有历史证据，但本审计无法在沙箱内证明）。
- 前端 vitest（25）与 Playwright E2E（5）**未复跑**（E2E 需要空数据根 + 后端 + Chromium）；本报告采用"用例计数 + 文档记录 5 passed / 25 tests"并明确标注。`[事实]`
- 结论：**测试资产可用，但含 Mockito 的 2 个通知渠道用例在当前沙箱下不能作为本地门禁**；CI（`.github/workflows/ci.yml` 的 ubuntu runner）与普通终端不受此限。

---

## 2. 四层事实分离

> 本节是整份报告的地基。后续 A–E 的回答都只引用这里的已核事实或明确标注的推断。

### 2.1 产品设计目标（文档成文）

| 目标 | 文档出处 | 关键原文/要点 |
|---|---|---|
| 领域化 AI 工作平台，**AI 工作入口** | `docs/01-PRODUCT-CHARTER.md:9-11` | "成为…AI 工作入口，使内部人员能够基于可靠上下文更快地理解项目、澄清工作、制定业务与技术方案…" |
| 不是聊天、不是自动写代码 | `docs/00-MASTER-GUIDE.md:11` | "核心不是'聊天'，也不是'自动写代码'，而是…" |
| 不可妥协原则 | `docs/00-MASTER-GUIDE.md:15-22` | Project First / Context Before Action / Evidence Based / Human Governed / Knowledge Accumulation / Business Closure / Runtime Independence / Scope Integrity |
| 人机职责 | `docs/02-PRODUCT-BOUNDARIES.md:32-34` | Agent 可归纳/检索/比较/给候选/拆分/生成/分析；**人**决定客户意图、范围、业务规则、审批、生产操作、最终交付 |
| 分层里明确有 **Agent 会话入口** | `docs/05-ARCHITECTURE-BASELINE.md:13` | "Desktop / UI：项目、客户、产品、知识、工作项和 **Agent 会话入口**。" |
| 第一版成功路径是业务闭环 | `docs/01-PRODUCT-CHARTER.md:38-53` | 建客户/产品/项目→导入→逆向知识→检索问答→需求→方案→交接→回收→交付→知识；第一版可手动交接外部 Agent |
| 领域模型有 **AgentRun** | `docs/03-DOMAIN-MODEL.md:26,39` | "AgentRun｜Agent 一次有边界的工作执行｜记录输入引用、产物、工具及状态"；必须记录基线/知识版本/输入引用 |
| 可观测性要求 Agent Run 记录 | `docs/05-ARCHITECTURE-BASELINE.md:102-104` | 发起人、项目、工作项、运行时/模型、提示模板、基线、知识版本、工具调用、产物、审批… |
| 集成形态曾明确列为待决 | `docs/00-MASTER-GUIDE.md:123-136`（尤其 127） | "Mammoth 与 DSH 的集成形态：纯 DSH Bundle、DSH 扩展 + 独立 Domain API，还是更独立的桌面应用"。**2026-11-13 的 00 更新只说 M1–M4 完成，未新增"桌面壳/Agent-first"决策** |

**外部输入（负责人本轮研究前提，非仓库成文目标）**：`[事实-外部输入]` 本轮任务说明把产品愿景表述为"以项目为核心、以 Agent 为主要工作入口、形态接近 Codex Desktop / DSH Desktop 的桌面工作平台"。**仓库中没有任何 ADR、产品规格或路线图把"Desktop 优先/Agent 为主要入口"写成已批准决策**（`docs/13-OPEN-DECISIONS.md` 也未登记该决策）。`[事实]`

### 2.2 已批准架构决策（ADR 状态与约束）

| ADR | 状态 | 对本问题的关键约束 |
|---|---|---|
| 0001 DSH 集成 | **Accepted v1.0**（2026-11-12） | 采纳**方案 B**：out-of-tree Bundle/Plugin + 独立 Domain API/存储；**不 Fork**（A 不批准）；**C（独立桌面应用，DSH 仅外部 Runtime）作为"扩展点不足"时的保留路径**；业务权威状态不得存 DSH storage；Domain API↔DSH 推荐边界 = MCP（`ADR-0001:61-85`） |
| 0002 技术栈/UI | **Accepted v1.0**（2026-11-12） | **方案 B + B1**：Spring Boot 4.1.2 + Java 17 + Maven；**独立 SPA（Vite+React+TS）由 Spring Boot 托管，浏览器访问；M1 不嵌入 DSH Client slot**；数据根默认 OS 应用数据目录（`ADR-0002:31-46`） |
| 0003 DB/迁移 | Accepted v1.0 | SQLite(WAL)+Flyway+JPA；PG 为团队阶段目标；两套方言目录计划（当前仅 SQLite） |
| 0004 Artifact | Accepted v1.0 | 本地内容寻址 `artifacts/<sha256>` + 关系库元数据；`ArtifactStore` 可替换 |
| 0005 检索/RAG | Accepted v1.0 | M1 **全文优先**（FTS5 trigram+BM25），向量/混合延后；`SearchAdapter` 抽象 |
| 0006 身份/授权 | **Accepted v1.1**（2026-11-13 增补） | BCrypt(12)、服务端不透明会话（DB 存 SHA-256、8h TTL、可撤销）、Cookie+CSRF、默认拒绝 + 项目 RBAC；`org_admin` 组织角色与项目角色分离；用户生命周期；**Agent 账号禁 Web 登录** |
| 0007 Git 基线 | Accepted v1.0（2026-11-13） | 只读本地 Git + 人工不可变基线；不 clone/fetch/push；代码内容不进知识库 |
| 0008 工作区 | **Accepted v1.0 + M4 增补**（2026-11-13） | 单机本地单写者 + 导出/导入包；拒绝网络盘/云同步根；**MCP 入口只读连接、不跑迁移**；新表权威/运营分类；MCP 本地审计 journal + writer 入库 |
| 0009 文档解析 | **OPEN**（无 ADR 文件） | PDF/Office/图片/音频解析未定，M4 不解析 |
| 0010 需求上下文基线 | Accepted v1.0 | 需求级轻量快照 `requirement_snapshot`，方案版本绑定快照 |
| 0011 外部集成 | **Accepted v1.0（M4 文件导入范围）** | 只读优先：文件导入 → 只读 API → 双向（未）；外部记录不是权威；凭据只走环境/OS |
| 0012 团队服务器/PG | **Proposed**（S6 文档化延期） | 单节点+反向代理 TLS+SQLite；**PG 多实例=M5** |
| 0013 通知/外发 | **Accepted v1.0（M4 范围）** | 站内默认启用；邮件/企微完整实现但**默认关闭**；内部通知边界；外发走 outbox/server_job |

### 2.3 当前实际代码实现（as-built）

**逻辑架构（代码事实）**

```text
[浏览器] ──HTTP/Cookie+CSRF──> [Spring Boot Domain Service :8080]
                                  ├─ web  (20 Controller / 107 个 /api 端点 / SPA 静态托管)
                                  ├─ security (会话 Filter / CSRF / 登录限速 / BCrypt / org_admin)
                                  ├─ service (AuthorizationService / UserService / ProjectService / CatalogService / OrganizationService / AuditService)
                                  ├─ 领域包: persistence, artifact, knowledge, workitem, solution, delivery, execution, baseline, git, external, notification, mcp, admin, config, support
                                  ├─ SQLite(WAL) <数据根>/db/mammoth.db + Flyway V1–V10
                                  ├─ 本地内容寻址 <数据根>/artifacts/<sha256>
                                  └─ 只读 FTS5 检索（knowledge_chunk_fts）
[DSH Desktop] ──stdio JSON-RPC(MCP 2024-11-05)──> [java -jar ... --mcp 只读进程]
                                  ├─ 3 个只读工具（search_project_knowledge / get_knowledge_document / get_requirement_context）
                                  ├─ 身份 = 环境变量 MAMMOTH_MCP_AGENT_USER_ID（项目成员）
                                  └─ 审计 = <数据根>/audit/agent-audit.jsonl（writer 定时入库 agent_call + audit_log）
[同一 jar 另两种入口]
  --admin-export/import/backup/restore/clear-lock（headless CLI，ADR-0008）
  默认 main（writer）：取实例锁、执行迁移、启动 HTTP
```

**代码事实要点**

| 事实 | 证据 |
|---|---|
| SPA 为独立应用：`index.html` + `#root` + `react-router-dom`；无 `dsh.client`、无 `__ModuleLoader__`、无 `ctx.slots`、无 `--dsw-*` 主题 token | `apps/web/index.html`、`apps/web/package.json:13-26`、对 `apps/web` 全量 grep 无命中 |
| SPA 由后端同源托管（静态资源 + 深链接回退） | `services/domain/src/main/java/com/mammoth/domain/web/SpaWebConfig.java`、`SpaRouteController.java` |
| 107 个 `/api` 端点：项目/资料/知识/需求/方案/基线/交付/导入/通知/用户/审计/健康/设置/认证 | 20 个 Controller；前缀见 `@RequestMapping`（如 `ProjectController:27`、`KnowledgeController:24`、`WorkItemController:25`、`SolutionController:29`、`DeliveryController:28`、`ExternalImportController:26`、`NotificationController:21`、`UserController:24`、`AuthController:22`、`Admin` 两个 controller 等） |
| 认证/授权：服务端会话 + CSRF + 强制改密白名单；默认拒绝的项目 RBAC + `org_admin` | `security/SecurityConfig.java:31-71`、`security/SessionService.java:22,47-49`、`security/AuthenticationService.java:41`、`service/AuthorizationService.java:24-29,60-68,81-85` |
| MCP 工具精确 3 个、输入 schema、只读语义、身份来源 | `mcp/McpServerMain.java:128-166`（3 个 tool descriptor，required 字段见 142/153/165）；身份 `:70,174`；`application.yml:33-36`；`McpToolService.java:59,77,108` 三个 `@Transactional(readOnly = true)`；仅 `verified` `:81-83`；项目成员授权 `:138-142` |
| MCP 工具描述与实际 provider 实现同文件硬编码；无 MCP SDK（手写 JSON-RPC） | `McpServerMain.java:34`（protocol `2024-11-05`）、`mcp/McpJson.java`、无 MCP Java SDK 依赖（`pom.xml:29-81`） |
| DSH 接入 = **配置型 bundle**（无 Host 代码）：插入 `@deepseek-ai/dsh-mcp-client`，stdio 启动 java `--mcp` | `integrations/dsh/mammoth-domain-mcp/package.json:11-18`（`engines.dsh: >=0.2.2-rc.2 <0.3.0`）、`cordis.patch.yml:11-25`、`README.md:1-9`；S2 spike `:54-59` 已实测 |
| 数据根/实例锁/只读入口/导出导入/备份恢复/锁清理 | `config/DataRootResolver.java`、`DataRootInstanceLock.java`、`DataSourceConfig.java`、`mcp/McpServerMain.java:49-68`、`admin/*`、`docs/dev-setup.md:54-61,163-192` |
| 外部导入：CSV/JSON + Jira 中文表头 + preview 零落库 + apply 幂等 + 人工映射证据 + Jira Data Center REST v2 只读客户端 | `external/ExternalImportParser.java:52-57`、`ImportMappingProfile.java:26-43`、`ExternalImportService.java:71-90,212,325-327`、`JiraClient.java:20-65`、`external/JiraImportService.java` |
| 通知/任务：站内默认启用；邮件/企微实现默认关闭；writer-only `NotificationJobWorker` | `notification/*`、`application.yml:40-58`、`V10__notifications_jobs.sql`、`NotificationWorkflowTest` 等 |
| 交付闭环：handoff Markdown/ZIP、execution、deviation、acceptance matrix、As-built、knowledge proposal | `delivery/*`、`execution/*`、`V5__m3_delivery.sql`、`DeliveryController.java:162-294` |
| Agent 专属种子：账号类型/审计/交接/Hook | 见第 7 节（E） |
| 无：LLM SDK、应用内会话、AgentRun/AgentTask 表、Agent 写工具、工具审批、MCP SDK、PG 驱动/迁移、向量库、文档解析 | `pom.xml` 全量依赖（仅 web/validation/jdbc/jpa/dialect/flyway/sqlite/security-crypto/security/csv/mail/test）；`V1–V10` 无 `agent_run`；`McpServerMain` 仅 3 个只读 tool；全仓库 grep `AgentRun|agent_run|AgentTask|agent_task` 只命中文档与 Hook 命名（无表/实体） |

### 2.4 尚未实现 / 仅文档 / OPEN 的能力（汇总）

| 能力 | 状态 | 证据 |
|---|---|---|
| 应用内 LLM 调用 / 应用内 Agent 会话 / 对话 UI | **未实现**（且被 M2/M4/M5 明确排除） | `M2-PHASE2-PLAN.md:171-175`（§7 不引入 LLM）、`M4-PHASE4-PLAN.md:43-52`（§2.2 明确不做，:49 应用内 LLM/AgentRun）、`M5-PHASE5-PLAN.md:21,54`（M5 仍不做）；`docs/00:138` "应用内 LLM 仍需另立 ADR" |
| AgentRun（执行/输入引用/产物/审批） | **未实现**（仅领域文档定义） | `docs/03-DOMAIN-MODEL.md:26,39`；`docs/05:102-104`；代码无表/实体 |
| Agent 写操作（写需求/方案/知识） | **未实现**（设计上排除） | `McpToolService` 3 个只读；`M2-PHASE2-PLAN.md:175`；`docs/08:89-96` |
| 文档解析（PDF/Office/图片/音频） | **未实现**（ADR-0009 OPEN） | `docs/13-OPEN-DECISIONS.md:15`；`docs/06:75`；`dev-setup.md:200` |
| 向量/混合检索 | **未实现**（ADR-0005 延后） | `ADR-0005`；检索仅 FTS5（`SqliteKnowledgeSearchAdapter.java`） |
| PostgreSQL/多实例/HA/共享 Artifact | **未实现**（M5，Proposed） | `ADR-0012`；`M5-PHASE5-PLAN.md`；无 `db/migration-pg` |
| 多组织/多租户/SSO/OIDC | **未实现** | `ADR-0006`（单组织 org_admin）；`M4-PHASE4-PLAN.md:50` 明确不做 |
| 桌面客户端（Electron/DSH 内嵌应用） | **未实现** | 无 Electron/Tauri/安装器代码；ADR-0001 方案 C 仅为保留路径；ADR-0002 明确浏览器 SPA |
| CSM 适配 / 双向同步 / 真实生产 Jira 全量同步 | **未实现/部分** | `ADR-0011`（M4 文件导入；Jira 只读 API 在 M4.5-1 实现并 smoke；CSM 无 API）；`M4.5-PHASE45-PLAN.md:29-41,234-268` |
| 真实 SMTP 启用 / 企微正式事件运营 | **部分**（代码完整、默认关闭；M4.5 仅企微测试群 1 条） | `ADR-0013`；`docs/specs/M4.5-PHASE45-PLAN.md` §10.2（企微测试发送 1 条；SMTP/CSM 未做）；`docs/evidence/M4.5-VERIFICATION.md` §4（编码显示问题不影响事实读取，本报告以计划实施记录为准） |
| 远程 Agent 网关/服务令牌 | **未实现**（M4.5 可选） | `M4.5-PHASE45-PLAN.md:107-120` |
| 客户/产品档案页面、项目↔产品关联 | **未实现/仅表结构** | `CatalogController.java:48-67` 仅 create/list；`project_product` 表存在但 Java 无任何引用（仅 `AdminTables:17`、`V1`、`MigrationTest:26`）；M4.5 验证记录亦写"项目→产品外键未新增（当前模型限制）" |
| 跨项目需求池视图 | **未实现**（仅项目内列表） | `RequirementsPage.tsx` 使用 `useProject()`；`WorkItemController` 前缀为项目级；无全局列表端点 |
| 缺陷/问题/优化/技术债/交付事项工作项类型 | **未实现**（仅 `requirement`） | `docs/03:43`、`M2-PHASE2-PLAN.md:13`、`WorkItemStatus`/`WorkItemService` 仅 requirement 流 |
| 需求评论/讨论、通知中心之外的协作 | **未实现** | `M2-PHASE2-PLAN.md:257-261`、`UI-SPEC:18` |
| 后端分页（前端仅客户端分页） | **未实现** | `UI-SPEC:198`、`dev-setup.md:200`、`lib/pagination.ts` |
| 审计管理 UI（审计 API 有） | **部分** | `AuditController` 2 个 GET；`App.tsx` 无 audit 路由；无管理页 |
| BG-02 基线部署版本/DDL 版本 | **未实现**（M5 顺延） | `M3.5-BACKLOG.md:21-31` |
| 导出包加密、增量导出、账号批量重置 | **未实现** | `ADR-0008` 实施记录、`M3.5-BACKLOG.md:56-62` |

---

## 3. 分析问题 A：为什么长成"独立 SPA + Domain Service + 只读 MCP"

### 3.1 决策链（按时间/证据顺序）

1. **起点是产品假设，不是形态定案**。`docs/04-MVP-STRATEGY.md:9` 的 Phase 0 明确要"用真实证据确认 DSH 扩展边界、桌面体验、数据服务和存储的可行性"，Spike 输出必须是 ADR。`docs/05-ARCHITECTURE-BASELINE.md:31` 原文：
   > "优先验证：Mammoth Domain Core/API + 独立数据存储 + **DSH Bundle/Plugin UI/Agent Tools**。"
   注意措辞是"优先验证"，而且 `docs/05` 标题本身写的是"提案，待 Spike/ADR 验证"（`:1`）。`[事实]`
2. **S1（DSH 扩展 Spike）给出关键约束**（`docs/spikes/S1-DSH-EXTENSION-SPIKE.md`）：
   - F1 外部 bundle 可安装/HMR 激活；工具可注册并真实调用；
   - **F2（最重要）外部 bundle 不能直接 `import '@deepseek-ai/*'`**，必须自带精确版本依赖并在自身目录安装（`:72-73`，S1b `:196-241` 印证）；
   - F5 **UI slot 只注册无报错，但未做浏览器视觉验证**（`:75`）；`§6` 未验证清单把"UI slot 实际渲染效果""跨进程重启""权限/审批策略"列为未验证（`:152-159`）；
   - Host 存储缝 `ctx.storageDomain` 可用，但业务权威数据不能放 DSH storage。
3. **ADR-0001 选定 B**：out-of-tree Bundle/Plugin + 独立 Domain API；不 Fork（A 不批准）；C（独立桌面应用、DSH 仅外部 Runtime）保留为"公开扩展点不足"时的路径；并把 **MCP** 定为 Domain API↔DSH 的推荐边界（`ADR-0001:61-85`，尤其 65、84）。
4. **S2 实测 MCP 可行**（`docs/spikes/S2-DOMAIN-API-MCP-BOUNDARY.md`）：Java 17 stdio MCP server + **配置型 bundle（零 Host 代码）**，DSH 会话真实调用成功；结论明确"插件层零 DSH 依赖"（`:54-59`）。这条路线绕开了 F2，成本极低。
5. **ADR-0002 在 MCP 边界已可行的前提下选技术栈**：Spring Boot 4.1.2 + Java 17 + Maven；UI 采用 **B1 独立 SPA**（Vite+React+TS，由后端托管），**M1 不嵌入 DSH Client slot**。理由原文（`ADR-0002:39-46`）：
   - 团队 Java 主力，Spring 生态覆盖权限/迁移/测试；
   - **UI 独立 SPA 保持 Runtime Independence**，避免 DSH client 插件构建与版本耦合（直接引用 S1 的 F2 类问题）；
   - 可独立做浏览器端测试与验收（docs/09 要求真实 UI 验收）；
   - MCP 单独入口让 Web 容器与 Agent 通道解耦。
   S3 技术栈 spike 的同结论见 `docs/spikes/S3-TECH-STACK-SPIKE.md:48-56`。
6. **M1 计划与 UI-SPEC 把"独立 SPA"固定为正式 UI 基座**：`M1-PHASE1-PLAN.md:38-53` 架构图把浏览器 SPA 与 DSH desktop profile 画成两条独立路径；`UI-SPEC.md:5` 写"当前 SPA 作为**正式产品 UI 的基座**演进（路线 A）"；`:20` 把"把 UI 嵌入 DSH 客户端 slot"列为**非目标**，并注明"ADR-0002 已定独立 SPA；若未来改变需新 ADR"。
7. **之后所有里程碑都在"补业务闭环"，Agent 侧只加只读能力**：M2 明确不引入应用内 LLM（`M2-PHASE2-PLAN.md:171-175`）；M4 明确"应用内 LLM/AgentRun 另立 ADR"（`M4-PHASE4-PLAN.md:49`）；M5 仍不做（`M5-PHASE5-PLAN.md:21,54`）。Agent 侧只扩展了 MCP 只读工具（M2 加 `get_requirement_context`）、Agent 账号、审计、交接包与 Hook。

### 3.2 `docs/05` 原建议 vs 实现结果

| `docs/05` §4 建议（`docs/05:31`） | 实现结果 | 判定 |
|---|---|---|
| Mammoth Domain Core/API | ✅ Spring Boot Domain Service，107 REST 端点，独立进程 | 已实现 |
| 独立数据存储 | ✅ SQLite(WAL) 数据根、Artifact 内容寻址、Flyway V1–V10 | 已实现 |
| DSH Bundle/Plugin **UI** | ❌ 未实现。独立 SPA 由浏览器访问；DSH 侧只有**配置型 MCP bundle**（无 Host 代码、无 UI） | 被 ADR-0002 显式替换 |
| DSH Bundle/Plugin **Agent Tools** | ✅ 3 个只读 MCP 工具，已在真实 DSH 会话验证 | 已实现（只读） |

**结论**：原建议的 4 项中，3 项完整落地，1 项（Plugin UI）被 ADR-0002 的 B1 决策替换。`[事实]`
**为什么替换成立**：M1 时 UI slot 只有"注册无报错"级别证据（S1 F5/未验证清单），DSH 处于 rc.2 预发布、破坏性变更频繁、外部 bundle 依赖强耦合；而"Domain API + MCP"已被 S2 证明可行且零耦合。于是团队选择了**可独立测试、可独立演进、不绑定 Runtime** 的浏览器 SPA。`[推断，依据 S1 §6、ADR-0001 §6.3、ADR-0002:43-45]`

### 3.3 一个被忽略的替代解释

- 不能简单归因于"DSH UI 不可行"。S1 从未证明 UI slot 不可行，只是**未验证渲染**（`S1:152-159`）；ADR-0002 的 B1 选择是**风险/成本权衡**，不是技术否决。`[事实+推断]`
- 因此"独立 SPA"是**当时窗口下的合理近似最优**，但它把产品形态锁定为"浏览器管理后台"，而"桌面宿主 + Agent 主入口"这一产品形态**从未被单独决策**。`[推断]`

---

## 4. 分析问题 B：产品交互形态是否发生偏移

### 4.1 目标 vs 事实对照

| 维度 | 产品设计目标（成文/负责人口径） | 当前代码事实 | 偏移 |
|---|---|---|---|
| 主要工作入口 | "AI 工作入口"（`docs/01:11`）；UI 层应含"Agent 会话入口"（`docs/05:13`） | 顶栏 = 品牌 + 通知 + 用户 + 退出（`AppShell.tsx:36-57`）；全局导航仅 `/projects`、`/notifications`、`/settings`；无 Agent/会话入口 | **是** |
| 客户端形态 | 负责人本轮前提：Desktop 优先，接近 Codex Desktop / DSH Desktop；`docs/04:9` 要验证"桌面体验"；`docs/05:13` 称 Desktop/UI 层 | 浏览器 SPA（`index.html` + react-router），由 Spring Boot 托管；无 Electron/Tauri/桌面打包；本地服务需手动启动或 Docker | **是** |
| 工作空间结构 | "项目空间围绕 Agent 工作" | 项目空间围绕**业务对象**：资料、知识、需求与方案、外部导入、代码基线、知识检索、成员（`ProjectLayout.tsx:19-27`） | **是** |
| Agent 可参与方式 | Agent 可归纳/检索/比较/给候选/拆分/生成/分析（`docs/02:32`） | Agent 仅 3 个只读 MCP 工具（检索/读文档/读需求上下文），且必须由外部 DSH 调用 | **部分/是** |
| LLM 是否应用内 | "Agent 辅助真实工作"（`docs/01`） | 无任何 LLM SDK/配置/HTTP 客户端；无聊天/流式 UI；M2/M4/M5 明确排除 | **是（被显式排除）** |
| Agent 身份与审计 | Agent 参与需审计（`docs/08:44`） | 有 `account_type=agent`、MCP 审计入库、`agent_call`；但身份来自环境变量、无服务令牌/审批 | **部分** |

### 4.2 偏移证据清单（代码级）

1. **路由树无 Agent/会话入口**：`App.tsx:44-71` 共 23 个 `<Route>`；登录/设置/项目/资料/知识/检索/需求/方案/交付/导入/基线/成员/错误页；**没有 chat/conversation/agent 路由**。`[事实]`
2. **页面类型全部是列表-详情-表单**：19 个 page（4,617 行），典型：
   - `ProjectsPage.tsx:87-167` 内嵌"新建客户/产品/项目"三个表单；
   - `RequirementsPage.tsx:13-23,92-159` 状态筛选 + 列表 + "新建需求"表单；
   - `RequirementDetailPage.tsx`（544 行）证据批次/11 维评估/方案版本；
   - `DeliveryPage.tsx`（666 行）交接包/实施/偏差/矩阵/As-built/知识提议；
   - `ImportsPage.tsx`（506 行）、`SettingsPage.tsx`（423 行）、`SolutionPage.tsx`（493 行）。
   这些是典型业务管理后台的信息架构，不是 Agent 工作台。`[事实]`
3. **应用内无 LLM**：
   - 后端依赖清单无任何模型/推理 SDK（`services/domain/pom.xml:29-81`）；
   - 前端依赖仅 react/react-dom/react-router-dom + 构建测试工具（`apps/web/package.json:13-26`）；
   - 全 `apps/web/src` grep `聊天|对话|模型|LLM|流式|prompt` 只命中"证据类型=沟通"等业务词，无聊天实现；
   - 计划文档明确排除（`M2-PHASE2-PLAN.md:171-175`、`M4-PHASE4-PLAN.md:49`、`M5-PHASE5-PLAN.md:21,54`）。
   `[事实]`
4. **Agent 接入完全在 SPA 之外**：`integrations/dsh/mammoth-domain-mcp/` 是给**已安装的 DSH** 用的配置型 bundle；需要手工替换 4 个占位符（java 路径、jar、数据根、Agent 用户 id），且 MCP 进程与 Web 共用同一数据根（`cordis.patch.yml:1-25`、`README.md:18-22`、`docs/dsh-mcp-integration.md:30-41`）。**SPA 用户没有"在这里问 Agent"的入口**。`[事实]`
5. **Agent 在 SPA 中唯一可见的痕迹是"创建 Agent 账号"**（`SettingsPage.tsx:246,264,334`：Agent/人类类型、不可登录、创建 Agent 账号选项），属于治理动作，不是工作入口。`[事实]`
6. **MCP 只读且不在 SPA 内**：`McpServerMain.java` 的 `tools/call` 只分发 `search_project_knowledge` / `get_knowledge_document` / `get_requirement_context`（`:182-263`），无写操作；MCP 入口强制只读（`:49-56,64-68`）。`[事实]`
7. **UI 规格本身没有 Agent UI**：`UI-SPEC.md` 的 P01–P10 页面清单（`:53-68`）无 Agent/会话页；非目标明确列"通知中心/需求/方案界面/嵌入 DSH slot"等（`:17-21`），后两者后来被 M2/M4 补上，但"Agent 会话"始终不在清单。`[事实]`

### 4.3 偏移的性质与时间线

- **时间线**：M0（11-12）定 DSH 集成=方案 B；M1（11-12）定独立 SPA 并作为"正式 UI 基座"；M2（11-12）明确无应用内 LLM；M3（11-13）补交付闭环；M4（11-13）补组织/集成/通知/Agent 只读交付；M4.5 补 Jira 只读与 Hook。**每一步都有文档/ADR 依据，没有代码层面的"偷偷跑偏"**。`[事实]`
- **真正的偏移是"产品形态决策缺位"**：`docs/00:127` 曾把"集成形态：纯 Bundle / Bundle+独立 API / 更独立桌面应用"列为待决；ADR-0001 只决定了**集成方式**（B）和**不 Fork**，ADR-0002 只决定了**技术栈与 UI 实现方式**（B1），**没有任何 ADR 决定最终产品是浏览器后台、桌面壳还是 DSH 内嵌工作台**。于是"最省事可交付"的浏览器后台在 4 个里程碑中自然固化为事实产品形态。`[推断，依据 ADR-0001/0002 范围与 docs/00 待决清单]`
- **影响**：现在要做"产品形态与客户端架构重新评估"，**不能从代码里推出"产品就是管理后台"**，只能从代码里推出"当前实现是管理后台"；产品形态属于**尚未被 ADR 覆盖的空白区**，应作为新决策立项。`[推断]`

---

## 5. 分析问题 C：已实现能力清单 vs 产品边界要求

> 判定口径：**完整**＝API/UI/测试/权限端到端可用且在本报告核对范围内闭环；**部分**＝有可用实现但缺关键边界/页面/类型/阶段；**未实现**＝无代码或仅表结构/文档。

| # | 能力域 | 状态 | 证据 | 主要缺口 |
|---|---|---|---|---|
| 1 | 组织（Organization） | **部分** | `V1` `organization` 表；`OrganizationService`；`org_admin`（V7）；`docs/03:9` | 单组织固定；无多组织/多租户；无组织设置页（只有成员管理） |
| 2 | 用户/身份/会话 | **完整** | `app_user`/`session`/`role_assignment`；BCrypt(12)、服务端会话 SHA-256、8h TTL、撤销、登录 5 次限速（`SessionService.java:22,47-49`、`LoginRateLimiter.java:25-28`、`AuthenticationService.java:41`）；停用/启用/管理员重置/强制改密（V7、`UserController.java:64-113`） | 无邮件找回；无 SSO/OIDC；Agent 账号不可登录（设计如此） |
| 3 | 权限（项目 RBAC + 组织角色） | **完整** | `AuthorizationService.java:24-29,60-68,81-85`；`SecurityConfig.java:31-71`；`PermissionIsolationTest`、`AuditImmutabilityTest`；UI `lib/permissions.ts` 与服务端镜像 | 无字段级/文档级权限；org_admin 不自动获得项目写权限（设计） |
| 4 | 客户（Customer） | **部分** | `V1 customer`；`CatalogController.java:48-58` 仅 POST/GET；`ProjectsPage.tsx:87-109` 仅创建表单 | **无客户档案页面**；无详情/编辑/删除/附件；无客户级知识 scope 页面 |
| 5 | 产品（Product） | **部分** | `V1 product`；`CatalogController.java:60-67` 仅 POST/GET；`ProjectsPage.tsx:111-129` 仅创建表单 | **无产品档案页面**；`project_product` 仅建表、无 Java 引用/API/UI（项目↔产品关联未落地）；无产品版本/标准知识视图 |
| 6 | 项目（Project） | **完整**（对 v1） | `V1 project`/`project_member`；`ProjectController.java:66-113`；项目切换器/侧栏 `ProjectLayout.tsx:80-117`；`ProjectIntegrationTest` | 无项目归档/状态流转/设置页；无项目级"产品版本基线"（BG-02） |
| 7 | 项目资料（Artifact） | **完整** | `artifact` 表 + `LocalArtifactStore`（内容寻址、SHA-256、只读、去重）；`ArtifactController.java:53-83`；`ArtifactsPage.tsx` | 无 GB 级流式、无 PDF/Office 解析、无资料分类/版本 UI |
| 8 | 知识文档生命周期 | **完整** | `knowledge_document`（draft/needs_review/verified/superseded/deprecated）、四眼、来源引用；`KnowledgeController.java:71-165`；`KnowledgeWorkflowTest`、`KnowledgeDetailPage.tsx` | 需人工创建知识；无自动逆向/知识抽取；无富文本 |
| 9 | 项目知识检索（全文） | **完整**（全文范围） | `V2` FTS5 trigram + 查询三元组 + BM25、项目 scope、仅 verified；`SqliteKnowledgeSearchAdapter.java`；`SearchPage.tsx`；ADR-0005 | 无向量/混合/语义；无代码符号/DDL 检索；无大规模性能验证 |
| 10 | 需求池 / WorkItem | **部分** | `V4 work_item`；`WorkItemController.java:128-206`；`RequirementsPage.tsx`（项目内列表 + 状态筛选 + 客户端分页） | 仅 `requirement` 类型；**无跨项目需求池视图**；无评论/讨论/关注；状态止于 `approved`（下游实施通过 ExecutionRecord 承接） |
| 11 | 需求证据 / 就绪 | **完整** | `requirement_evidence`（追加写、批次）、`requirement_assessment`（11 维）、`requirement_snapshot`；`ReadinessService`、`RequirementDetailPage.tsx`；`RequirementWorkflowTest` | 证据仅文本/Artifact 引用；无自动摘录（ADR-0009）；无冲突自动检测（人工评估） |
| 12 | 方案（SolutionModel/双视图/任务/验收/四眼） | **完整** | `V4 solution_*`；`SolutionService`/`SolutionRenderer`/`SolutionConsistency`；`SolutionController.java:137-275`；`SolutionPage.tsx`；`SolutionWorkflowTest` | 无模板库；无多方案对比；无导出 Word/PDF |
| 13 | 代码基线 / Git | **完整**（只读范围） | `V5 project_repository/baseline*`；`LocalGitAdapter`（白名单命令/超时/输出上限/中文路径/精确 safe.directory）；ADR-0007；`BaselinesPage.tsx` | 仅文件级变更映射；无符号/DDL 映射；不 clone/fetch；子模块不递归 |
| 14 | 交付/实施回收 | **完整**（M3 范围） | `execution_record/change/task/deviation`、`delivery_snapshot`、`knowledge_update_proposal`；`DeliveryController.java:162-294`；`DeliveryPage.tsx`；`DeliveryWorkflowTest` | 交接包仅 Markdown/ZIP；无实施记录与 CI/Jira 自动关联；BG-02 缺部署/schema 版本 |
| 15 | 通知 | **完整（站内）/部分（真实外发）** | `V10 notification/notification_delivery/server_job`；站内默认、邮件/企微实现默认关闭、重试/死信/限速；`NotificationController`、`NotificationAdminController`、`NotificationJobWorker`、`NotificationsPage.tsx` | 真实 SMTP/企微未正式启用（M4.5 仅企微测试 1 条）；无客户/外部群发送（需新 ADR） |
| 16 | 外部导入 | **完整（文件）/部分（只读 API）** | `V9 external_source/import_batch/external_record`；CSV/JSON、GBK、Jira 中文表头、preview 零落库、apply 幂等、人工映射证据；`ExternalImportController`、`ImportsPage.tsx`；`JiraClient`（Data Center REST v2 只读） | CSM 未实现；无同步游标/V11；无双向同步；外部附件正文不解析 |
| 17 | 审计 | **部分** | `audit_log` 追加写 + V3 触发器禁止 UPDATE/DELETE；20+ 类动作落库；`agent_call` 审计；`AuditController.java:46-62` | **无审计管理 UI**；无跨对象统一检索/导出/归档；MCP journal 轮转缺失 |
| 18 | Agent 参与（总） | **部分（只读旁路）** | 见第 7 节 E | 见下 18a–18f |
| 18a | 应用内 Agent 会话 | **未实现** | `App.tsx` 无路由；无 LLM/会话/流式代码；M2/M4/M5 排除 | 需新 ADR + 新客户端/服务端能力 |
| 18b | Agent 只读工具（MCP） | **完整（3 工具）** | `McpServerMain.java:128-166`；`McpToolService.java`；DSH 实测（S2、M1 验收） | 身份=环境变量、非服务令牌；工具面窄；无资源/prompts 能力 |
| 18c | Agent 写操作 | **未实现** | 只读 3 工具；`M2-PHASE2-PLAN.md:175`；`docs/08:89-96` | 需审批 + 写工具白名单 + 领域服务授权；目前不存在 |
| 18d | Agent 运行审计 | **部分** | `agent_call`（V8）+ local JSONL + writer ingest；`AdminTables` 保留；`AgentAuditIngestService.java:43-111` | 只记录 tool 级事件；无 AgentRun 级输入/输出/审批；无 Agent 会话 |
| 18e | Agent 交接包 | **完整** | `HandoffPackageService.java:25-92`（zip、manifest、逐文件 sha256、秘密扫描、Artifact、审计） | 不加密；无下载授权细分（项目成员） |
| 18f | Agent Hook 通知 | **完整（契约级）** | `AgentHookService.java:14-94`；`POST /api/projects/{projectId}/agent-hooks`；事件 `agent.task.completed/confirmation_required`；站内/企微白名单 | **无 AgentTask 表**；当前只能由 owner/editor 手工调用模拟；无真实 Agent 运行时接入 |
| 19 | 文档解析（PDF/Office） | **未实现** | ADR-0009 OPEN；`docs/06:75`；`dev-setup.md:200` | 全部依赖人工摘录/文本 |
| 20 | 团队/服务端多用户 | **部分** | 同一进程内多用户 + DB 会话 + 项目成员；单节点 SQLite + 实例锁；`ADR-0012` | 无多实例/HA/TLS 交付（S6 仅文档化延期）；PG=M5 |
| 21 | 桌面客户端 | **未实现** | 无 Electron/Tauri/安装器；Dockerfile 为浏览器服务镜像 | 需新 ADR 与客户端工程 |
| 22 | 健康/元信息/Swagger/可观测 | **部分** | `HealthController /api/health /api/meta`；无 Actuator/Swagger；日志为 Spring 默认 | 无指标/追踪；M5 规划可观测性 |

**特别回答四个点名项**：

- **"应用内 Agent 会话"**：**未实现**。无 LLM 调用、无会话存储、无 Chat/流式 UI、无路由；且这是 M2/M4/M5 三份计划连续明确排除的项（`M2:171-175`、`M4:49`、`M5:21,54`）。`[事实]`
- **"Agent 写操作"**：**未实现**。MCP 只有 3 个只读工具；`McpToolService` 全部 `@Transactional(readOnly=true)`；`docs/07:7`/`docs/02:32` 的"Agent 可生成候选"没有对应写路径。`[事实]`
- **"需求池视图"**：**部分实现**（项目内需求列表/筛选/分页已有；**无跨项目需求池**；无缺陷/问题/优化类型）。`[事实]`
- **"客户/产品档案页面"**：**未实现**。只有 `ProjectsPage` 内嵌的"新建客户/产品"表单与两个 create/list API；没有客户/产品详情页、编辑、删除、附件、关联项目/产品视图；`project_product` 表存在但无任何 Java 代码使用。`[事实]`

---

## 6. 分析问题 D：现有代码资产复用评估（三候选路线）

### 6.0 先确认两条硬事实

1. **现有资产与 UI 无关的核心很厚**：Domain Service、SQLite schema/迁移、权限、审计、MCP、Git、Artifact、导入、通知、交付闭环全部在服务端；前端只依赖 HTTP API（`apps/web/src/lib/api.ts`：94 个导出函数、54 个接口/类型、1,369 行）。因此**换客户端形态不要求重写业务核心**。`[事实]`
2. **DSH 官方实践禁止 iframe 嵌入 Host HTML**（本机安装版 rc.2 的官方 dsh-doc 引用）：
   > "Render plugin pages as React components in a slot. **Do not serve an HTML page from the Host and embed it in an iframe**: an iframe document does not receive the host's theme tokens, light/dark switching, or `ctx.locale`."
   > —— `.../skills/dsh-doc/references/practices.md:33`（本机 `D:\Tools\DeepSeek Harness\resources\app.asar\dsh\node_modules\@deepseek-ai\dsh-agent-preset\skills\dsh-doc`）
   另有 `references/ui-plugin.md:13`："Do not replace the app root or append a second application to `document.body`"；模板要求 `dsh.client` 清单 + `window.__ModuleLoader__.load(...)` 客户端模块（`templates/decoration/package.json:13-20`、`client.js:1-22`），并继承 Host 主题 token（`--dsw-alias-*`）与 locale。`[事实]`
   **影响判定**：现有 SPA 是自带 `index.html`/`react-router`/全局 CSS 的独立应用（`apps/web/index.html`、`App.tsx:44-71`、`styles.css` 693 行、`#247bbf` 中性色基线），**不能直接嵌入 DSH Host**；如走"DSH 宿主内页面"，必须把页面重写为 slot React 组件（组件/工具函数可部分复用，页面壳/路由/样式体系不可）。`[事实+推断]`

### 6.1 资产 × 路线复用矩阵

图例：**高**＝基本可直接复用，小改造；**中**＝复用主体但需适配/重写接口层；**低**＝大部分重写或仅作参考。

| 资产 | A：DSH Desktop 为宿主，开发 Mammoth Bundle/Plugin | B：自研 Electron 桌面壳 + 集成 DSH Runtime | C：独立完整 B/S Agent 工作平台 | 理由（共同） |
|---|---|---|---|---|
| **Domain Service**（171 文件/16.8k LOC，107 API） | **高** | **高** | **高** | 与 UI/Runtime 解耦（ADR-0001 目标）；A 只需 MCP 或 HTTP；B 作为本地 sidecar/子进程；C 作为服务端核心。`[事实]` |
| **DB/SQLite/Flyway/39 表** | **高** | **高** | **高**（多实例时按 M5 迁 PG） | 单机本地单写者与桌面形态天然匹配（ADR-0008）；C 若面向团队要补 PG/M5。`[事实+推断]` |
| **检索（FTS5+引用）** | **高**（工具/服务端） | **高** | **中高** | 全文检索服务可直接复用；A 的 UI 需重写搜索页；C 的 Agent 检索还缺语义/向量（ADR-0005 延后）。`[事实]` |
| **权限/会话/审计** | **中** | **高** | **高** | 现有 HTTP 会话 Cookie+CSRF 适合同源浏览器 SPA 与 Electron 自建页；**DSH Host 内插件是另一个 Web 源，跨源 Cookie/CORS/CSRF 与服务身份需要新设计**（且 MCP 身份目前是 env var）`[待验证+事实]`。C 可沿用服务端模式。`[推断]` |
| **MCP（3 只读工具+配置 bundle+审计）** | **高**（现成，ADR 指定的边界） | **中高**（若 DSH 作为 sidecar/独立 runtime 仍可用；若深度嵌入需另设计 IPC） | **中**（保留作为外部 Agent 接口；应用内 Agent 需要更宽工具面与写/审批） | S2 实测可行、零 DSH 依赖；M4.5 网关未实现。`[事实]` |
| **SPA 页面（19 页/6.5k LOC）** | **低-中**：**不能 iframe**；整页需改写为 slot React 组件（自带 router/CSS/顶栏不适用），API 客户端/部分组件/格式化逻辑可复用 | **高**：现有 SPA 可直接作为 Electron BrowserWindow 的 renderer（同源或本地服务），是**最小 UI 改造的桌面化路径** | **高**：现有业务页面可作为"管理台"部分保留；但需新增 Agent 工作区/会话页作为主入口 | iframe 事实见 6.0；Electron/C 不改变 SPA 运行方式。`[事实+推断]` |
| **测试资产（124 后端/25 前端/5 E2E/8 演练脚本）** | **中高**：后端测试全复用；Playwright 只适用于"SPA 仍在浏览器/伴生窗口"；DSH 内嵌页需新 UI 测试方式（`[待验证]`：DSH 官方 verification 文档支持连接页验证） | **高**：后端/前端/E2E/脚本全复用；新增 Electron 壳冒烟 | **高**：同上，另加 Agent UI/服务测试 | 资产本身不依赖客户端形态；DSH 内嵌场景的 UI 测试最大不确定项。`[事实+推断]` |
| **DSH 集成资产（MCP 配置 bundle）** | **高**（已按此设计） | **中高**（DSH 与 Mammoth 的关系从"宿主"变"集成 runtime"，MCP 仍可用；若 DSH 需嵌入 Electron 内运行，官方支持方式 `[待验证]`） | **低**（仅保留作为外部 Agent 接口） | 现 bundle 只有 5 个文件、零 Host 代码（`integrations/...`）。`[事实]` |

### 6.2 三路线定性比较

| 维度 | A：DSH Desktop 为宿主 | B：自研桌面壳 + DSH Runtime | C：独立 B/S Agent 平台 |
|---|---|---|---|
| 与现有资产连续性 | 中（服务端高、UI 低） | **最高**（全栈 + SPA 直接复用） | 中高（业务页面可作管理台） |
| 新增工程 | DSH client bundle（页面/主题/多 slot）+ 跨源认证 + DSH 版本升级同步 | Electron 壳、进程编排、自动更新、凭证/安全、DSH runtime 集成方式 | Agent Runtime（LLM 网关、会话/流式、工具、审批、沙箱）、团队/部署 |
| 与"Desktop 优先/Agent 主入口"契合 | **高**（Agent 与业务同屏） | **高**（自控壳，可把 Agent 设为默认首页） | 中（Web 端 Agent 平台，桌面体验弱） |
| 主要风险 | DSH 预发布破坏性变更（ADR-0001/S1 已证实 20 项 rc 变更）；外部插件依赖强耦合（F2）；UI slot 未验证；DSH 安装/升级体验绑定上游 | 自研壳的跨平台/签名/更新/安全成本；DSH 是否能作为可嵌入 runtime 的官方方式 `[待验证]`；"DSH + 自研壳"双产品叙事 | 最大自研范围；与 DSH 生态/模型能力重复建设；Agent UX 质量风险；多用户/多实例（M5）叠加 |
| 数据/安全边界 | 延续 ADR-0001（业务状态留 Domain API）；但 DSH Host 环境不属于 Mammoth 业务域（`docs/08:46-66`） | 可延续数据根/实例锁/MCP 只读；需新增壳的进程信任边界 | 延续现有 Web 安全模型 |
| 对 DSH 版本耦合 | **高**（插件必须随 DSH 版本升级；S1 F2/S1b 21 包精确版本） | 中高（可锁定 DSH 版本，壳内分发） | 低（仅继续用 DSH 作为开发工具/外部 Agent） |

### 6.3 复用结论（倾向，供 Lead 决策参考，不是本报告的架构决定）

- **如果"Desktop 优先 + Agent 为主要入口"是确定方向**：路线 **B** 的资产复用最优，能在不重写 Domain/DB/权限/MCP/SPA 的前提下把产品升级为桌面工作台；DSH 作为可替换的 Agent Runtime/外部执行器保留（符合 `docs/00:21` Runtime Independence）。代价是自研壳的工程与分发投入，以及"DSH 集成方式"（子进程？SDK？分发包？）必须先验证。`[推断]`
- **如果"Agent 与业务同屏、复用 DSH 原生 UX/模型/插件生态"优先**：路线 **A** 的产品体验最接近 DSH Desktop；但必须接受 **UI 层重写**（现有 19 页/6.5k LOC 基本不能直接进 Host；可复用 api.ts/format/permissions/部分组件）和 **DSH rc.2 版本耦合**（每个页面都必须遵守 slot/theme/locale/客户端构建规则，且 `cordis_inspect_query` 才能动态确认 slot 目录与可用性——本会话无该工具，无法核实可用 page slot 清单，`[待验证]`）。`[推断]`
- **路线 C** 只有在"Mammoth 要做独立 Agent 平台、不依赖 DSH 的 UX/运行时"时才成立；它把当前最大的空白（应用内 LLM/AgentRun/工具审批/会话）全部变成自研范围，是三条里**新增工程量最大、与现有资产增量最不匹配**的一条；现有 SPA 只能作为其中的"管理台"，不能直接充当 Agent 工作区。`[推断]`

### 6.4 关于 iframe 的准确表述与不确定点

- `[事实]` 官方文档原文禁止的是："从 Host 提供 HTML 页面并把它嵌进 iframe"（Host-served HTML + iframe）。它给出的是**主题 token / 明暗 / locale 不一致**与"Dsh 插件 UI 是 Host UI 一部分"的理由；这不是一个运行时"技术上不可能"的硬限制，而是**官方实践/一致性的硬约束**。
- `[事实]` 现有 SPA 不是 Host 提供的 HTML，而是 Spring Boot 在同源下托管的独立应用；以浏览器窗口/Electron renderer 访问不受该条目约束。
- `[待验证]` 1) rc.2 是否存在可承载"完整项目工作台"的 page 级 slot（本会话无 `cordis_inspect_list/query`，无法枚举；官方 skill 说可用 Inspect 查 `Slots.listSubTree`）；2) 若把现有 React 页面改写为 `client.js` 模块，哪些依赖（react-router、CSS、fetch 会话）需要替换；3) DSH 侧客户端插件与 Domain Service 的跨源登录/会话方案；4) 插件 UI 的自动化测试与浏览器验证方式（官方 `references/verification.md` 提到"连接页验证"路径，未在本会话实测）。

---

## 7. 分析问题 E：已存在但未被产品化利用的"Agent 入口"种子

| # | 种子 | 代码/数据位置 | 当前行为 | 产品化缺口 | 激活条件 |
|---|---|---|---|---|---|
| E1 | **专用 Agent 账号** | `app_user.account_type='human'/'agent'`（`V7`）；`AppUser.java:34,48,58,94-107`；`AuthenticationService.java:41`（`isAgent()` 直接 401）；`UserService.createAsAdmin`；`SettingsPage.tsx:334`（"Agent 账号（不可登录）"） | org_admin 可建 Agent 账号，禁止 Web 登录；项目 owner 加为 viewer；MCP 用其 userId | 无 Agent 身份令牌/轮换/权限细分；MCP 身份仍是环境变量而非认证 | 需要"Agent 身份服务"或网关（M4.5-4） |
| E2 | **3 个只读 MCP 工具** | `McpServerMain.java:128-166`；`McpToolService.java:48-143` | `search_project_knowledge`（检索 verified 知识）、`get_knowledge_document`、`get_requirement_context`（证据/11 维/批准方案摘要）；工具名 `mcp__mammoth_domain__*` | 面窄；无资源/prompts；无写工具/审批；无工具级权限配置 UI | 扩展工具面 + 审批（另立 ADR） |
| E3 | **MCP 调用审计** | `AgentAuditJournal.java:18-58`（`<dataRoot>/audit/agent-audit.jsonl`，不含参数/正文）；`AgentAuditIngestService.java:43-111`（writer 定时入库 `agent_call` + `audit_log`）；`V8__agent_call_audit.sql`；`AdminTables.java:24` 含 `agent_call` | 每次 tool call 一条审计；幂等 `event_id`；损坏行跳过；只读进程不写 DB | 无 AgentRun 级语义；无 journal 轮转/保留策略；无审计查询 UI | 需要 AgentRun/会话模型后合并 |
| E4 | **BG-01 交接包（给外部 Coding Agent）** | `HandoffPackageService.java:25-92`；`DeliveryController.java:171-179`；`handoff_package` Artifact 类型；审计 `handoff.export` | 从批准方案生成固定清单 zip（方案/任务/验收/基线/README/SECURITY + manifest sha256 + 秘密扫描），落 Artifact 可下载 | 不加密；无 Agent 侧回执/导入结果回流；无"交接包 → Agent 执行 → ExecutionRecord"自动闭环 | 需要外部 Agent 适配器/回执协议 |
| E5 | **Agent Hook 事件** | `AgentHookService.java:14-94`；`AgentHookController.java:15,34`；`V10` notification；`application.yml:52` 企微事件白名单默认含 `agent.task.completed/confirmation_required` | `POST /api/projects/{p}/agent-hooks`，owner/editor 手工模拟；站内通知收件人（completed→owner/editor/reviewer；confirmation→owner/reviewer）；`link` 仅应用内相对路径；审计 `agent.hook` | **无 AgentTask 持久表**；无真实 Agent 运行时调用方；无确认回写/审批动作 | 需要 AgentTask/AgentRun 模型 + 运行时接入 |
| E6 | **上下文/基线快照（Agent 输入引用基础）** | `V4 requirement_snapshot` + `work_item.context_snapshot_id`；`solution_version` 绑定快照（ADR-0010）；`V5 project_baseline`/`project_baseline_repo`；`knowledge_document.applicable_baseline_id` | 人工就绪确认时生成需求快照；方案提交绑定快照；项目基线不可变；知识过期只提示 | 尚未有 `AgentRun.input_snapshot_id` 一类挂接点；无 Agent 输入引用的统一记录 | 设计 AgentRun 时直接复用（`docs/03:39` 要求） |
| E7 | **通知事件类型命名已为 Agent 预留** | `NotificationService.java:112-125`（`agent.task.completed`/`agent.task.confirmation_required` 创建通知）；`Notification`/`notification_delivery` | 站内默认 + 企微白名单可外发 | 无真实 Agent 运行时产生事件 | 同 E5 |
| E8 | **开发期 Agent 能力（非产品运行时）** | `AGENTS.md`、`docs/agents/*`、`docs/11`；DSH subagent/team/worktree 工具 | 用于开发 Mammoth 本身 | 与产品内 Agent 无关，不能当产品入口 | 不适用（避免混淆两类"Agent"） |
| — | **缺失：AgentRun/AgentTask 表** | 全仓库 grep：除 `docs/03:26` 定义、`docs/05:102-104` 观测要求、M4/M5 计划"另立 ADR"外，**无 `agent_run`/`agent_task` 表、无实体、无 API** | — | 产品化 Agent 的**第一缺口** | 新 ADR + V11 迁移 |
| — | **缺失：应用内会话/流式/模型网关** | `pom.xml` 无 LLM SDK；无任何模型 provider/配置（`application.yml` 只有 Jira/mail/notification） | — | 无法在应用内与 Agent 对话 | 新 ADR + 凭据/计费/安全方案 |
| — | **缺失：Agent 写操作与审批** | `McpToolService` 只读；无 approval/tool 审批表 | — | Agent 只能"建议"，不能"执行" | 需工具风险分级 + 审批 + 领域服务写路径 |

**小结**：现有代码已经具备"Agent 外部只读协作"的完整最小闭环（身份→工具→审计→交付包→回执契约雏形），但**离"Agent 为主要工作入口"还差三层**：(1) 运行时/会话（LLM 网关 + AgentRun/AgentTask + 流式 UI）；(2) 写与审批（工具白名单/风险分级/人工确认）；(3) 客户端形态（桌面壳或 DSH 内嵌工作台）。`[事实+推断]`

---

## 8. 文档/代码漂移与风险清单（供后续修订）

| ID | 类型 | 事实 | 证据 | 影响 | 建议 |
|---|---|---|---|---|---|
| D1 | 文档漂移 | MCP 接入模板 README 写"两个只读工具"，实际代码 3 个（M2 新增 `get_requirement_context`） | `integrations/dsh/mammoth-domain-mcp/README.md:4-8` vs `McpServerMain.java:128-166`（`:226-263` 为第三个工具） | 接入/验收者误判工具面 | 更新 README 为 3 工具 |
| D2 | 文档计数 | `docs/05:82` 写"schema v10（38 表，35 张导出表）"；实测 39 张 CREATE TABLE（含 `session`）、35 张导出 | `MigrationTest.java:24-39`（39 项）、`AdminTables.java:10-45`（35 项）、10 个迁移 `CREATE TABLE` 计数 | 表数口径不一致 | 统一为"39 张表（含 session）/35 张导出"或注明排除项 |
| D3 | 文档/实现 | 领域文档要求项目可关联多个产品（`docs/02:9-11`、`docs/03:31`），但 `project_product` 只有表结构，Java/API/UI 均无使用；M4.5 记录亦承认"项目→产品外键未新增" | `V1__m1_baseline.sql:56`、`AdminTables:17`、`MigrationTest:26`；全仓库 Java grep 无 `project_product`；`docs/evidence/M4.5-VERIFICATION.md:18`（"当前 Project 只关联客户，产品为独立主数据；未新增项目→产品外键"） | 项目↔产品业务关系未落地 | 作为产品边界缺口登记；实现或明确延期 |
| D4 | 文档/实现 | `docs/05:13` 写 UI 层应有"Agent 会话入口"；实际无此页面/路由/会话模型 | `App.tsx:44-71`；`UI-SPEC.md:17-21` 也未列该页面 | 产品形态决策缺位被文档掩盖 | 在新客户端架构 ADR 中明确"Agent 入口"归属 |
| D5 | 决策债 | `docs/00:127` 把"纯 Bundle / Bundle+独立 API / 更独立桌面应用"列为待决；ADR-0001/0002 只定了集成方式与技术栈，**最终产品形态无 ADR** | `docs/00:123-136`；`ADR-0001:61-65`；`ADR-0002:31-36`；`docs/13` 全表无"产品形态/桌面客户端"条目 | 当前实现被默认为产品形态 | 立项新 ADR（产品形态/客户端架构） |
| D6 | 未验证项 | DSH UI slot 实际渲染从未验证 | `S1:75,152-159`；`S1c:243-250`（UI slot 未验证） | 路线 A 的可行性边界未知 | 用 DSH `cordis_inspect_query`/verification 做最小 page slot 验证 |
| D7 | 产品化缺口 | MCP bundle 4 个占位符需手工替换；Windows 下重建 jar 前必须卸载 bundle | `cordis.patch.yml:1-7`、`README.md:18-22`、S2 spike §5 | 无法作为终端用户安装体验 | 安装向导/启动器；参数由壳注入 |
| D8 | 安全/审计 | MCP 身份 = `MAMMOTH_MCP_AGENT_USER_ID` 环境变量（临时机制）；无服务令牌/轮换 | `application.yml:33-36`、`McpServerMain.java:70,174`、`docs/dsh-mcp-integration.md:50-56` | 多用户/远程场景不可用 | M4.5-4 服务令牌/网关；桌面形态可改本地伴生认证 |
| D9 | 测试门禁 | 含 Mockito 的 2 个通知渠道用例在 DSH 沙箱中因 ByteBuddy 自附加被拒而失败 | 本审计 `mvn test`：124 tests、2 errors、BUILD FAILURE；`surefire-reports/...NotificationChannelTest.txt` | 沙箱内不能作为本地门禁 | CI/普通终端跑；或在沙箱中改 mock 策略 |
| D10 | CI 真实性 | CI workflow 已配置但远端从未实际运行（文档多份承认） | `.github/workflows/ci.yml`；`docs/M1-ACCEPTANCE.md:6`、`M2-ACCEPTANCE.md:6`、`M4-ACCEPTANCE.md:43` | "本地通过≠CI 通过" | 推送后核对 Actions |
| D11 | 前端分页 | 资料/知识/成员/需求/方案均客户端分页 | `lib/pagination.ts`、`UI-SPEC.md:198`、`dev-setup.md:200` | 大数据量不可用 | 后端分页（已登记） |
| D12 | 知识/能力范围 | 无文档解析（ADR-0009 OPEN）、无向量检索、无代码符号/DDL 映射 | `docs/13:15`、`ADR-0005`、`ADR-0007` | 与"持续理解项目/知识逆向"的愿景有差距 | 按 backlog 分阶段 |

---

## 9. 未验证项/不确定项（必须在后续研究中补齐）

1. `[待验证]` **DSH 内嵌页面能力边界**：rc.2 有哪些可承载完整工作台的 slot（`Slots.listSubTree` 结果未知，本会话无 `cordis_inspect_list/query`）；UI slot 实际渲染（S1 未验证）；插件页面在真实 Desktop 中的可用性/性能。
2. `[待验证]` **DSH 作为可嵌入 Runtime 的官方方式**：安装包依赖里存在 `@deepseek-ai/dsh-sdk-app`、`dsh-sdk-client`、`dsh-acp-app` 等包，但"Electron 壳内嵌 DSH runtime"的官方支持/许可证/版本策略未核对。
3. `[待验证]` **跨源认证**：DSH Host 内插件访问本地 Domain Service 的 Cookie/CORS/CSRF 可行性；MCP 之外是否需要 HTTP 直连。
4. `[待验证]` **路线 B 的壳工程**：Electron（或 Tauri）与 Java 服务进程编排、签名/自动更新、Windows 安装体验、离线策略。
5. `[待验证]` **路线 A 的 UI 复用度**：把现有 19 页拆成 slot 组件的真实工作量；`api.ts`（1,369 行）能否在 client 模块中直接复用（fetch/Cookie/CSRF 假设）。
6. `[待验证]` **前端测试/E2E 能否覆盖 DSH 内嵌 UI**（官方 `references/verification.md` 的连接页验证路径未实测）。
7. `[待验证]` **"Desktop 优先/Agent 为主要入口"是否已获负责人正式确认**：仓库无 ADR/规格记录，本轮任务说明只是研究前提。
8. `[待验证]` **测试基线**：在普通终端复跑 124 tests 是否 0 failures/errors（历史证据支持，但沙箱本轮 2 errors）。
9. `[待验证]` **M4.5 现状**：Jira 只读 API 已实现并有真实 smoke（M4.5 记录）；本报告未能复跑其真实外部调用（环境/凭据约束）。
10. `[待验证]` **多组织/多租户/SSO** 对权限模型的影响（当前单组织；ADR-0006 只给迁移路径）。

---

## 10. 对"推荐架构"最关键的 5 条事实

1. **现有架构是"服务端领域核心 + 浏览器 SPA + 外部 DSH 只读 MCP"的明确分层；领域/数据层与 UI/Runtime 解耦，这是 ADR-0001/0002 的既定目标，不是历史包袱。**（`ADR-0001:61-85`、`ADR-0002:31-46`、`App.tsx`、`integrations/...`）
2. **Agent 侧只有只读能力：3 个 MCP 工具、专用 Agent 账号、调用审计、交接包、Hook 契约；不存在应用内会话、LLM、AgentRun/AgentTask 表、Agent 写操作与审批。**（`McpServerMain.java:128-166`、`AgentAuditJournal`/`AgentAuditIngestService`、`V8`、`AgentHookService`、`HandoffPackageService`）
3. **产品交互形态已实质偏移为"业务管理后台（列表-详情-表单）"，且这是 M2/M4/M5 连续排除应用内 LLM 的结果；产品形态本身从未被 ADR 决定。**（`App.tsx:44-71`、19 页/4,617 行、`pom.xml` 无 LLM、`M2:171-175`、`M4:49`、`M5:21,54`、`docs/00:127`）
4. **DSH 官方明确禁止 iframe 嵌入 Host HTML，插件页面必须用 Host slot 中的 React 组件与主题/locale；现有 SPA 不能直接进 Host，但在浏览器窗口/自研 Electron 壳下可高复用。**（安装版 rc.2 `dsh-doc/references/practices.md:33`、`ui-plugin.md:13`、`templates/decoration/*`；`apps/web/index.html`、`apps/web/package.json`）
5. **技术复用排序：Domain Service/DB/权限/MCP/后端测试 = 高；SPA 在 Electron/浏览器形态 = 高、在 DSH 内嵌形态 = 低-中；三条路线中"自研桌面壳 + 集成 DSH Runtime"（B）与现有资产最连续，且最符合"Desktop 优先/Agent 主入口"目标；但需先验证 DSH 可嵌入方式与桌面壳工程成本。**（第 6 节矩阵；`[推断]`）

---

## 附录 A：关键证据索引（文件:行）

- DSH 集成决策：`decisions/ADR-0001-DSH-INTEGRATION.md:61-85`（采纳 B、MCP 边界、业务状态不进 DSH storage）
- 技术栈/UI 决策：`decisions/ADR-0002-TECH-STACK.md:29-46`（B1 独立 SPA、不嵌 slot、Runtime Independence）
- 05 原建议：`docs/05-ARCHITECTURE-BASELINE.md:31`；UI 层含 Agent 会话入口：`:13`；可观测性：`:102-104`
- DSH Spike：`docs/spikes/S1-DSH-EXTENSION-SPIKE.md:72-75,152-159`；MCP Spike：`docs/spikes/S2-DOMAIN-API-MCP-BOUNDARY.md:54-59`；技术栈 Spike：`docs/spikes/S3-TECH-STACK-SPIKE.md:48-56`
- 产品愿景/边界：`docs/01-PRODUCT-CHARTER.md:9-11,36-53`；`docs/02-PRODUCT-BOUNDARIES.md:26-34`；`docs/03-DOMAIN-MODEL.md:26,39`；`docs/00-MASTER-GUIDE.md:11,15-22,123-136`
- 排除应用内 LLM：`docs/specs/M2-PHASE2-PLAN.md:171-175`；`docs/specs/M4-PHASE4-PLAN.md:43-52`（`:49`）；`docs/specs/M5-PHASE5-PLAN.md:21,54`
- 前端路由/壳：`apps/web/src/App.tsx:44-71`；`apps/web/src/app/AppShell.tsx:36-57`；`apps/web/src/pages/ProjectLayout.tsx:19-27,80-117`
- 前端依赖：`apps/web/package.json:6-26`；SPA 托管：`services/domain/src/main/java/com/mammoth/domain/web/SpaWebConfig.java`
- 后端坐标/Java 版本/依赖：`services/domain/pom.xml:7-12,20-27,29-81`
- MCP：`services/domain/src/main/java/com/mammoth/domain/mcp/McpServerMain.java:34,49-56,64-72,128-166,182-263,277-283`；`McpToolService.java:28-30,59,77,81-83,108,138-142`；`application.yml:33-36`
- MCP 审计：`mcp/AgentAuditJournal.java:18-58`；`mcp/AgentAuditIngestService.java:43-111`；`V8__agent_call_audit.sql`；`admin/AdminTables.java:24`
- Agent Hook：`notification/AgentHookService.java:14-94`；`web/AgentHookController.java:15,34`
- 交接包：`delivery/HandoffPackageService.java:25-92`；`web/DeliveryController.java:162-294`
- 权限/会话：`security/SecurityConfig.java:31-71`；`security/SessionService.java:22,47-49`；`security/AuthenticationService.java:41`；`security/LoginRateLimiter.java:25-28`；`service/AuthorizationService.java:24-29,60-68,81-85`
- 客户/产品 API：`web/CatalogController.java:48-67`；`apps/web/src/pages/ProjectsPage.tsx:87-167`
- 表/迁移：`resources/db/migration/V1__m1_baseline.sql`（14 表）、`V4`（9）、`V5`（9）、`V8`（1）、`V9`（3）、`V10`（3）；`src/test/java/com/mammoth/domain/migration/MigrationTest.java:24-39`；`admin/AdminTables.java:10-45`
- DSH 官方 UI 规则（本机安装版）：`...\dsh-doc\references\practices.md:33`；`...\references\ui-plugin.md:13`；`...\templates\decoration\package.json:13-20`、`client.js:1-22`
- DSH 版本：`D:\Tools\DeepSeek Harness\resources\app.asar\package.json:3`、`...\dsh\node_modules\@deepseek-ai\dsh\package.json:4`

## 附录 B：本审计执行过的命令与结果（可复现摘要）

| 命令/操作 | 结果 |
|---|---|
| `git status --porcelain=v2 --branch` / `git log -3` | `main` @ `fcc851c`，与 origin 同步，工作树 clean |
| `git ls-files` | 442 个跟踪文件（117 md） |
| PowerShell 行数/文件数统计 | 后端 171/16,800 LOC；测试 39 文件/5,203 LOC；前端 30/6,599 LOC；19 pages/4,617 行；`api.ts` 1,369 行；`styles.css` 693 行 |
| `@(Get|Post|Put|Patch|Delete)Mapping` 计数 | 20 Controller，108 方法级映射（107 `/api` + 1 SPA fallback） |
| 迁移 `CREATE TABLE` 计数 + `MigrationTest` | 10 个迁移；39 表；35 导出表 |
| 既有 `surefire-reports` 汇总 | 38 个 XML：124 tests / 0 failures / 0 errors（审计开始时） |
| `mvn -B -ntp -f services/domain/pom.xml test`（任务允许的可选复跑） | Tests run 124, Failures 0, Errors 2（`NotificationChannelTest`，Mockito/ByteBuddy 自附加被沙箱拒绝），BUILD FAILURE；**环境限制，非代码失败** |
| 前端测试统计（未复跑） | 12+3+10 = 25 个 vitest 用例；5 个 Playwright test |
| 读 DSH 安装版 package.json | `@deepseek-ai/dsh-desktop@0.2.2-rc.2`、`@deepseek-ai/dsh@0.2.2-rc.2`；未读 `~/.dsh` 凭据/业务数据 |

## 附录 C：端点/数据/路由清单（摘要）

- **20 个 Controller**：`AgentHook`(1)、`Artifact`(4)、`Audit`(2)、`Baseline`(6)、`Catalog`(4)、`Delivery`(14)、`ExternalImport`(9)、`Health`(2)、`JiraImport`(2)、`JobAdmin`(2)、`Knowledge`(9)、`NotificationAdmin`(2)、`Notification`(4)、`Project`(7)、`Setup`(1)、`Solution`(16)、`SpaRoute`(1)、`User`(7)、`WorkItem`(9)、`Auth`(6)。
- **10 个迁移**：V1 m1_baseline（14 表）、V2 knowledge_search（FTS5 虚拟表+触发器）、V3 audit_append_only（触发器）、V4 m2_requirements（9 表）、V5 m3_delivery（9 表）、V6 knowledge_supersede（列）、V7 org_admin_user_lifecycle（列/索引/回填）、V8 agent_call_audit（1 表）、V9 external_import（3 表）、V10 notifications_jobs（3 表）。
- **39 张表**：`organization, app_user, role_assignment, customer, product, project, project_product, project_member, artifact, knowledge_document, knowledge_source_ref, knowledge_chunk, audit_log, session, work_item, requirement_evidence, requirement_assessment, requirement_snapshot, solution_version, solution_design_item, acceptance_criterion, solution_task, approval_record, project_repository, project_baseline, project_baseline_repo, execution_record, execution_change, execution_task, execution_deviation, delivery_snapshot, knowledge_update_proposal, external_source, import_batch, external_record, agent_call, notification, notification_delivery, server_job`。**不存在** `agent_run` / `agent_task` / `chat_session` 等表。
- **SPA 路由**（`App.tsx`）：`/login`、`/setup`、`/projects`、`/notifications`、`/settings`、`/projects/:projectId`（index→artifacts；artifacts/knowledge/knowledge/:documentId/requirements/requirements/:workItemId/requirements/:workItemId/solutions/:versionId/requirements/:workItemId/delivery/imports/baselines/search/members）、`/403`、`/404`、`/`、`*`。**无** chat/conversation/agent 路由。

---

## 附录 D：补充明细（页面/模块/MCP 工具/迁移/集成模板）

### D.1 前端页面清单（19 个 page，共 4,617 行；来源：`apps/web/src/pages/*.tsx`，行数为 `Get-Content` 统计）

| 页面文件 | 行数 | 职责（代码事实） |
|---|---|---|
| `ProjectsPage.tsx` | 170 | 项目卡片列表 + 客户/产品/项目三个创建表单（org_admin 才显示客户/产品表单） |
| `ProjectLayout.tsx` | 119 | 项目框架：项目切换器 + 侧栏 7 项导航 + 角色显示 + `Outlet` 上下文 |
| `ArtifactsPage.tsx` | 108 | 项目资料上传/列表/下载（原始资料不可变） |
| `KnowledgePage.tsx` | 189 | 知识文档列列表/状态筛选/创建草稿/提交审核入口 |
| `KnowledgeDetailPage.tsx` | 270 | 知识详情：编辑草稿、来源引用、提交审核、四眼审核、supersede/deprecate |
| `SearchPage.tsx` | 74 | 已认证知识检索：查询 + 命中片段 + 引用数 + 文档链接 |
| `RequirementsPage.tsx` | 161 | 项目内需求列表/状态筛选/客户端分页/新建需求表单 |
| `RequirementDetailPage.tsx` | 544 | 需求详情：证据批次追加、11 维评估、就绪确认、状态流转、方案版本 |
| `SolutionPage.tsx` | 493 | 方案编辑：设计项/验收标准/任务、一致性检查、双视图渲染、提交/批准/要求修改/新版本 |
| `BaselinesPage.tsx` | 233 | 代码仓库注册/停用、不可变基线捕获、基线列表 |
| `DeliveryPage.tsx` | 666 | 交付闭环：handoff Markdown/ZIP、实施回收、偏差、验收矩阵、As-built 快照、知识更新提议 |
| `ImportsPage.tsx` | 506 | 外部来源/CSV 预检/应用/批次/待映射记录映射与拒绝、Jira 直连卡片 |
| `MembersPage.tsx` | 252 | 项目成员添加（含新建用户并加入）/角色变更/移除（最后 owner 保护） |
| `NotificationsPage.tsx` | 113 | 站内通知收件箱、未读/已读、全部已读 |
| `SettingsPage.tsx` | 423 | 个人资料/改密、组织成员管理（停用/启用/重置/Agent 账号）、通知渠道状态与测试发送、任务列表/重试 |
| `LoginPage.tsx` | 62 | 登录 |
| `SetupPage.tsx` | 79 | 空库首次引导（创建管理员） |
| `ForcePasswordChangePage.tsx` | 95 | 临时口令会话强制改密 |
| `ErrorPage.tsx` | 22 | 403/404 错误页 |
| **合计** | **4,617** | （= 前文 4,617 行口径） |

### D.2 后端模块职责（`services/domain/src/main/java/com/mammoth/domain`，18 个包）

| 包 | 职责（代码事实） |
|---|---|
| `web` | 20 个 Controller、`ApiException(ApiExceptionHandler)`（RFC 9457 ProblemDetail）、SPA 静态托管/深链接回退 |
| `security` | `SecurityConfig`（会话+CSRF+强制改密白名单）、`SessionService`（SHA-256/8h/撤销）、`AuthenticationService`（BCrypt/限速/Agent 禁登）、`LoginRateLimiter`、`AuthController`、`AccountService`、`CurrentUser` |
| `service` | `AuthorizationService`（项目 RBAC/org_admin）、`UserService`、`CatalogService`、`ProjectService`、`OrganizationService`、`AuditService` |
| `persistence` | JPA 实体/仓储：organization/app_user/role_assignment/customer/product/project/project_member/audit_log |
| `artifact` | `ArtifactRecord`/`ArtifactStore`/`LocalArtifactStore`（内容寻址、临时文件→ATOMIC_MOVE→只读、读校验 sha256） |
| `knowledge` | 文档/来源/chunk/检索适配器/过期检测（`KnowledgeStalenessService`） |
| `workitem` | 工作项/状态机/证据批次/11 维评估/就绪引擎/上下文快照 |
| `solution` | 版本/设计项/任务/验收标准/一致性校验/双视图渲染/审批记录 |
| `git` | `GitAdapter` 接口 + `LocalGitAdapter`（只读白名单命令、超时、输出上限、UTF-8、精确 safe.directory） |
| `baseline` | 项目仓库注册、不可变基线捕获 |
| `execution` | 实施记录/文件级变更/任务完成/人工偏差/验收矩阵 |
| `delivery` | 开发交接 Markdown/`HandoffPackageService`（ZIP+manifest+秘密扫描）/As-built 快照/知识更新提议 |
| `external` | CSV/JSON 解析（UTF-8/GBK/Jira 中文表头）、导入批次/幂等、外部记录、Jira Data Center REST v2 只读客户端 |
| `notification` | 站内通知/收件人/去重、邮件/企微渠道、`server_job` writer-only worker、Agent Hook |
| `mcp` | `McpServerMain`（stdio JSON-RPC）、`McpToolService`（只读工具）、`AgentAuditJournal`、`AgentAuditIngestService`、`agent_call` |
| `admin` | `--admin-export/import/backup/restore/clear-lock` CLI、`AdminTables`（35 张导出表） |
| `config` | `DataRootResolver/Bootstrap/InstanceLock`、`DataSourceConfig`（只读数据源、schema 门槛、条件 Flyway）、`SchedulingConfig`、`MailConfig`、`JiraProperties`、`PasswordConfig` |
| `support` | `Ids`、`JsonLite`（极简 JSON） |

### D.3 MCP 工具精确清单（`McpServerMain.java`）

| 工具名 | 输入 schema（行号） | 只读约束 | 身份/授权 |
|---|---|---|---|
| `search_project_knowledge` | `projectId: string`、`query: string`、`topK: integer(1-20 默认5)`；required `[projectId, query]`（`:130-142`） | 只调 `SearchAdapter.search`，仅返回 verified，只读 `@Transactional(readOnly=true)`（`McpToolService:59-75`） | `MAMMOTH_MCP_AGENT_USER_ID` → active user + 项目角色 owner/editor/reviewer/viewer（`McpToolService:28-30,138-142`） |
| `get_knowledge_document` | `projectId: string`、`documentId: string`；required 两者（`:143-153`） | 仅 `status=verified`，否则 403（`McpToolService:78-90`） | 同上 |
| `get_requirement_context` | `projectId: string`、`workItemId: string`；required 两者（`:154-165`） | 只读返回证据/11 维评估/批准方案摘要；无写路径（`McpToolService:107-136`） | 同上 |

- 协议：`initialize` / `ping` / `tools/list` / `tools/call`，`protocolVersion=2024-11-05`（`McpServerMain:34,114-126`）。
- 只读强制：`--mcp` 设置 `mammoth.data-root.read-only=true`，拒绝显式 false，二次校验（`:49-56,64-68`）；MCP 不取实例锁、不建目录、不跑 Flyway（ADR-0008 S1）。
- 审计：每次 `tools/call` 调 `AgentAuditJournal.record(...)`（`:269,277-283`）；JSONL 字段 `eventId/at/agentUserId/tool/projectId/resultCount/durationMs/error`，不含参数/正文（`AgentAuditJournal:36-58`）；writer `AgentAuditIngestService` 幂等入库 `agent_call` + `audit_log`（`:88-107`）。
- 注意：`integrations/dsh/mammoth-domain-mcp/README.md` 仍写"两个只读工具"，与实际 3 个不一致（见第 8 节 D1 漂移）。

### D.4 Flyway 迁移与表清单（V1–V10）

| 版本 | 文件 | 表/变更 |
|---|---|---|
| V1 | `V1__m1_baseline.sql` | 14 表：organization, app_user, role_assignment, customer, product, project, project_product, project_member, artifact, knowledge_document, knowledge_source_ref, knowledge_chunk, audit_log, session |
| V2 | `V2__knowledge_search.sql` | FTS5 虚拟表 `knowledge_chunk_fts` + 3 个同步触发器（无普通表） |
| V3 | `V3__audit_append_only.sql` | `audit_log` UPDATE/DELETE 禁止触发器（无表） |
| V4 | `V4__m2_requirements.sql` | 9 表：work_item, requirement_evidence, requirement_assessment, requirement_snapshot, solution_version, solution_design_item, acceptance_criterion, solution_task, approval_record |
| V5 | `V5__m3_delivery.sql` | 9 表：project_repository, project_baseline, project_baseline_repo, execution_record, execution_change, execution_task, execution_deviation, delivery_snapshot, knowledge_update_proposal（+ `knowledge_document.applicable_baseline_id`） |
| V6 | `V6__knowledge_supersede.sql` | 列：`knowledge_document.superseded_by`、`deprecation_note` |
| V7 | `V7__org_admin_user_lifecycle.sql` | 列/索引/回填：`app_user.email/account_type/password_reset_required`、`session.password_reset_required`、`role_assignment` 唯一索引、最早 active 用户回填 org_admin |
| V8 | `V8__agent_call_audit.sql` | 1 表：agent_call |
| V9 | `V9__external_import.sql` | 3 表：external_source, import_batch, external_record |
| V10 | `V10__notifications_jobs.sql` | 3 表：notification, notification_delivery, server_job |
| **合计** | 10 个迁移 | **39 张 `CREATE TABLE`**（含 `session`）；35 张导出表（`AdminTables`） |

### D.5 `integrations/dsh/mammoth-domain-mcp/` 完整作用（5 个文件）

| 文件 | 内容与作用 |
|---|---|
| `package.json` | `@local/mammoth-domain-mcp@0.1.0`，`private`；`exports` 只暴露 locale/icon；`files` 只含 patch/locale/icon；`engines.dsh: ">=0.2.2-rc.2 <0.3.0"`；`dsh.bundle.patch: "./cordis.patch.yml"`。**无入口 JS → 配置型 bundle，零 Host 代码** |
| `cordis.patch.yml` | `insert` 一行 `id=mammoth-domain-mcp, name=@deepseek-ai/dsh-mcp-client`；config：`serverName=mammoth_domain`、`transport=stdio`、`command=<JAVA_EXE>`、`args=['-jar','<MAMMOTH_JAR>','--mcp']`、env `MAMMOTH_DATA_ROOT`/`MAMMOTH_MCP_AGENT_USER_ID`、`failOnStartupError=false`；注释说明临时身份与只读边界 |
| `README.md` | 接入模板说明：配置型 bundle、无 Host 代码；列出两个工具（实际 3 个，已漂移）；2026-11-12 已验证事实；4 个占位符必须替换；Windows 重建 jar 前必须卸载；数据根必须与 Web 一致 |
| `locale/en.json` | 插件面板元数据：title/description（read-only、仅 verified 项目知识） |
| `icon.svg` | 插件图标 |

---

## 附录 E：本报告明确不做的事

- 不提出最终架构决策（只做复用评估与倾向判断，决策权归 Lead/负责人，并需按 `AGENTS.md` 走 ADR）。
- 不改任何源码/配置/数据库/迁移/DSH Profile/插件。
- 不把"模型推断"写成"事实"；所有推断已标注 `[推断]`。
- 不读取或导出 `~/.dsh` 凭据/业务数据；DSH 侧只做安装版文档/包元数据的只读环境核对。
