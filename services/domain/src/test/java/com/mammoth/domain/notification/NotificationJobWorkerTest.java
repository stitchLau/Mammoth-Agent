package com.mammoth.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.UserService;
import com.mammoth.domain.support.Ids;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Worker retry/dead-letter path with a deliberately failing channel; no network. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(NotificationJobWorkerTest.ChannelConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationJobWorkerTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-notification-worker");
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

  @TestConfiguration
  static class ChannelConfig {
    @Bean
    NotificationChannel failingChannel() {
      return new NotificationChannel() {
        @Override
        public String channel() {
          return "test";
        }

        @Override
        public boolean enabled() {
          return true;
        }

        @Override
        public boolean configured() {
          return true;
        }

        @Override
        public String missingConfiguration() {
          return "";
        }

        @Override
        public void send(Notification notification, AppUser recipient) {
          throw new IllegalStateException("boom");
        }
      };
    }
  }

  @Autowired
  private UserService userService;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private NotificationDeliveryRepository deliveryRepository;

  @Autowired
  private ServerJobRepository jobRepository;

  @Autowired
  private NotificationJobWorker worker;

  private AppUser actor;

  @BeforeAll
  void bootstrap() {
    actor = userService.create("worker-admin", "Worker Admin", "Passw0rd!2026");
  }

  @Test
  void failedDeliveryRetriesThenBecomesDead() {
    String now = Ids.nowIso();
    Notification notification = new Notification(Ids.newId(), actor.getOrgId(), actor.getId(),
        "solution.submitted", "测试", "测试", null, "x", "1", "/", Notification.SEVERITY_INFO,
        "worker:1", now);
    notificationRepository.save(notification);
    NotificationDelivery delivery = new NotificationDelivery(Ids.newId(), notification.getId(),
        "test", NotificationDelivery.STATUS_PENDING, now, now, now);
    deliveryRepository.save(delivery);
    ServerJob job = new ServerJob(Ids.newId(), ServerJob.TYPE_NOTIFICATION_DISPATCH,
        "{\"deliveryId\":\"" + delivery.getId() + "\"}", "delivery:" + delivery.getId(),
        actor.getId(), now);
    jobRepository.save(job);

    worker.poll();
    ServerJob afterFirst = jobRepository.findById(job.getId()).orElseThrow();
    assertThat(afterFirst.getStatus()).isEqualTo(ServerJob.STATUS_QUEUED);
    assertThat(afterFirst.getAttempts()).isEqualTo(1);
    assertThat(deliveryRepository.findById(delivery.getId()).orElseThrow().getStatus())
        .isEqualTo(NotificationDelivery.STATUS_FAILED);

    for (int attempt = 0; attempt < 4; attempt++) {
      ServerJob current = jobRepository.findById(job.getId()).orElseThrow();
      current.requeue(Ids.nowIso());
      jobRepository.save(current);
      worker.poll();
    }
    ServerJob dead = jobRepository.findById(job.getId()).orElseThrow();
    assertThat(dead.getStatus()).isEqualTo(ServerJob.STATUS_DEAD);
    assertThat(dead.getAttempts()).isEqualTo(5);
  }
}
