package com.mammoth.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.service.CatalogService;
import com.mammoth.domain.service.ProjectService;
import com.mammoth.domain.service.UserService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ADR-0013: in-app default, recipient resolution, de-dup and external channels off by default. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationWorkflowTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-notification-it");
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("mammoth.data-root", DATA_ROOT::toString);
    registry.add("mammoth.jobs.initial-delay-ms", () -> "600000");
    registry.add("mammoth.jobs.poll-interval-ms", () -> "600000");
  }

  @Autowired
  private UserService userService;

  @Autowired
  private CatalogService catalogService;

  @Autowired
  private ProjectService projectService;

  @Autowired
  private NotificationService notificationService;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private NotificationDeliveryRepository deliveryRepository;

  @Autowired
  private ServerJobRepository jobRepository;

  private AppUser owner;
  private AppUser reviewer;
  private Project project;

  @BeforeAll
  void bootstrap() {
    owner = userService.create("notify-owner", "Notify Owner", "Passw0rd!2026");
    reviewer = userService.create("notify-reviewer", "Notify Reviewer", "Passw0rd!2026");
    Customer customer = catalogService.createCustomer(owner, "NOTIFY-ACME");
    project = projectService.create(owner, customer.getId(), "NOTIFY-1", "Notify project");
    projectService.addMember(owner, project.getId(), reviewer.getId(),
        com.mammoth.domain.service.AuthorizationService.REVIEWER);
  }

  @Test
  void solutionSubmittedNotifiesReviewerOnlyAndIsIdempotent() {
    int first = notificationService.solutionSubmitted(owner, project.getId(), "work-1",
        "version-1", 1, "方案");
    assertThat(first).isEqualTo(1);
    assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(reviewer.getId()))
        .anyMatch(notification -> "solution.submitted".equals(notification.getType())
            && notification.getStatus().equals(Notification.STATUS_UNREAD));

    int duplicate = notificationService.solutionSubmitted(owner, project.getId(), "work-1",
        "version-1", 1, "方案");
    assertThat(duplicate).isZero();
    assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(owner.getId()))
        .noneMatch(notification -> "solution.submitted".equals(notification.getType()));
  }

  @Test
  void defaultChannelsCreateOnlyInAppDeliveriesAndNoJobs() {
    notificationService.solutionSubmitted(owner, project.getId(), "work-2", "version-2", 1,
        "方案二");
    assertThat(deliveryRepository.findAll())
        .allMatch(delivery -> NotificationDelivery.CHANNEL_IN_APP.equals(delivery.getChannel()));
    assertThat(jobRepository.count()).isZero();
  }

  @Test
  void jobFailureNotifiesOrgAdmin() {
    int created = notificationService.jobFailed("job-1", "notification.dispatch", "boom");
    assertThat(created).isPositive();
    assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(owner.getId()))
        .anyMatch(notification -> "job.failed".equals(notification.getType()));
  }
}
