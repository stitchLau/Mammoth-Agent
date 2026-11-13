package com.mammoth.domain.baseline;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepositoryRepository extends JpaRepository<ProjectRepository, String> {

  List<ProjectRepository> findByProjectIdOrderByAddedAtAsc(String projectId);

  List<ProjectRepository> findByProjectIdAndStatusOrderByAddedAtAsc(String projectId, String status);

  Optional<ProjectRepository> findByIdAndProjectId(String id, String projectId);

  boolean existsByProjectIdAndAbsolutePath(String projectId, String absolutePath);
}
