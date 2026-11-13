package com.mammoth.domain.notification;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, String> {

  List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(String recipientUserId);

  List<Notification> findByRecipientUserIdAndStatusOrderByCreatedAtDesc(String recipientUserId,
      String status);

  long countByRecipientUserIdAndStatus(String recipientUserId, String status);

  Optional<Notification> findByIdAndRecipientUserId(String id, String recipientUserId);

  boolean existsByRecipientUserIdAndDedupeKey(String recipientUserId, String dedupeKey);
}
