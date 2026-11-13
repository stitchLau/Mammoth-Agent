package com.mammoth.domain.external;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalRecordRepository extends JpaRepository<ExternalRecord, String> {

  List<ExternalRecord> findByProjectIdAndStatusOrderByCreatedAtAsc(String projectId, String status);

  List<ExternalRecord> findByProjectIdOrderByCreatedAtAsc(String projectId);

  List<ExternalRecord> findByProjectIdAndMappedCriterionIdAndStatus(
      String projectId, String mappedCriterionId, String status);

  List<ExternalRecord> findByProjectIdAndMappedWorkItemIdAndMappedCriterionIdIsNullAndStatus(
      String projectId, String mappedWorkItemId, String status);

  Optional<ExternalRecord> findByIdAndProjectId(String id, String projectId);

  boolean existsBySourceIdAndExternalTypeAndExternalIdAndExternalVersion(
      String sourceId, String externalType, String externalId, String externalVersion);
}
