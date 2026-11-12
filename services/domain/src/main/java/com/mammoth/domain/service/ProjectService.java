package com.mammoth.domain.service;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AppUserRepository;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.persistence.ProjectMember;
import com.mammoth.domain.persistence.ProjectMemberRepository;
import com.mammoth.domain.persistence.ProjectRepository;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.web.ApiException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Project aggregate: creation (must bind a customer), membership, and project-scoped reads. */
@Service
public class ProjectService {

  private final ProjectRepository projectRepository;
  private final ProjectMemberRepository projectMemberRepository;
  private final AppUserRepository appUserRepository;
  private final CatalogService catalogService;
  private final OrganizationService organizationService;
  private final AuditService auditService;

  public ProjectService(
      ProjectRepository projectRepository,
      ProjectMemberRepository projectMemberRepository,
      AppUserRepository appUserRepository,
      CatalogService catalogService,
      OrganizationService organizationService,
      AuditService auditService) {
    this.projectRepository = projectRepository;
    this.projectMemberRepository = projectMemberRepository;
    this.appUserRepository = appUserRepository;
    this.catalogService = catalogService;
    this.organizationService = organizationService;
    this.auditService = auditService;
  }

  @Transactional
  public Project create(AppUser actor, String customerId, String code, String name) {
    catalogService.requireCustomer(customerId);
    String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    if (normalizedCode.isEmpty()) {
      throw ApiException.badRequest("project code must not be empty");
    }
    if (projectRepository.existsByCode(normalizedCode)) {
      throw ApiException.conflict("project code already exists: " + normalizedCode);
    }
    Project project = new Project(
        Ids.newId(),
        organizationService.currentOrgId(),
        customerId,
        normalizedCode,
        name == null || name.isBlank() ? normalizedCode : name.trim(),
        "active",
        actor.getId(),
        Ids.nowIso());
    projectRepository.save(project);
    projectMemberRepository.save(new ProjectMember(
        project.getId(), actor.getId(), AuthorizationService.OWNER, Ids.nowIso()));
    auditService.record(actor.getId(), "project.create", "project", project.getId(), null);
    return project;
  }

  @Transactional(readOnly = true)
  public Project requireProject(String projectId) {
    return projectRepository.findById(projectId)
        .orElseThrow(() -> ApiException.notFound("project not found: " + projectId));
  }

  @Transactional(readOnly = true)
  public List<Project> listVisible(AppUser user) {
    List<String> projectIds = projectMemberRepository.findByUserId(user.getId()).stream()
        .map(ProjectMember::getProjectId)
        .toList();
    return projectRepository.findAllById(projectIds).stream()
        .sorted((left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()))
        .toList();
  }

  @Transactional
  public ProjectMember addMember(AppUser actor, String projectId, String userId, String role) {
    requireProject(projectId);
    requireSupportedRole(role);
    AppUser target = appUserRepository.findById(userId)
        .orElseThrow(() -> ApiException.notFound("user not found: " + userId));
    if (projectMemberRepository.findByProjectIdAndUserId(projectId, userId).isPresent()) {
      throw ApiException.conflict("user is already a member: " + target.getUsername());
    }
    ProjectMember member = new ProjectMember(projectId, userId, role, Ids.nowIso());
    projectMemberRepository.save(member);
    auditService.record(actor.getId(), "project.member.add", "project", projectId,
        "{\"userId\":\"" + userId + "\",\"role\":\"" + role + "\"}");
    return member;
  }

  @Transactional(readOnly = true)
  public List<ProjectMember> members(String projectId) {
    return projectMemberRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
  }

  private static final List<String> ROLES = List.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);

  private static void requireSupportedRole(String role) {
    if (role == null || !ROLES.contains(role)) {
      throw ApiException.badRequest("unsupported role: " + role);
    }
  }

  /** Defense in depth: member management is owner-only even if a caller bypasses the controller. */
  private ProjectMember requireOwner(AppUser actor, String projectId) {
    ProjectMember membership = projectMemberRepository.findByProjectIdAndUserId(projectId, actor.getId())
        .orElseThrow(() -> ApiException.forbidden("no access to project " + projectId));
    if (!AuthorizationService.OWNER.equals(membership.getRole())) {
      throw ApiException.forbidden("only the project owner can manage members");
    }
    return membership;
  }

  @Transactional
  public ProjectMember changeMemberRole(AppUser actor, String projectId, String userId, String newRole) {
    requireProject(projectId);
    requireOwner(actor, projectId);
    requireSupportedRole(newRole);
    ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
        .orElseThrow(() -> ApiException.notFound("member not found: " + userId));
    boolean demotingOwner = AuthorizationService.OWNER.equals(member.getRole())
        && !AuthorizationService.OWNER.equals(newRole);
    if (demotingOwner && projectMemberRepository.countByProjectIdAndRole(
        projectId, AuthorizationService.OWNER) <= 1) {
      throw ApiException.conflict("cannot demote the last project owner");
    }
    String previousRole = member.getRole();
    member.changeRole(newRole);
    projectMemberRepository.save(member);
    auditService.record(actor.getId(), "project.member.role_change", "project", projectId,
        "{\"userId\":\"" + userId + "\",\"from\":\"" + previousRole + "\",\"to\":\"" + newRole + "\"}");
    return member;
  }

  @Transactional
  public void removeMember(AppUser actor, String projectId, String userId) {
    requireProject(projectId);
    requireOwner(actor, projectId);
    ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
        .orElseThrow(() -> ApiException.notFound("member not found: " + userId));
    if (AuthorizationService.OWNER.equals(member.getRole())
        && projectMemberRepository.countByProjectIdAndRole(projectId, AuthorizationService.OWNER) <= 1) {
      throw ApiException.conflict("cannot remove the last project owner");
    }
    projectMemberRepository.delete(member);
    auditService.record(actor.getId(), "project.member.remove", "project", projectId,
        "{\"userId\":\"" + userId + "\"}");
  }
}
