package com.mammoth.domain.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Local user account (ADR-0006). Password is stored as a BCrypt hash only. */
@Entity
@Table(name = "app_user")
public class AppUser {

  public static final String STATUS_ACTIVE = "active";
  public static final String STATUS_DISABLED = "disabled";
  public static final String ACCOUNT_HUMAN = "human";
  public static final String ACCOUNT_AGENT = "agent";

  @Id
  private String id;

  private String orgId;

  private String username;

  private String displayName;

  private String passwordHash;

  private String status;

  private String createdAt;

  private String email;

  private String accountType;

  private boolean passwordResetRequired;

  protected AppUser() {
  }

  public AppUser(String id, String orgId, String username, String displayName, String passwordHash,
      String status, String createdAt) {
    this(id, orgId, username, displayName, passwordHash, status, createdAt, null, ACCOUNT_HUMAN,
        false);
  }

  public AppUser(String id, String orgId, String username, String displayName, String passwordHash,
      String status, String createdAt, String email, String accountType,
      boolean passwordResetRequired) {
    this.id = id;
    this.orgId = orgId;
    this.username = username;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
    this.status = status;
    this.createdAt = createdAt;
    this.email = email;
    this.accountType = accountType == null ? ACCOUNT_HUMAN : accountType;
    this.passwordResetRequired = passwordResetRequired;
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

  public String getEmail() {
    return email;
  }

  public String getAccountType() {
    return accountType;
  }

  public boolean isPasswordResetRequired() {
    return passwordResetRequired;
  }

  public boolean isActive() {
    return STATUS_ACTIVE.equals(status);
  }

  public boolean isAgent() {
    return ACCOUNT_AGENT.equals(accountType);
  }

  public void rename(String displayName) {
    this.displayName = displayName;
  }

  public void updateEmail(String email) {
    this.email = email;
  }

  public void disable() {
    this.status = STATUS_DISABLED;
  }

  public void enable() {
    this.status = STATUS_ACTIVE;
  }

  public void markPasswordResetRequired() {
    this.passwordResetRequired = true;
  }

  public void clearPasswordResetRequired() {
    this.passwordResetRequired = false;
  }

  public void changePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }
}
