package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Whether an algorithm reads as a stuck piece taken out, then a basic case put in with one of its
 * listed algorithms: some prefix of at most seven turns leaves the pair a basic case, and the rest
 * matches. A turn merged at the seam ({@code R' U2 R2 U' R'}) and a last-layer turn absorbed there
 * are split back apart; a rotation in the prefix is undone before the case is read.
 */
final class TakeOutShape {

  private static final int LONGEST_TAKE_OUT = 7;
  private static final String[] LAYER_TURNS = {"", "U", "U'", "U2"};

  private TakeOutShape() {
  }

  static boolean isTakeOut(String moves) {
    List<String> turns = Arrays.asList(moves.trim().split("\\s+"));
    String state = Notation.caseState(moves);
    for (int k = 1; k <= Math.min(LONGEST_TAKE_OUT, turns.size()); k++) {
      for (String[] split : splits(turns, k)) {
        if (!split[0].isEmpty() && !split[1].isEmpty() && splitsAt(state, split[0], split[1])) {
          return true;
        }
      }
    }
    return false;
  }

  /** The prefix of k turns and the rest, with the seam moved by a layer turn or a part of a turn. */
  private static List<String[]> splits(List<String> turns, int k) {
    List<String[]> splits = new ArrayList<>();
    String head = String.join(" ", turns.subList(0, k));
    String tail = String.join(" ", turns.subList(k, turns.size()));
    for (String layer : LAYER_TURNS) {
      String undo = layer.isEmpty() ? "" : Notation.inverse(layer);
      splits.add(new String[] {(head + " " + layer).trim(), (undo + " " + tail).trim()});
    }
    String last = turns.get(k - 1);
    char face = last.charAt(0);
    if ("RLFBUD".indexOf(face) >= 0) {
      int quarters = last.endsWith("2") ? 2 : last.endsWith("'") ? 3 : 1;
      for (int part = 1; part <= 3; part++) {
        int rest = ((quarters - part) % 4 + 4) % 4;
        if (rest != 0) {
          String before = String.join(" ", turns.subList(0, k - 1));
          splits.add(new String[] {(before + " " + turn(face, part)).trim(),
              (turn(face, rest) + " " + tail).trim()});
        }
      }
    }
    return splits;
  }

  private static boolean splitsAt(String state, String takeOut, String rest) {
    String after = Notation.apply(state, takeOut);
    String rotation = rotations(takeOut);
    if (!rotation.isEmpty()) {
      after = Notation.apply(after, Notation.inverse(rotation));
    }
    String basic = F2LCases.pairCase(after, Cubies.D, Cubies.DFR, Cubies.FR);
    if (basic == null || !F2LCases.isBasic(basic)) {
      return false;
    }
    // The rest is turned in the rotated grip, so it is read with the rotation in front.
    String turned = rotation.isEmpty() ? rest : rotation + " " + rest;
    return F2LCaseAlgorithms.matching(basic, turned) != null;
  }

  private static String rotations(String moves) {
    List<String> rotations = new ArrayList<>();
    for (String token : moves.split(" ")) {
      if (!token.isEmpty() && "xyz".indexOf(token.charAt(0)) >= 0) {
        rotations.add(token);
      }
    }
    return String.join(" ", rotations);
  }

  private static String turn(char face, int quarters) {
    return face + (quarters == 1 ? "" : quarters == 2 ? "2" : "'");
  }
}
