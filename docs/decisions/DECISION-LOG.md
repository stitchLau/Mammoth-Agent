# 决策补充日志（M1–M4 S0）

> ADR（`decisions/ADR-0001`–`0013`）记录**架构级**决策；本文件记录 M1–M4 S0 过程中经负责人确认、但未单独立 ADR 的决策与默认值。D-001–D-009 为 2026-11-12，D-010 起为 2026-11-12/13（Asia/Shanghai）。M2 期间负责人授权"过程中的疑问均按 Agent 建议执行"，D-010 起按该授权记录；M4 由负责人逐项拍板。

| ID | 决策 | 结论 | 确认方式 | 影响/记录 |
|---|---|---|---|---|
| D-001 | M1 计划与阶段门禁 | 按 `docs/specs/M1-PHASE1-PLAN.md` 执行 T1–T12，验收以该文件 §2/§10 为准 | 负责人批准计划 | M1 验收见 `docs/M1-ACCEPTANCE.md` |
| D-002 | 代码与资料同仓 | 产品代码（`apps/`、`services/`）与资料（`docs/`、`decisions/`）同仓库；允许补 `.gitignore` 与最小 CI | 负责人确认 | 仓库结构、CI |
| D-003 | 数据根目录 | 默认位于 OS 应用数据目录（Windows：`%LOCALAPPDATA%\Mammoth\data`），可通过配置/环境变量指定；启动时拒绝 DSH Home、源码仓库、安装目录 | 负责人认可方案（"数据根目录放在安装目录或应用内设置均可，按更优方案执行"） | `DataRootResolver`/`DataRootBootstrap`；`docs/dev-setup.md` |
| D-004 | DSH 版本与 Fork 验证 | DSH 对齐 `0.2.2-rc.2`；本轮不验证 Fork 继承 | 负责人确认 | `docs/spikes/S1`；`AGENTS.md` 安全边界 |
| D-005 | UI 定位（路线 A） | 当前 SPA 作为**正式产品 UI 基座**演进，不是一次性演示壳；技术形态沿用 ADR-0002（独立 SPA + 后端托管） | 负责人确认"按推荐执行" | `docs/specs/UI-SPEC.md` v0.2；`CHANGELOG V0.6.0` |
| D-006 | M1.5 UI 默认项 | ①引入 `react-router`；②知识正文先用 Markdown 纯文本；③沿用中性色 + `#247bbf`，等品牌规范再替换；④允许安装 Playwright/Chromium 做浏览器 E2E；⑤保持独立 SPA（不内嵌 DSH 侧栏） | 负责人选择"按推荐默认全部执行" | `UI-SPEC` §13；`CHANGELOG V0.7.0/V0.8.0` |
| D-007 | MCP Agent 身份 | 正式认证落地前，MCP 入口使用 `MAMMOTH_MCP_AGENT_USER_ID` 指定本地用户；该用户须为项目成员，且应使用专用只读账号 | 负责人确认 M1 认证范围；MCP 文档记录 | `docs/dsh-mcp-integration.md` §4；待正式凭据机制替换 |
| D-008 | 安全边界 | 禁止读取/导出 DSH 凭据文件；`~/.dsh` 不进入产品知识/RAG；插件启停需批准与回滚 | 负责人确认（写入仓库指令） | `AGENTS.md` "安全边界" |
| D-009 | M1.5 收尾范围 | 账号设置、成员角色管理、用户创建 UI、客户端分页、E2E 入 CI；后端分页/密码重置/通知等延后 | 负责人"继续 M1.5 收尾" | `UI-SPEC` §13；`CHANGELOG V0.8.0` |
| D-010 | M2 范围与顺序 | 需求工作项→证据→就绪→方案→任务/追溯→评审/版本共 6 切片；先出规格再编码 | 负责人授权"按建议执行" | `docs/specs/M2-PHASE2-PLAN.md` §9 |
| D-011 | ADR-0007/0008/0009 | M2 全部保持 OPEN；M2 用轻量快照与人工摘录绕开 | 负责人授权按建议执行 | `docs/13-OPEN-DECISIONS.md` |
| D-012 | M2 上下文基线 | 新增 ADR-0010：需求级 JSON 快照，方案版本绑定快照 ID | 负责人授权 | `decisions/ADR-0010-M2-CONTEXT-BASELINE.md` |
| D-013 | M2 AI 参与 | 不接应用内 LLM；仅 MCP 只读需求上下文；AI 草案留 M2.5/M3（ADR-0011 待立） | 负责人授权 | `M2-PHASE2-PLAN.md` §7 |
| D-014 | M2 权限模型 | 不新增角色，沿用 owner/editor/reviewer/viewer + 四眼 | 负责人授权 | `M2-PHASE2-PLAN.md` §5 |
| D-015 | 方案视图形态 | 单模型只读双视图渲染 + 结构层编辑，不提供视图级编辑 | 负责人授权 | `M2-PHASE2-PLAN.md` §4.4 |
| D-016 | M2 状态机上限 | 需求状态止于 `approved`，`in_development` 起由 M3 接管 | 负责人授权 | `M2-PHASE2-PLAN.md` §4.2 |
| D-017 | M2 生产化 | 不先做 TLS/共享会话/PG 方言/后端分页；只做与新表相关的必要加固 | 负责人授权 | `M2-PHASE2-PLAN.md` §2 |
| D-018 | ADR-0007 定案 | 本地只读 Git + 人工捕获不可变基线；白名单只读命令；不 clone/fetch/push/checkout；多仓库基线=commit 集合；脏树默认拒绝；代码不进知识库；知识过期只提示不改状态 | 负责人 2026-11-13 指示先定 ADR-0007/0008 | `decisions/ADR-0007-GIT-BASELINE.md` |
| D-019 | ADR-0008 定案 | 单机本地单写者；数据根必须本地固定盘且拒绝网络盘/云同步根；实例锁；MCP 只读连接且不执行迁移；跨机仅导出/导入（不含凭据，仅空库导入）；备份 `VACUUM INTO`/backup API | 负责人 2026-11-13 指示 | `decisions/ADR-0008-WORKSPACE.md` |
| D-020 | M3 迁移职责 | Flyway 迁移只由 Web/管理入口执行；MCP 入口改只读连接（`query_only`），消除双进程迁移风险 | 由 D-019 推导，M3 首个切片实现 | `ADR-0008` §决策 4/13 |
| D-021 | M3 范围与切片 | 按 S1 运行边界 → S2 仓库/基线 → S3 交接包 → S4 实施回收 → S5 偏差/矩阵 → S6 As-built/知识闭环逐片实现；先出规格再编码 | 负责人"完成M3阶段"，授权按建议执行 | `M3-PHASE3-PLAN.md` §3/§10 |
| D-022 | Git 基线实现口径 | 多仓库基线=commit 集合；脏树默认 409，仅 owner 可 `allowDirty`；只读适配器白名单；实施记录按仓库逐条（from=基线 commit，diverged=`!isAncestor`） | 负责人 M3 授权 | `ADR-0007` 落地记录；`BaselineService`/`ExecutionService` |
| D-023 | 实施回收与偏差 | 任务完成由人勾选；偏差=系统未完成候选 + 人工分类；变更仅文件级；矩阵按验收标准/任务完成度聚合 | 负责人 M3 授权 | `M3-PHASE3-PLAN.md` §10.3 偏差 2/3 |
| D-024 | 交接包 v1 | 交接包为 Markdown API 响应，不含凭据；文件导出（zip/Artifact/下载）登记 BG-01 | 负责人 M3 授权；M3.5 复盘确认 | `M3.5-BACKLOG.md` BG-01 |
| D-025 | 知识生命周期 | 提议接受生成草稿，发布仍走四眼 verify；M3.5 增加 `superseded`/`deprecated` 与 `supersede`（新替旧），历史保留、仅 verified 可检索 | 负责人"补第 2 项" | `V6` 迁移；`KnowledgeService`；`M3-PHASE3-PLAN.md` §12 |
| D-026 | ADR-0008 补齐 | 导出/导入/备份/恢复 CLI；导入仅空库、校验 checksum、密码锁定、指定管理员重置；锁清理 `--admin-clear-lock` + PS 脚本 | 负责人"导出/导入与备份演练/锁清理" | `ADR-0008` 补齐章节；`dev-setup.md` |
| D-027 | M3.5 技术债 | BG-01 交接包文件导出、BG-02 Baseline 部署/DDL 版本、BG-03 矩阵业务目标/证据链接登记为 M3.5，不阻塞 M3 关闭 | 负责人"把 3–5 项登记为 M3.5/技术债" | `docs/specs/M3.5-BACKLOG.md`；`12-ROADMAP.md` |
| D-028 | M4 总体口径 | M4 收敛为"单节点团队协作 + 只读外部集成 + 站内通知 + 外部 Agent 只读交付"；生产 API/双向同步/真实外发/PG 多实例/远程网关延后并登记 | 负责人 2026-11-13 确认"按建议，但需要 M4.5 计划" | `docs/specs/M4-PHASE4-PLAN.md`；`docs/specs/M4.5-PHASE45-PLAN.md` |
| D-029 | M4 切片顺序 | S0 准入 → S1 组织/用户 → S2 BG-01/Agent/MCP 审计 → S3 导入框架 → S4 Jira 文件/BG-03 → S5 通知/任务/邮件企微 → S6 团队服务器预案 | 负责人"按你建议" | `docs/specs/M4-PHASE4-PLAN.md` §5 |
| D-030 | 集成首发 | 只读文件导入优先；Jira 先于 CSM；Jira 目标 `http://119.145.133.146:18009/` 仅记录，M4 不调用；CSM 端点未提供，只留 SPI/规划 | 负责人"按建议"+提供 Jira 地址/CSM 暂缺 | ADR-0011；`M4.5-PHASE45-PLAN.md` |
| D-031 | 服务端/数据库 | M4 单节点 + SQLite；团队服务器/证书暂不处理；**PG 多实例 = M5**；反向代理 TLS 为后续团队部署路径；M5 计划见 `docs/specs/M5-PHASE5-PLAN.md` | 负责人"按建议；明确 PG 阶段；服务器与证书暂不处理" | ADR-0012；`M4.5-PHASE45-PLAN.md` §6；`M5-PHASE5-PLAN.md` |
| D-032 | 通知渠道 | 站内通知默认启用；邮件/企微**完整实现**但默认关闭；必须有配置参数才能启用；企微先机器人；SMTP 同口径 | 负责人 2026-11-13 明确 | ADR-0013；`M4-PHASE4-PLAN.md` §9 |
| D-033 | 外部 Agent | 专用只读 Agent 账号 + BG-01 交接包 zip + MCP 审计方案 A（本地 JSONL + writer 入库）；远程网关 M4.5 评估 | 负责人"按建议" | ADR-0006/0008 增补；`M4-PHASE4-PLAN.md` §7 |
| D-034 | 组织权限 | 新增 `org_admin`、用户停用/启用、管理员重置+强制改密、会话撤销、限制建用户/用户目录；不新增项目角色 | 负责人"按建议" | ADR-0006 增补；`M4-PHASE4-PLAN.md` §6/§11 |
| D-035 | ADR 编号 | ADR-0011 外部集成/凭据、0012 服务端/PG、0013 通知/外发；应用内 LLM 顺延另立 | 负责人"按建议" | `docs/13-OPEN-DECISIONS.md`；ADR-0011/0012/0013 |
| D-036 | M3.5 BG 顺序 | BG-01 → M4 S2；BG-03 → M4 S4；BG-02 → M4 S6/M5（S6 条件） | 负责人"按你建议" | `docs/specs/M3.5-BACKLOG.md`；`M4-PHASE4-PLAN.md` §8 |
| D-037 | M4 S1 实施 | `org_admin`（`role_assignment`）+ 用户停用/启用/管理员重置/强制改密/Agent 账号 + 建用户/目录收紧 + 客户/产品主数据仅 org_admin；V7；后端 103 tests、前端 25、E2E 4、演练 18/18/9/9/25/25 | 负责人"开S1" | `M4-PHASE4-PLAN.md` §6.6；`ADR-0006` v1.1 |
| D-038 | M4 S2 实施 | BG-01 交接包 ZIP（manifest/逐文件 sha256/秘密扫描/Artifact/`handoff.export`）+ 专用 Agent 账号（复用 S1）+ MCP 本地 JSONL 审计 + writer 入库 `agent_call`/`audit_log`；V8；后端 109 tests、前端 25、E2E 4、演练 18/18/9/9/25/25 | 负责人"开S2" | `M4-PHASE4-PLAN.md` §17；`ADR-0008` 增补 |

## M3 前置（2026-11-13 已解除）

| ID | 事项 | 状态 |
|---|---|---|
| ADR-0007 | Git 代码基线与变更映射 | **Accepted v1.0**；M3 实现 `project_repository`/`project_baseline`/`project_baseline_repo` 与只读 `GitAdapter` |
| ADR-0008 | 项目工作区与正式知识空间 | **Accepted v1.0**；M3 实现实例锁、网络盘/云同步拒绝、MCP 只读连接、导出/导入与备份演练 |

## M4 S0 待评审（2026-11-13）

| ID | 事项 | 状态 |
|---|---|---|
| ADR-0011 | 外部系统集成与凭据边界 | **Proposed**；方向已确认（只读文件→只读 API→双向；Jira 先于 CSM；凭据不入库）；待 S0 评审转 Accepted |
| ADR-0012 | 团队服务端部署与数据库演进 | **Proposed**；单节点 + 反向代理 TLS + SQLite；**PG 多实例 = M5**；服务器/证书暂不处理 |
| ADR-0013 | 通知与外部发送 | **Proposed**；站内默认启用；邮件/企微完整实现、默认关闭、配置后启用 |
| ADR-0006 增补 | 组织角色/用户生命周期/Agent 账号 | **Proposed**；随 S1 实施 |
| ADR-0008 增补 | 新表分类/Agent 审计/单写者 | **Proposed**；随 S2/S3/S5 实施 |
| M4 计划 | S1–S6 切片、权限矩阵、数据模型、测试 | Draft；`docs/specs/M4-PHASE4-PLAN.md` |
| M4.5 计划 | Jira 只读 API/CSM/通知启用/远程 Agent/团队服务器；PG=M5 | Draft；`docs/specs/M4.5-PHASE45-PLAN.md` |
| M5 计划 | PostgreSQL/多实例/HA、共享 Artifact、共享会话/限速、任务并发、迁移/备份/回滚 | Planning；`docs/specs/M5-PHASE5-PLAN.md` |

## M2 待决（进入 M3 前）

| ID | 事项 | 当前状态 |
|---|---|---|
| Q-005 | 应用内 LLM 与 Agent 草案写入 | 未定；ADR-0011 编号已用于外部集成，LLM 需另立 ADR（0014+）；M4 不接 |
| Q-006 | 文档解析管线（ADR-0009） | OPEN；M2 用人工摘录，M4 导入不解析 PDF/Office |
| Q-007 | ProjectBaseline 与方案版本绑定 | **已关闭（M3）**：实现 `project_baseline` 与 `solution_version.baseline_id`；ADR-0007 Accepted |
| Q-008 | 需求工作项的评论/讨论、通知 | 通知进入 M4 S5（站内默认/邮件企微配置启用）；评论/讨论仍待排期 |

## 遗留待决（M1/M1.5 提出，M2 未关闭）

| ID | 事项 | 当前状态 |
|---|---|---|
| Q-001 | 视觉品牌规范（颜色/字体/Logo） | 未提供，暂用基线样式 |
| Q-002 | 知识正文最终编辑形态（Markdown vs 富文本） | 暂用纯文本，M2 再评（富文本需 XSS 安全评审） |
| Q-003 | 最终 UI 是否内嵌 DSH 侧栏浏览器 | 当前按 ADR-0002 独立 SPA；若改变需新 ADR |
| Q-004 | 生产认证目标（组织级 SSO/令牌） | ADR-0006 后续任务 |

> M3.5 技术债（BG-01–BG-03）见 `docs/specs/M3.5-BACKLOG.md`；M4 已由负责人 2026-11-13 拍板进入 S0，S0 ADR/规格待评审后转 Accepted 并开 S1；尚未写业务代码。
