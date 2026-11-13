package com.mammoth.domain.baseline;

import com.mammoth.domain.git.GitAdapter;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.support.JsonLite;
import com.mammoth.domain.web.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures immutable project baselines (ADR-0007): for every active repository, record the exact
 * commit, branch and working-tree state. Dirty working trees are rejected unless the project owner
 * explicitly accepts a non-reproducible baseline.
 */
@Service
public class BaselineService {

  private static final Set<String> WRITE_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR);
  private static final Set<String> OWNER_ONLY = Set.of(AuthorizationService.OWNER);
  private static final Set<String> ANY_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);

  private final ProjectBaselineRepository baselineRepository;
  private final ProjectBaselineRepoRepository baselineRepoRepository;
  private final ProjectRepositoryRepository repositoryRepository;
  private final GitAdapter gitAdapter;
  private final AuthorizationService authorizationService;
  private final AuditService auditService;

  public BaselineService(ProjectBaselineRepository baselineRepository,
      ProjectBaselineRepoRepository baselineRepoRepository,
      ProjectRepositoryRepository repositoryRepository, GitAdapter gitAdapter,
      AuthorizationService authorizationService, AuditService auditService) {
    this.baselineRepository = baselineRepository;
    this.baselineRepoRepository = baselineRepoRepository;
    this.repositoryRepository = repositoryRepository;
    this.gitAdapter = gitAdapter;
    this.authorizationService = authorizationService;
    this.auditService = auditService;
  }

  public record Detail(ProjectBaseline baseline, List<ProjectBaselineRepo> repos) {
  }

  @Transactional
  public Detail capture(AppUser actor, String projectId, String note, boolean allowDirty) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    if (allowDirty) {
      authorizationService.requireProjectRole(actor, projectId, OWNER_ONLY);
    }
    List<ProjectRepository> repositories = repositoryRepository
        .findByProjectIdAndStatusOrderByAddedAtAsc(projectId, ProjectRepository.ACTIVE);
    if (repositories.isEmpty()) {
      throw ApiException.badRequest(
          "no active repository registered for this project; register a repository first");
    }

    List<GitAdapter.Snapshot> snapshots = new ArrayList<>();
    List<String> dirtyRepositories = new ArrayList<>();
    for (ProjectRepository repository : repositories) {
      GitAdapter.Snapshot snapshot = gitAdapter.inspect(java.nio.file.Path.of(
          repository.getAbsolutePath()));
      snapshots.add(snapshot);
      if (snapshot.dirty()) {
        dirtyRepositories.add(repository.getLabel());
      }
    }
    if (!dirtyRepositories.isEmpty() && !allowDirty) {
      throw ApiException.conflict(
          "working tree is dirty for: " + String.join(", ", dirtyRepositories)
              + ". Commit/stash your changes first, or let the project owner capture a"
              + " non-reproducible baseline with allowDirty=true.");
    }

    String now = Ids.nowIso();
    ProjectBaseline baseline = new ProjectBaseline(Ids.newId(), projectId, blankToNull(note), true,
        actor.getId(), now);
    baselineRepository.save(baseline);
    for (int index = 0; index < repositories.size(); index++) {
      ProjectRepository repository = repositories.get(index);
      GitAdapter.Snapshot snapshot = snapshots.get(index);
      baselineRepoRepository.save(new ProjectBaselineRepo(Ids.newId(), baseline.getId(),
          repository.getId(), snapshot.headCommit(), snapshot.branch(), snapshot.dirty(),
          snapshot.detached(), snapshot.worktree(), now));
    }
    auditService.record(actor.getId(), "baseline.capture", "project_baseline", baseline.getId(),
        JsonLite.write(JsonLite.object(
            "repositories", repositories.size(),
            "allowDirty", allowDirty,
            "dirtyCount", dirtyRepositories.size())));
    return detail(baseline.getId(), projectId);
  }

  @Transactional(readOnly = true)
  public List<ProjectBaseline> list(AppUser actor, String projectId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    return baselineRepository.findByProjectIdOrderByCapturedAtDesc(projectId);
  }

  @Transactional(readOnly = true)
  public ProjectBaseline latest(String projectId) {
    return baselineRepository.findTopByProjectIdOrderByCapturedAtDesc(projectId).orElse(null);
  }

  @Transactional(readOnly = true)
  public ProjectBaseline require(String projectId, String baselineId) {
    return baselineRepository.findByIdAndProjectId(baselineId, projectId)
        .orElseThrow(() -> ApiException.notFound("baseline not found: " + baselineId));
  }

  @Transactional(readOnly = true)
  public Detail detail(String baselineId, String projectId) {
    ProjectBaseline baseline = require(projectId, baselineId);
    return new Detail(baseline, baselineRepoRepository.findByBaselineIdOrderByCapturedAtAsc(baselineId));
  }

  @Transactional(readOnly = true)
  public Detail detail(AppUser actor, String projectId, String baselineId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    return detail(baselineId, projectId);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
