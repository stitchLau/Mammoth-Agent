# Session Handoff — 2026-11-12（M0 收尾）

- **时间**：2026-11-12（Asia/Shanghai）
- **当前分支 / Commit**：`main` @ `e5ec5e3`（撰写时；本文件提交后以 `git log -1` 为准），已与 `origin/main` 同步
- **当前任务**：M0（工程启动与 DSH Spike）收尾 → M1 规划
- **会话目的**：完成仓库勘察、DSH 扩展 Spike、ADR-0001 定案、S1b/S1c/S2 实测、安全边界落地，并为 M1 产出第一垂直切片计划

## 已确认事实

1. **仓库**：Mammoth-Agent，`main` 干净，资料包 + `docs/spikes/` + `spike/` 夹具；无产品代码。
2. **DSH**：`0.2.2-rc.2`（桌面外壳与 payload 一致），build commit `a6b1b85f44891ee1484bed6a9fc08acaae777d90`；Profile `desktop`，当前 bundles = `dsh-base`、`dsh-web-app`、`@deepseek-ai/dsh-experimental-tool-worktree`；更新渠道 `nightly`。
3. **能力**（实测）：`subagent` / `subagent_fork`（工具在，继承未取得正例）/ `list_agents` / `send_message` / AgentTeam 任务板 / Workflow 工具可用；**Worktree 可选 bundle 已启用**，`create_worktree` 可用。
4. **环境陷阱**：Managed shell 是 Windows PowerShell 5.1 且为 **Low 完整性级别**；工作区根目录是 Low，但 `.git`、`docs` 等既有子目录是 Medium → shell 写这些目录被拒；Git 提交/推送需完全权限（本会话已切换为 danger-full-access）。
5. **扩展结论**（ADR-0001 Accepted v1.0）：
   - out-of-tree bundle 可安装/激活/卸载；`application: applied` 是生效判据。
   - 外部 bundle **不能**直接 import `@deepseek-ai/*`；必须声明精确依赖并在插件目录 `pnpm install`（S1b 已证）。
   - 官方 `defineTool` 与 `ctx.storageDomain` 可用；域数据落 `~/.dsh/storages/<domain>.json`。
   - **MCP 边界可行**：Java 17 stdio MCP server + 配置型 bundle → 工具名 `mcp__mammoth__*`，端到端调用成功，插件层零 DSH 依赖（S2）。
   - `engines.dsh` 不满足时安装被拒绝（错误码 `incompatible-version`，`nothing was installed`）。
6. **安全边界**（已写入 AGENTS.md / docs/08）：禁止读取 `~/.dsh/.credentials.yaml` 等凭据；`~/.dsh` 整体不进 RAG/知识库；插件安装属环境变更需批准与回滚。

## 已做变更（本会话全部提交，均已推送）

| Commit | 意图 |
|---|---|
| `deef338` | 新增 `docs/project-discovery.md` 勘察报告 |
| `961866f` | 勘察更新：rc.2 基线、Low IL 沙箱、Worktree 启用 |
| `aa34544` | `spike/dsh-extension/` 最小 bundle + S1 报告 + `.gitignore` |
| `32e0d46` | S1b：精确依赖 + `defineTool`/`storageDomain` 验证 + lockfile |
| `b492e23` | ADR-0001 Accepted + 安全边界 + 勘察更新 |
| `46d135b` | 文档一致性对齐 |
| `e5ec5e3` | S1c/S2 验证（engines 负向、读回、Java MCP 边界）+ 环境清理 |

## 运行过的命令和结果（摘要）

- `git status/log/remote/worktree`：初始 1 commit，47 文件，clean；最终 8 commits，同步 origin。
- DSH ACL 诊断脚本：修复工作区根 `WRITE_OWNER`（1 处授权，已验证，可回滚）。
- `plugin_manager install/remove/set_bundle`：探针包 3 次迭代后 `applied`；Worktree `applied`；负向测试被拒；最终两探针包已卸载。
- `pnpm install`（插件目录）：21 包，生成 lockfile。
- `javac`/`java` 协议自测：MCP initialize/tools/list/tools/call 全部正确。
- 工具实测：`mammoth_spike_probe` 计数 1→2→3→4（跨插件重载读回）；`mcp__mammoth__mammoth_project_summary` 返回合成 JSON。

## 当前 Git 状态

- `main` 与 `origin/main` 同步；工作树 clean。
- 本地残留分支 `spike/dsh-extension`（`b492e23`，已并入 main，可删除）。
- `spike/dsh-extension/node_modules` 已删除（README 记录 `pnpm install` 复现方式）；`spike/mcp-java/classes` 已删除（`javac` 可重建）。

## 未解决问题

1. **UI slot 视觉渲染**未验证（无浏览器控制；探针包已卸载，需重装后人工查看 composer dock 徽标）。
2. **DSH 进程重启后读回**未验证（需重启 Desktop；会终止会话）。
3. MCP 工具的 **权限/审批负向测试**（allow/deny/ask、guard、restrict）未做。
4. MCP server 崩溃/超时/重连行为未验证。
5. ADR-0002/0003/0004/0005/0006 仍 **OPEN**（技术栈、数据库、存储、RAG、身份）。
6. 无 CI、无 `.gitattributes`（CRLF 警告）；`manifest.json` 未随新增文件更新（当前与 `git ls-files` 不再一致，需重新生成或改为脚本维护）。
7. Worktree 的 rc.2 归档清理策略（`retainOnArchive` 默认关）未在真实工作流中验证。

## 尚未验证的假设

- DSH 升级到后续 rc 时，插件 `engines.dsh` 与依赖版本同步策略是否足够。
- MCP 在真实 Java Domain Service（非 stub）下的性能与错误传播。
- AgentTeam 多成员并发与依赖阻塞的真实行为。
- 外部 bundle 在"构建期打包为自包含产物"方案下的行为（未测）。

## 下一步最小行动

**执行 ADR-0004 Artifact Storage Spike**（原始资料不可变存储：内容寻址、去重、只读校验、备份与容量；候选：本地内容寻址目录 vs 对象存储/SQLite BLOB 对比），产出 ADR-0004 草案。ADR-0002（技术栈）与 ADR-0003（SQLite+Flyway，PG 为团队阶段目标）Spike 均已完成并产出 Proposed 草案，批准后作为实施基线。

## 新会话必须先阅读

1. `AGENTS.md`
2. `docs/00-MASTER-GUIDE.md`
3. `docs/project-discovery.md`（含 §14 追加验证与清理记录）
4. `docs/spikes/S1-DSH-EXTENSION-SPIKE.md`（ADR-0001 Accepted v1.0 + F1–F7 + S1c）
5. `docs/spikes/S2-DOMAIN-API-MCP-BOUNDARY.md`（MCP 边界与 Java 桥接）
6. `decisions/ADR-0001-DSH-INTEGRATION.md`（§6 决策结果与工程约束）
7. `docs/specs/M1-PHASE1-PLAN.md`（M1 第一垂直切片计划）
8. `docs/03-DOMAIN-MODEL.md`、`docs/04-MVP-STRATEGY.md`、`docs/06-KNOWLEDGE-AND-RAG.md`、`docs/09-QUALITY-AND-TESTING.md`

## 风险提醒

- **不要把业务数据放进 `~/.dsh`**；Mammoth 数据根目录必须独立（见 `docs/08-SECURITY-AND-TRUST.md` §8）。
- 外部 DSH bundle 与 DSH 版本强耦合：升级 DSH 必须同步升级插件依赖。
- Low IL shell 无法写 `.git`/`docs` 等 Medium 目录；Git 操作需完全权限会话，或先修复目录完整性标签。
- 不得把未运行验证写成已验证；本会话已按此原则标注所有未验证项。
- 不要在未批准的情况下扩大产品边界或删减 M1 验收条件。
