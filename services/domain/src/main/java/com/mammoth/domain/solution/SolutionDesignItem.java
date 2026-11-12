package com.mammoth.domain.solution;

import com.mammoth.domain.web.ApiException;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Set;

/**
 * A node of the unified SolutionModel. The single item tree is rendered into the business view,
 * the technical view or both ({@code shared}) — there is no separately editable view.
 */
@Entity
@Table(name = "solution_design_item")
public class SolutionDesignItem {

  public static final String BUSINESS = "business";
  public static final String TECHNICAL = "technical";
  public static final String SHARED = "shared";
  public static final Set<String> KINDS = Set.of(BUSINESS, TECHNICAL, SHARED);

  @Id
  private String id;

  private String solutionVersionId;

  private String itemKind;

  private String parentId;

  private String title;

  private String body;

  private int ordinal;

  protected SolutionDesignItem() {
  }

  public SolutionDesignItem(String id, String solutionVersionId, String itemKind, String parentId,
      String title, String body, int ordinal) {
    this.id = id;
    this.solutionVersionId = solutionVersionId;
    this.itemKind = normalizeKind(itemKind);
    this.parentId = parentId;
    this.title = title;
    this.body = body;
    this.ordinal = ordinal;
  }

  public static String normalizeKind(String kind) {
    if (kind == null || kind.isBlank()) {
      throw ApiException.badRequest("design item kind is required (business/technical/shared)");
    }
    String normalized = kind.trim().toLowerCase(Locale.ROOT);
    if (!KINDS.contains(normalized)) {
      throw ApiException.badRequest("unsupported design item kind: " + kind);
    }
    return normalized;
  }

  public void update(String itemKind, String title, String body) {
    this.itemKind = normalizeKind(itemKind);
    this.title = title;
    this.body = body;
  }

  public String getId() {
    return id;
  }

  public String getSolutionVersionId() {
    return solutionVersionId;
  }

  public String getItemKind() {
    return itemKind;
  }

  public String getParentId() {
    return parentId;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public int getOrdinal() {
    return ordinal;
  }
}
