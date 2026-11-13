package com.mammoth.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mammoth.domain.persistence.AppUser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

/** Channel-level checks with a mocked mail sender and a local HTTP stub; no external sends. */
class NotificationChannelTest {

  private static Notification notification() {
    return new Notification("n-1", "org", "user", "solution.submitted", "方案待审核",
        "请审核", "project", "solution_version", "v1", "/projects/p", Notification.SEVERITY_INFO,
        "dedupe", "2026-11-13T00:00:00Z");
  }

  @Test
  void emailBuildsMessageFromConfiguration() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("spring.mail.host", "localhost");
    NotificationProperties properties = new NotificationProperties(environment);
    properties.getEmail().setEnabled(true);
    properties.getEmail().setFrom("mammoth@example.com");
    JavaMailSender sender = mock(JavaMailSender.class);
    EmailNotificationChannel channel = new EmailNotificationChannel(properties, sender, environment);

    channel.sendTo("user@example.com", notification());

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender).send(captor.capture());
    assertThat(captor.getValue().getFrom()).isEqualTo("mammoth@example.com");
    assertThat(captor.getValue().getTo()).containsExactly("user@example.com");
    assertThat(captor.getValue().getSubject()).contains("方案待审核");
  }

  @Test
  void wecomPostsMarkdownToConfiguredWebhookOnly() throws Exception {
    AtomicReference<String> body = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/webhook", exchange -> {
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = "{\"errcode\":0,\"errmsg\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      MockEnvironment environment = new MockEnvironment();
      NotificationProperties properties = new NotificationProperties(environment);
      properties.getWecom().setEnabled(true);
      properties.getWecom().setWebhookUrl(
          "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook");
      properties.getWecom().setEvents("solution.submitted");
      WecomNotificationChannel channel = new WecomNotificationChannel(properties);

      channel.send(notification(), null);

      assertThat(body.get()).contains("\"msgtype\":\"markdown\"")
          .contains("方案待审核");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void disabledChannelsDoNotClaimToBeConfigured() {
    NotificationProperties properties = new NotificationProperties(new MockEnvironment());
    JavaMailSender sender = mock(JavaMailSender.class);
    EmailNotificationChannel email = new EmailNotificationChannel(properties, sender,
        new MockEnvironment());
    WecomNotificationChannel wecom = new WecomNotificationChannel(properties);
    assertThat(email.enabled()).isFalse();
    assertThat(wecom.enabled()).isFalse();
  }
}
