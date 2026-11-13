package com.mammoth.domain.notification;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AppUserRepository;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.support.JsonLite;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Writer-only notification outbox worker (ADR-0013). SQLite is single-writer, so a simple claim is
 * sufficient; M5 replaces it with a multi-instance lease/SKIP LOCKED strategy.
 */
@Service
public class NotificationJobWorker {

  private static final Logger log = LoggerFactory.getLogger(NotificationJobWorker.class);
  private static final int MAX_ATTEMPTS = 5;

  private final ServerJobRepository jobRepository;
  private final NotificationDeliveryRepository deliveryRepository;
  private final NotificationRepository notificationRepository;
  private final AppUserRepository appUserRepository;
  private final List<NotificationChannel> channels;
  private final NotificationService notificationService;
  private final AuditService auditService;
  private final TransactionTemplate transactionTemplate;
  private final ObjectMapper mapper = new ObjectMapper();

  public NotificationJobWorker(ServerJobRepository jobRepository,
      NotificationDeliveryRepository deliveryRepository, NotificationRepository notificationRepository,
      AppUserRepository appUserRepository, List<NotificationChannel> channels,
      NotificationService notificationService, AuditService auditService,
      TransactionTemplate transactionTemplate) {
    this.jobRepository = jobRepository;
    this.deliveryRepository = deliveryRepository;
    this.notificationRepository = notificationRepository;
    this.appUserRepository = appUserRepository;
    this.channels = channels;
    this.notificationService = notificationService;
    this.auditService = auditService;
    this.transactionTemplate = transactionTemplate;
  }

  @Scheduled(fixedDelayString = "${mammoth.jobs.poll-interval-ms:5000}",
      initialDelayString = "${mammoth.jobs.initial-delay-ms:10000}")
  public void poll() {
    try {
      for (int index = 0; index < 10; index++) {
        ServerJob job = claim();
        if (job == null) {
          return;
        }
        process(job);
      }
    } catch (RuntimeException error) {
      log.warn("notification job poll failed: {}", error.getMessage());
    }
  }

  private ServerJob claim() {
    return transactionTemplate.execute(status -> {
      String now = Ids.nowIso();
      ServerJob job = jobRepository
          .findFirstByStatusAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
              ServerJob.STATUS_QUEUED, now)
          .orElse(null);
      if (job == null) {
        return null;
      }
      job.markRunning("writer", now);
      jobRepository.save(job);
      auditService.record(job.getCreatedBy(), "job.running", "server_job", job.getId(), null);
      return job;
    });
  }

  private void process(ServerJob job) {
    NotificationDelivery delivery;
    Notification notification;
    AppUser recipient;
    try {
      delivery = transactionTemplate.execute(status -> deliveryRepository.findById(
          deliveryId(job.getPayloadJson())).orElse(null));
      if (delivery == null) {
        finish(job, null, true, null);
        return;
      }
      notification = transactionTemplate.execute(status -> notificationRepository.findById(
          delivery.getNotificationId()).orElse(null));
      if (notification == null) {
        finish(job, delivery, true, null);
        return;
      }
      recipient = transactionTemplate.execute(status -> appUserRepository.findById(
          notification.getRecipientUserId()).filter(AppUser::isActive).orElse(null));
      NotificationChannel channel = channels.stream()
          .filter(entry -> entry.channel().equals(delivery.getChannel())).findFirst().orElse(null);
      if (channel == null || !channel.enabled() || !channel.configured()) {
        finish(job, delivery, false, "channel disabled or not configured");
        return;
      }
      if (recipient == null) {
        finish(job, delivery, false, "recipient inactive or missing");
        return;
      }
      channel.send(notification, recipient);
      finish(job, delivery, true, null);
    } catch (NotificationSkipException skip) {
      finishOnError(job, skip.getMessage(), true);
    } catch (Exception error) {
      finishOnError(job, error.getClass().getSimpleName() + ": " + error.getMessage(), false);
    }
  }

  private void finishOnError(ServerJob job, String error, boolean skip) {
    boolean[] dead = {false};
    transactionTemplate.executeWithoutResult(status -> {
      NotificationDelivery delivery = deliveryRepository.findById(
          deliveryId(job.getPayloadJson())).orElse(null);
      String now = Ids.nowIso();
      if (delivery != null) {
        if (skip) {
          delivery.markSkipped(error, now);
        } else {
          delivery.markFailed(error, null, now);
        }
        deliveryRepository.save(delivery);
      }
      ServerJob current = jobRepository.findById(job.getId()).orElse(job);
      if (skip) {
        current.markSucceeded(now);
        jobRepository.save(current);
      } else if (current.getAttempts() + 1 >= MAX_ATTEMPTS) {
        current.markDead(error, now);
        jobRepository.save(current);
        dead[0] = true;
        auditService.record(current.getCreatedBy(), "job.dead", "server_job", current.getId(),
            JsonLite.write(JsonLite.object("error", error)));
      } else {
        current.markRetry(error, backoff(current.getAttempts() + 1), now);
        jobRepository.save(current);
        auditService.record(current.getCreatedBy(), "job.retry", "server_job", current.getId(),
            JsonLite.write(JsonLite.object("attempt", current.getAttempts())));
      }
    });
    if (dead[0]) {
      try {
        notificationService.jobFailed(job.getId(), job.getType(), error);
      } catch (RuntimeException notificationError) {
        log.warn("failed to notify org admin about dead job {}: {}", job.getId(),
            notificationError.getMessage());
      }
    }
    if (!skip) {
      log.warn("notification delivery failed for job {}: {}", job.getId(), error);
    }
  }

  private void finish(ServerJob job, NotificationDelivery delivery, boolean success, String note) {
    transactionTemplate.executeWithoutResult(status -> {
      String now = Ids.nowIso();
      ServerJob current = jobRepository.findById(job.getId()).orElse(job);
      current.markSucceeded(now);
      jobRepository.save(current);
      if (delivery != null) {
        if (success) {
          delivery.markSent(now);
        } else {
          delivery.markSkipped(note == null ? "skipped" : note, now);
        }
        deliveryRepository.save(delivery);
      }
      auditService.record(current.getCreatedBy(), "job.succeeded", "server_job", current.getId(),
          null);
    });
  }

  private static String deliveryId(String payloadJson) {
    if (payloadJson == null || payloadJson.isBlank()) {
      return "";
    }
    try {
      return new ObjectMapper().readTree(payloadJson).path("deliveryId").asText("");
    } catch (Exception error) {
      return "";
    }
  }

  private static String backoff(int attempts) {
    long seconds = Math.min(3600L, 30L * (1L << Math.min(attempts, 6)));
    return Instant.now().plusSeconds(seconds).toString();
  }

  public Map<String, Object> channelStatus() {
    return channels.stream().collect(Collectors.toMap(NotificationChannel::channel, channel ->
        Map.of("enabled", channel.enabled(), "configured", channel.configured(),
            "missing", channel.missingConfiguration()), (left, right) -> left,
        java.util.LinkedHashMap::new));
  }
}
