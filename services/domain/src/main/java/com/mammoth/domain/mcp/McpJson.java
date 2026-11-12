package com.mammoth.domain.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny JSON reader/writer for the MCP stdio protocol (no external dependency).
 *
 * <p>MCP uses newline-delimited JSON-RPC 2.0; this helper keeps the MCP entrypoint dependency-free
 * and independent from Jackson to avoid coupling protocol framing to the web stack.
 */
final class McpJson {

  private McpJson() {
  }

  static Object parse(String text) {
    Parser parser = new Parser(text);
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

  private static void writeValue(Object value, StringBuilder out) {
    if (value == null) {
      out.append("null");
    } else if (value instanceof String string) {
      out.append('"');
      for (int index = 0; index < string.length(); index++) {
        char character = string.charAt(index);
        switch (character) {
          case '"' -> out.append("\\\"");
          case '\\' -> out.append("\\\\");
          case '\n' -> out.append("\\n");
          case '\r' -> out.append("\\r");
          case '\t' -> out.append("\\t");
          default -> {
            if (character < 0x20) {
              out.append(String.format("\\u%04x", (int) character));
            } else {
              out.append(character);
            }
          }
        }
      }
      out.append('"');
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
        writeValue(String.valueOf(entry.getKey()), out);
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
      writeValue(String.valueOf(value), out);
    }
  }

  static Map<String, Object> orderedMap(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int index = 0; index + 1 < pairs.length; index += 2) {
      map.put(String.valueOf(pairs[index]), pairs[index + 1]);
    }
    return map;
  }

  static List<Object> list(Object... items) {
    List<Object> result = new ArrayList<>();
    for (Object item : items) {
      result.add(item);
    }
    return result;
  }

  private static final class Parser {

    private final String text;
    private int index;

    private Parser(String text) {
      this.text = text;
    }

    private Object value() {
      skipWhitespace();
      if (index >= text.length()) {
        throw new IllegalArgumentException("unexpected end of input");
      }
      return switch (text.charAt(index)) {
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
        char separator = next();
        if (separator == '}') {
          return result;
        }
        if (separator != ',') {
          throw new IllegalArgumentException("expected , or } at " + (index - 1));
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
        char separator = next();
        if (separator == ']') {
          return result;
        }
        if (separator != ',') {
          throw new IllegalArgumentException("expected , or ] at " + (index - 1));
        }
      }
    }

    private String string() {
      expect('"');
      StringBuilder result = new StringBuilder();
      while (true) {
        char character = next();
        if (character == '"') {
          return result.toString();
        }
        if (character != '\\') {
          result.append(character);
          continue;
        }
        char escaped = next();
        switch (escaped) {
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
          default -> throw new IllegalArgumentException("bad escape \\" + escaped);
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
      return raw.contains(".") || raw.contains("e") || raw.contains("E")
          ? Double.parseDouble(raw)
          : Long.parseLong(raw);
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
        throw new IllegalArgumentException("unexpected end of input");
      }
      return text.charAt(index);
    }

    private char next() {
      char character = peek();
      index++;
      return character;
    }

    private void expect(char expected) {
      if (next() != expected) {
        throw new IllegalArgumentException("expected " + expected + " at " + (index - 1));
      }
    }
  }
}
