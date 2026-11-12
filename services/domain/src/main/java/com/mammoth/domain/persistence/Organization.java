package com.mammoth.domain.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Organization aggregate root. Column names follow the V1 migration (snake_case). */
@Entity
@Table(name = "organization")
public class Organization {

  @Id
  private String id;

  private String name;

  private String createdAt;

  protected Organization() {
  }

  public Organization(String id, String name, String createdAt) {
    this.id = id;
    this.name = name;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getCreatedAt() {
    return createdAt;
  }
}
