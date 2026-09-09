package com.cube.nanotimer.smartcube.step;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What a last-layer case is called: the short name a speedcuber writes it as, for an OLL the shape
 * it is known by, and the group it is learnt in. The last two differ for seven cases only, which is
 * why they are two things: a solver says "Sune", and learns it alongside the six others that leave
 * the edges oriented.
 *
 * <p>Left in English on purpose, as the rest of the app's cubing vocabulary is. "Sune" and "Ga" are
 * the words a solver has already learnt from every tutorial and chart there is, and a translated
 * shape name would be a second name for something that already has one.
 */
public final class LastLayerCaseNames {

  /**
   * The group each OLL is learnt in, in the usual grouping. Every group holds at least two cases,
   * which is what {@code OCLL} is doing here: the seven cases that leave the edges already oriented
   * are one group of the set, not seven groups of one.
   */
  private static final Map<String, String> ORIENTATION_GROUPS = new LinkedHashMap<String, String>();

  /**
   * The seven that have a name of their own as well as a group. They are the ones a solver says out
   * loud, so the picture is captioned with the name and only the filtering goes by the group.
   */
  private static final Map<String, String> ORIENTATION_NAMES = new LinkedHashMap<String, String>();

  static {
    name("H", "21");
    name("Pi", "22");
    name("U", "23");
    name("T", "24");
    name("L", "25");
    name("Antisune", "26");
    name("Sune", "27");
    put("OCLL", "21", "22", "23", "24", "25", "26", "27");
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

  private static void put(String group, String... cases) {
    for (String name : cases) {
      ORIENTATION_GROUPS.put(name, group);
    }
  }

  private static void name(String name, String caseNumber) {
    ORIENTATION_NAMES.put(caseNumber, name);
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

  /**
   * What an OLL is called under its picture: its own name where it has one, otherwise the group it
   * is learnt in. Null for a permutation, which is named by its letter.
   */
  public static String shape(String caseCode) {
    String group = group(caseCode);
    if (group == null) {
      return null;
    }
    String name = ORIENTATION_NAMES.get(bareName(caseCode));
    return name == null ? group : name;
  }

  /**
   * The group an OLL is learnt and filtered in, or null for a permutation. Unlike {@link #shape},
   * this never names a single case: it is what a solver learns a handful of cases as.
   */
  public static String group(String caseCode) {
    if (caseCode == null || !caseCode.startsWith("oll_")) {
      return null;
    }
    return ORIENTATION_GROUPS.get(bareName(caseCode));
  }

  private static String bareName(String caseCode) {
    int split = caseCode == null ? -1 : caseCode.indexOf('_');
    return split < 0 ? caseCode : caseCode.substring(split + 1);
  }
}
