# M1/M1.5 缺陷台账（真实发现与修复）

- **范围**：M1 Phase 1 与 M1.5 UI 产品化期间在开发/验证中发现并修复的缺陷
- **原则**：只记录真实复现过的问题；每条附根因与回归证据
- **相关**：时间线 [`docs/process/M1-TIMELINE.md`](../process/M1-TIMELINE.md)、验证证据 [`docs/evidence/M1-VERIFICATION.md`](../evidence/M1-VERIFICATION.md)

## 1. 缺陷清单

| ID | 阶段 | 现象（可复现） | 根因 | 修复 | 回归证据 |
|---|---|---|---|---|---|
| BUG-001 | M1 T3 | 空库启动后 `schemaVersion` 不可用、表不存在，接口 500 | Spring Boot 4 将 Flyway 自动配置拆到独立模块，`spring-boot-starter-jdbc` 不再传递 Flyway 自动配置 | `DataSourceConfig` 显式声明 `@Bean Flyway` 并在启动时迁移 | `MigrationTest`（3 个迁移、幂等）；`/api/meta` schemaVersion |
| BUG-002 | M1 T8 | 中文检索整句 `MATCH` 命中 0（如"条码重复怎么处理"） | FTS5 trigram 分词器下整句 MATCH 无法匹配连续短语 | 查询拆为字符三元组 `OR` 列表 + BM25 排序 | `SqliteKnowledgeSearchAdapter.trigramQuery` 单测 + `KnowledgeWorkflowTest` 检索用例 |
| BUG-003 | M1 T9 | DSH 会话调用 MCP 工具返回中文乱码 | MCP 入口把 JVM `System.out`（Windows 默认 GBK）当作协议输出流 | 基于 `FileDescriptor.out` 重建 UTF-8 `PrintStream` | `McpServerMainTest`（UTF-8 JSON 行断言）；DSH 真实会话复验中文正常 |
| BUG-004 | M1 T9 | 安装 MCP bundle 后重建 jar 失败：无法重命名 `.jar` | 运行中的 MCP 子进程持有 jar（Windows 文件锁） | 文档化流程：先卸载 bundle → 构建 → 重装 | `docs/dsh-mcp-integration.md` 故障排查；实测通过 |
| BUG-005 | M1 T10 | 知识创建仅 controller 校验角色，服务层可绕过 | 纵深防御缺失 | service 层补 `requireEditorRole` | `KnowledgeWorkflowTest` / `PermissionIsolationTest` |
| BUG-006 | M1 认证 | 登录后所有变更请求 403（CSRF） | `SessionManagementFilter` 在过滤器建立认证后触发 `CsrfAuthenticationStrategy`，轮换并清空 CSRF cookie | 会话认证过滤器改放到 `AuthorizationFilter` 之前（SessionManagementFilter 之后），并禁用无状态会话的轮换策略 | 认证版演示 14/14；E2E 全流程 |
| BUG-007 | M1 认证 | SPA 首次读取 CSRF token 时无 cookie，首个变更请求 403 | `CookieCsrfTokenRepository` 延迟生成，GET 不落 cookie | 新增 `GET /api/auth/csrf` 强制生成；SPA 加载时调用 | 演示/E2E 首步 `csrf-token` |
| BUG-008 | M1 认证 | 非 Web 上下文（MCP 模式 / `webEnvironment=NONE` 测试）启动失败 | `SecurityConfig` 依赖 `HttpSecurity`，在非 servlet 上下文不存在 | `@ConditionalOnWebApplication(SERVLET)` | MCP 冒烟；全部 `@SpringBootTest` 通过 |
| BUG-009 | M1.5 | 浏览器直接刷新 `/projects/...` 返回 401，页面打不开 | 安全过滤器先于 SPA 路由拦截 | Security 放行 SPA 路由 + `SpaRouteController` 回退 index.html | E2E 通过；`GET /projects` 200 |
| BUG-010 | M1.5 | 选择文件后上传无反应、列表不更新 | `FileList` 是实时集合，清空 input 后异步处理的是空列表 | 先 `Array.from(files)` 复制再清空 | E2E 上传断言（接口 200 + 文件名可见） |
| BUG-011 | M1.5 | 修改密码后跳转登录页但"密码已修改"提示丢失 | 改密撤销会话 → 登出接口 401 → `signOut` 抛错 → RequireAuth 重定向覆盖 state | 登出 best-effort；提示改存会话上下文 | E2E 改密步骤断言提示与重登 |
| BUG-012 | M1.5（测试） | E2E 偶发 strict mode 冲突：`getByLabel('显示名')` 匹配表单与输入框 | 表单 `aria-label="修改显示名"` 含相同子串 | 使用 `exact: true` | E2E 稳定通过 |
| BUG-013 | M1.5（视觉） | 页面可正常操作但**完全没有样式**（截图评审发现） | 路由重构重写 `main.tsx` 时遗漏 `import './styles.css'`，Vite 未打包 CSS | 恢复样式导入；构建产物出现 `index-*.css` 并由后端托管 | 重新构建（CSS 6.7 KB）→ 重跑 E2E（1 passed）→ 重拍截图（样式正常） |

## 2. 仅记录、未修复（当前不阻塞 M1/M1.5）

| 项 | 说明 | 处理计划 |
|---|---|---|
| CI 远端未运行 | `e2e` job 已配置，未在 GitHub Actions 实际执行 | M2 前推送后到仓库页面确认 |
| MCP 崩溃重连/超时 | 未专项验证 | 生产化阶段 |
| 大文件（>50MB） | 上传上限 50MB，无流式 | 后续阶段 |
| PostgreSQL 方言 | 仅 SQLite 迁移 | ADR-0003 服务端阶段 |
