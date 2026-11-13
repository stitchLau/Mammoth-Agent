# 13 — 待决策事项登记表

以下事项在有证据/负责人确认前保持 OPEN 或 Proposed。Agent 不得擅自把“建议”转成“已决定”。

| ID | 决策主题 | 当前建议 | 需要验证的内容 | 状态 |
|---|---|---|---|---|
| ADR-0001 | DSH 集成策略 | 优先验证 out-of-tree Bundle + 独立 Domain API | UI、工具、服务、持久化、打包、升级 | **Accepted v1.0**（2026-11-12） |
| ADR-0002 | 技术栈 | 先做技术 Spike，不根据模型偏好定栈 | 团队能力、生态、部署、测试、长期维护 | **Accepted v1.0**（2026-11-12） |
| ADR-0003 | 关系数据库/ORM | 结构化业务状态使用关系库 | 本地单机、多人、迁移、备份与审计 | **Accepted v1.0**（2026-11-12） |
| ADR-0004 | Artifact Storage | 原始文件与派生知识分开 | 本地/共享、容量、版本、权限 | **Accepted v1.0**（2026-11-12） |
| ADR-0005 | RAG 技术 | 抽象检索接口，先全文+向量混合检索 | 中文效果、代码检索、部署和成本 | **Accepted v1.0**（2026-11-12；M1 全文优先，向量延后） |
| ADR-0006 | 身份与授权 | 第一版保留用户/角色/项目授权模型；M4 增补 `org_admin`/用户生命周期/Agent 账号 | 单机身份与未来组织级认证迁移 | **Accepted v1.1**（2026-11-13 M4 S1 落地） |
| ADR-0007 | Git 与知识同步 | 本地只读 Git + 人工捕获基线；不 clone/fetch/push；变更只存路径与统计 | 多仓库、多分支、基线可复现、知识过期检测 | **Accepted v1.0**（2026-11-13） |
| ADR-0008 | 项目工作区 | 单机本地单写者 + 导出/导入包；拒绝网络盘/云同步根；MCP 只读连接 | 多 PC 同步、冲突、权限、离线、备份 | **Accepted v1.0**（2026-11-13） |
| ADR-0009 | 文档解析 | 统一解析管线和原始资料留存 | PDF/Office/图片/音频/会议转录 | OPEN（M2 证据=原始资料+人工摘录） |
| ADR-0010 | 需求上下文基线 | 需求级轻量快照，方案版本绑定快照 ID；未来挂接 ProjectBaseline | 快照可复现性、与 Git 基线兼容 | **Accepted v1.0**（2026-11-12） |
| ADR-0011 | 外部系统集成与凭据 | 只读文件导入 → 只读 API → 双向同步；M4 只做文件导入；外部记录=证据/候选；凭据只走环境/OS 存储 | Jira/CSM API、凭据、白名单、幂等、审计 | **Accepted v1.0（M4 S3/S4 文件导入范围）**；真实 Jira API/CSM 留 M4.5 |
| ADR-0012 | 团队服务端与数据库演进 | 单节点 + 反向代理 TLS + SQLite；**PG 多实例 = M5**；服务器/证书暂不处理；M5 计划见 `docs/specs/M5-PHASE5-PLAN.md` | 多用户、TLS、备份、PG 迁移 | **Proposed**（M4 S6 文档化延期；待 M5） |
| ADR-0013 | 通知与外部发送 | 站内默认启用；邮件/企微完整实现、默认关闭、配置后启用；内部通知边界 | SMTP/企微模板、审批、限速、审计 | **Accepted v1.0（M4 S5）**；真实启用/联调留 M4.5 |

每项决策必须使用 `docs/templates/ADR.md`，写明选项、证据、取舍、风险、验证结果和回滚/替代路径。

**ADR-0002 进度（2026-11-12）**：DSH ↔ Domain API 的 **MCP 边界已实测可行**（Java 17 stdio MCP server + 配置型 bundle，工具 `mcp__mammoth_domain__*` 端到端调用成功），证据见 `docs/spikes/S2-DOMAIN-API-MCP-BOUNDARY.md`；技术栈 **Spike 已完成并产出草案**：建议 Spring Boot 4.1.2 + Java 17 + Maven、独立 SPA、数据根目录可配置（默认 OS 应用数据目录），实测对比见 `docs/spikes/S3-TECH-STACK-SPIKE.md`，草案见 `decisions/ADR-0002-TECH-STACK.md`（**Accepted v1.0**）。

**ADR-0005 进度（2026-11-12）**：检索 Spike 已完成——SQLite FTS5（trigram + 查询三元组 OR + BM25）在 12 文档/10 中文查询上 recall@1 9/10、@3 9/10、约 1.2 ms/查询；hash 向量基线仅 6/10（非语义），hybrid 无增益；scope 过滤 0 泄漏、索引重建 71 ms。关键坑：trigram 整句 MATCH 必然 0 命中，必须做查询 n-gram 化。建议 M1 用全文 + `SearchAdapter` 抽象，向量/混合延后到真实 embedding Spike。证据 `docs/spikes/S7-RETRIEVAL-SPIKE.md`，草案 `decisions/ADR-0005-RETRIEVAL-RAG.md`（**Accepted v1.0**）。

**ADR-0006 进度（2026-11-12）**：身份与授权 Spike 已完成——BCrypt(12) 哈希 214 ms/校验 205 ms（错误口令拒绝），服务端不透明令牌可撤销、撤销后立即失效；10 条项目级 RBAC 用例全部通过（含跨项目、无令牌、角色升级）。建议 BCrypt + 服务端会话 Cookie + 默认拒绝的项目级 RBAC，审计必落库。证据 `docs/spikes/S6-IDENTITY-AUTH-SPIKE.md`，草案 `decisions/ADR-0006-IDENTITY-AUTHORIZATION.md`（**Accepted v1.0**）。

**ADR-0004 进度（2026-11-12）**：Artifact 存储 Spike 已完成——本地内容寻址目录 vs SQLite BLOB 实测（6 文件/4 唯一内容，含 5 MB 与 0 字节）：文件系统写入 37 ms、读取 19 ms、备份 6 ms，且只读属性可阻止误删；BLOB 对应 296/43/22 ms 且无文件级不可变保护。建议文件系统内容寻址 + 关系库元数据，抽象 `ArtifactStore` 便于未来切对象存储。证据 `docs/spikes/S5-ARTIFACT-STORAGE-SPIKE.md`，草案 `decisions/ADR-0004-ARTIFACT-STORAGE.md`（**Accepted v1.0**）。

**ADR-0003 进度（2026-11-12）**：数据库 Spike 已完成——Flyway 13.12.1 对 SQLite 3.54 与 PostgreSQL 17 均支持（迁移幂等）；SQLite FTS5 trigram 与 PG pg_trgm 中文子串检索均可用（各 2 命中/0 ms），PG `tsvector('simple')` 对中文 0 命中；SQLite 文件备份恢复与 PG `pg_dump/pg_restore` 均验证。建议 SQLite（WAL）为 M1 主库、PG 为团队阶段目标。证据 `docs/spikes/S4-DATABASE-SPIKE.md`，草案 `decisions/ADR-0003-DATABASE-MIGRATION.md`（**Accepted v1.0**）。

**ADR-0007 进度（2026-11-13）**：定案为**本地只读 Git + 人工捕获不可变基线**。Mammoth 只执行白名单只读命令（`rev-parse`/`status --porcelain=v2`/`log --format`/`diff --name-status`/`--stat`/`worktree list`/`remote get-url` 脱敏），不 clone/fetch/push/checkout、不写工作树；基线按"项目 + 仓库集合 → commit"保存；基线默认要求干净工作树；代码内容不复制进知识库；知识"过期"只提示、不改状态；DSH 临时 worktree 不得注册（rc.2 归档会清理，S1 F6）。本机已实测只读命令与 `safe.directory`（git 2.50.1）。详见 `decisions/ADR-0007-GIT-BASELINE.md`。**S2–S4 已落地（2026-11-13）**：只读 `LocalGitAdapter`、仓库注册、不可变基线（`V5`）、`ExecutionRecord`/变更/`diverged`、知识过期标记；79 tests + E2E 3 passed + 演示 25/25。

**ADR-0008 进度（2026-11-13）**：定案为**单机本地单写者 + 显式导出/导入包**。数据根必须本地固定盘，拒绝 UNC/网络盘/云同步目录；新增数据根实例锁（M3 实现）；MCP 入口改为**只读连接**（不跑 Flyway、不写库），迁移只由 Web/管理入口执行；跨机仅导出/导入（不含凭据，仅空库导入）；备份用 `VACUUM INTO`/backup API（已实测 WAL 下成功，需专用连接且无进行中语句）。详见 `decisions/ADR-0008-WORKSPACE.md`。**S1 已落地（2026-11-13）**：实例锁 + holder 元数据、UNC/云同步根拒绝、MCP 强制只读；**导出/导入与备份/恢复已补齐（2026-11-13）**：CLI `--admin-export/import/backup/restore`、空库导入、密码锁定、校验和验证；证据 `docs/evidence/M3-VERIFICATION.md` §7–§8，83 tests + 演练 9/9。

**M4 S0 进度（2026-11-13，历史记录）**：ADR-0011（外部集成/凭据）、ADR-0012（服务端/PG=M5）、ADR-0013（通知/外发）已起草为 **Proposed**；ADR-0006 增补（`org_admin`/用户生命周期/Agent 账号）、ADR-0008 增补（新表分类/Agent 审计/单写者）同步起草。M4 计划 `docs/specs/M4-PHASE4-PLAN.md`、M4.5 计划 `docs/specs/M4.5-PHASE45-PLAN.md`、威胁模型 `docs/specs/M4-THREAT-MODEL.md` 已落盘；负责人已确认切片顺序、只读优先、邮件/企微完整实现默认关闭、Jira 地址（M4 不调用）、CSM 延后、PG 多实例=M5。后续 S1–S5 已完成，状态见下方进度。

**M4 S1 进度（2026-11-13）**：负责人“开S1”；ADR-0006 增补转 **Accepted v1.1**。V7 + `org_admin` + 用户停用/启用/管理员重置/强制改密 + Agent 账号 + 建用户/目录收紧 + 主数据 org_admin 门禁已落地；后端 **103 tests**、前端 25、E2E **4 passed**、M3 演练 18/18、ADR-0008 演练 9/9、M3 演示 25/25。ADR-0011/0012/0013 仍 Proposed；下一阶段 S2。

**M4 S2 进度（2026-11-13）**：负责人“开S2”；BG-01 交接包 ZIP（manifest/sha256/秘密扫描/Artifact/`handoff.export`）、专用 Agent 账号（复用 S1）、MCP 本地 journal + writer 入库 `agent_call`/`audit_log`、V8 已落地；后端 **109 tests**、前端 25、E2E **4 passed**（含 ZIP）、M3 演练 18/18、ADR-0008 演练 9/9（schema 8）、M3 演示 25/25。BG-01 关闭；下一阶段 S3。

**M4 S3–S6 进度（2026-11-13）**：S3 落地 V9 只读导入（Commons CSV、UTF-8/GBK、Jira 中文表头、preview 零落库、apply 幂等、证据映射）；S4 补强 BG-03 矩阵；S5 落地 V10 站内通知 + 邮件/企微（默认关闭）+ writer-only 任务；S6 按负责人决定仅文档化延期。后端 **120 tests**、前端 25、E2E **5 passed（19.4s）**、M3 演练 18/18、ADR-0008 9/9（schema 10，35 张导出表）、M3 演示 25/25。**M4 本机验收通过；真实 Jira/CSM/SMTP/企微、服务器/证书、PG 多实例留 M4.5/M5。**

