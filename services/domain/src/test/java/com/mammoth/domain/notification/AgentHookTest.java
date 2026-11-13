package com.mammoth.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.service.CatalogService;
import com.mammoth.domain.service.ProjectService;
import com.mammoth.domain.service.UserService;
import com.mammoth.domain.web.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Agent hook contract: completed and pending-confirmation events go to project humans. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentHookTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-agent-hook");
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
  private AgentHookService agentHookService;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private ServerJobRepository jobRepository;

  private AppUser owner;
  private AppUser reviewer;
  private Project project;

  @BeforeAll
  void bootstrap() {
    owner = userService.create("hook-owner", "Hook Owner", "Passw0rd!2026");
    reviewer = userService.create("hook-reviewer", "Hook Reviewer", "Passw0rd!2026");
    Customer customer = catalogService.createCustomer(owner, "HOOK-ACME");
    project = projectService.create(owner, customer.getId(), "HOOK-1", "Hook project");
    projectService.addMember(owner, project.getId(), reviewer.getId(),
        com.mammoth.domain.service.AuthorizationService.REVIEWER);
  }

  @Test
  void completedAndConfirmationEventsNotifyHumansAndAreIdempotent() {
    int completed = agentHookService.emit(owner, project.getId(), AgentHookService.TASK_COMPLETED,
        "task-1", "扫描完成", "已生成结果", "/projects/" + project.getId() + "/requirements");
    assertThat(completed).isEqualTo(1);
    assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(reviewer.getId()))
        .anyMatch(notification -> "agent.task.completed".equals(notification.getType())
            && notification.getSeverity().equals(Notification.SEVERITY_INFO)
            && notification.getLink().startsWith("/projects/"));

    int duplicate = agentHookService.emit(owner, project.getId(), AgentHookService.TASK_COMPLETED,
        "task-1", "扫描完成", "已生成结果", "/projects/" + project.getId() + "/requirements");
    assertThat(duplicate).isZero();

    int confirmation = agentHookService.emit(owner, project.getId(),
        AgentHookService.CONFIRMATION_REQUIRED, "task-2", "需要确认", "请确认是否继续",
        null);
    assertThat(confirmation).isEqualTo(1);
    assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(reviewer.getId()))
        .anyMatch(notification -> "agent.task.confirmation_required".equals(notification.getType())
            && notification.getSeverity().equals(Notification.SEVERITY_WARNING));
    assertThat(jobRepository.count()).isZero();
  }

  @Test
  void unsupportedEventAndExternalLinkAreRejected() {
    assertThatThrownBy(() -> agentHookService.emit(owner, project.getId(), "agent.task.other",
        "task-3", "x", null, null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("unsupported");
    assertThatThrownBy(() -> agentHookService.emit(owner, project.getId(),
        AgentHookService.TASK_COMPLETED, "task-4", "x", null, "https://example.com"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("relative");
  }
}
