package com.mammoth.domain.workitem;

import com.mammoth.domain.web.ApiException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Requirement lifecycle rules (docs/07 §2, docs/specs/M2-PHASE2-PLAN.md §4.2).
 *
 * <p>Transitions are a whitelist in code, not a prompt convention: the service asks this class
 * before touching the entity, and an illegal transition becomes a 409 conflict.
 */
public final class WorkItemStatus {

  public static final String DRAFT = "draft";
  public static final String COLLECTING = "collecting";
  public static final String READY_FOR_ANALYSIS = "ready_for_analysis";
  public static final String ANALYZING = "analyzing";
  public static final String SOLUTION_REVIEW = "solution_review";
  public static final String APPROVED = "approved";
  public static final String ON_HOLD = "on_hold";
  public static final String BLOCKED = "blocked";
  public static final String WITHDRAWN = "withdrawn";
  public static final String REJECTED = "rejected";

  private static final Set<String> PAUSED = Set.of(ON_HOLD, BLOCKED);
  private static final Map<String, Set<String>> ALLOWED = new HashMap<>();

  static {
    ALLOWED.put(DRAFT, Set.of(COLLECTING, ON_HOLD, WITHDRAWN, REJECTED));
    ALLOWED.put(COLLECTING,
        Set.of(READY_FOR_ANALYSIS, ON_HOLD, BLOCKED, WITHDRAWN, REJECTED));
    ALLOWED.put(READY_FOR_ANALYSIS,
        Set.of(ANALYZING, ON_HOLD, BLOCKED, WITHDRAWN, REJECTED));
    ALLOWED.put(ANALYZING, Set.of(SOLUTION_REVIEW, ON_HOLD, BLOCKED, WITHDRAWN, REJECTED));
    ALLOWED.put(SOLUTION_REVIEW, Set.of(APPROVED, ANALYZING, BLOCKED, WITHDRAWN, REJECTED));
    // Resume targets are validated against prior_status, but the set documents what is reachable.
    ALLOWED.put(ON_HOLD,
        Set.of(DRAFT, COLLECTING, READY_FOR_ANALYSIS, ANALYZING, SOLUTION_REVIEW, WITHDRAWN,
            REJECTED));
    ALLOWED.put(BLOCKED,
        Set.of(DRAFT, COLLECTING, READY_FOR_ANALYSIS, ANALYZING, SOLUTION_REVIEW, WITHDRAWN,
            REJECTED));
    // An approved requirement reopens only through "create a new solution version" (AC-5): the
    // approved version itself stays immutable and becomes superseded when the new one is approved.
    ALLOWED.put(APPROVED, Set.of(ANALYZING));
    ALLOWED.put(WITHDRAWN, Set.of());
    ALLOWED.put(REJECTED, Set.of());
  }

  private WorkItemStatus() {
  }

  public static boolean isKnown(String status) {
    return ALLOWED.containsKey(status);
  }

  public static boolean isPaused(String status) {
    return PAUSED.contains(status);
  }

  public static boolean isTerminal(String status) {
    return APPROVED.equals(status) || WITHDRAWN.equals(status) || REJECTED.equals(status);
  }

  /** True when the lifecycle is frozen for content edits (approved/rejected/withdrawn). */
  public static boolean freezesContent(String status) {
    return isTerminal(status);
  }

  /** The status a paused item resumes to (falls back to collecting for legacy rows). */
  public static String resumeTarget(String priorStatus) {
    return priorStatus == null || priorStatus.isBlank() ? COLLECTING : priorStatus;
  }

  /**
   * Validate and describe a transition; throws 409 when the whitelist rejects it.
   *
   * @param from current status
   * @param target requested status
   * @param priorStatus recorded status for on_hold/blocked items (used to validate resume)
   */
  public static void requireTransition(String from, String target, String priorStatus) {
    if (target == null || target.isBlank()) {
      throw ApiException.badRequest("target status must not be empty");
    }
    if (!isKnown(target)) {
      throw ApiException.badRequest("unknown requirement status: " + target);
    }
    if (target.equals(from)) {
      throw ApiException.conflict("requirement is already " + from);
    }
    if (isPaused(from)) {
      boolean leavingPause = !WITHDRAWN.equals(target) && !REJECTED.equals(target);
      if (leavingPause) {
        String expected = resumeTarget(priorStatus);
        if (!target.equals(expected)) {
          throw ApiException.conflict(
              "requirement can only resume to " + expected + " from " + from);
        }
        return;
      }
    }
    Set<String> allowed = ALLOWED.getOrDefault(from, Set.of());
    if (!allowed.contains(target)) {
      throw ApiException.conflict("cannot change requirement status from " + from + " to " + target);
    }
  }
}
