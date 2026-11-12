# S2 — Domain API ↔ DSH 的 MCP 边界 Spike（ADR-0002 前置）

- **版本**：0.1（Working Draft）
- **日期**：2026-11-12
- **目的**：验证 ADR-0001 采纳的边界——**Java Domain API 通过 MCP 暴露给 DSH**——在真实 Profile Composition 中可用，且插件层**零 DSH 依赖**（规避 S1 的 F2 问题）。
- **状态**：边界可行性已实测；ADR-0002（技术栈）本身仍 **OPEN**，本 Spike 只固定"协议边界"，不选语言框架之外的栈。

> 分类：**已验证**（实际执行并观察到结果）/ **文档证据** / **未验证**。

## 1. 方案

```
DSH（0.2.2-rc.2, desktop profile）
  └─ 配置型 bundle：@local/mammoth-mcp-domain（无 index.js，仅 patch + locale + icon）
       └─ insert @deepseek-ai/dsh-mcp-client
            config: serverName=mammoth, transport=stdio,
                    command=<java.exe>, args=['-cp', classes, 'MammothMcpServer']
  └─ MCP stdio（换行分隔 JSON-RPC 2.0）
       └─ Java 17 MCP server（MammothMcpServer，零第三方依赖）
            └─ 一个工具：mammoth_project_summary（合成数据）
```

选择该方案的直接原因（S1 结论）：

- 外部 bundle 的代码**不能**直接 `import '@deepseek-ai/*'`（S1 F2）；配置型 bundle 不写 Host 代码，天然规避。
- `typert`/`api-gateway` 只是 Host↔Client 内部 RPC，不是对外 API。
- MCP 是文档化、语言无关、DSH 侧零代码的官方接入口（`references/mcp-bundle.md:3,5`）。

## 2. 实现清单

| 文件 | 作用 |
|---|---|
| `spike/mcp-java/MammothMcpServer.java` | Java 17 单文件 MCP server：手写 JSON 解析/序列化，处理 `initialize`、`ping`、`tools/list`、`tools/call`；诊断只写 stderr |
| `spike/mcp-java/bundle/package.json` | 配置型 bundle manifest（`engines.dsh`、`dsh.bundle.patch`，无入口文件） |
| `spike/mcp-java/bundle/cordis.patch.yml` | 插入 `@deepseek-ai/dsh-mcp-client` 行（stdio、绝对路径 command/args、`failOnStartupError: false`） |
| `spike/mcp-java/bundle/locale/en.json`、`icon.svg` | 插件面板显示元数据 |
| `spike/mcp-java/classes/` | `javac` 产物（不入库，`.gitignore` 覆盖） |

## 3. 实测记录

1. **编译**：`javac -encoding UTF-8 -d spike/mcp-java/classes MammothMcpServer.java` → exit 0。
2. **协议自测**（直接管道喂 3 条请求）：
   - `initialize` → `{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"mammoth-domain-stub","version":"0.1.0"}}`
   - `tools/list` → 1 个工具，`inputSchema.required=["projectId"]`
   - `tools/call` → `content[0].text` 为合成 JSON，`isError:false`
3. **安装**：`plugin_manager install_bundle(spike/mcp-java/bundle)` → **`application: applied`**、`warnings: []`。
4. **工具注入**：会话立即出现 `mcp__mammoth__mammoth_project_summary`，同时出现 MCP 资源工具（`list_mcp_resources` / `list_mcp_resource_templates` / `read_mcp_resource`），server 名为 `mammoth`。
5. **端到端调用**：
   `mcp__mammoth__mammoth_project_summary({projectId:"PRJ-001"})`
   → `{"source":"mammoth-domain-api-stub","synthetic":true,"projectId":"PRJ-001","summary":"Project PRJ-001: 2 open requirements, 1 linked knowledge document."}`
6. **资源面**：`list_mcp_resources({server:"mammoth"})` → `{"resources":[]}`（server 只声明 tools 能力，符合预期）。
7. **卸载**：`remove_bundle(@local/mammoth-mcp-domain)` → `application: applied`。

## 4. 结论

- **MCP 边界可行**：Java 进程 ↔ DSH 的 stdio MCP 通路在真实 profile 中工作，模型侧工具命名稳定为 `mcp__<serverName>__<tool>`。
- **插件层零 DSH 依赖**：配置型 bundle 只有文本文件，不需要 `pnpm install`，不产生 F2 的版本耦合问题；Domain API 可用任意 JVM 技术栈实现。
- **生命周期由 DSH 管理**：MCP server 由 `dsh-mcp-client` 启动/回收；`failOnStartupError: false` 时启动失败不会拖垮宿主。
- **权限边界**：MCP 工具进入统一 tools pipeline（与原生工具同样受 `tools/pre-execute`/guard/restrict 与审批约束，属文档证据），但**本 Spike 未做 deny/ask 负向测试**，故权限策略行为仍标记未验证。

## 5. 风险与限制

| 风险 | 说明 | 缓解 |
|---|---|---|
| 机器特定路径 | patch 中 `command`/`args` 为绝对路径 | 产品化时由安装步骤生成，或改用 `dsh plugin` 配置注入 |
| 无认证的本地 stdio | 本 Spike 未设计 Domain API 鉴权 | 产品化必须设计服务身份/令牌与最小权限；不得暴露公网 |
| 进程协议兼容 | MCP 版本协商固定 `2024-11-05` | 跟随 `dsh-mcp-client` 支持版本，加入升级回归 |
| 工具爆炸面 | 每个 MCP 工具都会进入模型工具集 | 用 server 粒度权限、工具白名单与审批策略收窄 |
| 数据暴露 | MCP server 结果直接进入模型上下文 | Domain API 侧做字段级最小化与脱敏 |

## 6. 未验证项

1. `tools/pre-execute`/guard/restrict 对 MCP 工具的 allow/deny/ask 负向测试。
2. MCP server 崩溃、超时、重连行为（10 次重试预算后移除工具）。
3. 多用户/鉴权/多租户下的 MCP 连接隔离。
4. 进程重启后 `mcp` server 自动恢复。
5. 大结果集、资源模板（resources/templates）与提示模板（本版本不支持 MCP prompt templates）。
6. 具体 Java Web/领域框架选型（仍属 ADR-0002/0003/0006）。

## 7. 对 ADR-0002 的输入

- 技术栈候选应满足：可独立进程运行、可暴露 MCP（或由薄适配层暴露）、支持本地单机与未来服务端部署。
- 推荐边界：**Domain Service（Java）+ MCP server 适配层 + DSH 配置型 bundle**；DSH 侧不写领域逻辑。
- 仍 OPEN：Java 框架（Spring Boot / Quarkus / 纯 JDK）、持久化（ADR-0003）、鉴权（ADR-0006）、部署形态。
