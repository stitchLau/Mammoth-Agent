package com.mammoth.domain.solution;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcceptanceCriterionRepository extends JpaRepository<AcceptanceCriterion, String> {

  List<AcceptanceCriterion> findBySolutionVersionIdOrderByOrdinalAsc(String solutionVersionId);

  Optional<AcceptanceCriterion> findByIdAndSolutionVersionId(String id, String solutionVersionId);

  long countByDesignItemId(String designItemId);
}
