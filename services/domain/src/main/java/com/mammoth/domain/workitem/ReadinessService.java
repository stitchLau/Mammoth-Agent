package com.mammoth.domain.workitem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Deterministic readiness evaluation (M2 plan §4.3).
 *
 * <p>This is a rule engine over human assessments, not an AI judgement: it explains exactly which
 * dimension blocks the gate so the requirement owner can fix or explicitly accept the risk.
 */
@Service
public class ReadinessService {

  /** One dimension as reported to the UI (assessed or defaulted to missing). */
  public record DimensionView(String dimension, boolean critical, String state, String note,
      String assumptionNote, String decisionOwner, String updatedBy, String updatedAt) {
  }

  /** Full readiness report; {@code blocking} is the reason list shown on a failed gate. */
  public record ReadinessSummary(String workItemId, boolean ready, List<DimensionView> dimensions,
      List<String> blocking, List<String> warnings, int catalogVersion, String confirmedBy,
      String confirmedAt, String snapshotId) {
  }

  public ReadinessSummary evaluate(WorkItem item, List<RequirementAssessment> assessments) {
    Map<String, RequirementAssessment> byDimension = new LinkedHashMap<>();
    for (RequirementAssessment assessment : assessments) {
      byDimension.put(assessment.getDimension(), assessment);
    }

    List<DimensionView> dimensions = new ArrayList<>();
    List<String> blocking = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    for (String dimension : RequirementDimension.ALL) {
      RequirementAssessment assessment = byDimension.get(dimension);
      String state = assessment == null || assessment.getState() == null
          ? RequirementDimension.MISSING
          : assessment.getState();
      boolean critical = RequirementDimension.isCritical(dimension);
      dimensions.add(new DimensionView(dimension, critical, state,
          assessment == null ? null : assessment.getNote(),
          assessment == null ? null : assessment.getAssumptionNote(),
          assessment == null ? null : assessment.getDecisionOwner(),
          assessment == null ? null : assessment.getUpdatedBy(),
          assessment == null ? null : assessment.getUpdatedAt()));

      boolean hasAssumption = assessment != null && notBlank(assessment.getAssumptionNote());
      boolean hasOwner = assessment != null && notBlank(assessment.getDecisionOwner());

      if (RequirementDimension.CONFLICT.equals(state)) {
        blocking.add(dimension + ": unresolved conflict");
      } else if (RequirementDimension.MISSING.equals(state)
          || RequirementDimension.PARTIAL.equals(state)) {
        if (critical) {
          blocking.add(dimension + ": " + state + " on a critical dimension");
        } else if (!hasAssumption || !hasOwner) {
          blocking.add(dimension + ": " + state + " requires an assumption note and a decision owner");
        }
      } else if (RequirementDimension.INFERRED.equals(state) && !hasOwner) {
        blocking.add(dimension + ": inferred requires a decision owner");
      } else if (RequirementDimension.INFERRED.equals(state)) {
        warnings.add(dimension + ": inferred, decision owner " + assessment.getDecisionOwner());
      }
    }

    return new ReadinessSummary(item.getId(), blocking.isEmpty(), dimensions, blocking, warnings,
        RequirementDimension.CATALOG_VERSION, item.getReadyConfirmedBy(), item.getReadyConfirmedAt(),
        item.getContextSnapshotId());
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
