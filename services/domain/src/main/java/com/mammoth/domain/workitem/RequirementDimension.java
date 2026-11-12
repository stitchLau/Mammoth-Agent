package com.mammoth.domain.workitem;

import java.util.List;
import java.util.Set;

/**
 * The 11 readiness dimensions of a manufacturing requirement (docs/07 §4, M2 plan §4.3).
 *
 * <p>The catalog is versioned because snapshots encode which questions were asked; adding or
 * renaming a dimension later must bump {@link #CATALOG_VERSION}.
 */
public final class RequirementDimension {

  public static final int CATALOG_VERSION = 1;

  public static final String BACKGROUND = "background";
  public static final String GOALS = "goals";
  public static final String CURRENT_PROCESS = "current_process";
  public static final String TARGET_PROCESS = "target_process";
  public static final String BUSINESS_RULES = "business_rules";
  public static final String ROLES_AND_SCOPE = "roles_and_scope";
  public static final String EXCEPTIONS = "exceptions";
  public static final String DATA_AND_METRICS = "data_and_metrics";
  public static final String INTEGRATIONS = "integrations";
  public static final String ACCEPTANCE = "acceptance";
  public static final String CONSTRAINTS = "constraints";

  public static final String MISSING = "missing";
  public static final String PARTIAL = "partial";
  public static final String CONFIRMED = "confirmed";
  public static final String INFERRED = "inferred";
  public static final String CONFLICT = "conflict";

  public static final Set<String> STATES =
      Set.of(MISSING, PARTIAL, CONFIRMED, INFERRED, CONFLICT);

  /** Dimensions whose absence always blocks the readiness gate. */
  public static final Set<String> CRITICAL = Set.of(BACKGROUND, GOALS, TARGET_PROCESS, ACCEPTANCE);

  /** Ordered catalog (drives API/UI order and snapshot payload). */
  public static final List<String> ALL = List.of(
      BACKGROUND, GOALS, CURRENT_PROCESS, TARGET_PROCESS, BUSINESS_RULES, ROLES_AND_SCOPE,
      EXCEPTIONS, DATA_AND_METRICS, INTEGRATIONS, ACCEPTANCE, CONSTRAINTS);

  private RequirementDimension() {
  }

  public static boolean isKnown(String dimension) {
    return ALL.contains(dimension);
  }

  public static boolean isCritical(String dimension) {
    return CRITICAL.contains(dimension);
  }

  public static void requireKnown(String dimension) {
    if (!isKnown(dimension)) {
      throw com.mammoth.domain.web.ApiException.badRequest("unknown readiness dimension: " + dimension);
    }
  }

  public static void requireValidState(String state) {
    if (state == null || !STATES.contains(state)) {
      throw com.mammoth.domain.web.ApiException.badRequest("unknown readiness state: " + state);
    }
  }

  /** Uncertain states are the ones a reviewer must be able to see at a glance. */
  public static boolean isUncertain(String state) {
    return MISSING.equals(state) || PARTIAL.equals(state) || CONFLICT.equals(state)
        || INFERRED.equals(state);
  }
}
