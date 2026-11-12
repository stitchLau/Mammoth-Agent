# Prompt 01 — DeepSeek Harness 扩展能力 Spike

目标：验证 Mammoth 能否以可维护的 DSH 扩展运行，不直接 Fork 上游。

先读取：Master Guide、Architecture Baseline、DSH Guide、相关 Harness 官方文档。确认实际 DSH 版本、commit、Profile 和安装方式。

请先只读研究当前上游源代码和官方文档，回答：
1. 本版本推荐的 Bundle/Plugin 开发方式是什么？
2. 哪些公共扩展点可注册后端 service、Agent tool、workspace context、UI page/slot？
3. 数据持久化是否有适合外部领域服务的扩展点？限制是什么？
4. Profile/Bundle 如何安装、更新、卸载？是否需要重启？
5. 哪些 API 当前 pre-stable？升级风险是什么？
6. 若将业务领域模型放在独立 Domain API，DSH 插件如何安全连接？
7. 需要怎样的集成测试证明真实 Profile Composition 可用？

然后提出一个最小 Spike 计划，范围只包括：一个插件包、一个服务、一个工具、一个 UI 入口（若官方扩展点支持）、一个持久化往返验证、一个安装/卸载验证和一组测试。

不要修改 DSH 上游核心包。若必须修改，请指出不可替代的原因并暂停等待确认。

输出 ADR-0001 草案，注明证据 URL、具体版本/Commit、测试步骤、风险和结论。没有运行的验证不得写成已验证。
