# Session Handoff — 2026-11-13（M3 完成）

- **时间**：2026-11-13（Asia/Shanghai）
- **当前分支 / Commit**：`main`，M3 S2–S6 提交（`git log -1` 为准；此前 `86abb59` = M3 S1）
- **当前任务**：M3（实施回收与知识闭环）已完成并通过本地验证；下一阶段 **M4（企业集成与协同）**
- **会话目的**：让全新会话在无历史上下文时独立恢复 M4/后续工作

## 已确认事实

- 后端 `mvn verify` → **79 tests**；前端 build + **25 tests**；Playwright **3 passed（12.8s）**；`demo-m3.mjs` **25/25**。
- M3 能力：只读 Git 仓库注册与不可变基线（多仓库、脏树拒绝/owner 覆盖、URL 脱敏）、交接包、实施回收（from/to、变更清单、diverged）、任务完成与人工偏差、验收矩阵（covered/partial/unverified + 未完成候选）、As-built 快照（四眼、Artifact、不可覆盖）、知识更新提议（接受生成知识草稿）、知识过期提示。
- 决策：ADR-0007/0008 Accepted；M3 迁移 `V5`；Git 只读白名单；源码不进入数据库/检索。

## 已做变更

- **数据**：`V5__m3_delivery.sql`（9 表 + `knowledge_document.applicable_baseline_id`）。
- **后端**：`git/LocalGitAdapter`、`baseline/*`、`execution/*`、`delivery/*`、`knowledge/KnowledgeStalenessService`、`web/BaselineController`、`web/DeliveryController`、Knowledge 响应新增 `applicableBaselineId/stale`。
- **前端**：`BaselinesPage`、`DeliveryPage`、需求详情"交付与知识闭环"入口、知识列表/详情/检索"可能过期"标记、侧栏"代码基线"。
- **测试**：`LocalGitAdapterTest` 3、`BaselineWorkflowTest` 2、`DeliveryWorkflowTest` 3、`MigrationTest` V5、E2E 第 3 场景。
- **文档**：M3 规格 §9–§10、验证证据 §7、时间线 §5、缺陷 B-M3-006–008、ADR-0007 落地记录、演示说明、README/ROADMAP/CHANGELOG/13-OPEN-DECISIONS、本交接。

## 运行过的命令和结果

```powershell
mvn -B -ntp -f services/domain/pom.xml verify        # BUILD SUCCESS, 79 tests
cd apps/web; npm run build; npm test                 # build ok, 25 tests
cd apps/web; npm run test:e2e                        # 3 passed (12.8s), 空数据根 18153
node scripts/demo-m3.mjs http://127.0.0.1:18150      # 25/25
```

## 当前 Git 状态

- 分支 `main`；M3 代码与文档同批提交（`git log` 为准）并推送 origin。
- 工作树应干净。

## 未解决问题

- **ADR-0008 后续**：导出/导入包、实例锁清理脚本、备份/恢复演练自动化。
- **ADR-0009**：文档解析仍 OPEN（证据=原始资料+人工摘录）。
- **M4 范围**：Jira/CSM、企业微信/邮件、外部 Agent 适配、服务端运行、组织权限细化、TLS/PG 方言。
- **CI 远端未运行**（所有新测试仅本机）。
- **限制**：Git 变更映射仅文件级；重命名 numstat 近似；子模块不递归；Windows 映射网络盘检测依赖启发式；交接包当前为 Markdown 响应而非可下载文件。

## 尚未验证的假设

- 真实客户仓库（大仓库/中文路径/子模块）下的超时与性能；已用小型临时仓库验证。
- 交付快照 Markdown 作为知识草稿正文的长期可读性（可能过长，后续可拆分为结构化知识条目）。
- 多仓库实施记录在验收矩阵中的聚合是否符合真实项目习惯（当前按任务完成度聚合）。

## 下一步最小行动（M4 或 M3 补强）

1. 若要补强 M3/ADR-0008：先做导出/导入包 v1（manifest + sha256 + 空库导入 + 审计）与备份恢复演练脚本；
2. 若进入 M4：先定外部系统集成边界（Jira/CSM 只读导入 vs 双向同步）与凭据管理方案，避免把生产凭据带入本地；
3. 任何新切片沿用"规格→实现→测试→证据→交接"，并保持 M1–M3 全量回归。

## 新会话必须先阅读

- `AGENTS.md`、`docs/00-MASTER-GUIDE.md`、`docs/README.md`、本交接；
- `docs/specs/M3-PHASE3-PLAN.md`（§9 设计、§10 实施记录）、`docs/evidence/M3-VERIFICATION.md`、`docs/quality/M3-BUGS.md`；
- `decisions/ADR-0007-GIT-BASELINE.md`、`decisions/ADR-0008-WORKSPACE.md`；
- 代码：`services/domain/src/main/java/com/mammoth/domain/{git,baseline,execution,delivery}`、`apps/web/src/pages/{BaselinesPage,DeliveryPage}.tsx`。

## 风险提醒

- Git 集成必须保持只读；不要为"方便"增加 fetch/checkout/commit。
- 批准后的方案/交付快照不可覆盖，变更只能新建版本。
- 知识更新提议接受只生成**草稿**，不得直接 verified；仍需四眼审核。
- 演示/测试使用的仓库必须是合成临时仓库，禁止指向真实客户仓库。
## 2026-11-13 更新：ADR-0008 导出/导入与备份/恢复已补齐

- **实现**：com.mammoth.domain.admin + java -jar ... --admin-export/--admin-import/--admin-backup/--admin-restore；admin 参数在 Spring 环境构建前过滤，导入口令优先 MAMMOTH_IMPORT_ADMIN_PASSWORD。
- **导出包**：manifest.properties + 31 张表 SQL（跳过 session/flyway）+ Artifact 字节；密码哈希替换为 !mammoth-import-locked，不含会话/凭据。
- **导入**：仅空库；校验 schema/逐条目 sha256/行数/Artifact 大小；单事务；为 --admin-username 设置新口令，其余账号锁定；审计 dmin.import。
- **备份/恢复**：VACUUM INTO + Artifact 复制 + manifest；恢复校验 db sha256、integrity_check、schema、Artifact 哈希，目标必须空目录。
- **验证**：后端 **83 tests**；scripts/admin-drill.mjs **9/9**（种子→导出→导入→启动校验→备份→恢复→再启动→非空拒绝）；详见 docs/evidence/M3-VERIFICATION.md §8。
- **仍限制/未做**：导出不加密；导入不合并/不增量；非管理员账号锁定（无密码重置）；实例锁清理脚本未自动化；备份要求写实例停止。
- **下一步**：若要继续加固，可做导出包加密（需密钥管理 ADR）、账号批量重置、实例锁清理脚本；否则可进入 M4。
## 2026-11-13 更新：M3.5 补强完成（用户确认补 1/2 项 + 锁清理）

- **知识生命周期**：新增 superseded/deprecated（V6：superseded_by、deprecation_note）；POST /knowledge/{id}/supersede 与 /deprecate（owner/reviewer、保留历史、不参与检索）；详情页提供操作与状态筛选。
- **知识发布后半段**：交付知识 草稿 → 作者提交审核 → 另一人四眼 verify → supersede 旧知识；DeliveryWorkflowTest 覆盖。
- **脱敏项目验收演练**：scripts/m3-acceptance-drill.mjs **18/18**（批准方案→交接→实施回收→矩阵→偏差→As-built→知识发布→supersede→检索只返回新知识→审计→登出）。
- **实例锁清理**：--admin-clear-lock --data-root=<root> + scripts/clear-data-root-lock.ps1；运行中拒绝（含 pid/host），停止后清理，无锁幂等；LockCleanupServiceTest 4 tests。
- **技术债登记**：docs/specs/M3.5-BACKLOG.md（BG-01 交接包文件导出、BG-02 Baseline 部署/DDL 版本、BG-03 矩阵业务目标/证据链接）；ROADMAP 切片状态已修正。
- **验证**：后端 **89 tests**；前端 build + 25 tests；E2E 3 passed；验收演练 18/18；ADR-0008 演练 9/9。
- **剩余**：真实客户项目验收需负责人提供授权脱敏项目；BG-01–BG-03 未实现；导出加密/账号重置/增量导出仍为后续。
## 2026-11-13 资料补全（M2/M3 归档）

- 新增阶段验收记录：[docs/M2-ACCEPTANCE.md](../M2-ACCEPTANCE.md)、[docs/M3-ACCEPTANCE.md](../M3-ACCEPTANCE.md)。
- 新增 M2/M3 UI 截图：docs/evidence/screenshots/m2-*.png（3 张）、m3-*.png（3 张），由 scripts/capture-m2m3-screenshots.mjs 生成；证据文件已回链。
- 决策日志补齐 D-021–D-027（M3 范围、Git 基线、实施/偏差、交接包、知识生命周期、ADR-0008 补齐、M3.5 技术债）。
- M4 新会话启动 Prompt：[docs/prompts/10-M4-KICKOFF.md](../prompts/10-M4-KICKOFF.md)（整段粘贴；先出准入方案，不先写代码）。