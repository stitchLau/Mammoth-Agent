# Prompt 00 — Mammoth 仓库启动与勘察

你现在是 Mammoth Agent 项目的工程负责人助手。先不要编写产品功能代码。

请先阅读：
1. `AGENTS.md`
2. `docs/00-MASTER-GUIDE.md`
3. `docs/01-PRODUCT-CHARTER.md`
4. `docs/02-PRODUCT-BOUNDARIES.md`
5. `docs/05-ARCHITECTURE-BASELINE.md`
6. `docs/11-DEEPSEEK-HARNESS-GUIDE.md`

执行只读勘察：
- 确认当前仓库是否为空、已有技术栈、目录、脚本、测试、Git 状态和未提交改动；
- 确认你实际读取了哪些 AGENTS.md 指令文件；
- 检查当前 DSH Profile/会话是否实际提供 `subagent`、`subagent_fork`、`list_agents`、`send_message`、AgentTeam 和 Worktree 能力；逐项标记已确认/未发现/无法验证；
- 不要猜测不存在的目录/命令；
- 列出产品文档中 OPEN 的技术决策；
- 给出仓库现状报告、风险、建议下一步和计划。

禁止：
- 不要初始化或覆盖已有工程；
- 不要自动安装依赖；
- 不要创建大量样板代码；
- 不要擅自选定技术栈；
- 不要修改用户已有文件。

最后输出：事实、未知、冲突、当前 DSH 能力清单、推荐 Spike、需要负责人决策的问题。将结果建议保存为 `docs/project-discovery.md`，但先向用户说明拟新增文件后再写入。
