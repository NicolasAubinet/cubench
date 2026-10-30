package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Algorithms that put an F2L pair into a back slot from the grip the solver already holds, with no
 * cube rotation: for every basic case, into back right and into back left. What the rotations drill
 * teaches in place of turning the cube to bring the slot to the front.
 *
 * <p>Found by a search over R, L, U, F, D and d turns (no B, no wide moves, no rotations, no d next
 * to a U, which together make a y), at most two turns longer than the case's shortest front-right
 * algorithm where one exists, then picked by hand. Of two otherwise equal, the one with fewer U2
 * turns is first: a U or U' is quicker. A leading d does a rotation's job in one turn, as
 * the front-right sheet's {@code d R' U' R} does.
 *
 * <p>{@code F2LBackSlotAlgorithmsTest} checks every row puts its case's pair into its slot and
 * leaves the cross and the other slots as they were.
 */
public final class F2LBackSlotAlgorithms {

  /** Back right and back left, as a case code spells them. */
  public static final String BACK_RIGHT = "br";
  public static final String BACK_LEFT = "bl";

  /** One row per algorithm: the case, the slot, the algorithm, most recommended first. */
  private static final String[][] ALGORITHMS = {
    {"1", "br", "d L U' L'"},
    {"1", "bl", "U L U' L'"},
    {"2", "br", "U' R' U R"},
    {"2", "bl", "d' R' U R"},
    {"3", "br", "R' U' R"},
    {"3", "bl", "U2 L F U' F' U2 L'"},
    {"4", "br", "U2 R' F' U F U2 R"},
    {"4", "bl", "L U L'"},
    {"5", "br", "R2 F' U' F U R2"},
    {"5", "bl", "U' L U L' U' L U2 L'"},
    {"6", "br", "U R' U' R U R' U2 R"},
    {"6", "bl", "L2 F U F' U' L2"},
    {"7", "br", "d' R U2 R' U' R U2 R'"},
    {"7", "bl", "U' L U2 L' U' L U2 L'"},
    {"8", "br", "U R' U2 R U R' U2 R"},
    {"8", "bl", "d L' U2 L U L' U2 L"},
    {"9", "br", "U R' U' R U' R' U' R"},
    {"9", "bl", "d L' U' L U' L' U' L"},
    {"10", "br", "d' R U R' U R U R'"},
    {"10", "bl", "U' L U L' U L U L'"},
    {"11", "br", "R U2 R2 U' R2 U' R'"},
    {"11", "bl", "U' L U2 L' d L' U' L"},
    {"12", "br", "U R' U2 R d' R U R'"},
    {"12", "bl", "L' U2 L2 U L2 U L"},
    {"13", "br", "U R' U R U' R' U' R"},
    {"13", "bl", "d L' U L U' L' U' L"},
    {"14", "br", "d' R U' R' U R U R'"},
    {"14", "bl", "U' L U' L' U L U L'"},
    {"15", "br", "R' U F' U F U2 R"},
    {"15", "bl", "L U' L U2 L2 U' L2 U' L2"},
    {"16", "br", "R' U R' U2 R2 U R2 U R2"},
    {"16", "bl", "L U' F U' F' U2 L'"},
    {"17", "br", "R' U2 F R U R' U' F' R"},
    {"17", "bl", "L U2 L' U' L U L'"},
    {"18", "br", "R' U2 R U R' U' R"},
    {"18", "bl", "L U2 F' L' U' L U F L'"},
    {"19", "br", "d L d2 R' U R U' R'"},
    {"19", "bl", "U L U2 L' U L U' L'"},
    {"20", "br", "U' R' U2 R U' R' U R"},
    {"20", "bl", "d' R' d2 L U' L' U L"},
    {"21", "br", "U2 R' d' F R U R'"},
    {"21", "bl", "L U' L' U2 L U L'"},
    {"22", "br", "R' U R U2 R' U' R"},
    {"22", "bl", "U2 L d F' L' U' L"},
    {"23", "br", "d' R' U' R U' R' U2 R2 U' R'"},
    {"23", "bl", "U2 L2 U2 L' U' L U' L2"},
    {"24", "br", "U2 R2 U2 R U R' U R2"},
    {"24", "bl", "d L U L' U L U2 L2 U L"},
    {"25", "br", "R U2 F R' F' U2 R'"},
    {"25", "bl", "L' U' L' U' L' U L U L"},
    {"26", "br", "R U R U R U' R' U' R'"},
    {"26", "bl", "L' U2 F' L F U2 L"},
    {"27", "br", "R' U' R d' R U2 R'"},
    {"27", "bl", "L U' L' U L U' L'"},
    {"28", "br", "R' U R U' R' U R"},
    {"28", "bl", "L U L' d L' U2 L"},
    {"29", "br", "R' U' R U R' U' R"},
    {"29", "bl", "d L' U2 L U2 L' U' L"},
    {"30", "br", "d' R U2 R' U2 R U R'"},
    {"30", "bl", "L U L' U' L U L'"},
    {"31", "br", "R' U R d' R U' R'"},
    {"31", "bl", "L U' L' d L' U L"},
    {"32", "br", "R2 U' R2 U' R2 U2 R2"},
    {"32", "bl", "L2 U L2 U L2 U2 L2"},
    {"33", "br", "U R' U2 R U' R' U' R"},
    {"33", "bl", "U' L U' L' U' L U2 L'"},
    {"34", "br", "U R' U R U R' U2 R"},
    {"34", "bl", "U L U L' U2 L U L'"},
    {"35", "br", "d' R U R' d R' U' R"},
    {"35", "bl", "U' L U L' d L' U' L"},
    {"36", "br", "U R' U' R d' R U R'"},
    {"36", "bl", "d L' U' L d' L U L'"},
    {"37", "br", "R' U R d' R U2 R' U' R U2 R'"},
    {"37", "bl", "L U L' U2 L U2 L' d L' U' L"},
    {"38", "br", "R' U2 R' U' R U' R' U2 R2"},
    {"38", "bl", "L2 U2 L' U' L U' L' U2 L'"},
    {"39", "br", "R2 U2 R U R' U R U2 R"},
    {"39", "bl", "L U2 L U L' U L U2 L2"},
    {"40", "br", "R2 F' U' F U R U' R"},
    {"40", "bl", "L U' L U F U' F' L2"},
    {"41", "br", "R' U R' U' F' U F R2"},
    {"41", "bl", "L2 F U F' U' L' U L'"},
  };

  private F2LBackSlotAlgorithms() {
  }

  /** The algorithms for a case into a slot, the recommended one first. Empty for none. */
  public static List<String> forCase(String pairCase, String slot) {
    List<String> algorithms = new ArrayList<String>();
    for (String[] row : ALGORITHMS) {
      if (row[0].equals(pairCase) && row[1].equals(slot)) {
        algorithms.add(row[2]);
      }
    }
    return Collections.unmodifiableList(algorithms);
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
    if (pairCase == null || moves == null || moves.trim().isEmpty()) {
      return false;
    }
    boolean right = BACK_RIGHT.equals(slot);
    if (!right && !BACK_LEFT.equals(slot)) {
      return false;
    }
    try {
      String state = Notation.caseState(moves);
      if (!pairCase.equals(F2LCases.pairCase(state, Cubies.D,
          right ? Cubies.DRB : Cubies.DBL, right ? Cubies.BR : Cubies.BL))) {
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

  /**
   * Whether two spellings turn the same faces, read from the grip the pair was dealt in, with the
   * turns of the top layer off both ends. Never from another grip: the same turns started after a
   * rotation are the rotation this drill is there to take away.
   */
  public static boolean sameTurning(String moves, String other) {
    if (moves == null || other == null) {
      return false;
    }
    List<String> turned = AlgorithmForm.comparable(moves);
    return !turned.isEmpty() && turned.equals(AlgorithmForm.comparable(other));
  }

  static String[][] rows() {
    return ALGORITHMS.clone();
  }
}
