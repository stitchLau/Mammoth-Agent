# 13 — 待决策事项登记表

以下事项在有证据前保持 OPEN。Agent 不得擅自把“建议”转成“已决定”。

| ID | 决策主题 | 当前建议 | 需要验证的内容 | 状态 |
|---|---|---|---|---|
| ADR-0001 | DSH 集成策略 | 优先验证 out-of-tree Bundle + 独立 Domain API | UI、工具、服务、持久化、打包、升级 | OPEN |
| ADR-0002 | 技术栈 | 先做技术 Spike，不根据模型偏好定栈 | 团队能力、生态、部署、测试、长期维护 | OPEN |
| ADR-0003 | 关系数据库/ORM | 结构化业务状态使用关系库 | 本地单机、多人、迁移、备份与审计 | OPEN |
| ADR-0004 | Artifact Storage | 原始文件与派生知识分开 | 本地/共享、容量、版本、权限 | OPEN |
| ADR-0005 | RAG 技术 | 抽象检索接口，先全文+向量混合检索 | 中文效果、代码检索、部署和成本 | OPEN |
| ADR-0006 | 身份与授权 | 第一版保留用户/角色/项目授权模型 | 单机身份与未来组织级认证迁移 | OPEN |
| ADR-0007 | Git 与知识同步 | Git Commit/Worktree 作为代码基线 | 多仓库、多分支、版本基线与变更映射 | OPEN |
| ADR-0008 | 项目工作区 | 本地执行空间 + 可共享的正式知识空间 | 多 PC 同步、冲突、权限、离线 | OPEN |
| ADR-0009 | 文档解析 | 统一解析管线和原始资料留存 | PDF/Office/图片/音频/会议转录 | OPEN |

每项决策必须使用 `docs/templates/ADR.md`，写明选项、证据、取舍、风险、验证结果和回滚/替代路径。

**ADR-0002 进度（2026-11-12）**：DSH ↔ Domain API 的 **MCP 边界已实测可行**（Java 17 stdio MCP server + 配置型 bundle，工具 `mcp__mammoth__*` 端到端调用成功），证据见 `docs/spikes/S2-DOMAIN-API-MCP-BOUNDARY.md`；技术栈 **Spike 已完成并产出草案**：建议 Spring Boot 4.1.2 + Java 17 + Maven、独立 SPA、数据根目录可配置（默认 OS 应用数据目录），实测对比见 `docs/spikes/S3-TECH-STACK-SPIKE.md`，草案见 `decisions/ADR-0002-TECH-STACK.md`（**Proposed，待批准**）。

**ADR-0004 进度（2026-11-12）**：Artifact 存储 Spike 已完成——本地内容寻址目录 vs SQLite BLOB 实测（6 文件/4 唯一内容，含 5 MB 与 0 字节）：文件系统写入 37 ms、读取 19 ms、备份 6 ms，且只读属性可阻止误删；BLOB 对应 296/43/22 ms 且无文件级不可变保护。建议文件系统内容寻址 + 关系库元数据，抽象 `ArtifactStore` 便于未来切对象存储。证据 `docs/spikes/S5-ARTIFACT-STORAGE-SPIKE.md`，草案 `decisions/ADR-0004-ARTIFACT-STORAGE.md`（**Proposed，待批准**）。

**ADR-0003 进度（2026-11-12）**：数据库 Spike 已完成——Flyway 13.12.1 对 SQLite 3.54 与 PostgreSQL 17 均支持（迁移幂等）；SQLite FTS5 trigram 与 PG pg_trgm 中文子串检索均可用（各 2 命中/0 ms），PG `tsvector('simple')` 对中文 0 命中；SQLite 文件备份恢复与 PG `pg_dump/pg_restore` 均验证。建议 SQLite（WAL）为 M1 主库、PG 为团队阶段目标。证据 `docs/spikes/S4-DATABASE-SPIKE.md`，草案 `decisions/ADR-0003-DATABASE-MIGRATION.md`（**Proposed，待批准**）。

