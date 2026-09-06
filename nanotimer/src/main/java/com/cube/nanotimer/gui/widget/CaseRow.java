package com.cube.nanotimer.gui.widget;

import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.cube.nanotimer.R;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.view.DrillSplitBarView;
import com.cube.nanotimer.util.view.StepPalette;

/**
 * One line of a case list, filled part by part.
 *
 * <p>Four screens draw this line: a rep of a finished drill, a drilled case, a case in real solves,
 * and a step of the solve. They differ in which parts they have to say and in what the three columns
 * hold, never in how the line is put together, so the differences are calls that are made or not
 * made rather than a layout each.
 *
 * <p>The parts left unset stay hidden, so a caller says only what its screen has. The three figure
 * columns and the name are the only ones every screen fills.
 */
public class CaseRow {

  /** How many figure columns a row carries, which is what makes the headings the sort. */
  public static final int COLUMNS = 3;

  private static final int[] VALUE_IDS = {R.id.tvCaseRowValueOne, R.id.tvCaseRowValueTwo,
      R.id.tvCaseRowValueThree};

  /** What a column that is not the ranked one is worth: still coloured, but standing back. */
  private static final float UNRANKED_ALPHA = 0.6f;

  private final View line;

  public static View inflate(LayoutInflater inflater, ViewGroup parent) {
    return inflater.inflate(R.layout.case_row, parent, false);
  }

  public CaseRow(View line) {
    this.line = line;
  }

  public View view() {
    return line;
  }

  /** How much is behind the figures, or where a rep fell in its drill. */
  public CaseRow count(CharSequence count) {
    return text(R.id.tvCaseRowCount, count, true);
  }

  /** A line with no count: the picture slides left into the space it leaves. */
  public CaseRow noCount() {
    line.findViewById(R.id.tvCaseRowCount).setVisibility(View.GONE);
    return this;
  }

  public CaseRow chart(String caseCode) {
    ((LastLayerCaseView) line.findViewById(R.id.vCaseRowChart))
        .setDiagram(LastLayerDiagram.forCase(caseCode));
    return this;
  }

  /** What a step carries in place of a chart, since a step of the solve has no picture. */
  public CaseRow dot(int color) {
    line.findViewById(R.id.vCaseRowChart).setVisibility(View.GONE);
    View dot = line.findViewById(R.id.vCaseRowDot);
    dot.setVisibility(View.VISIBLE);
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(line.getResources().getDimension(R.dimen.case_row_dot_corner));
    shape.setColor(color);
    dot.setBackground(shape);
    return this;
  }

  public CaseRow name(CharSequence name) {
    return text(R.id.tvCaseRowName, name, true);
  }

  /** What has to be said about the line before its figures are read. */
  public CaseRow note(CharSequence note) {
    return text(R.id.tvCaseRowNote, note, note != null && note.length() > 0);
  }

  public CaseRow chip(CharSequence chip) {
    return text(R.id.tvCaseRowChip, chip, chip != null && chip.length() > 0);
  }

  /**
   * The meter, with the two figures beside it: where the case's time went, in the family's own hue.
   */
  public CaseRow meter(long recognitionMs, long executionMs, int hue) {
    bar(recognitionMs, executionMs, hue);
    TextView recognition = line.findViewById(R.id.tvCaseRowRecognition);
    recognition.setVisibility(View.VISIBLE);
    recognition.setText(FormatterService.INSTANCE.formatSolveTime(recognitionMs));
    recognition.setTextColor(StepPalette.dim(hue));
    TextView execution = line.findViewById(R.id.tvCaseRowExecution);
    execution.setVisibility(View.VISIBLE);
    execution.setText(FormatterService.INSTANCE.formatSolveTime(executionMs));
    execution.setTextColor(hue);
    return this;
  }

  /**
   * The bar alone, for a row whose columns already print the two halves. It takes a fixed width
   * rather than the row's: it is the shape of the rep and not its size, so every one is measured
   * against the same length, and at the row's full width a dozen of them read as progress bars.
   */
  public CaseRow shape(long recognitionMs, long executionMs, int hue) {
    bar(recognitionMs, executionMs, hue);
    line.findViewById(R.id.tvCaseRowRecognition).setVisibility(View.GONE);
    line.findViewById(R.id.tvCaseRowExecution).setVisibility(View.GONE);
    View bar = line.findViewById(R.id.vCaseRowBar);
    ViewGroup.LayoutParams params = bar.getLayoutParams();
    params.width = line.getResources().getDimensionPixelSize(R.dimen.case_row_shape_width);
    ((android.widget.LinearLayout.LayoutParams) params).weight = 0;
    bar.setLayoutParams(params);
    return this;
  }

  /** A line with nothing to say about where its time went. */
  public CaseRow noMeter() {
    line.findViewById(R.id.llCaseRowMeter).setVisibility(View.GONE);
    return this;
  }

  /** Hides the bar without hiding the meter, for a line that has no time to divide. */
  public CaseRow hideBar(boolean hidden) {
    line.findViewById(R.id.vCaseRowBar)
        .setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
    return this;
  }

  /** @param column 0 to {@link #COLUMNS} - 1, left to right */
  public CaseRow value(int column, CharSequence value, int color) {
    TextView view = line.findViewById(VALUE_IDS[column]);
    view.setText(value);
    view.setTextColor(color);
    return this;
  }

  public TextView valueView(int column) {
    return line.findViewById(VALUE_IDS[column]);
  }

  /** The promise that the row leads somewhere, on the tables whose rows do. */
  public CaseRow chevron() {
    line.findViewById(R.id.ivCaseRowChevron).setVisibility(View.VISIBLE);
    return this;
  }

  /**
   * Says which column the table is ranked by, by standing the others back: the colours are one
   * gradient throughout, so something has to say which ranking the list is in.
   *
   * @param column 0 for the count, 1 to {@link #COLUMNS} for a figure, negative for none
   */
  public CaseRow rank(int column) {
    line.findViewById(R.id.tvCaseRowCount).setAlpha(column == 0 ? 1f : UNRANKED_ALPHA);
    for (int i = 0; i < COLUMNS; i++) {
      valueView(i).setAlpha(i + 1 == column ? 1f : UNRANKED_ALPHA);
    }
    return this;
  }

  /**
   * Strikes the line through and turns it down whole: a rep the reader has thrown out is still
   * legible and plainly not one of the ones being read. Turned down whole rather than in parts,
   * since the chart's own dim drops its well and leaves a smudge on a row with no card.
   */
  public CaseRow struck(boolean struck, float alpha) {
    line.setAlpha(struck ? alpha : 1f);
    strikeThrough(line.findViewById(R.id.tvCaseRowName), struck);
    strikeThrough(line.findViewById(R.id.tvCaseRowCount), struck);
    for (int i = 0; i < COLUMNS; i++) {
      strikeThrough(valueView(i), struck);
    }
    return this;
  }

  private void bar(long recognitionMs, long executionMs, int hue) {
    ((DrillSplitBarView) line.findViewById(R.id.vCaseRowBar))
        .setSplit(recognitionMs, executionMs, hue);
  }

  private CaseRow text(int id, CharSequence value, boolean shown) {
    TextView view = line.findViewById(id);
    view.setText(value);
    view.setVisibility(shown ? View.VISIBLE : View.GONE);
    return this;
  }

  private static void strikeThrough(TextView view, boolean struck) {
    view.setPaintFlags(struck ? view.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
        : view.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
  }
}
