package com.mammoth.domain.notification;

import com.mammoth.domain.persistence.AppUser;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Full SMTP implementation, disabled unless explicitly configured (ADR-0013). */
@Component
public class EmailNotificationChannel implements NotificationChannel {

  private final NotificationProperties properties;
  private final JavaMailSender mailSender;
  private final Environment environment;

  public EmailNotificationChannel(NotificationProperties properties, JavaMailSender mailSender,
      Environment environment) {
    this.properties = properties;
    this.mailSender = mailSender;
    this.environment = environment;
  }

  @Override
  public String channel() {
    return NotificationDelivery.CHANNEL_EMAIL;
  }

  @Override
  public boolean enabled() {
    return properties.getEmail().isEnabled();
  }

  @Override
  public boolean configured() {
    return !environment.getProperty("spring.mail.host", "").isBlank()
        && properties.getEmail().getFrom() != null
        && !properties.getEmail().getFrom().isBlank();
  }

  @Override
  public String missingConfiguration() {
    if (environment.getProperty("spring.mail.host", "").isBlank()) {
      return "spring.mail.host";
    }
    if (properties.getEmail().getFrom() == null || properties.getEmail().getFrom().isBlank()) {
      return "mammoth.notifications.email.from";
    }
    return "";
  }

  @Override
  public void send(Notification notification, AppUser recipient) {
    if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
      throw new NotificationSkipException("recipient has no email");
    }
    sendTo(recipient.getEmail(), notification);
  }

  /** Test-send path; the caller must have verified the channel is enabled/configured. */
  public void sendTo(String email, Notification notification) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(properties.getEmail().getFrom());
    message.setTo(email);
    message.setSubject("[Mammoth] " + notification.getTitle());
    message.setText(notification.getBody() == null ? notification.getTitle()
        : notification.getTitle() + "\n\n" + notification.getBody());
    mailSender.send(message);
  }
}
