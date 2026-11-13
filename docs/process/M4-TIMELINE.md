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

## 4. 下一步

- 负责人评审 S0 的 ADR/M4/M4.5/威胁模型；确认后 ADR 转 Accepted。
- 进入 **S1**：V7 + `org_admin` + 用户停用/重置/会话撤销 + 建用户/目录收紧 + 强制改密；先写测试再实现。
- S1 完成后按 S2→S5 推进；S6 视负责人是否要团队服务器。
