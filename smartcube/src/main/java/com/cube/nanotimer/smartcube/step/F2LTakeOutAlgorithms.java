package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The way an F2L pair with one piece trapped in another slot is put in before advanced F2L: the
 * trapped piece taken out, then the basic case it leaves solved with one of that case's usual
 * algorithms. What the stuck-pair drill shows a solver not yet at the advanced stage.
 *
 * <p><b>Every row is a community algorithm, ranked by its votes</b> (owner, 2026-11-09): of the
 * algorithms {@link F2LCaseAlgorithms} lists for the case, those that read as a take-out, most
 * voted first, at most three, written exactly as listed, rotations kept. Votes stand for ergonomics;
 * length never ranks. A search only checks the shape: some prefix of the algorithm leaves the pair
 * a basic case, and the rest is one of that case's listed algorithms.
 *
 * <p>On 21 of the 33 one-piece cases the most voted algorithm is itself a take-out. On 10 a take-out
 * has votes but the top algorithm is another kind, which the advanced stage teaches. a3a and a23
 * have no voted take-out and are left out, pending the owner. Both pieces in one slot are left out
 * too: one trigger lifts the pair out whole, and it comes up on one pair in seventy.
 *
 * <p>Written for the pair going into front right with the cross down, as every F2L table is.
 * {@code F2LTakeOutAlgorithmsTest} holds the table to that rule.
 */
public final class F2LTakeOutAlgorithms {

  /** Case, then its ways, the most voted first. */
  private static final String[][] ROWS = {
    {"a1", "y' R U' R' y R U' R'", "L' U' L U R U' R'"},
    {"a1a", "y' F' U' F R' U' R", "y' U2 R U' R' U' R' U' R", "y' U' R U R2 U' R U' R' U R"},
    {"a2", "y2 R U' R' L U2 L'", "y' L' U' L y R U' R'", "L U' L' U' R U' R'"},
    {"a2a", "y2 R' F R F' L U2 L'", "y2 R' F R F' U' L U' L'", "y2 F' U' F U' y2 F' U' F"},
    {"a3", "y R U' R' U F' r U r'", "y2 L' U' L2 U2 L'", "R' U2 R2 U' R'"},
    {"a4", "y' F R' F' R U R' U2 R", "y' R U R' U2 f R f'", "U2 L' U L U' R U R'"},
    {"a4a", "y' U R U R2 U2 R", "y2 U R' U R y U R' U2 R", "U L' U L y' U2 R' U R"},
    {"a5", "y2 R U R' U L U L'", "y' r U' r' F R' U2 R", "y' U2 L' U L U2 f R f'"},
    {"a5a", "y' U L' U L R' U2 R"},
    {"a6", "y R U R' F U F'", "y2 U2 L' U L U L U L'", "y F R' F' R L' U L"},
    {"a6a", "y2 U L' U L f' L f", "y U R U R' U' L' U L"},
    {"a7", "U' L' U L R U' R'", "U' L' U' L R U R'", "y' R U' R' U' R U' R' f R f'"},
    {"a7a", "y' U2 R U' R' U2 R' U R", "y' U2 R U' R' U R' U2 R"},
    {"a8", "y U R' U2 R F U F'", "y2 U' R' F R F' U2 L U L'", "y2 R U' R' U L U' L' U L U L'"},
    {"a8a", "y2 U2 R U' R' d L' U L", "y' U2 L F' L' F U2 R' U' R", "y2 R U2 R' f' L' f"},
    {"a9", "U R' U2 R U' R U R'"},
    {"a9a", "y U2 R U' R' L' U L"},
    {"a10", "y' U' R U R2 U' R", "L' U L R U' R' U R U R'"},
    {"a11", "y2 U' R U' R' L U' L'", "y' U' L' U' L R' U' R"},
    {"a12", "y U2 R U R' L' U L", "y2 U' L U L2 U' L2 U L'", "y U' R U R' U2 L' U' L"},
    {"a13", "y' U R' F R F' R' U' R", "U' L F' L' F U2 R U R'"},
    {"a14", "y2 U' F R' F' R U L U L'", "L U L' U' L U L' U R U R'"},
    {"a15", "y2 U' L F' L' F L U L'", "y U R' F R F' U2 L' U' L"},
    {"a16", "y' U' R U' R' U f R' f'", "y' U' R U' R' B' R B R'", "y' U' R U' R' r' U' R U M'"},
    {"a17", "y2 U' R U R' U' f' L' f", "y' U2 F U' F' U R' U' R"},
    {"a18", "y U F' U2 F L' U' L", "y U' R U' R' U2 F' L F L'", "y U' R U' R' F U2 F'"},
    {"a19", "y' U' R U2 R' f R f'", "y' U R U R' U y R U R'", "y2 U R' U R d L' U L"},
    {"a20", "y2 U R U R' L U L'", "y2 U' R U2 R' U' L U L'"},
    {"a21", "y U R U R' U' F U F'"},
    {"a22", "y' R U' R2 U R", "L' U L U2 R U' R'"},
    {"a24", "y R U' R' U2 L' U L", "y2 L' U L2 U' L'"},
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
