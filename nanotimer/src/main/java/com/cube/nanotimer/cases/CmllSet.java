package com.cube.nanotimer.cases;

import com.cube.nanotimer.R;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.CmllCases;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Roux's CMLL, drawn from above as OLL and PLL are, corners only. A case is named by its group and
 * its name within it, as SpeedCubeDB writes them: the tile says "Sune" over "Left Bar".
 */
final class CmllSet implements CaseSet {

  static final CmllSet INSTANCE = new CmllSet();

  private final List<CaseGroup> groups;
  private final Map<String, CaseGroup> groupOfCase = new LinkedHashMap<String, CaseGroup>();

  private CmllSet() {
    List<CaseGroup> all = new ArrayList<CaseGroup>();
    for (String name : CmllCases.groups()) {
      List<String> codes = new ArrayList<String>();
      for (String code : CmllCases.codes()) {
        if (name.equals(CmllCases.group(code))) {
          codes.add(code);
        }
      }
      String id = name.toLowerCase(Locale.US).replace(" ", "");
      CaseGroup group = new CaseGroup(id, name, 0, codes);
      all.add(group);
      for (String code : codes) {
        groupOfCase.put(code, group);
      }
    }
    groups = Collections.unmodifiableList(all);
  }

  @Override
  public String id() {
    return "cmll";
  }

  @Override
  public String title() {
    return "CMLL";
  }

  @Override
  public int descriptionRes() {
    return R.string.case_set_cmll_description;
  }

  @Override
  public String method() {
    return "Roux";
  }

  @Override
  public String coverCase() {
    return "cmll_suneleftbar";
  }

  @Override
  public List<String> cases() {
    return CmllCases.codes();
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
    return CmllCases.group(caseCode);
  }

  @Override
  public String shape(String caseCode) {
    return CmllCases.variant(caseCode);
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
