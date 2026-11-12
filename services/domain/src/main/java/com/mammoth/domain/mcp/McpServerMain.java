package com.mammoth.domain.mcp;

import com.mammoth.domain.DomainApplication;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * MCP stdio entrypoint for DSH integration (ADR-0001/0005, T9).
 *
 * <p>Launch: {@code java -jar mammoth-domain-service.jar --mcp}. The same Spring context provides
 * Flyway migrations and the domain services; stdout is reserved for newline-delimited JSON-RPC so
 * all application logging is redirected to stderr.
 *
 * <p>Agent identity comes from {@code MAMMOTH_MCP_AGENT_USER_ID} (mapped to
 * {@code mammoth.mcp.agent-user-id}); the agent must be an active project member and only verified
 * knowledge is exposed. This identity strategy is temporary until the authentication task replaces
 * the header/session model end to end.
 */
public final class McpServerMain {

  private static final String PROTOCOL_VERSION = "2024-11-05";

  private McpServerMain() {
  }

  public static void main(String[] args) throws Exception {
    // MCP speaks UTF-8 on stdout. JVM System.out uses the platform encoding (GBK on zh-CN Windows),
    // which corrupts Chinese payloads, so the protocol stream is rebuilt over the raw descriptor.
    PrintStream protocolOut = new PrintStream(
        new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
    System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));

    try (ConfigurableApplicationContext context = new SpringApplicationBuilder(DomainApplication.class)
        .web(WebApplicationType.NONE)
        .run(args)) {
      McpToolService tools = context.getBean(McpToolService.class);
      String agentUserId = context.getEnvironment().getProperty("mammoth.mcp.agent-user-id", "");
      serve(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
          protocolOut, tools, agentUserId);
    }
  }

  static void serve(BufferedReader reader, PrintStream out, McpToolService tools, String agentUserId)
      throws Exception {
    String line;
    while ((line = reader.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      }
      Object parsed;
      try {
        parsed = McpJson.parse(line);
      } catch (RuntimeException error) {
        System.err.println("[mammoth-mcp] bad json: " + error.getMessage());
        continue;
      }
      if (!(parsed instanceof Map<?, ?> message)) {
        continue;
      }
      String method = asString(message.get("method"));
      Object id = message.get("id");
      if (method == null || id == null) {
        continue;
      }
      try {
        dispatch(out, id, method, message, tools, agentUserId);
      } catch (RuntimeException error) {
        sendError(out, id, -32603, "Internal error: " + error.getMessage());
      }
    }
  }

  private static void dispatch(PrintStream out, Object id, String method, Map<?, ?> message,
      McpToolService tools, String agentUserId) {
    switch (method) {
      case "initialize" -> sendResult(out, id, McpJson.orderedMap(
          "protocolVersion", PROTOCOL_VERSION,
          "capabilities", McpJson.orderedMap("tools", McpJson.orderedMap()),
          "serverInfo", McpJson.orderedMap("name", "mammoth-domain", "version", "0.1.0")));
      case "ping" -> sendResult(out, id, McpJson.orderedMap());
      case "tools/list" -> sendResult(out, id, McpJson.orderedMap("tools", toolDescriptors()));
      case "tools/call" -> handleToolCall(out, id, message, tools, agentUserId);
      default -> sendError(out, id, -32601, "Method not found: " + method);
    }
  }

  private static List<Object> toolDescriptors() {
    return List.of(
        McpJson.orderedMap(
            "name", "search_project_knowledge",
            "description",
            "Search verified Mammoth knowledge inside one project and return citable chunks. "
                + "Only verified documents in the given project are searched; results carry "
                + "document, chunk and source-reference metadata.",
            "inputSchema", McpJson.orderedMap(
                "type", "object",
                "properties", McpJson.orderedMap(
                    "projectId", McpJson.orderedMap("type", "string", "description", "Project identifier."),
                    "query", McpJson.orderedMap("type", "string", "description", "Natural-language Chinese query."),
                    "topK", McpJson.orderedMap("type", "integer", "description", "Maximum hits (1-20, default 5).")),
                "required", List.of("projectId", "query"))),
        McpJson.orderedMap(
            "name", "get_knowledge_document",
            "description",
            "Read one verified Mammoth knowledge document with its evidence references. "
                + "Draft or needs-review documents are not exposed to agents.",
            "inputSchema", McpJson.orderedMap(
                "type", "object",
                "properties", McpJson.orderedMap(
                    "projectId", McpJson.orderedMap("type", "string", "description", "Project identifier."),
                    "documentId", McpJson.orderedMap("type", "string", "description", "Knowledge document identifier.")),
                "required", List.of("projectId", "documentId"))));
  }

  private static void handleToolCall(PrintStream out, Object id, Map<?, ?> message,
      McpToolService tools, String agentUserId) {
    Map<?, ?> params = asMap(message.get("params"));
    String name = asString(params.get("name"));
    Map<?, ?> arguments = asMap(params.get("arguments"));
    if (agentUserId == null || agentUserId.isBlank()) {
      sendToolError(out, id, "MAMMOTH_MCP_AGENT_USER_ID is not configured; refusing agent access.");
      return;
    }
    try {
      switch (name == null ? "" : name) {
        case "search_project_knowledge" -> {
          List<McpToolService.SearchHitView> hits = tools.searchProjectKnowledge(
              agentUserId,
              asString(arguments.get("projectId")),
              asString(arguments.get("query")),
              asInt(arguments.get("topK")));
          List<Object> payload = hits.stream().map(hit -> (Object) McpJson.orderedMap(
              "documentId", hit.documentId(),
              "title", hit.title(),
              "chunkId", hit.chunkId(),
              "ordinal", hit.ordinal(),
              "text", hit.text(),
              "score", hit.score(),
              "sourceRefCount", hit.sourceRefCount())).toList();
          sendResult(out, id, McpJson.orderedMap(
              "content", List.of(McpJson.orderedMap("type", "text", "text", McpJson.write(payload))),
              "isError", Boolean.FALSE));
        }
        case "get_knowledge_document" -> {
          McpToolService.DocumentView document = tools.getKnowledgeDocument(
              agentUserId,
              asString(arguments.get("projectId")),
              asString(arguments.get("documentId")));
          List<Object> refs = document.sourceRefs().stream().map(ref -> (Object) McpJson.orderedMap(
              "artifactId", ref.artifactId(),
              "locator", ref.locator(),
              "note", ref.note())).toList();
          Object payload = McpJson.orderedMap(
              "id", document.id(),
              "projectId", document.projectId(),
              "docType", document.docType(),
              "title", document.title(),
              "body", document.body(),
              "status", document.status(),
              "reviewer", document.reviewer(),
              "reviewedAt", document.reviewedAt(),
              "sourceRefs", refs);
          sendResult(out, id, McpJson.orderedMap(
              "content", List.of(McpJson.orderedMap("type", "text", "text", McpJson.write(payload))),
              "isError", Boolean.FALSE));
        }
        default -> sendToolError(out, id, "unknown tool: " + name);
      }
    } catch (RuntimeException error) {
      sendToolError(out, id, error.getClass().getSimpleName() + ": " + error.getMessage());
    }
  }

  private static void sendToolError(PrintStream out, Object id, String message) {
    sendResult(out, id, McpJson.orderedMap(
        "content", List.of(McpJson.orderedMap("type", "text", "text", message)),
        "isError", Boolean.TRUE));
  }

  private static void sendResult(PrintStream out, Object id, Object result) {
    out.println(McpJson.write(McpJson.orderedMap("jsonrpc", "2.0", "id", id, "result", result)));
    out.flush();
  }

  private static void sendError(PrintStream out, Object id, int code, String message) {
    out.println(McpJson.write(McpJson.orderedMap(
        "jsonrpc", "2.0", "id", id,
        "error", McpJson.orderedMap("code", code, "message", message))));
    out.flush();
  }

  private static Map<?, ?> asMap(Object value) {
    return value instanceof Map<?, ?> map ? map : Map.of();
  }

  private static String asString(Object value) {
    return value instanceof String string ? string : null;
  }

  private static int asInt(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    return 0;
  }
}
