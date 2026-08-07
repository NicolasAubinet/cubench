package com.cube.nanotimer.smartcube.step;

import java.util.HashMap;
import java.util.Map;

/**
 * Which last-layer case a solve was handed: the OLL its F2L left, and the PLL its OLL left. Read off
 * the state at those two milestones — the case is what the solver was looking at when the step began,
 * so it is the state the step <em>started</em> from that names it, not the one it ended on.
 *
 * <p>A case is a class of states rather than one state. The same OLL turns up in four rotations of
 * the last layer, and a PLL in sixteen (the solver may align the layer any of four ways before
 * starting, and the whole cube may be held any of four ways round). So a state is reduced to the
 * lowest of those readings, and that key is what the tables are written against.
 *
 * <p>The tables themselves are keyed by the case rather than by an algorithm, but they were built
 * from algorithms: {@code LastLayerCasesTest} applies the standard algorithm for every named case and
 * checks it lands on the key claimed here, and that the keys account for every last-layer state there
 * is. So a wrong entry cannot pass unnoticed, and the names mean what a speedcuber means by them.
 */
final class LastLayerCases {

  /** The step was already done when it was reached: there was no case to solve. */
  static final String SKIP = "skip";

  /** Corner permutation then edge permutation, each piece written as the slot it belongs in. */
  private static final Map<String, String> PERMUTATIONS = new HashMap<>();

  /** Corner twists then edge flips, 0 for a piece already facing up. */
  private static final Map<String, String> ORIENTATIONS = new HashMap<>();

  static {
    PERMUTATIONS.put("01230123", SKIP);
    PERMUTATIONS.put("01323012", "aa");
    PERMUTATIONS.put("01321230", "ab");
    PERMUTATIONS.put("03211230", "e");
    PERMUTATIONS.put("01322103", "f");
    PERMUTATIONS.put("01323201", "ga");
    PERMUTATIONS.put("01322310", "gb");
    PERMUTATIONS.put("01322031", "gc");
    PERMUTATIONS.put("01321302", "gd");
    PERMUTATIONS.put("01232301", "h");
    PERMUTATIONS.put("01320132", "ja");
    PERMUTATIONS.put("01323120", "jb");
    PERMUTATIONS.put("03212103", "na");
    PERMUTATIONS.put("03210321", "nb");
    PERMUTATIONS.put("01320213", "ra");
    PERMUTATIONS.put("01321023", "rb");
    PERMUTATIONS.put("01320321", "t");
    PERMUTATIONS.put("01230312", "ua");
    PERMUTATIONS.put("01230231", "ub");
    PERMUTATIONS.put("03210132", "v");
    PERMUTATIONS.put("03210213", "y");
    PERMUTATIONS.put("01231032", "z");

    ORIENTATIONS.put("00000000", SKIP);
    ORIENTATIONS.put("12121111", "1");
    ORIENTATIONS.put("11221111", "2");
    ORIENTATIONS.put("02221111", "3");
    ORIENTATIONS.put("01111111", "4");
    ORIENTATIONS.put("02220011", "5");
    ORIENTATIONS.put("01110011", "6");
    ORIENTATIONS.put("02221001", "7");
    ORIENTATIONS.put("01110110", "8");
    ORIENTATIONS.put("01111100", "9");
    ORIENTATIONS.put("02221100", "10");
    ORIENTATIONS.put("02220110", "11");
    ORIENTATIONS.put("01111001", "12");
    ORIENTATIONS.put("02221010", "13");
    ORIENTATIONS.put("01110101", "14");
    ORIENTATIONS.put("02220101", "15");
    ORIENTATIONS.put("01111010", "16");
    ORIENTATIONS.put("01021111", "17");
    ORIENTATIONS.put("00211111", "18");
    ORIENTATIONS.put("00121111", "19");
    ORIENTATIONS.put("00001111", "20");
    ORIENTATIONS.put("12120000", "21");
    ORIENTATIONS.put("11220000", "22");
    ORIENTATIONS.put("00210000", "23");
    ORIENTATIONS.put("00120000", "24");
    ORIENTATIONS.put("01020000", "25");
    ORIENTATIONS.put("01110000", "26");
    ORIENTATIONS.put("02220000", "27");
    ORIENTATIONS.put("00000011", "28");
    ORIENTATIONS.put("00120110", "29");
    ORIENTATIONS.put("00121100", "30");
    ORIENTATIONS.put("00120011", "31");
    ORIENTATIONS.put("00121001", "32");
    ORIENTATIONS.put("00121010", "33");
    ORIENTATIONS.put("00120101", "34");
    ORIENTATIONS.put("01020011", "35");
    ORIENTATIONS.put("01021001", "36");
    ORIENTATIONS.put("01021100", "37");
    ORIENTATIONS.put("01020110", "38");
    ORIENTATIONS.put("01021010", "39");
    ORIENTATIONS.put("01020101", "40");
    ORIENTATIONS.put("00211100", "41");
    ORIENTATIONS.put("00210110", "42");
    ORIENTATIONS.put("00210011", "43");
    ORIENTATIONS.put("00211001", "44");
    ORIENTATIONS.put("00211010", "45");
    ORIENTATIONS.put("00210101", "46");
    ORIENTATIONS.put("11220110", "47");
    ORIENTATIONS.put("11220011", "48");
    ORIENTATIONS.put("11221001", "49");
    ORIENTATIONS.put("11221100", "50");
    ORIENTATIONS.put("11220101", "51");
    ORIENTATIONS.put("11221010", "52");
    ORIENTATIONS.put("12120011", "53");
    ORIENTATIONS.put("12120110", "54");
    ORIENTATIONS.put("12121010", "55");
    ORIENTATIONS.put("12120101", "56");
    ORIENTATIONS.put("00000101", "57");
  }

  private LastLayerCases() {
  }

  /**
   * The OLL a state is at, or null if it is not one to read a case from — the first two layers are
   * not there, so what sits in the last layer is not the last layer's pieces.
   */
  static String orientation(String facelets, int crossFace) {
    return ORIENTATIONS.get(orientationKey(facelets, crossFace));
  }

  /** The PLL a state is at, or null if it is not one to read a case from. */
  static String permutation(String facelets, int crossFace) {
    return PERMUTATIONS.get(permutationKey(facelets, crossFace));
  }

  /** The key a state's orientation reduces to, or null when the last layer cannot be read. */
  static String orientationKey(String facelets, int crossFace) {
    String state = lastLayerUp(facelets, crossFace);
    if (state == null) {
      return null;
    }
    int[] corners = new int[4];
    int[] edges = new int[4];
    char up = Cubies.FACES.charAt(Cubies.U);
    for (int i = 0; i < 4; i++) {
      corners[i] = turnedAway(state, Cubies.CORNERS[i], up);
      edges[i] = turnedAway(state, Cubies.EDGES[i], up);
      if (corners[i] < 0 || edges[i] < 0) {
        return null;
      }
    }
    return lowestReading(corners, edges, false);
  }

  /** The key a state's permutation reduces to, or null when the last layer cannot be read. */
  static String permutationKey(String facelets, int crossFace) {
    String state = lastLayerUp(facelets, crossFace);
    if (state == null) {
      return null;
    }
    int[] corners = new int[4];
    int[] edges = new int[4];
    for (int i = 0; i < 4; i++) {
      corners[i] = Cubies.homeSlotOf(state, Cubies.EDGES.length + i) - Cubies.EDGES.length;
      edges[i] = Cubies.homeSlotOf(state, i);
      if (corners[i] < 0 || corners[i] > 3 || edges[i] < 0 || edges[i] > 3) {
        return null;
      }
    }
    return lowestReading(corners, edges, true);
  }

  /**
   * The state as it reads with the last layer on top, so one set of tables answers for whichever face
   * the cross was built on. Any of the four rotations that lift the face will do: they differ by a
   * quarter turn of the layer, which the keys already read past.
   */
  private static String lastLayerUp(String facelets, int crossFace) {
    if (facelets == null) {
      return null;
    }
    int lastLayer = Cubies.opposite(crossFace);
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      if (FaceletRotations.face(rotation, lastLayer) == Cubies.U) {
        return inFrame(facelets, rotation);
      }
    }
    return null;
  }

  /** The state turned in space, colours renamed with it, so a solved cube stays a solved cube. */
  static String inFrame(String facelets, int rotation) {
    char[] framed = new char[facelets.length()];
    for (int facelet = 0; facelet < framed.length; facelet++) {
      int colour = Cubies.FACES.indexOf(facelets.charAt(facelet));
      framed[FaceletRotations.apply(rotation, facelet)] =
          Cubies.FACES.charAt(FaceletRotations.face(rotation, colour));
    }
    return new String(framed);
  }

  /** How far round the piece its up-colour is turned, or -1 when it carries none. */
  private static int turnedAway(String facelets, int[] piece, char up) {
    for (int i = 0; i < piece.length; i++) {
      if (facelets.charAt(piece[i]) == up) {
        return i;
      }
    }
    return -1;
  }

  /**
   * The lowest of the readings of one case, which is what the tables are keyed by. An alignment turn
   * carries every piece one slot round the layer, and holding the cube a quarter turn further round
   * does that and renames the pieces with it — neither changes the case the solver has to recognise.
   *
   * @param rename true for permutations, whose pieces are named by where they belong and so are
   *     renamed along with the slots; false for orientations, since a piece is twisted just as far
   *     however the cube is held
   */
  private static String lowestReading(int[] corners, int[] edges, boolean rename) {
    String best = null;
    for (int shift = 0; shift < 4; shift++) {
      for (int renamed = 0; renamed < (rename ? 4 : 1); renamed++) {
        char[] key = new char[8];
        for (int i = 0; i < 4; i++) {
          key[i] = digit(corners[(i + shift) % 4] + renamed);
          key[4 + i] = digit(edges[(i + shift) % 4] + renamed);
        }
        String candidate = new String(key);
        if (best == null || candidate.compareTo(best) < 0) {
          best = candidate;
        }
      }
    }
    return best;
  }

  private static char digit(int value) {
    return (char) ('0' + value % 4);
  }
}
