# S3 — 技术栈 Spike（ADR-0002 证据）

- **版本**：0.1
- **日期**：2026-11-12
- **目标**：为 ADR-0002 提供证据——Java 后端框架、构建工具、UI 形态、数据根目录策略。
- **环境**：Windows 10 x64；JDK 17.0.15（Oracle）；Maven 3.9.11；Docker 可用（本 Spike 未使用）；Maven Central 可达（HTTP 200）。
- **外部版本证据**（GitHub API，2026-11-12 抓取）：Spring Boot 最新 `v4.1.2`（2026-10-22 发布）；Quarkus 最新 `3.40.1.2`（2026-11-09 发布）。

> 分类：**已验证**（实际运行）/ **文档证据** / **未验证**。探针为一次性产物，位于 `spike/adr-0002-stack/`。

## 1. 统一验证目标

同一工程内同时提供：

1. **REST** 给 Mammoth UI（`GET /health`、`GET /api/projects/{id}`）；
2. **MCP stdio** 给 DSH Agent（复用 `spike/mcp-java/MammothMcpServer.java`，JDK-only）。

两者共享同一代码库，是 ADR-0001 边界的直接体现。

## 2. 实测结果（2026-11-12）

| 指标 | 纯 JDK 17 | Spring Boot 4.1.2 |
|---|---|---|
| 构建 | `javac` 直接编译（无依赖下载） | `mvn package` 成功；jar 19 MB |
| 依赖 | 0（仅 JDK） | 34 个 `BOOT-INF/lib` jar |
| REST 首次就绪 | **279 ms**（进程内 uptime 159 ms） | **2 439 ms**（日志 `Started ... in 1.762 s`） |
| 常驻内存 RSS | **53 MB** | **204 MB** |
| REST 正确性 | `/health` 200；示例端点 JSON 正确 | `/health` 200（键顺序不同）；示例端点 JSON 正确 |
| 同工程 MCP 入口 | 可用（同 classpath） | 可用（`java -cp target/classes MammothMcpServer`） |
| 开发配套 | 需自建路由/校验/DI/事务/安全/迁移 | Spring MVC、Spring Data、Security、Flyway 等生态齐全 |

## 3. 候选对比

| 维度 | A 纯 JDK | B Spring Boot | C Quarkus |
|---|---|---|---|
| 团队匹配（Java 团队） | 高（基础） | **高**（最常见） | 中（需学习 reactive/native 习惯） |
| 启动与内存 | **最优（实测）** | 可接受（2.4 s / 204 MB） | 优于 B（未实测） |
| 生态/文档/招聘 | 低（全靠自建） | **最高** | 中高 |
| 事务/安全/迁移 | 自建 | **成熟** | 成熟 |
| 测试生态 | 自建 | 强（JUnit/MockMvc/Testcontainers） | 强 |
| 构建工具 | javac | Maven 3.9.11（已验证） | Maven（需额外插件配置） |
| 桌面单机资源占用 | 最优 | 可接受（本地伴随进程） | 更优 |
| 风险 | 长期维护成本最高 | 版本升级节奏快（4.x 需 Java 17+） | 团队经验与 native 构建复杂度 |
| 本 Spike 实测 | ✅ | ✅ | ❌ 未运行（仅版本/生态证据） |

## 4. 建议

1. **后端框架：Spring Boot 4.1.2 + Java 17 + Maven。** 理由：团队主力 Java、生态最全（Data/Security/Flyway/MockMvc），实测 2.4 s 启动与 204 MB 常驻对"本地伴随服务"可接受；纯 JDK 虽快但需自建全部基础设施，长期维护成本高。
2. **UI 形态：独立 SPA（Vite + React + TypeScript），由 Spring Boot 以静态资源托管。** 不嵌入 DSH 客户端 slot：保持 Runtime Independence、避免 DSH client 构建耦合、可独立迭代与测试；DSH 仅作 Agent Runtime。
3. **同工程双入口**：REST 与 MCP 共享领域服务；MCP 入口作为独立 `main`（stdio），不依赖 Web 容器启动。
4. **数据根目录策略（负责人授权 Lead 决定）**：
   - 默认：`%LOCALAPPDATA%\Mammoth\data`（Windows）/ `$XDG_DATA_HOME/mammoth`（Linux）/ `~/Library/Application Support/Mammoth`（macOS）；
   - 允许在应用设置中指定"工作区/数据目录"（用户建议的"应用内提供工作区目录设置"被采纳）；
   - **启动自检拒绝**：路径位于 Mammoth 安装目录内、位于 `~/.dsh` 内、位于源码仓库内、或不可写 → 明确报错并拒绝启动。
   - 理由：安装目录可能只读、会被升级覆盖，且多用户机器上共享安装目录不安全；业务数据必须独立、可备份、可迁移。

## 5. 未验证 / 后续

- Quarkus 未实机运行；若团队倾向 Quarkus，需重跑同探针。
- Spring Boot 4.x 的 JDK 17 兼容性已由本构建证明，但 Spring Boot 4 需要 Java 17+，未来升级到 JDK 21/25 需在 ADR-0002 批准后单独评估。
- 数据库、迁移工具、ORM（ADR-0003）未在本 Spike 决策。
- 打包为 Windows 安装器/服务、自动启动、端口冲突处理未验证。
- 多实例/端口、日志位置、配置加密未验证。
