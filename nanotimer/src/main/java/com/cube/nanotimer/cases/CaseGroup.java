package com.cube.nanotimer.cases;

import android.content.Context;

import java.util.Collections;
import java.util.List;

/** A group of a set's cases as the filter offers it: an OLL shape, a kind of PLL swap. */
public final class CaseGroup {

  private final String id;
  private final String name;
  private final int nameRes;
  private final List<String> cases;

  /**
   * @param name the group's fixed name (cubing vocabulary, left in English), or null to use
   *     {@code nameRes}
   * @param nameRes a translated name, read when {@code name} is null
   */
  CaseGroup(String id, String name, int nameRes, List<String> cases) {
    this.id = id;
    this.name = name;
    this.nameRes = nameRes;
    this.cases = Collections.unmodifiableList(cases);
  }

  /** Stable across releases: a saved filter is kept by it. */
  public String getId() {
    return id;
  }

  public String getName(Context context) {
    return name != null ? name : context.getString(nameRes);
  }

  /** The group's cases, in the set's order. */
  public List<String> getCases() {
    return cases;
  }

  public boolean contains(String caseCode) {
    return cases.contains(caseCode);
  }
}
