package com.mammoth.domain.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Product master record. */
@Entity
@Table(name = "product")
public class Product {

  @Id
  private String id;

  private String orgId;

  private String name;

  private String status;

  private String createdAt;

  protected Product() {
  }

  public Product(String id, String orgId, String name, String status, String createdAt) {
    this.id = id;
    this.orgId = orgId;
    this.name = name;
    this.status = status;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getOrgId() {
    return orgId;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public String getCreatedAt() {
    return createdAt;
  }
}
