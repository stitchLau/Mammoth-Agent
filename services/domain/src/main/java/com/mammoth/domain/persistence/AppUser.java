package com.mammoth.domain.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Local user account (ADR-0006). Password is stored as a BCrypt hash only. */
@Entity
@Table(name = "app_user")
public class AppUser {

  @Id
  private String id;

  private String orgId;

  private String username;

  private String displayName;

  private String passwordHash;

  private String status;

  private String createdAt;

  protected AppUser() {
  }

  public AppUser(String id, String orgId, String username, String displayName, String passwordHash,
      String status, String createdAt) {
    this.id = id;
    this.orgId = orgId;
    this.username = username;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
    this.status = status;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getOrgId() {
    return orgId;
  }

  public String getUsername() {
    return username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getStatus() {
    return status;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public boolean isActive() {
    return "active".equals(status);
  }
}
