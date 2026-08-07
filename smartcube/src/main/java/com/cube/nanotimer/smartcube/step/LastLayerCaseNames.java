package com.cube.nanotimer.smartcube.step;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What a last-layer case is called: the short name a speedcuber writes it as, and for an OLL the
 * shape it is known by.
 *
 * <p>Left in English on purpose, as the rest of the app's cubing vocabulary is. "Sune" and "Ga" are
 * the words a solver has already learnt from every tutorial and chart there is, and a translated
 * shape name would be a second name for something that already has one.
 */
public final class LastLayerCaseNames {

  /**
   * The shape each OLL is known by, in the usual grouping. The seven cases that leave the edges
   * already oriented carry their own name rather than a group's, since those are the ones with
   * names of their own.
   */
  private static final Map<String, String> ORIENTATION_SHAPES = new LinkedHashMap<String, String>();

  static {
    put("H", "21");
    put("Pi", "22");
    put("U", "23");
    put("T", "24");
    put("L", "25");
    put("Antisune", "26");
    put("Sune", "27");
    put("Dot", "1", "2", "3", "4", "17", "18", "19", "20");
    put("Lightning", "7", "8", "11", "12", "39", "40");
    put("L shape", "47", "48", "49", "50", "53", "54");
    put("Fish", "9", "10", "35", "37");
    put("Knight move", "13", "14", "15", "16");
    put("P shape", "31", "32", "43", "44");
    put("I shape", "51", "52", "55", "56");
    put("Awkward", "29", "30", "41", "42");
    put("T shape", "33", "45");
    put("C shape", "34", "46");
    put("W shape", "36", "38");
    put("Square", "5", "6");
    put("Cross", "28", "57");
  }

  private LastLayerCaseNames() {
  }

  private static void put(String shape, String... cases) {
    for (String name : cases) {
      ORIENTATION_SHAPES.put(name, shape);
    }
  }

  /**
   * The case as it is written down: {@code "Ga"} for a permutation, {@code "21"} for an
   * orientation, which is a number because that is the only name an OLL has.
   */
  public static String shortName(String caseCode) {
    String name = bareName(caseCode);
    if (name == null || name.isEmpty()) {
      return name;
    }
    return name.substring(0, 1).toUpperCase(Locale.US) + name.substring(1);
  }

  /** The shape an OLL is known by, or null for a permutation, which is named by its letter. */
  public static String shape(String caseCode) {
    if (caseCode == null || !caseCode.startsWith("oll_")) {
      return null;
    }
    return ORIENTATION_SHAPES.get(bareName(caseCode));
  }

  private static String bareName(String caseCode) {
    int split = caseCode == null ? -1 : caseCode.indexOf('_');
    return split < 0 ? caseCode : caseCode.substring(split + 1);
  }
}
