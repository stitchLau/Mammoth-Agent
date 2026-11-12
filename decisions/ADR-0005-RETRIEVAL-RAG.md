# ADR-0005: 检索与 RAG 方案

- 状态：**Accepted v1.0**（2026-11-12 负责人批准）
- 日期：2026-11-12
- 决策人：项目负责人（2026-11-12 批准）
- 相关需求/功能：M1 项目知识底座（检索与证据引用）；M2 需求分析（上下文检索）

## 背景与约束

- 检索必须按用户权限与任务上下文过滤（`docs/06-KNOWLEDGE-AND-RAG.md` §1、§5）。
- 索引是派生数据，可重建；不得作为唯一知识保存形式。
- M1 本地单机优先；不得依赖外部服务即可完成 M1 验收。
- 关系库已定 SQLite（ADR-0003），全文检索能力已由 S4 验证（FTS5 trigram 可用；PG `tsvector('simple')` 对中文无效）。

## 需要解决的问题

1. M1 用全文、向量还是混合检索；
2. 中文切分/匹配方案；
3. 检索接口抽象与索引重建；
4. 引用（可核验证据）结构；
5. 何时引入向量检索与如何评估。

## 备选方案

- A：SQLite FTS5（trigram）+ BM25 + 查询三元组 OR。
- B：向量检索（需 embedding 模型：本地 ONNX / 外部 API）。
- C：A + B 的 RRF 混合。

## 决策

1. **M1 采用方案 A（全文检索）**：SQLite FTS5（trigram tokenizer）+ 查询字符三元组 OR + BM25 排序 + `project_id` scope 过滤；chunk 元数据入库（`doc_id`、`chunk_id`、`ordinal`、`start_offset`、`text`、`index_version`）。
2. **抽象 `SearchAdapter`**：`indexDocument` / `search(query, scope, topK)` / `rebuild()` / `deleteDocument`；全文实现先落地，向量实现后置，业务代码只依赖接口。
3. **向量与混合检索不在 M1 验收路径**：接入真实中文 embedding 前，hybrid 无证据收益（S7：hybrid 与全文同为 9/10）。
4. **中文方案**：M1 不引入分词器；实现层必须做"查询 n-gram 化"（S7 证明整句 MATCH 为 0 命中）。若真实语料召回不足，再评估本地分词库或 embedding。
5. **引用结构**：检索结果返回 `document_id/chunk_id/ordinal/start_offset/text/来源 Artifact(s)/文档版本`，Agent 输出必须携带引用；引用不可回源的结果视为无效。
6. **索引版本与重建**：`index_version` 记录切分/嵌入配置；重建为后台可重复操作，不阻塞读取。

## 决策理由

- 实测（`docs/spikes/S7-RETRIEVAL-SPIKE.md`）：全文 BM25 在 10 条中文查询上 recall@1 9/10、@3 9/10、1.2 ms/查询；hash 向量基线仅 6/10（非语义），hybrid 无增益；索引重建 71 ms、跨项目泄漏 0。
- 全文方案零外部依赖、离线可用、成本为零，满足 M1 本地单机要求。
- 把向量能力留在接口后面，避免过早绑定 embedding 供应商或模型版本（Runtime Independence 原则）。

## 取舍和影响

- 纯字面匹配无法覆盖语义改写（S7 中 1 条语义题未命中），M1 问答质量受此限制；需在 M1 验收样例中明确"未知/低置信"行为。
- 若引入向量，需要模型体积/CPU/隐私/更新策略评估；本地单机可能受资源限制。
- 关键词三元组方案在长语料上的噪声与性能未验证。
- 引用仅到 chunk 级；更精确定位（页码/行号/DDL 对象）需在 M1 T8 增强。

## 安全/数据/迁移影响

- 检索必须做 scope 与权限过滤（M1 实测 0 泄漏）；后续加工作项/文档级权限时同步扩展过滤条件。
- 嵌入与索引不得包含未授权资料；索引重建需保留来源映射。
- 切换数据库（SQLite→PG）时检索实现需替换（FTS5 → pg_trgm/全文），接口不变。

## 验证计划和结果

已执行（2026-11-12）：12 文档/10 查询的三种策略对比、scope 过滤、索引重建、引用结构；证据 `docs/spikes/S7-RETRIEVAL-SPIKE.md`。

待补充：真实长文档切分与召回、语义检索 Spike（真实 embedding）、大规模性能、更多权限维度。

## 回滚或替代路径

- 若 M1 全文召回不达标 → 先引入本地中文分词（轻量）再评估向量；接口不变。
- 若必须语义检索 → 独立 ADR/Spike 选 embedding（本地模型优先），以 RRF 接入 hybrid。
- 切换到 PostgreSQL 时以 `pg_trgm`（或分词扩展）实现同一 `SearchAdapter`。

## 后续行动

1. ~~负责人批准~~ → 2026-11-12 已批准（Accepted v1.0）。
2. M1 T8/T9 落地：`SearchAdapter`、chunk 索引、引用结构、scope 过滤与越权测试。
3. 后续独立 Spike：真实中文 embedding 与混合检索评估。
