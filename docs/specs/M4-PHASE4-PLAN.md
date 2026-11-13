# M4 阶段计划：企业集成与协同（S0 草案）

- **状态**：Draft（S0，待负责人评审 ADR/规格后进入 S1）
- **日期**：2026-11-13
- **基线**：`main` @ `3231b43`（以 `git log -1` 为准）
- **依据**：`docs/04-MVP-STRATEGY.md` Phase 4、`docs/12-ROADMAP.md` M4、`docs/13-OPEN-DECISIONS.md`、`decisions/ADR-0006`（含 2026-11-13 增补）、`ADR-0007`、`ADR-0008`（含增补）、`ADR-0011`、`ADR-0012`、`ADR-0013`
- **配套**：[`M4.5-PHASE45-PLAN.md`](M4.5-PHASE45-PLAN.md)（生产集成/运营启用）、[`M4-THREAT-MODEL.md`](M4-THREAT-MODEL.md)

## 1. 阶段目标与退出条件

### 1.1 目标

在不引入生产凭据、生产数据、双向同步与 PostgreSQL 多实例的前提下，交付 M4-core：

1. **组织与权限**：`org_admin`、用户停用/启用、管理员口令重置、Agent 账号、会话撤销、建用户/用户目录收紧；
2. **外部 Agent**：BG-01 交接包 zip、专用只读 Agent 账号、MCP 调用审计（本地 journal + writer 入库）；
3. **只读集成**：通用 CSV/JSON 导入框架、来源/批次/外部记录、幂等、dry-run、审计；
4. **Jira 只读文件导入**：Jira 导出文件映射为证据候选；CSM 只留适配器接口与 M4.5 规划；
5. **验收矩阵补强（BG-03）**：业务目标 + 执行记录/变更/证据链接；
6. **通知与任务**：站内通知默认启用；邮件/企微**完整实现但默认关闭**，配置后启用；持久任务队列、重试/死信；
7. **团队服务器预案（S6，条件）**：反向代理 TLS、Secure Cookie、备份演练；不交付生产证书/域名；PG 多实例归 M5。

### 1.2 建议退出条件

- **S1–S5 验收标准全部通过**；S6 为条件项（负责人明确“服务器与证书暂不处理”，可不实施）。
- 全量回归：后端 `mvn verify`、前端 build + tests、浏览器 E2E、`demo-m4.mjs`、M3 验收演练 18/18、ADR-0008 演练 9/9。
- 证据归档：`docs/evidence/M4-VERIFICATION.md`、`docs/M4-ACCEPTANCE.md`、`docs/process/M4-TIMELINE.md`、`docs/quality/M4-BUGS.md`。
- 不接触真实客户数据/生产凭据/生产环境；真实客户验收仍需负责人授权。

## 2. 范围与非目标

### 2.1 范围内（M4-core）

| 切片 | 内容 |
|---|---|
| S1 | 组织/用户生命周期与权限收紧（V7） |
| S2 | 外部 Agent 前置：BG-01 交接包 zip + 专用 Agent 账号 + MCP 审计（V8） |
| S3 | 通用只读导入框架（V9） |
| S4 | Jira 导出文件映射 + BG-03 验收矩阵补强（无迁移） |
| S5 | 站内通知 + 邮件/企微渠道（完整实现、默认关闭）+ 服务端任务（V10） |
| S6 | 团队服务器预案（条件；无代码或仅配置/文档） |

### 2.2 明确不做（M4）

- Jira/CSM **生产 API 调用**、写操作、双向同步（→ M4.5/M5+）；
- 真实邮件/企微**发送启用**（代码在 M4 S5 完整实现，默认关闭；启用由负责人配置，M4 验收不真实外发）；
- PostgreSQL、多实例、HA、共享网络盘数据根（→ M5）；
- 远程 Agent 网关/服务令牌（→ M4.5 评估）；
- 应用内 LLM/AgentRun（ADR-0011 占位已改用于外部集成；LLM 另立 ADR）；
- 多组织/多租户、SSO/OIDC、生产证书/域名/OS 服务化；
- 文档解析（ADR-0009 仍 OPEN）：导入只处理文本字段/原始文件，不解析 PDF/Office；
- 真实客户项目验收（需负责人授权脱敏项目）。

### 2.3 延期登记

所有延期项写入 [`M4.5-PHASE45-PLAN.md`](M4.5-PHASE45-PLAN.md)，标明价值、依赖、恢复条件与验收；不从产品文档删除。

## 3. 已确认的前置决策

| 决策 | 结论 | 记录 |
|---|---|---|
| 集成形态 | 只读文件导入 → 只读 API → 双向同步；M4 只做文件导入 | ADR-0011 |
| 首发系统 | Jira 先于 CSM；Jira 目标 `http://119.145.133.146:18009/`（M4 不调用） | ADR-0011、M4.5 |
| 凭据 | 环境/OS 存储 + `SecretProvider`；不入库/日志/导出；只读账号 | ADR-0011 |
| 服务端 | 单节点 + 反向代理 TLS + SQLite；PG 多实例 = M5 | ADR-0012 |
| 通知 | 站内默认启用；邮件/企微完整实现、默认关闭、配置后启用 | ADR-0013 |
| 外部 Agent | 专用只读账号 + BG-01 zip + MCP 审计方案 A（本地 journal + writer 入库） | ADR-0006/0008、本文件 §7 |
| 组织权限 | 新增 `org_admin` + 停用/重置/会话撤销 + 限制建用户；不新增项目角色 | ADR-0006 增补 |
| M3.5 顺序 | BG-01 → S2；BG-03 → S4；BG-02 → S6/M5 | 本文件 §8 |
| ADR 编号 | 0011 外部集成、0012 服务端、0013 通知；LLM 顺延 | `docs/13-OPEN-DECISIONS.md` |

## 4. 现状证据（影响设计）

- 授权只有项目级角色；`role_assignment` 未使用；`POST /api/users` 任意登录用户可用；无停用/管理员重置（[AuthorizationService.java](../../services/domain/src/main/java/com/mammoth/domain/service/AuthorizationService.java)、[UserController.java](../../services/domain/src/main/java/com/mammoth/domain/web/UserController.java)）。
- MCP 3 个只读工具；身份用 `MAMMOTH_MCP_AGENT_USER_ID`；MCP 进程只读，不能写 `audit_log`（[McpToolService.java](../../services/domain/src/main/java/com/mammoth/domain/mcp/McpToolService.java)、[McpServerMain.java](../../services/domain/src/main/java/com/mammoth/domain/mcp/McpServerMain.java)）。
- 无 mail/调度/出站 HTTP；无 PG 迁移目录；`MIN_SCHEMA_VERSION=6`；`AdminTables` 硬编码 31 表（[pom.xml](../../services/domain/pom.xml)、[DataSourceConfig.java](../../services/domain/src/main/java/com/mammoth/domain/config/DataSourceConfig.java)、[AdminTables.java](../../services/domain/src/main/java/com/mammoth/domain/admin/AdminTables.java)）。
- 用户无 email 列；会话 `Secure` 依赖 `request.isSecure()`；登录限速内存（[AuthController.java](../../services/domain/src/main/java/com/mammoth/domain/security/AuthController.java)、[LoginRateLimiter.java](../../services/domain/src/main/java/com/mammoth/domain/security/LoginRateLimiter.java)）。
- 交接包当前为 Markdown 响应，未落文件（BG-01）；验收矩阵缺业务目标/执行证据（BG-03）（[DeliveryController.java](../../services/domain/src/main/java/com/mammoth/domain/web/DeliveryController.java)、[M3.5-BACKLOG.md](M3.5-BACKLOG.md)）。
- CI workflow 已配置，远端未实际运行（[ci.yml](../../.github/workflows/ci.yml)）。

## 5. 切片总览与顺序

```text
S0 准入（本文件/ADR/威胁模型/夹具清单）
  └─ S1 组织/用户生命周期与权限收紧（V7）
       ├─ S2 外部 Agent 前置：BG-01 + Agent 账号 + MCP 审计（V8）
       ├─ S3 通用只读导入框架（V9）
       │    └─ S4 Jira 导出文件 + BG-03
       └─ S5 站内通知 + 邮件/企微 + 服务端任务（V10）
            └─ S6 团队服务器预案（条件）
```

依赖说明：S2 的 Agent 账号依赖 S1 的 `account_type`/`org_admin`；S3 的映射依赖 ADR-0011；S5 的任务 worker 依赖 S1 的 org_admin 审计；S4 只依赖 S3；S6 不阻塞 S1–S5。

## 6. S1：组织/用户生命周期与权限收紧

### 6.1 目标

关闭 ADR-0006 已承诺但未实现的账号治理缺口，为团队服务器、通知收件人、Agent 账号提供安全前置。

### 6.2 数据（V7）

| 变更 | 说明 |
|---|---|
| `app_user.email` | TEXT NULL；格式校验；本人/org_admin 可改；用于邮件通知 |
| `app_user.account_type` | TEXT NOT NULL DEFAULT `human`；`human`/`agent` |
| `app_user.password_reset_required` | INTEGER NOT NULL DEFAULT 0 |
| `session.password_reset_required` | INTEGER NOT NULL DEFAULT 0；登录时从用户复制 |
| `role_assignment` 唯一索引 | `(user_id, role, scope_type, scope_id)` |
| 回填 | 最早创建的 active 用户成为 `org_admin`（`scope_type='organization'`）；无用户则 setup 时创建 |

### 6.3 行为

- **bootstrap**：空库首个用户自动 `org_admin`；`POST /api/users` 空库例外保留。
- **创建用户**：仅 `org_admin`；可创建 `human`/`agent`；Agent 账号必须无交互登录。
- **目录**：`GET /api/users`（含状态/时间）仅 `org_admin`；`GET /api/users/directory` 返回 id/displayName/username，供项目 owner 添加成员。
- **停用/启用**：`POST /api/users/{id}/disable`、`/enable`（org_admin）；停用撤销全部会话；`requireActiveUser` 拒绝 disabled。
- **管理员重置**：`POST /api/users/{id}/reset-password`（org_admin）；设置临时口令 + `password_reset_required=true`；撤销会话；用户登录后仅能改密/登出/查看自己，直到改密成功。
- **强制改密**：`SessionAuthenticationFilter` 根据 session 标记授予 `ROLE_PASSWORD_RESET_REQUIRED`；`SecurityConfig` 只放行 `/api/auth/{me,password,logout,csrf}`，其余 `/api/**` 要求 `ROLE_USER`。
- **主数据**：客户/产品创建仅 `org_admin`；项目创建仍允许任何 active 用户（创建者 owner）。
- **审计**：见 ADR-0006 增补 §6。
- **UI**：设置页/成员页显示用户状态、邮箱、账户类型；org_admin 可停用/重置/创建 Agent；非 admin 隐藏且 API 403。

### 6.4 测试

`OrgAdminTest`、`UserLifecycleTest`、`PasswordResetTest`、`UserDirectoryTest`、`MigrationTest` V7；权限负向（非 admin 建用户/停用/重置/改主数据）全部 403；停用后旧 Cookie 401；Agent 登录 401；强制改密只能访问白名单。

### 6.5 DoD

代码 + V7 + 前端 + 测试 + 审计 + `docs/08`/`ADR-0006` 更新；不破坏 M1–M3 回归。

## 7. S2：外部 Agent 前置（BG-01 + Agent 账号 + MCP 审计）

### 7.1 BG-01 交接包文件导出

- 端点：`GET /api/projects/{p}/work-items/{w}/handoff/package`（项目成员；与现有 handoff 权限一致）。
- 内容：`manifest.properties`（formatVersion、generatedAt、appVersion、project/需求、solutionVersion、baseline、contextSnapshot、文件清单+sha256）、`solution-business.md`、`solution-technical.md`、`tasks.md`、`acceptance.md`、`baseline.md`、`README.md`、`SECURITY.md`。
- 存储：zip 作为 Artifact（kind=`handoff_package`，内容寻址）；返回 `artifactId`、`sha256`、`downloadUrl`。
- 安全：不含凭据/源码/`.env`/连接串；生成前做秘密模式扫描（`password=`、`token=`、`-----BEGIN`、`jdbc:` 等），命中则 409 并提示；下载需项目角色；审计 `handoff.export`（含 artifactId/sha256，不含正文）。
- 测试：manifest 校验、sha256、无秘密、角色 403、审计、重复下载同一 Artifact。

### 7.2 专用 Agent 账号

- `org_admin` 创建 `account_type=agent` 用户（随机口令、不可登录、不可改密）；项目 owner 加为 `viewer`。
- MCP 的 `MAMMOTH_MCP_AGENT_USER_ID` 指向该账号；文档更新 `docs/dsh-mcp-integration.md`。
- 测试：Agent 账号登录 401；非成员/非 viewer 工具调用 403；审计 actor 为 Agent 账号。

### 7.3 MCP 调用审计（ADR-0008 增补方案 A）

- MCP 每次 `tools/call` 追加 `<dataRoot>/audit/agent-audit.jsonl`：`eventId`、`at`、`agentUserId`、`tool`、`projectId`、`resultCount`、`durationMs`、`error`；不含参数原文/正文。
- writer 启动创建 `audit/` 目录；MCP 不建目录/不取锁；不可写时 stderr 告警，不崩溃。
- `AgentAuditIngestService`（writer-only）启动 + 定时读取新行，写 `agent_call`（`event_id` 唯一）+ `audit_log`（`agent.tool_call`）；损坏行记录错误并跳过。S2 先用简单定时 ingest，S5 可迁移到 `server_job`。
- `agent_call` 表（V8）：`event_id` PK、agent_user_id、tool、project_id、result_count、duration_ms、error、at、ingested_at、source_file。
- 测试：JSONL 写入/解析、重复导入不重复、损坏行、只读进程不写 DB、审计脱敏。

## 8. S3/S4：只读导入框架与 Jira 文件导入

### 8.1 S3 数据（V9）

| 表 | 关键列 | 说明 |
|---|---|---|
| `external_source` | id, project_id, system_type, name, base_url_sanitized, status, created_by, created_at | 项目外部来源；不含凭据；项目内 name 唯一 |
| `import_batch` | id, project_id, source_id, artifact_id, mapping_profile, dry_run, status, row_count, accepted_count, rejected_count, error_summary, created_by, created_at, finished_at | 原始文件 Artifact + 批次状态 |
| `external_record` | id, batch_id, source_id, project_id, external_type, external_id, external_key, external_version, source_url_sanitized, content_hash, mapped_work_item_id, mapped_evidence_id, status, created_at | 外部记录；唯一 `(source_id, external_type, external_id, external_version)` |

- 原始文件作为 Artifact（kind=`external_import`），sha256/大小校验；记录不存源码正文。
- `external_record.status`：`unmapped`/`mapped`/`rejected`；`mapped_*` 可空。

### 8.2 S3 API/流程

1. `POST /api/projects/{p}/external-sources`（owner）创建来源；
2. `POST /api/projects/{p}/imports`（owner/editor，multipart）上传文件 + sourceId + mappingProfile + `dryRun=true`（默认）→ 校验并返回批次报告；
3. `POST /api/projects/{p}/imports/{id}/apply` → 写入 `external_record`（不改 WorkItem/证据）；
4. `GET /api/projects/{p}/external-records?status=unmapped` → 待映射；
5. `POST /api/projects/{p}/external-records/{id}/map`（owner/editor，body `{workItemId}`）→ 创建 `RequirementEvidence`（`evidence_type=jira|external`，text=摘要+描述，artifact=批次原始文件，locator=`jira:KEY`/`source:ID`），更新 `mapped_*`，审计 `external_record.map`；
6. `POST .../external-records/{id}/reject`（owner/editor）→ 记录拒绝原因，审计。

- 权限：owner 管理来源；owner/editor 导入/映射/拒绝；viewer 只读；跨项目 403。
- dry-run：解析/校验/去重/报告，不写 `external_record`、不产生证据、不通知。
- 幂等：同文件 sha256 + source + mapping 识别重复批次；同记录同版本跳过；版本变化新增记录/证据批次。
- 解析：CSV（RFC4180：引号/逗号/换行/BOM/UTF-8）+ JSON（Jackson）；建议引入 `commons-csv`（Apache-2.0）或内置最小解析器，S3 规格评审时定；单文件 ≤50 MB、≤50k 行，可配置。
- 审计：`integration.import`（batchId/sourceId/counts/status）、`integration.import.dry_run`、`external_record.map`、`external_record.reject`。

### 8.3 S4 Jira 导出文件映射

- 目标：`http://119.145.133.146:18009/`（负责人提供；**M4 不调用**，仅记录为 M4.5 目标）。
- 支持 Jira CSV/JSON 导出字段：Issue key、Summary、Description、Issue Type、Status、Priority、Created、Updated、Reporter、Assignee、Labels、Components、Fix Version/s、Resolution、Project key；列名通过 mapping profile 映射，不硬编码 Jira 云/DC 差异。
- 证据候选文本：`[KEY] Summary` + 描述 + 状态/类型/优先级/更新时间；locator=`jira:KEY` 或配置的 base URL + key（脱敏）；不下载附件；附件 URL 只作为 locator/note。
- 新增证据类型：`jira`/`external`（Java 常量；DB TEXT 无约束）。
- 测试：Jira 格式夹具（中文字段、空值、多行描述、引号）、映射、去重、版本变化、坏行。

### 8.4 S4 BG-03：验收矩阵补强

- `MatrixRow` 增加：`goal`（根 business design item 标题）、`executionRecordIds`、`changeCount`、`evidenceRefs`（外部记录/Artifact）、`deviationIds`。
- 查询链：验收标准 → 设计项 → 根业务目标；任务 → `execution_task` → `execution_record` → `execution_change`；偏差按 `target_type/target_id` 关联。
- 偏差 UI：可选择设计项/任务/验收标准（`targetType/targetId` 不再传 null）。
- 测试：矩阵行含目标/证据；偏差目标 UI；无证据时显示“未关联”；权限不变。

## 9. S5：通知、邮件/企微渠道与服务端任务

> 负责人决策：**站内默认启用；邮件/企微完整实现、默认关闭；配置参数后才能启用**。

### 9.1 数据（V10）

| 表 | 关键列 |
|---|---|
| `notification` | id, org_id, recipient_user_id, type, title, body, project_id, object_type, object_id, link, severity, status(unread/read), dedupe_key, created_at, read_at；唯一 `(recipient_user_id, dedupe_key)` |
| `notification_delivery` | id, notification_id, channel(in_app/email/wecom), status(pending/sent/failed/dead/skipped), attempts, last_error, next_attempt_at, sent_at, created_at, updated_at |
| `server_job` | id, type, payload_json, status(queued/running/succeeded/failed/dead), attempts, next_run_at, locked_by, locked_at, idempotency_key UNIQUE, created_by, created_at, updated_at, last_error |

- `notification`/`notification_delivery`/`server_job` 为运营表，默认不进导出（ADR-0008 增补）。
- `agent_call` 已在 V8。

### 9.2 事件与收件人

| 事件 | 收件人 | 渠道 |
|---|---|---|
| `solution.submitted` | 项目 owner + reviewer（排除操作者） | 站内/邮件/企微（若启用） |
| `solution.approved` / `changes_requested` | 方案作者 + 项目 owner（排除操作者） | 同上 |
| `delivery.approved` | 项目成员（排除操作者） | 同上 |
| `knowledge.review_requested` | reviewer | 同上 |
| `import.failed` | 导入人 + 项目 owner | 同上 |
| `job.failed` | org_admin | 站内/邮件（企微可选） |

- 去重键：`type:objectId:version:recipient`；同一对象/版本不重复；无 email 的用户跳过邮件并记录 `skipped_no_address`；操作者不自通知。

### 9.3 渠道实现与配置

**站内（默认启用）**

- `mammoth.notifications.in-app.enabled=true`（默认）。
- API：`GET /api/notifications`、`GET /api/notifications/unread-count`、`POST /api/notifications/{id}/read`、`POST /api/notifications/read-all`；仅本人。
- UI：顶栏铃铛/未读数、列表、已读；项目/对象链接按权限。

**邮件（完整实现、默认关闭）**

- 依赖：`spring-boot-starter-mail`；`JavaMailSender`。
- 配置：
  ```properties
  mammoth.notifications.email.enabled=false
  mammoth.notifications.email.from=
  mammoth.notifications.email.test-recipient=        # 测试发送目标（可为空）
  spring.mail.host=
  spring.mail.port=587
  spring.mail.username=
  spring.mail.password=                              # 环境变量注入，不入库
  spring.mail.properties.mail.smtp.auth=true
  spring.mail.properties.mail.smtp.starttls.enable=true
  ```
- 启用校验：`enabled=true` 时 host/port/from/username/password 必填；缺失则启动/保存失败（可操作错误）；默认关闭时**零外发**。
- 发送：按 `app_user.email`；模板标题/正文；禁止头注入；失败重试/死信；审计 `notification.delivery`。
- 测试：本地 SMTP sink（建议 GreenMail，Apache-2.0，test scope）验证收件人/主题/正文/头注入；默认配置零调用。

**企微机器人（完整实现、默认关闭）**

- 形态：群机器人 webhook（HTTP POST JSON）；具体消息类型/字段/限速/返回码以企业微信官方文档为准，实施时核对并记录。
- 配置：
  ```properties
  mammoth.notifications.wecom.enabled=false
  mammoth.notifications.wecom.webhook-url=           # 环境变量/OS 密钥，视为凭据
  mammoth.notifications.wecom.msg-type=markdown      # text|markdown
  mammoth.notifications.wecom.events=solution.submitted,job.failed
  mammoth.notifications.wecom.rate-limit-per-minute=20
  mammoth.notifications.wecom.test-webhook-url=       # 可选：测试群 webhook；为空则用主 webhook
  ```
- 启用校验：`enabled=true` 时 webhook-url 必填；URL 只从环境/OS 读取，UI 掩码；不写日志/导出。
- 发送：事件白名单；正文截断；限速；重试/死信；检查返回码；失败错误脱敏。
- 测试：JDK `HttpServer` 本地 stub 验证 URL/方法/JSON/返回码/重试/限速；默认配置零调用。

**渠道启停与测试发送**

- `POST /api/admin/notifications/channels/{channel}/test`（org_admin）：邮件发到 `test-recipient`/当前用户；企微发到 `test-webhook-url`（为空则主 webhook）；审计 `notification.test_send`；受同样限速。
- 渠道状态 API：`GET /api/admin/notifications/channels` 返回 enabled/configured/缺失项（不返回秘密）。
- 配置变更走环境/配置文件 + 重启（M4 不做 DB 热配置）；UI 只读展示。

### 9.4 服务端任务

- `@EnableScheduling` 仅在 writer 生效（`@ConditionalOnProperty(mammoth.data-root.read-only=false)`）；MCP 只读进程不跑。
- `JobWorker` 轮询 `server_job`（默认 5s），领取/执行/更新；指数退避、最大次数、死信；`idempotency_key` 防重复。
- 任务类型：`notification.dispatch`、`agent.audit.ingest`、`import.apply`（大文件异步）、`knowledge.staleness_scan`（可选）。
- UI：org_admin 任务列表/失败原因/重试/死信（不显示 payload 秘密）。
- 审计：`job.enqueue`、`job.succeeded`、`job.failed`、`job.dead`、`job.retry`。

### 9.5 测试（S5）

默认配置零外发；启用配置本地 SMTP/HTTP stub 收到消息；重试/退避/死信/幂等；限速；无 email 跳过；渠道停用；收件人权限；MCP 只读不跑任务；审计不含秘密；前端通知/渠道状态。

## 10. S6：团队服务器预案（条件）

- 负责人已明确“服务器与证书暂时不处理，部署在内网甚至开发本机”；S6 **不作为 M4 必交项**。
- 如后续实施：反向代理 TLS、`server.forward-headers-strategy=framework`、只信任配置代理、Secure/HttpOnly/SameSite Cookie、默认绑定 `127.0.0.1`、备份/恢复演练、BG-02 部署/schema/DDL 版本登记。
- 不交付生产证书/域名/OS 服务化；PG 多实例归 M5（ADR-0012）。
- 若 S6 不做，BG-02 随 PG/团队服务器一起进 M5。

## 11. 权限矩阵

| 动作 | org_admin | 项目 owner | editor | reviewer | viewer |
|---|---|---|---|---|---|
| 创建/停用/启用用户、重置口令、改 org 角色 | ✓ | — | — | — | — |
| 用户完整列表 | ✓ | — | — | — | — |
| 用户最小目录 | ✓ | ✓ | ✓ | ✓ | ✓ |
| 客户/产品创建 | ✓ | — | — | — | — |
| 项目创建 | ✓ | ✓ | ✓ | ✓ | ✓ |
| 项目成员管理 | — | ✓ | — | — | — |
| 外部来源管理 | — | ✓ | — | — | — |
| 导入/映射/拒绝 | — | ✓ | ✓ | — | — |
| 交接包下载 | — | ✓ | ✓ | ✓ | ✓ |
| 通知（本人） | ✓ | ✓ | ✓ | ✓ | ✓ |
| 渠道配置/测试发送/任务 | ✓ | — | — | — | — |
| Agent 账号创建 | ✓ | — | — | — | — |
| Agent 账号加入项目（viewer） | — | ✓ | — | — | — |

> `org_admin` 不自动获得项目写权限；项目权限仍按 `project_member` 判定。

## 12. 数据迁移汇总

| 版本 | 切片 | 内容 |
|---|---|---|
| V7 | S1 | `app_user.email/account_type/password_reset_required`、`session.password_reset_required`、`role_assignment` 唯一索引、org_admin 回填 |
| V8 | S2 | `agent_call` |
| V9 | S3 | `external_source`、`import_batch`、`external_record` |
| V10 | S5 | `notification`、`notification_delivery`、`server_job` |

- 每次迁移更新 `MIN_SCHEMA_VERSION`、`MigrationTest`、`AdminTables`（仅权威表）、`admin-drill`。
- SQLite 为主；SQL 保持可移植（TEXT/INTEGER、避免 SQLite 专有函数），为 M5 PG 留余地。

## 13. 测试策略

| 层 | 覆盖 |
|---|---|
| Unit | 权限、用户生命周期、导入解析/映射/幂等、通知收件人、任务重试、模板 |
| Integration | V7–V10 迁移、SQLite、Artifact、会话撤销、MCP journal 入库、SMTP/HTTP stub |
| Contract | CSV/JSON/Jira 夹具、MCP 工具描述、通知模板 |
| E2E | 导入→证据→矩阵；通知→已读；停用→旧会话失效；交接包下载/校验；渠道状态 |
| Security | 跨项目、非 admin、Agent 禁登、zip 无秘密、MCP 只读无任务、默认零外发 |
| Regression | M1–M3 全量 + `m3-acceptance-drill` 18/18 + `admin-drill` 9/9 |

**新增脚本/证据**：`scripts/demo-m4.mjs`、`docs/evidence/M4-VERIFICATION.md`、`docs/M4-ACCEPTANCE.md`；CI 本机先跑，远端结果以 Actions 页面为准。

## 14. DoD 与证据

每个切片必须：规格/AC 明确；代码符合边界；单元/集成/E2E/安全测试通过；错误/空/加载/权限处理；迁移幂等；审计；文档同步；完整 diff 审阅；未完成/风险记录。

阶段证据：`docs/evidence/M4-VERIFICATION.md`（命令/结果/截图）、`docs/M4-ACCEPTANCE.md`（AC 映射）、`docs/process/M4-TIMELINE.md`、`docs/quality/M4-BUGS.md`、交接。

## 15. 风险与限制

| 风险 | 缓解 |
|---|---|
| M4 范围过大 | 切片 + S6 条件 + M4.5 分层 |
| 默认误发邮件/企微 | 双重默认关闭 + 配置校验 + 测试零外发 + 限速/审计 |
| 凭据泄露 | SecretProvider/环境；不入库/日志/导出；掩码；只读账号 |
| MCP 只读审计缺口 | 本地 journal + writer 入库；失败降级 stderr；M4.5 评估网关 |
| SQLite 并发/单写者 | 任务短事务；worker 仅 writer；PG=M5 |
| 新表破坏导出/导入 | 表分类 + AdminTables/MigrationTest/drill + 版本号 |
| Jira 真实格式差异 | 映射 profile + 夹具 + M4.5 真实只读 smoke test |
| CSM 不确定 | 只留 SPI/规划，不猜 API |
| CI 未跑 | 不把本机结果表述为 CI 通过 |

**已知未知**：Jira Cloud/Data Center 与 API 版本；Jira 账号/权限；CSM 端点/产品；SMTP 服务；企微机器人限速/消息类型；负责人启用渠道的时间；真实客户验收授权。

## 16. 参考

- [`M4.5-PHASE45-PLAN.md`](M4.5-PHASE45-PLAN.md)
- [`M4-THREAT-MODEL.md`](M4-THREAT-MODEL.md)
- [`M3.5-BACKLOG.md`](M3.5-BACKLOG.md)
- `decisions/ADR-0011`、`ADR-0012`、`ADR-0013`、`ADR-0006`/`ADR-0008` 增补
- `docs/04`、`docs/07`、`docs/08`、`docs/09`、`docs/12`
