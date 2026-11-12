# 决策补充日志（M1 及 M1.5）

> ADR（`decisions/ADR-0001`–`0009`）记录**架构级**决策；本文件记录 M1/M1.5 过程中经负责人确认、但未单独立 ADR 的决策与默认值。日期均为 2026-11-12（Asia/Shanghai）。

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

## 待决（M2 前/中）

| ID | 事项 | 当前状态 |
|---|---|---|
| Q-001 | 视觉品牌规范（颜色/字体/Logo） | 未提供，暂用基线样式 |
| Q-002 | 知识正文最终编辑形态（Markdown vs 富文本） | 暂用纯文本，M2 再评（富文本需 XSS 安全评审） |
| Q-003 | 最终 UI 是否内嵌 DSH 侧栏浏览器 | 当前按 ADR-0002 独立 SPA；若改变需新 ADR |
| Q-004 | 生产认证目标（组织级 SSO/令牌） | ADR-0006 后续任务 |
