package com.mammoth.domain;

import com.mammoth.domain.mcp.McpServerMain;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mammoth Domain Service.
 *
 * <p>Architecture baseline (ADR-0001/0002/0003): the domain service owns authoritative business
 * state; the web UI talks to it over REST, and DSH agents reach it through a separate MCP stdio
 * entrypoint that reuses the same domain layer.
 *
 * <p>Launching with {@code --mcp} runs the read-only MCP stdio server for DSH instead of the HTTP
 * server (see {@link McpServerMain}).
 */
@SpringBootApplication
public class DomainApplication {

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && "--mcp".equals(args[0])) {
      McpServerMain.main(Arrays.copyOfRange(args, 1, args.length));
      return;
    }
    SpringApplication.run(DomainApplication.class, args);
  }
}
