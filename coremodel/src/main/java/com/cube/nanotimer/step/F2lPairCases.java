package com.cube.nanotimer.step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The F2L cases a pair drill can be dealt, under the codes a solve records them with: the 41 basic
 * ones ({@code pair_1} to {@code pair_41}) and the 51 with a piece trapped in another slot
 * ({@code pair_a1} to {@code pair_a42}, and {@code pair_a1a} to {@code pair_a9a}).
 *
 * <p>Here so that a plan can be checked for a dead drill button without the case reader, which lives
 * in {@code smartcube}; {@code DrillCaseVocabularyTest} pins the two lists to each other.
 */
public final class F2lPairCases {

  private static final String PREFIX = "pair_";
  private static final int BASIC = 41;
  private static final int ADVANCED = 42;
  private static final int ADVANCED_TWINS = 9;

  private static final int CORNER_STUCK = 9;
  private static final int EDGE_STUCK_LAST = 24;
  private static final String HARD_TWIN = "pair_a9a";

  private static final List<String> STUCK = stuck();
  private static final List<String> ALL = all();
  private static final List<String> TAKE_OUT = takeOut();
  private static final List<String> EDGE_STUCK = edgeStuck();

  private F2lPairCases() {
  }

  /** Every case, basic then advanced. */
  public static List<String> codes() {
    return ALL;
  }

  /** The cases with neither piece in another slot. */
  public static List<String> basicCodes() {
    return ALL.subList(0, BASIC);
  }

  /** The cases with a piece trapped in another slot. */
  public static List<String> stuckCodes() {
    return STUCK;
  }

  /**
   * The stuck cases a solver short of advanced F2L is dealt: one piece trapped and the other in the
   * last layer, taken out with a trigger. Both trapped together, and a9a, are left out.
   */
  public static List<String> takeOutCodes() {
    return TAKE_OUT;
  }

  /** The cases with the edge trapped and the corner in the last layer, a10 to a24. */
  public static List<String> edgeStuckCodes() {
    return EDGE_STUCK;
  }

  public static boolean isCase(String code) {
    return ALL.contains(code);
  }

  /** The back slots a bare code is dealt into by a drill written for payload 13. */
  public static final List<String> BACK_SLOTS =
      Collections.unmodifiableList(Arrays.asList("br", "bl"));

  /** Every slot a pair can be dealt into, front first, as a code spells them. */
  public static final List<String> SLOTS =
      Collections.unmodifiableList(Arrays.asList("fr", "fl", "br", "bl"));

  /**
   * A case dealt into a named slot, {@code pair_27_fl}: a drill's case of its own, never a solve's,
   * so no per-case figure or case knowledge is read from one.
   */
  public static String inSlot(String code, String slot) {
    return code + "_" + slot;
  }

  /** The slot a code deals its pair into, or null for any other code. */
  public static String slotOf(String code) {
    if (code == null || !code.startsWith(PREFIX)) {
      return null;
    }
    for (String slot : SLOTS) {
      if (code.endsWith("_" + slot)) {
        return slot;
      }
    }
    return null;
  }

  /** The case a slot code deals, {@code pair_27} for {@code pair_27_fl}; any other code as it is. */
  public static String withoutSlot(String code) {
    String slot = slotOf(code);
    return slot == null ? code : code.substring(0, code.length() - slot.length() - 1);
  }

  /** Whether a slot is one in front, either side. */
  public static boolean isFront(String slot) {
    return "fr".equals(slot) || "fl".equals(slot);
  }

  private static List<String> stuck() {
    List<String> codes = new ArrayList<String>();
    for (int i = 1; i <= ADVANCED; i++) {
      codes.add(PREFIX + "a" + i);
      if (i <= ADVANCED_TWINS) {
        codes.add(PREFIX + "a" + i + "a");
      }
    }
    return Collections.unmodifiableList(codes);
  }

  private static List<String> takeOut() {
    List<String> codes = new ArrayList<String>();
    for (String code : STUCK) {
      if (number(code) <= EDGE_STUCK_LAST && !HARD_TWIN.equals(code)) {
        codes.add(code);
      }
    }
    return Collections.unmodifiableList(codes);
  }

  private static List<String> edgeStuck() {
    List<String> codes = new ArrayList<String>();
    for (String code : STUCK) {
      if (number(code) > CORNER_STUCK && number(code) <= EDGE_STUCK_LAST) {
        codes.add(code);
      }
    }
    return Collections.unmodifiableList(codes);
  }

  /** 12 for {@code pair_a12} and for {@code pair_a12a}. */
  private static int number(String stuckCode) {
    return Integer.parseInt(stuckCode.replaceAll("\\D", ""));
  }

  private static List<String> all() {
    List<String> codes = new ArrayList<String>();
    for (int i = 1; i <= BASIC; i++) {
      codes.add(PREFIX + i);
    }
    codes.addAll(STUCK);
    return Collections.unmodifiableList(codes);
  }
}
