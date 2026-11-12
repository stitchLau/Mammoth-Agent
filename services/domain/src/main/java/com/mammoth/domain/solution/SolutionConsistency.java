package com.mammoth.domain.solution;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Six consistency rules that gate the solution review (M2 plan §4.4). The rules are deterministic
 * so that "the business and technical views agree" becomes an executable check instead of a review
 * opinion.
 */
public final class SolutionConsistency {

  public static final String REQUIRED_ITEM_MISSING = "required_item_missing";
  public static final String PARENT_MISSING = "parent_missing";
  public static final String CRITERION_WITHOUT_DESIGN_ITEM = "criterion_without_design_item";
  public static final String TASK_WITHOUT_TRACE = "task_without_trace";
  public static final String GOAL_WITHOUT_ACCEPTANCE = "goal_without_acceptance";
  public static final String CROSS_VERSION_REFERENCE = "cross_version_reference";
  public static final String DEPENDENCY_MISSING = "dependency_missing";
  public static final String HIERARCHY_INVALID = "hierarchy_invalid";

  public static final int MAX_DEPTH = 5;

  public record Issue(String code, String message, String objectId) {
  }

  public record Counts(int businessItems, int technicalItems, int sharedItems,
      int acceptanceCriteria, int tasks, int uncoveredGoals) {
  }

  public record Report(boolean ok, List<Issue> issues, Counts counts) {
  }

  private SolutionConsistency() {
  }

  public static Report validate(List<SolutionDesignItem> items,
      List<AcceptanceCriterion> criteria, List<SolutionTask> tasks) {
    List<Issue> issues = new ArrayList<>();
    Map<String, SolutionDesignItem> byId = new LinkedHashMap<>();
    for (SolutionDesignItem item : items) {
      byId.put(item.getId(), item);
    }
    Set<String> criterionIds = new HashSet<>();
    for (AcceptanceCriterion criterion : criteria) {
      criterionIds.add(criterion.getId());
    }
    Set<String> taskIds = new HashSet<>();
    for (SolutionTask task : tasks) {
      taskIds.add(task.getId());
    }

    long business = items.stream().filter(item -> SolutionDesignItem.BUSINESS.equals(item.getItemKind())).count();
    long technical = items.stream().filter(item -> SolutionDesignItem.TECHNICAL.equals(item.getItemKind())).count();
    long shared = items.stream().filter(item -> SolutionDesignItem.SHARED.equals(item.getItemKind())).count();

    if (business == 0) {
      issues.add(new Issue(REQUIRED_ITEM_MISSING, "at least one business design item is required", null));
    }
    if (technical == 0) {
      issues.add(new Issue(REQUIRED_ITEM_MISSING, "at least one technical design item is required", null));
    }
    if (criteria.isEmpty()) {
      issues.add(new Issue(REQUIRED_ITEM_MISSING, "at least one acceptance criterion is required", null));
    }
    if (tasks.isEmpty()) {
      issues.add(new Issue(REQUIRED_ITEM_MISSING, "at least one development task is required", null));
    }

    for (SolutionDesignItem item : items) {
      if (item.getParentId() != null) {
        if (!byId.containsKey(item.getParentId())) {
          issues.add(new Issue(PARENT_MISSING,
              "design item '" + item.getTitle() + "' references a parent outside this version",
              item.getId()));
        } else if (depth(item, byId) > MAX_DEPTH) {
          issues.add(new Issue(HIERARCHY_INVALID,
              "design item '" + item.getTitle() + "' exceeds the maximum depth of " + MAX_DEPTH,
              item.getId()));
        }
      }
    }

    for (AcceptanceCriterion criterion : criteria) {
      if (criterion.getDesignItemId() == null || !byId.containsKey(criterion.getDesignItemId())) {
        issues.add(new Issue(CRITERION_WITHOUT_DESIGN_ITEM,
            "acceptance criterion '" + criterion.getText() + "' must reference a design item of this version",
            criterion.getId()));
      }
    }

    for (SolutionTask task : tasks) {
      if (isBlank(task.getDesignItemId()) && isBlank(task.getAcceptanceCriterionId())) {
        issues.add(new Issue(TASK_WITHOUT_TRACE,
            "task '" + task.getTitle() + "' must trace to a design item or an acceptance criterion",
            task.getId()));
      }
      if (!isBlank(task.getDesignItemId()) && !byId.containsKey(task.getDesignItemId())) {
        issues.add(new Issue(CROSS_VERSION_REFERENCE,
            "task '" + task.getTitle() + "' references a design item outside this version",
            task.getId()));
      }
      if (!isBlank(task.getAcceptanceCriterionId()) && !criterionIds.contains(task.getAcceptanceCriterionId())) {
        issues.add(new Issue(CROSS_VERSION_REFERENCE,
            "task '" + task.getTitle() + "' references an acceptance criterion outside this version",
            task.getId()));
      }
      if (!isBlank(task.getDependsOnTaskId()) && !taskIds.contains(task.getDependsOnTaskId())) {
        issues.add(new Issue(DEPENDENCY_MISSING,
            "task '" + task.getTitle() + "' depends on a task outside this version",
            task.getId()));
      }
    }

    int uncovered = 0;
    for (SolutionDesignItem item : items) {
      if (!SolutionDesignItem.BUSINESS.equals(item.getItemKind()) || item.getParentId() != null) {
        continue;
      }
      Set<String> subtree = subtreeIds(item, items);
      boolean covered = criteria.stream().anyMatch(criterion -> subtree.contains(criterion.getDesignItemId()));
      if (!covered) {
        uncovered++;
        issues.add(new Issue(GOAL_WITHOUT_ACCEPTANCE,
            "business goal '" + item.getTitle() + "' has no acceptance criterion in its subtree",
            item.getId()));
      }
    }

    return new Report(issues.isEmpty(), issues,
        new Counts((int) business, (int) technical, (int) shared, criteria.size(), tasks.size(), uncovered));
  }

  public static int depth(SolutionDesignItem item, Map<String, SolutionDesignItem> byId) {
    int depth = 1;
    Set<String> visited = new HashSet<>();
    SolutionDesignItem current = item;
    while (current.getParentId() != null && visited.add(current.getId())) {
      SolutionDesignItem parent = byId.get(current.getParentId());
      if (parent == null) {
        return depth;
      }
      depth++;
      current = parent;
      if (depth > MAX_DEPTH + 1) {
        return depth;
      }
    }
    return depth;
  }

  public static Set<String> subtreeIds(SolutionDesignItem root, List<SolutionDesignItem> items) {
    Set<String> result = new HashSet<>();
    result.add(root.getId());
    boolean changed = true;
    while (changed) {
      changed = false;
      for (SolutionDesignItem item : items) {
        if (item.getParentId() != null && result.contains(item.getParentId())
            && result.add(item.getId())) {
          changed = true;
        }
      }
    }
    return result;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
