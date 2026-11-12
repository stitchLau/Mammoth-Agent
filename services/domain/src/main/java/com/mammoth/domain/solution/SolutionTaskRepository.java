package com.mammoth.domain.solution;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolutionTaskRepository extends JpaRepository<SolutionTask, String> {

  List<SolutionTask> findBySolutionVersionIdOrderByOrdinalAsc(String solutionVersionId);

  Optional<SolutionTask> findByIdAndSolutionVersionId(String id, String solutionVersionId);

  long countByDesignItemId(String designItemId);

  long countByAcceptanceCriterionId(String acceptanceCriterionId);

  long countByDependsOnTaskId(String dependsOnTaskId);
}
