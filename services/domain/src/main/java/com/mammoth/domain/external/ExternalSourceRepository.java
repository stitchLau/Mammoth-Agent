package com.mammoth.domain.external;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalSourceRepository extends JpaRepository<ExternalSource, String> {

  List<ExternalSource> findByProjectIdOrderByCreatedAtAsc(String projectId);

  Optional<ExternalSource> findByIdAndProjectId(String id, String projectId);

  boolean existsByProjectIdAndName(String projectId, String name);
}
