package com.mammoth.domain.external;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, String> {

  List<ImportBatch> findByProjectIdOrderByCreatedAtDesc(String projectId);

  Optional<ImportBatch> findByIdAndProjectId(String id, String projectId);

  Optional<ImportBatch> findBySourceIdAndFileSha256(String sourceId, String fileSha256);
}
