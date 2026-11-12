# Session Handoff — 2026-11-12（M1 / M1.5 完成）

- **时间**：2026-11-12（Asia/Shanghai）
- **当前分支**：`main`，已与 `origin/main` 同步；最新提交 `3591b40`（M1.5 收尾）
- **状态**：**M1 与 M1.5 已完成并通过本地验证**；下一阶段为 **M2（需求与方案）**
- **本文件用途**：新会话交接入口；详细过程见 `docs/process/M1-TIMELINE.md`，证据见 `docs/evidence/M1-VERIFICATION.md`

## 1. 现在有什么

- **产品代码**：Spring Boot 4.1.2 / Java 17 Domain Service（`services/domain`）+ React 19 / Vite 7 SPA（`apps/web`，由后端托管）。
- **能力（M1 闭环）**：本地账号 + 会话认证 + CSRF + 登录限速；客户/产品/项目/成员与 RBAC；资料内容寻址导入（不可变、去重）；知识状态机与四眼审核（不可自审）；项目内 FTS5 检索（仅 verified，带引用）；审计追加写（DB 触发器禁止改删）；MCP 只读工具（DSH 真实会话已验证）。
- **UI（M1.5）**：路由化产品壳、首次引导、项目/资料、知识创建/编辑/审核、检索、成员与角色、账号设置、分页、统一状态组件；Playwright E2E 全流程通过。
- **文档**：M1 计划/验收、UI 规格、7 个 Spike、6 个 ADR、决策日志、缺陷台账、时间线、验证证据（含截图）、开发/运维与 DSH 接入指南。

## 2. 关键基线（用于继续验证）

| 项 | 值 |
|---|---|
| 后端 | `mvn -B -ntp -f services/domain/pom.xml verify` → 48 tests |
| 前端 | `cd apps/web && npm run build` / `npm test` → 16 tests |
| 浏览器 E2E | 后端空数据根 + `cd apps/web && npm run test:e2e` → 1 passed |
| API 演示 | `node scripts/demo-m1.mjs http://127.0.0.1:PORT` → 14/14 |
| MCP | `java -jar services/domain/target/mammoth-domain-service.jar --mcp` + `MAMMOTH_DATA_ROOT`/`MAMMOTH_MCP_AGENT_USER_ID`；接入见 `docs/dsh-mcp-integration.md` |

## 3. 已知限制（进入 M2 前需知晓）

- CI 的 `e2e` job 已配置但**远端未运行**；
- 生产化未做：TLS、共享会话/限速存储、MCP 专用只读账号、PostgreSQL 方言；
- 后端分页、密码重置、通知、用户停用未实现（当前分页为客户端）；
- 视觉/品牌未评审（人工视觉验收建议补一次）；
- DSH 插件启停属环境变更，需负责人批准（见 `AGENTS.md`）。

## 4. 下一步：M2（需求与方案）

按 `docs/00-MASTER-GUIDE.md` 与 `docs/04-MVP-STRATEGY.md` Phase 2 推进：
1. 读 `docs/01`–`09`、`docs/12-ROADMAP.md`、`docs/13-OPEN-DECISIONS.md`（ADR-0007/0008/0009 仍 OPEN）；
2. 产出 M2 规格（需求工作项、证据追加、方案版本、任务交接等）与必要 ADR；
3. 按垂直切片实施，继续保持：先测试/验收标准 → 实现 → 证据归档 → 交接。

## 5. 常用命令

```powershell
# 构建并启动（构建 SPA 后由后端托管 UI）
cd apps/web; npm run build; cd ../..
mvn -B -ntp -f services/domain/pom.xml package
java -jar services/domain/target/mammoth-domain-service.jar --server.port=8080 `
  --mammoth.data-root="$env:LOCALAPPDATA\Mammoth\data"

# 全量检查
mvn -B -ntp -f services/domain/pom.xml verify
cd apps/web; npm run build; npm test
```

## 6. 文档地图

- 总指导：`docs/00-MASTER-GUIDE.md`
- M1：`docs/specs/M1-PHASE1-PLAN.md`、`docs/M1-ACCEPTANCE.md`
- M1.5：`docs/specs/UI-SPEC.md`（§13 实施记录）
- 过程：`docs/process/M1-TIMELINE.md`、`docs/quality/M1-BUGS.md`
- 证据：`docs/evidence/M1-VERIFICATION.md` + `docs/evidence/screenshots/`
- 决策：`decisions/ADR-0001`–`0006`、`docs/decisions/DECISION-LOG.md`、`docs/13-OPEN-DECISIONS.md`
- 运维/接入：`docs/dev-setup.md`、`docs/dsh-mcp-integration.md`、`docs/demos/M1-DEMO.md`
