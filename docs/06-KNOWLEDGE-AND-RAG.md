# 06 — 知识工程与 RAG 设计

## 1. 知识层级

- Global / Organization：经审批的通用方法和公司标准。
- Product：产品标准功能、流程、规则、版本。
- Customer：客户业务背景、合同蓝图、管理约定。
- Project：实际部署、定制代码、数据库、项目规则和决策。
- WorkItem：某项需求/缺陷收集的证据、分析和实施经验。

检索必须按用户权限和任务上下文过滤。客户 A 的项目知识不得无条件进入客户 B 的答案。

## 2. 资料、知识、记忆、索引区分

- Artifact：原始输入，不可被派生结果覆盖。
- KnowledgeDocument：分析、整理、验证后的可读知识。
- Memory：明确登记的长期上下文/决策，不等同于聊天历史。
- Index：全文、向量、关键词、代码符号等派生检索索引，可重新构建。

## 3. 知识文档必备元数据

`document_id`、`scope_type`、`scope_id`、`document_type`、`module`、`scenario`、`source_refs`、`source_version`、`knowledge_status`、`valid_from`、`valid_to`、`version`、`reviewer`、`reviewed_at`、`created_by`、`updated_at`。

状态至少包括：`draft`、`needs_review`、`verified`、`superseded`、`deprecated`。AI 推断不得直接标记为 `verified`。

## 4. 证据类型

- `source_code`：文件路径、符号、行号/Commit。
- `database_ddl`：schema、table/view/procedure/function、DDL 版本。
- `business_document`：文档名、页码/章节、版本。
- `conversation`：沟通时间、参与角色、原始记录引用。
- `system_observation`：经授权的环境/系统观察。
- `human_confirmation`：确认人、确认时间、确认内容。

## 5. 检索流程建议

1. 根据任务确定客户/产品/项目/版本 scope。
2. 从元数据和权限过滤候选文档。
3. 并行执行关键词/全文、向量、代码符号/数据库对象检索。
4. 对结果进行重排序与去重。
5. 提供引用片段和来源定位。
6. Agent 输出结论时区分事实、推断、冲突和未知。
7. 记录使用过的知识版本，便于复现。

## 6. 逆向工程流程

- 扫描仓库结构、语言/框架、入口和模块边界；
- 扫描数据库对象并建立对象索引；
- 识别模块和核心业务场景；
- 按模块分析业务流程、规则和数据关系；
- 对照产品标准、客户蓝图、历史需求；
- 标注冲突、未覆盖和推断；
- 人工审核后发布知识基线。

不要求一次性理解所有代码。应使用模块覆盖矩阵管理进度，但“项目总体地图 + 核心业务场景 + 关键数据关系”必须作为首批基础产物。

## 7. 防止知识污染

- 不把客户专属流程自动升级为产品标准。
- 不把一次 AI 回答自动转成正式知识。
- 不把旧版本知识用于新版本项目而不提示。
- 不把“相似项目做法”当成当前项目事实。
- 对冲突资料保留多个来源及时间，不自行抹平。
- 对失效知识做 supersede/deprecate，不物理覆盖历史。

## 8. 第一版实现策略

先使用结构化元数据 + Markdown/原始文件 + 可替换的检索接口。向量库只是 Adapter，不让业务服务直接依赖特定向量数据库。支持重新解析、重新切分、重新嵌入和索引版本升级。

## 9. M4 外部导入与知识边界（2026-11-13）

- Jira/CSM 导入产生的是 `external_record` 与“证据候选”，**不是知识文档**；必须人工映射到需求证据/工作项，再经原有分析与四眼审核才可能成为知识。
- 导入内容不自动进入全文/向量索引；不自动标记 `verified`；不把外部状态/优先级当作 Mammoth 的业务结论。
- 原始导入文件作为 Artifact 保留（sha256），证据 locator 指回外部 key/URL；知识过期/替代规则不变。
- 外部附件正文解析仍受 ADR-0009（OPEN）约束；M4 不解析 PDF/Office。
