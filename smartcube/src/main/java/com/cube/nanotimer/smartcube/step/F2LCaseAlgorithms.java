package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The algorithms F2L cases are usually solved with, for the 41 basic cases {@link F2LCases} names.
 * A pair turned with none of them is one worth pointing out.
 *
 * <p>Gathered by hand from published algorithm sheets and committed, never fetched. Every algorithm
 * is written for the pair going into front right with the cross down, and a case's rows are in no
 * particular order.
 *
 * <p>Some only work with another slot still empty, and say which: they break that slot's pair, so they
 * are usual for the case but not something to show a solver whose slot is filled.
 *
 * <p>{@code F2LCaseAlgorithmsTest} checks that every row solves the case it is filed under and
 * disturbs no slot but the one it names, which is what makes taking a table off a website safe.
 */
public final class F2LCaseAlgorithms {

  /** One row per algorithm: the case, the algorithm, and the slot it needs empty ("" for none). */
  private static final String[][] ALGORITHMS = {
    {"1", "U R U' R'", ""},
    {"1", "R' F R F'", ""},
    {"1", "U2 R U2 R'", ""},
    {"1", "M' U R U' r'", ""},
    {"1", "y' r' U' R U M'", ""},
    {"1", "y U F' L F L2 U L", ""},
    {"2", "y' U' R' U R", ""},
    {"2", "U' F' U F", ""},
    {"2", "F R' F' R", ""},
    {"2", "y U' L' U L", ""},
    {"2", "d' L' U L", ""},
    {"3", "F' U' F", ""},
    {"3", "y' R' U' R", ""},
    {"3", "y L' U' L", ""},
    {"3", "S U R U' R' S'", ""},
    {"4", "R U R'", ""},
    {"4", "y F U F'", ""},
    {"4", "y' f R f'", ""},
    {"4", "y2 L U L'", ""},
    {"5", "U' R U R' U2 R U' R'", ""},
    {"5", "U' R U R' U R' F R F'", ""},
    {"5", "U' R U R' U' R U2 R'", ""},
    {"5", "U F' R' F' R F", "fl"},
    {"5", "U R F R' F' R'", "br"},
    {"5", "F2 L' U' L U F2", ""},
    {"6", "d R' U' R U2 R' U R", ""},
    {"6", "y' U R' U' R U2 R' U R", ""},
    {"6", "U' r U' R' U R U r'", ""},
    {"6", "y F2 R U R' U' F2", ""},
    {"6", "U' F' R' F R F", "fl"},
    {"6", "U' R F R F' R'", "br"},
    {"6", "y U L' U' L U2 L' U L", ""},
    {"7", "U' R U2 R' U2 R U' R'", ""},
    {"7", "U' R U2 R' U' R U2 R'", ""},
    {"7", "M' U' M U2 r U' r'", ""},
    {"7", "U' R U2 R' U R' F R F'", ""},
    {"8", "d R' U2 R U2 R' U R", ""},
    {"8", "y' U R' U2 R U2 R' U R", ""},
    {"8", "U R' F R F' R' F R F' R U' R'", ""},
    {"8", "r' U2 R2 U R2 U r", ""},
    {"8", "d R' U2 R U R' U2 R", ""},
    {"8", "y U L' U2 L U2 L' U L", ""},
    {"9", "U' R U' R' d R' U' R", ""},
    {"9", "U' R U' R' U F' U' F", ""},
    {"9", "d R' U' R U' R' U' R", ""},
    {"9", "F R U R' U' F' R U' R'", ""},
    {"9", "y F2 U R U' R' F2", ""},
    {"9", "R' U' R F' U' F", "br"},
    {"9", "F U' F2 U' F", "fl"},
    {"10", "U' R U R' U R U R'", ""},
    {"10", "R d' R U R' U2 F'", ""},
    {"10", "U F' U F U' R U R'", ""},
    {"10", "d R' U R d' R U R'", ""},
    {"10", "y2 B2 U' R' U R B2", ""},
    {"10", "F U F' R U R'", "fl"},
    {"10", "R' U R2 U R'", "br"},
    {"10", "U2 R U' R' U' R U R'", ""},
    {"10", "y' U R' U R U' f R f'", ""},
    {"11", "U' R U2 R' d R' U' R", ""},
    {"11", "U' R U2 R' U F' U' F", ""},
    {"11", "y' R U2 R2 U' R2 U' R'", ""},
    {"11", "F' U L' U2 L U2 F", ""},
    {"12", "R U' R' U R U' R' U2 R U' R'", ""},
    {"12", "R U2 R' U2 R U2 R' U2 R U' R'", ""},
    {"12", "R' U2 R2 U R2 U R", ""},
    {"12", "U R U' R' U' R U R' U' R U R'", ""},
    {"12", "R U R' U R' F R F' R U R'", ""},
    {"12", "R' U2 R2 U R'", "br"},
    {"12", "U F' U2 F U' R U R'", ""},
    {"13", "d R' U R U' R' U' R", ""},
    {"13", "y' U R' U R U' R' U' R", ""},
    {"13", "R U' R' U R' F R F' R U' R'", ""},
    {"13", "M' U' R U R' U2 R U' r'", ""},
    {"14", "U' R U' R' U R U R'", ""},
    {"14", "F U' F' R U R'", "fl"},
    {"14", "R U2 R' U2 R U R' U2 R U' R'", ""},
    {"14", "U' R2 D R' U R D' R2", ""},
    {"14", "U2 R2 U R' U R U2 R2", ""},
    {"15", "R' D' R U' R' D R U R U' R'", ""},
    {"15", "F' U F U2 R U R'", ""},
    {"15", "U R' F R F' U R U R'", ""},
    {"15", "R U2 R' U R U R' U R U' R'", ""},
    {"15", "R U R' U2 R U' R' U R U' R'", ""},
    {"15", "R U' R U2 R2 U' R2 U' R2", ""},
    {"15", "U' R2 U2 R U' R' U R' U2 R2", ""},
    {"15", "U2 R U R' U' R U2 R' U R U R'", ""},
    {"15", "U' R' U2 R' U R' U' R U2 R", ""},
    {"15", "U' R' U R U' R U R'", "br"},
    {"15", "U R U' R' D R U' R' D'", "fl"},
    {"15", "R F U F' U' R'", "fl"},
    {"15", "M U r U' r' U' M'", ""},
    {"16", "R U' R' U d R' U' R", ""},
    {"16", "R U' R' U2 F' U' F", ""},
    {"16", "U R F R U R' U' F' R'", ""},
    {"16", "y' U R U2 R U' R U R' U2 R'", ""},
    {"16", "F' R' U' R U F", "br"},
    {"16", "R U' R' U2 y' R' U' R", ""},
    {"16", "U M' U R U' r' U' R U R'", ""},
    {"16", "U F U R U' R' F' R U R'", ""},
    {"17", "R U2 R' U' R U R'", ""},
    {"17", "y2 L U2 L' U' L U L'", ""},
    {"17", "R U R' U' R U2 R' U2 R U R'", ""},
    {"17", "y L F' L' F L' U L U' L' U L", ""},
    {"18", "y' R' U2 R U R' U' R", ""},
    {"18", "y L' U2 L U L' U' L", ""},
    {"18", "R U R' U' R U R' U' F R' F' R", ""},
    {"18", "R' F R F' R U' R' U R U' R'", ""},
    {"18", "F' U2 F U F' U' F", ""},
    {"19", "U R U2 R' U R U' R'", ""},
    {"19", "R U' R' U R U' R' U R U R'", ""},
    {"19", "U R U2 R2 F R F'", ""},
    {"19", "d f R2 f' U f R' f'", ""},
    {"20", "y' U' R' U2 R U' R' U R", ""},
    {"20", "U' R U' R2 F R F' R U' R'", ""},
    {"20", "y' R' U R U' R' U R U' R' U' R", ""},
    {"20", "y U' L' U2 L U' L' U L", ""},
    {"20", "U' F' U2 F U' F' U F", ""},
    {"21", "R U' R' U2 R U R'", ""},
    {"21", "U2 R U R' U R U' R'", ""},
    {"21", "R B U2 B' R'", ""},
    {"21", "y' f R' f' U2 f R f'", ""},
    {"22", "y' R' U R U2 R' U' R", ""},
    {"22", "y' U2 R' U' R U' R' U R", ""},
    {"22", "r U' r' U2 r U r'", ""},
    {"22", "F' L' U2 L F", ""},
    {"22", "y U2 L' U' L U' L' U L", ""},
    {"23", "U2 R2 U2 R' U' R U' R2", ""},
    {"23", "U F R' F' R U R U R'", ""},
    {"23", "U R U' R' U' R U' R' U R U' R'", ""},
    {"23", "R U R' U' U' R U R' U' R U R'", ""},
    {"23", "R U' R' U' R U R' U2 R U R'", ""},
    {"23", "R2 U R' U R U2 R' U' R'", ""},
    {"23", "R U R' U2 R U R' U' R U R'", ""},
    {"23", "R U' R2 D' R U2 R' D R", ""},
    {"24", "R U R' d R' U R U' R' U R", ""},
    {"24", "y' R' U' R U U R' U' R U R' U' R", ""},
    {"24", "y' U2 R2 U2 R U R' U R2", ""},
    {"24", "U' R U R2 F R F' R U' R'", ""},
    {"24", "F U R U' R' F' R U' R'", ""},
    {"24", "R U R' U R U R' U' F R' F' R", ""},
    {"24", "R U R' U R U2 R' F' U2 F", ""},
    {"24", "y' R' U' R U2 R' U' R U R' U' R", ""},
    {"24", "U F' L' U L F R U R'", ""},
    {"25", "U' R' F R F' R U R'", ""},
    {"25", "R' U' R' U' R' U R U R", ""},
    {"25", "U' F' U F U R U' R'", ""},
    {"25", "R' F' R U R U' R' F", ""},
    {"25", "U' F' R U R' U' R' F R", ""},
    {"25", "R U' R' U' R U' R' U R U R'", ""},
    {"25", "U R' U' R' U' R2 U R U R", ""},
    {"25", "U2 R' U' R' U' R U R U R", ""},
    {"25", "R2 U' R' U R2", "br"},
    {"25", "U D' R U' R' D", "br"},
    {"25", "l' U' l U l F' l' F", ""},
    {"26", "y' R U R U R U' R' U' R'", ""},
    {"26", "U R U' R' U' F' U F", ""},
    {"26", "U R U' R' F R' F' R", ""},
    {"26", "y' U' R U R U R2 U' R' U' R'", ""},
    {"26", "y' U2 R U R U R' U' R' U' R'", ""},
    {"26", "r U r' U2 r U r' U2 r U' r'", ""},
    {"26", "U l F' l' F R' F' R", ""},
    {"26", "d R B' R' B R' U' R", ""},
    {"26", "R S' R' U R S R'", ""},
    {"26", "U R U R' U' y L' U' L", ""},
    {"27", "R U' R' U R U' R'", ""},
    {"27", "R U' R2 F R F'", ""},
    {"27", "F' U' F U2 R U' R'", ""},
    {"27", "y' f R' f' U f R' f'", ""},
    {"27", "y' f R' f' r' U' R U M'", ""},
    {"28", "R U R' U' F R' F' R", ""},
    {"28", "R U R' d R' U2 R", ""},
    {"28", "y L' U L U' L' U L", ""},
    {"28", "y' R' U R U' R' U R", ""},
    {"28", "R U R' U F' U2 F", ""},
    {"28", "F' U F U' F' U F", ""},
    {"29", "y' R' U' R U R' U' R", ""},
    {"29", "U2 R U' R' y' R' U' R", ""},
    {"29", "R' F R F' R' F R F'", ""},
    {"29", "R' F R F' U R U' R'", ""},
    {"29", "y L' U' L U L' U' L", ""},
    {"30", "R U R' U' R U R'", ""},
    {"30", "U2 F' U F R U R'", ""},
    {"30", "U' R U2 R' U2 R U R'", ""},
    {"30", "U' F R' F' R2 U R'", ""},
    {"31", "R U' R' d R' U R", ""},
    {"31", "R U' R' U F' U F", ""},
    {"31", "U' R' F R F' R U' R'", ""},
    {"31", "R U2 R' U' F R' F' R", ""},
    {"31", "R U' R' U y' R' U R", ""},
    {"31", "F' U F R U2 R'", ""},
    {"31", "R U' R' U y L' U L", ""},
    {"32", "R U R' U' R U R' U' R U R'", ""},
    {"32", "U R U' R' U R U' R' U R U' R'", ""},
    {"32", "U' R U R' U' R U R' U R U' R'", ""},
    {"32", "R2 U R2 U R2 U2 R2", ""},
    {"32", "U' F R' F' R U' R U R'", ""},
    {"33", "U' R U' R' U2 R U' R'", ""},
    {"33", "y U' L' U' L U2 L' U' L", ""},
    {"33", "u R U' R' u'", "br"},
    {"33", "E F' U' F u", "fl"},
    {"33", "y R' D R U' R' D' R", ""},
    {"33", "R U R' U' R U' R' U R U' R'", ""},
    {"33", "U' R U' R' U' R U2 R'", ""},
    {"34", "U F' U F U2 F' U F", ""},
    {"34", "U' R U2 R' U R U R'", ""},
    {"34", "U R U R' U2 R U R'", ""},
    {"34", "d R' U R U2 R' U R", ""},
    {"34", "y U L' U L U2 L' U L", ""},
    {"34", "U y F U2 R U2 R' F'", ""},
    {"34", "E' R U R' E", "br"},
    {"34", "u' F' U F u", "fl"},
    {"34", "U R' D' R U' R' D R", ""},
    {"35", "U' R U R' U F' U' F", ""},
    {"35", "U' R U R' d R' U' R", ""},
    {"35", "U2 R U' R' U' F' U' F", ""},
    {"35", "U2 R U R' F R' F' R", ""},
    {"35", "U' R U R' U y' R' U' R", ""},
    {"36", "U F' U' F U' R U R'", ""},
    {"36", "U2 R' F R F' U2 R U R'", ""},
    {"36", "y' U R' U' R U' y R U R'", ""},
    {"36", "R U R' U R U R' U' F' U' F", ""},
    {"36", "R2 u R U R' U' u' R' U R'", ""},
    {"37", "R' F R F' R U' R' U R U' R' U2 R U' R'", ""},
    {"37", "R U' R' d R' U2 R U2 R' U R", ""},
    {"37", "R U R' U2 R U2 R' d R' U' R", ""},
    {"37", "R2 U2 F R2 F' U2 R' U R'", ""},
    {"37", "R U R' U' R U2 R' d R' U' R U' R' U R", ""},
    {"37", "R U' R2 U2 R U' F' U F", "br"},
    {"37", "R U' R2 U2 R F R' F' R", "br"},
    {"37", "R U' R2 U2 R U2 F' U2 F", "br"},
    {"37", "R U2 R' U R U2 R' U F' U' F", ""},
    {"37", "R U R' U2 R U2 R' U y' R' U' R", ""},
    {"38", "R U' R' U' R U R' U2 R U' R'", ""},
    {"38", "y' R' U' R U2 R' U R U' R' U' R", ""},
    {"38", "R U R' U' R U2 R' U' R U R'", ""},
    {"38", "R2 U2 R' U' R U' R' U2 R'", ""},
    {"38", "R U' R' U' R U R' U' R U2 R'", ""},
    {"39", "R U' R' U R U2 R' U R U' R'", ""},
    {"39", "R U R' U2 R U' R' U R U R'", ""},
    {"39", "R U2 R U R' U R U2 R2", ""},
    {"39", "y' R2 U2 R U R' U R U2 R", ""},
    {"39", "R U2 R' U R U' R' U R U R'", ""},
    {"40", "r U' r' U2 r U r' R U R'", ""},
    {"40", "R U' R' d R' U' R U' R' U' R", ""},
    {"40", "R U' R' U' R U' R' d R' U' R", ""},
    {"40", "R F U R U' R' F' U' R'", ""},
    {"40", "R U' R2 U' R y' R' U' R y", "br"},
    {"40", "F' L' U2 L F R U R'", ""},
    {"40", "R U' R' F R U R' U' F' R U' R'", ""},
    {"40", "R U' R' U' R U' R' U y' R' U' R", ""},
    {"41", "R U' R' r U' r' U2 r U r'", ""},
    {"41", "R U' R' U d R' U' R U' R' U R", ""},
    {"41", "R U' R' U2 y' R' U' R U' R' U R", ""},
    {"41", "R U R' U' R U' R' U2 F' U' F", ""},
    {"41", "R U F R U R' U' F' R'", ""},
    {"41", "y' R' U R2 U R' y R U R'", "fl"},
    {"41", "R U' R' F' L' U2 L F", ""},
    {"41", "R U R' U' y M U' R' F R U M'", ""},
    {"41", "R U R' U' R U' R' U2 y' R' U' R", ""},
  };

  private F2LCaseAlgorithms() {
  }

  /** The algorithms for a basic case, as {@link F2LCases} names it. */
  public static List<Algorithm> forCase(String pairCase) {
    List<Algorithm> algorithms = new ArrayList<>();
    for (String[] row : ALGORITHMS) {
      if (row[0].equals(pairCase)) {
        algorithms.add(new Algorithm(row[1], row[2].isEmpty() ? null : row[2]));
      }
    }
    return Collections.unmodifiableList(algorithms);
  }

  static String[][] rows() {
    return ALGORITHMS.clone();
  }

  public static final class Algorithm {

    private final String moves;
    private final String emptySlot;

    Algorithm(String moves, String emptySlot) {
      this.moves = moves;
      this.emptySlot = emptySlot;
    }

    public String getMoves() {
      return moves;
    }

    /** The slot ("fl", "br") that has to be empty for this algorithm, or null if none does. */
    public String getEmptySlot() {
      return emptySlot;
    }
  }
}
