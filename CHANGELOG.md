# Changelog

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
