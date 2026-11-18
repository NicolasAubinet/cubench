package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.cases.CaseGroup;
import com.cube.nanotimer.cases.CaseSet;
import com.cube.nanotimer.cases.CaseSets;
import com.cube.nanotimer.gui.widget.cases.CaseFilterBar;
import com.cube.nanotimer.gui.widget.cases.CaseTile;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.smartcube.step.CmllCases;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.FlowLayout;
import com.cube.nanotimer.util.view.KnowledgeRingView;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.util.view.ViewSegments;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.StepStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Analysis hub's Cases tab: one step's whole set, the cases the window holds figures for
 * slowest first, and the rest of the set under them.
 *
 * <p><b>One step at a time, which is why there is no "all" segment.</b> A single ranking across the
 * families was drawn first and reads as a comparison it cannot make: an OLL and a PLL are different
 * lengths of work drawn from sets of different sizes, so the top of a mixed list says more about
 * which set has 57 cases in it than about which case to look at. Ranking is only meaningful inside
 * one set, so the tab opens on the method's first step and the segments switch between them.
 *
 * <p><b>Every column here is a reading, and none of them is a verdict.</b> The table ranked by cost
 * once, a margin over the step's mean times a count, which is a judgement about what is worth
 * working on wearing a number's clothes. That ranking belongs to the coach, which still makes it
 * out of {@link MethodStatistics#getTimeLostMs}. What is left says how often a case came up, how
 * slow it runs, how good it has been and how steady it is, and the reader weighs them.
 *
 * <p><b>The set is shown whole, in two parts, and they run on two different clocks.</b> The table is
 * the cases that went in with one algorithm, each read over its own last solves (the tab's own
 * window, {@link com.cube.nanotimer.gui.AnalysisCaseWindow}) and only those, since a
 * two-look time is not the time of an algorithm the solver has; the section under it is the rest of
 * the set, and where each of those stands is read from every solve there has ever been. That is
 * not a "never seen" list: a case known cold that did not come up in the last hundred solves is in
 * it. Nothing there is ordered by what to learn first and nothing
 * is recommended — the list is a sort over the solver's own history, which is all it claims to be.
 *
 * <p><b>A case has two names, and each gets its own control.</b> A solve names a case by its number,
 * but a solver learns it by its shape, so both have to stay reachable. Ordering by the case itself is
 * the leftmost heading; narrowing to a shape is a picker beside the step segments. Neither can move
 * the other, so the two names never compete.
 *
 * <p><b>The shape is a picker rather than a second row of segments.</b> Drawn on the step row, twenty
 * groups read as twenty more steps: same height, same fill, same line, with a rule carrying the whole
 * parent-and-child distinction. The app already has the control this wants one row up, where the
 * window chip opens a single-choice list, and a list is also the only place the groups can be ordered
 * by size legibly, which is what keeps the seven single-case groups off the front.
 */
public class AnalysisCases {

  /** The column the table opens ranked by: the slowest case of the set, first. */
  private static final int MEAN_COLUMN = 1;

  /** The spread, which is the one column a case can be too new to have a figure for. */
  private static final int SPREAD_COLUMN = 3;

  // The last three are the step table's own columns, so a reader crossing tabs reads them once.
  // No count heading: the count rides on the case picture as a badge.
  private static final int[] HEADING_LABELS = {0,
      R.string.drill_summary_cell_mean, R.string.drill_summary_cell_best,
      R.string.drill_stats_column_spread};
  private static final boolean[] OPENS_DESCENDING = {true, true, true, true};

  private static final int CHART_DP = 44;

  private static final int OPAQUE = 255;

  /**
   * Every case the app has a picture and a set for, which is what this table may list, each against
   * its place in the set: an OLL by its number, a PLL by its letter, a CMLL in SpeedCubeDB's order,
   * which is the order every chart of them is printed in.
   */
  private static final Map<String, Integer> DRAWN = drawn();

  /**
   * The rest of the set, grouped in the order the known-algorithms screen already walks it: learnt,
   * learnt and slipping, not learnt, then nothing said either way. Not a learning order, which is
   * the one thing this section does not offer.
   */
  private static final CaseKnowledge.Status[] STANDINGS = {CaseKnowledge.Status.KNOWN,
      CaseKnowledge.Status.NEEDS_REVIEW, CaseKnowledge.Status.TO_LEARN, null};

  private static Map<String, Integer> drawn() {
    Map<String, Integer> order = new LinkedHashMap<String, Integer>();
    for (String code : LastLayerScrambles.cases()) {
      order.put(code, Integer.valueOf(order.size()));
    }
    for (String code : CaseSets.CMLL.cases()) {
      order.put(code, Integer.valueOf(order.size()));
    }
    return order;
  }

  /** Told which case the reader asked to see, or that they asked where the plan is. */
  public interface Listener {
    void onCasePicked(String caseCode);

    /** @param family the step the tab shows, whose costliest cases the plan drills */
    void onRecommendedDrills(String family);
  }

  private final Context context;
  private final View root;
  private final Listener listener;
  private final CaseTableHeadings headings;
  private final LinearLayout rows;

  private MethodStatistics statistics;
  private List<StepStats> oneAlgorithm;
  private StepPalette palette;
  private final Map<String, CaseKnowledge.Status> statuses =
      new LinkedHashMap<String, CaseKnowledge.Status>();
  private final List<String> families = new ArrayList<String>();
  /** The step being read, which is null only until the first read says what the steps are. */
  private String family;
  /** The group the family is narrowed to, or null for all of it. */
  private CaseGroup group;

  public AnalysisCases(Context context, View root, Listener listener) {
    this.context = context;
    this.root = root;
    this.listener = listener;
    this.rows = root.findViewById(R.id.llCaseTableRows);
    headings = new CaseTableHeadings(root, HEADING_LABELS, OPENS_DESCENDING, MEAN_COLUMN,
        new CaseTableHeadings.Listener() {
          @Override
          public void onRanked(int column, boolean descending) {
            showCases();
          }
        });
    headings.setLabel(R.string.analysis_column_case);
    headings.rankableLabel(false); // a set opens at its first case, which is OLL 1 and PLL Aa
    headings.reserveChevron(true); // every case opens its algorithms, so every row carries one
    root.findViewById(R.id.llAnalysisRecommended).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        listener.onRecommendedDrills(family);
      }
    });
  }

  /** Opens on one family, for a reader who arrived by tapping that step rather than the tab. */
  public void setFamily(String family) {
    this.family = family;
  }

  /** Whether this tab has cases of that family to show, which is what a step row's chevron says. */
  public boolean holds(String family) {
    return families.contains(family);
  }

  /**
   * @param oneAlgorithm each case over its own last solves where it went in one algorithm,
   *     which is all the table shows: a two-looked case is not one the solver has an algorithm for
   */
  public void show(MethodStatistics statistics, List<StepStats> oneAlgorithm,
      List<CaseKnowledge> known, StepPalette palette, CubeMethod method) {
    this.statistics = statistics;
    this.oneAlgorithm = oneAlgorithm;
    this.palette = palette;
    statuses.clear();
    for (CaseKnowledge caseKnown : known) {
      statuses.put(caseKnown.getCode(), caseKnown.getStatus());
    }
    findFamilies();
    boolean anything = !families.isEmpty();
    // The heading strip goes with the rows it heads: an empty table still drew its label, four
    // blank sort headings that were still tappable, and a rule under them.
    root.findViewById(R.id.llAnalysisKnowledgeCard)
        .setVisibility(anything ? View.VISIBLE : View.GONE);
    root.findViewById(R.id.svAnalysisFamilies).setVisibility(anything ? View.VISIBLE : View.GONE);
    root.findViewById(R.id.llAnalysisCaseTable).setVisibility(anything ? View.VISIBLE : View.GONE);
    // The plan it leads to is written for CFOP only.
    root.findViewById(R.id.llAnalysisRecommended)
        .setVisibility(anything && method == CubeMethod.CFOP ? View.VISIBLE : View.GONE);
    TextView empty = (TextView) root.findViewById(R.id.tvAnalysisCasesEmpty);
    empty.setVisibility(anything ? View.GONE : View.VISIBLE);
    empty.setText(emptyLine(method));
    root.findViewById(R.id.llAnalysisRestGroups)
        .setVisibility(anything ? View.VISIBLE : View.GONE);
    if (!anything) {
      rows.removeAllViews();
      return;
    }
    redraw();
  }

  /**
   * Why this tab is empty, which is three different things and only one of them is a matter of
   * solving more. A blind solve has no set to be dealt from, and a method whose steps this app has
   * no charts for cannot name a case however many solves it reads: telling either reader to go and
   * solve with a cube connected is advice that will never work.
   */
  private int emptyLine(CubeMethod method) {
    if (method == CubeMethod.BLIND) {
      return R.string.analysis_cases_blind;
    }
    return method == CubeMethod.CFOP || method == CubeMethod.ROUX ? R.string.analysis_cases_empty
        : R.string.analysis_cases_method;
  }

  /** The segments, the ring, the table and the rest of the set as the pick now leaves them. */
  private void redraw() {
    showFamilies();
    showKnowledge();
    showCases();
    showRest();
  }

  /**
   * The families the window holds cases of, in the order the method solves them.
   *
   * <p><b>A case here is one the app has a set and a picture for</b>, which today means the last
   * layer. That rule keeps out two things that are not cases in the sense this table means: an F2L
   * slot, which is a part of every solve rather than one of a set and has no picture to draw or a
   * name of its own to be listed under, and an algorithm code, which names what was turned at a case
   * rather than the case that was dealt.
   */
  private void findFamilies() {
    families.clear();
    // The method's own steps only. A part is coded by the algorithm that answered a case, so
    // taking parts as well would list one case twice, once as dealt and once as answered.
    for (StepStats step : statistics.getFamilies()) {
      if (!drawnCodes(step.getCode()).isEmpty()) {
        families.add(step.getCode());
      }
    }
    // The table reads each case's own last solves, which can reach back past the hub's window
    for (StepStats stepCase : oneAlgorithm) {
      String of = MethodStatistics.familyOf(stepCase.getCode());
      if (DRAWN.containsKey(stepCase.getCode()) && !families.contains(of)) {
        families.add(of);
      }
    }
    if (!families.isEmpty() && !families.contains(family)) {
      // Nothing was asked for, or what was asked for is not a step this window can say anything
      // about. Either way the tab opens on the first step the method solves.
      family = families.get(0);
    }
    if (group != null && !anyDrawn(group)) {
      group = null;
    }
  }

  private void showFamilies() {
    LinearLayout segments = root.findViewById(R.id.llAnalysisFamilies);
    LinearLayout row = root.findViewById(R.id.llAnalysisFamilyRow);
    segments.removeAllViews();
    // The row is the segments plus whatever the shape picker last put after them.
    row.removeViews(1, row.getChildCount() - 1);
    LayoutInflater inflater = LayoutInflater.from(context);
    for (String code : families) {
      addSegment(segments, inflater, code, Utils.toSmartCubeStepLocalizedName(context, code, 0));
    }
    showGroupFilter(row, inflater);
  }

  /**
   * The family's group filter, after the family chips behind a rule: one family's, never all of
   * them at once, since a group is how one set's cases are recognised.
   */
  private void showGroupFilter(LinearLayout row, LayoutInflater inflater) {
    CaseSet set = CaseSets.ofFamily(family);
    if (set == null) {
      return;
    }
    CaseFilterBar filter = new CaseFilterBar(context, set, new CaseFilterBar.Listener() {
      @Override
      public void onGroupPicked(CaseGroup picked) {
        group = picked;
        redraw();
      }
    }).offer(drawnCodes(family)).known(knownCodes()).setGroup(group);
    if (!filter.hasGroups()) {
      return;
    }
    row.addView(inflater.inflate(R.layout.analysis_chip_rule, row, false));
    row.addView(filter.chip(inflater, row));
  }

  private Set<String> knownCodes() {
    Set<String> known = new HashSet<String>();
    for (Map.Entry<String, CaseKnowledge.Status> entry : statuses.entrySet()) {
      if (entry.getValue() != null && entry.getValue().isLearned()) {
        known.add(entry.getKey());
      }
    }
    return known;
  }

  /** Whether the window drew any of the group's cases, which is what offers it. */
  private boolean anyDrawn(CaseGroup candidate) {
    for (String code : drawnCodes(family)) {
      if (candidate.contains(code)) {
        return true;
      }
    }
    return false;
  }

  private void addSegment(LinearLayout segments, LayoutInflater inflater, final String code,
      String label) {
    TextView cell = (TextView) inflater.inflate(R.layout.analysis_family_segment, segments, false);
    cell.setText(label);
    ViewSegments.style(cell, code.equals(family));
    cell.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        family = code;
        group = null; // a group narrows the step it belongs to, so it cannot outlive the pick
        redraw();
      }
    });
    segments.addView(cell);
  }

  /**
   * How far through the set the reader is. Only for a family whose set is closed and known: an F2L
   * slot has no list of cases to be a fraction of, and a ring against an unknown whole would be
   * inventing its own denominator.
   */
  private void showKnowledge() {
    List<String> set = closedSet();
    View card = root.findViewById(R.id.llAnalysisKnowledgeCard);
    card.setVisibility(set.isEmpty() ? View.GONE : View.VISIBLE);
    if (set.isEmpty()) {
      return;
    }
    int knownCount = 0;
    int reviewCount = 0;
    int toLearnCount = 0;
    for (String code : set) {
      CaseKnowledge.Status status = statuses.get(code);
      if (status == CaseKnowledge.Status.KNOWN) {
        knownCount++;
      } else if (status == CaseKnowledge.Status.NEEDS_REVIEW) {
        reviewCount++;
      } else if (status != null) {
        toLearnCount++;
      }
    }
    int hue = ContextCompat.getColor(context, R.color.lightblue);
    ((KnowledgeRingView) root.findViewById(R.id.vAnalysisKnowledgeRing))
        .setCounts(knownCount, reviewCount, toLearnCount, set.size(), hue);
    ((TextView) root.findViewById(R.id.tvAnalysisKnowledgeKnown))
        .setText(String.valueOf(knownCount));
    ((TextView) root.findViewById(R.id.tvAnalysisKnowledgeOf))
        .setText(context.getString(R.string.analysis_knowledge_of, Integer.valueOf(set.size())));

    LinearLayout keys = root.findViewById(R.id.llAnalysisKnowledgeKeys);
    keys.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(context);
    addArcKey(keys, inflater, hue, OPAQUE, R.string.known_algorithms_status_known, knownCount);
    addArcKey(keys, inflater, hue, KnowledgeRingView.REVIEW_ALPHA,
        R.string.known_algorithms_status_review, reviewCount);
    addArcKey(keys, inflater, hue, KnowledgeRingView.SEEN_ALPHA,
        R.string.known_algorithms_status_to_learn, toLearnCount);
    addArcKey(keys, inflater, ContextCompat.getColor(context, R.color.hero_inset), OPAQUE,
        R.string.analysis_knowledge_unseen, set.size() - knownCount - reviewCount - toLearnCount);
  }

  /** One of the ring's arcs, named and counted beside the swatch that is drawn in that arc. */
  private void addArcKey(LinearLayout keys, LayoutInflater inflater, int color, int alpha, int name,
      int count) {
    View key = addKey(keys, inflater, name, String.valueOf(count));
    key.findViewById(R.id.vAnalysisKeySwatch)
        .setBackgroundColor(color & 0x00FFFFFF | alpha << 24);
  }

  private View addKey(LinearLayout keys, LayoutInflater inflater, int name, String count) {
    View key = inflater.inflate(R.layout.analysis_knowledge_key, keys, false);
    ((TextView) key.findViewById(R.id.tvAnalysisKeyName)).setText(name);
    ((TextView) key.findViewById(R.id.tvAnalysisKeyCount)).setText(count);
    keys.addView(key);
    return key;
  }

  /**
   * The whole set of cases the picked family is drawn from.
   *
   * <p><b>Read off the set itself, never off what the window happens to hold.</b> Whether a case
   * goes in unaided is a fact about the solver rather than about their last hundred solves, so a
   * ring counting only the families present in the window fell from "of 78" to "of 57" when a
   * window with no PLL in it was picked, and the reader watched their own known fraction move
   * because they touched a control about something else.
   */
  private List<String> closedSet() {
    List<String> set = new ArrayList<String>();
    for (String code : DRAWN.keySet()) {
      if (family.equals(MethodStatistics.familyOf(code))
          && (group == null || group.contains(code))) {
        set.add(code);
      }
    }
    return set;
  }

  private void showCases() {
    List<StepStats> cases = casesOf(family);
    final int column = headings.column();
    final boolean descending = headings.descending();
    Collections.sort(cases, new Comparator<StepStats>() {
      @Override
      public int compare(StepStats a, StepStats b) {
        // A case that cannot quote the ranked column sits under the ones that can, whichever way
        // round it is turned, as it does on the step table.
        boolean quotableA = quotable(a, column);
        boolean quotableB = quotable(b, column);
        if (quotableA != quotableB) {
          return quotableA ? -1 : 1;
        }
        int order = Long.compare(value(a, column), value(b, column));
        return descending ? -order : order;
      }
    });

    rows.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(context);
    long slowest = 1;
    for (StepStats stepCase : cases) {
      slowest = Math.max(slowest,
          stepCase.getMeanRecognitionMs() + stepCase.getMeanExecutionMs());
    }
    float density = context.getResources().getDisplayMetrics().density;
    int chartPx = Math.round(CHART_DP * density);
    for (final StepStats stepCase : cases) {
      String code = stepCase.getCode();
      int hue = palette.colorFor(MethodStatistics.familyOf(code));
      CaseRow row = new CaseRow(CaseRow.inflate(inflater, rows));
      row.noCount()
          .chartSize(chartPx)
          .chart(code)
          .name(rowName(code))
          .meter(stepCase.getMeanRecognitionMs(), stepCase.getMeanExecutionMs(), hue)
          .scaleBar((float) (stepCase.getMeanRecognitionMs() + stepCase.getMeanExecutionMs())
              / slowest)
          .value(0, FormatterService.INSTANCE.formatSolveTime(stepCase.getMeanMs()),
              ContextCompat.getColor(context, R.color.white))
          .lead(0)
          .value(1, FormatterService.INSTANCE.formatSolveTime(stepCase.getBestMs()),
              ContextCompat.getColor(context, R.color.secondary_text))
          .value(2, quotable(stepCase, SPREAD_COLUMN)
                  ? FormatterService.INSTANCE.formatSolveTime(stepCase.getStdDevMs())
                  : context.getString(R.string.NA),
              ContextCompat.getColor(context, R.color.secondary_text))
          .rank(column)
          .chevron();
      row.view().setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          listener.onCasePicked(stepCase.getCode());
        }
      });
      rows.addView(row.view());
    }
    headings.refresh();
  }

  /** A CMLL's name alone: the segment already says CMLL, and the prefix cut the longer names short. */
  private String rowName(String code) {
    String cmll = CmllCases.fullName(code);
    return cmll != null ? cmll : Utils.toSmartCubeCaseHeadline(context, code);
  }

  /**
   * The rest of the step's set: every case of it the table does not hold, grouped under where it
   * stands: a case this solve type only ever two-looked, or never met. Where it stands is read over
   * every solve of any type, so a case known from drills or another solve type is here as known.
   *
   * <p>A case with nothing behind it is grouped under the words the ring above already counts it
   * with, and never under anything reading as "you do not know this": absence is not evidence.
   *
   * <p><b>Nothing on this tab offers a drill.</b> A drill composed from a finding is the app saying
   * what to do next, which is what the plan is for; this tab says where the solver stands and stops
   * there. The case set is not withheld by it — every case is named, ranked and pictured, and the
   * drill picker composes any set of them by hand — so what the plan sells is the choosing.
   */
  private void showRest() {
    List<String> rest = restOfSet();
    LinearLayout groups = root.findViewById(R.id.llAnalysisRestGroups);
    groups.setVisibility(rest.isEmpty() ? View.GONE : View.VISIBLE);
    groups.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(context);
    for (CaseKnowledge.Status standing : STANDINGS) {
      List<String> group = new ArrayList<String>();
      for (String code : rest) {
        if (statuses.get(code) == standing) {
          group.add(code);
        }
      }
      if (!group.isEmpty()) {
        groups.addView(restGroup(inflater, groups, standing, group));
      }
    }
  }

  /** One standing's cases, in the order the set is printed in. */
  private View restGroup(LayoutInflater inflater, LinearLayout groups,
      CaseKnowledge.Status standing, List<String> group) {
    View view = inflater.inflate(R.layout.analysis_rest_group, groups, false);
    ((TextView) view.findViewById(R.id.tvAnalysisRestGroup))
        .setText(context.getString(R.string.known_algorithms_status_count,
            context.getString(standingName(standing)), Integer.valueOf(group.size())));
    FlowLayout cases = view.findViewById(R.id.flAnalysisRestCases);
    for (final String code : group) {
      CaseSet set = CaseSets.ofCase(code);
      View cell = CaseTile.inflate(inflater, cases, R.layout.analysis_rest_cell)
          .show(set, code).view();
      if (set == CaseSets.CMLL) {
        cell.setMinimumWidth(context.getResources() // FlowLayout measures cells to their content
            .getDimensionPixelSize(R.dimen.analysis_rest_cell_cmll_width));
      } else {
        cell.findViewById(R.id.tvCaseTileShape).setVisibility(View.GONE); // the number is unique
      }
      cell.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          listener.onCasePicked(code);
        }
      });
      cases.addView(cell);
    }
    return view;
  }

  /**
   * Where a case stands, in the words the known-algorithms screen already says it in, except for the
   * cases nothing is known about: those take the ring's own label, because the ring on this screen
   * counts exactly them in its third arc and two names for one set on one screen read as two sets.
   * The known-algorithms screen calls them "nothing to say yet", which is truer of a case only ever
   * drilled cleanly, but that screen has no ring beside it to disagree with.
   */
  private static int standingName(CaseKnowledge.Status standing) {
    if (standing == CaseKnowledge.Status.KNOWN) {
      return R.string.known_algorithms_status_known;
    }
    if (standing == CaseKnowledge.Status.NEEDS_REVIEW) {
      return R.string.known_algorithms_status_review;
    }
    return standing == CaseKnowledge.Status.TO_LEARN ? R.string.known_algorithms_status_to_learn
        : R.string.analysis_knowledge_unseen;
  }

  /** The set the segments and the picker leave, less every case the table above already holds. */
  private List<String> restOfSet() {
    List<String> rest = new ArrayList<String>(closedSet());
    for (StepStats stepCase : casesOf(family)) {
      rest.remove(stepCase.getCode());
    }
    return rest;
  }

  /** The picked family's cases the window put in with one algorithm, narrowed to the picked group. */
  private List<StepStats> casesOf(String picked) {
    List<StepStats> cases = new ArrayList<StepStats>();
    for (StepStats stepCase : oneAlgorithm) {
      String code = stepCase.getCode();
      if (DRAWN.containsKey(code) && picked.equals(MethodStatistics.familyOf(code))
          && (group == null || group.contains(code))) {
        cases.add(stepCase);
      }
    }
    return cases;
  }

  /** One family's cases that the app has a picture and a set for, from the window or the table. */
  private List<String> drawnCodes(String family) {
    List<String> drawn = new ArrayList<String>();
    for (StepStats stepCase : statistics.getCases(family)) {
      if (DRAWN.containsKey(stepCase.getCode())) {
        drawn.add(stepCase.getCode());
      }
    }
    for (StepStats stepCase : oneAlgorithm) {
      String code = stepCase.getCode();
      if (DRAWN.containsKey(code) && family.equals(MethodStatistics.familyOf(code))
          && !drawn.contains(code)) {
        drawn.add(code);
      }
    }
    return drawn;
  }

  /**
   * Whether this case has a figure in that column. Only the spread can be missing: a case read
   * once has none, since 0.00 off one solve would read as the steadiest case there is. Its mean and
   * best are real figures and rank with the rest.
   */
  private boolean quotable(StepStats stepCase, int column) {
    return column != SPREAD_COLUMN || stepCase.getCount() >= 2;
  }

  private long value(StepStats stepCase, int column) {
    if (column == CaseTableHeadings.LABEL_COLUMN) {
      return DRAWN.get(stepCase.getCode()).longValue();
    }
    switch (column) {
      case 1:
        return stepCase.getMeanMs();
      case 2:
        return stepCase.getBestMs();
      case SPREAD_COLUMN:
        return stepCase.getStdDevMs();
      default:
        return stepCase.getCount();
    }
  }
}
