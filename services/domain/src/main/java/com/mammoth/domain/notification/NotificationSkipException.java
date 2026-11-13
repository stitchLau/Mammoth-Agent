package com.mammoth.domain.notification;

/** A delivery that should be marked skipped rather than retried. */
public class NotificationSkipException extends RuntimeException {

  public NotificationSkipException(String message) {
    super(message);
  }
}
