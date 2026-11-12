package com.mammoth.retrievalspike;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ADR-0005 spike — retrieval pipeline mechanics for Mammoth M1.
 *
 * <p>Compares three strategies over a synthetic Chinese corpus across two projects:
 * <ul>
 *   <li>A: SQLite FTS5 full-text with trigram tokenizer (BM25 ranking);</li>
 *   <li>B: vector-only with a deterministic local hash-embedding baseline (character trigrams,
 *       256 dimensions, cosine) — no external embedding model, so semantic quality is NOT
 *       evaluated, only pipeline mechanics;</li>
 *   <li>C: hybrid Reciprocal Rank Fusion (RRF) of A and B.</li>
 * </ul>
 *
 * <p>Also verifies scope filtering (a query scoped to P1 must never return P2 chunks), index
 * rebuild, and the citation structure. Output is UTF-8 JSON. Disposable spike code.
 */
public final class RetrievalSpike {

  private static final int CHUNK_SIZE = 80;
  private static final int CHUNK_OVERLAP = 20;
  private static final int DIM = 256;
  private static final int TOP_K = 3;

  private record Doc(String id, String projectId, String title, String body) {
  }

  private record Query(String text, String expectedDocId, String kind) {
  }

  private record Hit(String chunkId, String docId, String projectId, int ordinal, String text, double score) {
  }

  private static final List<Doc> DOCS = List.of(
      new Doc("d1", "P1", "收货流程异常处理",
          "收货流程中条码重复时，先冻结库存再人工复核，异常需当天闭环。冻结由仓管发起，质检确认后解冻。"),
      new Doc("d2", "P1", "收货流程正常路径",
          "供应商到货后先预约月台，扫码核对采购订单，确认数量与批次后生成收货单并入库存台账。"),
      new Doc("d3", "P1", "现场盘点规则",
          "现场盘点分冻结盘与循环盘，差异超过阈值触发复盘流程，复盘结果需仓库主管签字。"),
      new Doc("d4", "P1", "库存台账结构",
          "库存台账记录物料、批次、库位、数量与状态，冻结数量单独记录，不允许直接覆盖历史记录。"),
      new Doc("d5", "P1", "质检不合格处理",
          "质检不合格时进入待处理区，隔离库存并通知供应商，退货或让步接收需质量经理审批。"),
      new Doc("d6", "P1", "工单下达流程",
          "生产计划确认后下达工单，工单绑定物料清单与工艺路线，变更需计划员重新排产。"),
      new Doc("d7", "P1", "角色权限说明",
          "仓管负责收货与盘点，质检负责判定，计划员负责工单，主管负责审批与复盘签字。"),
      new Doc("d8", "P1", "数据库对象清单",
          "核心对象包含工单、库存、质检与收货表，收货流程写入库存台账并保留操作日志。"),
      new Doc("d9", "P2", "采购订单审批流程",
          "采购订单超过金额阈值需财务复核，审批链包含部门经理与财务总监。"),
      new Doc("d10", "P2", "供应商主数据",
          "供应商主数据包含银行账户、税号与联系人，变更需采购与财务双人复核。"),
      new Doc("d11", "P2", "财务对账规则",
          "月末执行供应商对账，差异挂账并生成对账差异表，未结清不得下新订单。"),
      new Doc("d12", "P2", "报表导出说明",
          "报表导出支持 Excel 与 PDF，导出动作记录操作人与时间，敏感字段默认脱敏。"));

  private static final List<Query> QUERIES = List.of(
      new Query("条码重复怎么处理", "d1", "exact"),
      new Query("到货扫码核对采购订单", "d2", "exact"),
      new Query("盘点差异超过阈值", "d3", "exact"),
      new Query("冻结数量怎么记录", "d4", "partial"),
      new Query("质检不合格隔离库存", "d5", "exact"),
      new Query("工单变更需要重新排产", "d6", "exact"),
      new Query("谁负责收货与盘点", "d7", "partial"),
      new Query("核心数据库对象有哪些", "d8", "partial"),
      new Query("库房盘货出现差异如何复盘", "d3", "semantic"),
      new Query("不合格货物暂存位置和审批人", "d5", "semantic"));

  private RetrievalSpike() {
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("usage: <workDir> <outJson>");
      System.exit(2);
    }
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException ignored) {
      // reported by connection attempt
    }
    Path work = Path.of(args[0]).toAbsolutePath();
    Path out = Path.of(args[1]);
    Files.createDirectories(work);
    Path db = work.resolve("retrieval.db");
    Files.deleteIfExists(db);
    String url = "jdbc:sqlite:" + db;

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("docs", DOCS.size());
    result.put("queries", QUERIES.size());
    result.put("chunkSize", CHUNK_SIZE);
    result.put("chunkOverlap", CHUNK_OVERLAP);
    result.put("vectorDim", DIM);
    result.put("embeddingBaseline", "char-trigram hashing (no semantic model)");

    // ---------- index build ----------
    long buildStart = System.nanoTime();
    int chunks = buildIndex(url);
    result.put("chunks", chunks);
    result.put("indexBuildMs", (System.nanoTime() - buildStart) / 1_000_000);
    result.put("indexSizeBytes", Files.size(db));

    // ---------- per-strategy evaluation ----------
    result.put("fulltext", evaluate(url, "fulltext"));
    result.put("vector", evaluate(url, "vector"));
    result.put("hybrid", evaluate(url, "hybrid"));

    // ---------- scope filtering ----------
    result.put("scopeViolations", countScopeViolations(url));

    // ---------- rebuild ----------
    long rebuildStart = System.nanoTime();
    try (Connection c = DriverManager.getConnection(url)) {
      execute(c, "DROP TABLE IF EXISTS chunk_fts");
      execute(c, "DROP TABLE IF EXISTS chunk");
      execute(c, "DROP TABLE IF EXISTS chunk_vector");
    }
    int rebuilt = buildIndex(url);
    result.put("rebuildChunks", rebuilt);
    result.put("rebuildMs", (System.nanoTime() - rebuildStart) / 1_000_000);
    result.put("rebuildFulltext", evaluate(url, "fulltext"));

    // ---------- citation sample ----------
    result.put("citationSample", citationSample(url, "条码重复怎么处理"));

    result.put("_out", out.toAbsolutePath().toString());
    String json = toJson(result);
    Files.writeString(out, json, StandardCharsets.UTF_8);
    System.out.println(json);
  }

  // ---------------- index ----------------

  private static int buildIndex(String url) throws Exception {
    int chunkCount = 0;
    try (Connection c = DriverManager.getConnection(url)) {
      execute(c, "CREATE TABLE IF NOT EXISTS chunk ("
          + "chunk_id TEXT PRIMARY KEY, doc_id TEXT NOT NULL, project_id TEXT NOT NULL,"
          + "ordinal INTEGER NOT NULL, text TEXT NOT NULL, start_offset INTEGER NOT NULL)");
      execute(c, "CREATE VIRTUAL TABLE IF NOT EXISTS chunk_fts USING fts5(chunk_id UNINDEXED, text, tokenize='trigram')");
      execute(c, "CREATE TABLE IF NOT EXISTS chunk_vector (chunk_id TEXT PRIMARY KEY, vec BLOB NOT NULL)");
      try (PreparedStatement chunk = c.prepareStatement(
          "INSERT INTO chunk VALUES (?,?,?,?,?,?)");
           PreparedStatement fts = c.prepareStatement("INSERT INTO chunk_fts (chunk_id,text) VALUES (?,?)");
           PreparedStatement vec = c.prepareStatement("INSERT INTO chunk_vector (chunk_id,vec) VALUES (?,?)")) {
        for (Doc doc : DOCS) {
          List<int[]> spans = chunkSpans(doc.body());
          int ordinal = 0;
          for (int[] span : spans) {
            String text = doc.body().substring(span[0], span[1]);
            String chunkId = doc.id() + "#" + ordinal;
            chunk.setString(1, chunkId);
            chunk.setString(2, doc.id());
            chunk.setString(3, doc.projectId());
            chunk.setInt(4, ordinal);
            chunk.setString(5, text);
            chunk.setInt(6, span[0]);
            chunk.addBatch();
            fts.setString(1, chunkId);
            fts.setString(2, doc.title() + " " + text);
            fts.addBatch();
            vec.setString(1, chunkId);
            vec.setBytes(2, embedding(doc.title() + " " + text));
            vec.addBatch();
            ordinal++;
            chunkCount++;
          }
        }
        chunk.executeBatch();
        fts.executeBatch();
        vec.executeBatch();
      }
    }
    return chunkCount;
  }

  private static List<int[]> chunkSpans(String body) {
    List<int[]> spans = new ArrayList<>();
    int start = 0;
    while (start < body.length()) {
      int end = Math.min(start + CHUNK_SIZE, body.length());
      spans.add(new int[] {start, end});
      if (end == body.length()) {
        break;
      }
      start = Math.max(start + 1, end - CHUNK_OVERLAP);
    }
    return spans;
  }

  /** Deterministic local hash embedding: character trigrams hashed into DIM buckets, L2 normalized. */
  private static byte[] embedding(String text) {
    float[] vector = new float[DIM];
    for (int i = 0; i + 2 < text.length(); i++) {
      int hash = text.substring(i, i + 3).hashCode();
      vector[Math.floorMod(hash, DIM)] += 1.0f;
    }
    double norm = 0;
    for (float value : vector) {
      norm += value * value;
    }
    norm = Math.sqrt(norm);
    if (norm > 0) {
      for (int i = 0; i < DIM; i++) {
        vector[i] = (float) (vector[i] / norm);
      }
    }
    ByteBuffer buffer = ByteBuffer.allocate(DIM * 4);
    for (float value : vector) {
      buffer.putFloat(value);
    }
    return buffer.array();
  }

  // ---------------- evaluation ----------------

  private static Map<String, Object> evaluate(String url, String strategy) throws Exception {
    int hitsAt1 = 0;
    int hitsAt3 = 0;
    long totalMicros = 0;
    List<String> details = new ArrayList<>();
    try (Connection c = DriverManager.getConnection(url)) {
      for (Query query : QUERIES) {
        long start = System.nanoTime();
        List<Hit> hits = strategy.equals("fulltext")
            ? fulltextSearch(c, query.text(), "P1")
            : strategy.equals("vector")
                ? vectorSearch(c, query.text(), "P1")
                : hybridSearch(c, query.text(), "P1");
        totalMicros += (System.nanoTime() - start) / 1000;
        boolean at1 = !hits.isEmpty() && hits.get(0).docId().equals(query.expectedDocId());
        boolean at3 = hits.stream().limit(TOP_K).anyMatch(hit -> hit.docId().equals(query.expectedDocId()));
        hitsAt1 += at1 ? 1 : 0;
        hitsAt3 += at3 ? 1 : 0;
        details.add((at3 ? "HIT " : "MISS ") + query.kind() + " q='" + query.text()
            + "' expected=" + query.expectedDocId() + " got=" + hitDocs(hits));
      }
    }
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("recallAt1", hitsAt1 + "/" + QUERIES.size());
    r.put("recallAt3", hitsAt3 + "/" + QUERIES.size());
    r.put("avgQueryUs", totalMicros / QUERIES.size());
    r.put("details", details);
    return r;
  }

  private static String hitDocs(List<Hit> hits) {
    List<String> docs = new ArrayList<>();
    for (Hit hit : hits.stream().limit(TOP_K).toList()) {
      docs.add(hit.docId());
    }
    return docs.isEmpty() ? "-" : String.join(",", docs);
  }

  // ---------------- search implementations ----------------

  private static List<Hit> fulltextSearch(Connection c, String query, String projectId) throws Exception {
    List<Hit> hits = new ArrayList<>();
    try (PreparedStatement ps = c.prepareStatement(
        "SELECT ch.chunk_id, ch.doc_id, ch.project_id, ch.ordinal, ch.text, bm25(chunk_fts) AS rank "
            + "FROM chunk_fts JOIN chunk ch ON ch.chunk_id = chunk_fts.chunk_id "
            + "WHERE chunk_fts MATCH ? AND ch.project_id = ? "
            + "ORDER BY rank LIMIT ?")) {
      ps.setString(1, ftsQuery(query));
      ps.setString(2, projectId);
      ps.setInt(3, TOP_K);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          hits.add(new Hit(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
              rs.getString(5), rs.getDouble(6)));
        }
      }
    }
    return hits;
  }

  /**
   * Without a Chinese segmenter, a whole natural-language query is not a contiguous substring of a
   * document, and FTS5 trigram phrase matching would return nothing. Build an OR query over the
   * query's character trigrams so BM25 ranks chunks by shared keyword n-grams.
   */
  private static String ftsQuery(String query) {
    java.util.LinkedHashSet<String> trigrams = new java.util.LinkedHashSet<>();
    for (int i = 0; i + 3 <= query.length(); i++) {
      trigrams.add(query.substring(i, i + 3));
    }
    if (trigrams.isEmpty()) {
      return "\"" + query + "\"";
    }
    return String.join(" OR ", trigrams.stream().map(term -> "\"" + term + "\"").toList());
  }

  private static List<Hit> vectorSearch(Connection c, String query, String projectId) throws Exception {
    float[] queryVector = toFloats(embedding(query));
    List<Hit> scored = new ArrayList<>();
    try (PreparedStatement ps = c.prepareStatement(
        "SELECT ch.chunk_id, ch.doc_id, ch.project_id, ch.ordinal, ch.text, cv.vec "
            + "FROM chunk_vector cv JOIN chunk ch ON ch.chunk_id = cv.chunk_id "
            + "WHERE ch.project_id = ?")) {
      ps.setString(1, projectId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          float[] vector = toFloats(rs.getBytes(6));
          scored.add(new Hit(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
              rs.getString(5), cosine(queryVector, vector)));
        }
      }
    }
    scored.sort((a, b) -> Double.compare(b.score(), a.score()));
    return scored.subList(0, Math.min(TOP_K, scored.size()));
  }

  private static List<Hit> hybridSearch(Connection c, String query, String projectId) throws Exception {
    List<Hit> fulltext = fulltextSearch(c, query, projectId);
    List<Hit> vector = vectorSearch(c, query, projectId);
    Map<String, Double> rrf = new LinkedHashMap<>();
    Map<String, Hit> byId = new LinkedHashMap<>();
    int rank = 1;
    for (Hit hit : fulltext) {
      rrf.merge(hit.chunkId(), 1.0 / (60 + rank), Double::sum);
      byId.putIfAbsent(hit.chunkId(), hit);
      rank++;
    }
    rank = 1;
    for (Hit hit : vector) {
      rrf.merge(hit.chunkId(), 1.0 / (60 + rank), Double::sum);
      byId.putIfAbsent(hit.chunkId(), hit);
      rank++;
    }
    List<Hit> merged = new ArrayList<>();
    for (Map.Entry<String, Double> entry : rrf.entrySet()) {
      Hit hit = byId.get(entry.getKey());
      merged.add(new Hit(hit.chunkId(), hit.docId(), hit.projectId(), hit.ordinal(), hit.text(), entry.getValue()));
    }
    merged.sort((a, b) -> Double.compare(b.score(), a.score()));
    return merged.subList(0, Math.min(TOP_K, merged.size()));
  }

  private static int countScopeViolations(String url) throws Exception {
    int violations = 0;
    try (Connection c = DriverManager.getConnection(url)) {
      for (Query query : QUERIES) {
        for (Hit hit : fulltextSearch(c, query.text(), "P1")) {
          if (!"P1".equals(hit.projectId())) {
            violations++;
          }
        }
      }
    }
    return violations;
  }

  private static List<String> citationSample(String url, String query) throws Exception {
    List<String> citations = new ArrayList<>();
    try (Connection c = DriverManager.getConnection(url)) {
      for (Hit hit : fulltextSearch(c, query, "P1")) {
        citations.add("doc=" + hit.docId() + " chunk=" + hit.chunkId() + " ordinal=" + hit.ordinal()
            + " project=" + hit.projectId() + " text='" + hit.text() + "'");
      }
    }
    return citations;
  }

  private static float[] toFloats(byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    float[] floats = new float[bytes.length / 4];
    for (int i = 0; i < floats.length; i++) {
      floats[i] = buffer.getFloat();
    }
    return floats;
  }

  private static double cosine(float[] a, float[] b) {
    double dot = 0;
    double na = 0;
    double nb = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      na += a[i] * a[i];
      nb += b[i] * b[i];
    }
    return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
  }

  private static void execute(Connection c, String sql) throws Exception {
    try (Statement s = c.createStatement()) {
      s.execute(sql);
    }
  }

  private static String toJson(Object value) {
    StringBuilder out = new StringBuilder();
    writeJson(value, out);
    return out.toString();
  }

  private static void writeJson(Object value, StringBuilder out) {
    if (value == null) {
      out.append("null");
    } else if (value instanceof String string) {
      out.append('"').append(string.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append('"');
    } else if (value instanceof Number || value instanceof Boolean) {
      out.append(value);
    } else if (value instanceof Map<?, ?> map) {
      out.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!first) {
          out.append(',');
        }
        first = false;
        writeJson(String.valueOf(entry.getKey()), out);
        out.append(':');
        writeJson(entry.getValue(), out);
      }
      out.append('}');
    } else if (value instanceof Iterable<?> items) {
      out.append('[');
      boolean first = true;
      for (Object item : items) {
        if (!first) {
          out.append(',');
        }
        first = false;
        writeJson(item, out);
      }
      out.append(']');
    } else {
      writeJson(String.valueOf(value), out);
    }
  }
}
