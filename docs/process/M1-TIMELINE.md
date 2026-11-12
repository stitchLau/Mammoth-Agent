# M1 阶段执行时间线（过程记录）

- **范围**：M0 收尾 → M1 Phase 1（T1–T13）→ M1.5 UI 产品化 → M1.5 收尾
- **状态**：M1 与 M1.5 均已完成并通过本地验证（2026-11-12）
- **相关**：计划 [`docs/specs/M1-PHASE1-PLAN.md`](../specs/M1-PHASE1-PLAN.md)、验收 [`docs/M1-ACCEPTANCE.md`](../M1-ACCEPTANCE.md)、UI [`docs/specs/UI-SPEC.md`](../specs/UI-SPEC.md)、变更 [`CHANGELOG.md`](../../CHANGELOG.md)、缺陷 [`docs/quality/M1-BUGS.md`](../quality/M1-BUGS.md)
- **证据**：每个提交哈希可用 `git show <hash>` 复核；验证命令与数字见 [`docs/evidence/M1-VERIFICATION.md`](../evidence/M1-VERIFICATION.md)

## 1. 阶段记录

| 阶段 | 提交 | 关键动作 | 验证 | 遗留/备注 |
|---|---|---|---|---|
| M0 收尾 | `9960924` | M0 交接、路线图状态、M1 Phase 1 计划草案 | 文档审阅 | 待负责人批准计划 |
| M1 T1 骨架 | `8202143` | 接受 ADR-0002..0006；Spring Boot 4.1.2 + Java 17 + Maven、Vite/React SPA、`.gitignore`、CI | `mvn verify`、`npm build`/`test` 通过 | 认证/数据根未做 |
| M1 T2/T3 数据根与迁移 | `dfed52e` | 数据根解析/自检（拒 DSH Home、源码、安装目录）、SQLite WAL、Flyway V1（14 表） | 冷启动建目录；误配启动失败；迁移幂等 | PostgreSQL 方言延后 |
| M1 T4 主数据与项目 | `df09a91` | 用户/客户/产品/项目/成员、`AuthorizationService` 默认拒绝、审计 | 项目绑定客户、角色用例 | 身份仍为临时 header |
| M1 T5 Artifact | `377eeb7` | 内容寻址存储、SHA-256、原子移动、只读、去重、项目资料 UI | 去重/篡改检测/元数据测试 | — |
| M1 T6/T8 知识与检索 | `62331db` | 知识状态机（draft→needs_review→verified）、四眼审核、sourceRefs、FTS5 trigram 检索、引用 chunk | 非法跳转拒绝；自审拒绝；越权空结果 | 向量检索延后 |
| M1 T9/T10/T11 | `a95d3d7` | MCP stdio 只读工具（2 个）、权限隔离专项测试、演示脚本、DSH 真实会话接入 | 演示 14/14；DSH 调用中文与引用正常；MCP UTF-8 修复 | MCP 进程持有 jar |
| M1 正式认证 + T7 + 验收 | `22e4bc4` | Spring Security 会话/CSRF/登录限速、bootstrap 首账号、注销撤销；审计查询 + V3 触发器禁止改删；`M1-ACCEPTANCE.md` | 39→43 tests；认证版演示 14/14 | 前端知识视图待补 |
| M1 UI 补全 + SPA 托管 | `364dd82` | 前端知识创建/审核/引用视图、项目创建、`SpaWebConfig` 后端托管 SPA、验收更新 | 43 tests；前端 7 tests；`GET /` 200 + assets 200；演示 14/14 | 浏览器点击未验证 |
| UI 规格 | `fcfb80b` | 路线 A 决策、信息架构/页面/权限矩阵、M1.5 清单与待决项 | 文档审阅 | 5 个待决项 |
| M1.5 首批 | `47cd3dd` | react-router 路由化、AppShell/项目侧栏、首次引导、知识详情/编辑/审核、成员页、统一状态、Playwright E2E、`PUT` 草稿编辑、`GET /api/setup/status`、SPA 深链接回退 | 后端 44 tests；前端 13 tests；**E2E 1 passed**；演示 14/14 | 修 SPA 深链接 401、上传丢文件 |
| M1.5 收尾 | `3591b40` | 账号设置（显示名/改密并撤销会话）、成员角色变更/移除（最后 owner 保护）、新建用户并加入、客户端分页、E2E 入 CI | 后端 **48 tests**；前端 **16 tests**；E2E **1 passed（4.5s）**；演示 14/14 | 修改密后登出 401 提示丢失 |

> 上表提交哈希均来自 `git log`（2026-11-12）；同一提交可能同时覆盖多个任务。

## 2. 需求变更与决策（过程）

| 时间 | 变更/决策 | 记录位置 |
|---|---|---|
| 2026-11-12 | 产品代码同仓、允许 `.gitignore` 与最小 CI | 会话确认；`DECISION-LOG` D-002 |
| 2026-11-12 | 数据根方案：默认 OS 应用数据目录 + 可配置 + 启动自检 | `DECISION-LOG` D-003；`docs/dev-setup.md` |
| 2026-11-12 | DSH 版本对齐 `0.2.2-rc.2`；不验证 Fork 继承 | `DECISION-LOG` D-004 |
| 2026-11-12 | UI 定位为正式产品基座（路线 A） | `DECISION-LOG` D-005；`UI-SPEC` v0.2 |
| 2026-11-12 | M1.5 默认项（路由/Markdown/视觉/Playwright/独立 SPA） | `DECISION-LOG` D-006 |

## 3. 验证证据汇总（最终）

- 后端：`mvn -B -ntp -f services/domain/pom.xml verify` → **BUILD SUCCESS，48 tests**
- 前端：`npm run build` 通过；`npm test` → **16 tests passed**
- 浏览器 E2E：`npm run test:e2e` → **1 passed（4.5s）**（setup→项目→资料→知识→不可自审→改密→reviewer 审核→检索引用）
- API 演示：`node scripts/demo-m1.mjs` → **14/14**
- SPA 托管：`GET /projects` → 200 且返回 index.html；`/assets/*.js` → 200
- 证据归档：[`docs/evidence/M1-VERIFICATION.md`](../evidence/M1-VERIFICATION.md) 与 `docs/evidence/screenshots/`

## 4. 未完成/未验证（如实记录）

- CI 远端 Runner 未实际运行（workflow 已配置；本地同步骤通过）；
- 生产化项：TLS、共享会话/限速存储、MCP 专用只读账号、PostgreSQL 方言迁移；
- 后端分页、密码重置、通知、用户停用未实现；
- 视觉/品牌规范未评审；浏览器 E2E 自动化通过，但人工视觉验收仍建议补一次。
