package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.coach.CoachPayloadBuilder;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.smartcube.step.LastLayerCaseNames;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.KnowledgeRingView;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.vo.StepStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Analysis hub's Cases tab: every case the window holds, ranked by what it costs, across every
 * family at once.
 *
 * <p><b>Ranking across families is what removes the OLL-or-PLL control.</b> "Which cases cost me"
 * does not care whether the answer is an OLL or a PLL, so a two-way segmented strip above the table
 * was making the reader answer a question in order to be shown the answer to theirs — and it implied
 * there were only two families, when Roux, blind and layer-by-layer each have their own. The family
 * is a chip that narrows a list already holding the answer.
 *
 * <p>A cost is a margin times a count, so it is only quoted for a case seen at least
 * {@link CoachPayloadBuilder#CASE_FLOOR} times; the rest say so rather than showing a figure read
 * off two occurrences.
 *
 * <p><b>A case has two names, and each gets its own control.</b> A solve names a case by its number,
 * but a solver learns it by its shape, so both have to stay reachable. Ordering by the case itself is
 * the leftmost heading; narrowing to a shape is a picker beside the family chips. Neither can move
 * the other, so the two names never compete.
 *
 * <p><b>The shape is a picker rather than more chips.</b> Drawn as chips on the family row, twenty
 * groups read as twenty more families: same height, same fill, same line, with a rule carrying the
 * whole parent-and-child distinction. The app already has the control this wants one row up, where
 * the window chip opens a single-choice list, and a list is also the only place the groups can be
 * ordered by size legibly, which is what keeps the seven single-case groups off the front.
 */
public class AnalysisCases {

  /** What a case costs, and the column the table opens ranked by: it is what the tab is for. */
  private static final int COST_COLUMN = 3;

  private static final int[] HEADING_LABELS = {R.string.drill_stats_column_count,
      R.string.drill_summary_cell_mean, R.string.drill_summary_cell_best,
      R.string.analysis_column_cost};
  private static final boolean[] OPENS_DESCENDING = {true, true, true, true};

  /** How many of the dearest cases the drill button offers, when there are that many to offer. */
  private static final int DRILL_CASES = 4;

  private static final int OPAQUE = 255;

  /** Every family, which is how the chip row opens and what it falls back to. */
  private static final String ALL = "";

  /** Every shape, which is how a family opens once it has been picked. */
  private static final String ANY = "";

  /**
   * Every case the app has a picture and a set for, which is what this table may list, each against
   * its place in the set: an OLL by its number, a PLL by its letter, which is the order every chart
   * of them is printed in.
   */
  private static final Map<String, Integer> DRAWN = drawn();

  /** How many cases each shape groups, which is a fact about the set and not about a window. */
  private static final Map<String, Integer> SHAPE_SIZES = shapeSizes();

  private static Map<String, Integer> shapeSizes() {
    Map<String, Integer> sizes = new LinkedHashMap<String, Integer>();
    for (String code : DRAWN.keySet()) {
      String name = LastLayerCaseNames.group(code);
      if (name != null) {
        Integer held = sizes.get(name);
        sizes.put(name, Integer.valueOf(held == null ? 1 : held.intValue() + 1));
      }
    }
    return sizes;
  }

  private static Map<String, Integer> drawn() {
    Map<String, Integer> order = new LinkedHashMap<String, Integer>();
    for (String code : LastLayerScrambles.cases()) {
      order.put(code, Integer.valueOf(order.size()));
    }
    return order;
  }

  /** Told which case the reader asked to see, and which ones they asked to drill. */
  public interface Listener {
    void onCasePicked(String caseCode);

    void onDrillPicked(List<String> caseCodes);
  }

  private final Context context;
  private final View root;
  private final Listener listener;
  private final CaseTableHeadings headings;
  private final LinearLayout rows;

  private MethodStatistics statistics;
  private StepPalette palette;
  private final Map<String, CaseKnowledge.Status> statuses =
      new LinkedHashMap<String, CaseKnowledge.Status>();
  private final List<String> families = new ArrayList<String>();
  private String family = ALL;
  private String shape = ANY;

  public AnalysisCases(Context context, View root, Listener listener) {
    this.context = context;
    this.root = root;
    this.listener = listener;
    this.rows = root.findViewById(R.id.llCaseTableRows);
    headings = new CaseTableHeadings(root, HEADING_LABELS, OPENS_DESCENDING, COST_COLUMN,
        new CaseTableHeadings.Listener() {
          @Override
          public void onRanked(int column, boolean descending) {
            showCases();
          }
        });
    headings.setLabel(R.string.analysis_column_case);
    headings.rankableLabel(false); // a set opens at its first case, which is OLL 1 and PLL Aa
    headings.reserveChevron(true); // every case opens its algorithms, so every row carries one
  }

  /** Opens on one family, for a reader who arrived by tapping that step rather than the tab. */
  public void setFamily(String family) {
    this.family = family == null ? ALL : family;
  }

  /** Whether this tab has cases of that family to show, which is what a step row's chevron says. */
  public boolean holds(String family) {
    return families.contains(family);
  }

  public void show(MethodStatistics statistics, List<CaseKnowledge> known, StepPalette palette) {
    this.statistics = statistics;
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
    root.findViewById(R.id.tvAnalysisCasesEmpty)
        .setVisibility(anything ? View.GONE : View.VISIBLE);
    if (!anything) {
      rows.removeAllViews();
      root.findViewById(R.id.tvAnalysisDrillCostliest).setVisibility(View.GONE);
      return;
    }
    redraw();
  }

  /** The chips, the ring and the table as the picked family and shape now leave them. */
  private void redraw() {
    showFamilies();
    showKnowledge();
    showCases();
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
      if (!drawnCases(step.getCode()).isEmpty()) {
        families.add(step.getCode());
      }
    }
    if (!families.contains(family)) {
      family = ALL; // the family arrived on is not one this window has anything to say about
    }
    if (!ANY.equals(shape) && !shapesOf(family).contains(shape)) {
      shape = ANY;
    }
  }

  private void showFamilies() {
    LinearLayout row = root.findViewById(R.id.llAnalysisFamilies);
    row.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(context);
    addChip(row, inflater, ALL, context.getString(R.string.analysis_family_all));
    for (String code : families) {
      addChip(row, inflater, code, Utils.toSmartCubeStepLocalizedName(context, code, 0));
    }
    showShapePicker(row, inflater);
  }

  /**
   * The shape picker, offered after the family chips behind a rule.
   *
   * <p>Only under one family, and never beside them all: a shape is how one family's cases are
   * recognised, so it says nothing about a PLL and nothing at all about a list holding both.
   */
  private void showShapePicker(LinearLayout row, LayoutInflater inflater) {
    if (shapesOf(family).isEmpty()) {
      return;
    }
    row.addView(inflater.inflate(R.layout.analysis_chip_rule, row, false));
    TextView chip = (TextView) inflater.inflate(R.layout.analysis_family_chip, row, false);
    chip.setText(context.getString(R.string.analysis_window_chip,
        ANY.equals(shape) ? context.getString(R.string.analysis_shape_title) : shape));
    chip.setSelected(!ANY.equals(shape));
    chip.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        pickShape();
      }
    });
    row.addView(chip);
  }

  /**
   * The groups to narrow to, biggest first, each with how many cases it holds.
   *
   * <p>Size order puts the groups with the most in them at the top, and the count beside each is
   * what makes that order explain itself. Neither works in a scrolling row, which is the whole
   * reason this is a list.
   */
  private void pickShape() {
    final List<String> shapes = shapesOf(family);
    String[] labels = new String[shapes.size() + 1];
    labels[0] = context.getString(R.string.analysis_shape_all);
    for (int i = 0; i < shapes.size(); i++) {
      labels[i + 1] = context.getString(R.string.analysis_shape_count, shapes.get(i),
          SHAPE_SIZES.get(shapes.get(i)));
    }
    // The window chip's own dialog, down to its theme: this is the same control one row up.
    new AlertDialog.Builder(context, R.style.NanoTimerDialogTheme)
        .setTitle(R.string.analysis_shape_title)
        .setSingleChoiceItems(labels, shapes.indexOf(shape) + 1,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                shape = which == 0 ? ANY : shapes.get(which - 1);
                dialog.dismiss();
                redraw();
              }
            })
        .show();
  }

  /**
   * The shapes one family's cases in this window are grouped by, biggest group first and ties by
   * name. Left in the cubing vocabulary the app already keeps its case names in.
   */
  private List<String> shapesOf(String picked) {
    List<String> shapes = new ArrayList<String>();
    if (ALL.equals(picked)) {
      return shapes;
    }
    for (StepStats stepCase : drawnCases(picked)) {
      String name = LastLayerCaseNames.group(stepCase.getCode());
      if (name != null && !shapes.contains(name)) {
        shapes.add(name);
      }
    }
    Collections.sort(shapes, new Comparator<String>() {
      @Override
      public int compare(String a, String b) {
        int size = SHAPE_SIZES.get(b).compareTo(SHAPE_SIZES.get(a));
        return size != 0 ? size : a.compareTo(b);
      }
    });
    return shapes;
  }

  private void addChip(LinearLayout row, LayoutInflater inflater, final String code, String label) {
    TextView chip = (TextView) inflater.inflate(R.layout.analysis_family_chip, row, false);
    chip.setText(label);
    chip.setSelected(code.equals(family));
    chip.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        family = code;
        shape = ANY; // a shape narrows the family it belongs to, so it cannot outlive the pick
        redraw();
      }
    });
    row.addView(chip);
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
    int seenCount = 0;
    for (String code : set) {
      CaseKnowledge.Status status = statuses.get(code);
      if (status == CaseKnowledge.Status.KNOWN) {
        knownCount++;
      } else if (status != null) {
        seenCount++;
      }
    }
    int hue = ContextCompat.getColor(context, R.color.lightblue);
    ((KnowledgeRingView) root.findViewById(R.id.vAnalysisKnowledgeRing))
        .setCounts(knownCount, seenCount, set.size(), hue);
    ((TextView) root.findViewById(R.id.tvAnalysisKnowledgeKnown))
        .setText(String.valueOf(knownCount));
    ((TextView) root.findViewById(R.id.tvAnalysisKnowledgeOf))
        .setText(context.getString(R.string.analysis_knowledge_of, Integer.valueOf(set.size())));

    LinearLayout keys = root.findViewById(R.id.llAnalysisKnowledgeKeys);
    keys.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(context);
    addArcKey(keys, inflater, hue, OPAQUE, R.string.analysis_knowledge_known, knownCount);
    addArcKey(keys, inflater, hue, KnowledgeRingView.SEEN_ALPHA,
        R.string.analysis_knowledge_seen, seenCount);
    addArcKey(keys, inflater, ContextCompat.getColor(context, R.color.hero_inset), OPAQUE,
        R.string.analysis_knowledge_unseen, set.size() - knownCount - seenCount);
    // What the ring is about rather than one of its arcs, so it takes no swatch and its own colour.
    View costing = addKey(keys, inflater, R.string.analysis_knowledge_costing, cost(totalCost()));
    costing.findViewById(R.id.vAnalysisKeySwatch).setVisibility(View.INVISIBLE);
    ((TextView) costing.findViewById(R.id.tvAnalysisKeyCount))
        .setTextColor(ContextCompat.getColor(context, R.color.analysis_cost));
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
      if ((ALL.equals(family) || family.equals(MethodStatistics.familyOf(code)))
          && (ANY.equals(shape) || shape.equals(LastLayerCaseNames.group(code)))) {
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
        // A case with no cost to quote sits under the ones that have, whichever way round the
        // column is turned: it is not a cheap case, it is a case nothing can be said about.
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
    for (final StepStats stepCase : cases) {
      String code = stepCase.getCode();
      int hue = palette.colorFor(MethodStatistics.familyOf(code));
      long lost = statistics.getTimeLostMs(code);
      boolean enough = enough(stepCase);
      CaseRow row = new CaseRow(CaseRow.inflate(inflater, rows));
      row.count(String.valueOf(stepCase.getCount()))
          .chart(code)
          .name(Utils.toSmartCubeCaseHeadline(context, code))
          .meter(stepCase.getMeanRecognitionMs(), stepCase.getMeanExecutionMs(), hue)
          .value(0, FormatterService.INSTANCE.formatSolveTime(stepCase.getMeanMs()),
              ContextCompat.getColor(context, R.color.white))
          .value(1, FormatterService.INSTANCE.formatSolveTime(stepCase.getBestMs()),
              ContextCompat.getColor(context, R.color.secondary_text))
          .value(2, enough ? cost(lost) : context.getString(R.string.NA),
              ContextCompat.getColor(context,
                  enough && lost > 0 ? R.color.analysis_cost : R.color.secondary_text))
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
    showDrill(cases);
  }

  /** The dearest few, offered as one drill: the answer to the top of the table it sits under. */
  private void showDrill(List<StepStats> cases) {
    final List<String> costliest = new ArrayList<String>();
    List<StepStats> byCost = new ArrayList<StepStats>(cases);
    Collections.sort(byCost, new Comparator<StepStats>() {
      @Override
      public int compare(StepStats a, StepStats b) {
        return Long.compare(statistics.getTimeLostMs(b.getCode()),
            statistics.getTimeLostMs(a.getCode()));
      }
    });
    for (StepStats stepCase : byCost) {
      if (costliest.size() < DRILL_CASES && enough(stepCase)
          && statistics.getTimeLostMs(stepCase.getCode()) > 0) {
        costliest.add(stepCase.getCode());
      }
    }
    TextView drill = root.findViewById(R.id.tvAnalysisDrillCostliest);
    // Nothing to offer where no case is costing anything, which is a table worth being proud of.
    drill.setVisibility(costliest.isEmpty() ? View.GONE : View.VISIBLE);
    drill.setText(context.getResources().getQuantityString(R.plurals.analysis_drill_costliest,
        costliest.size(), Integer.valueOf(costliest.size())));
    drill.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        listener.onDrillPicked(costliest);
      }
    });
  }

  /** Every case of the picked family, or of all of them, narrowed to the picked shape. */
  private List<StepStats> casesOf(String picked) {
    List<StepStats> cases = new ArrayList<StepStats>();
    for (String code : families) {
      if (ALL.equals(picked) || code.equals(picked)) {
        for (StepStats stepCase : drawnCases(code)) {
          if (ANY.equals(shape) || shape.equals(LastLayerCaseNames.group(stepCase.getCode()))) {
            cases.add(stepCase);
          }
        }
      }
    }
    return cases;
  }

  /** One family's cases that the app has a picture and a set for. */
  private List<StepStats> drawnCases(String family) {
    List<StepStats> drawn = new ArrayList<StepStats>();
    for (StepStats stepCase : statistics.getCases(family)) {
      if (DRAWN.containsKey(stepCase.getCode())) {
        drawn.add(stepCase);
      }
    }
    return drawn;
  }

  private long totalCost() {
    long lost = 0;
    for (StepStats stepCase : casesOf(family)) {
      if (enough(stepCase)) {
        lost += statistics.getTimeLostMs(stepCase.getCode());
      }
    }
    return lost;
  }

  private String cost(long lostMs) {
    return context.getString(R.string.analysis_cost, FormatterService.INSTANCE
        .formatFloat(lostMs / 1000d, 1));
  }

  /** Whether the column has a figure for this case at all, which only the cost column can lack. */
  private boolean quotable(StepStats stepCase, int column) {
    return column != COST_COLUMN || enough(stepCase);
  }

  /** Whether enough of the case has been seen for what it costs to be worth quoting. */
  private boolean enough(StepStats stepCase) {
    return stepCase.getCount() >= CoachPayloadBuilder.CASE_FLOOR;
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
      case COST_COLUMN:
        return enough(stepCase) ? statistics.getTimeLostMs(stepCase.getCode()) : 0;
      default:
        return stepCase.getCount();
    }
  }
}
