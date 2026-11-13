package com.mammoth.domain.external;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.mammoth.domain.web.ApiException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Parses CSV/JSON exports into canonical external records. CSV supports UTF-8/GBK and the Jira
 * Data Center 8.x Chinese headers; JSON supports arrays, {@code {records:[]}} and Jira-style
 * {@code {issues:[{key,fields:{...}}]}} objects. No network access.
 */
@Component
public class ExternalImportParser {

  private static final int MAX_ROWS = 50_000;
  private static final long MAX_BYTES = 50L * 1024 * 1024;

  private final ObjectMapper mapper = new ObjectMapper();

  public ImportReport.ParseResult parse(byte[] content, String originalName, String mappingProfile) {
    if (content == null || content.length == 0) {
      throw ApiException.badRequest("import file must not be empty");
    }
    if (content.length > MAX_BYTES) {
      throw ApiException.badRequest("import file is too large (limit 50 MB)");
    }
    ImportMappingProfile profile = profile(mappingProfile);
    String text = stripBom(new String(content, detect(content)));
    String trimmed = text.trim();
    boolean json = (originalName != null && originalName.toLowerCase().endsWith(".json"))
        || trimmed.startsWith("[")
        || trimmed.startsWith("{");
    return json ? parseJson(trimmed, profile) : parseCsv(text, profile);
  }

  private ImportMappingProfile profile(String value) {
    if (value == null || value.isBlank() || "generic".equals(value.trim())) {
      return ImportMappingProfile.generic();
    }
    String normalized = value.trim();
    if ("jira".equals(normalized)) {
      return ImportMappingProfile.jira();
    }
    if (!normalized.startsWith("{")) {
      throw ApiException.badRequest("unsupported mapping profile: " + normalized);
    }
    try {
      JsonNode node = mapper.readTree(normalized);
      Map<String, String> fields = new LinkedHashMap<>();
      for (String canonical : ImportMappingProfile.CANONICAL_FIELDS) {
        JsonNode mapped = node.get(canonical);
        if (mapped != null && mapped.isTextual() && !mapped.asText().isBlank()) {
          fields.put(canonical, mapped.asText().trim());
        }
      }
      String defaultType = node.path("_externalType").asText("record");
      return new ImportMappingProfile("custom", defaultType, fields);
    } catch (Exception error) {
      throw ApiException.badRequest("invalid mapping profile JSON: " + error.getMessage());
    }
  }

  private ImportReport.ParseResult parseCsv(String text, ImportMappingProfile profile) {
    List<ImportedRecord> records = new ArrayList<>();
    List<ImportReport.RowError> errors = new ArrayList<>();
    CSVFormat format = CSVFormat.DEFAULT.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setIgnoreEmptyLines(true)
        .setTrim(true)
        .build();
    int rowCount = 0;
    try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
      Map<String, Integer> headers = parser.getHeaderMap();
      for (String canonical : List.of("externalId", "externalKey", "title")) {
        String source = profile.fields().get(canonical);
        if (source != null && !headers.containsKey(source)) {
          throw ApiException.badRequest("mapping column not found in CSV header: " + source);
        }
      }
      for (CSVRecord record : parser) {
        rowCount++;
        if (rowCount > MAX_ROWS) {
          throw ApiException.badRequest("import has too many rows (limit " + MAX_ROWS + ")");
        }
        if (record.size() == 0 || allBlank(record)) {
          continue;
        }
        Map<String, String> raw = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> header : headers.entrySet()) {
          int index = header.getValue();
          raw.put(header.getKey(), index < record.size() ? record.get(index) : "");
        }
        try {
          records.add(normalize(raw, profile));
        } catch (RowProblem problem) {
          errors.add(new ImportReport.RowError(rowCount + 1, problem.getMessage()));
        }
      }
    } catch (IOException error) {
      throw ApiException.badRequest("cannot parse CSV: " + error.getMessage());
    }
    return new ImportReport.ParseResult(records, errors, rowCount);
  }

  private ImportReport.ParseResult parseJson(String text, ImportMappingProfile profile) {
    JsonNode root;
    try {
      root = mapper.readTree(text);
    } catch (Exception error) {
      throw ApiException.badRequest("cannot parse JSON: " + error.getMessage());
    }
    List<JsonNode> nodes = new ArrayList<>();
    if (root.isArray()) {
      root.forEach(nodes::add);
    } else if (root.isObject() && (root.has("records") || root.has("issues"))) {
      JsonNode array = root.has("records") ? root.get("records") : root.get("issues");
      if (!array.isArray()) {
        throw ApiException.badRequest("JSON records/issues must be an array");
      }
      array.forEach(nodes::add);
    } else if (root.isObject()) {
      nodes.add(root);
    } else {
      throw ApiException.badRequest("JSON must be an object or array");
    }
    if (nodes.size() > MAX_ROWS) {
      throw ApiException.badRequest("import has too many rows (limit " + MAX_ROWS + ")");
    }
    List<ImportedRecord> records = new ArrayList<>();
    List<ImportReport.RowError> errors = new ArrayList<>();
    int row = 0;
    for (JsonNode node : nodes) {
      row++;
      Map<String, String> raw = flatten(node);
      try {
        records.add(normalize(raw, profile));
      } catch (RowProblem problem) {
        errors.add(new ImportReport.RowError(row, problem.getMessage()));
      }
    }
    return new ImportReport.ParseResult(records, errors, nodes.size());
  }

  private static Map<String, String> flatten(JsonNode node) {
    Map<String, String> raw = new LinkedHashMap<>();
    if (!node.isObject()) {
      return raw;
    }
    node.properties().forEach(entry -> {
      JsonNode value = entry.getValue();
      if (value.isValueNode() && !value.isNull()) {
        raw.put(entry.getKey(), value.asText());
      } else if (value.isObject() && value.has("name")) {
        raw.put(entry.getKey(), value.get("name").asText());
      } else if (!value.isNull()) {
        raw.put(entry.getKey(), value.toString());
      }
    });
    JsonNode fields = node.get("fields");
    if (fields != null && fields.isObject()) {
      fields.properties().forEach(entry -> {
        JsonNode value = entry.getValue();
        if (value.isValueNode() && !value.isNull()) {
          raw.put(entry.getKey(), value.asText());
        } else if (value.isObject() && value.has("name")) {
          raw.put(entry.getKey(), value.get("name").asText());
        }
      });
      if (node.has("key")) {
        raw.put("key", node.get("key").asText());
      }
    }
    return raw;
  }

  private static ImportedRecord normalize(Map<String, String> raw, ImportMappingProfile profile) {
    String externalKey = value(raw, profile, "externalKey");
    String externalId = value(raw, profile, "externalId");
    if (externalId == null && externalKey != null) {
      externalId = externalKey;
    }
    if (externalKey == null) {
      externalKey = externalId;
    }
    if (externalId == null) {
      throw new RowProblem("missing external id/key");
    }
    String title = value(raw, profile, "title");
    if (title == null) {
      title = externalKey;
    }
    if (title == null || title.isBlank()) {
      throw new RowProblem("missing title for " + externalId);
    }
    String externalType = value(raw, profile, "externalType");
    if (externalType == null) {
      externalType = profile.defaultExternalType();
    }
    Map<String, String> metadata = new LinkedHashMap<>(raw);
    return new ImportedRecord(
        externalType,
        externalId,
        externalKey,
        "",
        title,
        value(raw, profile, "description"),
        value(raw, profile, "status"),
        value(raw, profile, "type"),
        value(raw, profile, "priority"),
        value(raw, profile, "createdAt"),
        value(raw, profile, "updatedAt"),
        value(raw, profile, "url"),
        value(raw, profile, "assignee"),
        value(raw, profile, "reporter"),
        value(raw, profile, "component"),
        metadata);
  }

  private static String value(Map<String, String> raw, ImportMappingProfile profile,
      String canonical) {
    String source = profile.fields().get(canonical);
    if (source == null) {
      return null;
    }
    String value = raw.get(source);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static Charset detect(byte[] content) {
    if (content.length >= 3 && (content[0] & 0xFF) == 0xEF
        && (content[1] & 0xFF) == 0xBB && (content[2] & 0xFF) == 0xBF) {
      return StandardCharsets.UTF_8;
    }
    try {
      StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(content));
      return StandardCharsets.UTF_8;
    } catch (CharacterCodingException error) {
      return Charset.forName("GBK");
    }
  }

  private static String stripBom(String value) {
    return value.startsWith("\uFEFF") ? value.substring(1) : value;
  }

  private static boolean allBlank(CSVRecord record) {
    for (String value : record) {
      if (value != null && !value.isBlank()) {
        return false;
      }
    }
    return true;
  }

  private static final class RowProblem extends RuntimeException {
    private RowProblem(String message) {
      super(message);
    }
  }
}
