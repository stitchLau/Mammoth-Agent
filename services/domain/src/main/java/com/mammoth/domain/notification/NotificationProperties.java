package com.mammoth.domain.notification;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Notification channel settings (ADR-0013). In-app is on by default; email/WeCom are off and must be
 * explicitly enabled with complete configuration. Secrets are read from env/OS and never logged.
 */
@Component
@ConfigurationProperties(prefix = "mammoth.notifications")
public class NotificationProperties {

  private final InApp inApp = new InApp();
  private final Email email = new Email();
  private final Wecom wecom = new Wecom();
  private final Environment environment;

  public NotificationProperties(Environment environment) {
    this.environment = environment;
  }

  @PostConstruct
  public void validate() {
    if (email.isEnabled()) {
      String host = environment.getProperty("spring.mail.host", "");
      if (host.isBlank()) {
        throw new IllegalStateException(
            "mammoth.notifications.email.enabled=true requires spring.mail.host");
      }
      if (email.getFrom() == null || email.getFrom().isBlank()) {
        throw new IllegalStateException(
            "mammoth.notifications.email.enabled=true requires mammoth.notifications.email.from");
      }
    }
    if (wecom.isEnabled() && (wecom.getWebhookUrl() == null || wecom.getWebhookUrl().isBlank())) {
      throw new IllegalStateException(
          "mammoth.notifications.wecom.enabled=true requires"
              + " mammoth.notifications.wecom.webhook-url");
    }
  }

  public InApp getInApp() {
    return inApp;
  }

  public Email getEmail() {
    return email;
  }

  public Wecom getWecom() {
    return wecom;
  }

  public static class InApp {
    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  public static class Email {
    private boolean enabled;
    private String from;
    private String testRecipient;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getFrom() {
      return from;
    }

    public void setFrom(String from) {
      this.from = from;
    }

    public String getTestRecipient() {
      return testRecipient;
    }

    public void setTestRecipient(String testRecipient) {
      this.testRecipient = testRecipient;
    }
  }

  public static class Wecom {
    private boolean enabled;
    private String webhookUrl;
    private String msgType = "markdown";
    private String events = "solution.submitted,job.failed";
    private int rateLimitPerMinute = 20;
    private String testWebhookUrl;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getWebhookUrl() {
      return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
      this.webhookUrl = webhookUrl;
    }

    public String getMsgType() {
      return msgType;
    }

    public void setMsgType(String msgType) {
      this.msgType = msgType;
    }

    public String getEvents() {
      return events;
    }

    public void setEvents(String events) {
      this.events = events;
    }

    public int getRateLimitPerMinute() {
      return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
      this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public String getTestWebhookUrl() {
      return testWebhookUrl;
    }

    public void setTestWebhookUrl(String testWebhookUrl) {
      this.testWebhookUrl = testWebhookUrl;
    }
  }
}
