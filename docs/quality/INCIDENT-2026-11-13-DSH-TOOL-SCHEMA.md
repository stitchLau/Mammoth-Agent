# INCIDENT-2026-11-13 — DSH 工具 schema 非法导致全部会话请求失败

- **严重级别**：P0（全部 DSH 会话不可用）
- **发生**：2026-11-13 17:36 前后（bundle 接入 `desktop` profile 并激活后；首次失败时间未单独记录）
- **恢复**：2026-11-13 18:02（最小修复 + schema 校验）
- **影响**：本机 `desktop` profile 的全部 DSH 会话/子 Agent/工作区；无业务数据写入，无数据损坏
- **状态**：已恢复；已补深层防护；bundle 已提交（`dec48c4`）；上游缺口待反馈

## 1. 现象

所有会话在**每一次**模型请求时失败，提示：

```
本轮运行失败：Invalid schema for function 'mammoth_propose_work_item':
true is not of type "array" (request_id: 6f272116-9cfa-4991-8c95-f8bfedd9c364)
```

失败与具体任务、工作区、模型输出无关；新建会话、只读会话、子 Agent 同样失败。错误发生在请求发出阶段，尚未产生任何模型响应，因此也**不会**执行任何工具或业务写入。

## 2. 直接原因

`integrations/dsh/mammoth-bundle/index.js` 中 `mammoth_propose_work_item` 的 `parameters` 是**手写 raw JSON Schema**，却在 `properties.title` / `properties.summary` 上使用了 DSH author DSL 的写法：

```js
properties: {
  title:   { type: 'string', required: true, ... },   // 非法：raw schema 中 required 必须是数组
  summary: { type: 'string', required: true, ... },
}
```

JSON Schema 规定 `required` 是对象级的**字符串数组**，Provider 校验工具参数 schema 时看到 `required: true`，判定整个请求非法并返回 400。由于工具列表是全局的，**每个请求**都会携带这个坏 schema，于是所有会话一起失败。

最小修复（已由上一会话完成并验证）：

```diff
-        title: { type: 'string', required: true, ... },
-        summary: { type: 'string', required: true, ... },
+        title: { type: 'string', ... },
+        summary: { type: 'string', ... },
       },
+      required: ['title', 'summary'],
```

备份：`integrations/dsh/mammoth-bundle/local/index.js.bak-20261113-1756`（git-ignored，可还原）。

## 3. 为什么没被 DSH 拦住

DSH 有**两套 schema 形式**，语义不同：

| 形式 | 入口 | `required` 写法 | 校验时机 |
|---|---|---|---|
| Author DSL | `defineTool()`（`@deepseek-ai/dsh-tools`） | 属性上 `required: true`，编译为根级数组 | 编译时 `assertSupportedJsonSchema()` |
| Raw JSON Schema | 直接 `ctx.tools.register(definition)` | 对象根级 `required: ['a','b']` | **注册时不校验 `parameters`** |

证据（本机 DSH `0.2.2-rc.2`）：

- `@deepseek-ai/dsh-tools/lib/index.js` 的 `ToolRegistry.register()` 只调用 `assertSupportedJsonSchema(output.schema)`，**没有**校验 `definition.parameters`。
- `@deepseek-ai/dsh-tools/README.md` 的 `defineTool()` 示例正是 `path: { type: 'string', required: true }` —— 这是 author DSL 的合法写法，也是本次误用的来源。
- 同一版本 `assertSupportedJsonSchema()` 本身能识别该错误；用它对备份文件实测：

```
FAIL mammoth_propose_work_item ::
schema.properties.title.required is not supported on type "string";
schema.properties.summary.required is not supported on type "string"
```

结论：这不是“DSH 没有校验能力”，而是**注册路径漏校验**。bundle 又刻意不依赖 `dsh-tools`（零依赖），因此绕过了唯一会自动编译/校验的入口。

## 4. 为什么影响所有会话

1. `mammoth-host` 通过 `ctx.tools.register()` 注册的是**全局工具**，会进入每个 Agent、每个工作区、每个子 Agent 的工具目录。
2. `desktop` profile 的 `package.json` 以 `link:` 方式安装本 bundle：

   ```
   "@mammoth/dsh-bundle": "link:E:/MyProj/MommothAgent/code/Mammoth-Agent-spike/integrations/dsh/mammoth-bundle"
   ```

   并存在 junction：`~/.dsh/profiles/desktop/node_modules/@mammoth/dsh-bundle -> 上述目录`。
3. 因此**源码目录就是全局运行时**：保存即生效，没有构建、测试或 schema 门禁。
4. Provider 对工具参数 schema 做严格校验；一个坏 schema 使**所有**请求 400，而不是只让该工具不可用。

## 5. 为什么难发现、难定位

- 失败表现为“本轮运行失败/Invalid schema”，看起来像模型或网络问题，而非插件加载错误。
- 插件本身加载成功，工具也注册成功；没有启动期异常。
- 错误只在请求发出时由 Provider 返回，DSH 侧没有本地预检。
- bundle 目录当时**未被 Git 跟踪**（`git status` 显示 `?? integrations/dsh/mammoth-bundle/`），只能靠手工 `.bak` 回滚，缺少 diff/历史。

## 6. 已实施的修复

### 6.1 最小修复（已生效）

见第 2 节 diff；`node --check` 通过，DSH 自带 `assertSupportedJsonSchema()` 校验通过；用户侧确认会话恢复，Provider 不再拒绝。

### 6.2 深层修复（本次补齐，防再发）

| 变更 | 作用 |
|---|---|
| 新增 `tool-schema.js` | 按 DSH 0.2.2-rc.2 的 raw schema 子集实现本地校验；对 `required: true` 给出针对性错误 |
| 新增 `tool-parameters.js` | 3 个工具的 `parameters` 单一来源，供注册与自检共用 |
| `index.js` 的 `registerTool()` | 注册前 `assertToolParameters()`；非法 schema 让**插件激活失败**，不再污染全局工具面 |
| 新增 `scripts/check-tool-schemas.mjs` | 回归夹具（含本次事故）+ 真实 `apply()` 激活捕获校验；安装/重载前运行 |
| `package.json` | 增加 `check` 脚本 |
| `README.md` / `docs/DETAIL.md` | 记录强制流程、回滚与已知限制 |

设计取舍：bundle 保持零运行时依赖，不引入 `@deepseek-ai/dsh-tools`（linked bundle 无法稳定解析 Host 包）；改为本地镜像其 raw schema 子集，并在 DSH 升级时重新核对。

### 6.3 验证证据

| 命令 | 结果 |
|---|---|
| `node --check index.js` / `tool-schema.js` / `tool-parameters.js` | 全部 exit 0 |
| `node scripts/check-tool-schemas.mjs` | 全部 PASS（含事故回归夹具、3 个真实工具、激活捕获） |
| 用 DSH `assertSupportedJsonSchema()` 校验修改后的 bundle | 3 个工具全部 PASS |
| 用同一校验器校验备份文件 | `mammoth_propose_work_item` FAIL（成功复现根因） |
| 重启 DSH 后（2026-11-16 09:32） | bundle 写入 `plugin.activated`（`configError=null`），说明新模块加载且 3 个工具通过注册前校验；`mammoth_get_project_context` 只读调用成功，Provider 接受当前工具 schema |

## 7. 残余风险与未决事项

1. **bundle 已纳入 Git（已缓解）**：2026-11-16 提交 `dec48c4`（spike 仓库 `spike/dsh-desktop-integration` 分支，15 files，+3014）；`.bak-20261113-1756` 保留为修复前快照。后续变更必须继续提交，避免再次出现 untracked 的全局运行时。
2. **live link 仍指向开发工作树**：源码即全局运行时；JS 语法错误会让 Mammoth 工具整体不可用（但不至于所有会话失败）。长期应使用专用 dev profile 或“构建产物安装”。
3. **自检脚本是人工步骤**：注册时 fail-fast 是自动兜底，但会以“Mammoth 工具缺失”的形式暴露；安装/重载前仍必须运行 `check`。
4. **上游缺口**：`ToolRegistry.register()` 未校验 `parameters`。建议向 DSH 上游反馈：注册时对 `parameters` 也执行 `assertSupportedJsonSchema()`，或在 Provider 400 时把错误归因到具体工具/插件。
5. **DSH 升级回归**：本地校验镜像的是 `0.2.2-rc.2` 子集；升级 `engines.dsh` 后必须重跑 `check` 并核对上游 schema 规则。

## 8. 防再发规则

已写入：

- 仓库级：`AGENTS.md` 顶部“最高优先级：DSH 工具 schema 与全局 Profile 变更”；
- 用户级全局：`~/.dsh/AGENTS.md`（对所有工作区生效）；
- 运行手册：`docs/11-DEEPSEEK-HARNESS-GUIDE.md`、`docs/09-QUALITY-AND-TESTING.md`、bundle `README.md`。

核心三条：

1. raw schema 的 `required` 只能是根级字符串数组；`required: true` 只属于 `defineTool()`。
2. 已 link 进 profile 的 bundle 按生产变更处理：先备份、先自检、再重载、再冒烟。
3. 见到 `Invalid schema for function` 立即回滚，不重试、不当作模型故障。
