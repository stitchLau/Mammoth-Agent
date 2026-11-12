# 03 — 领域模型（初始基线）

本文件定义业务概念，不预设具体 ORM 或数据库实现。实现前先绘制 ERD，并通过 ADR 确认聚合边界。

## 1. 核心实体

| 实体 | 责任 | 关键关系 |
|---|---|---|
| Organization | 企业/组织边界 | 拥有用户、客户、产品和项目 |
| User / Role / Permission | 身份和授权 | 用户加入组织、角色授予权限 |
| Customer | 客户主档 | 关联客户资料和项目 |
| Product | 公司产品档案 | 拥有产品版本和标准知识 |
| ProductVersion | 产品版本基线 | 关联版本说明和标准能力 |
| Project | 项目实例 | 关联客户、产品、成员、资料和工作项 |
| ProjectBaseline | 项目实际版本快照 | 关联部署版本、Git Commit、DB 结构版本 |
| Artifact | 原始资料或文件 | 关联 scope、来源、权限和版本 |
| KnowledgeDocument | 整理后的知识 | 有类型、状态、来源、适用版本和审核记录 |
| KnowledgeChunk / IndexRef | 检索派生对象 | 可重建，不是权威来源 |
| WorkItem | 工作项主对象 | 归属项目，可有子项和关联项 |
| RequirementEvidence | 需求证据 | 归属工作项，保留原始来源和时间 |
| DecisionRecord | 决策记录 | 关联项目/工作项/方案及决策人 |
| SolutionVersion | 方案版本 | 关联业务视图、技术视图、基准模型 |
| DevelopmentTask | 开发任务 | 追溯到方案设计和验收标准 |
| ExecutionRecord | 实际实施证据 | Git Commit、改动摘要、测试、偏差 |
| DeliverySnapshot | 最终交付快照 | 包含业务/技术交付文档与版本 |
| AgentRun | Agent 一次有边界的工作执行 | 记录输入引用、产物、工具及状态 |
| ApprovalRecord | 人工确认/审批 | 记录谁对什么内容作出何种决定 |

## 2. 关键关系约束

- 每个 Project 必须关联一个 Customer；可以关联多个 Product。
- WorkItem 必须有一个主 Project，可关联其他项目，但不能无主归属。
- Artifact 原始文件不可被分析过程覆盖；派生文本和知识文档另存。
- KnowledgeDocument 必须有 scope、source references、version/status。
- KnowledgeChunk 由知识文档生成，可删除重建；不得作为唯一知识保存形式。
- SolutionVersion 必须绑定需求基准版本和项目基线。
- DevelopmentTask 必须追溯到业务目标/设计项/验收标准中的至少一项。
- DeliverySnapshot 不覆盖 Approved Solution；二者表达“原计划”和“实际交付”。
- AgentRun 必须记录执行时使用的项目基线、知识版本和关键输入引用。

## 3. 工作项类型

初始枚举建议：`requirement`、`defect`、`incident`、`improvement`、`technical_debt`、`delivery_action`。第一版只完整实现 `requirement`；其他类型仅预留模型扩展点，不做空壳 UI。

## 4. 需求状态建议

`draft → collecting → ready_for_analysis → analyzing → solution_review → approved → in_development → pending_acceptance → pending_knowledge_sync → closed`

终止/旁路状态：`rejected`、`withdrawn`、`blocked`、`on_hold`。

状态变化必须有触发条件、授权角色和审计记录。状态机不能只存在于 Prompt 中。

## 5. 需求信息完整度

按需求模板检查：背景、目标、当前流程、目标流程、规则、角色/范围、异常、数据、集成、验收、约束。Agent 输出“缺失/冲突/已确认/推断”，不使用未经定义的单一成熟度百分比作为唯一门槛。

## 6. 方案数据模型

建议先定义统一的 SolutionModel（结构化基准），再渲染成：
- Business Solution：给客户/业务/管理人员；
- Technical Solution：给研发/测试；
- Development Task List：给执行人员；
- Acceptance Matrix：连接业务目标、设计项、任务和验证。

禁止业务方案、技术方案分别独立发散生成后直接定版。

## 7. 结项三件套

- Approved Design：开发前批准的设计。
- Implementation Record：开发期间实际发生的变化和偏差。
- As-built / Final Delivery：最终交付事实。

保留差异原因：客户变更、技术限制、缺陷修复、额外实现、未实现/延期、设计错误。

## 8. 领域事件建议

`ProjectCreated`、`ProjectBaselineCaptured`、`ArtifactAdded`、`KnowledgeDocumentPublished`、`WorkItemCreated`、`EvidenceAdded`、`ReadinessReassessed`、`SolutionVersionCreated`、`SolutionApproved`、`TaskHandoffCreated`、`ExecutionRecorded`、`KnowledgeSyncRequested`、`DeliverySnapshotPublished`、`WorkItemClosed`。

第一版可用应用服务内部事件，不必先引入消息中间件。
