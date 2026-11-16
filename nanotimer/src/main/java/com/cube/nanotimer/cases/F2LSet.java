package com.cube.nanotimer.cases;

import com.cube.nanotimer.R;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F2L: the 41 basic cases, then the advanced ones with a piece stuck in another slot, drawn as a 3D
 * pair and laid out in sections by where the two pieces are. Its cases have no public names, so a
 * tile shows the picture alone and the section says what the case is.
 *
 * <p>The groups follow the case numbering: basic 1-24 have both pieces on top, 25-30 the corner in
 * its slot, 31-36 the edge, 37-41 both; advanced 1-9 (and their "a" twins) have the corner stuck,
 * 10-24 the edge, 25-42 both, in one other slot.
 */
final class F2LSet implements CaseSet {

  static final F2LSet INSTANCE = new F2LSet();

  private final List<String> cases;
  private final List<CaseGroup> groups;
  private final Map<String, CaseGroup> groupOfCase = new LinkedHashMap<String, CaseGroup>();

  private F2LSet() {
    List<CaseGroup> all = new ArrayList<CaseGroup>();
    all.add(group("top", R.string.case_group_f2l_top, false, 1, 24));
    all.add(group("corner", R.string.case_group_f2l_corner, false, 25, 30));
    all.add(group("edge", R.string.case_group_f2l_edge, false, 31, 36));
    all.add(group("both", R.string.case_group_f2l_both, false, 37, 41));
    all.add(group("stuck_corner", R.string.case_group_f2l_stuck_corner, true, 1, 9));
    all.add(group("stuck_edge", R.string.case_group_f2l_stuck_edge, true, 10, 24));
    all.add(group("stuck_both", R.string.case_group_f2l_stuck_both, true, 25, 42));
    groups = Collections.unmodifiableList(all);
    List<String> codes = new ArrayList<String>();
    for (CaseGroup group : groups) {
      for (String code : group.getCases()) {
        groupOfCase.put(code, group);
        codes.add(code);
      }
    }
    cases = Collections.unmodifiableList(codes);
  }

  /** The named cases numbered from {@code first} to {@code last}, of one set, in its order. */
  private static CaseGroup group(String id, int nameRes, boolean advanced, int first, int last) {
    List<String> codes = new ArrayList<String>();
    for (String code : CaseAlgorithms.pairCases()) {
      if (CaseAlgorithms.isBasicPair(code) != advanced) {
        int number = Integer.parseInt(CaseAlgorithms.pairCase(code).replaceAll("\\D", ""));
        if (number >= first && number <= last) {
          codes.add(code);
        }
      }
    }
    return new CaseGroup(id, null, nameRes, codes);
  }

  @Override
  public String id() {
    return "pair";
  }

  @Override
  public String title() {
    return "F2L";
  }

  @Override
  public int descriptionRes() {
    return R.string.case_set_f2l_description;
  }

  @Override
  public String method() {
    return "CFOP";
  }

  @Override
  public String coverCase() {
    return "pair_7";
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
    return Picture.PAIR;
  }

  /** None: F2L numbers are one website's, never shown. */
  @Override
  public String name(String caseCode) {
    return null;
  }

  @Override
  public String shape(String caseCode) {
    return null;
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
    return true;
  }

  @Override
  public List<CaseAlgorithms.Listed> algorithms(String caseCode) {
    return CaseAlgorithms.shown(caseCode);
  }
}
