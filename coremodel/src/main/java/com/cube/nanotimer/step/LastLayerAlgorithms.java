package com.cube.nanotimer.step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An algorithm for every last-layer case {@code LastLayerCases} can name. Naming a case and
 * scrambling one back up are the same vocabulary read in opposite directions, so both read this
 * table: the detector calls a state {@code pll_ga} because it matches the key here, and a scramble
 * that leaves a Ga is this row's algorithm undone.
 *
 * <p>It lives here rather than beside those two because a row is also the case vocabulary: the coach
 * service reads this table to know which last-layer cases exist and which of them a drill can be
 * dealt, without any of the cube machinery that naming and scrambling need.
 *
 * <p>{@code LastLayerCasesTest}, in smartcube, applies every row and checks it lands on the case it
 * is filed under, and that the rows account for every last-layer state there is. A mistyped algorithm
 * therefore lands on some other case's key, which shows up as that case being claimed twice and
 * another not at all.
 */
public final class LastLayerAlgorithms {

  /**
   * One row per PLL: its name, then the algorithms that solve it. The first is the everyday one and
   * the one scrambles are built from; the second, where a family's naming turns on it, comes from a
   * different source so that swapping two names cannot pass unnoticed.
   */
  public static final String[][] PERMUTATIONS = {
    {"aa", "l' U R' D2 R U' R' D2 R2", "R' F R' B2 R F' R' B2 R2"},
    {"ab", "l U' R D2 R' U R D2 R2", "L F' L B2 L' F L B2 L2"},
    {"e", "x' R U' R' D R U R' D' R U R' D R U' R' D'",
        "R B' R' F R B R' F' R B R' F R B' R' F'"},
    {"f", "R' U' F' R U R' U' R' F R2 U' R' U' R U R' U R",
        "R' U2 R' d' R' F' R2 U' R' U R' F R U' F"},
    {"ga", "R2 u R' U R' U' R u' R2 y' R' U R", "R2 U R' U R' U' R U' R2 U' D R' U R D'"},
    {"gb", "L' U' L y' R2 u R' U R U' R u' R2", "R' U' R U D' R2 U R' U R U' R U' R2 D"},
    {"gc", "R2 u' R U' R U R' u R2 y R U' R'", "R2 U' R U' R U R' U R2 U D' R U' R' D"},
    {"gd", "R U R' y' R2 u' R U' R' U R' u R2", "R U R' U' D R2 U' R U' R' U R' U R2 D'"},
    {"h", "M2 U M2 U2 M2 U M2", "R2 U2 R U2 R2 U2 R2 U2 R U2 R2"},
    {"ja", "R' U L' U2 R U' R' U2 L R U'", "x R2 F R F' R U2 r' U r U2"},
    {"jb", "R U R' F' R U R' U' R' F R2 U' R' U'"},
    {"na", "L U' R U2 L' U R' L U' R U2 L' U R' U",
        "R U R' U R U R' F' R U R' U' R' F R2 U' R' U2 R U' R'"},
    {"nb", "R' U L' U2 R U' L R' U L' U2 R U' L U'"},
    {"ra", "L U2 L' U2 L F' L' U' L U L F L2 U"},
    {"rb", "R' U2 R U2 R' F R U R' U' R' F' R2 U'", "R2 F R U R U' R' F' R U2 R' U2 R"},
    {"t", "R U R' U' R' F R2 U' R' U' R U R' F'"},
    {"ua", "R U' R U R U R U' R' U' R2", "M2 U M U2 M' U M2"},
    {"ub", "R2 U R U R' U' R' U' R' U R'", "M2 U' M U2 M' U' M2"},
    {"v", "R' U R' U' y R' F' R2 U' R' U R' F R F"},
    {"y", "F R U' R' U' R U R' F' R U R' U' R' F R F'"},
    {"z", "M2 U M2 U M' U2 M2 U2 M' U2", "M' U M2 U M2 U M' U2 M2"},
  };

  /** One row per OLL, in the standard 1-57 numbering. */
  public static final String[][] ORIENTATIONS = {
    {"1", "R U2 R2 F R F' U2 R' F R F'"},
    {"2", "R U' R2 D' r U r' D R2 U R'"},
    {"3", "f R U R' U' f' U' F R U R' U' F'"},
    {"4", "f R U R' U' f' U F R U R' U' F'"},
    {"5", "l' U2 L U L' U l"},
    {"6", "r U2 R' U' R U' r'"},
    {"7", "r U R' U R U2 r'"},
    {"8", "l' U' L U' L' U2 l"},
    {"9", "R U R' U' R' F R2 U R' U' F'"},
    {"10", "R U R' U R' F R F' R U2 R'"},
    {"11", "r' R2 U R' U R U2 R' U M'"},
    {"12", "r R2 U' R U' R' U2 R U' r' R"},
    {"13", "F U R U' R2 F' R U R U' R'"},
    {"14", "R' F R U R' F' R F U' F'"},
    {"15", "l' U' l L' U' L U l' U l"},
    {"16", "r U r' R U R' U' r U' r'"},
    {"17", "R U R' U R' F R F' U2 R' F R F'"},
    {"18", "R U2 R2 F R F' U2 M' U R U' r'"},
    {"19", "S' R U R' S U' R' F R F'"},
    {"20", "r' R U R U R' U' r R' M' U R U' r'"},
    {"21", "R U R' U R U' R' U R U2 R'"},
    {"22", "R U2 R2 U' R2 U' R2 U2 R"},
    {"23", "R2 D' R U2 R' D R U2 R"},
    {"24", "r U R' U' r' F R F'"},
    {"25", "F R' F' r U R U' r'"},
    {"26", "R' U' R U' R' U2 R"},
    {"27", "R U R' U R U2 R'"},
    {"28", "r U R' U' r' R U R U' R'"},
    {"29", "R U R' U' R U' R' F' U' F R U R'"},
    {"30", "F U R U2 R' U' R U2 R' U' F'"},
    {"31", "R' U' F U R U' R' F' R"},
    {"32", "S R U R' U' R' F R f'"},
    {"33", "R U R' U' R' F R F'"},
    {"34", "R U R2 U' R' F R U R U' F'"},
    {"35", "R U2 R2 F R F' R U2 R'"},
    {"36", "L' U' L U' L' U L U L F' L' F"},
    {"37", "F R' F' R U R U' R'"},
    {"38", "R U R' U R U' R' U' R' F R F'"},
    {"39", "L F' L' U' L U F U' L'"},
    {"40", "R' F R U R' U' F' U R"},
    {"41", "R U R' U R U2 R' F R U R' U' F'"},
    {"42", "R' U' R U' R' U2 R F R U R' U' F'"},
    {"43", "R' U' F' U F R"},
    {"44", "F U R U' R' F'"},
    {"45", "F R U R' U' F'"},
    {"46", "R' U' R' F R F' U R"},
    {"47", "F R' F' R U2 R U' R' U R U2 R'"},
    {"48", "F R U R' U' R U R' U' F'"},
    {"49", "r U' r2 U r2 U r2 U' r"},
    {"50", "R' F R2 B' R2 F' R2 B R'"},
    {"51", "F U R U' R' U R U' R' F'"},
    {"52", "R U R' U R U' B U' B' R'"},
    {"53", "l' U' L U' L' U L U' L' U2 l"},
    {"54", "r U R' U R U' R' U R U2 r'"},
    {"55", "R' F R U R U' R2 F' R2 U' R' U R U R'"},
    {"56", "r U r' U R U' R' U R U' R' r U' r'"},
    {"57", "R U R' U' M' U R U' r'"},
  };

  /** The step a row of each table is filed under, as a solve records it. */
  private static final String ORIENTATION_STEP = "oll";
  private static final String PERMUTATION_STEP = "pll";

  private LastLayerAlgorithms() {
  }

  /** The everyday algorithm for a case, or null if the table does not hold that name. */
  public static String algorithm(String[][] cases, String name) {
    for (String[] row : cases) {
      if (row[0].equals(name)) {
        return row[1];
      }
    }
    return null;
  }

  /**
   * The everyday algorithm for a case written the way a solve writes it, {@code "oll_21"} or
   * {@code "pll_ga"}, or null when the code names no case this table holds.
   *
   * <p>This is the join between the two vocabularies, and it is here so that there is one of it. A
   * scramble is this algorithm undone, a drill is dealt from that scramble, and a plan may only
   * prescribe a case a drill can be dealt of: three readings of one table, which used to be three
   * copies of this five-line mapping in three modules.
   */
  public static String forCaseCode(String caseCode) {
    int split = caseCode == null ? -1 : caseCode.indexOf('_');
    if (split < 0) {
      return null;
    }
    String step = caseCode.substring(0, split);
    String name = caseCode.substring(split + 1);
    if (ORIENTATION_STEP.equals(step)) {
      return algorithm(ORIENTATIONS, name);
    }
    return PERMUTATION_STEP.equals(step) ? algorithm(PERMUTATIONS, name) : null;
  }

  /**
   * Whether this is a last-layer case the table holds, which is the same question as whether a drill
   * of it can be dealt: a scramble is a row of this table undone, so a case that is here has one and
   * a case that is not cannot have one.
   *
   * <p>It answers no to a family on its own. {@code oll} is a family this deals cases of and is not
   * itself a case, so a drill asking for it is a button with nothing behind it just the same.
   */
  public static boolean dealsCase(String caseCode) {
    return forCaseCode(caseCode) != null;
  }

  /** Every case in both tables, under the codes a solve records them with. */
  public static List<String> caseCodes() {
    List<String> codes = new ArrayList<String>();
    for (String[] row : ORIENTATIONS) {
      codes.add(ORIENTATION_STEP + "_" + row[0]);
    }
    for (String[] row : PERMUTATIONS) {
      codes.add(PERMUTATION_STEP + "_" + row[0]);
    }
    return Collections.unmodifiableList(codes);
  }
}
