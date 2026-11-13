package com.mammoth.domain.notification;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.ProjectRepository;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.support.JsonLite;
import com.mammoth.domain.web.ApiException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hook contract for future Agent-task runtimes (M4.5-1). The current codebase has no AgentTask
 * table, so this is deliberately a small, auditable event ingress rather than a fake task model.
 */
@Service
public class AgentHookService {

  public static final String TASK_COMPLETED = "agent.task.completed";
  public static final String CONFIRMATION_REQUIRED = "agent.task.confirmation_required";
  public static final Set<String> TYPES = Set.of(TASK_COMPLETED, CONFIRMATION_REQUIRED);

  private static final Set<String> WRITE_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR);

  private final NotificationService notificationService;
  private final AuthorizationService authorizationService;
  private final ProjectRepository projectRepository;
  private final AuditService auditService;

  public AgentHookService(NotificationService notificationService,
      AuthorizationService authorizationService, ProjectRepository projectRepository,
      AuditService auditService) {
    this.notificationService = notificationService;
    this.authorizationService = authorizationService;
    this.projectRepository = projectRepository;
    this.auditService = auditService;
  }

  @Transactional
  public int emit(AppUser actor, String projectId, String eventType, String taskId, String title,
      String summary, String link) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    projectRepository.findById(projectId)
        .orElseThrow(() -> ApiException.notFound("project not found: " + projectId));
    String type = requireText(eventType, "eventType", 80);
    if (!TYPES.contains(type)) {
      throw ApiException.badRequest("unsupported agent hook event: " + type);
    }
    String normalizedTaskId = requireText(taskId, "taskId", 100);
    String normalizedTitle = requireText(title, "title", 200);
    String normalizedSummary = summary == null ? null : truncate(summary.trim(), 2000);
    String normalizedLink = relativeLink(projectId, link);
    int created = switch (type) {
      case TASK_COMPLETED -> notificationService.agentTaskCompleted(actor, projectId,
          normalizedTaskId, normalizedTitle, normalizedSummary, normalizedLink);
      case CONFIRMATION_REQUIRED -> notificationService.agentTaskConfirmationRequired(actor,
          projectId, normalizedTaskId, normalizedTitle, normalizedSummary, normalizedLink);
      default -> 0;
    };
    auditService.record(actor.getId(), "agent.hook", "agent_task", normalizedTaskId,
        JsonLite.write(JsonLite.object("eventType", type, "projectId", projectId,
            "created", created, "at", Ids.nowIso())));
    return created;
  }

  private static String relativeLink(String projectId, String link) {
    if (link == null || link.isBlank()) {
      return "/projects/" + projectId;
    }
    String trimmed = link.trim();
    if (!trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.length() > 500) {
      throw ApiException.badRequest("link must be a relative app path starting with /");
    }
    return trimmed;
  }

  private static String requireText(String value, String field, int max) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      throw ApiException.badRequest(field + " must not be empty");
    }
    if (normalized.length() > max) {
      throw ApiException.badRequest(field + " is too long (max " + max + ")");
    }
    return normalized;
  }

  private static String truncate(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max);
  }
}
