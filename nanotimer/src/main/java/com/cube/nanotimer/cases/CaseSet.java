package com.cube.nanotimer.cases;

import com.cube.nanotimer.smartcube.step.CaseAlgorithms;

import java.util.List;

/**
 * One set of cases a solver learns (OLL, PLL; later F2L, CMLL), asked the same questions by every
 * screen that shows cases: the tile, the row, the filter bar, the case sheet, the library and the
 * drill picker. A new set plugs in by answering them, with no new screen.
 *
 * <p>Case codes are the ones a solve records ({@code oll_27}, {@code pll_jb}), so a set's cases key
 * the same rows as {@code CaseKnowledge} and the drill tables.
 */
public interface CaseSet {

  /** How a set's cases are drawn. */
  enum Picture {
    /** The last layer seen from above, {@code LastLayerCaseView}. */
    TOP_VIEW,
    /** An F2L pair in 3D. */
    PAIR
  }

  /** {@code "oll"}, {@code "pll"}: stable, used in preferences and as the case code prefix. */
  String id();

  /** The set as a solver writes it, "OLL". Cubing vocabulary, so never translated. */
  String title();

  /** What the set does, "Orient the last layer", as a string resource. */
  int descriptionRes();

  /** The method the set belongs to, "CFOP", which the library groups sets under. */
  String method();

  /** The case the set's card is drawn with. */
  String coverCase();

  /** Every case, in the set's own order (OLL by number, PLL by letter). */
  List<String> cases();

  /** Whether the code is one of this set's cases. */
  boolean contains(String caseCode);

  Picture picture();

  /** The case's short name, "27" or "Jb", or null for a set whose cases have no public names. */
  String name(String caseCode);

  /** What is written under the name ("Sune", "Lightning"), or null for none. */
  String shape(String caseCode);

  /** The groups the filter offers, in order, together covering every case once. */
  List<CaseGroup> groups();

  /** The group a case is in, or null for a code outside the set. */
  CaseGroup groupOf(String caseCode);

  /** Whether the list is drawn in group sections rather than filtered by group. */
  boolean sectioned();

  /** The case's algorithms, most used first. */
  List<CaseAlgorithms.Listed> algorithms(String caseCode);
}
