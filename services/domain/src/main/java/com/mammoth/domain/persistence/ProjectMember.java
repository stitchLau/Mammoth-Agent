package com.mammoth.domain.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/** Project membership with a role (ADR-0006 project-scoped RBAC). */
@Entity
@Table(name = "project_member")
@IdClass(ProjectMember.Key.class)
public class ProjectMember {

  @Id
  private String projectId;

  @Id
  private String userId;

  private String role;

  private String createdAt;

  protected ProjectMember() {
  }

  public ProjectMember(String projectId, String userId, String role, String createdAt) {
    this.projectId = projectId;
    this.userId = userId;
    this.role = role;
    this.createdAt = createdAt;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getUserId() {
    return userId;
  }

  public String getRole() {
    return role;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void changeRole(String role) {
    this.role = role;
  }

  /** Composite primary key. */
  public static class Key implements Serializable {

    private String projectId;

    private String userId;

    public Key() {
    }

    public Key(String projectId, String userId) {
      this.projectId = projectId;
      this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof Key key)) {
        return false;
      }
      return Objects.equals(projectId, key.projectId) && Objects.equals(userId, key.userId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(projectId, userId);
    }
  }
}
