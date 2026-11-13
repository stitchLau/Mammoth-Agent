# M3 端到端演示（实施回收与知识闭环）

- **日期**：2026-11-13
- **脚本**：`scripts/demo-m3.mjs`（合成数据；会创建/删除临时 Git 仓库，不接触真实客户仓库）
- **对应规格**：`docs/specs/M3-PHASE3-PLAN.md`、`docs/evidence/M3-VERIFICATION.md` §7

## 1. 前置

- JDK 17、Node 24、**Git 2.30+ 可用**（脚本用 `git` 创建临时仓库）
- 已构建后端 jar 与前端 dist
- **空数据根**（脚本 bootstrap 首个账号）
- 独立端口（示例 18150）

## 2. 运行

```powershell
$root = Join-Path $env:TEMP ("mammoth-m3-demo-" + (Get-Date -Format 'HHmmss'))
java -jar services/domain/target/mammoth-domain-service.jar `
  --server.port=18150 --mammoth.data-root="$root"

# 另开终端
node scripts/demo-m3.mjs http://127.0.0.1:18150
```

## 3. 步骤与验证点（25 步）

| 阶段 | 验证 |
|---|---|
| 基础 | health / CSRF / 首个管理员 / 项目 + reviewer |
| Git 夹具 | 临时仓库初始化、提交、origin 远端（含伪造 token） |
| 仓库注册 | 归一化仓库根；**远程 URL 去 userinfo**（不含 token） |
| 基线 | commit/branch/complete；不可变；当前基线 |
| 需求与方案 | 需求 → 证据 → 11 维就绪 → 方案 → 结构 → 提交 → 作者自批 403 → reviewer 批准 |
| 交接包 | 含基线、任务、验收、只读约束 |
| 实施回收 | 新 commit → `from→to`、`diverged=false`、变更清单 |
| 验收矩阵 | covered=1、无未完成任务 |
| 偏差 | 记录"额外实现" |
| As-built | 草稿生成；作者自批 403；reviewer 批准 + Artifact |
| 知识提议 | 提议者自接受 403；reviewer 接受 → 知识草稿（来源 Artifact、绑定基线） |
| 过期提示 | 新基线后知识 `stale=true`，状态仍 draft |
| 审计 | 仓库/基线/实施/交付/知识动作可查；登出 401 |

## 4. 预期输出

```json
{ "base": "http://127.0.0.1:18150", "passed": 25, "failed": 0, "steps": [ ... ] }
```

## 5. 常见问题

- `git-fixture` 失败：确认 `git --version` 可用且 `os.tmpdir()` 可写。
- 基线捕获 409：仓库工作树不干净（演示仓库应为 clean；脚本在基线后才会提交）。
- 数据根非空：换新的临时目录。
