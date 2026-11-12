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

> **临时身份来源**：T4 阶段通过 `X-Mammoth-User-Id` 头传递调用者；这不是最终认证方案，登录/会话/CSRF 在认证任务中用 Spring Security 替换，授权判定仍统一走 `AuthorizationService`（ADR-0006）。

## 数据根目录规则（ADR-0002/0004）

- 默认：`%LOCALAPPDATA%\Mammoth\data`（Windows）
- 可在应用中配置（配置项/环境变量），但**不得**位于：
  - DSH Home（`~/.dsh`）——Agent 运行环境不属于 Mammoth 业务数据域；
  - 源码仓库目录——避免误提交与误删；
  - 应用安装目录——可能只读、会被升级覆盖。
- 违反时 Domain Service 启动即失败（fail fast）。

## CI

`.github/workflows/ci.yml`：后端 `mvn -B -ntp verify`、前端 `npm ci && npm run build && npm test`。

**注意**：本地可复现同一组命令，但 GitHub Actions 是否真正运行需推送后在仓库页面确认；本地验证不等于远端 CI 已验证。

## 已知限制（当前阶段）

- T1–T6、T8 已完成：工程骨架、数据根自检、SQLite/Flyway 迁移、主数据/项目/成员 + 授权骨架 + 审计、Artifact 导入、知识文档状态机与四眼审核、项目内全文检索（仅 verified）；
- 检索为全文（FTS5 trigram），语义改写能力有限；向量/混合检索按 ADR-0005 延后；
- 认证仍是临时的 `X-Mammoth-User-Id` 头，**不得用于多人/网络暴露环境**；
- 尚未提供 MCP 只读工具（T9）、权限隔离专项测试（T10）、演示脚本（T11）；前端尚无知识视图。
