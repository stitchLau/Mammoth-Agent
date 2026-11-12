package com.mammoth.domain.workitem;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementAssessmentRepository
    extends JpaRepository<RequirementAssessment, String> {

  List<RequirementAssessment> findByWorkItemIdOrderByDimensionAsc(String workItemId);

  Optional<RequirementAssessment> findByWorkItemIdAndDimension(String workItemId, String dimension);
}
