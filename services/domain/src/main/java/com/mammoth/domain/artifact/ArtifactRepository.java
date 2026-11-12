package com.mammoth.domain.artifact;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtifactRepository extends JpaRepository<ArtifactRecord, String> {

  List<ArtifactRecord> findByProjectIdOrderByCreatedAtAsc(String projectId);

  Optional<ArtifactRecord> findByProjectIdAndContentHash(String projectId, String contentHash);
}
