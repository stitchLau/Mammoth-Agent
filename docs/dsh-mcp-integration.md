# DSH ↔ Mammoth Domain Service（MCP 只读工具）接入指南

- **状态**：已实测（2026-11-12，DSH `0.2.2-rc.2` / desktop profile）
- **依据**：ADR-0001（MCP 边界）、ADR-0005（检索）、ADR-0006（身份）
- **模板**：[`integrations/dsh/mammoth-domain-mcp/`](../integrations/dsh/mammoth-domain-mcp/README.md)

## 1. 它做什么

把 Mammoth Domain Service 的**只读**能力通过 MCP stdio 暴露给 DSH Agent：

| 工具 | 作用 |
|---|---|
| `mcp__mammoth_domain__search_project_knowledge` | 在指定项目内检索已验证知识，返回可引用 chunk（documentId/chunkId/ordinal/text/sourceRefCount） |
| `mcp__mammoth_domain__get_knowledge_document` | 读取一篇已验证知识文档及其来源引用 |
| `mcp__mammoth_domain__get_requirement_context` | 读取一个需求工作项的证据批次、11 维就绪评估摘要与当前批准方案摘要（M2 新增） |

边界：**只读**、**仅 verified 知识**、**仅项目成员**、**不返回草稿**。需求上下文包含项目内部需求/证据，不包含知识草稿；Agent 没有写需求或方案的路径（M2 明确不做 Agent 直写，见 `docs/specs/M2-PHASE2-PLAN.md` §7）。

## 2. 接入步骤

1. 构建服务：`mvn -B -ntp -f services/domain/pom.xml package` → `services/domain/target/mammoth-domain-service.jar`
2. 用 Web 服务准备数据（示例脚本）：
   ```powershell
   $env:MAMMOTH_DATA_ROOT = Join-Path $env:TEMP "mammoth-mcp"
   java -jar services/domain/target/mammoth-domain-service.jar --server.port=8080
   node scripts/demo-m1.mjs http://127.0.0.1:8080
   ```
   记下输出中各用户/项目/知识文档的 id（演示脚本会创建它们；也可用 `POST /api/users` 自行创建）。
3. 选定 **agent 身份**：取一个本项目成员的用户 id（临时机制，见第 4 节）。
4. 复制模板并替换四个占位符：
   ```yaml
   command: '<JAVA_EXE>'                 # e.g. D:\Tools\Java\jdk-17.0.15\bin\java.exe
   args: ['-jar', '<MAMMOTH_JAR>', '--mcp']
   env:
     MAMMOTH_DATA_ROOT: '<DATA_ROOT>'     # 必须与 Web 服务一致
     MAMMOTH_MCP_AGENT_USER_ID: '<AGENT_USER_ID>'
   ```
5. 安装（二选一）：
   - 让 DSH Agent 调用 `plugin_manager` `action: install_bundle`，`target` 指向该目录；
   - 或在 DSH **Plugins → Add plugin** 中选择该本地目录。
6. 验证：新会话工具列表出现 `mcp__mammoth_domain__*`；调用检索应返回中文内容与引用。若启动失败，检查 `failOnStartupError: false` 下插件行状态与 MCP 进程 stderr。

## 3. 已验证结果（2026-11-12）

- `install_bundle` → `application: applied`，两个工具出现在会话中；
- 真实调用检索返回 1 条命中（document/chunk/sourceRefCount 完整），中文正常；
- `get_knowledge_document` 返回 `status=verified`、`reviewer`、`sourceRefs`；
- 非项目成员被拒绝；草稿不暴露；卸载后工具消失。

## 4. 安全说明（重要）

- HTTP 侧已使用正式认证（Spring Security 会话 Cookie + CSRF，2026-11-12 起）；MCP 入口**不经过 HTTP**，仍以 `MAMMOTH_MCP_AGENT_USER_ID` 指定本地用户身份。
- 该用户必须是项目成员；**不得**用 owner 身份做长期 Agent 身份，应为 Agent 建**专用只读账号**（项目 viewer 角色），并纳入审计。
- 仅 `verified` 知识对 Agent 可见；草稿/待审文档不会泄露。
- 工具只读，不提供写操作；写操作仍由人工在 Web/API 完成并留审计。
- 后续应将环境变量身份替换为 MCP 专属服务凭据（ADR-0006 后续任务）。

## 5. 故障排查

| 现象 | 原因 / 处理 |
|---|---|
| 工具不出现 | MCP 进程启动失败：检查 java 路径、jar 路径、数据根是否存在；查插件行状态与进程 stderr |
| 中文乱码 | 已修复（MCP 入口强制 UTF-8 输出；JVM 默认 GBK 曾导致乱码）；如再出现，确认 `McpServerMain` 未被改回 `System.out` |
| 检索为空 | 数据根不一致、知识未 verified、或项目/成员不匹配 |
| 构建 jar 失败（Windows） | 运行中的 MCP 进程锁定了 jar：先卸载 bundle，再构建，再重装 |
| 启动超时 | MCP 入口会启动完整 Spring 上下文（Flyway/JPA），首次约 2–4 秒；`dsh-mcp-client` 启动超时过短时可能首次不可用，重试或调整超时 |

## 6. 未验证 / 后续

- 多用户并发、MCP server 崩溃重连、工具调用超时策略未做专项验证；
- 正式认证替换临时身份头/环境变量（ADR-0006 后续任务）；
- Windows 服务化部署与自动启动未做。
