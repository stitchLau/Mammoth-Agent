# M4 API 演示（导入 + 通知）

本演示只使用合成数据，不访问真实 Jira/CSM/SMTP/企微。

## 启动

```powershell
$root = Join-Path $env:TEMP 'mammoth-demo-m4'
if (Test-Path $root) { Remove-Item -Recurse -Force $root }
java -jar services/domain/target/mammoth-domain-service.jar --server.port=8080 --mammoth.data-root=$root
```

要求**空数据根**（首次设置）；`8080` 被占用时用 `--server.port=<port>` 并设置 `MAMMOTH_BASE`。

## 运行

```powershell
node scripts/demo-m4.mjs
```

## 覆盖

1. `/api/health`；
2. 首个管理员 + 登录；
3. 项目/需求；
4. Jira 来源；
5. Jira 中文 CSV 预检（1 行接受）；
6. 应用导入（批次/去重）；
7. 待映射记录；
8. 映射为需求证据；
9. 知识提交审核 → reviewer 站内通知；
10. 通知已读。

预期：`passed: 8, failed: 0`。

## 限制

- 不覆盖矩阵 BG-03、执行回收、交付批准；这些由 `DeliveryWorkflowTest` / M3 E2E 覆盖。
- 不启用邮件/企微；真实渠道配置留 M4.5。
- 需要重启后端才能重复运行（首次设置只允许一次）。
