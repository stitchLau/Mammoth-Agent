package com.mammoth.domain.notification;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerJobRepository extends JpaRepository<ServerJob, String> {

  Optional<ServerJob> findFirstByStatusAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
      String status, String now);

  List<ServerJob> findByStatusOrderByCreatedAtDesc(String status);

  boolean existsByIdempotencyKey(String idempotencyKey);
}
