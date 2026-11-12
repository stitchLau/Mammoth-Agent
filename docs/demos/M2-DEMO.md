# M2 端到端演示（需求与方案）

- **日期**：2026-11-12
- **脚本**：`scripts/demo-m2.mjs`（合成数据，仅本地；不接触真实客户数据/生产环境）
- **对应规格**：`docs/specs/M2-PHASE2-PLAN.md` §3 AC-1–AC-8

## 1. 前置

- 已构建后端 jar 与前端 dist：`mvn -B -ntp -f services/domain/pom.xml package`（含测试）与 `cd apps/web; npm run build`
- 一个**空数据根**（脚本会 bootstrap 首个账号，已有用户则失败）
- 独立端口（避免 8080 被本地开发占用）

## 2. 运行

```powershell
$root = Join-Path $env:TEMP ("mammoth-m2-demo-" + (Get-Date -Format 'HHmmss'))
java -jar services/domain/target/mammoth-domain-service.jar `
  --server.port=18132 --mammoth.data-root="$root"

# 另开终端
node scripts/demo-m2.mjs http://127.0.0.1:18132
```

## 3. 步骤与验证点（26 步）

| # | 步骤 | 验证 |
|---|---|---|
| 1–4 | health / CSRF / 首个管理员 / 登录 | 会话 Cookie 生效 |
| 5–6 | 项目 + reviewer 成员 + 资料上传 | 内容寻址 SHA-256 |
| 7 | 创建需求 | `REQ-0001`、`draft` |
| 8–10 | 两批证据（第二批引用 Artifact） | 批次 1→2、自动 `collecting` |
| 11 | 就绪检查（未填评估） | 11 条阻断原因 |
| 12–13 | 10 维确认 + `exceptions` 假设 + 决策责任人 | 非关键假设可放行 |
| 14 | 负责人确认就绪 | `ready_for_analysis` + 快照 v1 |
| 15 | 新建方案 v1 | 需求 `analyzing` |
| 16 | 空方案提交 | **409** `required_item_missing` |
| 17–19 | 业务/技术/共享设计项 + 验收标准 + 任务 | 一致性 `ok` |
| 20 | 业务/技术双视图渲染 | 同一模型；追溯矩阵 1 行 |
| 21 | 提交评审 | `in_review`，快照 v2 |
| 22 | 作者自批 | **403**（四眼） |
| 23 | reviewer 批准 | `approved`，需求 `approved` |
| 24 | 批准版本再编辑 | **409**（不可覆盖） |
| 25 | `new-version` | v2 草稿，需求 `analyzing` |
| 26 | v2 提交 + 批准 | v1 `superseded`、v2 `approved`；快照 3；审计可查；登出 **401** |

## 4. 预期输出

```json
{ "base": "http://127.0.0.1:18132", "passed": 26, "failed": 0, "steps": [ ... ] }
```

## 5. 常见问题

- 首步 `bootstrap-first-user` 失败：数据根非空（换新目录或删除临时数据根）。
- 端口占用：换端口并同步脚本参数。
- 中文乱码：PowerShell 控制台编码问题，脚本输出为 UTF-8 JSON；必要时重定向到文件查看。
