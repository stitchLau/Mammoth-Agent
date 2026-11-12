# Mammoth M0 spike bundle（一次性验证夹具）

这是 `docs/spikes/S1-DSH-EXTENSION-SPIKE.md` 对应的一次性验证包，**不是产品代码**，不进入 Mammoth 产品边界。

## 它验证什么

1. 工作区内手工编写的 out-of-tree bundle 能否被 `plugin_manager` 的 `install_bundle` 装入 `desktop` profile 并激活；
2. Host 侧能否用 `ctx.tools.register(defineTool(...))` 注册一个模型可见的 Agent 工具（`mammoth_spike_probe`）；
3. Client 侧能否通过 `ctx.slots.inject/register` 向 `conversation.composer.dock` 贡献一个纯装饰徽标；
4. 文档化的 Host 持久化缝 `ctx.storageDomain` 能否从 out-of-tree bundle 中 `import` 并完成一次写-读往返；失败时把原因写进工具返回值，而不是让插件加载失败。

## 它不做什么

- 不读写工作区文件，不访问网络，不接触凭据；
- 不注册 session 事件，不修改 DSH 上游包，不手改 `$DSH_PROFILE_DIR` 中的清单；
- 不承担任何产品语义：`mammoth_spike_probe` 只是计数器 + 状态回执。

## 移除方式

由 `plugin_manager` 的 `remove_bundle` 卸载（标识为 `@local/mammoth-spike`），不要手工删除 profile 文件。
