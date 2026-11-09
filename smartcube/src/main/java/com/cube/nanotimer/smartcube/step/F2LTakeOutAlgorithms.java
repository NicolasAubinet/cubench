package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The way an F2L pair with one piece trapped in another slot is put in before advanced F2L: the
 * trapped piece taken out with that slot's three-move trigger, then the basic case it leaves solved
 * with one of its usual algorithms. What the stuck-pair drill shows a solver not yet at the advanced
 * stage, in place of the advanced set's algorithms.
 *
 * <p>Found by a search (2026-11-09): every trigger of the trapped slot after any turn of the last
 * layer, then every algorithm the basic case is listed with by at least ten votes, the moves where
 * they meet merged, and no rotation, B, D, slice or wide turn. Ranked by length, then by the votes
 * of the advanced-set algorithm it turns the same way as (most are one, written with a rotation),
 * then by the basic algorithm's votes; a near tie is settled for the better known basic algorithm.
 *
 * <p>Both pieces in the same slot are left out: one trigger lifts the pair out whole, and a solver
 * meets it on a pair in seventy. So is a9a, the corner twisted in back right, whose shortest such
 * way is fourteen moves: advanced F2L is the answer to it.
 *
 * <p>Written for the pair going into front right with the cross down, as every F2L table is.
 * {@code F2LTakeOutAlgorithmsTest} holds each row to its case and to the slots it may disturb.
 */
public final class F2LTakeOutAlgorithms {

  /** Case, then its ways, the recommended one first. */
  private static final String[][] ROWS = {
    {"a1", "F U' F' R U' R'", "L' U' L U R U' R'"},
    {"a1a", "L' U' L F' U' F", "U2 F U' F' U' F' U' F"},
    {"a2", "L U' L' U' R U' R'"},
    {"a2a", "U2 L U' L' U2 F' U' F"},
    {"a3", "R' U2 R2 U' R'", "R' U' R U' R U' R'"},
    {"a3a", "R' U' R U2 F' U' F", "U R' U2 R U' F' U' F"},
    {"a4", "F U F' U2 R U R'", "U2 L' U L U' R U R'"},
    {"a4a", "U F U2 F2 U F", "U L' U L U2 F' U F"},
    {"a5", "L U L' U R U R'", "U' L U2 L' R U R'"},
    {"a5a", "U L U L' F' U F", "U L U2 L' U' F' U F"},
    {"a6", "U2 R' U R U R U R'"},
    {"a6a", "U R' U R F' U F"},
    {"a7", "U' L' U L R U' R'", "U' L' U' L R U R'"},
    {"a7a", "F U2 F' U F' U' F", "U2 F U' F' U2 F' U F"},
    {"a8", "L U L' R U R' U2 R U' R'", "L U' L' U R U' R' U R U R'"},
    {"a8a", "L U2 L' F' U' F", "U2 L U' L' U F' U F"},
    {"a9", "U R' U2 R U' R U R'", "U' R' U' R U2 R U R'"},
    {"a10", "U' F U F2 U' F", "U2 F U F' U2 F' U F"},
    {"a11", "U' L U' L' R U' R'"},
    {"a12", "R' U R U2 R U' R' U R U R'", "R' U2 R U R U' R' U R U R'"},
    {"a13", "U2 L' U L F' L' U2 L F", "U' F U' F' R U2 R' U' R U R'"},
    {"a14", "U' L U2 L' R U2 R' U R U' R'", "L U' L' R' U2 R2 U R2 U R"},
    {"a15", "U R' U' R F' L' U2 L F", "U R' U R F' U2 F U F' U' F"},
    {"a16", "U' F U' F' U R U' R'", "U' L' U' L U F' U' F"},
    {"a17", "U' L U L' U' F' U' F", "U2 L U' L' U F' U' F"},
    {"a18", "U R' U2 R F' U' F", "U' R' U' R U' F' U' F"},
    {"a19", "U' F U2 F' R U R'", "U F U F' U R U R'"},
    {"a20", "U L U L' R U R'", "U' L U2 L' U' R U R'"},
    {"a21", "U R' U R U' F' U F"},
    {"a22", "F U' F2 U F", "L' U L U2 R U' R'"},
    {"a23", "L U2 L' F' U F", "L U' L' U' F' U F"},
    {"a24", "R' U R2 U' R'", "R' U2 R U' R U' R'"},
  };

  private static final Map<String, List<String>> BY_CASE = byCase();

  private F2LTakeOutAlgorithms() {
  }

  /** The cases taken out this way, as {@link F2LCases} names them ("a1", "a1a"...). */
  public static List<String> cases() {
    return Collections.unmodifiableList(new ArrayList<String>(BY_CASE.keySet()));
  }

  /** A case's ways, the recommended one first; empty for a case not taken out this way. */
  public static List<String> forCase(String pairCase) {
    List<String> ways = BY_CASE.get(pairCase);
    return ways == null ? Collections.<String>emptyList() : ways;
  }

  /** Whether the moves turn one of the case's ways, from any grip. */
  public static boolean isListed(String pairCase, String moves) {
    for (String way : forCase(pairCase)) {
      if (F2LCaseAlgorithms.sameTurning(pairCase, way, moves)) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, List<String>> byCase() {
    Map<String, List<String>> byCase = new LinkedHashMap<String, List<String>>();
    for (String[] row : ROWS) {
      List<String> ways = new ArrayList<String>();
      for (int i = 1; i < row.length; i++) {
        ways.add(row[i]);
      }
      byCase.put(row[0], Collections.unmodifiableList(ways));
    }
    return Collections.unmodifiableMap(byCase);
  }
}
