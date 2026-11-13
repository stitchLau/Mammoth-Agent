package com.mammoth.domain.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.mammoth.domain.config.DataRootBootstrap;
import com.mammoth.domain.persistence.AuditLogRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ADR-0008 M4 addendum: writer ingests MCP JSONL into agent_call + audit_log idempotently. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AgentAuditIngestTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-agent-audit-it");
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("mammoth.data-root", DATA_ROOT::toString);
    // Keep the scheduled poller out of the test window; the test calls ingestNow() explicitly.
    registry.add("mammoth.agent-audit.initial-delay-ms", () -> "600000");
    registry.add("mammoth.agent-audit.ingest-interval-ms", () -> "600000");
  }

  @Autowired
  private DataRootBootstrap bootstrap;

  @Autowired
  private AgentAuditIngestService ingestService;

  @Autowired
  private AgentCallRepository agentCallRepository;

  @Autowired
  private AuditLogRepository auditLogRepository;

  @Test
  void ingestsJournalIdempotentlyAndSkipsMalformedLines() throws Exception {
    AgentAuditJournal journal = new AgentAuditJournal(bootstrap.root().resolve("audit"));
    String first = journal.record("agent-1", "search_project_knowledge", "project-1", 2, 12, null);
    String second = journal.record("agent-1", "get_knowledge_document", "project-1", 1, 3, "403");
    assertThat(first).isNotBlank();
    assertThat(second).isNotBlank();
    Files.writeString(journal.file(), "not-json\n", StandardCharsets.UTF_8,
        StandardOpenOption.APPEND);

    assertThat(ingestService.ingestNow()).isEqualTo(2);
    assertThat(agentCallRepository.existsById(first)).isTrue();
    assertThat(agentCallRepository.findById(second).orElseThrow().getError()).isEqualTo("403");
    assertThat(auditLogRepository.findByActorUserIdOrderByAtDesc("agent-1"))
        .extracting(log -> log.getAction())
        .contains("agent.tool_call");

    assertThat(ingestService.ingestNow()).isZero();
    assertThat(agentCallRepository.count()).isEqualTo(2);
  }
}
