package com.cube.nanotimer.cases;

import com.cube.nanotimer.R;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.LastLayerCaseNames;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.step.OllShapes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OLL or PLL: the cases of the algorithm table under one prefix, drawn from above. */
final class LastLayerSet implements CaseSet {

  static final LastLayerSet OLL = new LastLayerSet("oll", "OLL");
  static final LastLayerSet PLL = new LastLayerSet("pll", "PLL");

  private final String id;
  private final String title;
  private final String prefix;
  private final List<String> cases;
  private final List<CaseGroup> groups;
  private final Map<String, CaseGroup> groupOfCase = new LinkedHashMap<String, CaseGroup>();

  private LastLayerSet(String id, String title) {
    this.id = id;
    this.title = title;
    this.prefix = id + "_";
    List<String> codes = new ArrayList<String>();
    for (String code : LastLayerScrambles.cases()) {
      if (code.startsWith(prefix)) {
        codes.add(code);
      }
    }
    cases = Collections.unmodifiableList(codes);
    groups = Collections.unmodifiableList("oll".equals(id) ? ollGroups() : pllGroups());
    for (CaseGroup group : groups) {
      for (String code : group.getCases()) {
        groupOfCase.put(code, group);
      }
    }
  }

  /** The shape groups, in the order their first case comes. */
  private List<CaseGroup> ollGroups() {
    Map<String, List<String>> byShape = new LinkedHashMap<String, List<String>>();
    for (String code : cases) {
      String shape = OllShapes.groupOf(code);
      if (!byShape.containsKey(shape)) {
        byShape.put(shape, new ArrayList<String>());
      }
      byShape.get(shape).add(code);
    }
    List<CaseGroup> all = new ArrayList<CaseGroup>();
    for (Map.Entry<String, List<String>> shape : byShape.entrySet()) {
      all.add(new CaseGroup(shape.getKey(), shape.getKey(), 0, shape.getValue()));
    }
    return all;
  }

  /** By what the case swaps, the usual way PLL is taught. */
  private List<CaseGroup> pllGroups() {
    List<CaseGroup> all = new ArrayList<CaseGroup>();
    all.add(pllGroup("edges", R.string.case_group_pll_edges, "ua", "ub", "h", "z"));
    all.add(pllGroup("corners", R.string.case_group_pll_corners, "aa", "ab", "e"));
    all.add(pllGroup("adjacent", R.string.case_group_pll_adjacent,
        "f", "ga", "gb", "gc", "gd", "ja", "jb", "ra", "rb", "t"));
    all.add(pllGroup("diagonal", R.string.case_group_pll_diagonal, "na", "nb", "v", "y"));
    return all;
  }

  private CaseGroup pllGroup(String groupId, int nameRes, String... names) {
    List<String> members = Arrays.asList(names);
    List<String> codes = new ArrayList<String>();
    for (String code : cases) {
      if (members.contains(code.substring(prefix.length()))) {
        codes.add(code);
      }
    }
    return new CaseGroup(groupId, null, nameRes, codes);
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public String title() {
    return title;
  }

  @Override
  public List<String> cases() {
    return cases;
  }

  @Override
  public boolean contains(String caseCode) {
    return groupOfCase.containsKey(caseCode);
  }

  @Override
  public Picture picture() {
    return Picture.TOP_VIEW;
  }

  @Override
  public String name(String caseCode) {
    return contains(caseCode) ? LastLayerCaseNames.shortName(caseCode) : null;
  }

  @Override
  public String shape(String caseCode) {
    return contains(caseCode) ? LastLayerCaseNames.shape(caseCode) : null;
  }

  @Override
  public List<CaseGroup> groups() {
    return groups;
  }

  @Override
  public CaseGroup groupOf(String caseCode) {
    return groupOfCase.get(caseCode);
  }

  @Override
  public boolean sectioned() {
    return false;
  }

  @Override
  public List<CaseAlgorithms.Listed> algorithms(String caseCode) {
    return CaseAlgorithms.shown(caseCode);
  }
}
