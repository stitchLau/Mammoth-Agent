package com.mammoth.domain.notification;

import com.mammoth.domain.persistence.AppUser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** WeCom group-robot webhook implementation, disabled unless explicitly configured (ADR-0013). */
@Component
public class WecomNotificationChannel implements NotificationChannel {

  private final NotificationProperties properties;
  private final HttpClient client;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Deque<Instant> sends = new ArrayDeque<>();

  public WecomNotificationChannel(NotificationProperties properties) {
    this.properties = properties;
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  @Override
  public String channel() {
    return NotificationDelivery.CHANNEL_WECOM;
  }

  @Override
  public boolean enabled() {
    return properties.getWecom().isEnabled();
  }

  @Override
  public boolean configured() {
    return properties.getWecom().getWebhookUrl() != null
        && !properties.getWecom().getWebhookUrl().isBlank();
  }

  @Override
  public String missingConfiguration() {
    return configured() ? "" : "mammoth.notifications.wecom.webhook-url";
  }

  @Override
  public void send(Notification notification, AppUser recipient) throws Exception {
    sendTo(notification, properties.getWecom().getWebhookUrl());
  }

  /** Test-send path: an optional test group webhook overrides the main webhook. */
  public void sendTo(Notification notification, String webhookUrl) throws Exception {
    sendTo(notification, webhookUrl, false);
  }

  /** Admin test-send bypasses the production event allowlist but still rate-limits. */
  public void sendTo(Notification notification, String webhookUrl, boolean bypassEventFilter)
      throws Exception {
    if (webhookUrl == null || webhookUrl.isBlank()) {
      throw new NotificationSkipException("WeCom webhook is not configured");
    }
    if (!bypassEventFilter && !eventsAllow(notification.getType())) {
      throw new NotificationSkipException("WeCom event is not enabled: " + notification.getType());
    }
    rateLimit();
    String type = properties.getWecom().getMsgType() == null ? "text"
        : properties.getWecom().getMsgType().trim().toLowerCase();
    String content = notification.getTitle()
        + (notification.getBody() == null || notification.getBody().isBlank() ? ""
            : "\n" + notification.getBody());
    content = content.length() <= 4000 ? content : content.substring(0, 4000);
    String payload = "markdown".equals(type)
        ? "{\"msgtype\":\"markdown\",\"markdown\":{\"content\":" + json(content) + "}}"
        : "{\"msgtype\":\"text\",\"text\":{\"content\":" + json(content) + "}}";
    HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
        .timeout(Duration.ofSeconds(8))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) {
      throw new IllegalStateException("WeCom HTTP " + response.statusCode());
    }
    JsonNode body = mapper.readTree(response.body());
    int errcode = body.path("errcode").asInt(0);
    if (errcode != 0) {
      throw new IllegalStateException("WeCom errcode " + errcode + ": "
          + body.path("errmsg").asText(""));
    }
  }

  private boolean eventsAllow(String type) {
    String configured = properties.getWecom().getEvents();
    if (configured == null || configured.isBlank()) {
      return false;
    }
    Set<String> allowed = Arrays.stream(configured.split(","))
        .map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toSet());
    return allowed.contains(type);
  }

  private void rateLimit() {
    int limit = Math.max(1, properties.getWecom().getRateLimitPerMinute());
    Instant cutoff = Instant.now().minusSeconds(60);
    synchronized (sends) {
      while (!sends.isEmpty() && sends.peekFirst().isBefore(cutoff)) {
        sends.removeFirst();
      }
      if (sends.size() >= limit) {
        throw new IllegalStateException("WeCom rate limit exceeded");
      }
      sends.addLast(Instant.now());
    }
  }

  private String json(String value) {
    return mapper.writeValueAsString(value);
  }
}
