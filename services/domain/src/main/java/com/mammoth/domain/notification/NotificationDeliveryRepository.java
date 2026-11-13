package com.mammoth.domain.notification;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, String> {

  List<NotificationDelivery> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
      String status, String now);

  List<NotificationDelivery> findByNotificationIdOrderByCreatedAtAsc(String notificationId);
}
