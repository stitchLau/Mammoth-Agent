package com.mammoth.domain.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Project aggregate root. Every project belongs to exactly one customer (ADR domain model). */
@Entity
@Table(name = "project")
public class Project {

  @Id
  private String id;

  private String orgId;

  private String customerId;

  private String code;

  private String name;

  private String status;

  private String ownerUserId;

  private String createdAt;

  protected Project() {
  }

  public Project(String id, String orgId, String customerId, String code, String name, String status,
      String ownerUserId, String createdAt) {
    this.id = id;
    this.orgId = orgId;
    this.customerId = customerId;
    this.code = code;
    this.name = name;
    this.status = status;
    this.ownerUserId = ownerUserId;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getOrgId() {
    return orgId;
  }

  public String getCustomerId() {
    return customerId;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public String getOwnerUserId() {
    return ownerUserId;
  }

  public String getCreatedAt() {
    return createdAt;
  }
}
