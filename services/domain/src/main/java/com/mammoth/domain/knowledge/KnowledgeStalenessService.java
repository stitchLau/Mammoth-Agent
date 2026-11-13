package com.mammoth.domain.knowledge;

import com.mammoth.domain.baseline.BaselineService;
import com.mammoth.domain.baseline.ProjectBaseline;
import org.springframework.stereotype.Service;

/**
 * Knowledge staleness (ADR-0007 §11): a document tied to an older code baseline is flagged as
 * "possibly outdated". The flag is advisory only; knowledge status is never changed automatically.
 */
@Service
public class KnowledgeStalenessService {

  private final BaselineService baselineService;

  public KnowledgeStalenessService(BaselineService baselineService) {
    this.baselineService = baselineService;
  }

  public boolean isStale(KnowledgeDocument document) {
    String baselineId = document.getApplicableBaselineId();
    if (baselineId == null) {
      return false;
    }
    ProjectBaseline latest = baselineService.latest(document.getScopeId());
    return latest != null && !baselineId.equals(latest.getId());
  }
}
