package com.cube.nanotimer.smartcube.step;

import java.util.HashMap;
import java.util.Map;

/**
 * Which F2L case a pair was handed: where its corner and edge were when the solver started on it,
 * read off the state the pair before it left.
 *
 * <p>A case is written against one slot, front right with the cross down, the way F2L cases are
 * taught. So the state is turned until the pair's own slot sits there, which is what makes a back
 * slot's case the same case as a front one's and a pair's case the same whatever face the cross was
 * built on. Turns of the last layer are then read past, since the solver can make one before
 * starting: a case is the class of states those turns connect.
 *
 * <p>Nothing but the pair's two pieces is read. Where the rest of the unsolved pieces sit changes
 * which algorithm is best, not which case it is.
 *
 * <p>Two sets of names. The 41 basic cases have both pieces in the last layer or in their own slot.
 * The advanced ones ("a1" to "a42", with "a1a" to "a9a" beside the first nine) have one piece or both
 * trapped in another unsolved slot: the corner alone, with the edge in the last layer; the edge
 * alone, with the corner in the last layer; or both in the same slot. The pieces can be placed in 168
 * ways the solver can tell apart, and the 75 the two sets leave unnamed read as {@link #OTHER}: a
 * piece in its own slot with the other trapped, the two trapped in different slots, and 3 of the 18
 * trapped edges, which the advanced set lists under the names of three others.
 *
 * <p>{@code F2LCasesTest} holds the table to one algorithm per case, each checked to take its case
 * to a solved pair, and to every placement there is, each under one name at most.
 */
final class F2LCases {

  /** The pair was already in when it was reached: there was no case to solve. */
  static final String SKIP = "skip";

  /** A placement neither set names. */
  static final String OTHER = "other";

  /** The corner's slot and twist, then the edge's slot and flip, as {@link #key} writes them. */
  private static final Map<String, String> CASES = new HashMap<>();

  static {
    CASES.put("4080", SKIP);
    CASES.put("0200", "1");
    CASES.put("0111", "2");
    CASES.put("0221", "3");
    CASES.put("0130", "4");
    CASES.put("0230", "5");
    CASES.put("0121", "6");
    CASES.put("0220", "7");
    CASES.put("0131", "8");
    CASES.put("0231", "9");
    CASES.put("0120", "10");
    CASES.put("0201", "11");
    CASES.put("0110", "12");
    CASES.put("0211", "13");
    CASES.put("0100", "14");
    CASES.put("0210", "15");
    CASES.put("0101", "16");
    CASES.put("0000", "17");
    CASES.put("0011", "18");
    CASES.put("0030", "19");
    CASES.put("0021", "20");
    CASES.put("0020", "21");
    CASES.put("0031", "22");
    CASES.put("0010", "23");
    CASES.put("0001", "24");
    CASES.put("4000", "25");
    CASES.put("4001", "26");
    CASES.put("4100", "27");
    CASES.put("4201", "28");
    CASES.put("4101", "29");
    CASES.put("4200", "30");
    CASES.put("0081", "31");
    CASES.put("0080", "32");
    CASES.put("0280", "33");
    CASES.put("0180", "34");
    CASES.put("0281", "35");
    CASES.put("0181", "36");
    CASES.put("4081", "37");
    CASES.put("4180", "38");
    CASES.put("4280", "39");
    CASES.put("4181", "40");
    CASES.put("4281", "41");
    CASES.put("5100", "a1");
    CASES.put("6100", "a2");
    CASES.put("7100", "a3");
    CASES.put("5200", "a4");
    CASES.put("6200", "a5");
    CASES.put("7200", "a6");
    CASES.put("5000", "a7");
    CASES.put("6000", "a8");
    CASES.put("7000", "a9");
    CASES.put("0290", "a10");
    CASES.put("02a0", "a11");
    CASES.put("02b0", "a12");
    CASES.put("0091", "a13");
    CASES.put("00a0", "a14");
    CASES.put("00b1", "a15");
    CASES.put("0291", "a16");
    CASES.put("02a1", "a17");
    CASES.put("02b1", "a18");
    CASES.put("0191", "a19");
    CASES.put("01a0", "a20");
    CASES.put("01b1", "a21");
    CASES.put("0090", "a22");
    CASES.put("00a1", "a23");
    CASES.put("00b0", "a24");
    CASES.put("5091", "a25");
    CASES.put("60a0", "a26");
    CASES.put("70b1", "a27");
    CASES.put("5190", "a28");
    CASES.put("61a1", "a29");
    CASES.put("71b0", "a30");
    CASES.put("5290", "a31");
    CASES.put("62a1", "a32");
    CASES.put("72b0", "a33");
    CASES.put("5191", "a34");
    CASES.put("61a0", "a35");
    CASES.put("71b1", "a36");
    CASES.put("5291", "a37");
    CASES.put("62a0", "a38");
    CASES.put("72b1", "a39");
    CASES.put("5090", "a40");
    CASES.put("60a1", "a41");
    CASES.put("70b0", "a42");
    CASES.put("5101", "a1a");
    CASES.put("6101", "a2a");
    CASES.put("7101", "a3a");
    CASES.put("5201", "a4a");
    CASES.put("6201", "a5a");
    CASES.put("7201", "a6a");
    CASES.put("5001", "a7a");
    CASES.put("6001", "a8a");
    CASES.put("7001", "a9a");
  }

  private F2LCases() {
  }

  /**
   * The case the pair in the given slot is at, or null if the state is not one to read a case from:
   * the cross is not there, so the pair is not what the solver would be working on.
   *
   * @param corner the pair's corner, as an index into {@link Cubies#CORNERS}
   * @param edge the pair's edge, as an index into {@link Cubies#EDGES}
   */
  static String pairCase(String facelets, int crossFace, int corner, int edge) {
    String key = key(facelets, crossFace, corner, edge);
    if (key == null) {
      return null;
    }
    String name = CASES.get(key);
    return name == null ? OTHER : name;
  }

  /** The key the pair's placement reduces to, or null when it cannot be read. */
  static String key(String facelets, int crossFace, int corner, int edge) {
    if (facelets == null || !Cubies.crossDone(facelets, crossFace)) {
      return null;
    }
    Integer rotation = toFrontRight(crossFace, corner, edge);
    if (rotation == null) {
      return null;
    }
    String state = LastLayerCases.inFrame(facelets, rotation);
    int[] cornerAt = find(state, Cubies.CORNERS, "DFR", 'D');
    int[] edgeAt = find(state, Cubies.EDGES, "FR", 'F');
    if (cornerAt == null || edgeAt == null) {
      return null;
    }
    String best = null;
    for (int turn = 0; turn < 4; turn++) {
      String candidate = "" + digit(turned(cornerAt[0], turn)) + cornerAt[1]
          + digit(turned(edgeAt[0], turn)) + edgeAt[1];
      if (best == null || candidate.compareTo(best) < 0) {
        best = candidate;
      }
    }
    return best;
  }

  /** The rotation that puts the cross down and the pair's slot at front right, or null if none does. */
  private static Integer toFrontRight(int crossFace, int corner, int edge) {
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      if (FaceletRotations.face(rotation, crossFace) == Cubies.D
          && landsOn(rotation, Cubies.CORNERS[corner], Cubies.CORNERS[Cubies.DFR])
          && landsOn(rotation, Cubies.EDGES[edge], Cubies.EDGES[Cubies.FR])) {
        return rotation;
      }
    }
    return null;
  }

  private static boolean landsOn(int rotation, int[] piece, int[] slot) {
    int landed = FaceletRotations.apply(rotation, piece[0]);
    for (int facelet : slot) {
      if (facelet == landed) {
        return true;
      }
    }
    return false;
  }

  /** {slot, index of the marked colour} for the piece with these colours. A last-layer slot lists
   * its up facelet first, so turning the layer leaves the index alone. */
  private static int[] find(String state, int[][] slots, String colours, char marked) {
    for (int slot = 0; slot < slots.length; slot++) {
      int[] piece = slots[slot];
      if (piece.length != colours.length()) {
        continue;
      }
      int index = -1;
      boolean match = true;
      for (int i = 0; i < piece.length && match; i++) {
        char colour = state.charAt(piece[i]);
        match = colours.indexOf(colour) >= 0;
        if (colour == marked) {
          index = i;
        }
      }
      if (match && index >= 0 && distinct(state, piece)) {
        return new int[] {slot, index};
      }
    }
    return null;
  }

  private static boolean distinct(String state, int[] piece) {
    for (int i = 0; i < piece.length; i++) {
      for (int j = i + 1; j < piece.length; j++) {
        if (state.charAt(piece[i]) == state.charAt(piece[j])) {
          return false;
        }
      }
    }
    return true;
  }

  /** A slot after the given turns of the last layer: the first four are that layer, in turning order. */
  private static int turned(int slot, int turns) {
    return slot < 4 ? (slot + turns) % 4 : slot;
  }

  private static char digit(int value) {
    return Character.forDigit(value, 16);
  }
}
