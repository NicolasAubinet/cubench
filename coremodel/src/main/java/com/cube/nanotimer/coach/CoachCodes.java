package com.cube.nanotimer.coach;

import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.step.F2lPairCases;
import com.cube.nanotimer.step.LastLayerAlgorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Every label a payload can carry, field by field, and the filter that holds a payload to them.
 *
 * <p>A payload carries no text the solver wrote, only codes and figures, and the coach service
 * writes its codes into a prompt. Checked only for their shape, a label could still be an
 * instruction spelled in snake_case. {@link #clean} removes every entry whose label this app could
 * not have written, so only these words ever reach a prompt. It removes rather than refuses: a
 * label missing from these lists costs one entry of one plan, not the solver's whole plan, and the
 * service logs what it removed so a gap is seen.
 *
 * <p>The step and part families are spelled by the smart-cube step detectors, which this module
 * cannot see; a test in the smartcube module holds every detector's names to {@link #FAMILIES}.
 */
public final class CoachCodes {

  /** The puzzles a payload is written for. */
  public static final Set<String> PUZZLES = set("3x3");

  /** The methods, as {@code CubeMethod} codes are written in lower case. */
  public static final Set<String> METHODS = set("cfop", "roux", "lbl", "bld");

  /**
   * Every step and part family of every method's step detector, and the OLL edge and corner parts
   * of solves recorded before OLL was split by algorithm.
   */
  public static final Set<String> FAMILIES = set(
      "cross", "f2l", "oll", "pll", "pair", "ollalg", "alg", "ollrestart", "pllrestart",
      "edges", "corners",
      "fb", "sb", "cmll", "lse",
      "layer1", "layer2", "ll", "lleo", "llco", "llcp", "llep", "corner", "edge",
      "memo", "execution", "parity", "undo");

  /** The F2L groups. */
  public static final Set<String> F2L_GROUPS =
      set(F2lGroups.BASIC, F2lGroups.STUCK, F2lGroups.BASIC_EARLY, F2lGroups.FIRST,
          F2lGroups.LATER);

  /** The two sides of a last layer step split by looks. */
  public static final Set<String> LOOKS = set(LastLayerLooks.ONE_LOOK, LastLayerLooks.TWO_LOOK);

  /** Every field name a payload is written with, which is what a cited path is made of. */
  static final Set<String> FIELDS = withHeldRotations(
      "aufs", "basic_pairs", "basic_rotated_pairs", "before", "best_ms", "cards", "cases", "code", "codes",
      "comparisons", "count", "cross_moves", "drill", "drill_cases", "drill_count", "drill_mean_ms", "drill_reps",
      "drills", "evidence", "excess_ms", "extra_moves", "f2l_groups", "f2l_habits", "f2l_moves",
      "f2l_rotation_cases", "f2l_rotations", "families", "final_auf", "family_mean_ms", "fumble_mean_ms",
      "fumble_rate", "gap_ms", "known_cases",
      "language", "learning_cases", "ll_habits", "ll_moves", "long_crosses", "mean_ms", "method",
      "moves", "off_list_rate", "oll_looks", "optimal_moves", "pairs", "parts", "path", "pause_ms", "pll_looks", "previous", "puzzle",
      "reason", "recent_mean_ms", "recognition_ms", "rejection_rate", "rotated", "rotated_pairs",
      "rotations", "run", "schema_version", "share", "since", "skip_rate", "slot", "solve",
      "solve_count", "solve_mean_ms", "solves", "solves_since", "stall_ms", "stalls", "std_dev_ms",
      "stop_ms", "time_lost_ms", "to_learn_cases", "turned_moves", "turning", "two_look_count",
      "two_look_pll_count", "type", "usual_moves", "value", "was", "windows", "x_crosses");

  private CoachCodes() {
  }

  /** A last layer case ({@code oll_21}, {@code pll_gb}) or an F2L pair case ({@code pair_27}). */
  public static boolean isCase(String code) {
    return LastLayerAlgorithms.dealsCase(code) || F2lPairCases.isCase(code);
  }

  /** A case, or an F2L pair case dealt into a named slot ({@code pair_27_fl}). */
  public static boolean isDrillCase(String code) {
    return isCase(code) || (F2lPairCases.slotOf(code) != null
        && F2lPairCases.isCase(F2lPairCases.withoutSlot(code)));
  }

  /** Any label a payload or a plan can name. */
  public static boolean isCode(String code) {
    return FAMILIES.contains(code) || F2L_GROUPS.contains(code) || LOOKS.contains(code)
        || CoachPayloadBuilder.SOLVE.equals(code) || isDrillCase(code);
  }

  /** A cited path: field names, codes and indexes, dotted ({@code cases.pll_gb.mean_ms}). */
  public static boolean isPath(String path) {
    if (path.isEmpty()) {
      return false;
    }
    for (String segment : path.split("\\.", -1)) {
      if (!FIELDS.contains(segment) && !isCode(segment) && !segment.matches("[0-9]{1,4}")) {
        return false;
      }
    }
    return true;
  }

  /**
   * Removes, in place, every entry of {@code payload} whose label is not one of these.
   *
   * @return what was removed, one {@code field: label} each, empty for a payload left whole
   * @throws IllegalArgumentException when the puzzle or the method is unknown, which no entry can
   *     be removed to mend
   */
  public static List<String> clean(JSONObject payload) throws JSONException {
    require(payload, "puzzle", PUZZLES);
    require(payload, "method", METHODS);
    List<String> removed = new ArrayList<String>();
    figures(payload, "families", FAMILIES, removed);
    figures(payload, "parts", FAMILIES, removed);
    figures(payload, "ll_moves", FAMILIES, removed);
    figures(payload, "f2l_groups", F2L_GROUPS, removed);
    figures(payload, "oll_looks", LOOKS, removed);
    figures(payload, "pll_looks", LOOKS, removed);
    turning(payload, removed);
    for (String field : Arrays.asList("cases", "comparisons", "ll_habits", "f2l_habits",
        "case_recall")) {
      cases(payload, field, false, removed);
    }
    cases(payload, "drill_cases", true, removed);
    for (String field : Arrays.asList("known_cases", "learning_cases", "to_learn_cases")) {
      labels(payload, field, false, removed);
    }
    solve(payload, removed);
    JSONObject rotations = payload.optJSONObject("f2l_rotations");
    if (rotations != null) {
      rotationCases(rotations, removed);
    }
    JSONObject previous = payload.optJSONObject("previous");
    if (previous != null) {
      previous(previous, removed);
    }
    return removed;
  }

  private static void previous(JSONObject previous, List<String> removed) throws JSONException {
    for (String side : Arrays.asList("before", "since")) {
      JSONObject steps = previous.optJSONObject(side);
      if (steps != null) {
        figures(steps, "families", FAMILIES, removed);
        figures(steps, "f2l_groups", F2L_GROUPS, removed);
        turning(steps, removed);
        solve(steps, removed);
      }
    }
    JSONArray cards = previous.optJSONArray("cards");
    for (int i = 0; cards != null && i < cards.length(); i++) {
      JSONObject card = cards.optJSONObject(i);
      if (card == null || !isReason(card.optString("reason"))) {
        removed.add("previous.cards: " + (card == null ? "?" : card.optString("reason")));
        cards.remove(i--);
        continue;
      }
      labels(card, "codes", false, removed);
      JSONObject drill = card.optJSONObject("drill");
      if (drill != null) {
        if (isDrillType(drill.optString("type"))) {
          labels(drill, "cases", true, removed);
        } else {
          removed.add("previous.cards.drill: " + drill.optString("type"));
          card.remove("drill");
        }
      }
      JSONArray evidence = card.optJSONArray("evidence");
      for (int j = 0; evidence != null && j < evidence.length(); j++) {
        JSONObject cited = evidence.optJSONObject(j);
        String path = cited == null ? "" : cited.optString("path");
        if (!isPath(path)) {
          removed.add("previous.cards.evidence: " + path);
          evidence.remove(j--);
        }
      }
    }
  }

  private static boolean isReason(String code) {
    for (FocusArea.Reason reason : FocusArea.Reason.values()) {
      if (reason.code().equals(code)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isDrillType(String code) {
    for (DrillSpec.Type type : DrillSpec.Type.values()) {
      if (type.code().equals(code)) {
        return true;
      }
    }
    return false;
  }

  private static void require(JSONObject payload, String field, Set<String> known) {
    String value = payload.optString(field, null);
    if (value == null || !known.contains(value)) {
      throw new IllegalArgumentException("Unknown " + field + ": " + value);
    }
  }

  /** The entries of an array of objects whose {@code code} is not in {@code known}. */
  private static void figures(JSONObject owner, String field, Set<String> known,
      List<String> removed) {
    JSONArray array = owner.optJSONArray(field);
    for (int i = 0; array != null && i < array.length(); i++) {
      String code = codeOf(array.opt(i));
      if (!known.contains(code)) {
        removed.add(field + ": " + code);
        array.remove(i--);
      }
    }
  }

  private static void turning(JSONObject owner, List<String> removed) {
    JSONArray array = owner.optJSONArray("turning");
    for (int i = 0; array != null && i < array.length(); i++) {
      String code = codeOf(array.opt(i));
      if (!FAMILIES.contains(code) && !CoachPayloadBuilder.SOLVE.equals(code)) {
        removed.add("turning: " + code);
        array.remove(i--);
      }
    }
  }

  private static void cases(JSONObject owner, String field, boolean drilled,
      List<String> removed) {
    JSONArray array = owner.optJSONArray(field);
    for (int i = 0; array != null && i < array.length(); i++) {
      String code = codeOf(array.opt(i));
      if (!(drilled ? isDrillCase(code) : isCase(code))) {
        removed.add(field + ": " + code);
        array.remove(i--);
      }
    }
  }

  /** The entries of an array of bare labels. */
  private static void labels(JSONObject owner, String field, boolean drilled,
      List<String> removed) {
    JSONArray array = owner.optJSONArray(field);
    for (int i = 0; array != null && i < array.length(); i++) {
      String code = array.optString(i, null);
      boolean known = code != null && (drilled ? isDrillCase(code)
          : "codes".equals(field) ? isCode(code) : isCase(code));
      if (!known) {
        removed.add(field + ": " + code);
        array.remove(i--);
      }
    }
  }

  private static void solve(JSONObject owner, List<String> removed) {
    JSONObject solve = owner.optJSONObject("solve");
    if (solve != null && !CoachPayloadBuilder.SOLVE.equals(solve.optString("code"))) {
      removed.add("solve: " + solve.optString("code"));
      owner.remove("solve");
    }
  }

  private static void rotationCases(JSONObject rotations, List<String> removed) {
    JSONArray array = rotations.optJSONArray("cases");
    for (int i = 0; array != null && i < array.length(); i++) {
      JSONObject item = array.optJSONObject(i);
      String code = codeOf(item);
      String slot = item == null ? null : item.optString("slot", null);
      if (!F2lPairCases.isCase(code) || (slot != null && !F2lPairCases.SLOTS.contains(slot))) {
        removed.add("f2l_rotations.cases: " + code + (slot == null ? "" : " " + slot));
        array.remove(i--);
      }
    }
  }

  private static String codeOf(Object entry) {
    return entry instanceof JSONObject ? ((JSONObject) entry).optString("code", null) : null;
  }

  /** The front and back rotation counts of payload 14, named in {@link F2lRotationFigure.Held}. */
  private static Set<String> withHeldRotations(String... names) {
    Set<String> fields = new LinkedHashSet<String>(Arrays.asList(names));
    fields.addAll(Arrays.asList(F2lRotationFigure.Held.NAMES));
    return Collections.unmodifiableSet(fields);
  }

  private static Set<String> set(String... codes) {
    return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(codes)));
  }
}
