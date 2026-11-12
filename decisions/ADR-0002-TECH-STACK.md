# ADR-0002: 技术栈与 UI 形态

- 状态：Proposed（2026-11-12，等待负责人批准）
- 日期：2026-11-12
- 决策人：（待填）
- 相关需求/功能：M1 项目知识底座；DSH 集成（ADR-0001）

## 背景与约束

- 团队主要使用 Java；Mammoth 是项目制企业的领域工作平台，第一版为本地/单机优先，未来可演进到服务端。
- ADR-0001 已定：DSH 仅作 Agent Runtime/宿主，Mammoth Domain API 独立；DSH 通过 MCP 只读工具访问知识。
- 环境已实测：JDK 17.0.15、Maven 3.9.11、Maven Central 可达；DSH `0.2.2-rc.2`。
- 约束：不预设前端框架捷径，不把业务逻辑写进提示词；数据根目录不得位于 DSH Home 或安装目录。

## 需要解决的问题

1. 后端框架、构建工具；
2. UI 形态与前端栈，以及 Mammoth UI 与 DSH 的关系；
3. 业务数据根目录的位置与可配置性。

## 备选方案

### 方案 A：纯 JDK（`com.sun.net.httpserver` + 自建基础设施）

### 方案 B：Spring Boot 4.1.2 + Java 17 + Maven（建议）

### 方案 C：Quarkus 3.40.x

UI 子方案：B1 独立 SPA 由后端托管（建议）/ B2 嵌入 DSH Client slot 页面。

## 决策

采纳 **方案 B + B1**：

1. 后端：**Spring Boot 4.1.2**、**Java 17**、**Maven**；REST 供 UI，MCP stdio 作为同一工程的独立入口共享领域服务。
2. UI：**独立 SPA（Vite + React + TypeScript）**，由 Spring Boot 托管静态资源，浏览器访问；M1 不嵌入 DSH Client slot。
3. 数据根目录：默认 OS 应用数据目录（Windows `%LOCALAPPDATA%\Mammoth\data`），**可在应用设置中指定**；启动自检拒绝安装目录、`~/.dsh`、源码仓库内路径及不可写路径。

## 决策理由

- 团队 Java 主力 → Spring Boot 学习与协作成本最低，生态（Spring Data/Security/Flyway/MockMvc）覆盖 M1 的权限、迁移、测试需求。
- 实测成本可接受：启动 2.4 s、常驻 204 MB、jar 19 MB/34 依赖；相对纯 JDK（279 ms/53 MB）的额外开销对本地伴随服务可接受，而纯 JDK 需自建 DI/事务/安全/迁移，长期维护成本更高。
- UI 独立 SPA：保持 Runtime Independence（产品 UI 不绑定某个 Agent Runtime），避免 DSH client 插件构建与版本耦合（S1 的 F2 类问题），且可独立做浏览器端测试与验收（docs/09 要求真实 UI 验收）。
- MCP 单独入口：Web 容器与 Agent 通道解耦，DSH 侧只需配置型 bundle（S2 已验证）。
- 数据根目录独立且可配置：安装目录可能只读/被升级覆盖；`~/.dsh` 属 Agent 运行环境，按安全边界禁止承载业务数据；可配置满足负责人"应用内工作区目录设置"的诉求。

## 取舍和影响

- 接受 Spring Boot 的启动/内存开销与较快的版本升级节奏（4.x 需 Java 17+）。
- 放弃 Quarkus 的更优启动/内存（未实测）；若团队后续倾向 Quarkus，需重跑 `spike/adr-0002-stack` 同探针并修订本 ADR。
- UI 独立意味着 DSH 内不会出现 Mammoth 原生页面，用户通过浏览器/DSH 侧栏浏览器访问；这是有意的边界。
- 仍未定：数据库与迁移工具（ADR-0003）、Artifact 存储（ADR-0004）、检索（ADR-0005）、身份授权（ADR-0006）。

## 安全/数据/迁移影响

- 数据根目录自检与路径约束写入启动流程；误配到 `~/.dsh`/安装目录/仓库时拒绝启动（fail fast）。
- 凭据不进入仓库与数据根目录明文；后续由 ADR-0006 定认证方式。
- Spring Boot 升级需回归 REST 契约与 MCP 入口；MCP 协议版本跟随 DSH 支持的 MCP 版本。
- UI 静态资源与后端同源部署（M1）；未来服务端多用户部署需另做认证/CSRF/多租户设计。

## 验证计划和结果

已执行（2026-11-12）：

- 纯 JDK 探针：REST 就绪 279 ms、RSS 53 MB、MCP 同 classpath 可用（`docs/spikes/S3-TECH-STACK-SPIKE.md`）。
- Spring Boot 4.1.2 探针：`mvn package` 成功，jar 19 MB / 34 依赖；REST 就绪 2 439 ms；RSS 204 MB；MCP 同工程 classpath 可用。
- 两个探针均未连接数据库、未访问网络、未使用业务数据。

待补充（M1 T1 前）：Quarkus 对比（若需要）、打包与自动启动方案、端口冲突处理。

## 回滚或替代路径

- 若 Spring Boot 在桌面单机资源/打包上不可接受 → 回退方案 A（纯 JDK，需自建基础设施），或切换 Quarkus（方案 C）。
- UI 如需嵌入 DSH → 另立 ADR 变更 B1 为 B2，并评估 DSH client 插件版本耦合。

## 后续行动

1. 负责人批准本 ADR（Proposed → Accepted）。
2. 执行 ADR-0003（数据库/迁移）Spike，随后 ADR-0004/0006/0005。
3. 建立 M1 工程骨架（`apps/web`、`services/domain`、`packages/contracts`），按 `docs/specs/M1-PHASE1-PLAN.md` T1 起实施。
