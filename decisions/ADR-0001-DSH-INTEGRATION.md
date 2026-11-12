# ADR-0001 — DeepSeek Harness 集成策略

- 状态：**Accepted（2026-11-12，负责人批准；受控于下方"未验证边界"）**
- 采纳版本：v1.0（由 `docs/spikes/S1-DSH-EXTENSION-SPIKE.md` 的 v0.3 草案批准而来）
- 日期：2026-11-12（原 Proposed：2026-11-12）

## 背景

Mammoth 计划使用 DeepSeek Harness 作为 Vibe Coding 工具，并考虑基于 Harness 扩展 Agent 产品能力。两种用途必须分开评估。

## 候选方案

A. 重度 Fork DSH，将 Mammoth 业务直接写入上游源码。

B. Out-of-tree Bundle/Plugin 扩展 DSH，Mammoth Domain API 和数据存储保持独立。

C. Mammoth 独立桌面应用，DSH 仅作为外部 Agent Runtime/执行器。

## 当前建议

先验证 B；同时将 A 作为高维护成本备选，将 C 作为扩展点不足时的备选。当前不批准重度 Fork。

## 原因

- DSH 是 Cordis 插件组合架构，官方提供 Profile/Bundle 扩展路径；
- 业务数据和生命周期需要独立于 Session/模型 Runtime；
- 插件扩展可减少对上游内部实现的长期耦合；
- 当前上游处于预发布/开发预览阶段，API 和安全边界需要额外审查。

## Spike 验证项

- [ ] 后端服务注册及调用
- [ ] Agent Tool 注册及权限/审批
- [ ] UI 入口或页面扩展
- [ ] 数据持久化往返
- [ ] Bundle 安装/升级/卸载
- [ ] 插件真实组合测试
- [ ] DSH 版本升级兼容性
- [ ] Desktop 打包路径和配置边界

## 决策门槛

仅当 Spike 证明关键 UI/领域能力无法通过公开扩展点实现，或插件架构造成不可接受的限制时，才重新评估 Fork。

## 参考

- https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/architecture.md
- https://github.com/deepseek-ai/deepseek-harness/blob/master/SAFETY.md


## 5. V0.2 补充：Vibe Coding 与 Runtime 集成边界

将 DSH 作为开发 Mammoth 的工具（Vibe Coding Host）与将 DSH 作为 Mammoth 的运行时（Product Runtime）分开决策。前者可以先使用；后者仍需单独 Spike。

角色定义文件 `docs/agents/*.md` 是可复用 Role Prompt，不是自动注册 DSH Agent 的配置。委派由 Lead Agent 在运行时读取角色文件并构造自包含 prompt。

AgentTeam 共享任务板不等于文件系统隔离。任何并行代码写入需要独立 Worktree/分支；否则串行。

这些规则是 V0.2 工程约束，并不意味着已验证当前安装版本的所有 SubAgent/Team API。必须记录实际 DSH 版本和 Profile 后再执行扩展 Spike。

## 6. 决策结果（2026-11-12 批准，Accepted v1.0）

### 6.1 采纳方案

**方案 B：out-of-tree Bundle/Plugin 扩展 DSH，Mammoth Domain API 与数据存储保持独立。** 不 Fork 上游（方案 A 不批准）；方案 C 作为"公开扩展点不足"时的保留路径。

### 6.2 采纳依据（实测环境与证据）

- 环境：DSH `0.2.2-rc.2`（外壳 + payload，build commit `a6b1b85`），Profile `desktop`，更新渠道 `nightly`，Managed shell = PowerShell 5.1（Low 完整性级别）。
- 已验证（2026-11-12，见 `docs/spikes/S1-DSH-EXTENSION-SPIKE.md` §3、§10）：
  1. 工作区 bundle 可经 `plugin_manager install_bundle` 装入 profile 并 HMR 激活（`application: applied`）；
  2. `ctx.tools.register(defineTool(...))` 注册的 Agent 工具可被真实调用；
  3. 官方 Host 持久化缝 `ctx.storageDomain` 可打开 schema 校验域并落盘（`~/.dsh/storages/mammoth_spike.json`）；
  4. `set_bundle` 可启用可选能力（Git Worktree，`create_worktree` 工具）；
  5. profile 组成可由 `list_bundles` 核对。
- 未验证（不据此宣称已验证）：UI slot 视觉渲染；跨重启读回；`engines.dsh` 负向拒绝；MCP 端到端；权限/审批策略在真实插件中的行为。

### 6.3 由此产生的工程约束

1. **外部 bundle 必须自带精确版本的 `@deepseek-ai/*` 依赖并在自身目录安装**，否则模块无法解析；插件与 DSH 版本强耦合，升级 DSH 时必须同步升级插件依赖。
2. 使用官方 `defineTool` 时工具 schema 用 DSL；直连注册才需原生 JSON Schema。
3. **业务权威状态不得存入 DSH storage**：DSH storage 仅放非权威本地状态；领域状态由独立 Domain API/关系库承载。
4. Domain API ↔ DSH 的推荐边界是 **MCP**（配置型 bundle + `dsh-mcp-client`），避免外部 bundle 依赖解析问题；备选 `dsh-sdk-jsonrpc-server`/ACP。
5. 安装/更新插件需 Full access 或审批；更新已装包先 `remove_bundle` 再 `install_bundle`。

### 6.4 回滚

- 卸载插件：`plugin_manager remove_bundle(<bundle>)`；关闭可选能力：`set_bundle(..., enabled=false)`。
- Profile 由 plugin_manager 维护，禁止手改；升级前备份 `$DSH_PROFILE_DIR` 与 session 存储。

### 6.5 后续

- ADR-0002（技术栈/UI 扩展边界）与 ADR-0007（Git/Worktree 使用策略）以本 ADR 为前置。
- 剩余未验证项在 M1 前用最小 S1c 补齐，任何结论仍须标注版本与证据。

