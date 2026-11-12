# ADR-0002 技术栈 Spike 探针（一次性）

比较两个候选在同一需求下的最小可运行性：

- `pure-jdk/`：仅 JDK 17（`com.sun.net.httpserver` 提供 REST，MCP 复用 `spike/mcp-java/MammothMcpServer.java`）。
- `spring-boot/`：Spring Boot 4.1.2（`spring-boot-starter-web` 提供 REST，MCP 复用同一个 JDK-only MCP 入口）。

两个探针都验证同一件事：**同一工程内同时提供 REST（给 UI）与 MCP stdio（给 DSH Agent）两个入口**，这是 ADR-0001/0002 的边界要求。

## 运行

```powershell
# pure-jdk
javac -encoding UTF-8 -d spike/adr-0002-stack/pure-jdk/out `
  spike/adr-0002-stack/pure-jdk/Probe.java spike/mcp-java/MammothMcpServer.java
java -cp spike/adr-0002-stack/pure-jdk/out Probe serve        # REST :18081
java -cp spike/adr-0002-stack/pure-jdk/out MammothMcpServer   # MCP stdio

# spring-boot
mvn -B -q -f spike/adr-0002-stack/spring-boot/pom.xml -DskipTests package
java -jar spike/adr-0002-stack/spring-boot/target/mammoth-probe.jar --server.port=18080   # REST
java -cp spike/adr-0002-stack/spring-boot/target/classes MammothMcpServer                  # MCP stdio
```

## 说明

- 探针只暴露 `/health` 与一个只读示例端点，不读写业务数据，不连接数据库、不访问网络。
- 测得的启动时间/内存/依赖数量写入 `docs/spikes/S3-TECH-STACK-SPIKE.md`，不在此处维护。
- 两个探针都是临时产物，不得作为产品代码基线；M1 工程骨架需在 ADR-0002 批准后单独建立。
