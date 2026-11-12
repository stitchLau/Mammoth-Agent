package com.mammoth.domain.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.artifact.ArtifactRecord;
import com.mammoth.domain.artifact.ArtifactService;
import com.mammoth.domain.knowledge.KnowledgeDocument;
import com.mammoth.domain.knowledge.KnowledgeService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.CatalogService;
import com.mammoth.domain.service.ProjectService;
import com.mammoth.domain.service.UserService;
import com.mammoth.domain.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** T9 acceptance: MCP tool layer enforces agent identity, project scope and verified-only access. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class McpToolServiceTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-t9-it");
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("mammoth.data-root", DATA_ROOT::toString);
  }

  @Autowired
  private UserService userService;

  @Autowired
  private CatalogService catalogService;

  @Autowired
  private ProjectService projectService;

  @Autowired
  private ArtifactService artifactService;

  @Autowired
  private KnowledgeService knowledgeService;

  @Autowired
  private McpToolService mcpToolService;

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private record Fixture(AppUser owner, AppUser reviewer, AppUser outsider, Project project,
      KnowledgeDocument verified, KnowledgeDocument draft) {
  }

  private Fixture fixture() {
    AppUser owner = userService.create(unique("owner"), "Owner", "Passw0rd!2026");
    AppUser reviewer = userService.create(unique("reviewer"), "Reviewer", "Passw0rd!2026");
    AppUser outsider = userService.create(unique("outsider"), "Outsider", "Passw0rd!2026");
    Customer customer = catalogService.createCustomer(owner, unique("ACME"));
    Project project = projectService.create(owner, customer.getId(), unique("PRJ"), "mcp project");
    projectService.addMember(owner, project.getId(), reviewer.getId(), AuthorizationService.REVIEWER);

    ArtifactRecord artifact = artifactService.upload(owner, project.getId(), "source",
        unique("notes") + ".md", "text/markdown", null,
        "收货流程：条码重复时冻结库存并人工复核。".getBytes(StandardCharsets.UTF_8));

    KnowledgeDocument document = knowledgeService.create(owner, project.getId(), "process",
        "收货流程异常处理", "收货流程中条码重复时，先冻结库存再人工复核，异常需当天闭环。",
        List.of(new KnowledgeService.SourceRefInput(artifact.getId(), "§2", "原始纪要")));
    knowledgeService.submitForReview(owner, project.getId(), document.getId());
    knowledgeService.verify(reviewer, project.getId(), document.getId());

    KnowledgeDocument draft = knowledgeService.create(owner, project.getId(), "process",
        "草稿流程", "草稿：条码重复处理方式待确认。",
        List.of(new KnowledgeService.SourceRefInput(artifact.getId(), null, null)));
    return new Fixture(owner, reviewer, outsider, project, document, draft);
  }

  @Test
  void agentSearchesVerifiedKnowledgeWithCitations() {
    Fixture fixture = fixture();

    var hits = mcpToolService.searchProjectKnowledge(
        fixture.owner().getId(), fixture.project().getId(), "条码重复怎么处理", 5);

    assertThat(hits).isNotEmpty();
    assertThat(hits.get(0).documentId()).isEqualTo(fixture.verified().getId());
    assertThat(hits.get(0).title()).isEqualTo("收货流程异常处理");
    assertThat(hits.get(0).chunkId()).isNotBlank();
    assertThat(hits.get(0).sourceRefCount()).isPositive();
  }

  @Test
  void agentReadsVerifiedDocumentWithSourceRefs() {
    Fixture fixture = fixture();

    var document = mcpToolService.getKnowledgeDocument(
        fixture.reviewer().getId(), fixture.project().getId(), fixture.verified().getId());

    assertThat(document.status()).isEqualTo(KnowledgeDocument.VERIFIED);
    assertThat(document.sourceRefs()).hasSize(1);
    assertThat(document.sourceRefs().get(0).artifactId()).isNotBlank();
  }

  @Test
  void draftsOutsideMembershipAndUnknownAgentsAreRejected() {
    Fixture fixture = fixture();

    assertThatThrownBy(() -> mcpToolService.getKnowledgeDocument(
        fixture.owner().getId(), fixture.project().getId(), fixture.draft().getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("only verified knowledge");

    assertThatThrownBy(() -> mcpToolService.searchProjectKnowledge(
        fixture.outsider().getId(), fixture.project().getId(), "条码重复", 5))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no access to project");

    assertThatThrownBy(() -> mcpToolService.searchProjectKnowledge(
        "missing-user", fixture.project().getId(), "条码重复", 5))
        .isInstanceOf(ApiException.class);

    assertThatThrownBy(() -> mcpToolService.searchProjectKnowledge(
        fixture.owner().getId(), fixture.project().getId(), "   ", 5))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("query must not be empty");
  }

  @Test
  void trigramQueryBuilderHandlesShortAndNormalQueries() {
    assertThat(com.mammoth.domain.knowledge.SqliteKnowledgeSearchAdapter.trigramQuery("条码重复"))
        .isEqualTo("\"条码重\" OR \"码重复\"");
    assertThat(com.mammoth.domain.knowledge.SqliteKnowledgeSearchAdapter.trigramQuery("收货"))
        .isEmpty();
  }
}
