package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which CMLL a Roux solve was handed: the last-layer corners its second block left, read against
 * the blocks rather than the centres.
 *
 * <p>A case is the corners only, up to the turn of the layer before the algorithm and the one after
 * it, which leaves 42 cases counting the skip. The edges and the middle slice are the last step's
 * business, so they are never read, and an algorithm may leave the slice anywhere: a CMLL algorithm
 * written with an {@code r} and no {@code r'} solves its case all the same.
 *
 * <p>Names and groups are SpeedCubeDB's ("Sune Left Bar"), the ones the algorithm table came with.
 * The keys are checked by {@code CmllCasesTest}: every algorithm of the table lands on the key of
 * the case it is filed under, and the 42 keys account for every corner state there is.
 */
public final class CmllCases {

  public static final String PREFIX = "cmll_";

  /** The corners were already solved when the blocks were: there was no case. */
  public static final String SKIP = LastLayerCases.SKIP;

  /** Code, group, the case's name within its group. In SpeedCubeDB's order, group by group. */
  private static final String[][] CASES = {
    {"oadjacent", "O", "Adjacent"},
    {"odiagonal", "O", "Diagonal"},
    {"hcolumns", "H", "Columns"},
    {"hrows", "H", "Rows"},
    {"hcolumn", "H", "Column"},
    {"hrow", "H", "Row"},
    {"pirightbar", "Pi", "Right Bar"},
    {"pidownslash", "Pi", "Down Slash"},
    {"pix", "Pi", "X"},
    {"piupslash", "Pi", "Up Slash"},
    {"picolumns", "Pi", "Columns"},
    {"pileftbar", "Pi", "Left Bar"},
    {"uupslash", "U", "Up Slash"},
    {"udownslash", "U", "Down Slash"},
    {"ubottomrow", "U", "Bottom Row"},
    {"urows", "U", "Rows"},
    {"ux", "U", "X"},
    {"uupperrow", "U", "Upper Row"},
    {"tleftbar", "T", "Left Bar"},
    {"trightbar", "T", "Right Bar"},
    {"trows", "T", "Rows"},
    {"tbottomrow", "T", "Bottom Row"},
    {"ttoprow", "T", "Top Row"},
    {"tcolumns", "T", "Columns"},
    {"suneleftbar", "Sune", "Left Bar"},
    {"sunex", "Sune", "X"},
    {"suneupslash", "Sune", "Up Slash"},
    {"sunecolumns", "Sune", "Columns"},
    {"sunerightbar", "Sune", "Right Bar"},
    {"sunedownslash", "Sune", "Down Slash"},
    {"antisunerightbar", "Anti Sune", "Right Bar"},
    {"antisunecolumns", "Anti Sune", "Columns"},
    {"antisunedownslash", "Anti Sune", "Down Slash"},
    {"antisunex", "Anti Sune", "X"},
    {"antisuneupslash", "Anti Sune", "Up Slash"},
    {"antisuneleftbar", "Anti Sune", "Left Bar"},
    {"lbest", "L", "Best"},
    {"lgood", "L", "Good"},
    {"lpure", "L", "Pure"},
    {"lfrontcommutator", "L", "Front Commutator"},
    {"ldiagonal", "L", "Diagonal"},
    {"lbackcommutator", "L", "Back Commutator"},
  };

  /** Corner permutation then twists, each corner read in the U layer's turning order. */
  private static final Map<String, String> KEYS = new HashMap<String, String>();

  private static final Map<String, String[]> BY_CODE = new HashMap<String, String[]>();
  private static final List<String> CODES;
  private static final List<String> GROUPS;

  /** The pieces of the two blocks, which every reading needs home. */
  private static final int[][] BLOCKS = {
    Cubies.CORNERS[Cubies.DLF], Cubies.CORNERS[Cubies.DBL],
    Cubies.EDGES[Cubies.DL], Cubies.EDGES[Cubies.FL], Cubies.EDGES[Cubies.BL],
    Cubies.CORNERS[Cubies.DFR], Cubies.CORNERS[Cubies.DRB],
    Cubies.EDGES[Cubies.DR], Cubies.EDGES[Cubies.FR], Cubies.EDGES[Cubies.BR],
  };

  /** What an algorithm solves, worked out once per spelling: a list asks it of every grip. */
  private static final Map<String, String> SOLVED_BY = new ConcurrentHashMap<String, String>();

  private static final String NONE = "";

  static {
    String[] keys = { // in CASES order
      "01320000", "03210000", "01231212", "03211212", "01321212", "01322121",
      "01231122", "01322211", "01322112", "01321122", "01321221", "03211122",
      "01320210", "01321002", "01230021", "01320021", "01322100", "03210021",
      "01322001", "01320120", "01230012", "01320012", "01321200", "03210012",
      "01230222", "01322022", "01320222", "01322220", "03210222", "01322202",
      "01230111", "01321101", "01321011", "01320111", "01321110", "03210111",
      "01320201", "01321020", "01230102", "01320102", "03210102", "01322010",
    };
    List<String> codes = new ArrayList<String>();
    List<String> groups = new ArrayList<String>();
    for (int i = 0; i < CASES.length; i++) {
      String code = PREFIX + CASES[i][0];
      codes.add(code);
      BY_CODE.put(code, CASES[i]);
      if (!groups.contains(CASES[i][1])) {
        groups.add(CASES[i][1]);
      }
      KEYS.put(keys[i], CASES[i][0]);
    }
    KEYS.put("01230000", SKIP);
    CODES = Collections.unmodifiableList(codes);
    GROUPS = Collections.unmodifiableList(groups);
  }

  private CmllCases() {
  }

  /** Every case code, {@code cmll_suneleftbar}, in the table's order. */
  public static List<String> codes() {
    return CODES;
  }

  /** The groups in order, "O" to "L", each named as it is written. */
  public static List<String> groups() {
    return GROUPS;
  }

  /** Whether the code is a CMLL case, or a CMLL that was skipped. */
  public static boolean isCase(String code) {
    return code != null && (BY_CODE.containsKey(code) || (PREFIX + SKIP).equals(code));
  }

  /** The case's group, "Sune", or null for a code that is not a case. */
  public static String group(String code) {
    String[] row = BY_CODE.get(code);
    return row == null ? null : row[1];
  }

  /** The case's name within its group, "Left Bar", or null. */
  public static String variant(String code) {
    String[] row = BY_CODE.get(code);
    return row == null ? null : row[2];
  }

  /** The case's whole name, "Sune Left Bar", or null. */
  public static String fullName(String code) {
    String[] row = BY_CODE.get(code);
    return row == null ? null : row[1] + " " + row[2];
  }

  /**
   * The case a Roux solve is at, read in the solver's frame: {@code orientation} carries the
   * standard blocks to where they were built, and {@code drift} is the turn of the middle slice
   * the cube reports as the rest of it turning. Null when the blocks are not both home.
   */
  static String handed(String facelets, int orientation, int drift) {
    if (facelets == null) {
      return null;
    }
    int[] colourOf = new int[6];
    for (int face = 0; face < 6; face++) {
      colourOf[FaceletRotations.face(orientation, face)] = face;
    }
    char[] framed = new char[facelets.length()];
    for (int home = 0; home < framed.length; home++) {
      char colour = facelets.charAt(
          FaceletRotations.apply(drift, FaceletRotations.apply(orientation, home)));
      framed[home] = Cubies.FACES.charAt(colourOf[Cubies.FACES.indexOf(colour)]);
    }
    return of(new String(framed));
  }

  /** The case a state is at, blocks on L and R and the layer on U, or null when it is not one. */
  static String of(String state) {
    String key = key(state);
    return key == null ? null : KEYS.get(key);
  }

  /** The key a state reduces to, or null when the blocks are not home. */
  static String key(String state) {
    for (int[] piece : BLOCKS) {
      if (!Cubies.inPlace(state, piece)) {
        return null;
      }
    }
    int[] corners = new int[4];
    int[] twists = new int[4];
    char up = Cubies.FACES.charAt(Cubies.U);
    for (int i = 0; i < 4; i++) {
      corners[i] = Cubies.homeSlotOf(state, Cubies.EDGES.length + i) - Cubies.EDGES.length;
      twists[i] = -1;
      for (int facelet = 0; facelet < 3; facelet++) {
        if (state.charAt(Cubies.CORNERS[i][facelet]) == up) {
          twists[i] = facelet;
        }
      }
      if (corners[i] < 0 || corners[i] > 3 || twists[i] < 0) {
        return null;
      }
    }
    String best = null;
    for (int before = 0; before < 4; before++) {
      for (int after = 0; after < 4; after++) {
        char[] key = new char[8];
        for (int i = 0; i < 4; i++) {
          key[i] = (char) ('0' + (corners[(i + before) % 4] + after) % 4);
          key[4 + i] = (char) ('0' + twists[(i + before) % 4]);
        }
        String candidate = new String(key);
        if (best == null || candidate.compareTo(best) < 0) {
          best = candidate;
        }
      }
    }
    return best;
  }

  /**
   * The state an algorithm solves, blocks home and colours standard, or null when it is not one
   * that keeps the blocks. The cube it ends on is found among the 24 ways to stand a solved one,
   * since an algorithm may carry a rotation; the slice it leaves turned is the next step's.
   */
  static String caseState(String algorithm) {
    String undo = Notation.inverse(algorithm);
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      char[] turned = new char[Cubies.SOLVED.length()];
      for (int facelet = 0; facelet < turned.length; facelet++) {
        turned[FaceletRotations.apply(rotation, facelet)] = Cubies.SOLVED.charAt(facelet);
      }
      String state = Notation.apply(new String(turned), undo);
      if (key(state) != null) {
        return state;
      }
    }
    return null;
  }

  /** The case an algorithm solves, without the prefix, or null for one that solves none. */
  static String solvedBy(String algorithm) {
    String found = SOLVED_BY.get(algorithm);
    if (found == null) {
      String state;
      try {
        state = caseState(algorithm);
      } catch (RuntimeException e) {
        state = null; // unreadable notation
      }
      String name = state == null ? null : of(state);
      found = name == null ? NONE : name;
      SOLVED_BY.put(algorithm, found);
    }
    return found.isEmpty() ? null : found;
  }

  /** Whether the algorithm solves the case, as every row of the table must. */
  public static boolean solves(String caseCode, String algorithm) {
    if (caseCode == null || algorithm == null || algorithm.trim().isEmpty()) {
      return false;
    }
    String name = solvedBy(algorithm.trim());
    return name != null && caseCode.equals(PREFIX + name);
  }
}
