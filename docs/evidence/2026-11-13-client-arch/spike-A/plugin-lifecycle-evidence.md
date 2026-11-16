# Spike A — 安装/卸载/引擎版本门禁证据

## 1. 安装（初始）

`plugin_manager` `install_bundle`，target = `integrations/dsh/mammoth-bundle`（绝对路径）：

- 结果：`application: applied`；profile `package.json` 增加
  `"@mammoth/dsh-bundle": "link:E:/.../integrations/dsh/mammoth-bundle"` 与 bundle 选择；
- 三个 Loader 行激活：`mammoth-host`、`mammoth-domain-mcp`、`preset-mammoth`；浏览器半渲染（截图 00–13）。

## 2. 卸载回滚（S-A0）

`plugin_manager` `remove_bundle`，target = `@mammoth/dsh-bundle`：

- 返回：`stage: remove, changed: true, application: applied`；
- pnpm 输出：`- @mammoth/dsh-bundle link:E:/.../integrations/dsh/mammoth-bundle`；
- profile `package.json` 恢复为：
  `@deepseek-ai/dsh-base`、`@deepseek-ai/dsh-web-app`、`@deepseek-ai/dsh-experimental-tool-worktree`（且无 `dependencies` 段）；
- 真实 Desktop 验证（截图 `16-after-remove-bundle.png`）：左栏 Mammoth 入口消失；工作台不再可达；
  源目录未被删除（link 语义），便于重装。

## 3. 重装（恢复验证环境）

`plugin_manager` `install_bundle`（同一 target）：

- 返回：`stage: enable, changed: true, application: applied, version: 0.1.1`，无 warnings；
- profile `package.json` 重新出现 link 依赖与 bundle 选择；
- 真实 Desktop 验证（截图 `17-after-reinstall.png`）：左栏 Mammoth 入口恢复。

## 4. `engines.dsh` 负向门禁（S-A0b）

构造测试 bundle `@mammoth/engine-negative-test@0.0.1`（`engines.dsh: ">=9.0.0"`，配置型 patch，无 Host/Client 代码），
`plugin_manager` `install_bundle` 结果：

```json
{
  "stage": "install",
  "target": "C:\\Users\\Lau\\AppData\\Local\\Temp\\mammoth-spike\\bad-engine-bundle",
  "enabled": true,
  "changed": false,
  "application": "failed",
  "error": { "code": "incompatible-version", "incompatible": [{ "name": "@mammoth/engine-negative-test", "version": "0.0.1", "runtimeVersion": "0.2.2-rc.2", "requirements": { "engines.dsh": ">=9.0.0" } }] }
}
```

pnpm 输出关键行：`dsh: installation rejected: Plugin @mammoth/engine-negative-test@0.0.1 is incompatible with dsh 0.2.2-rc.2`、
`dsh: nothing was installed.`。安装被拒绝且未写入 profile。

## 5. 结论

- 四合一 bundle 可安装、可卸载、可回滚、可重装；卸载不删除源目录、profile 清单可恢复；
- `engines.dsh` 版本闸门在安装期有效拒绝不兼容 bundle（负向证据）；
- 当前验证环境在重装后恢复可用（截图 17，工具/页面均可用）。
