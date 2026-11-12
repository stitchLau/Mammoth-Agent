package com.mammoth.domain.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

  List<AuditLog> findByObjectTypeAndObjectIdOrderByAtAsc(String objectType, String objectId);

  List<AuditLog> findByActorUserIdOrderByAtDesc(String actorUserId);

  long countByAction(String action);
}
