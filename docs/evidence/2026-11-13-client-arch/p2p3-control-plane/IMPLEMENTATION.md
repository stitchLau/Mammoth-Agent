# ADR-0015 P2/P3 实施与验证记录

- 日期：2026-11-16（合成隔离环境，127.0.0.1:18090）
- 分支：`feature/adr-0015-p1-controlled-write`、`feature/adr-0015-p3-bundle-integration`（本地）
- 相关：`decisions/ADR-0015-AGENT-CONTROLLED-WRITE-AND-SERVICE-IDENTITY.md`、`docs/specs/ADR-0015-P1-CONTROLLED-WRITE-PLAN.md`、`docs/specs/ADR-0015-P2-APPROVAL-SERVICE.md`

## P2 已验证事实
- apply token 文件仅 Administrators/SYSTEM 全权 + `MammothApproval` 读取；Agent 交互用户（Lau，非管理员）读取/列目录 → Access denied。
- 用 propose token 调 `POST /api/agent/proposals/{id}/apply` → 401 `invalid approval service token` + deny 审计。
- 人类会话批准后，SYSTEM 任务运行的审批服务 watch 自动 apply；Domain 生成 `REQ-0004`/`REQ-0005`，`created_by=spike_admin`，审计三段完整。
- 审批服务 CLI：无 token 文件 exit 2（fail-closed）；Domain 拒绝/失败 exit 1。

## P3 已验证事实
- `/approval` 页面（Domain 提供）：200，内嵌 Domain 登录表单；人类登录 `spike_admin` 后浏览器点击「批准」成功。
- `/api/agent/proposals/mine` 带 propose token：200。
- DSH Host 工具 `mammoth_propose_work_item`（P3 bundle，DSH 审批 allowed-once）创建提案后，人类浏览器批准 → 审批服务自动 apply → `REQ-0008`（`sourceNote=mammoth-proposal:648c0416-…`）。
- 之前机械链路：`REQ-0006`（API 创建提案）、`REQ-0007`（`/approval` 页面人类批准 → applied）。

## 已知问题/后续
- Domain `ProposalResponse` 缺 `title` 字段（提案标题在 payload），前端已用 summary 兜底。
- 测试脚手架用 Windows PowerShell 发中文 JSON 造成 `REQ-0007` 标题乱码（非产品缺陷；UTF-8 客户端正常）。
- P2.1：SYSTEM 任务身份需收紧回专用 MammothApproval；Domain writer 建议独立服务账号。
- 本轮为机械验证，`/approval` 页为最小实现，正式 IA/UX 归客户���迁移。
