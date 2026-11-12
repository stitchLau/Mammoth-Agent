# M1/M1.5 验证证据归档

- **归档日期**：2026-11-12
- **代码基线**：`main` @ `3591b40`（M1.5 收尾；M1 验收基线 `364dd82`）
- **目的**：把本阶段的可复现验证结果落到仓库，避免"只在会话里说过"

## 1. 命令与结果

| 检查 | 命令（仓库根目录） | 结果 |
|---|---|---|
| 后端构建/测试 | `mvn -B -ntp -f services/domain/pom.xml verify` | **BUILD SUCCESS，48 tests，0 failures** |
| 前端构建 | `cd apps/web && npm run build` | 通过（`tsc --noEmit` + `vite build`，JS ≈ 294 KB / gzip ≈ 92 KB） |
| 前端单元测试 | `cd apps/web && npm test` | **3 files / 16 tests passed** |
| 浏览器 E2E | `cd apps/web && npm run test:e2e`（需后端 + 空数据根，见下） | **1 passed（4.5s）** |
| API 端到端演示 | `node scripts/demo-m1.mjs http://127.0.0.1:PORT` | **14/14，exit 0** |
| SPA 托管 | `GET /`、`GET /projects`、`GET /assets/*.js` | **200**（深链接返回 index.html） |
| MCP 协议 | `node services/domain/target/mcp-smoke.mjs <root> <ownerId> <projectId> <documentId>` | `protocolVersion 2024-11-05`，两个工具，检索 1 命中，`status=verified`，`refs=1` |

> 注：`mcp-smoke.mjs` 与 `seed-t9.mjs` 为本地临时驱动（`target/` 下，未入库）；协议行为由 `McpServerMainTest`/`McpToolServiceTest` 覆盖。

## 2. 浏览器 E2E 覆盖路径（`apps/web/e2e/m1-ui.spec.ts`）

1. `/setup` 创建首个管理员并自动登录；
2. 创建客户 → 项目 → 进入项目资料；
3. 上传资料（接口 200，列表可见）；
4. 创建知识草稿 → 提交审核（待审核）；
5. 创建者打开详情：审核按钮禁用 + "不能自审"提示；
6. 账号设置：改显示名生效；改密码 → 全部会话撤销 → 提示 → 新密码重登；
7. API 创建 reviewer 并加入项目；
8. reviewer 登录 → 审核通过（已验证）；
9. 检索命中并显示"引用 N 项"。

运行前置：后端使用**空数据根**启动（E2E 会创建临时数据），例如：

```powershell
java -jar services/domain/target/mammoth-domain-service.jar --server.port=18130 `
  --mammoth.data-root="$env:TEMP\mammoth-e2e"
cd apps/web; npm run test:e2e
```

## 3. 界面截图（人工视觉参考）

由 `apps/web/e2e/capture-screenshots.mjs` 在临时数据根上真实操作后截取（构造合成数据）：

- [`screenshots/00-login.png`](screenshots/00-login.png) — 登录页
- [`screenshots/01-setup.png`](screenshots/01-setup.png) — 首次引导（空库创建管理员）
- [`screenshots/02-projects.png`](screenshots/02-projects.png) — 项目列表
- [`screenshots/03-artifacts.png`](screenshots/03-artifacts.png) — 项目资料 + 上传
- [`screenshots/04-knowledge-self-review-blocked.png`](screenshots/04-knowledge-self-review-blocked.png) — 详情页：创建者不可自审（按钮禁用 + 原因）
- [`screenshots/05-search-citations.png`](screenshots/05-search-citations.png) — 检索结果与引用数

> 截图使用合成测试数据；界面为 M1.5 基线样式，视觉规范待定（见 `DECISION-LOG` Q-001）。
>
> 过程说明：首次截图时发现整站无样式（`main.tsx` 遗漏 CSS 导入，BUG-013），修复并重新构建后重拍。

运行截图脚本（需空数据根 + 已构建 dist）：

```powershell
java -jar services/domain/target/mammoth-domain-service.jar --server.port=18132 `
  --mammoth.data-root="$env:TEMP\mammoth-shots"
cd apps/web; node e2e/capture-screenshots.mjs
```

## 4. 未归档/未验证（如实说明）

- Playwright `trace.zip` 体积大且含完整网络记录，未入库；本地失败时保留在 `apps/web/test-results/`（已 gitignore）；
- GitHub Actions 的 `e2e` job **未在远端实际运行**，本地等价步骤通过；
- 人工视觉/交互评审未做（自动化 E2E 通过）；
- DSH 真实会话 MCP 调用证据为文字记录（`docs/dsh-mcp-integration.md` §3），未保存会话截图。
