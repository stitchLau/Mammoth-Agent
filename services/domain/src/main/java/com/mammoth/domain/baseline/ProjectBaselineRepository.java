package com.mammoth.domain.baseline;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectBaselineRepository extends JpaRepository<ProjectBaseline, String> {

  List<ProjectBaseline> findByProjectIdOrderByCapturedAtDesc(String projectId);

  Optional<ProjectBaseline> findTopByProjectIdOrderByCapturedAtDesc(String projectId);

  Optional<ProjectBaseline> findByIdAndProjectId(String id, String projectId);
}
