package com.cube.nanotimer.gui.widget;

import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.cases.CaseGroup;
import com.cube.nanotimer.cases.CaseSet;
import com.cube.nanotimer.cases.CaseSets;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.DrillRepParts;
import com.cube.nanotimer.util.view.DrillSplitBarView;

import java.util.List;

/**
 * The card over the case lists (In solves, In drills, a set sorted by a mean): a finished drill's
 * card, lit in the family's colour, so a list of times reads like the drill that made them. It
 * holds the mean with its recognition/execution split and two cells, a count and the best; a list
 * about one case also carries the case on top, which opens its sheet.
 */
public final class CaseListHero {

  private static final int[] CELL_KEYS = {R.id.tvDrillCellKeyOne, R.id.tvDrillCellKeyTwo};
  private static final int[] CELL_VALUES = {R.id.tvDrillCellValueOne, R.id.tvDrillCellValueTwo};
  private static final int[] CELL_SUBS = {R.id.tvDrillCellSubOne, R.id.tvDrillCellSubTwo};

  private final View root;
  private final int hue;

  /** @param root the layout holding case_list_hero */
  public CaseListHero(View root, int hue) {
    this.root = root;
    this.hue = hue;
    StepCardBackground.paint(root.findViewById(R.id.llCaseListHero), hue);
    root.findViewById(R.id.tvDrillSummaryMode).setVisibility(View.GONE);
    ((TextView) root.findViewById(R.id.tvDrillSummaryEyebrow)).setTextColor(hue);
    ((TextView) root.findViewById(R.id.tvCaseListEyebrow)).setTextColor(hue);
    ((TextView) root.findViewById(R.id.tvDrillSummaryHeroUnit))
        .setText(R.string.drill_summary_cell_average);
  }

  /** A list about one case: its picture, set and name on top, opening its sheet when tapped. */
  public void showCase(String caseCode, View.OnClickListener open) {
    root.findViewById(R.id.llCaseListHead).setVisibility(View.VISIBLE);
    root.findViewById(R.id.llCaseListHead).setOnClickListener(open);
    root.findViewById(R.id.tvDrillSummaryEyebrow).setVisibility(View.GONE);
    LastLayerCaseView chart = root.findViewById(R.id.vCaseListChart);
    if (CaseAlgorithms.isPair(caseCode)) {
      chart.setVisibility(View.GONE);
      String picture = CaseAlgorithms.pairPicture(caseCode);
      if (picture != null) {
        PairCaseView pair = root.findViewById(R.id.vCaseListPair);
        pair.setVisibility(View.VISIBLE);
        pair.setPicture(picture);
      }
    } else {
      chart.setDiagram(LastLayerDiagram.forCase(caseCode));
    }
    ((TextView) root.findViewById(R.id.tvCaseListName))
        .setText(Utils.toSmartCubeCaseHeadline(root.getContext(), caseCode));
    String kicker = kicker(caseCode);
    TextView eyebrow = root.findViewById(R.id.tvCaseListEyebrow);
    eyebrow.setText(kicker);
    eyebrow.setVisibility(kicker == null ? View.GONE : View.VISIBLE);
  }

  /** The line under the case's name: which solves or which period the figures are from. */
  public void setSub(CharSequence sub) {
    ((TextView) root.findViewById(R.id.tvCaseListSub)).setText(sub);
  }

  /** A list of a whole set: no case on top, only the line over the mean. */
  public void setEyebrow(CharSequence text) {
    ((TextView) root.findViewById(R.id.tvDrillSummaryEyebrow)).setText(text);
  }

  /** Whether there are figures at all; without them the card is only the case. */
  public void showFigures(boolean shown) {
    int visibility = shown ? View.VISIBLE : View.GONE;
    root.findViewById(R.id.llDrillSummaryHero).setVisibility(visibility);
    root.findViewById(R.id.llDrillSummarySplit).setVisibility(visibility);
    root.findViewById(R.id.llDrillSummaryCells).setVisibility(visibility);
  }

  /** The mean, large, and the bar of where it went. */
  public void setMean(long recognitionMs, long executionMs) {
    ((TextView) root.findViewById(R.id.tvDrillSummaryHero))
        .setText(FormatterService.INSTANCE.formatSolveTime(recognitionMs + executionMs));
    List<DrillRepParts.Part> parts = DrillRepParts.split(recognitionMs, executionMs, hue);
    DrillRepParts.draw((DrillSplitBarView) root.findViewById(R.id.vDrillSummarySplit), parts);
    TextView first = root.findViewById(R.id.tvDrillSummaryRecognition);
    first.setText(DrillRepParts.words(root.getContext(), parts.subList(0, 1)));
    first.setGravity(Gravity.START);
    ((TextView) root.findViewById(R.id.tvDrillSummaryExecution))
        .setText(DrillRepParts.words(root.getContext(), parts.subList(1, 2)));
  }

  /** The count: how many times the figures are made of, and of what. */
  public void setCount(CharSequence key, int count, CharSequence sub) {
    setCell(0, key, String.valueOf(count), sub);
  }

  /**
   * The quickest time, in the green the lists give it.
   *
   * @param caseCode the case it was, pictured beside it, or null on a list of one case
   */
  public void setBest(long bestMs, CharSequence sub, String caseCode) {
    setCell(1, root.getContext().getString(R.string.drill_summary_cell_best),
        FormatterService.INSTANCE.formatSolveTime(bestMs), sub);
    ((TextView) root.findViewById(R.id.tvDrillCellValueTwo))
        .setTextColor(ContextCompat.getColor(root.getContext(), R.color.session_best));
    View caseView = root.findViewById(R.id.llDrillCellCaseTwo);
    caseView.setVisibility(caseCode == null ? View.GONE : View.VISIBLE);
    if (caseCode == null) {
      return;
    }
    LastLayerCaseView chart = root.findViewById(R.id.vDrillCellChartTwo);
    boolean pictured = !CaseAlgorithms.isPair(caseCode);
    chart.setVisibility(pictured ? View.VISIBLE : View.GONE);
    if (pictured) {
      chart.setDiagram(LastLayerDiagram.forCase(caseCode));
    }
    ((TextView) root.findViewById(R.id.tvDrillCellCaseTwo))
        .setText(DrillStatsTable.nameOf(root.getContext(), caseCode));
  }

  private void setCell(int index, CharSequence key, CharSequence value, CharSequence sub) {
    ((TextView) root.findViewById(CELL_KEYS[index])).setText(key);
    ((TextView) root.findViewById(CELL_VALUES[index])).setText(value);
    TextView tvSub = root.findViewById(CELL_SUBS[index]);
    tvSub.setText(sub);
    tvSub.setVisibility(sub == null || sub.length() == 0 ? View.GONE : View.VISIBLE);
  }

  /** The case's shape or group, as the case sheet puts it over the name; the name says the set. */
  private String kicker(String caseCode) {
    CaseSet set = CaseSets.ofCase(caseCode);
    if (set == null) {
      return null;
    }
    CaseGroup group = set.groupOf(caseCode);
    return set.shape(caseCode) != null ? set.shape(caseCode)
        : group != null ? group.getName(root.getContext()) : null;
  }
}
