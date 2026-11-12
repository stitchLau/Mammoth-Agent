import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mammoth M0 spike — minimal MCP (Model Context Protocol) server over stdio.
 *
 * This is a disposable ADR-0002 boundary probe, not product code:
 * it proves that a Java Domain API can be exposed to DSH through the documented
 * MCP seam (config-only bundle inserting @deepseek-ai/dsh-mcp-client) without the
 * plugin layer importing any DSH package.
 *
 * Protocol: newline-delimited JSON-RPC 2.0 on stdin/stdout; diagnostics on stderr only.
 * Exposed tool: mammoth_project_summary (synthetic data, no filesystem/network access).
 */
public final class MammothMcpServer {

  private static final String PROTOCOL_VERSION = "2024-11-05";
  private static final String SERVER_NAME = "mammoth-domain-stub";
  private static final String SERVER_VERSION = "0.1.0";
  private static final String TOOL_NAME = "mammoth_project_summary";

  private MammothMcpServer() {
  }

  public static void main(String[] args) throws Exception {
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
    System.err.println("[mammoth-mcp] started protocol=" + PROTOCOL_VERSION);

    String line;
    while ((line = in.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      }
      Object parsed;
      try {
        parsed = Json.parse(line);
      } catch (RuntimeException error) {
        System.err.println("[mammoth-mcp] bad json: " + error.getMessage());
        continue;
      }
      if (!(parsed instanceof Map)) {
        continue;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> message = (Map<String, Object>) parsed;
      String method = asString(message.get("method"));
      Object id = message.get("id");
      if (method == null || id == null) {
        // Responses from the client, or notifications such as notifications/initialized.
        continue;
      }
      try {
        dispatch(out, id, method, message);
      } catch (RuntimeException error) {
        System.err.println("[mammoth-mcp] handler error: " + error);
        sendError(out, id, -32603, "Internal error: " + error.getMessage());
      }
    }
    System.err.println("[mammoth-mcp] stdin closed, exiting");
  }

  private static void dispatch(PrintWriter out, Object id, String method, Map<String, Object> message) {
    switch (method) {
      case "initialize" -> sendResult(out, id, map(
          "protocolVersion", PROTOCOL_VERSION,
          "capabilities", map("tools", map()),
          "serverInfo", map("name", SERVER_NAME, "version", SERVER_VERSION)));
      case "ping" -> sendResult(out, id, map());
      case "tools/list" -> sendResult(out, id, map("tools", List.of(toolDescriptor())));
      case "tools/call" -> handleToolCall(out, id, message);
      default -> sendError(out, id, -32601, "Method not found: " + method);
    }
  }

  private static Map<String, Object> toolDescriptor() {
    return map(
        "name", TOOL_NAME,
        "description",
        "Return a synthetic project summary from the Mammoth Domain API stub. Spike fixture: "
            + "no real project data is stored or read.",
        "inputSchema", map(
            "type", "object",
            "properties", map(
                "projectId", map(
                    "type", "string",
                    "description", "Project identifier to summarize.")),
            "required", List.of("projectId")));
  }

  private static void handleToolCall(PrintWriter out, Object id, Map<String, Object> message) {
    Map<String, Object> params = asMap(message.get("params"));
    String name = asString(params.get("name"));
    if (!TOOL_NAME.equals(name)) {
      sendError(out, id, -32602, "Unknown tool: " + name);
      return;
    }
    Map<String, Object> arguments = asMap(params.get("arguments"));
    String projectId = asString(arguments.get("projectId"));
    if (projectId == null || projectId.isBlank()) {
      sendResult(out, id, map(
          "content", List.of(map("type", "text", "text", "projectId is required")),
          "isError", Boolean.TRUE));
      return;
    }
    String payload = Json.write(map(
        "source", "mammoth-domain-api-stub",
        "synthetic", Boolean.TRUE,
        "projectId", projectId,
        "summary", "Project " + projectId + ": 2 open requirements, 1 linked knowledge document."));
    sendResult(out, id, map(
        "content", List.of(map("type", "text", "text", payload)),
        "isError", Boolean.FALSE));
  }

  private static void sendResult(PrintWriter out, Object id, Object result) {
    out.println(Json.write(map("jsonrpc", "2.0", "id", id, "result", result)));
  }

  private static void sendError(PrintWriter out, Object id, int code, String message) {
    out.println(Json.write(map(
        "jsonrpc", "2.0",
        "id", id,
        "error", map("code", code, "message", message))));
  }

  private static Map<String, Object> map(Object... pairs) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i + 1 < pairs.length; i += 2) {
      result.put(String.valueOf(pairs[i]), pairs[i + 1]);
    }
    return result;
  }

  private static Map<String, Object> asMap(Object value) {
    if (value instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) value;
      return map;
    }
    return Map.of();
  }

  private static String asString(Object value) {
    return value instanceof String string ? string : null;
  }

  /** Minimal recursive-descent JSON reader/writer; no external dependencies. */
  static final class Json {

    private final String text;
    private int index;

    private Json(String text) {
      this.text = text;
    }

    static Object parse(String text) {
      Json parser = new Json(text);
      Object value = parser.value();
      parser.skipWhitespace();
      if (parser.index != text.length()) {
        throw new IllegalArgumentException("trailing characters at " + parser.index);
      }
      return value;
    }

    static String write(Object value) {
      StringBuilder out = new StringBuilder();
      writeValue(value, out);
      return out.toString();
    }

    private Object value() {
      skipWhitespace();
      if (index >= text.length()) {
        throw new IllegalArgumentException("unexpected end");
      }
      char c = text.charAt(index);
      return switch (c) {
        case '{' -> object();
        case '[' -> array();
        case '"' -> string();
        case 't' -> literal("true", Boolean.TRUE);
        case 'f' -> literal("false", Boolean.FALSE);
        case 'n' -> literal("null", null);
        default -> number();
      };
    }

    private Map<String, Object> object() {
      Map<String, Object> result = new LinkedHashMap<>();
      index++;
      skipWhitespace();
      if (peek() == '}') {
        index++;
        return result;
      }
      while (true) {
        skipWhitespace();
        String key = string();
        skipWhitespace();
        expect(':');
        result.put(key, value());
        skipWhitespace();
        char c = next();
        if (c == '}') {
          return result;
        }
        if (c != ',') {
          throw new IllegalArgumentException("expected , or } in object");
        }
      }
    }

    private List<Object> array() {
      List<Object> result = new ArrayList<>();
      index++;
      skipWhitespace();
      if (peek() == ']') {
        index++;
        return result;
      }
      while (true) {
        result.add(value());
        skipWhitespace();
        char c = next();
        if (c == ']') {
          return result;
        }
        if (c != ',') {
          throw new IllegalArgumentException("expected , or ] in array");
        }
      }
    }

    private String string() {
      expect('"');
      StringBuilder result = new StringBuilder();
      while (true) {
        char c = next();
        if (c == '"') {
          return result.toString();
        }
        if (c != '\\') {
          result.append(c);
          continue;
        }
        char escape = next();
        switch (escape) {
          case '"' -> result.append('"');
          case '\\' -> result.append('\\');
          case '/' -> result.append('/');
          case 'b' -> result.append('\b');
          case 'f' -> result.append('\f');
          case 'n' -> result.append('\n');
          case 'r' -> result.append('\r');
          case 't' -> result.append('\t');
          case 'u' -> {
            String hex = text.substring(index, index + 4);
            result.append((char) Integer.parseInt(hex, 16));
            index += 4;
          }
          default -> throw new IllegalArgumentException("bad escape \\" + escape);
        }
      }
    }

    private Object number() {
      int start = index;
      while (index < text.length() && "-+.eE0123456789".indexOf(text.charAt(index)) >= 0) {
        index++;
      }
      String raw = text.substring(start, index);
      if (raw.isEmpty()) {
        throw new IllegalArgumentException("expected value at " + start);
      }
      if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
        return Double.parseDouble(raw);
      }
      return Long.parseLong(raw);
    }

    private Object literal(String token, Object value) {
      if (!text.startsWith(token, index)) {
        throw new IllegalArgumentException("expected " + token + " at " + index);
      }
      index += token.length();
      return value;
    }

    private void skipWhitespace() {
      while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
        index++;
      }
    }

    private char peek() {
      if (index >= text.length()) {
        throw new IllegalArgumentException("unexpected end");
      }
      return text.charAt(index);
    }

    private char next() {
      char c = peek();
      index++;
      return c;
    }

    private void expect(char expected) {
      if (next() != expected) {
        throw new IllegalArgumentException("expected " + expected + " at " + (index - 1));
      }
    }

    private static void writeValue(Object value, StringBuilder out) {
      if (value == null) {
        out.append("null");
      } else if (value instanceof String string) {
        writeString(string, out);
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
          writeString(String.valueOf(entry.getKey()), out);
          out.append(':');
          writeValue(entry.getValue(), out);
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
          writeValue(item, out);
        }
        out.append(']');
      } else {
        writeString(String.valueOf(value), out);
      }
    }

    private static void writeString(String value, StringBuilder out) {
      out.append('"');
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        switch (c) {
          case '"' -> out.append("\\\"");
          case '\\' -> out.append("\\\\");
          case '\n' -> out.append("\\n");
          case '\r' -> out.append("\\r");
          case '\t' -> out.append("\\t");
          default -> {
            if (c < 0x20) {
              out.append(String.format("\\u%04x", (int) c));
            } else {
              out.append(c);
            }
          }
        }
      }
      out.append('"');
    }
  }
}
