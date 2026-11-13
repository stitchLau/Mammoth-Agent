package com.mammoth.domain.web;

import com.mammoth.domain.notification.Notification;
import com.mammoth.domain.notification.NotificationRepository;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.support.Ids;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/** Per-user in-app notifications (ADR-0013); a user can only see/read their own rows. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

  private final NotificationRepository notificationRepository;
  private final AuthorizationService authorizationService;
  private final AuditService auditService;

  public NotificationController(NotificationRepository notificationRepository,
      AuthorizationService authorizationService, AuditService auditService) {
    this.notificationRepository = notificationRepository;
    this.authorizationService = authorizationService;
    this.auditService = auditService;
  }

  public record NotificationResponse(String id, String type, String title, String body,
      String projectId, String objectType, String objectId, String link, String severity,
      String status, String createdAt, String readAt) {

    static NotificationResponse from(Notification notification) {
      return new NotificationResponse(notification.getId(), notification.getType(),
          notification.getTitle(), notification.getBody(), notification.getProjectId(),
          notification.getObjectType(), notification.getObjectId(), notification.getLink(),
          notification.getSeverity(), notification.getStatus(), notification.getCreatedAt(),
          notification.getReadAt());
    }
  }

  @GetMapping
  public List<NotificationResponse> list(
      @RequestParam(value = "limit", defaultValue = "100") int limit) {
    AppUser actor = CurrentUser.require(authorizationService);
    int capped = Math.max(1, Math.min(limit, 200));
    return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(actor.getId())
        .stream().limit(capped).map(NotificationResponse::from).toList();
  }

  @GetMapping("/unread-count")
  public java.util.Map<String, Object> unreadCount() {
    AppUser actor = CurrentUser.require(authorizationService);
    return java.util.Map.of("unread",
        notificationRepository.countByRecipientUserIdAndStatus(actor.getId(),
            Notification.STATUS_UNREAD));
  }

  @PostMapping("/{notificationId}/read")
  @Transactional
  public NotificationResponse read(@PathVariable String notificationId) {
    AppUser actor = CurrentUser.require(authorizationService);
    Notification notification = notificationRepository
        .findByIdAndRecipientUserId(notificationId, actor.getId())
        .orElseThrow(() -> com.mammoth.domain.web.ApiException.notFound("notification not found"));
    if (Notification.STATUS_UNREAD.equals(notification.getStatus())) {
      notification.markRead(Ids.nowIso());
      notificationRepository.save(notification);
      auditService.record(actor.getId(), "notification.read", "notification", notification.getId(),
          null);
    }
    return NotificationResponse.from(notification);
  }

  @PostMapping("/read-all")
  @Transactional
  public java.util.Map<String, Object> readAll() {
    AppUser actor = CurrentUser.require(authorizationService);
    int changed = 0;
    for (Notification notification : notificationRepository
        .findByRecipientUserIdAndStatusOrderByCreatedAtDesc(actor.getId(),
            Notification.STATUS_UNREAD)) {
      notification.markRead(Ids.nowIso());
      notificationRepository.save(notification);
      changed++;
    }
    if (changed > 0) {
      auditService.record(actor.getId(), "notification.read_all", "app_user", actor.getId(), null);
    }
    return java.util.Map.of("changed", changed);
  }
}
