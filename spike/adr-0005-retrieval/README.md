# ADR-0005 检索/RAG Spike（一次性）

验证 M1 检索管线机制与排序策略：

- 语料：12 篇合成中文知识文档，分属 2 个项目（P1 8 篇、P2 4 篇）；
- 查询：10 条（6 条精确术语、3 条部分措辞、1 条语义改写），期望文档已知；
- 策略：
  - `fulltext`：SQLite FTS5（trigram tokenizer + BM25）；
  - `vector`：本地确定性 hash embedding 基线（字符 trigram → 256 维，余弦）——**不含语义模型**，只验证管线机制；
  - `hybrid`：RRF(k=60) 融合；
- 同时验证：项目 scope 过滤（P1 查询不得返回 P2 片段）、索引重建、引用结构（doc/chunk/ordinal/text）。

## 运行

```powershell
mvn -B -q -f spike/adr-0005-retrieval/pom.xml -DskipTests package
$cp = "spike\adr-0005-retrieval\target\classes;spike\adr-0005-retrieval\target\lib\*"
java -cp $cp com.mammoth.retrievalspike.RetrievalSpike `
  spike/adr-0005-retrieval/target/work spike/adr-0005-retrieval/target/result-retrieval.json
```

## 说明

- 一次性验证代码，不是产品基线；结果写入 `docs/spikes/S7-RETRIEVAL-SPIKE.md`。
- 语料为合成内容，不涉及真实客户资料。
- 向量基线**不是**语义嵌入，不能代表接真实 embedding 模型后的效果；相关决策与后续 Spike 见 ADR-0005。
