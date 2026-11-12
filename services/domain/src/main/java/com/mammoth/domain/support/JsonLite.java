package com.mammoth.domain.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON writer for persisted payloads (requirement snapshots, audit details).
 *
 * <p>Deliberately dependency-free: snapshot payloads are written inside a domain transaction and
 * must not depend on a web serialization stack (see ADR-0010). Supports {@link Map}, {@link Iterable},
 * {@link String}, {@link Number}, {@link Boolean} and {@code null}.
 */
public final class JsonLite {

  private JsonLite() {
  }

  public static String write(Object value) {
    StringBuilder out = new StringBuilder();
    writeValue(value, out);
    return out.toString();
  }

  /** Convenience for ordered object literals: {@code object("a", 1, "b", "x")}. */
  public static Map<String, Object> object(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int index = 0; index + 1 < pairs.length; index += 2) {
      map.put(String.valueOf(pairs[index]), pairs[index + 1]);
    }
    return map;
  }

  public static ArrayList<Object> array(Object... items) {
    ArrayList<Object> list = new ArrayList<>();
    for (Object item : items) {
      list.add(item);
    }
    return list;
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

  private static void writeString(String string, StringBuilder out) {
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
  }
}
