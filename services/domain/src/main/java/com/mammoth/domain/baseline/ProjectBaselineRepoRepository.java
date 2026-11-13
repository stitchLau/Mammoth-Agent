package com.mammoth.domain.baseline;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectBaselineRepoRepository
    extends JpaRepository<ProjectBaselineRepo, String> {

  List<ProjectBaselineRepo> findByBaselineIdOrderByCapturedAtAsc(String baselineId);

  Optional<ProjectBaselineRepo> findByBaselineIdAndRepositoryId(String baselineId,
      String repositoryId);
}
