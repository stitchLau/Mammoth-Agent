package com.mammoth.domain.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background scheduling is writer-only (ADR-0008/0012): the MCP read-only entrypoint must not run
 * notification, audit or import jobs. Setting {@code mammoth.data-root.read-only=true} (which the
 * MCP entrypoint forces) disables this configuration.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "mammoth.data-root.read-only", havingValue = "false",
    matchIfMissing = true)
public class SchedulingConfig {
}
