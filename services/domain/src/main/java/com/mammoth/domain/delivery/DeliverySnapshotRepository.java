package com.mammoth.domain.delivery;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliverySnapshotRepository extends JpaRepository<DeliverySnapshot, String> {

  List<DeliverySnapshot> findByWorkItemIdOrderByCreatedAtAsc(String workItemId);

  Optional<DeliverySnapshot> findByIdAndProjectId(String id, String projectId);

  Optional<DeliverySnapshot> findTopByWorkItemIdOrderByCreatedAtDesc(String workItemId);
}
