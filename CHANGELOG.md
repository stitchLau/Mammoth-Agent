# Changelog

## V0.11.1 — 2026-11-13

- **ADR-0008 补齐（导出/导入与备份/恢复）**：
  - `--admin-export`：逻辑导出包（每表 SQL + Artifact 字节 + `manifest.properties`），密码哈希替换为锁定值，**不含会话与凭据**。
  - `--admin-import`：仅空库；校验 schema/逐条目 sha256/行数/Artifact 大小；为指定管理员设置新口令（其余账号锁定）；单事务导入并审计。
  - `--admin-backup`：`VACUUM INTO` + Artifact 复制 + 哈希/schema manifest（写实例停止后执行）。
  - `--admin-restore`：纯文件恢复，校验 db sha256 与 `integrity_check`、schema、每个 Artifact sha256；目标必须为空目录。
  - 新增 `AdminPackageTest`（4 tests）与 `scripts/admin-drill.mjs`（真实 CLI 演练 9/9）。
- **验证**：后端 `mvn verify` → **83 tests**；演练覆盖 种子→导出→导入→启动校验→备份→恢复→启动校验→非空拒绝。
- **限制**：导出不加密；导入不合并/不增量；非管理员账号锁定（无密码重置）；实例锁清理脚本仍未自动化。
- **文档**：ADR-0008 补齐记录、M3 规格 §11、验证证据 §8、时间线 §6、缺陷 B-M3-009、`dev-setup` CLI 说明、`.env.example`、交接更新。

## V0.11.0 — 2026-11-13

- **M3 完成（实施回收与知识闭环）**：
  - **Git 基线（ADR-0007）**：只读 `LocalGitAdapter`（白名单命令、10 s 超时、1 MiB 输出上限、UTF-8 中文路径、精确 `safe.directory`、远程 URL 脱敏）、项目仓库注册、不可变基线（`project_repository`/`project_baseline`/`project_baseline_repo`，`V5`）、脏树拒绝与 owner 覆盖。
  - **交接包**：基于批准方案生成业务/技术渲染 + 任务 + 验收 + 基线/上下文快照的 Markdown 交接包（无批准方案 409）。
  - **实施回收**：`ExecutionRecord`（from/to commit、变更清单、diverged）、任务完成标记、人工偏差分类。
  - **验收矩阵**：以批准方案验收标准为行，按任务完成情况给出 covered/partial/unverified 与未完成任务候选。
  - **交付快照与知识闭环**：As-built 快照（四眼批准、批准时落 Artifact、不可覆盖）、知识更新提议（接受生成知识草稿，仍需四眼审核发布）、知识"可能过期"提示（绑定基线，不改状态）。
  - **UI**：代码基线页、交付闭环页、知识过期标记、导航与路由。
- **验证**：后端 `mvn verify` **79 tests**；前端 build + **25 tests**；E2E **3 passed（12.8s）**；演示 `demo-m3.mjs` **25/25**。
- **文档**：M3 规格实施记录、验证证据、时间线、缺陷台账、ADR-0007 落地记录、M3 演示说明、交接；README/ROADMAP/13-OPEN-DECISIONS 同步。
- **已知限制**：Git 仅文件级映射；重命名统计近似；子模块不递归；ADR-0008 的导出/导入、锁清理脚本、备份演练仍在后续范围；CI 远端未运行。

## V0.10.0 — 2026-11-13

- **M3 切片 1（运行边界加固，ADR-0008）**：
  - **数据根实例锁**：写实例独占 `<数据根>/.mammoth-instance.lock`，持有者元数据写入旁路 `.mammoth-instance-holder`；第二个写实例启动失败并显示 pid/host/startedAt 与清理指引。
  - **数据根位置校验**：拒绝 UNC 路径、已知云同步目录（OneDrive/Dropbox/Google Drive/iCloud 等）、已知远端文件系统类型（SMB/CIFS/NFS/AFPFS/WebDAV/FUSE-SSHFS）；`mammoth.data-root.allow-unsupported`（`MAMMOTH_ALLOW_UNSUPPORTED_ROOT`）仅限受控覆盖。
  - **MCP 强制只读**：`--mcp` 不取写锁、不创建目录、不执行 Flyway、以只读连接 + `PRAGMA query_only=ON` 打开数据库；拒绝 `--mammoth.data-root.read-only=false`；未初始化数据根给出可操作错误（先启动 Web/管理入口）。
  - **迁移职责**：Flyway 仅由写入口执行（`OrganizationService` 改为 `ObjectProvider<Flyway>`，只读模式不写默认组织）。
- **验证**：后端 `mvn verify` **71 tests**（M2 基线 62 + 新增 9）；E2E 2 passed（9.2s）；手动集成 5/5（双写拒绝、UNC/OneDrive 拒绝、MCP 与写实例并存、空根 fail fast）。
- **文档**：`docs/specs/M3-PHASE3-PLAN.md`、`docs/evidence/M3-VERIFICATION.md`、`docs/process/M3-TIMELINE.md`、`docs/quality/M3-BUGS.md`、交接、`dev-setup` 运行模式章节、ADR-0008 S1 记录、README/ROADMAP/13-OPEN-DECISIONS 更新。
- **已知限制**：Windows 映射网络盘可能报告 NTFS（JVM 无法可靠识别）；导出/导入、Git 基线、实例锁清理脚本仍在后续切片；受限沙箱下 Playwright 需提权。

## V0.9.1 — 2026-11-13

- **ADR-0007 定案（Accepted v1.0）**：本地只读 Git + 人工捕获不可变代码基线；白名单只读命令，不 clone/fetch/push/checkout、不写工作树；多仓库基线 = commit 集合；脏树默认拒绝；代码内容不进知识库；知识过期只提示不改状态；DSH 临时 worktree 不得作为基线。见 `decisions/ADR-0007-GIT-BASELINE.md`。
- **ADR-0008 定案（Accepted v1.0）**：单机本地单写者 + 显式导出/导入包；数据根必须本地固定盘，拒绝 UNC/网络盘/云同步目录；数据根实例锁；MCP 入口改只读连接且不执行 Flyway；跨机导出/导入不含凭据、仅空库导入；备份用 `VACUUM INTO`/backup API。见 `decisions/ADR-0008-WORKSPACE.md`。
- **证据（2026-11-13 本机探针）**：git 2.50.1 只读命令可用且不改工作树；`safe.directory` 精确路径生效；SQLite `VACUUM INTO`（WAL 下）成功、副本读回 1 行，并复现"statements in progress"失败模式；只读连接与写连接并存可读、写入被拒；数据根默认在本地固定盘（DriveType=3/NTFS）。
- **文档**：`docs/13-OPEN-DECISIONS.md` 状态更新；`docs/decisions/DECISION-LOG.md` D-018–D-020；`docs/05-ARCHITECTURE-BASELINE.md` 决策状态；`docs/12-ROADMAP.md` M3 前置与切片建议；M2 交接补充 2026-11-13 更新。
- **注意**：实例锁、网络盘拒绝、MCP 只读连接、导出/导入、`GitAdapter`/基线表均**尚未实现**，仅完成决策，列入 M3。

## V0.9.0 — 2026-11-12

- **M2 需求与方案（第一轮完整闭环）**：
  - 需求工作项（`REQ-000n`）+ 多批次证据只追加（可引用 Artifact）+ 11 维就绪检查（关键维度阻断、非关键维度需"假设 + 决策责任人"）+ 负责人确认门槛与不可变上下文快照（ADR-0010）。
  - 统一 SolutionModel（业务/技术/共享设计项，支持混合层级）→ 业务/技术双视图由同一模型渲染；6 条一致性规则阻断评审；任务拆解 + 验收标准 + 追溯矩阵。
  - 方案评审与版本：提交评审、四眼批准（作者不可自批）、要求修改、批准版本不可覆盖、`new-version` 复制结构并将旧批准版本置为 `superseded`。
  - MCP 只读工具 `get_requirement_context`（证据/评估/批准方案摘要，无写路径）。
  - UI：需求池、需求详情（证据/就绪面板/方案版本）、方案编辑与双视图/一致性面板/评审记录。
- **数据**：`V4__m2_requirements.sql`（9 表：work_item、requirement_evidence/assessment/snapshot、solution_version/design_item/task、acceptance_criterion、approval_record）。
- **验证**：后端 `mvn verify` **62 tests**；前端 build + **25 tests**；Playwright E2E **2 passed**；API 演示 `demo-m2.mjs` **26/26**。
- **文档**：M2 计划、ADR-0010、验证证据、时间线、缺陷台账、演示说明、交接；README/ROADMAP/13-OPEN-DECISIONS/DECISION-LOG 同步。
- **已知限制**：CI 远端未运行；不接应用内 LLM（ADR-0011 待立）；文档解析（ADR-0009）与 Git 基线（ADR-0007）未定；TLS/PG 方言/后端分页未做。

## V0.8.2 — 2026-11-12

- 新增 M2 新会话启动指令 `docs/prompts/09-M2-KICKOFF.md`（背景、必读文档、工作规则、首步方案要求、验证命令、环境备注），并在 `docs/README.md` 索引中引用。

## V0.8.1 — 2026-11-12

- **修复**：路由重构时 `main.tsx` 遗漏 `import './styles.css'`，导致整站无样式（截图评审发现）；恢复导入并重跑构建/E2E/截图。
- **资料归档补齐**（M1/M1.5 过程资料）：
  - 新增 `docs/process/M1-TIMELINE.md`（提交级执行时间线）、`docs/decisions/DECISION-LOG.md`（负责人确认的决策与待决项）、`docs/quality/M1-BUGS.md`（缺陷台账）、`docs/evidence/M1-VERIFICATION.md` + `docs/evidence/screenshots/`（6 张真实 UI 截图与复现脚本）、`docs/README.md`（文档索引）；
  - 新增当前交接 `docs/handoffs/SESSION-HANDOFF-2026-11-12-M1.5.md`，旧 M0 交接标记为历史快照；
  - 修正 `docs/13-OPEN-DECISIONS.md` 中 ADR 进度脚注与状态表矛盾（Proposed → Accepted），更新 UI-SPEC 待决项为“已确认”。

## V0.8.0 — 2026-11-12

- **M1.5 收尾**：账号设置（显示名、修改密码并撤销全部会话）、成员角色变更/移除（保护最后一名 owner、owner-only、审计）、成员页新建用户并加入、资料/知识/成员列表客户端分页（10 条/页）。
- **后端新增**：`PUT /api/auth/profile`、`PUT /api/auth/password`、`PATCH/DELETE /api/projects/{id}/members/{userId}`；服务层纵深校验（owner-only、最后 owner 保护）；新增 `AccountSettingsTest`、`MemberManagementTest`。
- **CI**：新增 Playwright 浏览器 E2E job（构建 jar + web、启动后端、运行 `npm run test:e2e`、失败上传报告）。**远端 Runner 尚未实际执行**。
- **E2E 扩展**：覆盖改显示名 + 改密后重新登录、reviewer 审核与检索引用。
- **修复**：改密后登出 401 导致登录提示丢失（signOut best-effort + 会话上下文提示）；E2E 选择器歧义。
- **验证**：后端 `mvn verify` **48 tests**；前端 build + **16 tests**；Playwright E2E **1 passed（4.5s）**；API 演示 14/14。

## V0.7.0 — 2026-11-12

- **M1.5 UI 产品化（路线 A）**：按 `docs/specs/UI-SPEC.md` 重构为路由化产品壳：react-router + AppShell + 项目上下文侧栏导航；新增首次引导页（空库创建管理员并自动登录）、成员与角色页面（owner 添加成员）、知识详情/草稿编辑/提交审核/审核通过（含"不可自审"禁用原因）、统一状态组件与权限感知 UI。
- **后端支持**：`GET /api/setup/status`；`PUT /api/projects/{id}/knowledge/{docId}`（仅草稿可编辑、引用替换、重新分块、审计 `knowledge.update`）；SPA 深链接回退（`/projects/**` 等返回 index.html）并放行对应安全路径。
- **修复**：SPA 深链接被安全过滤器 401 拦截；多文件上传因清空 input 导致 `FileList` 丢失（E2E 发现）。
- **验证**：后端 `mvn verify` **44 tests**；前端 build + **13 tests**；Playwright 浏览器 E2E **1 passed**（setup→项目→资料→知识→审核→检索）；API 演示 14/14 不回归。

## V0.6.0 — 2026-11-12

- 新增 [`docs/specs/UI-SPEC.md`](docs/specs/UI-SPEC.md)（草案 v0.1）：确认当前 UI 定位为**正式产品 UI 基座（路线 A）**，给出信息架构与路由、页面清单、角色 × 动作权限矩阵与 UI 表现规则、统一状态/交互规范、组件基线、后端契约补充、M1.5 交付清单与验收标准，以及 5 个待负责人确认项。
- 更新 `docs/M1-ACCEPTANCE.md` 下一步指向 UI 规格；交接记录切换到 M1.5。
- 本轮无功能代码变更（仅规格与文档）。

## V0.5.1 — 2026-11-12

- **M1 完成**：前端补齐客户/产品/项目创建、知识文档创建/提交审核/审核通过/来源引用查看，并新增知识生命周期辅助函数与测试；SPA 由 Domain Service 托管（自动探测 `apps/web/dist`，可用 `MAMMOTH_WEB_DIST` 指定）。
- 验证：后端 `mvn verify` **43 tests**；前端 build + **7 tests**；SPA `GET /` 与 `/assets/*.js` 均 200；认证版演示 **14/14**。
- 验收记录更新：`docs/M1-ACCEPTANCE.md`（UI 功能完成，浏览器点击级验证因环境限制未执行并如实登记）。
- 文档：M1 计划 T12 完成并去除重复行；dev-setup 增加一体化启动方式；CHANGELOG/交接同步。

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
