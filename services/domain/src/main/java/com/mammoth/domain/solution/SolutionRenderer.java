package com.mammoth.domain.solution;

import com.mammoth.domain.web.ApiException;
import com.mammoth.domain.workitem.RequirementSnapshot;
import com.mammoth.domain.workitem.WorkItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Renders the single SolutionModel into the business view and the technical view (docs/07 §5).
 *
 * <p>Both views are produced from the same design item tree: the business view shows
 * {@code business} + {@code shared} items, the technical view shows {@code technical} +
 * {@code shared} items plus the task breakdown. There is no view-specific storage, so the views
 * cannot drift by construction.
 */
@Service
public class SolutionRenderer {

  public static final String VIEW_BUSINESS = "business";
  public static final String VIEW_TECHNICAL = "technical";

  public record TraceRow(String goal, String designItems, String tasks, String criteria) {
  }

  public record RenderResult(String view, String markdown, int designItemCount, int taskCount,
      int criterionCount, List<TraceRow> traceability) {
  }

  public RenderResult render(WorkItem item, SolutionVersion version, RequirementSnapshot snapshot,
      List<SolutionDesignItem> items, List<AcceptanceCriterion> criteria, List<SolutionTask> tasks,
      String view) {
    String normalized = normalizeView(view);
    boolean businessView = VIEW_BUSINESS.equals(normalized);

    List<SolutionDesignItem> visibleItems = items.stream()
        .filter(entry -> businessView
            ? !SolutionDesignItem.TECHNICAL.equals(entry.getItemKind())
            : !SolutionDesignItem.BUSINESS.equals(entry.getItemKind()))
        .toList();

    StringBuilder out = new StringBuilder();
    out.append("# ").append(businessView ? "业务方案" : "技术方案").append('：')
        .append(version.getTitle()).append('\n').append('\n');
    out.append("- 需求：").append(item.getItemNo()).append(' ').append(item.getTitle()).append('\n');
    out.append("- 方案版本：v").append(version.getVersion()).append("（")
        .append(version.getStatus()).append("）\n");
    out.append("- 上下文基线：")
        .append(snapshot == null ? "未记录" : "快照 v" + snapshot.getVersion() + "（" + snapshot.getId() + "）")
        .append('\n');
    out.append("- 变更摘要：")
        .append(version.getChangeSummary() == null ? "—" : version.getChangeSummary()).append('\n');
    if (version.getApproverUserId() != null) {
      out.append("- 批准人：").append(version.getApproverUserId())
          .append("，批准时间：").append(version.getApprovedAt()).append('\n');
    }
    out.append('\n');

    out.append("## 1. ").append(businessView ? "业务设计" : "技术设计").append('\n').append('\n');
    // A design item tree may mix kinds (e.g. a technical item nested under the business goal it
    // implements), so a view root is an item whose parent is itself hidden in this view.
    Map<String, List<SolutionDesignItem>> children = new LinkedHashMap<>();
    java.util.Set<String> visibleIds = new java.util.HashSet<>();
    for (SolutionDesignItem entry : visibleItems) {
      visibleIds.add(entry.getId());
    }
    List<SolutionDesignItem> roots = new ArrayList<>();
    for (SolutionDesignItem entry : visibleItems) {
      if (entry.getParentId() == null || !visibleIds.contains(entry.getParentId())) {
        roots.add(entry);
      } else {
        children.computeIfAbsent(entry.getParentId(), key -> new ArrayList<>()).add(entry);
      }
    }
    if (roots.isEmpty()) {
      out.append("（无）\n");
    }
    for (int index = 0; index < roots.size(); index++) {
      appendItem(out, roots.get(index), children, String.valueOf(index + 1), 3);
    }
    out.append('\n');

    out.append("## 2. 验收标准\n").append('\n');
    if (criteria.isEmpty()) {
      out.append("（无）\n");
    }
    for (AcceptanceCriterion criterion : criteria) {
      SolutionDesignItem designItem = findById(items, criterion.getDesignItemId());
      out.append("- ").append(designItem == null ? "" : "[" + designItem.getTitle() + "] ")
          .append(criterion.getText()).append('\n');
    }
    out.append('\n');

    if (!businessView) {
      out.append("## 3. 开发任务\n").append('\n');
      if (tasks.isEmpty()) {
        out.append("（无）\n");
      }
      for (SolutionTask task : tasks) {
        out.append("### ").append(task.getTitle()).append('\n').append('\n');
        SolutionDesignItem designItem = findById(items, task.getDesignItemId());
        if (designItem != null) {
          out.append("- 追溯设计项：").append(designItem.getTitle()).append('\n');
        }
        if (task.getAcceptanceCriterionId() != null) {
          out.append("- 关联验收标准：").append(task.getAcceptanceCriterionId()).append('\n');
        }
        if (task.getDescription() != null) {
          out.append("- 说明：").append(task.getDescription()).append('\n');
        }
        if (task.getDoneWhen() != null) {
          out.append("- 完成条件：").append(task.getDoneWhen()).append('\n');
        }
        if (task.getDependsOnTaskId() != null) {
          SolutionTask dependency = findTask(tasks, task.getDependsOnTaskId());
          out.append("- 依赖任务：")
              .append(dependency == null ? task.getDependsOnTaskId() : dependency.getTitle())
              .append('\n');
        }
        out.append('\n');
      }
    }

    out.append(businessView ? "## 3. 追溯矩阵" : "## 4. 追溯矩阵").append('\n').append('\n');
    List<TraceRow> traceability = traceability(items, criteria, tasks);
    out.append("| 业务目标 | 设计项 | 任务 | 验收标准 |\n|---|---|---|---|\n");
    for (TraceRow row : traceability) {
      out.append("| ").append(row.goal()).append(" | ").append(row.designItems()).append(" | ")
          .append(row.tasks()).append(" | ").append(row.criteria()).append(" |\n");
    }
    if (traceability.isEmpty()) {
      out.append("| （无业务目标） | — | — | — |\n");
    }

    return new RenderResult(normalized, out.toString(), visibleItems.size(),
        businessView ? 0 : tasks.size(), criteria.size(), traceability);
  }

  private void appendItem(StringBuilder out, SolutionDesignItem item,
      Map<String, List<SolutionDesignItem>> children, String numbering, int level) {
    out.append("#".repeat(level)).append(' ').append(numbering).append(' ')
        .append(item.getTitle()).append('\n').append('\n');
    if (item.getBody() != null && !item.getBody().isBlank()) {
      out.append(item.getBody()).append('\n').append('\n');
    }
    List<SolutionDesignItem> nested = children.getOrDefault(item.getId(), List.of());
    for (int index = 0; index < nested.size(); index++) {
      appendItem(out, nested.get(index), children, numbering + "." + (index + 1), level + 1);
    }
  }

  /** One row per root business goal: which design items, tasks and criteria cover it. */
  public List<TraceRow> traceability(List<SolutionDesignItem> items,
      List<AcceptanceCriterion> criteria, List<SolutionTask> tasks) {
    List<TraceRow> rows = new ArrayList<>();
    for (SolutionDesignItem item : items) {
      if (!SolutionDesignItem.BUSINESS.equals(item.getItemKind()) || item.getParentId() != null) {
        continue;
      }
      var subtree = SolutionConsistency.subtreeIds(item, items);
      List<String> designTitles = new ArrayList<>();
      for (SolutionDesignItem entry : items) {
        if (subtree.contains(entry.getId()) && !entry.getId().equals(item.getId())) {
          designTitles.add(entry.getTitle());
        }
      }
      List<String> taskTitles = new ArrayList<>();
      for (SolutionTask task : tasks) {
        if (task.getDesignItemId() != null && subtree.contains(task.getDesignItemId())) {
          taskTitles.add(task.getTitle());
        }
      }
      List<String> criterionTexts = new ArrayList<>();
      for (AcceptanceCriterion criterion : criteria) {
        if (criterion.getDesignItemId() != null && subtree.contains(criterion.getDesignItemId())) {
          criterionTexts.add(criterion.getText());
        }
      }
      rows.add(new TraceRow(item.getTitle(), joinOrDash(designTitles), joinOrDash(taskTitles),
          joinOrDash(criterionTexts)));
    }
    return rows;
  }

  private static String joinOrDash(List<String> values) {
    return values.isEmpty() ? "—" : String.join("；", values);
  }

  private static SolutionDesignItem findById(List<SolutionDesignItem> items, String id) {
    if (id == null) {
      return null;
    }
    for (SolutionDesignItem item : items) {
      if (item.getId().equals(id)) {
        return item;
      }
    }
    return null;
  }

  private static SolutionTask findTask(List<SolutionTask> tasks, String id) {
    for (SolutionTask task : tasks) {
      if (task.getId().equals(id)) {
        return task;
      }
    }
    return null;
  }

  public static String normalizeView(String view) {
    if (view == null || view.isBlank()) {
      return VIEW_BUSINESS;
    }
    String normalized = view.trim().toLowerCase(java.util.Locale.ROOT);
    if (!VIEW_BUSINESS.equals(normalized) && !VIEW_TECHNICAL.equals(normalized)) {
      throw ApiException.badRequest("unsupported solution view: " + view);
    }
    return normalized;
  }
}
