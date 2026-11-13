package com.mammoth.domain.web;

import com.mammoth.domain.baseline.BaselineService;
import com.mammoth.domain.baseline.ProjectBaselineRepo;
import com.mammoth.domain.baseline.ProjectRepository;
import com.mammoth.domain.baseline.ProjectRepositoryService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Project repositories and immutable code baselines (ADR-0007). */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class BaselineController {

  private final ProjectRepositoryService repositoryService;
  private final BaselineService baselineService;
  private final AuthorizationService authorizationService;

  public BaselineController(ProjectRepositoryService repositoryService,
      BaselineService baselineService, AuthorizationService authorizationService) {
    this.repositoryService = repositoryService;
    this.baselineService = baselineService;
    this.authorizationService = authorizationService;
  }

  public record RegisterRepositoryRequest(String label, String path, String defaultBranch) {
  }

  public record CaptureBaselineRequest(String note, Boolean allowDirty) {
  }

  public record RepositoryResponse(String id, String projectId, String label, String absolutePath,
      String remoteUrl, String defaultBranch, String status, String addedBy, String addedAt) {

    static RepositoryResponse from(ProjectRepository repository) {
      return new RepositoryResponse(repository.getId(), repository.getProjectId(),
          repository.getLabel(), repository.getAbsolutePath(), repository.getRemoteUrl(),
          repository.getDefaultBranch(), repository.getStatus(), repository.getAddedBy(),
          repository.getAddedAt());
    }
  }

  public record BaselineRepoResponse(String id, String repositoryId, String commitSha, String branch,
      boolean dirty, boolean detached, boolean worktree, String capturedAt) {

    static BaselineRepoResponse from(ProjectBaselineRepo entry) {
      return new BaselineRepoResponse(entry.getId(), entry.getRepositoryId(), entry.getCommitSha(),
          entry.getBranch(), entry.isDirty(), entry.isDetached(), entry.isWorktree(),
          entry.getCapturedAt());
    }
  }

  public record BaselineResponse(String id, String projectId, String note, boolean complete,
      String capturedBy, String capturedAt, List<BaselineRepoResponse> repos) {

    static BaselineResponse from(BaselineService.Detail detail) {
      return new BaselineResponse(detail.baseline().getId(), detail.baseline().getProjectId(),
          detail.baseline().getNote(), detail.baseline().isComplete(),
          detail.baseline().getCapturedBy(), detail.baseline().getCapturedAt(),
          detail.repos().stream().map(BaselineRepoResponse::from).toList());
    }
  }

  @GetMapping("/repositories")
  public List<RepositoryResponse> listRepositories(@PathVariable String projectId,
      @RequestParam(value = "includeInactive", defaultValue = "false") boolean includeInactive) {
    AppUser actor = CurrentUser.require(authorizationService);
    return repositoryService.list(actor, projectId, includeInactive).stream()
        .map(RepositoryResponse::from).toList();
  }

  @PostMapping("/repositories")
  public RepositoryResponse registerRepository(@PathVariable String projectId,
      @RequestBody RegisterRepositoryRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return RepositoryResponse.from(repositoryService.register(actor, projectId, request.label(),
        request.path(), request.defaultBranch()));
  }

  @DeleteMapping("/repositories/{repositoryId}")
  public RepositoryResponse deactivateRepository(@PathVariable String projectId,
      @PathVariable String repositoryId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return RepositoryResponse.from(
        repositoryService.deactivate(actor, projectId, repositoryId));
  }

  @GetMapping("/baselines")
  public List<BaselineResponse> listBaselines(@PathVariable String projectId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return baselineService.list(actor, projectId).stream()
        .map(baseline -> BaselineResponse.from(baselineService.detail(baseline.getId(), projectId)))
        .toList();
  }

  @PostMapping("/baselines")
  public BaselineResponse captureBaseline(@PathVariable String projectId,
      @RequestBody CaptureBaselineRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return BaselineResponse.from(baselineService.capture(actor, projectId, request.note(),
        request.allowDirty() != null && request.allowDirty()));
  }

  @GetMapping("/baselines/{baselineId}")
  public BaselineResponse baseline(@PathVariable String projectId,
      @PathVariable String baselineId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return BaselineResponse.from(baselineService.detail(actor, projectId, baselineId));
  }
}
