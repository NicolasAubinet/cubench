package com.cube.nanotimer.cases;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Every set the app knows, and the way from a case code or an id back to its set. */
public final class CaseSets {

  public static final CaseSet OLL = LastLayerSet.OLL;
  public static final CaseSet PLL = LastLayerSet.PLL;

  private static final List<CaseSet> ALL = Collections.unmodifiableList(
      Arrays.<CaseSet>asList(OLL, PLL));

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

  /** The set a case belongs to, or null for a code no set holds (an F2L pair, for now). */
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
