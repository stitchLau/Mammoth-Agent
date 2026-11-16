# Spike A 环境与回滚说明

本文件记录 Spike A 验证结束时本机环境的实际状态与回滚/清理方式。**所有数据均为合成数据。**

## 1. 运行中的进程

| 组件 | 说明 | 端口 |
|---|---|---|
| DSH Desktop | `desktop` profile，`0.2.2-rc.2`；窗口为真实安装应用 | Web carrier `127.0.0.1:3802`（以实际为准） |
| Mammoth Domain Service（writer） | 隔离 data root：`%TEMP%\mammoth-spike-data\root`；列出的 jar 为 `services/domain/target/mammoth-domain-service.jar` | `127.0.0.1:18090` |
| Proposal 适配器（Spike-only） | `spike/dsh-desktop-integration/proposal-stub.mjs`；状态 `%TEMP%\mammoth-spike\proposals.json`；审计 `%TEMP%\mammoth-spike\proposal-audit.jsonl` | `127.0.0.1:18091` |
| Mammoth Domain MCP（只读） | 由 DSH `mcp-client` 行以 stdio 启动的 Java 子进程（随 DSH 生命周期） | 无监听端口 |

停止（PowerShell；以实际 PID 为准）：

```powershell
# 由 18090/18091 反查 PID 后停止
Get-NetTCPConnection -LocalPort 18090,18091 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

## 2. DSH Profile 侧

- `@mammoth/dsh-bundle` 以 `link:` 装入 `C:\Users\<user>\.dsh\profiles\desktop`（`package.json` 的 `dsh.profile.bundles` 与 `dependencies`）。
- 安装前清单备份：`%TEMP%\mammoth-spike\profile-backup\`（`package.json`/`cordis.patch.yml`/`cordis.yml`/`pnpm-lock.yaml`/`pnpm-workspace.yaml`）。
- 生成的本机配置（git-ignore）：`integrations/dsh/mammoth-bundle/local/config.json`、`cordis.patch.yml`、`<stateDir>/credentials.json`、`<stateDir>/state.json`。

回滚：

```json
{ "action": "remove_bundle", "target": "@mammoth/dsh-bundle" }
```

或用备份覆盖 `%USERPROFILE%\.dsh\profiles\desktop` 下对应清单。卸载后：

- 左栏 Mammoth 入口、Mammoth 工具、MCP 工具、`preset-mammoth` 行应同时消失；
- 删除 bundle `local/`、`%TEMP%\mammoth-spike`、`%TEMP%\mammoth-spike-data`（含合成数据库/审计/锁）；
- 停止 18090/18091 进程；
- 如需删除证据目录：`docs/evidence/2026-11-13-client-arch/spike-A/`（注意：这是交付物，非临时文件）。

## 3. Windows 文件权限修复备份

验证期间 `docs/`、`docs/evidence/` 曾因缺少 WRITE_OWNER 被 sandbox 拒绝写入；已按 DSH 官方
`diagnose-windows-sandbox-acl` 流程修复（**仅补齐当前用户完全控制，不改文件内容与所有者**）。
备份与独立回滚脚本位于：`E:\MyProj\MommothAgent\code\Mammoth-Agent\.acl-recovery\`。
每个脚本形如：

```powershell
pwsh -NoProfile -File '<.acl-recovery>\acl-backup-<id>.json.ps1' -Path '<path>' -AllowRoot '<workspace>' -Restore '<.acl-recovery>\acl-backup-<id>.json'
```

报告文件：`.acl-recovery\acl-report-*.jsonl`（`SUMMARY FIXED=0 GRANTED=2 REFUSED=0 RESTORED=0`）。

## 4. 合成数据清单（仅隔离 data root）

- 项目：`SPIKE1 Spike Demo Project`（`a899ed37-7e3a-488c-9c1b-16c97bfe0b11`），客户 `Spike Demo Customer`；
- 需求：`REQ-0001`（原始）、`REQ-0002`（Spike A 提案审批后由 Domain 写入）；
- 知识：`Spike Agent Knowledge Contract`（verified）、`Spike Draft Must Not Leak`（draft）；
- 账号：`spike_admin`（人类 owner）、`spike_reviewer`（人类 reviewer/读取身份）、`spike_agent`（agent，只读，不可登录）；
- 提案：`50b44ed0-7ffd-476f-8e1d-d6c2711ee43a`（approved → `REQ-0002`）。

## 5. 安全提醒

- 合成的读取/approver 密码只存在于仓库外本机文件与进程环境；交付文档/截图不含密码；
- 不把这些凭据写入 DSH 提示词、会话日志、仓库或知识库；
- 正式生产不得复用本 Spike 的"预置人类凭据在本地适配器"做法，必须由独立 ADR 定义服务身份与四眼机制。
