package com.mammoth.domain.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Organization-level role assignment (ADR-0006 M4 addendum). The table already existed in V1 for
 * future scope; M4 uses it for {@code org_admin} instead of adding a second role model.
 */
@Entity
@Table(name = "role_assignment")
public class RoleAssignment {

  @Id
  private String id;

  private String userId;

  private String role;

  private String scopeType;

  private String scopeId;

  private String createdAt;

  protected RoleAssignment() {
  }

  public RoleAssignment(String id, String userId, String role, String scopeType, String scopeId,
      String createdAt) {
    this.id = id;
    this.userId = userId;
    this.role = role;
    this.scopeType = scopeType;
    this.scopeId = scopeId;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public String getRole() {
    return role;
  }

  public String getScopeType() {
    return scopeType;
  }

  public String getScopeId() {
    return scopeId;
  }

  public String getCreatedAt() {
    return createdAt;
  }
}
