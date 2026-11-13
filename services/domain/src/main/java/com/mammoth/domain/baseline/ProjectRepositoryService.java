package com.mammoth.domain.baseline;

import com.mammoth.domain.config.DataRootBootstrap;
import com.mammoth.domain.git.GitAdapter;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.support.JsonLite;
import com.mammoth.domain.web.ApiException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers local Git repositories for a project (ADR-0007). Repository paths are chosen and
 * confirmed by a human; Mammoth never scans the disk, clones, fetches or writes a repository.
 */
@Service
public class ProjectRepositoryService {

  private static final Set<String> ANY_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);

  private final ProjectRepositoryRepository repositoryRepository;
  private final GitAdapter gitAdapter;
  private final AuthorizationService authorizationService;
  private final AuditService auditService;
  private final DataRootBootstrap dataRootBootstrap;

  public ProjectRepositoryService(ProjectRepositoryRepository repositoryRepository,
      GitAdapter gitAdapter, AuthorizationService authorizationService, AuditService auditService,
      DataRootBootstrap dataRootBootstrap) {
    this.repositoryRepository = repositoryRepository;
    this.gitAdapter = gitAdapter;
    this.authorizationService = authorizationService;
    this.auditService = auditService;
    this.dataRootBootstrap = dataRootBootstrap;
  }

  @Transactional
  public ProjectRepository register(AppUser actor, String projectId, String label, String path,
      String defaultBranch) {
    authorizationService.requireProjectRole(actor, projectId, Set.of(AuthorizationService.OWNER));
    String normalizedLabel = requireText(label, "label");
    String normalizedPath = requireText(path, "path");
    Path candidate = Path.of(normalizedPath).toAbsolutePath().normalize();
    requireOutsideDataRootAndDsh(candidate);
    GitAdapter.Snapshot snapshot = gitAdapter.inspect(candidate);
    Path workingTreeRoot = Path.of(snapshot.workingTreeRoot()).toAbsolutePath().normalize();
    if (repositoryRepository.existsByProjectIdAndAbsolutePath(projectId,
        workingTreeRoot.toString())) {
      throw ApiException.conflict(
          "repository is already registered for this project: " + workingTreeRoot);
    }
    ProjectRepository repository = new ProjectRepository(Ids.newId(), projectId, normalizedLabel,
        workingTreeRoot.toString(), snapshot.remoteUrl(), defaultBranch, actor.getId(),
        Ids.nowIso());
    repositoryRepository.save(repository);
    auditService.record(actor.getId(), "repository.register", "project_repository",
        repository.getId(), JsonLite.write(JsonLite.object(
            "path", workingTreeRoot.toString(),
            "branch", snapshot.branch(),
            "dirty", snapshot.dirty(),
            "worktree", snapshot.worktree())));
    return repository;
  }

  @Transactional(readOnly = true)
  public List<ProjectRepository> list(AppUser actor, String projectId, boolean includeInactive) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    if (includeInactive) {
      return repositoryRepository.findByProjectIdOrderByAddedAtAsc(projectId);
    }
    return repositoryRepository.findByProjectIdAndStatusOrderByAddedAtAsc(
        projectId, ProjectRepository.ACTIVE);
  }

  @Transactional(readOnly = true)
  public ProjectRepository require(String projectId, String repositoryId) {
    return repositoryRepository.findByIdAndProjectId(repositoryId, projectId)
        .orElseThrow(() -> ApiException.notFound("repository not found: " + repositoryId));
  }

  @Transactional
  public ProjectRepository deactivate(AppUser actor, String projectId, String repositoryId) {
    authorizationService.requireProjectRole(actor, projectId, Set.of(AuthorizationService.OWNER));
    ProjectRepository repository = require(projectId, repositoryId);
    repository.deactivate();
    repositoryRepository.save(repository);
    auditService.record(actor.getId(), "repository.deactivate", "project_repository", repositoryId,
        null);
    return repository;
  }

  private void requireOutsideDataRootAndDsh(Path candidate) {
    Path dataRoot = dataRootBootstrap.root().toAbsolutePath().normalize();
    if (candidate.startsWith(dataRoot)) {
      throw ApiException.badRequest(
          "repository path must not be inside the Mammoth data root: " + candidate);
    }
    for (Path segment : candidate) {
      if (".dsh".equals(segment.toString())) {
        throw ApiException.badRequest(
            "repository path must not be inside the DSH home (~/.dsh): " + candidate);
      }
    }
  }

  private static String requireText(String value, String field) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      throw ApiException.badRequest(field + " must not be empty");
    }
    return normalized;
  }
}
