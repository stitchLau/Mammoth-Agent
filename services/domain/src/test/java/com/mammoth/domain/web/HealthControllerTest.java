package com.mammoth.domain.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mammoth.domain.config.DataRootResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Standalone MockMvc keeps the skeleton test independent of Spring Boot's test-autoconfigure module
 * layout (which changed between Spring Boot 3 and 4).
 */
class HealthControllerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    HealthController controller = new HealthController(new DataRootResolver(""));
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
  void metaReportsDataRoot() throws Exception {
    mockMvc.perform(get("/api/meta"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dataRoot").isNotEmpty())
        .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"));
  }
}
