package com.mammoth.domain.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mammoth.domain.config.DataRootBootstrap;
import com.mammoth.domain.config.DataRootResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.sqlite.SQLiteDataSource;

/**
 * Standalone MockMvc keeps the skeleton test independent of Spring Boot's test-autoconfigure module
 * layout (which changed between Spring Boot 3 and 4).
 */
class HealthControllerTest {

  @TempDir
  Path tempDir;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws Exception {
    Path root = Files.createDirectories(tempDir.resolve("data"));
    DataRootResolver resolver = new DataRootResolver(root.toString());
    DataRootBootstrap bootstrap = new DataRootBootstrap(resolver);
    bootstrap.initialize();
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + bootstrap.databaseFile().toAbsolutePath());
    HealthController controller = new HealthController(resolver, bootstrap, dataSource);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void healthReturnsUp() throws Exception {
    mockMvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("up"))
        .andExpect(jsonPath("$.service").value("mammoth-domain-service"));
  }

  @Test
  void metaReportsDataRootAndDatabase() throws Exception {
    mockMvc.perform(get("/api/meta"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dataRoot").isNotEmpty())
        .andExpect(jsonPath("$.database").isNotEmpty())
        .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"));
  }
}
