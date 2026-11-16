# Session Handoff — DSH 工具 schema P0 事故复盘与防再发

- **时间**：2026-11-16
- **当前分支 / Commit**：`main` @ `fcc851c` + 本次文档提交；spike @ `dec48c4`（2026-11-16 提交 bundle 与规则）
- **当前任务**：复盘 2026-11-13 `mammoth_propose_work_item` 非法 schema 导致全部 DSH 会话失败；判断并实施必要的深层修复；写入高优先级全局规则
- **会话目的**：确认最小修复是否足够、补齐防再发机制、把根因固化到全局指令
- **状态**：已重启并验证（2026-11-16 09:33）

## 已确认事实

1. 根因是 `parameters` 手写 raw JSON Schema，却在 `properties.title/summary` 使用 author DSL 的 `required: true`；Provider 校验工具参数 schema 时返回 400。
2. DSH `0.2.2-rc.2` 的 `ToolRegistry.register()` 只校验 `output.schema`，不校验 `parameters`，因此非法 schema 在注册期逃逸。
3. `desktop` profile 通过 `link:` + junction 直接加载 spike 工作树；坏 schema 随全局工具进入每个请求，导致所有会话、所有工作区失败。
4. 用 DSH 自带 `assertSupportedJsonSchema()` 对备份实测：`mammoth_propose_work_item` FAIL；对当前文件实测：3 个工具全部 PASS。
5. 最小修复（上一会话）已恢复服务。
6. 2026-11-16 09:32:43 重启 Desktop；09:32:46 写入 `plugin.activated`（`configError=null`），说明新模块的相对导入成功、3 个工具均通过注册前校验；09:33 只读工具 `mammoth_get_project_context` 调用成功（未绑定项目，返回提示），Provider 已接受当前工具 schema。

## 已做变更

**Mammoth-Agent-spike（bundle）**

- `integrations/dsh/mammoth-bundle/tool-schema.js`（新增）：raw schema 子集校验，含 `required: true` 针对性错误。
- `integrations/dsh/mammoth-bundle/tool-parameters.js`（新增）：3 个工具 `parameters` 单一来源（已与修复后版本逐字段比对一致）。
- `index.js`：`registerTool()` 注册前 `assertToolParameters()`；参数改为引用 `TOOL_PARAMETERS`。
- `scripts/check-tool-schemas.mjs`（新增）：事故回归夹具 + 真实 `apply()` 激活捕获校验。
- `package.json`：新增 `check` 脚本、`files` 增加两个新模块。
- `README.md` / `docs/DETAIL.md`：写入强制流程、已知限制与回滚提示。
- `AGENTS.md`：顶部新增最高优先级规则。

**Mammoth-Agent（文档/规则）**

- `AGENTS.md`：顶部新增同规则。
- `docs/quality/INCIDENT-2026-11-13-DSH-TOOL-SCHEMA.md`（新增）：完整复盘。
- `docs/11-DEEPSEEK-HARNESS-GUIDE.md`：新增 §9.1。
- `docs/09-QUALITY-AND-TESTING.md`：DoD、Contract 层、发布前检查各补一条。
- `docs/README.md`：索引新增事故复盘。
- `~/.dsh/AGENTS.md`（新增，用户级全局）：7 条精简规则；已被 DSH 立即加载。

## 运行过的命令和结果

| 命令 | 结果 |
|---|---|
| `node --check index.js` / `tool-schema.js` / `tool-parameters.js` / `scripts/check-tool-schemas.mjs` | 全部通过 |
| `node scripts/check-tool-schemas.mjs` | 全部 PASS（含事故夹具、3 个声明 schema、激活捕获） |
| `assertToolParameters()` 传入 `required: true` 的 schema | 按预期抛错并给出 DSL/raw 说明 |
| DSH `assertSupportedJsonSchema()` 校验当前 bundle | 3 个工具 PASS |
| 同一校验器校验 `.bak-20261113-1756` | `mammoth_propose_work_item` FAIL（复现根因） |
| 重构前后 `parameters` 深度比较 | IDENTICAL |
| 重启后进程启动时间 | 2026-11-16 09:32:43（DeepSeek Harness 进程组） |
| `state.json` 审计（bundle 自身状态） | `2026-11-16T01:32:46.029Z` `plugin.activated`，`configError=null` |
| `mammoth_get_project_context`（只读冒烟） | 调用成功，返回“未绑定项目”提示 |
| spike 仓库提交 | `dec48c4`，15 files，+3014；不含 `local/`、`cordis.patch.yml` |

## 当前 Git 状态

- `Mammoth-Agent`：本次文档提交包含 `AGENTS.md`、`docs/09`、`docs/11`、`docs/README.md`、`docs/quality/INCIDENT-...`、`docs/handoffs/SESSION-HANDOFF-...`；此前未跟踪的 `decisions/ADR-0014...`、`docs/evidence/2026-11-13-client-arch/`、`docs/reports/` 保持未触碰。
- `Mammoth-Agent-spike`：bundle 与 `AGENTS.md` 已提交 `dec48c4`；仅 `spike/dsh-desktop-integration/` 仍未跟踪（另一项 spike，未触碰）。

## 未解决问题

1. `check` 尚未接入 CI/提交钩子，目前依赖人工执行 + 注册时 fail-fast。
2. DSH 上游仍未在 `register()` 校验 `parameters`；建议反馈。

## 尚未验证的假设

- DSH `assertSupportedJsonSchema` 与 Provider 校验的完全等价性未穷举；本次仅覆盖实际使用的 schema 子集，且已被真实请求与工具调用验证。

## 下一步最小行动

1. （可选）向 DSH 上游反馈 `register()` 未校验 `parameters`。
2. （可选）把 `node scripts/check-tool-schemas.mjs` 接入提交钩子或 CI。

## 新会话必须先阅读

1. `docs/quality/INCIDENT-2026-11-13-DSH-TOOL-SCHEMA.md`
2. `AGENTS.md` 顶部“最高优先级”一节
3. `E:\MyProj\MommothAgent\code\Mammoth-Agent-spike\integrations\dsh\mammoth-bundle\tool-schema.js`
4. `...\mammoth-bundle\scripts\check-tool-schemas.mjs`
5. `docs/11-DEEPSEEK-HARNESS-GUIDE.md` §9.1

## 风险提醒

- 任何对 `tool-parameters.js` / `index.js` 的修改都会进入**全局**工具面；保存前先跑 `check`。
- 不要用“Provider 报错=模型/网络问题”的直觉处理；`Invalid schema for function` 必须回滚。
- `local/config.json`、`cordis.patch.yml`、`.bak` 均为机器本地文件，不得提交。
