package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Algorithms that put an F2L pair into a back slot from the grip the solver already holds, with no
 * cube rotation: for every basic case, into back right and into back left. What the rotations drill
 * teaches in place of turning the cube to bring the slot to the front.
 *
 * <p>Up to three per slot: the community's most voted algorithms for that case and slot (votes taken
 * 31 Oct 2026), most votes first, ties to the shorter one. A slice algorithm only leads when no
 * other has half its votes, as slices read less reliably on some cubes. Only algorithms the drill
 * can read are kept: wide moves, B and slices are fine, but no rotation, no u or E (the core turning
 * about the vertical reads as a rotation) and none that ends with the core turned.
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
    {"1", "br", "U f R' f'"},
    {"1", "br", "r' U' R U M'"},
    {"1", "br", "d L U' L'"},
    {"1", "bl", "U L U' L'"},
    {"1", "bl", "L' f U f'"},
    {"1", "bl", "U2 L U2 L'"},
    {"2", "br", "U' R' U R"},
    {"2", "br", "R f' U' f"},
    {"2", "br", "U2 R' U2 R"},
    {"2", "bl", "U' f' L f"},
    {"2", "bl", "l U L' U' M'"},
    {"2", "bl", "d' R' U R"},
    {"3", "br", "R' U' R"},
    {"3", "bl", "f' L' f"},
    {"3", "bl", "U' R U B' U' B R'"},
    {"4", "br", "f R f'"},
    {"4", "br", "U' r R2 U R U' M"},
    {"4", "bl", "L U L'"},
    {"4", "bl", "r B r'"},
    {"5", "br", "U' R' F R U R' U' F' R"},
    {"5", "br", "R2 F' U' F U R2"},
    {"5", "br", "U r' U R U' R' U' r"},
    {"5", "bl", "U' L U L' U2 L U' L'"},
    {"5", "bl", "U' L U L' U' L U2 L'"},
    {"6", "br", "U R' U' R U2 R' U R"},
    {"6", "bl", "U r U' r' U' L U F L'"},
    {"6", "bl", "U' l U' L' U L U l'"},
    {"6", "bl", "U L F' L' U' L U F L'"},
    {"7", "br", "r U2 R2 U' R2 U' r'"},
    {"7", "br", "F R U R2 U' R F'"},
    {"7", "bl", "U' L U2 L' U2 L U' L'"},
    {"7", "bl", "U' L U2 L' U' L U2 L'"},
    {"7", "bl", "M U' M' U2 l U' l'"},
    {"8", "br", "U R' U2 R U R' U2 R"},
    {"8", "br", "U R' U2 R U2 R' U R"},
    {"8", "bl", "l' U2 L2 U L2 U l"},
    {"8", "bl", "d L' U2 L U2 L' U L"},
    {"9", "br", "U R' U' R U' R' U' R"},
    {"9", "bl", "U' L U' L' U f' L' f"},
    {"9", "bl", "d L' U' L U' L' U' L"},
    {"9", "bl", "L2 U F U' F' L2"},
    {"10", "br", "U R' U R U' f R f'"},
    {"10", "br", "R2 U' F' U F R2"},
    {"10", "br", "d' R U R' U R U R'"},
    {"10", "bl", "U' L U L' U L U L'"},
    {"10", "bl", "U2 L U' L' U' L U L'"},
    {"11", "br", "R U2 R2 U' R2 U' R'"},
    {"11", "br", "R' U R U' R' U R U2 R' U R"},
    {"11", "br", "R' U R U' R' U R U R' U2 R"},
    {"11", "bl", "U' L U2 L' U f' L' f"},
    {"11", "bl", "U' L U2 L' d L' U' L"},
    {"11", "bl", "L U2 L' U' l U' l' U2 l U l'"},
    {"12", "br", "U R' U2 R U' f R f'"},
    {"12", "br", "f R' U R2 U' R2 f'"},
    {"12", "br", "U R' U2 R d' R U R'"},
    {"12", "bl", "L' U2 L2 U L2 U L"},
    {"12", "bl", "d L' U2 L U' F U F'"},
    {"12", "bl", "U2 R' U2 R U2 L U L' U2 R' U R"},
    {"13", "br", "U R' U R U' R' U' R"},
    {"13", "br", "U R' U R U' R' U R U R' U R"},
    {"13", "bl", "d L' U L U' L' U' L"},
    {"13", "bl", "U f' L f U' f' L' f"},
    {"13", "bl", "U L U' L F' L2 U' L U F U L'"},
    {"14", "br", "d' R U' R' U R U R'"},
    {"14", "br", "U' f R' f' U f R f'"},
    {"14", "br", "M U R' U' R U2 R' U r"},
    {"14", "bl", "U' L U' L' U L U L'"},
    {"14", "bl", "U' L U' L' U L U' L' U' L U' L'"},
    {"15", "br", "R' U R U2 f R f'"},
    {"15", "br", "R2 F R F' R U R' U2 R"},
    {"15", "br", "R2 F R F' R U2 R' U R"},
    {"15", "bl", "L U L' U2 L U' L' U L U' L'"},
    {"15", "bl", "M' U l U' l' U' M"},
    {"15", "bl", "f' L f U2 L U L'"},
    {"16", "br", "R' U' R U2 R' U R U' R' U R"},
    {"16", "br", "M' U' r' U r U M"},
    {"16", "br", "U2 R' U' R U R D R' U' R D' R'"},
    {"16", "bl", "L U' L' U2 f' L' f"},
    {"16", "bl", "L2 F' L' F L' U2 L U' L'"},
    {"17", "br", "R' U2 F R U R' U' F' R"},
    {"17", "br", "l U' R' U l' U R U' R' U R"},
    {"17", "bl", "L U2 L' U' L U L'"},
    {"17", "bl", "L U L' U' L U2 L' U2 L U L'"},
    {"18", "br", "R' U2 R U R' U' R"},
    {"18", "bl", "U F U R U' R' F' L U L'"},
    {"18", "bl", "L U2 F' L' U' L U F L'"},
    {"18", "bl", "L U2 L' U2 l U' L' U L U l'"},
    {"19", "br", "U R' F' U2 F R U R' U' R"},
    {"19", "br", "d L U2 L' U L U' L'"},
    {"19", "br", "U2 f R2 U R2 U' R f'"},
    {"19", "bl", "U L U2 L' U L U' L'"},
    {"19", "bl", "L U' L' U L U' L' U L U L'"},
    {"20", "br", "U' R' U2 R U' R' U R"},
    {"20", "br", "R' U R U' R' U R U' R' U' R"},
    {"20", "bl", "U' L F U2 F' L' U' L U L'"},
    {"20", "bl", "d' R' U2 R U' R' U R"},
    {"20", "bl", "U' L U L' U l U' l' U2 l U l'"},
    {"21", "br", "r' U r U2 r' U' r"},
    {"21", "br", "f U R2 U' f'"},
    {"21", "br", "U f U R U' R f'"},
    {"21", "bl", "L U' L' U2 L U L'"},
    {"21", "bl", "U2 L U L' U L U' L'"},
    {"21", "bl", "L F U2 F' L'"},
    {"22", "br", "R' U R U2 R' U' R"},
    {"22", "br", "U2 R' U' R U' R' U R"},
    {"22", "br", "R' F' U2 F R"},
    {"22", "bl", "l U' l' U2 l U l'"},
    {"22", "bl", "f' U' L2 U f"},
    {"23", "br", "U R' F R' F' R2 U' R' U R"},
    {"23", "br", "U2 l' U' L U' L' U2 B' l"},
    {"23", "br", "R' F' U' F U2 R U' R' U' R"},
    {"23", "bl", "U L U' L' U' L U' L' U L U' L'"},
    {"23", "bl", "L U L' U2 L U L' U' L U L'"},
    {"23", "bl", "U2 L2 U2 L' U' L U' L2"},
    {"24", "br", "R' U' R U2 R' U' R U R' U' R"},
    {"24", "br", "U2 R2 U2 R U R' U R2"},
    {"24", "br", "R U R' U R U2 R2 U2 R"},
    {"24", "bl", "U2 r U R' U R U2 B r'"},
    {"24", "bl", "U' L F' L F L2 U L U' L'"},
    {"24", "bl", "U2 L F U F2 L F L2"},
    {"25", "br", "U' R' U M U' R U M'"},
    {"25", "br", "R' S' R U' R' S R"},
    {"25", "br", "U' R' U R r' U' R U M'"},
    {"25", "bl", "R D' R' U' R D R' L U L'"},
    {"25", "bl", "L U' L' U' L U' L' U L U L'"},
    {"25", "bl", "U' f' L' f U L U L'"},
    {"26", "br", "U f R f' U' R' U' R"},
    {"26", "br", "R' U R U R' U R U' R' U' R"},
    {"26", "br", "R U R U R U' R' U' R'"},
    {"26", "bl", "L S L' U L S' L'"},
    {"26", "bl", "U L U' M U L' U' M'"},
    {"26", "bl", "U2 F' L2 U' L2 U L2 F"},
    {"27", "br", "R' U2 R' F R F' R"},
    {"27", "br", "R' U' R U r' U' R U M'"},
    {"27", "br", "R' U' R U f' U f R'"},
    {"27", "bl", "L U' L' U L U' L'"},
    {"28", "br", "R' U R U' R' U R"},
    {"28", "bl", "L U2 L F' L' F L'"},
    {"28", "bl", "L U2 L' U f' L f"},
    {"28", "bl", "L U L' U' l U L' U' M'"},
    {"29", "br", "R' U' R U R' U' R"},
    {"29", "br", "U R' U2 R U2 R' U' R"},
    {"29", "bl", "U2 L U' L' f' L' f"},
    {"29", "bl", "f' L' f U f' L' f"},
    {"30", "br", "f R f' U' f R f'"},
    {"30", "br", "U2 R' U R f R f'"},
    {"30", "bl", "L U L' U' L U L'"},
    {"30", "bl", "U' L U2 L' U2 L U L'"},
    {"31", "br", "R' U R' F R F' R"},
    {"31", "br", "R' U R f R2 f'"},
    {"31", "bl", "L U' L F' L' F L'"},
    {"31", "bl", "f' L f U' L U' L'"},
    {"31", "bl", "B' U B L U2 L'"},
    {"32", "br", "U' R' U R U' R' U R U' R' U R"},
    {"32", "br", "R' U' R U R' U' R U R' U' R"},
    {"32", "br", "U2 f R' U R U' R f'"},
    {"32", "bl", "L U L' U' L U L' U' L U L'"},
    {"32", "bl", "U L U' L' U L U' L' U L U' L'"},
    {"32", "bl", "L2 U L2 U L2 U2 L2"},
    {"33", "br", "U' R D R' U R D' R'"},
    {"33", "br", "U R' U2 R U' R' U' R"},
    {"33", "br", "U' R' U' R U2 R' U' R"},
    {"33", "bl", "U' L U' L' U2 L U' L'"},
    {"33", "bl", "U' L U' L' U' L U2 L'"},
    {"34", "br", "U R' U R U R' U2 R"},
    {"34", "bl", "U L U L' U2 L U L'"},
    {"34", "bl", "U L' D' L U' L' D L"},
    {"34", "bl", "U' L U2 L' U L U L'"},
    {"35", "br", "U' f R f' U R' U' R"},
    {"35", "br", "R' F R' F' R U R U' R' U' R"},
    {"35", "br", "U2 f R f' U' R' U R"},
    {"35", "bl", "U' L U L' U f' L' f"},
    {"35", "bl", "U2 L U L' U' L F U F' L'"},
    {"35", "bl", "U2 L U M U L' U' M'"},
    {"36", "br", "U R' U' R U' f R f'"},
    {"36", "br", "U2 R' U' R U R' F' U' F R"},
    {"36", "bl", "U f' L' f U' L U L'"},
    {"36", "bl", "U2 f' L' f U L U' L'"},
    {"36", "bl", "L F' L F L' U' L' U L U L'"},
    {"37", "br", "R' U R r U2 R2 U' R2 U' r'"},
    {"37", "br", "R' U2 R U' R' U2 R U' f R f'"},
    {"37", "br", "R' U R f R U R2 U' R f'"},
    {"37", "bl", "L U' L' l' U2 L2 U L2 U l"},
    {"37", "bl", "f' L f U' L U2 L' U2 L U' L'"},
    {"37", "bl", "L U2 L' U L U2 L' U f' L' f"},
    {"38", "br", "R' U' R U2 R' U R U' R' U' R"},
    {"38", "br", "R' U R U' R' U2 R U' R' U R"},
    {"38", "bl", "L U L' U' L U2 L' U' L U L'"},
    {"38", "bl", "L2 U2 L' U' L U' L' U2 L'"},
    {"38", "bl", "L U' L' U' L U L' U' L U2 L'"},
    {"39", "br", "R' U' R U R' U2 R U R' U' R"},
    {"39", "br", "R' U R U R' U' R U2 R' U R"},
    {"39", "br", "f R2 U R' U' F R' f' U F'"},
    {"39", "bl", "L U L' U2 L U' L' U L U L'"},
    {"39", "bl", "L U' L' U L U2 L' U L U' L'"},
    {"39", "bl", "L U2 L' U L U' L' U L U L'"},
    {"40", "br", "R' U R r' U r U2 r' U' r"},
    {"40", "br", "R' F' U2 F R f R f'"},
    {"40", "br", "R' U R f U R2 U' f'"},
    {"40", "bl", "l U' l' U2 l U l' L U L'"},
    {"40", "bl", "f' L f L F U2 F' L'"},
    {"40", "bl", "L U' L U F U' F' L2"},
    {"41", "br", "r' U r U2 r' U' r R' U' R"},
    {"41", "br", "R' U R' U' F' U F R2"},
    {"41", "br", "f R' f' U2 R' U' R U' R' U R"},
    {"41", "bl", "f' L f U' L U L' U L U L'"},
    {"41", "bl", "L2 F U F' U' L' U L'"},
    {"41", "bl", "L F U2 F' L' f' L' f"},
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
