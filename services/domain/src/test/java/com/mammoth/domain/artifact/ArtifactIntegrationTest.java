package com.mammoth.domain.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** T5 acceptance: artifact upload, dedupe, verified content and project-scoped authorization. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ArtifactIntegrationTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-t5-it");
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
  private AuthorizationService authorizationService;

  @Autowired
  private ArtifactService artifactService;

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private Project projectFor(AppUser owner) {
    Customer customer = catalogService.createCustomer(owner, unique("ACME"));
    return projectService.create(owner, customer.getId(), unique("PRJ"), "artifact project");
  }

  @Test
  void uploadsArtifactAndDeduplicatesWithinProject() {
    AppUser owner = userService.create(unique("owner"), "Owner", "Passw0rd!2026");
    Project project = projectFor(owner);
    byte[] content = "需求纪要：收货流程异常处理。".getBytes(StandardCharsets.UTF_8);

    ArtifactRecord first = artifactService.upload(
        owner, project.getId(), "source", "notes.md", "text/markdown", "第一次上传", content);
    ArtifactRecord second = artifactService.upload(
        owner, project.getId(), "source", "notes-copy.md", "text/markdown", null, content.clone());

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(first.getContentHash()).hasSize(64);
    assertThat(first.getStoragePath()).startsWith("artifacts/");
    assertThat(artifactService.list(project.getId())).hasSize(1);
    assertThat(artifactService.content(first)).isEqualTo(content);
  }

  @Test
  void rejectsEmptyContentAndForeignArtifactLookup() {
    AppUser owner = userService.create(unique("owner2"), "Owner2", "Passw0rd!2026");
    Project project = projectFor(owner);
    Project other = projectFor(owner);

    assertThatThrownBy(() -> artifactService.upload(
        owner, project.getId(), "source", "empty.txt", "text/plain", null, new byte[0]))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("must not be empty");

    ArtifactRecord record = artifactService.upload(
        owner, project.getId(), "source", "a.txt", "text/plain", null,
        "content-a".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> artifactService.require(other.getId(), record.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("artifact not found");
  }

  @Test
  void enforcesProjectMembershipForWriteAndRead() {
    AppUser owner = userService.create(unique("owner3"), "Owner3", "Passw0rd!2026");
    AppUser outsider = userService.create(unique("outsider3"), "Outsider", "Passw0rd!2026");
    Project project = projectFor(owner);

    assertThatThrownBy(() -> authorizationService.requireProjectRole(
        outsider, project.getId(), ArtifactService.WRITE_ROLES))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no access to project");

    projectService.addMember(owner, project.getId(), outsider.getId(), AuthorizationService.REVIEWER);

    assertThatThrownBy(() -> authorizationService.requireProjectRole(
        outsider, project.getId(), ArtifactService.WRITE_ROLES))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot perform this action");
  }
}
