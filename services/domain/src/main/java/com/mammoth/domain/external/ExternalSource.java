package com.mammoth.domain.external;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Project-scoped external system source (ADR-0011). Never stores credentials. */
@Entity
@Table(name = "external_source")
public class ExternalSource {

  public static final String TYPE_JIRA = "jira";
  public static final String TYPE_CSM = "csm";
  public static final String TYPE_GENERIC = "generic";
  public static final String STATUS_ACTIVE = "active";

  @Id
  private String id;

  private String projectId;

  private String systemType;

  private String name;

  private String baseUrlSanitized;

  private String status;

  private String createdBy;

  private String createdAt;

  protected ExternalSource() {
  }

  public ExternalSource(String id, String projectId, String systemType, String name,
      String baseUrlSanitized, String createdBy, String createdAt) {
    this.id = id;
    this.projectId = projectId;
    this.systemType = systemType;
    this.name = name;
    this.baseUrlSanitized = baseUrlSanitized;
    this.status = STATUS_ACTIVE;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getSystemType() {
    return systemType;
  }

  public String getName() {
    return name;
  }

  public String getBaseUrlSanitized() {
    return baseUrlSanitized;
  }

  public String getStatus() {
    return status;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getCreatedAt() {
    return createdAt;
  }
}
