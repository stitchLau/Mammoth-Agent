# 05 — 技术架构基线（提案，待 Spike/ADR 验证）

## 1. 架构目标

- Domain Model 与 Agent Runtime 解耦。
- 正式业务状态由 Domain Service 管理，不依赖会话历史。
- 文件/知识/向量索引可独立替换和重建。
- 本地工作区可用，未来可演进到团队共享/服务端执行。
- DSH 作为首选 Agent Runtime/宿主扩展候选，但不是数据主权边界。

## 2. 逻辑层

1. Desktop / UI：项目、客户、产品、知识、工作项和 Agent 会话入口。
2. Application API：认证、授权、命令、查询、状态流转。
3. Domain Layer：客户、产品、项目、知识、工作项、方案、任务、结项规则。
4. Agent Capability Layer：检索、逆向分析、需求分析、方案编制、任务拆解、知识更新。
5. Runtime Adapter：连接 DSH；将来可连接其他 Agent Runtime。
6. Persistence：关系数据、文件制品、检索索引、审计记录。
7. Integration：Git、文件导入、通知、未来 Jira/CSM/MCP。

## 3. 强制边界

- Agent 通过受控 API/工具调用领域操作，不允许直连生产数据库。
- 结构化状态由关系数据库保存；原始文件放 Artifact Storage；知识文档保留可读版本；向量索引是派生数据。
- Agent Runtime 的 Session ID 不能作为 WorkItem ID 或 Project ID。
- 任何 Agent 工具都必须有输入 Schema、输出 Schema、权限、风险等级、审计和超时策略。
- 业务逻辑不得藏在模型提示词里；提示词不能代替状态机和权限控制。

## 4. 建议初始架构选项

优先验证：Mammoth Domain Core/API + 独立数据存储 + DSH Bundle/Plugin UI/Agent Tools。

不建议第一步重度 Fork DSH。先验证 out-of-tree 扩展。如果某个必要扩展点无法通过 Bundle/Profile/公开 API 实现，再以 ADR 说明为什么需要 Fork，以及维护成本和上游合并策略。

## 5. 技术栈选择原则

由于尚未完成 DSH Spike，不在此锁死具体前后端、ORM、关系库、向量库。选型必须用 ADR 记录：
- 团队熟悉度和 Vibe Coding 支持度；
- DSH API 兼容性；
- 本地桌面/离线和未来服务器模式；
- 数据迁移和备份；
- 权限和审计；
- 测试生态与可维护性；
- 授权协议与供应链风险。

## 6. 关键架构决策待办

- ADR-0001：DSH 集成策略（Bundle、Profile、Fork 或独立壳）。**Accepted v1.0**
- ADR-0002：应用语言/框架及 UI 扩展边界。**Accepted v1.0**
- ADR-0003：关系数据库、ORM 和迁移机制。**Accepted v1.0**
- ADR-0004：文件存储与共享工作区。**Accepted v1.0**
- ADR-0005：全文/向量检索引擎。**Accepted v1.0**
- ADR-0006：身份认证、组织隔离和权限模型。**Accepted v1.0**
- ADR-0007：项目代码基线、Git Worktree 和知识更新机制。**Accepted v1.0（2026-11-13）** → `decisions/ADR-0007-GIT-BASELINE.md`
- ADR-0008：项目工作区与正式知识空间。**Accepted v1.0（2026-11-13）** → `decisions/ADR-0008-WORKSPACE.md`
- ADR-0009：文档解析（OPEN）；ADR-0010：需求上下文基线（Accepted v1.0）
- ADR-0011：外部系统集成与凭据边界（**Proposed**，2026-11-13）→ `decisions/ADR-0011-EXTERNAL-INTEGRATION.md`
- ADR-0012：团队服务端部署与数据库演进（**Proposed**，PG 多实例=M5）→ `decisions/ADR-0012-TEAM-SERVER.md`；M5 计划 `docs/specs/M5-PHASE5-PLAN.md`
- ADR-0013：通知与外部发送（**Proposed**，站内默认/邮件企微配置启用）→ `decisions/ADR-0013-NOTIFICATIONS.md`

## 6A. M4 逻辑组件（S0 草案）

```text
SPA
 ├─ 通知中心（站内默认；邮件/企微状态只读展示）
 ├─ 导入中心（来源/批次/dry-run/待映射记录）
 ├─ 验收矩阵（业务目标/执行证据/偏差目标）
 └─ 组织与用户（org_admin/停用/重置/Agent 账号）

Domain Service
 ├─ identity/org：org_admin、用户生命周期、Agent 账号、会话撤销
 ├─ external：ExternalAdapter SPI、CSV/JSON 导入、Jira 文件映射、幂等/溯源
 ├─ notification：事件→收件人、站内通知、邮件/企微渠道（默认关闭）
 ├─ jobs：server_job worker（仅 writer；重试/死信）
 ├─ agent：MCP 只读 + 本地审计 journal + writer 入库
 └─ delivery：BG-01 zip、BG-03 矩阵

边界：单节点 writer + SQLite；MCP 只读；无出站（除非渠道/API 配置启用）；PG/多实例=M5
```

新增外部依赖（计划）：`spring-boot-starter-mail`（邮件，配置后启用）、CSV 解析（`commons-csv` 或内置，S3 评审）、GreenMail（仅测试，Apache-2.0）。所有依赖需来源/许可证/漏洞检查。

## 6B. M5 目标形态（规划，2026-11-13）

```text
浏览器/客户端 → 反向代理 TLS → 应用实例 ×N（无本地单点状态）
                                      ├─ PostgreSQL（权威业务数据；Flyway db/migration-pg）
                                      ├─ 共享 Artifact Store（对象存储优先；内容寻址/sha256）
                                      ├─ 共享会话/登录限速（DB/共享存储）
                                      ├─ 多实例任务队列（SKIP LOCKED + 租约/幂等/死信）
                                      └─ 备份/监控/审计归档
```

- 目标：消除单节点假设（实例锁、SQLite FTS5、本地 Artifact、进程内限速、单进程 worker）。
- 不在 M5：双向同步、应用内 LLM/AgentRun、多租户、客户外发；另立 ADR/里程碑。
- 详见 `docs/specs/M5-PHASE5-PLAN.md` 与 ADR-0012。

## 7. 可观测性

每个 Agent Run 记录：发起人、项目、工作项、运行时/模型版本、提示模板版本、项目基线、知识文档版本、工具调用摘要、输入/输出产物、审批、耗时、状态和错误。禁止把模型隐藏思维链作为审计数据；保留可解释的结论、证据和执行轨迹即可。
