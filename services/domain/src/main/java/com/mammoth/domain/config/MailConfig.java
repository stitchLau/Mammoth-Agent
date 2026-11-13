package com.mammoth.domain.config;

import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Explicit JavaMailSender so the service still starts when mail is not configured (channel disabled)
 * and so the SMTP settings come from the documented {@code spring.mail.*} keys.
 */
@Configuration
public class MailConfig {

  @Bean
  @ConditionalOnMissingBean(JavaMailSender.class)
  public JavaMailSender javaMailSender(Environment environment) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(environment.getProperty("spring.mail.host", "localhost"));
    sender.setPort(environment.getProperty("spring.mail.port", Integer.class, 25));
    sender.setUsername(environment.getProperty("spring.mail.username", ""));
    sender.setPassword(environment.getProperty("spring.mail.password", ""));
    sender.setDefaultEncoding("UTF-8");
    Properties properties = sender.getJavaMailProperties();
    properties.put("mail.smtp.auth",
        environment.getProperty("spring.mail.properties.mail.smtp.auth", "false"));
    properties.put("mail.smtp.starttls.enable",
        environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable", "false"));
    properties.put("mail.smtp.connectiontimeout", "5000");
    properties.put("mail.smtp.timeout", "8000");
    properties.put("mail.smtp.writetimeout", "8000");
    return sender;
  }
}
