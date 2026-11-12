# Mammoth Domain MCP —— DSH 接入模板

这是 **配置型 bundle**：只包含 patch/locale/icon，不含 Host 代码。装入后 DSH 会启动
`java -jar mammoth-domain-service.jar --mcp` 子进程，并通过 MCP stdio 暴露两个**只读**工具：

- `mcp__mammoth_domain__search_project_knowledge`
- `mcp__mammoth_domain__get_knowledge_document`

安装步骤与安全说明见 [`docs/dsh-mcp-integration.md`](../../../docs/dsh-mcp-integration.md)。

## 已验证事实（2026-11-12，DSH 0.2.2-rc.2 / desktop）

- `plugin_manager install_bundle` → `application: applied`，工具出现在会话中；
- DSH 真实调用返回中文正常（曾发现 GBK 编码缺陷并修复为 UTF-8）；
- 仅 `verified` 知识可见；非项目成员被拒绝；
- 卸载后工具消失。

## 注意

- `cordis.patch.yml` 中四个占位符必须替换为本机真实路径/标识，否则 MCP 进程无法启动；
- 重建 jar 前必须先卸载该 bundle，否则 Windows 上运行中的 MCP 进程会锁定 jar 导致构建失败；
- `MAMMOTH_DATA_ROOT` 必须与 Web 服务使用同一数据根，否则看不到已发布知识。
