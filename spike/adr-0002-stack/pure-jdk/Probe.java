import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * ADR-0002 spike probe — REST entrypoint without any framework (JDK 17 only).
 *
 * Serves /health and a read-only sample endpoint so the same requirement
 * ("REST for the UI, MCP stdio for DSH agents") can be compared against Spring Boot.
 * No business data, no database, no network calls.
 */
public final class Probe {

  private static final long STARTED_AT = System.currentTimeMillis();

  private Probe() {
  }

  public static void main(String[] args) throws Exception {
    int port = args.length > 0 ? Integer.parseInt(args[0]) : 18081;
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
    server.createContext("/health", exchange -> respond(exchange,
        "{\"status\":\"up\",\"runtime\":\"pure-jdk\",\"uptimeMs\":" + (System.currentTimeMillis() - STARTED_AT) + "}"));
    server.createContext("/api/projects/PRJ-001", exchange -> respond(exchange,
        "{\"id\":\"PRJ-001\",\"name\":\"ACME-MES-2026\",\"customer\":\"ACME\",\"product\":\"MES\"}"));
    server.start();
    System.out.println("[pure-jdk] started on http://127.0.0.1:" + port
        + " in " + (System.currentTimeMillis() - STARTED_AT) + " ms");
  }

  private static void respond(HttpExchange exchange, String json) throws IOException {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }
}
