package com.cube.nanotimer.cases;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Every set the app knows, in the order a method solves them, and the way back to one. */
public final class CaseSets {

  public static final CaseSet F2L = F2LSet.INSTANCE;
  public static final CaseSet OLL = LastLayerSet.OLL;
  public static final CaseSet PLL = LastLayerSet.PLL;
  public static final CaseSet CMLL = CmllSet.INSTANCE;

  private static final List<CaseSet> ALL = Collections.unmodifiableList(
      Arrays.<CaseSet>asList(F2L, OLL, PLL, CMLL));

  private CaseSets() {
  }

  public static List<CaseSet> all() {
    return ALL;
  }

  /** The set with that id ({@code "oll"}), or null. */
  public static CaseSet byId(String id) {
    for (CaseSet set : ALL) {
      if (set.id().equals(id)) {
        return set;
      }
    }
    return null;
  }

  /** The set a case belongs to, or null for a code no set holds (a pair dealt into a slot). */
  public static CaseSet ofCase(String caseCode) {
    for (CaseSet set : ALL) {
      if (set.contains(caseCode)) {
        return set;
      }
    }
    return null;
  }

  /** The set a drill family prefix ({@code "oll_"}) names, or null. */
  public static CaseSet ofFamily(String family) {
    return family == null ? null : byId(family.endsWith("_")
        ? family.substring(0, family.length() - 1) : family);
  }
}
