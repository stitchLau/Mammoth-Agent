# M2 缺陷台账（需求与方案）

> 记录 M2 期间发现并修复的缺陷/缺陷级问题；每条含现象、根因、修复、回归证据。

## B-M2-001 追溯矩阵缺失任务（设计层缺陷）

- **现象**：`SolutionWorkflowTest.dualViews...` 首次失败——业务视图的追溯矩阵"任务"列为 `—`，任务明明已关联技术设计项。
- **根因**：技术设计项被建成根节点，而追溯按"业务目标子树"计算，任务挂在子树之外的目标下不可见。
- **修复**：允许设计项**混合层级**（技术项可挂在它实现的业务目标下）；渲染器把"父项在本视图不可见"的项作为视图根（`SolutionRenderer`）；规格 §4.1/§4.4 补充说明。
- **回归**：`SolutionWorkflowTest` 7 tests 全通过；demo 追溯矩阵 1 行含任务与验收。

## B-M2-002 就绪检查保存丢失（前端竞态）

- **现象**：Playwright 首跑后数据库只保留 `background`、`constraints` 两个维度为 `confirmed`，其余被写成 `missing`。
- **根因**：每次保存后 `reload()` 用服务端数据重置整个 `drafts` 状态；下一次选择在重置前后提交时，保存处理器拿到旧的 `missing`。
- **修复**：需求详情页只在首次加载时初始化草稿（`draftsSeeded` ref），后续刷新不再覆盖未保存编辑；E2E 在每次保存后等待按钮恢复可用。
- **回归**：E2E 11 维全部落库，`关键维度已就绪` 可见；`npm test` 25 通过。

## B-M2-003 演示脚本角色错位（脚本缺陷）

- **现象**：demo 首跑 19/26，`approved-version-immutable` 得到 403 而非 409，`new-version` 403。
- **根因**：评审通过后仍以 reviewer 身份执行只允许 owner/editor 的方案编辑动作。
- **修复**：调整顺序——reviewer 批准 → 切回 owner 验证不可覆盖并建新版本 → owner 提交 v2 → reviewer 批准 v2。
- **回归**：`node scripts/demo-m2.mjs` **26/26**。

## B-M2-004 E2E 定位歧义（测试缺陷）

- **现象**：`getByText('[收货扫码流程] 重复扫码被拦截且当天闭环')` 命中 3 个元素（列表项 + 两个渲染视图中）。
- **根因**：验收标准文本同时出现在维度列表与 Markdown 渲染结果中。
- **修复**：断言加 `.first()`；保留"双视图均包含验收标准"的正向断言。
- **回归**：E2E 2 passed。

## B-M2-005 迁移前向外键（迁移缺陷）

- **现象**：`work_item.context_snapshot_id`/`approved_solution_version_id` 指向后建表，PostgreSQL 会因顺序失败。
- **根因**：需求表与快照/方案版本存在循环引用。
- **修复**：两个前向引用列去掉 FK 约束，由应用层维护（注释说明）；其余外键保留。
- **回归**：`MigrationTest`（4 迁移、23 表、幂等）通过；SQLite 行为不变。

## B-M2-006 工具误写导致文件编码损坏（过程缺陷）

- **现象**：用 PowerShell `Set-Content` 做批量替换后，`SolutionService.java` 出现非法 UTF-8（`E2` 截断），编辑工具拒绝读写。
- **根因**：PowerShell 5.1 默认编码与 `-NoNewline` 组合破坏了多字节字符。
- **修复**：用文件写工具整体重写该文件；后续批量替换改为显式 `UTF8Encoding($false)` 读写或直接用编辑工具。
- **回归**：`mvn verify` 通过；该文件无残留乱码。
