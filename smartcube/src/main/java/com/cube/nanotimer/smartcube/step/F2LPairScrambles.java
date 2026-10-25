package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.step.LastLayerAlgorithms;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A scramble that hands one F2L pair as a named case, for drilling it: the cross down, the pair
 * going into front right, the other slots in, and the last layer scrambled so the case cannot be read
 * off a solved top. Named as a solve names it ({@code "pair_27"}, {@code "pair_a12"}).
 *
 * <p>Built like {@link LastLayerScrambles}: the case's most voted algorithm undone, between two turns
 * of U. A basic case is undone with an algorithm that needs no other slot empty, so only the pair
 * moves; an advanced one has to disturb the slot its piece is trapped in, which is the case itself.
 */
public final class F2LPairScrambles {

  private static final String PAIR_PREFIX = "pair_";

  /** The cubie order of {@code CubieCube}, and so of {@link Cubies#CORNERS} and {@link Cubies#EDGES}. */
  private static final String[] CORNER_NAMES = {
    "URF", "UFL", "ULB", "UBR", "DFR", "DLF", "DBL", "DRB",
  };
  private static final String[] EDGE_NAMES = {
    "UR", "UF", "UL", "UB", "DR", "DF", "DL", "DB", "FR", "FL", "BL", "BR",
  };

  private static final Map<String, Integer> PLACEMENTS = new HashMap<String, Integer>();

  private F2LPairScrambles() {
  }

  /** Every case a scramble can be asked for, basic then advanced. */
  public static List<String> cases() {
    List<String> codes = new ArrayList<String>();
    for (String name : F2LCases.named()) {
      codes.add(PAIR_PREFIX + name);
    }
    return Collections.unmodifiableList(codes);
  }

  /** The cases with a piece trapped in another slot, the ones the stuck-pieces figure counts. */
  public static List<String> stuckCases() {
    List<String> codes = new ArrayList<String>();
    for (String name : F2LCases.named()) {
      if (!F2LCases.isBasic(name)) {
        codes.add(PAIR_PREFIX + name);
      }
    }
    return Collections.unmodifiableList(codes);
  }

  /** The cases with both pieces out of every other slot, which a look-ahead rep is dealt from. */
  public static List<String> basicCases() {
    List<String> codes = new ArrayList<String>();
    for (String name : F2LCases.named()) {
      if (F2LCases.isBasic(name)) {
        codes.add(PAIR_PREFIX + name);
      }
    }
    return Collections.unmodifiableList(codes);
  }

  /**
   * A scramble leaving the given case at front right with the cross on D, or null if there is none
   * for it. Meant for a solved cube.
   */
  public static String forCase(String caseCode, Random random) {
    if (algorithmFor(caseCode) == null) {
      return null;
    }
    String layer = lastLayer(random); // drawn first, so a seeded deal is the one it always was
    return layer + " " + undoing(caseCode, random);
  }

  /**
   * The case's algorithm undone between two turns of U, with no last layer scramble before it:
   * {@link #forCase} without its first half, for a case dealt on top of another.
   */
  public static String undoing(String caseCode, Random random) {
    String algorithm = algorithmFor(caseCode);
    if (algorithm == null) {
      return null;
    }
    return LastLayerScrambles.undoing(algorithm, random.nextInt(4), random.nextInt(4));
  }

  /**
   * A scramble leaving the case an algorithm solves at front right, with no turn of the layer first:
   * the algorithm, leading rotation included, runs at once. Null if the moves cannot be read.
   */
  public static String forAlgorithm(String algorithm, Random random) {
    String undone = LastLayerScrambles.forAlgorithm(algorithm);
    return undone == null ? null : lastLayer(random) + " " + undone;
  }

  /**
   * How many of the 384 placements of a pair's corner and edge (the cross in) read as this case, 0
   * for a code that is not one. What a case is dealt by: a piece in the last layer can be met under
   * four turns of it, so such a case is four times as likely as one with both pieces in slots.
   */
  public static int placements(String caseCode) {
    synchronized (PLACEMENTS) {
      if (PLACEMENTS.isEmpty()) {
        countPlacements();
      }
      Integer count = caseCode == null || !caseCode.startsWith(PAIR_PREFIX) ? null
          : PLACEMENTS.get(caseCode.substring(PAIR_PREFIX.length()));
      return count == null ? 0 : count.intValue();
    }
  }

  private static void countPlacements() {
    for (int corner = 0; corner < 8; corner++) {
      for (int twist = 0; twist < 3; twist++) {
        for (int edge = 0; edge < 12; edge++) {
          if (edge >= Cubies.DR && edge <= Cubies.DB) {
            continue; // the cross
          }
          for (int flip = 0; flip < 2; flip++) {
            String name = F2LCases.pairCase(placed(corner, twist, edge, flip), Cubies.D,
                Cubies.DFR, Cubies.FR);
            Integer count = PLACEMENTS.get(name);
            PLACEMENTS.put(name, count == null ? 1 : count + 1);
          }
        }
      }
    }
  }

  /** A solved cube with only the front-right pair moved: nothing but those two pieces is read. */
  private static String placed(int corner, int twist, int edge, int flip) {
    int[] cp = {0, 1, 2, 3, 4, 5, 6, 7};
    int[] co = new int[8];
    int[] ep = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    int[] eo = new int[12];
    cp[Cubies.DFR] = corner;
    cp[corner] = Cubies.DFR;
    co[corner] = twist;
    ep[Cubies.FR] = edge;
    ep[edge] = Cubies.FR;
    eo[edge] = flip;
    CubieCube cube = new CubieCube();
    cube.fromPermutation(cp, co, ep, eo);
    return cube.toFaceCube();
  }

  /**
   * Whether the cross on {@code crossFace} is built and the pair of the given corner and edge is
   * home, which is all a pair's rep asks: the rest of F2L is not this case's business.
   *
   * @param corner the pair's corner by its faces, {@code "DFR"}, in any order
   * @param edge the pair's edge by its faces, {@code "FR"}, in any order
   */
  public static boolean isPairIn(String facelets, char crossFace, String corner, String edge) {
    int face = "URFDLB".indexOf(crossFace);
    return face >= 0 && Cubies.crossDone(facelets, face)
        && Cubies.inPlace(facelets, Cubies.CORNERS[cornerIndex(corner)])
        && Cubies.inPlace(facelets, Cubies.EDGES[edgeIndex(edge)]);
  }

  private static String algorithmFor(String caseCode) {
    if (caseCode == null || !caseCode.startsWith(PAIR_PREFIX)) {
      return null;
    }
    String name = caseCode.substring(PAIR_PREFIX.length());
    if (!F2LCases.named().contains(name)) {
      return null;
    }
    List<F2LCaseAlgorithms.Algorithm> algorithms = F2LCaseAlgorithms.forCase(name);
    for (F2LCaseAlgorithms.Algorithm algorithm : algorithms) {
      if (algorithm.getEmptySlot() == null || algorithm.getEmptySlot().isEmpty()) {
        return algorithm.getMoves();
      }
    }
    return algorithms.isEmpty() ? null : algorithms.get(0).getMoves();
  }

  /** A random orientation and permutation of the last layer, which leaves F2L alone. */
  private static String lastLayer(Random random) {
    List<String> oll = new ArrayList<String>();
    List<String> pll = new ArrayList<String>();
    for (String code : LastLayerAlgorithms.caseCodes()) {
      (code.startsWith("oll_") ? oll : pll).add(code);
    }
    return LastLayerScrambles.forCase(oll.get(random.nextInt(oll.size())), random) + " "
        + LastLayerScrambles.forCase(pll.get(random.nextInt(pll.size())), random);
  }

  /** A corner by its faces ({@code "DFR"}, any order), in {@code CubieCube}'s numbering. */
  public static int cornerIndex(String faces) {
    return indexOf(CORNER_NAMES, faces);
  }

  /** An edge by its faces ({@code "FR"}, any order), in {@code CubieCube}'s numbering. */
  public static int edgeIndex(String faces) {
    return indexOf(EDGE_NAMES, faces);
  }

  private static int indexOf(String[] names, String piece) {
    for (int i = 0; i < names.length; i++) {
      if (sameFaces(names[i], piece)) {
        return i;
      }
    }
    throw new IllegalArgumentException("Not a piece: " + piece);
  }

  private static boolean sameFaces(String name, String piece) {
    if (name.length() != piece.length()) {
      return false;
    }
    for (int i = 0; i < piece.length(); i++) {
      if (name.indexOf(piece.charAt(i)) < 0) {
        return false;
      }
    }
    return true;
  }
}
