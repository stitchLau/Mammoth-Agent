# ADR-0015 P2：Approval 服务独立身份与凭据隔离

- 状态：**实现草案完成（代码/脚本/测试）；OS 账号与凭据的实际创建待负责人以管理员执行**（2026-11-16）
- 依据：`decisions/ADR-0015-AGENT-CONTROLLED-WRITE-AND-SERVICE-IDENTITY.md`（Accepted v1.0，决策 1/3/5）
- 关联：`docs/specs/ADR-0015-P1-CONTROLLED-WRITE-PLAN.md`（P1 已完成）、`docs/08-SECURITY-AND-TRUST.md`
- 注意：本机当前会话**不是管理员**（`DESKTOP-9OP303T\Lau`, admin=False），因此 P2 的"创建独立本地账号 + ACL + 计划任务"只能由负责人在提权 PowerShell 中执行；本会话已完成代码、脚本、测试与非提权可验证部分。

## 1. 目标

关闭 P1 遗留的边界缺口："临时 Proposal 适配器与 Agent 同 OS 用户，token/凭据对 Agent shell 可读"。

P2 的边界模型：

| 身份 | 持有凭据 | 能做什么 | 不能做什么 |
|---|---|---|---|
| Host/Agent（交互用户 `Lau`） | **propose token**（`X-Mammoth-Agent-Token`） | 创建提案、读取项目数据 | **不能 apply**（无 apply token）、不能审批（人类会话） |
| 人类审批人 | Domain 登录会话/RBAC（浏览器） | approve/reject | 不能绕过人工审批直接写 |
| **Approval 服务（独立 Windows 本地账号 `MammothApproval`）** | **apply token**（`X-Mammoth-Apply-Token`，仅服务账号可读的 ACL 文件） | 轮询"已批准队列"并 apply、写审计 | 不能 propose、不能审批 |
| Domain Service | 验证两套服务 token | 校验、落库、审计 | —— |

关键点：**Agent shell 即使能执行任意命令，也读不到 apply token**（文件 ACL 只允许 `MammothApproval` 读取），因此无法直接调用 Domain 的 apply 端点；它只能 propose，必须等人类批准后由 Approval 服务执行。

## 2. 已实现（代码/脚本/测试）

1. **Domain 双 token 分离**
   - `mammoth.agent-write.agent-token`（env `MAMMOTH_AGENT_WRITE_TOKEN`）：propose 用；
   - `mammoth.agent-write.apply-token`（env `MAMMOTH_AGENT_WRITE_APPLY_TOKEN`）：apply 与"已批准队列"用；
   - 任一方未配置即 503 fail-closed；跨用（agent token 调 apply / apply token 调 propose）一律 401 并记 deny 审计；
   - 新增审批服务队列端点：`GET /api/agent/proposals/approved?projectId=`（apply token）。
2. **Approval 服务 CLI**（`ApprovalServiceMain`，可通过 `java -jar mammoth-domain-service.jar --approval-service ...` 运行）
   - 单提案模式：`--proposal-id <id>`；
   - 轮询模式：`--watch --project-id <id> [--once] [--poll-seconds 5]`，从队列取已批准提案逐个 apply；
   - token 只从 `--token-file`（或 `MAMMOTH_APPROVAL_TOKEN_FILE`）读取，缺失/空白退出码 2（fail-closed）；Domain 拒绝/提案失败退出码 1。
3. **Provisioning 脚本**（需管理员）：`scripts/windows/approval-service-provision.ps1`
   - 创建本地用户 `MammothApproval`（密码由负责人提供/生成，不落仓库）；
   - 创建 `C:\ProgramData\Mammoth\approval-service\`：`apply-token.txt`、`approval-service.config.json`、`apply-approved.cmd`；
   - `icacls` 严控：目录/文件移除继承，仅 Administrators/SYSTEM 全权 + 服务账号只读；
   - 可选注册计划任务 `\Mammoth\MammothApprovalApply`（开机启动，运行 watch 循环，`LogonType Password`）；
   - `-DryRun` 可在非管理员下预演（本会话已验证 exit 0）。
4. **Verification 脚本**（非提权可跑）：`scripts/windows/approval-service-verify.ps1`
   - 验证 Agent 用户**无法读取** apply token 文件；
   - 验证 CLI 在 token 文件缺失时 fail-closed（exit 2）；
   - 打印负责人/管理员应执行的剩余检查（ACL、Domain 队列、agent token 调 apply 必须 401、apply 成功与审计）。
5. **测试**：`ApprovalServiceMainTest`（token 文件/头/单提案/守卫/队列 watch-once）+ P2 双 token 用例；域服务全量 **168 tests** 通过。

## 3. 需要负责人以管理员执行（P2 收尾）

```powershell
# 1) 构建 jar（若尚未）
cd services/domain; mvn -q -DskipTests package

# 2) 提权 PowerShell 中执行（密码由你提供；不写入仓库）
.\scripts\windows\approval-service-provision.ps1 `
  -DomainBase http://127.0.0.1:8080 `
  -JarPath <repo>\services\domain\target\mammoth-domain-service.jar `
  -ProjectId <projectId> -ServiceAccountPassword (Read-Host -AsSecureString) `
  -RegisterScheduledTask

# 3) 为 Domain writer 设置 apply token（管理员读取 token 文件后设置环境变量），重启 Domain
$env:MAMMOTH_AGENT_WRITE_APPLY_TOKEN = Get-Content -Raw C:\ProgramData\Mammoth\approval-service\apply-token.txt

# 4) 以交互（Agent）用户执行边界自检
.\scripts\windows\approval-service-verify.ps1 -ProjectId <projectId> -DomainBase http://127.0.0.1:8080

# 5) 启动计划任务（或等开机）
Start-ScheduledTask -TaskName 'MammothApprovalApply'
```

## 4. 验证矩阵（P2 验收）

| # | 检查 | 期望 | 执行者 |
|---|---|---|---|
| V1 | Agent 用户读取 `apply-token.txt` | Access Denied | Agent 用户（verify 脚本） |
| V2 | `icacls` 显示仅 Administrators/SYSTEM/服务账号 | 无 Users/Everyone 继承 | 管理员 |
| V3 | CLI 无 token 文件 | exit 2，不访问 Domain | Agent 用户（verify 脚本） |
| V4 | agent token 调 `POST /{id}/apply` | 401 + deny 审计 | Agent 用户（curl） |
| V5 | apply token 调 `POST /api/agent/proposals` | 401 + deny 审计 | 管理员/Approval 服务 |
| V6 | 人类批准 → Approval 服务 apply | 提案 `applied` + Domain `createdBy`=人类 + 审计 apply(service) | 管理员 + Domain |
| V7 | 高风险提案仅 primary 批准 | apply 拒绝（409，等待第二人） | Agent/管理员 |
| V8 | 重复 apply | 幂等，无重复业务对象 | Approval 服务 |

## 5. 边界与残留风险

- **实测结果（2026-11-16）**：双 token 隔离、apply token 文件 ACL、Agent 用户读 token 被拒、人类批准后由独立进程自动 apply、审计三段、`createdBy`=人类均已实测通过。见 §7。
- **仍未用 DPAPI**：P2 采用"服务账号专属 ACL 文件"（ADR-0015 允许 OS 凭据存储/DPAPI；文件 ACL 是 P2 的等价隔离手段，写入 runbook）。P2.1 可改为 DPAPI 加密文件（由服务账号 DPAPI 保护），进一步降低管理员误拷贝风险。
- **任务身份偏差（待 P2.1 收紧）**：计划任务最终以 **SYSTEM** 身份通过任务 XML 注册（action 直接 `java.exe`），因为专用 `MammothApproval` 账号的 Task Scheduler `LogonUserEx` 反复失败（0x8007052E）。SYSTEM 仍不是 Agent 交互用户，但权限大于专用账号；P2.1 应改回专用账号（S4U 或修复任务凭据）。复现脚本：`scripts/windows/approval-service-task-system.ps1`。
- **Domain writer 运行在交互用户下（开发模式）**：Domain 进程环境中的 apply token 只用于验证；生产部署应让 Domain writer 也运行在独立服务账号（P2.1/P5）。
- **管理员=超级用户**：任何本地管理员都可以重置 ACL/读取 token；这是 OS 边界而非产品边界，P2 不解决"管理员恶意"。
- **本会话不是管理员**：V2/V4–V8 的最终执行与截图证据待负责人执行后补录（P2 验收记录）。

## 7. 实测记录（2026-11-16，合成环境）

| # | 检查 | 结果 |
|---|---|---|
| V1 | Agent 用户（`Lau`）读取 `apply-token.txt` / 列目录 | **Access denied**（边界成立） |
| V2 | `icacls` apply-token.txt | 仅 Administrators/SYSTEM 全权 + MammothApproval 读取 |
| V3 | CLI 无 token 文件 | exit 2，fail-closed（单元测试 + 手工） |
| V4 | 用 propose token 调 `POST /api/agent/proposals/{id}/apply` | **401** `invalid approval service token` + deny 审计 |
| V5 | apply token 调 propose | 单元测试 401（Domain 双 token 分离）；未在生产环境手工复测 |
| V6 | 人类会话批准 → 审批服务自动 apply | **通过**：提案 `applied`，Domain 生成 `REQ-0004`（手工 CLI）与 `REQ-0005`（watch 自动），`created_by`=人类 `spike_admin` |
| V7 | 高风险缺第二人 | 单元测试 403/不可 apply；未在真实环境跑高风险类型（P1 仅开放 `work_item.create`） |
| V8 | 重复 apply 幂等 | 单元测试通过；未手工复测 |
| V9 | 审批服务常驻任务 | SYSTEM + XML 任务运行中（java `--approval-service --watch`），批准后 ≤5s 自动 apply |

**审计样例（REQ-0005）**：`propose(agent)` → `approve(human)` → `apply(service)`，`seq=1/2/3`，`domain_object_id` 一致；deny 审计记录了无效 apply token 尝试。

## 6. 回滚

1. 停止并删除计划任务：`schtasks /End /TN MammothApprovalApply`、`schtasks /Delete /TN MammothApprovalApply /F`（或 `Unregister-ScheduledTask`）；
2. 删除 `C:\ProgramData\Mammoth\approval-service\`（token/配置/日志）；
3. 删除本地账号：`net user MammothApproval /delete`；
4. 清除 Domain 的 `MAMMOTH_AGENT_WRITE_APPLY_TOKEN` 并重启 Domain（apply 端点回到 503 fail-closed）；
5. 提案/审批/审计数据保留；未 apply 的提案保持 `approved`，可人工决定重试或拒绝。
