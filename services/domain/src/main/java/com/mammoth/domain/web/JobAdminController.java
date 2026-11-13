package com.mammoth.domain.web;

import com.mammoth.domain.notification.ServerJob;
import com.mammoth.domain.notification.ServerJobRepository;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.support.Ids;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/** Org-admin visibility/retry for the writer-only job queue (ADR-0013). */
@RestController
@RequestMapping("/api/admin/jobs")
public class JobAdminController {

  private final ServerJobRepository jobRepository;
  private final AuthorizationService authorizationService;
  private final AuditService auditService;

  public JobAdminController(ServerJobRepository jobRepository,
      AuthorizationService authorizationService, AuditService auditService) {
    this.jobRepository = jobRepository;
    this.authorizationService = authorizationService;
    this.auditService = auditService;
  }

  public record JobResponse(String id, String type, String status, int attempts, String nextRunAt,
      String lastError, String createdAt, String updatedAt) {

    static JobResponse from(ServerJob job) {
      return new JobResponse(job.getId(), job.getType(), job.getStatus(), job.getAttempts(),
          job.getNextRunAt(), job.getLastError(), job.getCreatedAt(), job.getUpdatedAt());
    }
  }

  @GetMapping
  public List<JobResponse> list(@RequestParam(value = "status", required = false) String status) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireOrgAdmin(actor);
    List<ServerJob> jobs = status == null || status.isBlank()
        ? jobRepository.findAll()
        : jobRepository.findByStatusOrderByCreatedAtDesc(status);
    return jobs.stream().limit(200).map(JobResponse::from).toList();
  }

  @PostMapping("/{jobId}/retry")
  @Transactional
  public JobResponse retry(@PathVariable String jobId) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireOrgAdmin(actor);
    ServerJob job = jobRepository.findById(jobId)
        .orElseThrow(() -> ApiException.notFound("job not found: " + jobId));
    if (ServerJob.STATUS_SUCCEEDED.equals(job.getStatus())) {
      throw ApiException.conflict("succeeded jobs cannot be retried");
    }
    job.requeue(Ids.nowIso());
    jobRepository.save(job);
    auditService.record(actor.getId(), "job.retry_manual", "server_job", job.getId(), null);
    return JobResponse.from(job);
  }
}
