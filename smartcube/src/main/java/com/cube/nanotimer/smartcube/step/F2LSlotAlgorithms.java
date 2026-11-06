package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.step.F2lPairCases;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Algorithms that put an F2L pair into a named slot from the grip the solver already holds, with
 * no cube rotation: what the rotations drill teaches in place of turning the cube.
 *
 * <p>Front right is the front table's own ({@link F2LCaseAlgorithms}), less those that need
 * another slot empty (a drill deals one pair with the rest of F2L in) or turn the cube. Front left
 * is each of those mirrored, filed under the case it solves there: the left-hand version every
 * solver learns. The back slots are {@link F2LBackSlotAlgorithms}'.
 */
public final class F2LSlotAlgorithms {

  /** The four slots, as a case code spells them. */
  public static final String FRONT_RIGHT = "fr";
  public static final String FRONT_LEFT = "fl";
  public static final String BACK_RIGHT = F2LBackSlotAlgorithms.BACK_RIGHT;
  public static final String BACK_LEFT = F2LBackSlotAlgorithms.BACK_LEFT;

  /** The four, front first. */
  public static final List<String> SLOTS = F2lPairCases.SLOTS;

  /** A rotation, or a turn of the core about the vertical, which the drill reads as one. */
  private static final Pattern TURNS_THE_CUBE = Pattern.compile("[xyzuE]");

  /** At most this many per case and slot, as in the back table. */
  private static final int PER_SLOT = 3;

  private static Map<String, List<String>> frontRight;
  private static Map<String, List<String>> frontLeft;

  private F2LSlotAlgorithms() {
  }

  public static boolean isSlot(String slot) {
    return FRONT_RIGHT.equals(slot) || FRONT_LEFT.equals(slot) || BACK_RIGHT.equals(slot)
        || BACK_LEFT.equals(slot);
  }

  /** The algorithms for a case into a slot, the recommended one first. Empty for none. */
  public static List<String> forCase(String pairCase, String slot) {
    if (BACK_RIGHT.equals(slot) || BACK_LEFT.equals(slot)) {
      return F2LBackSlotAlgorithms.forCase(pairCase, slot);
    }
    Map<String, List<String>> table = FRONT_RIGHT.equals(slot) ? frontRight()
        : FRONT_LEFT.equals(slot) ? frontLeft() : null;
    List<String> algorithms = table == null ? null : table.get(pairCase);
    return algorithms == null ? Collections.<String>emptyList() : algorithms;
  }

  /**
   * The case held with the cross up, as {@link F2LCaseAlgorithms#crossUpFacelets} draws a front
   * one: turned over with a z2, so a back-right pair goes into up-back-left. Null for no row.
   */
  public static String crossUpFacelets(String pairCase, String slot) {
    List<String> algorithms = forCase(pairCase, slot);
    return algorithms.isEmpty() ? null
        : Notation.caseState("z2 " + algorithms.get(0) + " z2");
  }

  /** Whether the moves put the case's pair into the slot, with the cross and the other slots in. */
  public static boolean solves(String pairCase, String slot, String moves) {
    if (pairCase == null || moves == null || moves.trim().isEmpty() || !isSlot(slot)) {
      return false;
    }
    try {
      String state = Notation.caseState(moves);
      if (!pairCase.equals(caseIn(state, slot))) {
        return false;
      }
      // Only that pair may be out: the algorithm must leave every other slot as it found it.
      String after = Notation.apply(state, moves);
      for (int face = 0; face < 6; face++) {
        if (face == Cubies.U) {
          continue;
        }
        char centre = after.charAt(face * 9 + 4);
        for (int i = face == Cubies.D ? 0 : 3; i < 9; i++) {
          if (after.charAt(face * 9 + i) != centre) {
            return false;
          }
        }
      }
      return true;
    } catch (RuntimeException e) {
      return false; // unreadable, or a sequence that does not put the cube back down
    }
  }

  /** @see F2LBackSlotAlgorithms#sameTurning */
  public static boolean sameTurning(String moves, String other) {
    return F2LBackSlotAlgorithms.sameTurning(moves, other);
  }

  /** The case a state hands the pair of a slot, with the cross on D. */
  private static String caseIn(String state, String slot) {
    int corner = FRONT_RIGHT.equals(slot) ? Cubies.DFR : FRONT_LEFT.equals(slot) ? Cubies.DLF
        : BACK_RIGHT.equals(slot) ? Cubies.DRB : Cubies.DBL;
    int edge = FRONT_RIGHT.equals(slot) ? Cubies.FR : FRONT_LEFT.equals(slot) ? Cubies.FL
        : BACK_RIGHT.equals(slot) ? Cubies.BR : Cubies.BL;
    return F2LCases.pairCase(state, Cubies.D, corner, edge);
  }

  private static synchronized Map<String, List<String>> frontRight() {
    if (frontRight == null) {
      Map<String, List<String>> table = new HashMap<String, List<String>>();
      for (int i = 1; i <= F2LCases.BASIC; i++) {
        String pairCase = String.valueOf(i);
        List<String> kept = new ArrayList<String>();
        for (F2LCaseAlgorithms.Algorithm algorithm : F2LCaseAlgorithms.listed(pairCase)) {
          if (kept.size() < PER_SLOT && algorithm.getEmptySlot() == null
              && !TURNS_THE_CUBE.matcher(algorithm.getMoves()).find()
              && solves(pairCase, FRONT_RIGHT, algorithm.getMoves())) {
            kept.add(algorithm.getMoves());
          }
        }
        table.put(pairCase, Collections.unmodifiableList(kept));
      }
      frontRight = table;
    }
    return frontRight;
  }

  private static synchronized Map<String, List<String>> frontLeft() {
    if (frontLeft == null) {
      Map<String, List<String>> table = new HashMap<String, List<String>>();
      for (int i = 1; i <= F2LCases.BASIC; i++) {
        for (String moves : frontRight().get(String.valueOf(i))) {
          String mirror = AlgorithmForm.mirroredNotation(moves);
          String pairCase = caseIn(Notation.caseState(mirror), FRONT_LEFT);
          if (pairCase == null || !solves(pairCase, FRONT_LEFT, mirror)) {
            continue;
          }
          List<String> kept = table.get(pairCase);
          if (kept == null) {
            kept = new ArrayList<String>();
            table.put(pairCase, kept);
          }
          if (kept.size() < PER_SLOT) {
            kept.add(mirror);
          }
        }
      }
      for (Map.Entry<String, List<String>> entry : table.entrySet()) {
        entry.setValue(Collections.unmodifiableList(entry.getValue()));
      }
      frontLeft = table;
    }
    return frontLeft;
  }
}
