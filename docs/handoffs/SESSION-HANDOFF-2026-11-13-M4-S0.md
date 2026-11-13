# Session Handoff — 2026-11-13（M4 S0：准入决策与规格）

- **时间**：2026-11-13（Asia/Shanghai）
- **当前分支 / Commit**：`main`，S0 提交（见 `git log -1`；基线 `3231b43`）
- **当前任务**：M4（企业集成与协同）S0 准入——ADR/规格/威胁模型/决策落盘；**尚未写业务代码**
- **会话目的**：让新会话在无历史上下文时独立恢复 M4 S1 及后续切片

## 已确认事实

- M1–M3（含 M3.5）已完成并本地验证：后端 89 tests、前端 build+25 tests、E2E 3 passed、M3 验收演练 18/18、ADR-0008 演练 9/9；CI 远端仍未运行。
- 负责人 2026-11-13 拍板 M4 口径（D-028–D-036）：
  - M4 收敛为**单节点团队协作 + 只读外部集成 + 站内通知 + 外部 Agent 只读交付**；
  - 生产 Jira/CSM API、真实邮件/企微启用、PG 多实例、远程 Agent 网关 → M4.5/M5；
  - 切片顺序 S0→S1→S2→S3→S4→S5→S6；S6（团队服务器）条件项；
  - Jira 地址 `http://119.145.133.146:18009/`（M4 **不调用**；M4.5 只读 API）；
  - CSM 端点未提供，只留 SPI + M4.5 规划；
  - 企微先机器人；邮件/企微**完整实现但默认关闭**，负责人后续配置启用；
  - **PG 多实例 = M5**，不在 M4/M4.5；服务器/证书暂不处理。
- 关键代码事实：授权仅项目级；`role_assignment` 未用；任意登录用户可建用户；无停用/管理员重置；MCP 只读不能写 `audit_log`；无 mail/调度/出站 HTTP；无 PG 目录；`MIN_SCHEMA_VERSION=6`；`AdminTables` 31 表；交接包仍为 Markdown；矩阵缺业务目标/执行证据。

## 已做变更（S0，仅文档）

- **ADR（Proposed）**：`decisions/ADR-0011-EXTERNAL-INTEGRATION.md`、`ADR-0012-TEAM-SERVER.md`、`ADR-0013-NOTIFICATIONS.md`；`ADR-0006` 增补（org_admin/用户生命周期/Agent 账号）、`ADR-0008` 增补（新表分类/Agent 审计/单写者）。
- **规格**：`docs/specs/M4-PHASE4-PLAN.md`（S1–S6、V7–V10、权限矩阵、导入/通知/Agent 设计、测试）、`docs/specs/M4.5-PHASE45-PLAN.md`（Jira 只读/CSM/渠道启用/远程 Agent/团队服务器；PG=M5）、`docs/specs/M4-THREAT-MODEL.md`。
- **同步**：`docs/README.md`、`12-ROADMAP.md`（M4/M4.5/M5）、`13-OPEN-DECISIONS.md`、`decisions/DECISION-LOG.md`（D-028–D-036）、`05-ARCHITECTURE-BASELINE.md`、`03-DOMAIN-MODEL.md`、`08-SECURITY-AND-TRUST.md`、`06-KNOWLEDGE-AND-RAG.md`、`07-WORK-ITEM-LIFECYCLE.md`、`00-MASTER-GUIDE.md`、`specs/M3.5-BACKLOG.md`、`dev-setup.md`（M4 配置预告）、`process/M4-TIMELINE.md`、`evidence/M4-VERIFICATION.md`。
- **未改代码**：无 Java/TS/SQL/前端改动；无迁移。

## 运行过的命令和结果

```powershell
git status --short        # 干净
git log --oneline -5      # 基线 3231b43
# 源码只读核对：glob/grep/read；未运行构建/测试（S0 无代码变更）
```

## 当前 Git 状态

- 分支 `main`；S0 文档提交后应干净并推送 origin；以 `git log -1`/`git status` 为准。

## 未解决问题

- S0 ADR/M4/M4.5/威胁模型**待负责人评审**；评审后 ADR 转 Accepted，进入 S1。
- M4 S1–S6 均未实现；V7–V10 未创建。
- Jira 产品/版本/认证/网络/TLS 未确认；CSM 端点/产品未提供；SMTP/企微配置未提供；服务器/证书暂不处理。
- 真实客户验收未做；CI 远端未运行。

## 尚未验证的假设

- `org_admin` 复用 `role_assignment` + V7 回填在现有数据上可行（需 MigrationTest）；
- 强制改密的 session 标记 + SecurityConfig 白名单不会破坏 M1–M3 路由/E2E；
- MCP JSONL 写入 + writer 入库在 Windows 文件锁/并发下可靠；
- 邮件/企微“完整实现 + 本地 stub”能覆盖真实协议差异（需官方文档核对）；
- Jira 导出 CSV 列名/编码/多行描述在真实环境一致（M4 只用夹具）。

## 下一步最小行动

1. 负责人评审 S0：ADR-0011/0012/0013、ADR-0006/0008 增补、M4/M4.5/威胁模型；确认后转 Accepted。
2. 开 **S1**：先写测试（OrgAdmin/UserLifecycle/PasswordReset/UserDirectory/Migration V7），再实现 V7 + 服务/控制器/前端；保持 M1–M3 全量回归。
3. S1 通过后再开 S2（BG-01 + Agent 账号 + MCP 审计）。

## 新会话必须先阅读

- `AGENTS.md`、`docs/00-MASTER-GUIDE.md`、`docs/README.md`、本交接；
- `docs/specs/M4-PHASE4-PLAN.md`、`docs/specs/M4.5-PHASE45-PLAN.md`、`docs/specs/M4-THREAT-MODEL.md`；
- `decisions/ADR-0011`、`ADR-0012`、`ADR-0013`、`ADR-0006`/`ADR-0008` 增补；
- 代码：`services/domain/src/main/java/com/mammoth/domain/{service/AuthorizationService,service/UserService,web/UserController,security/*,mcp/*,admin/*,config/*}`。

## 风险提醒

- S0 的 ADR 仍是 **Proposed**，未评审前不要按“已 Accepted”实施。
- 不得调用负责人提供的 Jira HTTP 地址；不得把凭据写入 DB/日志/导出/交接包。
- 邮件/企微必须默认关闭；测试只用本地 SMTP/HTTP stub，不得真实外发。
- MCP 只读进程不得写业务表/跑任务/发通知；审计走本地 journal + writer 入库。
- 团队服务器/证书暂不处理；PG 多实例不得提前到 M4/M4.5。
- 不要覆盖未提交改动；不要强推/重置分支。
