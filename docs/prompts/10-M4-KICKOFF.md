# M4 启动 Prompt（新会话用）

- **用途**：M1–M3（含 M3.5 补强）完成后新开会话继续 **M4（企业集成与协同）** 的初始指令
- **日期**：2026-11-13
- **仓库基线**：`main` @ `1f33a44`（M3.5 收尾；文档补全后可能前进，以 `git log -1` 为准）
- **使用方式**：新建 DSH 会话，将下方代码块整段粘贴为第一条消息；会话工作目录保持仓库根 `E:\MyProj\MommothAgent\code\Mammoth-Agent`

---

```text
你是 Mammoth-Agent 仓库的新会话 Lead Agent。工作目录：E:\MyProj\MommothAgent\code\Mammoth-Agent（仓库根）。全程使用中文。

## 背景（已确认事实，勿重新勘察 M1–M3）
- 项目：Mammoth Agent —— 面向制造业软件交付的项目知识底座（DSH 扩展）。
- M1、M1.5、M2、M3（含 M3.5 补强）已完成并通过本地验证；main 已推送 origin（以 git log -1 为准）。
- 技术栈：Spring Boot 4.1.2 / Java 17 / Maven；React 19 + Vite 7 + TS + react-router；SQLite(WAL) + Flyway（schema v6）；DSH 0.2.2-rc.2；数据根默认 %LOCALAPPDATA%\Mammoth\data。
- 基线验证（真实执行）：后端 mvn verify 89 tests；前端 build + 25 tests；Playwright E2E 3 passed（M1/M2/M3）；M3 API 演示 25/25；脱敏项目验收演练 18/18；ADR-0008 导出/导入/备份演练 9/9。
- M1–M3 已实现：认证/CSRF/限速/RBAC/审计不可篡改；资料内容寻址；知识四眼审核、FTS5 引用检索、supersede/deprecated；需求 11 维就绪、上下文快照、方案单一模型双视图、一致性、四眼版本；只读 Git 基线、交接包、实施记录/偏差/验收矩阵、As-built 快照、知识更新提议；实例锁、网络盘拒绝、MCP 只读、导出/导入/备份/恢复/锁清理 CLI。
- 关键决策：ADR-0007（只读 Git + 人工不可变基线）、ADR-0008（单机单写者 + 导出导入 + 只读 MCP）、ADR-0010（需求上下文快照）已 Accepted；ADR-0009（文档解析）OPEN；ADR-0011（应用内 LLM）未立。
- 已知限制/技术债：真实客户项目验收未做（仅脱敏演练）；CI 远端未运行；BG-01 交接包文件导出、BG-02 Baseline 部署/DDL 版本、BG-03 矩阵业务目标/证据链接登记为 M3.5（docs/specs/M3.5-BACKLOG.md）；TLS/共享会话/PG 方言/后端分页/密码重置/通知/用户停用未做；导出不加密。
- 安全边界：不接触真实客户生产数据、生产凭据、生产环境；禁止读取/导出 ~/.dsh/.credentials.yaml 等凭据；~/.dsh 不进入知识库/RAG；DSH 插件安装/启停需批准。

## 本会话任务
执行 M4（企业集成与协同）阶段，依据 docs/04-MVP-STRATEGY.md Phase 4 与 docs/12-ROADMAP.md M4：
Jira/CSM、MCP、企业微信/邮件、外部 Agent 适配、多用户共享、组织管理/权限细化、服务端任务运行。

## 开始前必读（按序）
1. AGENTS.md（仓库指令与安全边界）
2. docs/README.md（文档索引）
3. docs/handoffs/SESSION-HANDOFF-2026-11-13-M3.md（当前交接，含 M3.5）
4. docs/00-MASTER-GUIDE.md
5. docs/04-MVP-STRATEGY.md、docs/12-ROADMAP.md、docs/13-OPEN-DECISIONS.md
6. docs/03-DOMAIN-MODEL.md、docs/07-WORK-ITEM-LIFECYCLE.md、docs/08-SECURITY-AND-TRUST.md、docs/06-KNOWLEDGE-AND-RAG.md、docs/09-QUALITY-AND-TESTING.md
7. docs/M3-ACCEPTANCE.md、docs/specs/M3.5-BACKLOG.md、docs/specs/M3-PHASE3-PLAN.md §10–§12
8. decisions/ADR-0007-GIT-BASELINE.md、decisions/ADR-0008-WORKSPACE.md、decisions/ADR-0010-M2-CONTEXT-BASELINE.md、docs/decisions/DECISION-LOG.md
需要实现细节时读 docs/dev-setup.md、docs/evidence/M3-VERIFICATION.md、源码与测试。

## 工作规则（来自 AGENTS.md，必须遵守）
- 先澄清目标/约束/验收标准再动手；未经我确认，不执行跨模块、大范围或不可逆变更。
- 不得臆造实现、接口、数据结构、外部系统 API 或 DSH API；以源码、测试、官方文档为证据。
- 小步可验证垂直切片；每项工作必须含测试与结果说明；重要结论标明证据/假设/未知。
- 修改前先查 git status；不覆盖未提交改动；不擅自切换/重置/强推分支。
- 架构边界、产品范围、数据模型、安全策略的实质变更必须提 ADR 或更新 ADR。
- 不接触真实客户数据、生产凭据、生产环境；外部系统调用/发送（邮件、企微、Jira/CSM 写操作）必须先设计边界并经我批准。
- 外部集成默认先做“只读导入/本地演示”，生产 API、双向同步、生产账号不得先行。
- 完成任务后运行适用检查、审阅完整 diff、更新相关文档与交接（docs/templates/SESSION-HANDOFF.md）。

## 建议的第一步（先出方案，不写代码）
1. 阅读后输出 M4 准入与行动方案（1–2 页）：范围/非目标/验收标准/切片顺序/ADR 需求/风险/测试策略。
2. 先明确 M4 的前置决策，至少包括：
   - 集成形态：只读文件导入（CSV/JSON）→ 只读 API → 双向同步的推进顺序；是否先做 Jira/CSM 中的哪一个；
   - 凭据与数据边界：凭据管理方式（环境变量/密钥服务/人工配置）、允许出网范围、脱敏规则、失败补偿与审计；
   - 服务端/多用户形态：何时引入 TLS、共享会话、PostgreSQL 方言、部署与备份；与 ADR-0003/0008 的关系；
   - MCP/外部 Agent：是否补专用只读账号、交接包文件导出（BG-01）是否作为前置；
   - 通知渠道：先站内通知还是企微/邮件；发送审批与模板；
   - M3.5 BG-01–BG-03 的处理顺序。
3. 等我确认方案与 ADR 方向后，再按切片实施；每轮结束更新 docs/specs/、docs/process/、docs/quality/、docs/evidence/ 与交接。
4. 不要在本条消息后直接写业务代码；先给方案和需我拍板的清单。

## 验证命令（每轮按需执行）
- 后端：mvn -B -ntp -f services/domain/pom.xml verify（当前 89 tests）
- 前端：cd apps/web; npm run build; npm test（当前 25 tests）
- 浏览器 E2E：先构建 dist 并用空数据根启动后端，再 cd apps/web; npm run test:e2e（当前 3 passed）
- API 演示：node scripts/demo-m3.mjs http://127.0.0.1:PORT
- 脱敏验收：node scripts/m3-acceptance-drill.mjs（18/18；需 MAMMOTH_JAVA 指向 java）
- 导出/导入/备份：node scripts/admin-drill.mjs（9/9）
- 小步提交并 push origin main；文档与代码同 commit 或紧随其后。

## 环境备注
- Windows PowerShell 5.1；npm 用 npm.cmd；Maven：D:\Tools\Maven\apache-maven-3.9.11\bin\mvn.cmd；Java：D:\Tools\Java\jdk-17.0.15\bin\java.exe。
- 8080 可能被本地开发服务占用；测试用独立端口（18131+）与临时数据根。
- 构建 jar 前确认没有运行中的服务占用 services/domain/target/mammoth-domain-service.jar。
- 数据根写实例单写者；导出/导入/备份需停写实例；恢复目标必须为空目录；锁清理用 --admin-clear-lock 或 scripts/clear-data-root-lock.ps1。
- 当前会话为 danger-full-access；CI 远端仍未运行，不要把“本机通过”表述为“CI 通过”。

现在开始：先阅读并在完成后给我 M4 准入方案 + 需拍板清单，不要先写代码。
```

---

## 维护说明

- M4 范围/决策变化时，更新本文件与 `docs/decisions/DECISION-LOG.md`，不要只改会话；
- 基线数字（tests/commit）若已推进，以 `docs/handoffs/` 最新交接与 `docs/M3-ACCEPTANCE.md` 为准；
- M4 方案与 ADR 落盘后，应新增 `docs/specs/M4-PHASE4-PLAN.md` 与对应 `decisions/ADR-0011+`。
