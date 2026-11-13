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
