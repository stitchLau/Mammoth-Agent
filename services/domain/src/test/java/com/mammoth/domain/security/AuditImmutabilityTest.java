package com.mammoth.domain.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** T7 acceptance: audit_log is append-only at the database level (V3 triggers). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuditImmutabilityTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-audit-it");
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("mammoth.data-root", DATA_ROOT::toString);
  }

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private com.mammoth.domain.service.UserService userService;

  private void createAuditRow() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    userService.create("audit-" + suffix, "Audit User", "Passw0rd!2026");
  }

  @Test
  void auditRowsCannotBeUpdatedOrDeleted() {
    createAuditRow();
    Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log", Integer.class);
    assertThat(count).isPositive();

    assertThatThrownBy(() -> jdbcTemplate.update("UPDATE audit_log SET action='tampered'"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");

    assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_log"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");

    Integer after = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log", Integer.class);
    assertThat(after).isEqualTo(count);
  }
}
