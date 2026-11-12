# Changelog

## V0.5.0 — 2026-11-12

- **M1 收尾：正式认证（ADR-0006）**：Spring Security + 服务端不透明会话 Cookie（HttpOnly/SameSite=Lax）+ CSRF（`XSRF-TOKEN`/`X-XSRF-TOKEN`，登录与 csrf 端点豁免）+ 登录限速（5 次失败 → 429）+ 注销即时撤销 + 首次账号 bootstrap；移除临时 `X-Mammoth-User-Id` 身份。修复两个真实安全集成缺陷：CSRF 被 `SessionManagementFilter` 轮换清空（调整过滤器顺序）、SPA 需明文 CSRF 请求处理器。
- **M1 T7 审计收尾**：`/api/audit/me`、`/api/projects/{id}/audit`；V3 迁移用 SQLite 触发器禁止 `audit_log` UPDATE/DELETE（不可篡改），并有测试。
- **前端**：登录页 + 会话认证（`credentials: include` + CSRF header）+ 已验证知识检索（引用数/文档 id 展示）；构建与单元测试通过。
- **验收**：`mvn verify` BUILD SUCCESS（**39 tests**）；前端 build + 5 tests；认证版端到端演示 **14/14**（含 CSRF、登录/注销、401/403/409、审计查询）；新增 [`docs/M1-ACCEPTANCE.md`](docs/M1-ACCEPTANCE.md)。

## V0.4.0 — 2026-11-12

- **M1 T9 Agent 只读工具（MCP）**：`java -jar ... --mcp` 独立 stdio 入口，复用 Spring 上下文与领域服务；工具 `search_project_knowledge`、`get_knowledge_document`；强制**仅 verified、仅项目成员、只读**；配置型 DSH bundle 模板与接入指南落地（`integrations/dsh/mammoth-domain-mcp/`、`docs/dsh-mcp-integration.md`）。
  - 修复真实缺陷：MCP 输出曾使用平台编码（GBK），导致 DSH 侧中文乱码 → 强制 UTF-8 输出并加回归测试（`McpServerMainTest`）。
  - DSH 会话实测：bundle `application: applied`，真实调用返回中文与引用结构；验证后已卸载恢复环境。
- **M1 T10 权限隔离**：新增 `PermissionIsolationTest`（跨项目不泄漏、viewer 不可写、草稿/他项目不出现在检索、非成员全表面拒绝）；补齐知识创建的服务层角色校验（controller + service 双重防护）。
- **M1 T11 演示**：新增 `scripts/demo-m1.mjs` 与 `docs/demos/M1-DEMO.md`；实跑 **14/14 步骤通过**，审计 9 类动作落库。
- 验证：`mvn verify` BUILD SUCCESS（**35 tests**）；演示脚本可重复执行。

## V0.3.4 — 2026-11-12

- **M1 T6 知识文档**：`draft → needs_review → verified` 状态机；`source_refs` 必填且必须引用本项目 artifact；**四眼原则**（创建者不可审核自己的文档）；审核需 owner/reviewer；chunk 派生 + FTS5 触发器索引；审计 `knowledge.create/submit_review/verify`。
- **M1 T8 检索**：`KnowledgeSearchAdapter` + SQLite FTS5 trigram（查询三元组 OR + BM25），仅返回 verified 且限定项目 scope；引用结构含 document/chunk/ordinal/text/来源数；V2 迁移（外部内容 FTS + 三个同步触发器）。
- 验证：`mvn verify` BUILD SUCCESS（**24 tests**）；HTTP 冒烟：创建→draft、复审前 verify→**409**、提交后 needs_review、创建者自审→**403**、reviewer 审核→verified、检索命中 1 条且排除草稿、越权→**403**；迁移 1→2 已应用。

## V0.3.3 — 2026-11-12

- **M1 T5 Artifact 导入**：
  - `ArtifactStore` 接口 + `LocalArtifactStore`（内容寻址 `artifacts/<sha[0:2]>/<sha[2:4]>/<sha>`、临时文件→原子移动→只读、读时 SHA-256 校验、跨项目去重、平台无关的只读降级）。
  - `ArtifactRecord` + 上传/列表/元数据/下载 REST（multipart，50MB 上限；owner/editor 可写，任意成员可读）。
  - 审计：`artifact.add` / `artifact.reuse`。
- 前端接上最小数据流：临时身份头 + 项目列表 + 资料列表 + 文件上传（错误/加载/空状态）。
- 验证：后端 `mvn verify` BUILD SUCCESS（**20 tests**）；前端 `npm run build` 与 `npm test`（5 tests）通过。

## V0.3.2 — 2026-11-12

- **M1 T4 主数据与项目 + 授权骨架**：
  - JPA 实体/仓储（organization/app_user/customer/product/project/project_member/audit_log）+ `hibernate-community-dialects`（SQLite）。
  - REST：`/api/users`、`/api/customers`、`/api/products`、`/api/projects`、`/api/projects/{id}/members`；项目必须绑定客户、编码唯一并规范化大写、创建者自动成为 owner。
  - `AuthorizationService`（默认拒绝、项目角色 owner/editor/reviewer/viewer）、`AuditService`（追加写）、`ApiException`→RFC 9457 错误映射；密码 BCrypt(12)。
- 验证：`mvn verify` BUILD SUCCESS（**14 tests**）；HTTP 冒烟：无身份 401、非成员 403、重复编码 409、成员可见项目 1 个、审计 `user.create=2/customer.create=1/project.create=1`。

## V0.3.1 — 2026-11-12

- **M1 T2 数据根自检**：`DataRootBootstrap` 启动时创建 `db/artifacts/cache` 并做写读探测；禁止路径（`~/.dsh`、源码仓库）启动即失败；新增 `DataRootBootstrapTest`。
- **M1 T3 迁移基线**：接入 Flyway 13.12.1 + SQLite（WAL、外键、busy timeout）；`V1__m1_baseline.sql` 建 14 张 M1 基线表（organization/app_user/role_assignment/customer/product/project/project_product/project_member/artifact/knowledge_document/knowledge_source_ref/knowledge_chunk/audit_log/session）；`MigrationTest` 验证迁移与幂等。
- 明确 Flyway bean（Spring Boot 4 自动配置已拆分，显式声明保证版本固定与启动前迁移）；`/api/meta` 增加 `database` 与 `schemaVersion`。
- 验证：`mvn verify` BUILD SUCCESS（10 tests）；jar 冒烟 `schemaVersion=1`、15 张表（含 history）、Flyway `executed=1`。

## V0.3.0 — 2026-11-12

- **ADR 批准**：ADR-0002（Spring Boot 4.1.2 + Java 17 + Maven、独立 SPA、可配置数据根目录）、ADR-0003（SQLite+Flyway 主库，PG 为团队阶段目标）、ADR-0004（内容寻址 Artifact 存储）、ADR-0005（M1 全文检索 + SearchAdapter）、ADR-0006（BCrypt + 服务端会话 + 项目级 RBAC）全部置为 **Accepted v1.0**。
- **M1 T1 工程骨架落地**：
  - `services/domain/`：Spring Boot 4.1.2 Domain Service（`/api/health`、`/api/meta`、数据根目录解析与禁止路径 fail-fast、6 个测试）。
  - `apps/web/`：Vite 7 + React 19 + TypeScript SPA 骨架（健康检查页面、加载/空/错误状态、3 个单元测试）。
  - `docs/dev-setup.md`：本地启动、配置与数据根目录规则。
  - `.env.example`、`.github/workflows/ci.yml`（后端 `mvn verify`、前端 `npm ci/build/test`）。
- 验证：后端 `mvn verify` BUILD SUCCESS（6 tests）+ jar 冒烟 `/api/health` 200；前端 `npm install/build/test` 通过（103 packages，3 tests）。远端 CI 运行待推送后确认。

## V0.2.6 — 2026-11-12

- 新增 `docs/spikes/S7-RETRIEVAL-SPIKE.md`：12 文档/10 中文查询的检索对比（FTS5 trigram+BM25 recall@1 9/10 vs hash 向量基线 6/10 vs hybrid 9/10；scope 0 泄漏；重建 71 ms）；关键坑：trigram 整句 MATCH 为 0 命中。
- 新增 `decisions/ADR-0005-RETRIEVAL-RAG.md`（Proposed）：M1 全文检索 + `SearchAdapter` 抽象；向量/混合延后到真实 embedding Spike。
- 新增 `spike/adr-0005-retrieval/` 一次性探针。
- 更新 `docs/13-OPEN-DECISIONS.md`、`docs/specs/M1-PHASE1-PLAN.md`、`docs/handoffs/`。

## V0.2.5 — 2026-11-12

- 新增 `docs/spikes/S6-IDENTITY-AUTH-SPIKE.md`：PBKDF2 vs BCrypt 成本（226/56 ms vs 214/205 ms）、服务端不透明会话令牌（可撤销）vs HMAC（58 µs 校验）、10 条项目级 RBAC 用例全通过。
- 新增 `decisions/ADR-0006-IDENTITY-AUTHORIZATION.md`（Proposed）：BCrypt + 服务端会话 Cookie + 默认拒绝的项目级 RBAC；首次启动 setup；审计必落库；OIDC 迁移路径。
- 新增 `spike/adr-0006-auth/` 一次性探针。
- 更新 `docs/13-OPEN-DECISIONS.md`、`docs/specs/M1-PHASE1-PLAN.md`、`docs/handoffs/`。

## V0.2.4 — 2026-11-12

- 新增 `docs/spikes/S5-ARTIFACT-STORAGE-SPIKE.md`：内容寻址目录 vs SQLite BLOB 实测（写 37/296 ms、读 19/43 ms、备份 6/22 ms；只读属性阻止删除；去重与哈希校验）。
- 新增 `decisions/ADR-0004-ARTIFACT-STORAGE.md`（Proposed）：本地内容寻址 + 关系库元数据；`ArtifactStore` 接口；备份/删除/迁移规则。
- 新增 `spike/adr-0004-artifact/` 一次性探针。
- 更新 `docs/13-OPEN-DECISIONS.md`、`docs/specs/M1-PHASE1-PLAN.md`、`docs/handoffs/`。

## V0.2.3 — 2026-11-12

- 新增 `docs/spikes/S4-DATABASE-SPIKE.md`：SQLite 3.54 vs PostgreSQL 17 实测（Flyway 13.12.1 双库迁移幂等、中文检索 FTS5 trigram vs pg_trgm、`tsvector('simple')` 对中文 0 命中、备份恢复）。
- 新增 `decisions/ADR-0003-DATABASE-MIGRATION.md`（Proposed）：建议 SQLite（WAL）+ Flyway 为 M1 主库，PostgreSQL 为团队阶段目标；访问层 JPA + 独立 SearchAdapter；双方言迁移目录。
- 新增 `spike/adr-0003-db/` 一次性探针（Flyway/JDBC/中文检索/备份恢复）。
- 更新 `docs/13-OPEN-DECISIONS.md`、`docs/specs/M1-PHASE1-PLAN.md`、`docs/handoffs/`。

## V0.2.2 — 2026-11-12

- 新增 `docs/spikes/S3-TECH-STACK-SPIKE.md`：ADR-0002 技术栈 Spike 实测（纯 JDK 279 ms/53 MB vs Spring Boot 4.1.2 2.4 s/204 MB，REST + MCP 同工程验证）。
- 新增 `decisions/ADR-0002-TECH-STACK.md`（Proposed）：建议 Spring Boot 4.1.2 + Java 17 + Maven、独立 SPA（由后端托管）、数据根目录可配置且禁止安装目录/`~/.dsh`/源码目录。
- 新增 `spike/adr-0002-stack/` 一次性探针（pure-jdk、spring-boot）。
- 更新 `docs/13-OPEN-DECISIONS.md` ADR-0002 进度、`docs/handoffs/` 下一步行动与 `.gitignore`（`target/`、`out/`）。

## V0.2.1 — 2026-11-12

- 新增 `docs/spikes/`：S1 DSH 扩展能力 Spike（含 ADR-0001 v1.0 与实测发现 F1–F7、S1b/S1c）与 S2 MCP 边界 Spike。
- 新增 `docs/handoffs/`：M0 会话交接记录；新增 `docs/specs/`：M1 Phase 1 计划。
- 新增 `spike/` 验证夹具：`spike/dsh-extension`（DSH bundle）、`spike/mcp-java`（Java MCP server + 配置型 bundle）。
- 新增 `.gitignore`（M0 工程基线）。
- 明确 DSH 宿主安全边界：`AGENTS.md` 新增安全边界章节，`docs/08-SECURITY-AND-TRUST.md` 新增 §8（凭据禁止读取、`~/.dsh` 不进 RAG、插件变更管控）。
- `decisions/ADR-0001-DSH-INTEGRATION.md`：状态 Proposed → **Accepted v1.0**，新增 §6 决策结果、工程约束与未验证边界。
- `docs/12-ROADMAP.md`：M0 标记为已完成并列出证据。
- 已知遗留：`manifest.json` 仍为 V0.2 资料包清单，尚未覆盖新增目录（后续改为脚本生成）。

## V0.2 — 2026-11-12

- 新增 `docs/agents/`，提供 Product Analyst、Research Analyst、Architecture Reviewer、Implementation Agent、Test Designer、Independent Reviewer 六种可复用角色定义。
- 新增 `AGENT-ORCHESTRATION.md`，规定 Lead/SubAgent 的职责、Fresh/Fork 选择、任务包、并行写入隔离、AgentTeam 使用条件和冲突处理。
- 新增 `AGENT-USAGE-QUICKSTART.md`，提供 DSH 实际使用速查和可复制指令。
- 新增 SubAgent 委派与 AgentTeam 规划提示词。
- 新增 SubAgent/AgentTeam 使用检查清单。
- 更新 `AGENTS.md`、Master Guide、Vibe Coding Playbook 和 DSH Guide，明确 Role Markdown 不会自动注册为常驻 Agent。
- 修正 AgentTeam 相关说明：共享任务板不提供工作区隔离；Team 成员共享 checkout，AgentTeam 默认关闭/按 Session 启用的具体行为须以当前版本为准。
- 保留 V0.1 的产品定位、领域模型、完整产品边界和分阶段交付原则。

## V0.1 — 2026-11-12

- 初始产品定义、架构基线、工作模板、提示词和质量检查清单。
