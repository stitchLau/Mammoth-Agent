package com.mammoth.domain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mammoth Domain Service.
 *
 * <p>Architecture baseline (ADR-0001/0002/0003): the domain service owns authoritative business
 * state; the web UI talks to it over REST, and DSH agents reach it through a separate MCP stdio
 * entrypoint that reuses the same domain layer (wired in a later M1 task).
 */
@SpringBootApplication
public class DomainApplication {

  public static void main(String[] args) {
    SpringApplication.run(DomainApplication.class, args);
  }
}
