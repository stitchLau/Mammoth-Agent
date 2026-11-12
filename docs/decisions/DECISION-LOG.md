# 决策补充日志（M1 / M1.5 / M2）

> ADR（`decisions/ADR-0001`–`0010`）记录**架构级**决策；本文件记录 M1/M1.5/M2 过程中经负责人确认、但未单独立 ADR 的决策与默认值。日期均为 2026-11-12（Asia/Shanghai）。M2 期间负责人授权"过程中的疑问均按 Agent 建议执行"，D-010 起按该授权记录。

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

## M2 待决（进入 M3 前）

| ID | 事项 | 当前状态 |
|---|---|---|
| Q-005 | 应用内 LLM 与 Agent 草案写入（ADR-0011） | 未定；M2 明确不接 |
| Q-006 | 文档解析管线（ADR-0009） | OPEN；M2 用人工摘录 |
| Q-007 | ProjectBaseline 与方案版本绑定 | 依赖 ADR-0007，M3 前定 |
| Q-008 | 需求工作项的评论/讨论、通知 | 未实现，入 M3/M4 backlog |

## 遗留待决（M1/M1.5 提出，M2 未关闭）

| ID | 事项 | 当前状态 |
|---|---|---|
| Q-001 | 视觉品牌规范（颜色/字体/Logo） | 未提供，暂用基线样式 |
| Q-002 | 知识正文最终编辑形态（Markdown vs 富文本） | 暂用纯文本，M2 再评（富文本需 XSS 安全评审） |
| Q-003 | 最终 UI 是否内嵌 DSH 侧栏浏览器 | 当前按 ADR-0002 独立 SPA；若改变需新 ADR |
| Q-004 | 生产认证目标（组织级 SSO/令牌） | ADR-0006 后续任务 |
