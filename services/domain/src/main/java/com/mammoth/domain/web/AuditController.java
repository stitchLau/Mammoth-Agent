package com.mammoth.domain.web;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AuditLog;
import com.mammoth.domain.persistence.AuditLogRepository;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit trail reads (T7). The log itself is append-only (database triggers, V3); this endpoint only
 * exposes a user's own actions and a project's own audit entries to project members.
 */
@RestController
@RequestMapping("/api")
public class AuditController {

  private static final Set<String> ANY_ROLE = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);

  private final AuditLogRepository auditLogRepository;
  private final AuthorizationService authorizationService;

  public AuditController(AuditLogRepository auditLogRepository,
      AuthorizationService authorizationService) {
    this.auditLogRepository = auditLogRepository;
    this.authorizationService = authorizationService;
  }

  public record AuditResponse(String id, String actorUserId, String action, String objectType,
      String objectId, String at, String detailJson) {

    static AuditResponse from(AuditLog log) {
      return new AuditResponse(log.getId(), log.getActorUserId(), log.getAction(),
          log.getObjectType(), log.getObjectId(), log.getAt(), log.getDetailJson());
    }
  }

  @GetMapping("/audit/me")
  public List<AuditResponse> myAudit(@RequestParam(value = "limit", defaultValue = "50") int limit) {
    AppUser actor = CurrentUser.require(authorizationService);
    int capped = Math.max(1, Math.min(limit, 200));
    return auditLogRepository.findByActorUserIdOrderByAtDesc(actor.getId()).stream()
        .limit(capped)
        .map(AuditResponse::from)
        .toList();
  }

  @GetMapping("/projects/{projectId}/audit")
  public List<AuditResponse> projectAudit(@PathVariable String projectId) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return auditLogRepository.findByObjectTypeAndObjectIdOrderByAtAsc("project", projectId).stream()
        .map(AuditResponse::from)
        .toList();
  }
}
