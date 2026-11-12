# ADR-0001 — DeepSeek Harness 集成策略

- 状态：Proposed（尚未通过 Spike）
- 日期：2026-11-12

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
