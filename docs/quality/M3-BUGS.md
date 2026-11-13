# M3 缺陷台账（实施回收与知识闭环）

## B-M3-001 Windows 强制文件锁导致无法读取锁文件元数据

- **现象**：`DataRootInstanceLockTest` 第二次获取锁时抛 `IOException: 另一个程序正在使用此文件`，测试在第 29 行读取锁文件内容时失败。
- **根因**：Windows 的 `FileChannel.lock` 是**强制锁**（LockFileEx），被锁字节区间对其他句柄的读取同样返回共享冲突；把 pid/host 写进被锁文件后，阻塞进程无法读取。
- **修复**：锁文件只用于独占锁（空文件），holder 元数据写入旁路 `.mammoth-instance-holder`（不加锁，可读）；错误信息读取 holder 文件展示持有者。
- **回归**：`DataRootInstanceLockTest` 2 passed；手动双实例检查错误含 `pid=...;host=...;startedAt=...`。

## B-M3-002 只读上下文启动失败（Flyway 依赖 + 默认组织写入）

- **现象**：`ReadOnlyModeTest` 无法启动：`'organizationService' depends on missing bean 'flyway'`；修掉依赖后又出现 `SQLITE_READONLY ... insert into organization`。
- **根因**：`OrganizationService` 用 `@DependsOn("flyway")` 保证迁移顺序；只读模式没有 Flyway bean；其 `@PostConstruct` 还会在空组织时写入默认组织。
- **修复**：改为构造函数注入 `ObjectProvider<Flyway>`（写模式强制迁移，只读模式为空操作）；只读模式下若默认组织缺失则抛出"数据根未初始化，请先启动 Web/管理入口"，不写库。
- **回归**：`ReadOnlyModeTest` 通过；M2 全量测试不回归。

## B-M3-003 MCP 只读属性被 application.yml 默认值覆盖

- **现象**：手动运行 `--mcp` 时日志为 `Mammoth data root ready (writer)`，随后因第二个写实例锁冲突启动失败。
- **根因**：`SpringApplicationBuilder.properties()` 是**最低优先级**默认属性，`application.yml` 中 `mammoth.data-root.read-only: ${MAMMOTH_DATA_ROOT_READ_ONLY:false}` 覆盖了它。
- **修复**：MCP 入口在启动前设置 **System property**（优先级高于配置文件）为 `true`，并拒绝 `--mammoth.data-root.read-only=false` 参数；启动后再校验一次。
- **回归**：手动 MCP 检查日志 `data root ready (read-only)`，无 Flyway 迁移行，工具调用正常。

## B-M3-004 沙箱下 Mockito/Byte Buddy 无法自附加

- **现象**：`McpServerMainTest` 全部报 `Could not initialize inline Byte Buddy mock maker ... Could not self-attach`。
- **根因**：Byte Buddy 通过外部进程附加 agent，受限沙箱禁止该进程/管道（EPERM），与代码无关。
- **修复**：该测试只需要协议帧/拒绝路径，`serve(..., tools=null, ...)` 即可；移除 Mockito 依赖用法。
- **回归**：`McpServerMainTest` 3 passed。

## B-M3-005 受限沙箱下 Playwright 无法 fork worker

- **现象**：`npm run test:e2e` 报 `spawn EPERM`（node child_process.fork + 管道）。
- **根因**：DSH `workspace-write` 文件沙箱禁止命名管道/捕获式子进程，Playwright worker 依赖该机制。
- **处置**：一次性 `danger-full-access` 提权运行 E2E，结果 2 passed；记录到 `docs/evidence/M3-VERIFICATION.md` §4，后续本机受限会话需同样处理，CI 不受影响（非代码缺陷）。
## B-M3-006 E2E 基线捕获被后续导航中止

- **现象**：M3 E2E 首跑超时；数据根中仓库已注册但基线为 0，交接包显示"代码基线：未捕获"。
- **根因**：点击"捕获基线"后表单异步提交，测试立即 page.goto 交付页，浏览器中止了未完成的 POST；同时断言 getByText('当前基线') 命中了表单标题"捕获当前基线"的子串，掩盖了失败。
- **修复**：等待 POST /baselines 响应并断言唯一基线说明文案；后续同类"提交后导航"的测试遵循同一模式。
- **回归**：M3 E2E 通过（基线进入交接包与实施记录）。

## B-M3-007 E2E 严格模式定位冲突

- **现象**：getByText(<short sha>) 同时命中表格单元格与"实施记录"下拉选项。
- **修复**：断言加 .first()；表格/表单定位优先用角色与作用域。
- **回归**：E2E 3 passed。

## B-M3-008 测试 Git 仓库误建在 Mammoth 数据根内

- **现象**：BaselineWorkflowTest/DeliveryWorkflowTest 注册仓库返回 400 epository path must not be inside the Mammoth data root。
- **根因**：测试把临时 Git 仓库建在测试数据根目录下；服务按 ADR-0008 正确拒绝。
- **修复**：测试改用独立临时仓库根（mammoth-m3-*-repos），与数据根分离。
- **回归**：基线/交付测试通过。