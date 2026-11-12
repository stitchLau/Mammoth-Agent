package com.mammoth.domain.web;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.persistence.ProjectMember;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.ProjectService;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project aggregate API. Every project-scoped operation is authorized against the session user's
 * project membership (ADR-0006, deny by default).
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

  private static final Set<String> ANY_ROLE = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);
  private static final Set<String> OWNER_ONLY = Set.of(AuthorizationService.OWNER);

  private final ProjectService projectService;
  private final AuthorizationService authorizationService;

  public ProjectController(ProjectService projectService, AuthorizationService authorizationService) {
    this.projectService = projectService;
    this.authorizationService = authorizationService;
  }

  public record CreateProjectRequest(String customerId, String code, String name) {
  }

  public record AddMemberRequest(String userId, String role) {
  }

  public record ProjectResponse(String id, String customerId, String code, String name,
      String status, String ownerUserId, String createdAt) {

    static ProjectResponse from(Project project) {
      return new ProjectResponse(project.getId(), project.getCustomerId(), project.getCode(),
          project.getName(), project.getStatus(), project.getOwnerUserId(), project.getCreatedAt());
    }
  }

  public record MemberResponse(String projectId, String userId, String role, String createdAt) {

    static MemberResponse from(ProjectMember member) {
      return new MemberResponse(member.getProjectId(), member.getUserId(), member.getRole(),
          member.getCreatedAt());
    }
  }

  @PostMapping
  public ProjectResponse create(@RequestBody CreateProjectRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return ProjectResponse.from(
        projectService.create(actor, request.customerId(), request.code(), request.name()));
  }

  @GetMapping
  public List<ProjectResponse> list() {
    AppUser actor = CurrentUser.require(authorizationService);
    return projectService.listVisible(actor).stream().map(ProjectResponse::from).toList();
  }

  @GetMapping("/{projectId}")
  public ProjectResponse get(@PathVariable String projectId) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return ProjectResponse.from(projectService.requireProject(projectId));
  }

  @GetMapping("/{projectId}/members")
  public List<MemberResponse> members(@PathVariable String projectId) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return projectService.members(projectId).stream().map(MemberResponse::from).toList();
  }

  @PostMapping("/{projectId}/members")
  public MemberResponse addMember(@PathVariable String projectId, @RequestBody AddMemberRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, OWNER_ONLY);
    return MemberResponse.from(
        projectService.addMember(actor, projectId, request.userId(), request.role()));
  }
}
