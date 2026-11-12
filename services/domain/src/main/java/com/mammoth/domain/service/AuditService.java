package com.mammoth.domain.service;

import com.mammoth.domain.persistence.AuditLog;
import com.mammoth.domain.persistence.AuditLogRepository;
import com.mammoth.domain.support.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Append-only audit trail (ADR-0006). Must be called inside the business transaction. */
@Service
public class AuditService {

  private final AuditLogRepository auditLogRepository;

  public AuditService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(String actorUserId, String action, String objectType, String objectId,
      String detailJson) {
    auditLogRepository.save(new AuditLog(
        Ids.newId(), actorUserId, action, objectType, objectId, Ids.nowIso(), detailJson));
  }
}
