package com.mammoth.domain.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR-0008 M4 addendum: the read-only MCP process appends JSONL audit events, never DB rows. */
class AgentAuditJournalTest {

  @TempDir
  Path tempDir;

  @Test
  void appendsJsonLineWithoutArgumentsOrBodies() throws Exception {
    AgentAuditJournal journal = new AgentAuditJournal(tempDir);
    String eventId = journal.record("agent-1", "search_project_knowledge", "project-1", 2, 12,
        null);

    assertThat(eventId).isNotBlank();
    assertThat(journal.file()).exists();
    String line = Files.readString(journal.file(), StandardCharsets.UTF_8);
    assertThat(line).contains("\"eventId\":\"" + eventId + "\"");
    assertThat(line).contains("\"tool\":\"search_project_knowledge\"");
    assertThat(line).contains("\"projectId\":\"project-1\"");
    assertThat(line).contains("\"resultCount\":2");
    assertThat(line).doesNotContain("query", "body", "password");
  }

  @Test
  void missingAuditDirectoryIsBestEffortAndDoesNotThrow() {
    AgentAuditJournal journal = new AgentAuditJournal(tempDir.resolve("missing"));
    assertThat(journal.record("agent-1", "get_knowledge_document", "p", 1, 1, null)).isNull();
  }
}
