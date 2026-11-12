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
- `MAMMOTH_DATA_ROOT`（留空 = OS 应用数据目录；**禁止**位于源码仓库或 `~/.dsh`，启动会失败）
- `VITE_API_BASE_URL`（前端开发代理目标）

## 数据库（M1）

- 位置：`<数据根>/db/mammoth.db`（SQLite，WAL；`<数据根>/artifacts`、`<数据根>/cache` 同时创建）。
- 迁移：**Flyway 13.12.1**，脚本位于 `services/domain/src/main/resources/db/migration/`；`V1__m1_baseline.sql` 建 14 张 M1 基线表。
- 由于 Spring Boot 4 将 Flyway 自动配置拆到独立模块，本项目**显式声明 `Flyway` bean**（版本固定），迁移在 HTTP 服务开始接受请求前完成；失败即启动失败（fail fast）。
- `GET /api/meta` 返回 `dataRoot`、`database`、`schemaVersion`（当前为 `1`）。
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

- **路由**：`/login`、`/setup`（仅空库）、`/projects`、`/projects/:id/artifacts|knowledge|knowledge/:docId|search|members`、`/settings`、`/403`、`/404`；SPA 深链接由后端回退 `index.html`。
- **角色**：owner/editor 可写资料与知识；owner 管理成员；owner/reviewer 可审核且**不能自审**；viewer 只读。
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
  也可用 `MAMMOTH_E2E_BASE` 指向已有环境；E2E 会创建临时客户/项目/知识，**不要在真实数据根运行**。
- **CI**：E2E 已加入 `.github/workflows/ci.yml` 的 `e2e` job（远端 Runner 尚未在本机验证）。

## DSH 集成与演示


- **生产/本地一体化启动**：先 `cd apps/web && npm run build`，再从仓库根目录 `java -jar services/domain/target/mammoth-domain-service.jar` —— 后端会自动探测 `apps/web/dist` 并托管 SPA（`GET /` 返回页面，`/api/**` 为接口）；也可用 `MAMMOTH_WEB_DIST` 显式指定目录。开发模式仍用 `npm run dev` + 代理。
- **接入 DSH**：见 [`docs/dsh-mcp-integration.md`](dsh-mcp-integration.md)（配置型 bundle 模板在 `integrations/dsh/mammoth-domain-mcp/`）；两个只读 MCP 工具 `search_project_knowledge` / `get_knowledge_document`，仅 verified、仅项目成员。
- **端到端演示**：`node scripts/demo-m1.mjs http://127.0.0.1:8080`，说明见 [`docs/demos/M1-DEMO.md`](demos/M1-DEMO.md)。
- MCP 入口与 Web 入口共用同一数据根；MCP 进程会持有 jar（Windows 上重建 jar 前需卸载 bundle）。

## 已知限制（当前阶段）

- M1 T1–T13 已完成并通过验收（API/服务级闭环 + UI 功能实现；见 [`docs/M1-ACCEPTANCE.md`](M1-ACCEPTANCE.md)）；
- UI 已有登录、客户/产品/项目创建、资料上传、知识创建/审核/引用查看、知识检索；**浏览器点击级验证未执行**（本环境无浏览器控制）；
- 本地 localhost 为 HTTP（无 TLS）；局域网/服务端部署必须先做 TLS；
- 登录限速与 MCP agent 身份为单进程/环境变量实现；多实例需共享存储与专用只读账号；
- 仅 SQLite 迁移；PostgreSQL 方言迁移脚本按 ADR-0003 在服务端阶段补充。
