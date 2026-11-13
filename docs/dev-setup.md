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
