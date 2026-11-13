package com.mammoth.domain.web;

import com.mammoth.domain.notification.EmailNotificationChannel;
import com.mammoth.domain.notification.Notification;
import com.mammoth.domain.notification.NotificationChannel;
import com.mammoth.domain.notification.NotificationJobWorker;
import com.mammoth.domain.notification.NotificationProperties;
import com.mammoth.domain.notification.WecomNotificationChannel;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.support.Ids;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/** Org-admin channel status/test-send (ADR-0013); secrets are never returned. */
@RestController
@RequestMapping("/api/admin/notifications")
public class NotificationAdminController {

  private final NotificationJobWorker worker;
  private final NotificationProperties properties;
  private final List<NotificationChannel> channels;
  private final AuthorizationService authorizationService;
  private final AuditService auditService;

  public NotificationAdminController(NotificationJobWorker worker,
      NotificationProperties properties, List<NotificationChannel> channels,
      AuthorizationService authorizationService, AuditService auditService) {
    this.worker = worker;
    this.properties = properties;
    this.channels = channels;
    this.authorizationService = authorizationService;
    this.auditService = auditService;
  }

  @GetMapping("/channels")
  public Map<String, Object> channels() {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireOrgAdmin(actor);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("inApp", Map.of("enabled", properties.getInApp().isEnabled()));
    result.putAll(worker.channelStatus());
    return result;
  }

  @PostMapping("/channels/{channel}/test")
  @Transactional
  public Map<String, Object> test(@PathVariable String channel) throws Exception {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireOrgAdmin(actor);
    NotificationChannel target = channels.stream().filter(entry -> entry.channel().equals(channel))
        .findFirst().orElseThrow(() -> ApiException.notFound("unknown channel: " + channel));
    if (!target.enabled() || !target.configured()) {
      throw ApiException.conflict("channel is disabled or not configured: " + channel
          + " (" + target.missingConfiguration() + ")");
    }
    String now = Ids.nowIso();
    Notification notification = new Notification(Ids.newId(), actor.getOrgId(), actor.getId(),
        "notification.test", "Mammoth 通知测试", "这是一条来自 Mammoth 的测试通知。", null,
        "app_user", actor.getId(), "/settings", Notification.SEVERITY_INFO,
        "test:" + now + ":" + actor.getId(), now);
    if (target instanceof EmailNotificationChannel emailChannel) {
      String recipient = properties.getEmail().getTestRecipient();
      if (recipient == null || recipient.isBlank()) {
        recipient = actor.getEmail();
      }
      if (recipient == null || recipient.isBlank()) {
        throw ApiException.badRequest("set a test recipient or your account email first");
      }
      emailChannel.sendTo(recipient, notification);
    } else if (target instanceof WecomNotificationChannel wecomChannel) {
      String url = properties.getWecom().getTestWebhookUrl();
      if (url == null || url.isBlank()) {
        url = properties.getWecom().getWebhookUrl();
      }
      wecomChannel.sendTo(notification, url, true);
    } else {
      throw ApiException.badRequest("channel does not support test send: " + channel);
    }
    auditService.record(actor.getId(), "notification.test_send", "app_user", actor.getId(),
        "{\"channel\":\"" + channel + "\"}");
    return Map.of("status", "sent", "channel", channel);
  }
}
