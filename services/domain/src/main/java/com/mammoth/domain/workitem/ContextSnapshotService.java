package com.mammoth.domain.workitem;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.support.JsonLite;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures immutable requirement context baselines (ADR-0010). Payloads keep identifiers and
 * assessment states only; customer evidence bodies stay in {@code requirement_evidence}.
 */
@Service
public class ContextSnapshotService {

  private final RequirementSnapshotRepository snapshotRepository;
  private final RequirementEvidenceRepository evidenceRepository;
  private final RequirementAssessmentRepository assessmentRepository;

  public ContextSnapshotService(RequirementSnapshotRepository snapshotRepository,
      RequirementEvidenceRepository evidenceRepository,
      RequirementAssessmentRepository assessmentRepository) {
    this.snapshotRepository = snapshotRepository;
    this.evidenceRepository = evidenceRepository;
    this.assessmentRepository = assessmentRepository;
  }

  @Transactional
  public RequirementSnapshot capture(AppUser actor, WorkItem item, String purpose) {
    int version = (int) snapshotRepository.countByWorkItemId(item.getId()) + 1;
    String payload = JsonLite.write(buildPayload(item, purpose));
    RequirementSnapshot snapshot = new RequirementSnapshot(
        Ids.newId(), item.getId(), version, payload, actor.getId(), Ids.nowIso());
    return snapshotRepository.save(snapshot);
  }

  @Transactional(readOnly = true)
  public List<RequirementSnapshot> list(String workItemId) {
    return snapshotRepository.findByWorkItemIdOrderByVersionAsc(workItemId);
  }

  @Transactional(readOnly = true)
  public RequirementSnapshot require(String snapshotId) {
    return snapshotRepository.findById(snapshotId)
        .orElseThrow(() -> com.mammoth.domain.web.ApiException.notFound(
            "requirement snapshot not found: " + snapshotId));
  }

  Map<String, Object> buildPayload(WorkItem item, String purpose) {
    List<Object> evidence = new ArrayList<>();
    for (RequirementEvidence entry : evidenceRepository.findByWorkItemIdOrderByBatchNoAsc(item.getId())) {
      evidence.add(JsonLite.object(
          "id", entry.getId(),
          "batchNo", entry.getBatchNo(),
          "type", entry.getEvidenceType(),
          "artifactId", entry.getArtifactId(),
          "capturedAt", entry.getCapturedAt()));
    }

    Map<String, Object> assessments = new LinkedHashMap<>();
    for (RequirementAssessment assessment
        : assessmentRepository.findByWorkItemIdOrderByDimensionAsc(item.getId())) {
      assessments.put(assessment.getDimension(), JsonLite.object(
          "state", assessment.getState(),
          "note", assessment.getNote(),
          "assumptionNote", assessment.getAssumptionNote(),
          "decisionOwner", assessment.getDecisionOwner()));
    }

    return JsonLite.object(
        "purpose", purpose,
        "catalogVersion", RequirementDimension.CATALOG_VERSION,
        "workItem", JsonLite.object(
            "id", item.getId(),
            "itemNo", item.getItemNo(),
            "title", item.getTitle(),
            "summary", item.getSummary(),
            "status", item.getStatus(),
            "priority", item.getPriority(),
            "ownerUserId", item.getOwnerUserId(),
            "createdAt", item.getCreatedAt()),
        "evidence", evidence,
        "assessments", assessments);
  }
}
