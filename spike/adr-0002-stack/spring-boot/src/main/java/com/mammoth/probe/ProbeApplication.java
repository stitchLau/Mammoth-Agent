package com.mammoth.probe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** ADR-0002 spike probe — Spring Boot REST entrypoint (disposable). */
@SpringBootApplication
public class ProbeApplication {

  public static void main(String[] args) {
    SpringApplication.run(ProbeApplication.class, args);
  }
}
