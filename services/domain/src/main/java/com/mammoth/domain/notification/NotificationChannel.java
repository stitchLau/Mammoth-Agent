package com.mammoth.domain.notification;

import com.mammoth.domain.persistence.AppUser;

/** External notification channel (ADR-0013). Implementations must never log secrets. */
public interface NotificationChannel {

  String channel();

  boolean enabled();

  boolean configured();

  String missingConfiguration();

  /** Send one notification; throws on a retryable/permanent channel failure. */
  void send(Notification notification, AppUser recipient) throws Exception;
}
