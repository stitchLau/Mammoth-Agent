# M4 缺陷台账

- **日期**：2026-11-13
- **范围**：M4 S1–S5（S6 文档化延期）
- **说明**：记录实现/验证中发现的问题、根因、修复与回归；S2–S6 继续追加。

## B-M4-001 前端清空邮箱被后端当成“保持不变”

| 项 | 内容 |
|---|---|
| 现象 | 设置页把空邮箱转成 `null` 提交；后端约定 `null`=保持不变，导致用户无法清空邮箱 |
| 根因 | 前端把“空字符串”与“未提供”混为一谈；API 语义未在调用处对齐 |
| 修复 | 设置页始终提交 `email.trim()`（空串=清空）；后端 `updateProfile` 对空串归一为 `null` 并清空；`AccountSettingsTest.emailCanBeSetClearedAndValidated` 覆盖 |
| 回归 | 后端 103 tests、前端 build + 25 tests、E2E 4 passed |
| 状态 | 已关闭 |

## B-M4-002 E2E 文本定位歧义

| 项 | 内容 |
|---|---|
| 现象 | M4 E2E 中 `getByText('组织成员管理')` 同时命中页面副标题与 `<h2>`；`getByText(username)` 同时命中用户名与邮箱 |
| 根因 | Playwright strict mode + 新页面文本重复 |
| 修复 | 改用 `getByRole('heading', { name: '组织成员管理' })` 与 `getByText(username, { exact: true })` |
| 回归 | E2E 4 passed（15.1s） |
| 状态 | 已关闭 |

## 无产品缺陷说明

S1 未发现权限绕过、会话失效或迁移破坏；负向测试覆盖非 admin 建用户/停用/重置/主数据、停用撤销会话、Agent 禁登、最后 org_admin 保护、临时口令会话白名单。

## B-M4-003 S2 审计 ingest 测试与定时 poller 竞争

| 项 | 内容 |
|---|---|
| 现象 | `AgentAuditIngestTest` 依赖 `ingestNow()` 返回值；默认 10s 初始延迟的 `@Scheduled` 可能在测试窗口内先消费 journal，造成偶发失败 |
| 根因 | 测试上下文与生产调度共用同一 `@Scheduled` 默认参数 |
| 修复 | 测试用 `mammoth.agent-audit.initial-delay-ms=600000` / `ingest-interval-ms=600000` 隔离 poller；生产默认仍为 10s/30s |
| 回归 | 后端 109 tests；`AgentAuditIngestTest` 稳定通过 |
| 状态 | 已关闭 |

## S2 说明

S2 未发现权限绕过或审计丢失；负向覆盖 zip 秘密拒绝、跨项目/角色、journal 损坏行跳过、`event_id` 幂等。zip 不加密为已知限制（与 ADR-0008 一致）。

## B-M4-004 S3 外部记录缺少标题/描述

| 项 | 内容 |
|---|---|
| 现象 | 初始 `external_record` 只存 ID/元数据，映射证据文本无法展示标题/描述 |
| 根因 | V9 设计时把标题/描述只放在 `metadata_json`，证据生成没有可读字段 |
| 修复 | V9 增加 `title`/`description` 列；`ExternalRecord`/服务/响应同步；证据文本使用标题+描述+来源 |
| 回归 | `ExternalImportTest`、E2E 需求详情可见外部证据 |
| 状态 | 已关闭 |

## B-M4-005 CSV 短行导致解析异常

| 项 | 内容 |
|---|---|
| 现象 | 测试 CSV 中未加引号的多行描述被拆成短行，`record.get(header)` 抛越界 |
| 根因 | CSV 格式要求内嵌换行加引号；解析器对短行不够健壮 |
| 修复 | 测试夹具按 RFC4180 加引号；解析器按 header index 判断 `record.size()`，短行按空值处理 |
| 回归 | `ExternalImportTest` 4 passed |
| 状态 | 已关闭 |

## B-M4-006 无 JavaMailSender 导致上下文启动失败

| 项 | 内容 |
|---|---|
| 现象 | 邮件通道默认关闭，但 Spring 上下文仍要求 `JavaMailSender` bean，测试/启动失败 |
| 根因 | `spring-boot-starter-mail` 的自动配置未在无 host 配置时提供 bean |
| 修复 | `MailConfig` 提供 `@ConditionalOnMissingBean` 的 `JavaMailSenderImpl`，从 `spring.mail.*` 读取；默认 host=localhost，渠道关闭时不发送 |
| 回归 | 全量 120 tests、E2E 5 passed |
| 状态 | 已关闭 |

## B-M4-007 导入失败通知排除操作者

| 项 | 内容 |
|---|---|
| 现象 | `import.failed` 只通知 owner；editor 导入失败时自己收不到通知 |
| 根因 | 通用 `create` 统一排除 actor |
| 修复 | `importFailed` 显式加入 actor；`create` 对 `import.failed` 允许 actor；其他事件仍排除操作者 |
| 回归 | `NotificationWorkflowTest`、导入失败路径 |
| 状态 | 已关闭 |

## B-M4-008 S4 测试误用嵌套类型

| 项 | 内容 |
|---|---|
| 现象 | `ExecutionService.ExecutionDeviation` 不存在，测试编译失败 |
| 根因 | `ExecutionDeviation` 是独立实体类，不是 `ExecutionService` 内部 record |
| 修复 | 测试改用 `var`/实体类型 |
| 回归 | `DeliveryWorkflowTest` 5 passed |
| 状态 | 已关闭 |

## B-M4-009 E2E 严格定位歧义（M4 S3）

| 项 | 内容 |
|---|---|
| 现象 | 来源名同时出现在 `<li>` 与 `<option>`；证据 key 同时出现在证据正文与引用资料 |
| 根因 | Playwright strict mode + 页面重复文本 |
| 修复 | `.first()` 或 scope 到具体 list/row |
| 回归 | E2E 5 passed（19.4s） |
| 状态 | 已关闭 |
