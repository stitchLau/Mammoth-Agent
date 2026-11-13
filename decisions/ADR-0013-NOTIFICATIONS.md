# ADR-0013: 通知与外部发送（站内默认启用，邮件/企微完整实现但默认关闭）

- 状态：**Accepted v1.0（2026-11-13，M4 S5 站内通知 + 邮件/企微默认关闭）**；真实 SMTP/企微启用与联调留 M4.5
- 日期：2026-11-13
- 决策人：项目负责人（方向确认）；Lead Agent 起草
- 相关需求/功能：M4 通知渠道、服务端任务、外部 Agent 协作告警
- 相关文档：`docs/07-WORK-ITEM-LIFECYCLE.md` §3/§6、`docs/08-SECURITY-AND-TRUST.md` §5、`docs/09-QUALITY-AND-TESTING.md`、`decisions/ADR-0006`（用户/会话）、`decisions/ADR-0008`（单写者/任务边界）

## 背景与约束

- 现状（2026-11-13 核对）：没有通知表、没有邮件依赖、没有调度器、没有出站 HTTP 客户端；用户模型 `app_user` **没有 email 列**；`pom.xml` 无 `spring-boot-starter-mail`。
- 负责人 2026-11-13 决定：**站内通知默认启用**；**邮件/企微通知渠道必须完整实现，但默认不启用**；由负责人后续配置参数后自行启用。企微先以**机器人**形式接入；SMTP 按同一“配置后启用”口径。
- 安全边界：外部发送属于文档 `docs/08` 的 **External** 风险等级，必须有明确边界、模板、审批/启用控制、限速、脱敏与审计；开发/测试不得向真实客户或生产群发送。
- 服务端边界：通知/任务只能在 writer 进程运行；MCP 只读进程不运行任务、不发送通知（ADR-0008/0012）。

## 需要解决的问题

1. 通知事件、收件人、去重与已读状态；
2. 站内通知默认启用；
3. 邮件/企微“完整功能但默认关闭”的配置模型；
4. 发送审批、模板、限速、重试/死信、审计；
5. 外部收件人与内容脱敏；
6. 与任务队列/单写者/MCP 只读的关系；
7. 测试如何在没有真实 SMTP/企微账号时验证完整功能。

## 备选方案

- **方案 A（推荐）**：站内通知默认启用；邮件/企微渠道完整实现（JavaMail + 企微机器人 webhook），默认 `enabled=false`，配置参数齐全并校验后启用；所有外发走 outbox/任务队列，带限速、重试、死信、审计；测试用本地 SMTP sink 与本地 HTTP stub 验证真实协议路径。
- **方案 B**：只做站内通知，邮件/企微只留接口不实现。与负责人“完成完整功能”的要求不符，不采用。
- **方案 C**：默认启用邮件/企微。会向未配置/未批准的目标发送，风险不可接受，拒绝。
- **方案 D**：用第三方通知平台/企微自建应用。引入外部账号、回调、加解密与供应链风险；M4 先机器人，其他形态 M4.5/M5 评估。

## 决策

采纳 **方案 A**，并明确以下 14 条：

1. **站内通知默认启用**：`mammoth.notifications.in-app.enabled=true`（默认）。每个用户只能看自己的通知；按项目角色过滤对象可见性；已读/未读、未读计数、分页。
2. **邮件渠道完整实现、默认关闭**：使用 Spring Boot Mail/JavaMail；`mammoth.notifications.email.enabled=false` 默认；必须配置 SMTP host/port/from/认证/TLS 后才能启用；按用户 `app_user.email` 发送；无 email 的用户跳过并记录 `skipped_no_address`。
3. **企微机器人渠道完整实现、默认关闭**：使用企微群机器人 webhook（HTTP POST，JSON）；`mammoth.notifications.wecom.enabled=false` 默认；必须配置 webhook URL（视为凭据）与事件白名单后才能启用；按官方文档核对消息类型/字段/限速/返回码；webhook URL 不入库、不日志、不导出。
4. **配置即启用，但必须有校验**：启用开关 + 必填参数缺失时启动/保存失败并给出可操作错误；UI 只显示“已启用/未启用/配置缺失”，**不回显秘密值**；支持“发送测试消息给自己/负责人指定测试目标”，测试发送也走审计与限速。
5. **事件与收件人**：至少覆盖 `solution.submitted`（owner+reviewer）、`solution.approved`/`solution.changes_requested`（作者+owner）、`delivery.approved`（项目成员）、`knowledge.review_requested`（reviewer）、`import.failed`（导入人+owner）、`job.failed`（org_admin）。排除操作者本人；同一对象/版本去重。
6. **模板**：模板以代码常量/资源文件版本化（`NotificationTemplate`），不提供 DB 内任意编辑；模板含标题/正文/链接；外发模板必须转义/限制长度；邮件禁止头注入；企微 text/markdown 按官方限制截断。
7. **outbox/任务**：站内通知事务内落 `notification`；外发落 `notification_delivery`（pending/sent/failed/dead），由 writer 进程的 `server_job` worker 领取；重试指数退避、最大次数、死信可见；幂等键防重复发送。
8. **限速**：企微按官方文档配置每分钟上限（默认取保守值）；邮件按 SMTP 承受能力配置；超限排队而不是丢弃；单节点内存限速，M5 多实例时改共享存储。
9. **审批/启用边界**：M4 不允许向客户/外部群自动发送；外发仅限**内部通知**（项目成员/内部群），且必须由负责人显式启用渠道并配置目标。未来向客户发送需新 ADR（客户授权、模板审批、退订/黑名单、合规）。
10. **脱敏**：通知正文不含凭据、完整客户敏感正文、源码；只放标题/摘要/对象链接；日志只记 channel/recipientCount/status/errorCode，不记 webhook/SMTP 密码/完整邮件正文。
11. **审计**：`notification.create`、`notification.read`、`notification.delivery`（channel/status/attempts）、`notification.channel.enable/disable`、`notification.test_send`；审计不含秘密。
12. **只读进程边界**：`@EnableScheduling`/worker 用 `@ConditionalOnProperty(mammoth.data-root.read-only=false)` 或等价条件；MCP 只读进程不建表、不跑任务、不发通知。
13. **用户 email**：`app_user.email` 可空、格式校验、仅本人/org_admin 可改；不强制所有用户填写；导入后账号锁定的用户不发送。
14. **测试**：默认配置下断言**零外发**；启用配置下用本地 SMTP sink（建议 GreenMail，Apache-2.0，测试作用域）与 JDK `HttpServer` stub 验证真实 SMTP/HTTP 路径、返回码、重试、限速、脱敏；测试不得访问真实 SMTP/企微。

## 决策理由

- 站内通知是零外部依赖、可审计、可回滚的基础能力；先默认启用能立即产生协作价值。
- 邮件/企微“完整实现 + 默认关闭”满足“后续自己配置再启用”的要求，同时避免开发/CI 误发。
- outbox + 任务队列把“发送”从请求线程解耦，能实现重试/死信/限速，并与 M4 服务端任务切片一致。
- 用本地 SMTP sink/HTTP stub 验证协议路径，既证明“功能完整”，又不接触真实账号/生产群。
- 严格区分“内部通知”和“对客户发送”，避免把 M4 变成未经审批的外部群发工具。

## 取舍和影响

- **正向**：站内默认可用；邮件/企微代码完整、配置即启用；发送可追踪、可重试、可停用；测试可离线验证。
- **负向**：需要新增 `app_user.email`、通知/投递/任务表；SMTP/企微配置有一定运维成本；企微机器人只能发到群，不能按用户定向；单节点限速不能跨实例。
- **性能**：通知写入在同一事务；外发异步；大项目/多收件人需分页/批处理；任务轮询间隔可配置。
- **兼容**：新增表通过 V9；`AdminTables` 是否包含通知/投递/任务需在 ADR-0008 增补中明确（默认运营表不导出）；导出/导入演练需覆盖。

## 安全/数据/迁移影响

- 新增表（M4 S5，V10）：`notification`、`notification_delivery`、`server_job`；`app_user.email` 在 V7。
- 秘密：SMTP 密码、企微 webhook URL/key 只从环境/OS 存储读取；不写 DB、不写日志、不写导出/交接包；配置界面只显示掩码。
- 外发内容：仅内部事件摘要；不附源码/客户附件；链接需登录且有项目权限。
- 审计：通知创建/投递/启停/测试发送；审计不可篡改（V3 触发器）。
- 回滚：关闭渠道开关即可停止外发；已入队消息可标记取消/死信；站内通知保留。

## 验证计划和结果

**S0（本 ADR）**：现状与配置模型核对；无代码变更；无外部发送。

**M4 S5 必须验证**：

1. 默认配置：站内通知可用；邮件/企微 `enabled=false` 时**零外发**（mock/网络探针断言）；
2. 启用配置：本地 SMTP sink 收到邮件（收件人/主题/正文/头注入防护）；本地 HTTP stub 收到企微 JSON（URL/字段/返回码处理）；
3. 重试/退避/死信/幂等；限速；无 email 跳过；停用渠道后不再发送；
4. 收件人只含项目成员/org_admin；操作者不自通知；跨项目不泄露；
5. 审计落库且不含秘密/正文；
6. MCP 只读进程不跑任务/不发送；
7. 前端：通知列表/未读数/已读；渠道状态与配置缺失提示；测试发送。

## 回滚或替代路径

- 企微机器人不可用/被限 → 改用邮件或站内；M4.5 再评估企微自建应用（需新 ADR：回调、加解密、公网、权限）。
- SMTP 不可用 → 站内通知继续；外发死信可重试/导出排查。
- 需要向客户发送 → 新 ADR：客户授权、模板审批、退订、合规、发送审计。

## 后续行动

1. M4 S1：`app_user.email` 与用户管理；
2. M4 S5：通知/任务/渠道实现 + 本地 SMTP/HTTP 测试；
3. M4.5-3：负责人配置 SMTP/企微并做测试发送、监控与运维手册；
4. 同步 `docs/dev-setup.md` 配置说明与 `docs/08` 外发边界。

## 实施记录（2026-11-13）

- M4 S5 落地 V10（notification/notification_delivery/server_job）、站内默认启用、邮件/企微完整实现但默认关闭、writer-only worker、重试/死信、org_admin 状态/测试发送。
- 真实 SMTP/企微未启用；测试使用 Mock JavaMailSender + 本地 HTTP stub；启用配置/真实联调留 M4.5。
