package com.mammoth.domain.baseline;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;

/**
 * A Git repository registered for a project (ADR-0007). Mammoth stores the path and sanitized remote
 * URL only; source code never enters the database or the knowledge index.
 */
@Entity
@Table(name = "project_repository")
public class ProjectRepository {

  public static final String ACTIVE = "active";
  public static final String INACTIVE = "inactive";

  @Id
  private String id;

  private String projectId;

  private String label;

  private String absolutePath;

  private String remoteUrl;

  private String defaultBranch;

  private String status;

  private String addedBy;

  private String addedAt;

  protected ProjectRepository() {
  }

  public ProjectRepository(String id, String projectId, String label, String absolutePath,
      String remoteUrl, String defaultBranch, String addedBy, String addedAt) {
    this.id = id;
    this.projectId = projectId;
    this.label = label;
    this.absolutePath = absolutePath;
    this.remoteUrl = remoteUrl;
    this.defaultBranch = defaultBranch == null || defaultBranch.isBlank()
        ? null : defaultBranch.trim().toLowerCase(Locale.ROOT);
    this.status = ACTIVE;
    this.addedBy = addedBy;
    this.addedAt = addedAt;
  }

  public void deactivate() {
    this.status = INACTIVE;
  }

  public boolean isActive() {
    return ACTIVE.equals(status);
  }

  public String getId() {
    return id;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getLabel() {
    return label;
  }

  public String getAbsolutePath() {
    return absolutePath;
  }

  public String getRemoteUrl() {
    return remoteUrl;
  }

  public String getDefaultBranch() {
    return defaultBranch;
  }

  public String getStatus() {
    return status;
  }

  public String getAddedBy() {
    return addedBy;
  }

  public String getAddedAt() {
    return addedAt;
  }
}
