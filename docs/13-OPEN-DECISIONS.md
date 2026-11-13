# 13 — 待决策事项登记表

以下事项在有证据前保持 OPEN。Agent 不得擅自把“建议”转成“已决定”。

| ID | 决策主题 | 当前建议 | 需要验证的内容 | 状态 |
|---|---|---|---|---|
| ADR-0001 | DSH 集成策略 | 优先验证 out-of-tree Bundle + 独立 Domain API | UI、工具、服务、持久化、打包、升级 | **Accepted v1.0**（2026-11-12） |
| ADR-0002 | 技术栈 | 先做技术 Spike，不根据模型偏好定栈 | 团队能力、生态、部署、测试、长期维护 | **Accepted v1.0**（2026-11-12） |
| ADR-0003 | 关系数据库/ORM | 结构化业务状态使用关系库 | 本地单机、多人、迁移、备份与审计 | **Accepted v1.0**（2026-11-12） |
| ADR-0004 | Artifact Storage | 原始文件与派生知识分开 | 本地/共享、容量、版本、权限 | **Accepted v1.0**（2026-11-12） |
| ADR-0005 | RAG 技术 | 抽象检索接口，先全文+向量混合检索 | 中文效果、代码检索、部署和成本 | **Accepted v1.0**（2026-11-12；M1 全文优先，向量延后） |
| ADR-0006 | 身份与授权 | 第一版保留用户/角色/项目授权模型 | 单机身份与未来组织级认证迁移 | **Accepted v1.0**（2026-11-12） |
| ADR-0007 | Git 与知识同步 | 本地只读 Git + 人工捕获基线；不 clone/fetch/push；变更只存路径与统计 | 多仓库、多分支、基线可复现、知识过期检测 | **Accepted v1.0**（2026-11-13） |
| ADR-0008 | 项目工作区 | 单机本地单写者 + 导出/导入包；拒绝网络盘/云同步根；MCP 只读连接 | 多 PC 同步、冲突、权限、离线、备份 | **Accepted v1.0**（2026-11-13） |
| ADR-0009 | 文档解析 | 统一解析管线和原始资料留存 | PDF/Office/图片/音频/会议转录 | OPEN（M2 证据=原始资料+人工摘录） |
| ADR-0010 | 需求上下文基线 | 需求级轻量快照，方案版本绑定快照 ID；未来挂接 ProjectBaseline | 快照可复现性、与 Git 基线兼容 | **Accepted v1.0**（2026-11-12） |

每项决策必须使用 `docs/templates/ADR.md`，写明选项、证据、取舍、风险、验证结果和回滚/替代路径。

**ADR-0002 进度（2026-11-12）**：DSH ↔ Domain API 的 **MCP 边界已实测可行**（Java 17 stdio MCP server + 配置型 bundle，工具 `mcp__mammoth_domain__*` 端到端调用成功），证据见 `docs/spikes/S2-DOMAIN-API-MCP-BOUNDARY.md`；技术栈 **Spike 已完成并产出草案**：建议 Spring Boot 4.1.2 + Java 17 + Maven、独立 SPA、数据根目录可配置（默认 OS 应用数据目录），实测对比见 `docs/spikes/S3-TECH-STACK-SPIKE.md`，草案见 `decisions/ADR-0002-TECH-STACK.md`（**Accepted v1.0**）。

**ADR-0005 进度（2026-11-12）**：检索 Spike 已完成——SQLite FTS5（trigram + 查询三元组 OR + BM25）在 12 文档/10 中文查询上 recall@1 9/10、@3 9/10、约 1.2 ms/查询；hash 向量基线仅 6/10（非语义），hybrid 无增益；scope 过滤 0 泄漏、索引重建 71 ms。关键坑：trigram 整句 MATCH 必然 0 命中，必须做查询 n-gram 化。建议 M1 用全文 + `SearchAdapter` 抽象，向量/混合延后到真实 embedding Spike。证据 `docs/spikes/S7-RETRIEVAL-SPIKE.md`，草案 `decisions/ADR-0005-RETRIEVAL-RAG.md`（**Accepted v1.0**）。

**ADR-0006 进度（2026-11-12）**：身份与授权 Spike 已完成——BCrypt(12) 哈希 214 ms/校验 205 ms（错误口令拒绝），服务端不透明令牌可撤销、撤销后立即失效；10 条项目级 RBAC 用例全部通过（含跨项目、无令牌、角色升级）。建议 BCrypt + 服务端会话 Cookie + 默认拒绝的项目级 RBAC，审计必落库。证据 `docs/spikes/S6-IDENTITY-AUTH-SPIKE.md`，草案 `decisions/ADR-0006-IDENTITY-AUTHORIZATION.md`（**Accepted v1.0**）。

**ADR-0004 进度（2026-11-12）**：Artifact 存储 Spike 已完成——本地内容寻址目录 vs SQLite BLOB 实测（6 文件/4 唯一内容，含 5 MB 与 0 字节）：文件系统写入 37 ms、读取 19 ms、备份 6 ms，且只读属性可阻止误删；BLOB 对应 296/43/22 ms 且无文件级不可变保护。建议文件系统内容寻址 + 关系库元数据，抽象 `ArtifactStore` 便于未来切对象存储。证据 `docs/spikes/S5-ARTIFACT-STORAGE-SPIKE.md`，草案 `decisions/ADR-0004-ARTIFACT-STORAGE.md`（**Accepted v1.0**）。

**ADR-0003 进度（2026-11-12）**：数据库 Spike 已完成——Flyway 13.12.1 对 SQLite 3.54 与 PostgreSQL 17 均支持（迁移幂等）；SQLite FTS5 trigram 与 PG pg_trgm 中文子串检索均可用（各 2 命中/0 ms），PG `tsvector('simple')` 对中文 0 命中；SQLite 文件备份恢复与 PG `pg_dump/pg_restore` 均验证。建议 SQLite（WAL）为 M1 主库、PG 为团队阶段目标。证据 `docs/spikes/S4-DATABASE-SPIKE.md`，草案 `decisions/ADR-0003-DATABASE-MIGRATION.md`（**Accepted v1.0**）。

**ADR-0007 进度（2026-11-13）**：定案为**本地只读 Git + 人工捕获不可变基线**。Mammoth 只执行白名单只读命令（`rev-parse`/`status --porcelain=v2`/`log --format`/`diff --name-status`/`--stat`/`worktree list`/`remote get-url` 脱敏），不 clone/fetch/push/checkout、不写工作树；基线按"项目 + 仓库集合 → commit"保存；基线默认要求干净工作树；代码内容不复制进知识库；知识"过期"只提示、不改状态；DSH 临时 worktree 不得注册（rc.2 归档会清理，S1 F6）。本机已实测只读命令与 `safe.directory`（git 2.50.1）。详见 `decisions/ADR-0007-GIT-BASELINE.md`。**S2–S4 已落地（2026-11-13）**：只读 `LocalGitAdapter`、仓库注册、不可变基线（`V5`）、`ExecutionRecord`/变更/`diverged`、知识过期标记；79 tests + E2E 3 passed + 演示 25/25。

**ADR-0008 进度（2026-11-13）**：定案为**单机本地单写者 + 显式导出/导入包**。数据根必须本地固定盘，拒绝 UNC/网络盘/云同步目录；新增数据根实例锁（M3 实现）；MCP 入口改为**只读连接**（不跑 Flyway、不写库），迁移只由 Web/管理入口执行；跨机仅导出/导入（不含凭据，仅空库导入）；备份用 `VACUUM INTO`/backup API（已实测 WAL 下成功，需专用连接且无进行中语句）。详见 `decisions/ADR-0008-WORKSPACE.md`。**S1 已落地（2026-11-13）**：实例锁 + holder 元数据、UNC/云同步根拒绝、MCP 强制只读；**导出/导入与备份/恢复已补齐（2026-11-13）**：CLI `--admin-export/import/backup/restore`、空库导入、密码锁定、校验和验证；证据 `docs/evidence/M3-VERIFICATION.md` §7–§8，83 tests + 演练 9/9。

