# 开发环境与本地启动（M1）

本文件说明 M1 工程骨架的本地开发方式。架构决策见 `decisions/ADR-0002`（技术栈）、`ADR-0003`（数据库/迁移）、`ADR-0004`（Artifact 存储）、`ADR-0005`（检索）、`ADR-0006`（身份授权）。

## 前置

| 工具 | 版本（已验证） |
|---|---|
| JDK | 17（本机 17.0.15） |
| Maven | 3.9.11 |
| Node.js | 24（本机 24.18.0） |
| npm | 随 Node 提供（PowerShell 执行策略禁止 `npm.ps1` 时用 `npm.cmd`） |

## 目录约定（M1）

```
apps/web/                独立 SPA（Vite + React + TypeScript），由 Domain Service 托管静态资源
services/domain/         Spring Boot 4.1.2 Domain Service（REST + 后续 MCP stdio 入口）
packages/contracts/      预留：跨端共享契约（OpenAPI/DTO 生成），T4 起使用
docs/                    产品/工程文档（含 spikes、handoffs、specs）
spike/                   一次性验证夹具（不是产品代码）
```

## 启动后端

```powershell
cd services/domain
mvn -B -ntp spring-boot:run
# 或构建后运行： mvn -B -ntp verify; java -jar target/mammoth-domain-service.jar
```

健康检查：`GET http://127.0.0.1:8080/api/health`，元信息：`GET /api/meta`（含数据根目录）。

## 启动前端

```powershell
cd apps/web
npm install        # 首次
npm run dev        # http://127.0.0.1:5173 ，/api 代理到 8080
npm run build      # tsc --noEmit + vite build
npm test           # vitest
```

## 配置

复制 `.env.example` 为 `.env` 并按需修改（不要提交真实密钥）：

- `MAMMOTH_SERVER_PORT`（默认 8080）
- `MAMMOTH_DATA_ROOT`（留空 = OS 应用数据目录；**禁止**位于源码仓库、`~/.dsh`、网络路径或云同步目录，启动会失败——ADR-0008）
- `MAMMOTH_DATA_ROOT_READ_ONLY`（默认 false；MCP 入口会强制只读，仅诊断时手动使用）
- `MAMMOTH_ALLOW_UNSUPPORTED_ROOT`（默认 false；仅受控测试/一次性迁移可放开网络盘/云同步校验）
- `VITE_API_BASE_URL`（前端开发代理目标）

## 数据根与运行模式（ADR-0008，2026-11-13）

- **单写者**：Web/管理入口启动时对 `<数据根>/.mammoth-instance.lock` 取独占 OS 锁；第二个写实例**启动失败**，错误信息包含持有者（来自 `.mammoth-instance-holder`：pid/host/startedAt）与锁路径。进程崩溃时 OS 自动释放锁；残留文件不影响下次启动。
- **清理陈旧锁**：`java -jar mammoth-domain-service.jar --admin-clear-lock --data-root=<root>`（或 `scripts/clear-data-root-lock.ps1`）；仅在**没有活动写实例**时删除锁文件，运行中会拒绝并显示持有者。
- **只读入口（MCP）**：`java -jar ... --mcp` 强制 `mammoth.data-root.read-only=true`（System property，配置文件的默认值不能覆盖；拒绝 `--mammoth.data-root.read-only=false`）。只读入口不取写锁、不创建目录、不执行 Flyway、以只读连接 + `PRAGMA query_only=ON` 打开数据库。
- **前置**：只读入口要求数据根已由 Web/管理入口初始化（至少一次迁移且存在默认组织）；否则启动失败并提示先启动 Web 入口。迁移只能由写入口执行。
- **路径校验**：拒绝 UNC（`\\server\share`）、已知云同步目录（OneDrive/Dropbox/Google Drive/iCloud 等路径片段）、已知远端文件系统类型（SMB/CIFS/NFS/AFPFS/WebDAV/FUSE-SSHFS）。Windows 映射网络盘可能报告为 NTFS，JVM 无法可靠识别——按运维规范使用本地固定盘。
- **备份/恢复**：SQLite 用 `VACUUM INTO`/backup API（禁止运行中热复制 `.db`）；Artifact 目录复制后按 sha256 校验；恢复需停止写实例与只读进程。导出/导入包与实例锁清理手册在 M3 后续切片补齐。

## 数据库（M1）

- 位置：`<数据根>/db/mammoth.db`（SQLite，WAL；`<数据根>/artifacts`、`<数据根>/cache` 同时创建）。
- 迁移：**Flyway 13.12.1**，脚本位于 `services/domain/src/main/resources/db/migration/`；当前 `V1`–`V5`（M1 基线 14 表 + M2 需求/方案 9 表 + M3 交付闭环 9 表），共 32 张业务表。
- 由于 Spring Boot 4 将 Flyway 自动配置拆到独立模块，本项目**显式声明 `Flyway` bean**（版本固定），迁移在 HTTP 服务开始接受请求前完成；失败即启动失败（fail fast）。只读入口（ADR-0008）不创建该 bean、不执行迁移。
- `GET /api/meta` 返回 `dataRoot`、`database`、`schemaVersion`（当前为 `5`）。
- 手动核对：`sqlite3 <数据根>/db/mammoth.db "SELECT version, description, success FROM flyway_schema_history;"`
- 迁移不可在已有库上手工修改；新增变更一律新增 `V<n>__*.sql`。

## M1 API（当前实现）

| 方法 | 路径 | 说明 | 身份 |
|---|---|---|---|
| POST | `/api/users` | 创建本地账号（用户名/显示名/密码，BCrypt 存储） | 无（首次 setup） |
| GET | `/api/users` | 列出当前组织用户 | 无（T6 收紧） |
| POST/GET | `/api/customers` | 客户主数据 | 写需用户，读开放（T6 收紧） |
| POST/GET | `/api/products` | 产品主数据 | 同上 |
| POST | `/api/projects` | 创建项目（必须绑定客户、编码唯一；创建者成为 owner） | 需 `X-Mammoth-User-Id` |
| GET | `/api/projects` | 仅返回当前用户参与的项目 | 需 header |
| GET | `/api/projects/{id}` | 项目详情（需项目成员） | 需 header |
| GET/POST | `/api/projects/{id}/members` | 成员列表 / 添加成员（仅 owner） | 需 header |
| POST | `/api/projects/{id}/artifacts` | 上传原始资料（multipart `file`，可选 `kind`/`note`；内容寻址去重） | 需 header + owner/editor |
| GET | `/api/projects/{id}/artifacts` | 资料列表（元数据） | 需 header + 任意成员 |
| GET | `/api/projects/{id}/artifacts/{artifactId}` | 单条元数据 | 需 header + 任意成员 |
| GET | `/api/projects/{id}/artifacts/{artifactId}/content` | 下载原始字节（读时校验 SHA-256） | 需 header + 任意成员 |
| POST | `/api/projects/{id}/knowledge` | 创建知识文档（draft；`sourceRefs` 必填且须引用本项目 artifact） | 需 header + owner/editor |
| GET | `/api/projects/{id}/knowledge?status=` | 知识文档列表 | 需 header + 任意成员 |
| GET | `/api/projects/{id}/knowledge/{docId}` | 文档详情（含 sourceRefs） | 需 header + 任意成员 |
| POST | `/api/projects/{id}/knowledge/{docId}/submit-review` | draft → needs_review（须有来源引用） | 需 header（创建者或 owner/editor） |
| POST | `/api/projects/{id}/knowledge/{docId}/verify` | needs_review → verified（**创建者不可自审**；需 owner/reviewer 角色） | 需 header + owner/reviewer |
| GET | `/api/projects/{id}/knowledge/search?q=&topK=` | 全文检索（**仅 verified**、仅本项目；返回 chunk 引用） | 需 header + 任意成员 |

> **认证（2026-11-12 起）**：使用**服务端会话 Cookie**（`MAMMOTH_SESSION`，HttpOnly/SameSite=Lax）+ **CSRF**（首次加载调用 `GET /api/auth/csrf`，之后变更请求带 `X-XSRF-TOKEN`）。不再使用临时 `X-Mammoth-User-Id` 头。
>
> | 端点 | 说明 |
> |---|---|
> | `POST /api/auth/login` | `{username,password}` → 设置会话 Cookie；5 次失败后 429 |
> | `POST /api/auth/logout` | 撤销当前会话 |
> | `GET /api/auth/me` | 当前用户 |
> | `GET /api/auth/csrf` | 强制生成并返回 CSRF token（同时下发 XSRF-TOKEN cookie） |
> | `PUT /api/auth/profile` | 修改显示名 `{displayName}` |
> | `PUT /api/auth/password` | 修改密码 `{currentPassword,newPassword}`；成功后**撤销全部会话**，需重新登录 |
> | `POST /api/users` | **仅空库时**可匿名创建首个管理员；之后需登录 |
> | `PATCH /api/projects/{id}/members/{userId}` | owner 修改成员角色 `{role}`；最后一名 owner 不可降级（409） |
> | `DELETE /api/projects/{id}/members/{userId}` | owner 移除成员；最后一名 owner 不可移除（409） |
>
> 授权判定仍统一走 `AuthorizationService`（ADR-0006）。

## 数据根目录规则（ADR-0002/0004）

- 默认：`%LOCALAPPDATA%\Mammoth\data`（Windows）
- 可在应用中配置（配置项/环境变量），但**不得**位于：
  - DSH Home（`~/.dsh`）——Agent 运行环境不属于 Mammoth 业务数据域；
  - 源码仓库目录——避免误提交与误删；
  - 应用安装目录——可能只读、会被升级覆盖。
- 违反时 Domain Service 启动即失败（fail fast）。

## CI

`.github/workflows/ci.yml`：后端 `mvn -B -ntp verify`、前端 `npm ci && npm run build && npm test`、浏览器 E2E（构建 jar 与 web、安装 Chromium、启动后端、`npm run test:e2e`）。

**注意**：本地可复现同一组命令，但 GitHub Actions 是否真正运行需推送后在仓库页面确认；本地验证不等于远端 CI 已验证。

## UI 路由与浏览器 E2E

- **路由**：`/login`、`/setup`（仅空库）、`/projects`、`/projects/:id/artifacts|knowledge|knowledge/:docId|requirements|requirements/:workItemId|requirements/:workItemId/solutions/:versionId|search|members`、`/settings`、`/403`、`/404`；SPA 深链接由后端回退 `index.html`。
- **角色**：owner/editor 可写资料、知识与需求/方案；owner 管理成员；owner/reviewer 可审核（不能自审）、可确认需求就绪、可批准方案（方案作者不可自批）；viewer 只读。
- **浏览器 E2E**（Playwright，需先构建 dist 并使用空数据根启动后端）：
  ```powershell
  cd apps/web && npm install          # 首次
  npx playwright install chromium     # 首次
  cd ../..
  mvn -B -ntp -f services/domain/pom.xml package
  java -jar services/domain/target/mammoth-domain-service.jar --server.port=18130 `
    --mammoth.data-root="$env:TEMP\mammoth-e2e"
  # 另一个终端：
  cd apps/web && npm run test:e2e
  ```
  也可用 `MAMMOTH_E2E_BASE` 指向已有环境；E2E 会创建临时客户/项目/知识/需求/方案，**不要在真实数据根运行**。
  E2E 现含 2 个场景（M1 知识闭环、M2 需求→方案批准）；同一空数据根下顺序执行，拆分运行需独立数据根（见 `docs/evidence/M2-VERIFICATION.md` §2）。
- **CI**：E2E 已加入 `.github/workflows/ci.yml` 的 `e2e` job（远端 Runner 尚未在本机验证）。
- **截图**：`node scripts/capture-m2m3-screenshots.mjs` 用脱敏数据生成 M2/M3 UI 证据（输出到 `docs/evidence/screenshots/`）。

## DSH 集成与演示


- **生产/本地一体化启动**：先 `cd apps/web && npm run build`，再从仓库根目录 `java -jar services/domain/target/mammoth-domain-service.jar` —— 后端会自动探测 `apps/web/dist` 并托管 SPA（`GET /` 返回页面，`/api/**` 为接口）；也可用 `MAMMOTH_WEB_DIST` 显式指定目录。开发模式仍用 `npm run dev` + 代理。
- **接入 DSH**：见 [`docs/dsh-mcp-integration.md`](dsh-mcp-integration.md)（配置型 bundle 模板在 `integrations/dsh/mammoth-domain-mcp/`）；三个只读 MCP 工具 `search_project_knowledge` / `get_knowledge_document` / `get_requirement_context`，仅 verified 知识、仅项目成员、无写路径。
- **端到端演示**：`node scripts/demo-m1.mjs http://127.0.0.1:8080`（[`docs/demos/M1-DEMO.md`](demos/M1-DEMO.md)）与 `node scripts/demo-m2.mjs http://127.0.0.1:8080`（[`docs/demos/M2-DEMO.md`](demos/M2-DEMO.md)，需空数据根）。
- MCP 入口与 Web 入口共用同一数据根；MCP 进程会持有 jar（Windows 上重建 jar 前需卸载 bundle）。

## M3 交付闭环（2026-11-13）

- **代码仓库**：`POST /api/projects/{p}/repositories`（owner）注册本地仓库绝对路径；服务端用只读 Git 命令归一化到仓库根、校验非数据根/`~/.dsh`、脱敏远程 URL。禁止把源码放进数据根或知识库。
- **基线**：`POST /api/projects/{p}/baselines`（owner/editor）为所有 active 仓库捕获 commit/branch/脏树状态；脏工作树默认 409，owner 可 `allowDirty=true` 记录不可复现基线；基线不可修改。
- **交接包**：`GET /api/projects/{p}/work-items/{w}/handoff`（需批准方案）返回 Markdown（业务/技术渲染 + 任务 + 验收 + 基线）。
- **实施回收**：`POST .../executions`（owner/editor）以 `baselineId + repositoryId + toCommit` 回收文件级变更；`POST .../executions/{id}/deviations` 人工分类偏差；`GET .../acceptance-matrix` 返回覆盖状态与未完成任务候选。
- **交付快照**：`POST .../deliveries` 生成 As-built 草稿；`POST .../deliveries/{id}/approve` 需 owner/reviewer 且非作者（四眼），批准时 Markdown 落 Artifact，批准后不可覆盖。
- **知识闭环**：`POST .../knowledge-proposals` 基于已批准快照；`accept` 需 owner/reviewer 且非提议者，生成**知识草稿**（仍走既有四眼审核发布）；发布后可 `POST /knowledge/{id}/supersede`（新替旧）或 `/deprecate`（废弃），历史保留但不再检索；知识列表/详情/检索返回 `stale`（绑定的代码基线落后于最新基线），仅提示不改状态。
- **安全**：Git 只读白名单（不含 clone/fetch/push/checkout/commit/reset/clean），10 s 超时、1 MiB 输出上限；演示/测试只用临时合成仓库。
## 导出/导入与备份恢复（ADR-0008，CLI）

写实例运行时不能执行导出/导入/备份（数据根实例锁），先停止服务；恢复是纯文件操作，不需要启动服务。

```powershell
$jar = "services/domain/target/mammoth-domain-service.jar"

# 导出（默认写到 <数据根>/exports/；也可显式指定 zip）
java -jar $jar --admin-export="$env:TEMP\mammoth-export.zip" --mammoth.data-root="$env:LOCALAPPDATA\Mammoth\data"

# 导入（目标必须为空目录；密码优先用环境变量，避免出现在进程列表）
$env:MAMMOTH_IMPORT_ADMIN_PASSWORD = "<至少 8 位的目标管理员口令>"
java -jar $jar --admin-import="$env:TEMP\mammoth-export.zip" --admin-username="<导出中的用户名>" `
  --mammoth.data-root="$env:TEMP\mammoth-imported"

# 备份（在写实例停止后执行；目标目录不能在数据根内）
java -jar $jar --admin-backup="$env:TEMP\mammoth-backups" --mammoth.data-root="$env:LOCALAPPDATA\Mammoth\data"

# 恢复（目标必须为空目录，不能是当前运行中的数据根）
java -jar $jar --admin-restore="$env:TEMP\mammoth-backups\mammoth-backup-<时间戳>" --restore-to="$env:TEMP\mammoth-restored"
```

要点：

- **导出包**：`manifest.properties` + 每表 SQL + Artifact 字节；**不含**密码哈希与会话；导出为业务数据（含客户资料），按 Confidential 保管，**不要**提交到代码仓库或上传公开网盘。
- **导入**：只接受空库；校验 schema 版本、每个条目 sha256、行数与 Artifact 大小；只为 `--admin-username` 设置新口令，其他账号被锁定（需管理员重建/后续密码重置）。
- **备份**：`VACUUM INTO` + Artifact 复制 + manifest（db sha256/size、schema、artifact 哈希）；备份目录与数据根分离。
- **恢复**：校验 db sha256 与 `PRAGMA integrity_check`、schema 版本、每个 Artifact sha256；成功后写 `restore.properties` 与 `backup-manifest.properties`。
- **审计**：`admin.export` / `admin.import` / `admin.backup` 写入审计；恢复记录写文件（目标库尚未运行）。
- 演练脚本：`node scripts/admin-drill.mjs`（需要 `MAMMOTH_JAVA` 指向 java；合成数据；9 步全流程校验）。
## 已知限制（当前阶段）

- M1 / M1.5 / M2 已完成本地验收（见 [`docs/M1-ACCEPTANCE.md`](M1-ACCEPTANCE.md)、[`docs/evidence/M2-VERIFICATION.md`](evidence/M2-VERIFICATION.md)）；UI 已由 Playwright 真实浏览器流程覆盖（2 场景）；
- 应用内无 LLM 调用；AI 参与仅限 MCP 只读工具（ADR-0011 未立）；
- 本地 localhost 为 HTTP（无 TLS）；局域网/服务端部署必须先做 TLS；
- 登录限速与 MCP agent 身份为单进程/环境变量实现；多实例需共享存储与专用只读账号；
- 仅 SQLite 迁移；PostgreSQL 方言迁移脚本按 ADR-0003 在服务端阶段补充；
- 需求/方案列表仍为客户端分页；证据不支持 PDF/Office 解析（ADR-0009 OPEN）；
- 数据根单写者锁、网络盘/云同步拒绝、MCP 只读连接、Git 基线/实施回收/交付快照/知识闭环已实现（M3）；**导出/导入与备份/恢复已实现（ADR-0008 CLI）**；实例锁清理已提供 CLI/脚本；
- 在 DSH `workspace-write` 沙箱中 Playwright 会因 worker 管道 `EPERM` 失败；E2E 需普通终端、CI 或一次性提权。

## M4 S2：Agent 账号、MCP 审计与交接包 ZIP（2026-11-13）

- **专用 Agent 账号**：org_admin 在 `/settings` 创建“Agent 账号（不可登录）”，项目 owner 将其加为 `viewer`；MCP 的 `MAMMOTH_MCP_AGENT_USER_ID` 指向该账号。Agent 账号不能登录 Web、无口令重置。
- **MCP 审计 journal**：MCP 每次 `tools/call` 追加 `<数据根>/audit/agent-audit.jsonl`（工具、项目、结果数、耗时、错误；不含参数/正文）。writer 进程启动时创建 `audit/`，`AgentAuditIngestService` 定时（默认 30s）入库 `agent_call` + `audit_log`；MCP 只读进程不建目录、不写 DB。
- **可配置**：`mammoth.agent-audit.ingest-interval-ms`（默认 30000）、`mammoth.agent-audit.initial-delay-ms`（默认 10000）。
- **交接包 ZIP（BG-01）**：`GET /api/projects/{p}/work-items/{w}/handoff/package` 生成并落 Artifact；UI 在交付闭环页“生成交接包 ZIP”后显示下载链接。包内 `manifest.properties` 记录每个文件 SHA-256；生成前做秘密模式扫描；审计 `handoff.export`；不含凭据/源码。
- **注意**：MCP 只读进程不能写 `audit_log`，因此审计以本地 journal 为过渡；不要把它当作数据库直接写入。

## M4 S3/S4：只读导入与 Jira 文件映射（2026-11-13）

- **来源**：项目 owner 在 `/projects/{id}/imports` 创建 `jira`/`csm`/`generic` 来源；来源只保存脱敏 base URL，不保存账号/令牌。
- **Jira CSV**：支持 Jira Data Center 8.x 中文导出表头（`关键字/概要/描述/状态/问题类型/优先级/Issue URL/创建日期/已更新/经办人/报告人/系统模块` 等），自动识别 UTF-8/GBK/BOM；单文件 ≤50 MB、≤50k 行；JSON 支持数组/`records`/`issues`。
- **预检与应用**：`POST .../imports/preview` 零落库；`POST .../imports` 一次性写原始 Artifact + 批次 + 外部记录，按 `source+file_sha256` 幂等；同记录内容变化追加新版本。
- **证据映射**：人工把待映射记录挂到需求（可选验收标准），生成 `jira`/`external` 证据；冻结需求返回 409；不自动改需求/方案。
- **限制**：M4 只导入文件，不调用 Jira API；CSM 无 API，暂按文件/人工导入评估。

## M4 S5：站内通知、邮件/企微与服务端任务（2026-11-13）

站内通知默认启用；邮件/企微完整实现但默认关闭。用户可在 `/notifications` 查看/已读；org_admin 在 `/settings` 查看渠道状态和测试发送、后台任务。

**站内通知（默认启用）**

```properties
mammoth.notifications.in-app.enabled=true
```

**邮件（默认关闭；启用时必须配齐 host/from）**

```properties
mammoth.notifications.email.enabled=false
mammoth.notifications.email.from=
mammoth.notifications.email.test-recipient=
spring.mail.host=
spring.mail.port=587
spring.mail.username=
spring.mail.password=          # 用环境变量注入，不要写入仓库
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

**企微群机器人（默认关闭；webhook 视为凭据）**

```properties
mammoth.notifications.wecom.enabled=false
mammoth.notifications.wecom.webhook-url=       # 环境变量/OS 密钥，勿入仓/日志
mammoth.notifications.wecom.msg-type=markdown
mammoth.notifications.wecom.events=solution.submitted,job.failed
mammoth.notifications.wecom.rate-limit-per-minute=20
mammoth.notifications.wecom.test-webhook-url=  # 可选测试群
```

**任务 worker**

```properties
mammoth.jobs.poll-interval-ms=5000
mammoth.jobs.initial-delay-ms=10000
```

- 默认关闭时**零外发**；启用但缺必填参数会启动失败；渠道状态只显示 enabled/configured/missing，不回显秘密。
- 邮件/企微只用于内部通知；向客户/外部群发送需新 ADR。
- 凭据只放环境变量/OS 存储，不写数据库、不写日志、不写导出/交接包；`server_job`/通知表默认不进管理导出。
- worker 只由 writer 进程调度；`read-only=true` 的 MCP 进程不调度。

## M4.5-1 Jira 只读连接器（mock 已实现；真实 smoke 待环境变量）

- **目标**：Jira Data Center 8.x REST API v2 只读拉取 issue，进入 M4 S3 的导入/证据候选；**不写 Jira**。
- **入口**：项目 → 外部导入 → 选择 `jira` 来源 → “Jira 预检/拉取并应用”。JQL 默认 `updated >= -30d ORDER BY updated DESC`，可覆盖；最大条数 1–500。
- **字段**：`关键字/概要/描述/状态/问题类型/优先级/创建/更新/经办人/报告人/组件/Jira project key`；按记录人工映射到需求/证据，不自动关联 Mammoth 项目。
- **认证**：Data Center PAT 以 `Authorization: Bearer <token>` 发送；令牌只从环境/OS 读取，不写 DB/日志/导出。
- **安全**：令牌只发往 `mammoth.jira.base-url` 配置的 origin；项目来源的 base URL 只能作为显示/一致性校验，不能把令牌重定向到其他主机。

```properties
mammoth.jira.base-url=http://119.145.133.146:18009
mammoth.jira.token=          # MAMMOTH_JIRA_TOKEN，环境变量/OS 凭据，勿入仓
mammoth.jira.jql=updated >= -30d ORDER BY updated DESC
mammoth.jira.max-results=100
mammoth.jira.page-size=50
```

- 未配置 token 时 Jira 拉取返回 409，不会发起外部调用；真实 smoke 需要轮换后的只读 PAT。
- HTTP 已被负责人接受；仍建议仅受控内网/网络隔离，后续优先 HTTPS。
- 测试：`JiraImportTest` 用本地 JDK `HttpServer` 覆盖分页/Bearer/标准字段；**未调用真实 Jira**。

## M4.5 Agent Hook 通知（2026-11-13）

- 当前没有 AgentTask 持久表；先提供通用 hook ingress，供后续 Agent 运行时调用：
  `POST /api/projects/{projectId}/agent-hooks`
  body：`{"eventType":"agent.task.completed|agent.task.confirmation_required","taskId":"...","title":"...","summary":"...","link":"/projects/..."}`
- 权限：项目 owner/editor（未来 Agent 运行时通过内部服务调用；MCP 只读进程不能调用）。
- 收件人：`completed` → owner/editor/reviewer；`confirmation_required` → owner/reviewer；操作者不通知自己。
- 站内默认；企微是否外发由 `mammoth.notifications.wecom.events` 白名单决定，默认包含这两个事件与 `job.failed`。
- `link` 只能是 `/` 开头的应用内路径；外部 URL 拒绝，避免开放重定向。
- 审计：`agent.hook`（eventType/taskId/created；不含标题/正文）。
- 测试：`AgentHookTest` 2/2；Maven Surefire 强制测试环境关闭真实邮件/企微，避免继承开发机凭据。

## M4.5 本机 Docker Compose（2026-11-13）

- 仓库根新增 `Dockerfile`/`.dockerignore`；JRE 17 镜像，jar + `apps/web/dist` 打进镜像，数据根 `/data`。
- 本机 compose（不打进仓库）：`D:\DockerCompose\mammoth-agent.yml`。
- 项目名 `mammoth-agent`（独立 compose 项目，避免影响 `docker-compose.yml` 的 mysql/pg/redis/rabbitmq/protoforge）。
- 端口：`18080:8080`；数据卷：`D:\DockerCompose\data\mammoth-agent-data:/data`。
- 启动/更新：

```powershell
cd D:\DockerCompose
docker compose -f mammoth-agent.yml up -d --build
docker compose -f mammoth-agent.yml logs -f
```

- 环境变量由 compose 从当前 shell/`.env` 插值；**不要把 Jira token/企微 webhook 写进 compose 文件**。默认示例为 WeCom 关闭、Jira token 为空。
- 已用真实环境变量重建验证：WeCom `enabled/configured`、Jira token 注入、项目 `DWMS` 与 3 条 HSG 记录可见；未发送额外企微消息。
- 停止：`docker compose -f mammoth-agent.yml down`（保留卷）；不要 `down -v`，除非确认删除本地数据。
