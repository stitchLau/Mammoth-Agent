package com.mammoth.domain.solution;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolutionDesignItemRepository extends JpaRepository<SolutionDesignItem, String> {

  List<SolutionDesignItem> findBySolutionVersionIdOrderByOrdinalAsc(String solutionVersionId);

  Optional<SolutionDesignItem> findByIdAndSolutionVersionId(String id, String solutionVersionId);

  long countByParentId(String parentId);
}
