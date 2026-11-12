package com.mammoth.domain.web;

import com.mammoth.domain.artifact.ArtifactRecord;
import com.mammoth.domain.artifact.ArtifactService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Project-scoped artifact upload/list/download (ADR-0004, membership enforced). */
@RestController
@RequestMapping("/api/projects/{projectId}/artifacts")
public class ArtifactController {

  private static final Set<String> ANY_ROLE = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);

  private final ArtifactService artifactService;
  private final AuthorizationService authorizationService;

  public ArtifactController(
      ArtifactService artifactService,
      AuthorizationService authorizationService) {
    this.artifactService = artifactService;
    this.authorizationService = authorizationService;
  }

  public record ArtifactResponse(String id, String projectId, String kind, String originalName,
      String contentHash, long sizeBytes, String mime, String sourceNote, String uploadedBy,
      String createdAt) {

    static ArtifactResponse from(ArtifactRecord record) {
      return new ArtifactResponse(record.getId(), record.getProjectId(), record.getKind(),
          record.getOriginalName(), record.getContentHash(), record.getSizeBytes(), record.getMime(),
          record.getSourceNote(), record.getUploadedBy(), record.getCreatedAt());
    }
  }

  @PostMapping
  public ArtifactResponse upload(
      @PathVariable String projectId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "kind", required = false) String kind,
      @RequestParam(value = "note", required = false) String note) throws IOException {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, ArtifactService.WRITE_ROLES);
    ArtifactRecord record = artifactService.upload(
        actor, projectId, kind, file.getOriginalFilename(), file.getContentType(), note,
        file.getBytes());
    return ArtifactResponse.from(record);
  }

  @GetMapping
  public List<ArtifactResponse> list(@PathVariable String projectId) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return artifactService.list(projectId).stream().map(ArtifactResponse::from).toList();
  }

  @GetMapping("/{artifactId}")
  public ArtifactResponse metadata(@PathVariable String projectId, @PathVariable String artifactId) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return ArtifactResponse.from(artifactService.require(projectId, artifactId));
  }

  @GetMapping("/{artifactId}/content")
  public ResponseEntity<byte[]> content(@PathVariable String projectId,
      @PathVariable String artifactId) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    ArtifactRecord record = artifactService.require(projectId, artifactId);
    byte[] content = artifactService.content(record);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(record.getOriginalName()).build().toString())
        .contentType(record.getMime() == null
            ? MediaType.APPLICATION_OCTET_STREAM
            : MediaType.parseMediaType(record.getMime()))
        .body(content);
  }
}
