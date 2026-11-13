package com.mammoth.domain.notification;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AppUserRepository;
import com.mammoth.domain.persistence.ProjectMember;
import com.mammoth.domain.persistence.ProjectMemberRepository;
import com.mammoth.domain.persistence.RoleAssignmentRepository;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.OrganizationService;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.support.JsonLite;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Resolves recipients, de-duplicates and creates in-app + external delivery notifications. */
@Service
public class NotificationService {

  private static final Set<String> OWNER_REVIEWER = Set.of(AuthorizationService.OWNER,
      AuthorizationService.REVIEWER);

  private final NotificationRepository notificationRepository;
  private final NotificationDeliveryRepository deliveryRepository;
  private final ServerJobRepository jobRepository;
  private final ProjectMemberRepository projectMemberRepository;
  private final AppUserRepository appUserRepository;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final OrganizationService organizationService;
  private final NotificationProperties properties;
  private final List<NotificationChannel> channels;
  private final AuditService auditService;
  private final ObjectMapper mapper = new ObjectMapper();

  public NotificationService(NotificationRepository notificationRepository,
      NotificationDeliveryRepository deliveryRepository, ServerJobRepository jobRepository,
      ProjectMemberRepository projectMemberRepository, AppUserRepository appUserRepository,
      RoleAssignmentRepository roleAssignmentRepository, OrganizationService organizationService,
      NotificationProperties properties, List<NotificationChannel> channels,
      AuditService auditService) {
    this.notificationRepository = notificationRepository;
    this.deliveryRepository = deliveryRepository;
    this.jobRepository = jobRepository;
    this.projectMemberRepository = projectMemberRepository;
    this.appUserRepository = appUserRepository;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.organizationService = organizationService;
    this.properties = properties;
    this.channels = channels;
    this.auditService = auditService;
  }

  @Transactional
  public int solutionSubmitted(AppUser actor, String projectId, String workItemId,
      String versionId, int version, String title) {
    return create("solution.submitted", projectId, "solution_version", versionId,
        "v" + version, "方案待审核：v" + version + " " + title,
        "需求 " + workItemId + " 的方案已提交，请审核。",
        "/projects/" + projectId + "/requirements/" + workItemId + "/solutions/" + versionId,
        Notification.SEVERITY_INFO, projectMembers(projectId, OWNER_REVIEWER), actor);
  }

  @Transactional
  public int solutionApproved(AppUser actor, String projectId, String workItemId, String versionId,
      int version, String authorUserId, String title) {
    return create("solution.approved", projectId, "solution_version", versionId, "v" + version,
        "方案已批准：v" + version + " " + title, "需求 " + workItemId + " 的方案已批准。",
        "/projects/" + projectId + "/requirements/" + workItemId + "/solutions/" + versionId,
        Notification.SEVERITY_INFO, withUser(projectMembers(projectId, Set.of(
            AuthorizationService.OWNER)), authorUserId), actor);
  }

  @Transactional
  public int solutionChangesRequested(AppUser actor, String projectId, String workItemId,
      String versionId, int version, String authorUserId, String title) {
    return create("solution.changes_requested", projectId, "solution_version", versionId,
        "v" + version, "方案需修改：v" + version + " " + title,
        "需求 " + workItemId + " 的方案被退回修改。",
        "/projects/" + projectId + "/requirements/" + workItemId + "/solutions/" + versionId,
        Notification.SEVERITY_WARNING, withUser(projectMembers(projectId, Set.of(
            AuthorizationService.OWNER)), authorUserId), actor);
  }

  @Transactional
  public int deliveryApproved(AppUser actor, String projectId, String workItemId, String snapshotId,
      String title) {
    return create("delivery.approved", projectId, "delivery_snapshot", snapshotId, title,
        "交付快照已批准：" + title, "需求 " + workItemId + " 的 As-built 已批准。",
        "/projects/" + projectId + "/requirements/" + workItemId + "/delivery",
        Notification.SEVERITY_INFO, projectMembers(projectId, Set.of(
            AuthorizationService.OWNER, AuthorizationService.EDITOR,
            AuthorizationService.REVIEWER, AuthorizationService.VIEWER)), actor);
  }

  @Transactional
  public int knowledgeReviewRequested(AppUser actor, String projectId, String documentId,
      String title) {
    return create("knowledge.review_requested", projectId, "knowledge_document", documentId, title,
        "知识待审核：" + title, "请对知识文档进行四眼审核。",
        "/projects/" + projectId + "/knowledge/" + documentId,
        Notification.SEVERITY_INFO, projectMembers(projectId, Set.of(
            AuthorizationService.REVIEWER)), actor);
  }

  @Transactional
  public int agentTaskCompleted(AppUser actor, String projectId, String taskId, String title,
      String summary, String link) {
    return create("agent.task.completed", projectId, "agent_task", taskId, "completed",
        "Agent 任务完成：" + title, summary,
        link == null || link.isBlank() ? "/projects/" + projectId : link,
        Notification.SEVERITY_INFO, projectMembers(projectId, Set.of(
            AuthorizationService.OWNER, AuthorizationService.EDITOR,
            AuthorizationService.REVIEWER)), actor);
  }

  @Transactional
  public int agentTaskConfirmationRequired(AppUser actor, String projectId, String taskId,
      String title, String summary, String link) {
    return create("agent.task.confirmation_required", projectId, "agent_task", taskId,
        "confirmation", "Agent 任务待确认：" + title, summary,
        link == null || link.isBlank() ? "/projects/" + projectId : link,
        Notification.SEVERITY_WARNING, projectMembers(projectId, Set.of(
            AuthorizationService.OWNER, AuthorizationService.REVIEWER)), actor);
  }

  @Transactional
  public int importFailed(AppUser actor, String projectId, String sourceId, String message) {
    return create("import.failed", projectId, "external_source", sourceId, "failure",
        "外部导入失败", message,
        "/projects/" + projectId + "/imports", Notification.SEVERITY_ERROR,
        withUser(projectMembers(projectId, Set.of(AuthorizationService.OWNER)), actor.getId()),
        actor);
  }

  @Transactional
  public int jobFailed(String jobId, String type, String message) {
    return create("job.failed", null, "server_job", jobId, "failure", "后台任务失败",
        type + "：" + message, "/settings", Notification.SEVERITY_ERROR, orgAdmins(), null);
  }

  private int create(String type, String projectId, String objectType, String objectId,
      String version, String title, String body, String link, String severity,
      List<AppUser> recipients, AppUser actor) {
    if (!properties.getInApp().isEnabled() && channels.stream().noneMatch(
        channel -> channel.enabled() && channel.configured())) {
      return 0;
    }
    int created = 0;
    Set<String> seen = new LinkedHashSet<>();
    for (AppUser recipient : recipients) {
      if (recipient == null || !recipient.isActive() || recipient.isAgent()
          || (actor != null && actor.getId().equals(recipient.getId())
              && !"import.failed".equals(type))
          || !seen.add(recipient.getId())) {
        continue;
      }
      String dedupeKey = type + ":" + objectId + ":" + version + ":" + recipient.getId();
      if (notificationRepository.existsByRecipientUserIdAndDedupeKey(recipient.getId(), dedupeKey)) {
        continue;
      }
      String now = Ids.nowIso();
      Notification notification = new Notification(Ids.newId(), organizationService.currentOrgId(),
          recipient.getId(), type, title, body, projectId, objectType, objectId, link, severity,
          dedupeKey, now);
      notificationRepository.save(notification);
      if (properties.getInApp().isEnabled()) {
        NotificationDelivery inApp = new NotificationDelivery(Ids.newId(), notification.getId(),
            NotificationDelivery.CHANNEL_IN_APP, NotificationDelivery.STATUS_PENDING, null, now, now);
        inApp.markSent(now);
        deliveryRepository.save(inApp);
      }
      for (NotificationChannel channel : channels) {
        if (!channel.enabled() || !channel.configured()) {
          continue;
        }
        NotificationDelivery delivery = new NotificationDelivery(Ids.newId(), notification.getId(),
            channel.channel(), NotificationDelivery.STATUS_PENDING, now, now, now);
        deliveryRepository.save(delivery);
        String idempotencyKey = "delivery:" + delivery.getId();
        if (!jobRepository.existsByIdempotencyKey(idempotencyKey)) {
          jobRepository.save(new ServerJob(Ids.newId(), ServerJob.TYPE_NOTIFICATION_DISPATCH,
              payload(delivery.getId()), idempotencyKey, actor == null ? null : actor.getId(), now));
        }
      }
      auditService.record(actor == null ? null : actor.getId(), "notification.create",
          "notification", notification.getId(), JsonLite.write(JsonLite.object("type", type)));
      created++;
    }
    return created;
  }

  private String payload(String deliveryId) {
    try {
      return mapper.writeValueAsString(Map.of("deliveryId", deliveryId));
    } catch (Exception error) {
      return "{\"deliveryId\":\"" + deliveryId + "\"}";
    }
  }

  private List<AppUser> projectMembers(String projectId, Set<String> roles) {
    List<AppUser> users = new ArrayList<>();
    for (ProjectMember member : projectMemberRepository.findByProjectIdOrderByCreatedAtAsc(projectId)) {
      if (!roles.contains(member.getRole())) {
        continue;
      }
      appUserRepository.findById(member.getUserId()).filter(AppUser::isActive)
          .ifPresent(users::add);
    }
    return users;
  }

  private List<AppUser> withUser(List<AppUser> users, String userId) {
    List<AppUser> result = new ArrayList<>(users);
    if (userId != null) {
      appUserRepository.findById(userId).filter(AppUser::isActive)
          .ifPresent(result::add);
    }
    return result;
  }

  private List<AppUser> orgAdmins() {
    String orgId = organizationService.currentOrgId();
    List<AppUser> result = new ArrayList<>();
    roleAssignmentRepository.findByRoleAndScopeTypeAndScopeId(AuthorizationService.ORG_ADMIN,
            AuthorizationService.SCOPE_ORGANIZATION, orgId).stream()
        .map(assignment -> assignment.getUserId())
        .distinct()
        .forEach(userId -> appUserRepository.findById(userId).filter(AppUser::isActive)
            .ifPresent(result::add));
    return result;
  }
}
