# M4 执行时间线

- **日期**：2026-11-13（Asia/Shanghai）；时间为本地粗粒度记录

## 1. 提交

| 提交 | 内容 |
|---|---|
| S0 提交（见 `git log`） | `docs(m4)`：S0 ADR-0011/0012/0013、ADR-0006/0008 增补、M4/M4.5 规格、威胁模型、文档同步、交接 |

## 2. S0 准入（2026-11-13）

| 阶段 | 内容 | 结果 |
|---|---|---|
| 阅读 | AGENTS、README、M3 交接、Master Guide、04/12/13、03/06/07/08/09、M3 验收、M3.5、M3 计划 §10–§12、ADR-0006/0007/0008/0010、决策日志 | 完成 |
| 源码核对 | 授权/用户/MCP/DataSource/AdminTables/CI/pom；确认无 mail/调度/出站 HTTP、无 PG 目录、`MIN_SCHEMA_VERSION=6`、`AdminTables` 31 表、MCP 无审计、任意登录用户可建用户 | 完成，证据见 M4 规格 §4 |
| 方案 | M4 准入方案 + 10 项拍板清单 | 负责人逐项确认（D-028–D-036） |
| 决策 | ADR-0011（外部集成/凭据）、ADR-0012（服务端/PG=M5）、ADR-0013（通知/外发）；ADR-0006/0008 增补 | **Proposed**，待 S0 评审 |
| 规格 | `docs/specs/M4-PHASE4-PLAN.md`（S1–S6、V7–V10、权限矩阵、导入/通知/Agent 设计、测试） | Draft |
| 规划 | `docs/specs/M4.5-PHASE45-PLAN.md`（Jira 只读 API/CSM/渠道启用/远程 Agent/团队服务器；PG=M5）、`docs/specs/M5-PHASE5-PLAN.md`（PG/多实例/共享 Artifact/迁移/备份/回滚） | Draft |
| 安全 | `docs/specs/M4-THREAT-MODEL.md`（T1–T11 + 测试清单） | Draft |
| 文档同步 | README/ROADMAP/13-OPEN-DECISIONS/DECISION-LOG/架构/领域/安全/知识/工作项/M3.5 | 完成 |
| 代码 | **未写业务代码**（按 S0 门禁） | — |
| 验证 | `git diff --check`、本地链接检查、凭据模式 grep；无代码构建/测试（无代码变更） | 见 `docs/evidence/M4-VERIFICATION.md` §0 |

## 3. 关键判断

- M4 范围过大，按“单节点团队协作 + 只读集成 + 站内通知 + 外部 Agent 只读交付”收敛；生产 API/真实外发/PG 多实例/远程网关进 M4.5/M5（D-028）。
- Jira 先于 CSM：Jira 有官方 API/导出格式；CSM 无端点/产品定义，不能猜（D-030）。
- 负责人 Jira 地址为 HTTP：M4 不调用；M4.5 带凭据必须 HTTPS 或书面风险接受（ADR-0011 §6）。
- 邮件/企微“完整实现但默认关闭”：代码完整、配置校验、测试用本地 SMTP/HTTP stub，默认零外发（D-032/ADR-0013）。
- MCP 进程只读不能写 `audit_log`：采用本地 JSONL + writer 入库（D-033/ADR-0008 增补）。
- PG 多实例明确 M5，不在 M4/M4.5；服务器/证书暂不处理（D-031/ADR-0012）。

## 4. 切片 1（S1，2026-11-13）

| 阶段 | 内容 | 结果 |
|---|---|---|
| 规格 | M4 计划 §6（S1）、ADR-0006 增补（org_admin/用户生命周期/Agent 账号） | 先测试后实现 |
| 数据 | `V7__org_admin_user_lifecycle.sql`：`app_user.email/account_type/password_reset_required`、`session.password_reset_required`、`role_assignment` 唯一索引、最早用户回填 org_admin；`MIN_SCHEMA_VERSION=7` | 迁移幂等/回填测试通过 |
| 后端 | `RoleAssignment`/Repository；`AuthorizationService.isOrgAdmin/requireOrgAdmin`；`UserService` 生命周期；`SessionService.validateSession`；`SecurityConfig` 强制改密白名单；`AuthController` 新字段；`CatalogService` 主数据门禁；`UserController` API | 103 tests |
| 前端 | `ForcePasswordChangePage` + `RequireAuth` 门禁；`SettingsPage` 邮箱/组织成员管理；`MembersPage` 最小目录；`ProjectsPage` 主数据仅 org_admin | build + 25 tests |
| E2E | 新增 M4 S1：管理员建用户→重置口令→临时会话强制改密→新口令登录 | **4 passed（15.1s）** |
| 回归 | `m3-acceptance-drill`、`admin-drill`、`demo-m3` | **18/18、9/9、25/25** |
| 偏差 | `UserService.create` 保留内部/测试 provisioning；orgAdmin 列表 N+1；未做邮件找回/SSO | 见 M4 计划 §6.6 |

## 5. 切片 2（S2，2026-11-13）

| 阶段 | 内容 | 结果 |
|---|---|---|
| 规格 | M4 计划 §7（BG-01/Agent/MCP 审计）、ADR-0008 增补（agent_call/journal） | 先测试后实现 |
| 数据 | `V8__agent_call_audit.sql`：`agent_call` + 索引；`MIN_SCHEMA_VERSION=8`；`AdminTables` 加入 `agent_call` | 迁移/导出/备份测试通过 |
| BG-01 | `HandoffPackageService` + `GET .../handoff/package`；zip（manifest+sha256+方案/任务/验收/基线/README/SECURITY）；秘密扫描；Artifact；`handoff.export`；UI 下载链接 | DeliveryWorkflowTest +2 |
| MCP 审计 | `AgentAuditJournal`（只读进程 JSONL）；`AgentAuditIngestService`（writer 定时入库 `agent_call`+`audit_log`）；`SchedulingConfig` 只读禁用；`DataRootBootstrap` 创建 `audit/` | Journal/Ingest/McpServer 测试 |
| 回归 | 后端 109 tests；前端 25；E2E 4（含 ZIP）；演练 18/18、9/9；演示 25/25 | 全部通过 |
| 偏差 | journal 全量读取 + event_id 去重（大文件需轮转）；无专门审计 UI；zip 不加密 | 见 M4 计划 §17 |

## 6. 下一步

- S2 已完成；进入 **S3**：通用只读导入框架（CSV/JSON、来源/批次/外部记录、幂等、dry-run、审计；V9）。
- S3 前确认：CSV 解析依赖（`commons-csv` vs 内置）、导入映射 profile 形态、导入上限。
- S4 Jira 文件/BG-03、S5 通知/任务、S6 团队服务器按计划推进。
