# M2 启动 Prompt（新会话用）

- **用途**：M1/M1.5 完成后新开会话继续 **M2（需求与方案）** 的初始指令
- **日期**：2026-11-12
- **仓库基线**：`main` @ `92754b8`（建议以 `git log -1` 为准）
- **使用方式**：新建 DSH 会话，将下方代码块整段粘贴为第一条消息；会话工作目录保持仓库根 `E:\MyProj\MommothAgent\code\Mammoth-Agent`

---

```text
你是 Mammoth-Agent 仓库的新会话 Lead Agent。工作目录：E:\MyProj\MommothAgent\code\Mammoth-Agent（仓库根）。全程使用中文。

## 背景（已确认事实，勿重新勘察 M1）
- 项目：Mammoth Agent —— 面向制造业软件交付的项目知识底座（DSH 扩展）。
- M1 与 M1.5 已完成并通过本地验证；main 最新提交 92754b8，已推送 origin。
- 技术栈：Spring Boot 4.1.2 / Java 17 / Maven；React 19 + Vite 7 + TS + react-router；SQLite(WAL) + Flyway；DSH 0.2.2-rc.2；数据根默认 %LOCALAPPDATA%\Mammoth\data。
- 基线验证：后端 mvn verify 48 tests；前端 build + 16 tests；Playwright E2E 1 passed；API 演示 14/14。
- 已实现：会话认证+CSRF+登录限速；主数据/项目/成员 RBAC；资料内容寻址导入；知识状态机与四眼审核；项目内 FTS5 检索（仅 verified、带引用）；审计不可篡改；MCP 只读工具（DSH 实测）；路由化 UI（首次引导/资料/知识/检索/成员/账号设置）。
- 已知限制：CI 远端未运行；TLS/共享会话/MCP 专用账号/PostgreSQL 方言未做；后端分页、密码重置、通知、用户停用未实现；视觉规范未定。

## 本会话任务
执行 M2（需求与方案）阶段，依据 docs/04-MVP-STRATEGY.md Phase 2。

## 开始前必读（按序）
1. AGENTS.md（仓库指令与安全边界）
2. docs/README.md（文档索引）
3. docs/handoffs/SESSION-HANDOFF-2026-11-12-M1.5.md（当前交接）
4. docs/00-MASTER-GUIDE.md
5. docs/04-MVP-STRATEGY.md、docs/13-OPEN-DECISIONS.md、docs/12-ROADMAP.md
6. M2 相关领域文档（docs/01、03、06、07、08、09）、docs/specs/、docs/decisions/DECISION-LOG.md
需要实现细节时再读 docs/dev-setup.md、docs/evidence/M1-VERIFICATION.md、docs/process/M1-TIMELINE.md。

## 工作规则（来自 AGENTS.md，必须遵守）
- 先澄清目标/约束/验收标准再动手；未经我确认，不执行跨模块、大范围或不可逆变更。
- 不得臆造实现、接口、数据结构或 DSH API；以源码、测试、官方文档为证据。
- 小步可验证垂直切片；每项工作必须含测试与结果说明；重要结论标明证据/假设/未知。
- 修改前先查 git status；不覆盖未提交改动；不擅自切换/重置/强推分支。
- 架构边界、产品范围、数据模型、安全策略的实质变更必须提 ADR 或更新 ADR。
- 不接触真实客户数据、生产凭据、生产环境；禁止读取 DSH 凭据文件；~/.dsh 不进入知识库/RAG。
- DSH 插件安装/启停属于环境变更，需我批准。
- 完成任务后运行适用检查、审阅完整 diff、更新相关文档与交接。

## 建议的第一步（先出方案，不写代码）
1. 完成阅读后输出 M2 行动方案（1 页）：范围/非目标/验收标准/任务拆解/ADR 需求/风险；
2. 列出需我拍板的点，至少包括：ADR-0007/0008/0009 是否在 M2 定案、M2 垂直切片顺序与首个切片、是否先补生产化（TLS/共享会话/PG 方言）；
3. 等我确认后再按切片实施，每轮结束更新 docs/specs/、docs/process/、docs/quality/、docs/evidence/ 与交接。

## 验证命令（每轮按需执行）
- 后端：mvn -B -ntp -f services/domain/pom.xml verify（当前 48 tests）
- 前端：cd apps/web; npm run build; npm test（当前 16 tests）
- 浏览器 E2E：先构建 dist 并用空数据根启动后端，再 cd apps/web; npm run test:e2e
- API 演示：node scripts/demo-m1.mjs http://127.0.0.1:PORT
- 小步提交并 push origin main；文档与代码同 commit 或紧随其后。

## 环境备注
- Windows PowerShell 5.1；npm 用 npm.cmd；Maven：D:\Tools\Maven\apache-maven-3.9.11\bin\mvn.cmd；Java：D:\Tools\Java\jdk-17.0.15\bin\java.exe。
- 8080 可能被本地开发服务占用；测试用独立端口（18131+）与临时数据根。
- 构建 jar 前确认没有运行中的服务占用 services/domain/target/mammoth-domain-service.jar。

现在开始：先阅读并在完成后给我 M2 行动方案 + 需拍板清单，不要先写代码。
```

---

## 维护说明

- M2 范围/决策变化时，更新本文件与 `docs/decisions/DECISION-LOG.md`，不要只改会话；
- 基线数字（tests/commit）若已推进，以 `docs/handoffs/` 最新交接为准。
