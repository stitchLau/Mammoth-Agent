# Mammoth M0 spike bundle（一次性验证夹具，v0.2.0）

这是 `docs/spikes/S1-DSH-EXTENSION-SPIKE.md` 对应的一次性验证包，**不是产品代码**，不进入 Mammoth 产品边界。

## 它验证什么

1. 工作区内手工编写的 out-of-tree bundle 能被 `plugin_manager` 的 `install_bundle` 装入 `desktop` profile 并 HMR 激活；
2. Host 侧用官方 `defineTool` + `ctx.tools.register` 注册模型可见 Agent 工具（`mammoth_spike_probe`）；
3. Client 侧用 `ctx.slots.inject/register` 向 `conversation.composer.dock` 贡献纯装饰徽标；
4. 官方 Host 持久化缝 `ctx.storageDomain`（`defineDomain` + zod，`kv` 形态）能打开域并完成写-读往返；
5. 外部 bundle 的依赖方案：声明精确 `@deepseek-ai/*` 依赖并在本目录 `pnpm install` 后，DSH 包才能被导入。

## 依赖（重要）

插件代码从**自身目录**解析 Node 包，不经过 DSH 安装目录。因此：

```powershell
# 在 spike/dsh-extension 目录内执行（不要用 profile 目录）
pnpm install
```

`dependencies` 固定为 `@deepseek-ai/dsh-tools@0.2.2-rc.2`、`@deepseek-ai/dsh-storage-domain@0.2.2-rc.2`、`zod@4.6.6`，与当前 DSH 版本强耦合。

## 它不做什么

- 不读写工作区文件，不访问网络，不接触凭据；
- 不注册 session 事件，不修改 DSH 上游包，不手改 `$DSH_PROFILE_DIR` 中的清单；
- 不承担任何产品语义：`mammoth_spike_probe` 只是计数器 + 状态回执。

## 已验证 / 未验证

- 已验证（2026-11-12）：安装激活、工具真实调用、`ctx.storageDomain` 落盘、卸载。
- 未验证：UI 徽标的实际渲染、跨 DSH 重启读回、`engines.dsh` 负向拒绝。

## 移除方式

由 `plugin_manager` 的 `remove_bundle` 卸载（标识为 `@local/mammoth-spike`），不要手工删除 profile 文件。
夹具数据：`~/.dsh/storages/mammoth_spike.json`（可安全删除）。
