package com.cube.nanotimer.gui.widget;

import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

/**
 * The heading strip over a case list, which is also its sort.
 *
 * <p>Tapping a heading ranks the table by that column and tapping it again turns the table round,
 * so what a column is called and which one the list is in are the same piece of text. A control
 * above the table would have had to name the same four things a second time.
 *
 * <p>A column that has never been ranked opens at its own interesting end, which the caller says,
 * because the interesting end of a count is the biggest and the interesting end of a rep number is
 * the first one.
 */
public class CaseTableHeadings {

  /** Which way round the table is, said on the ranked heading. */
  private static final String DESCENDING = "▾";
  private static final String ASCENDING = "▴";

  private static final int[] HEADING_IDS = {R.id.tvCaseTableSortCount, R.id.tvCaseTableSortOne,
      R.id.tvCaseTableSortTwo, R.id.tvCaseTableSortThree};

  /** Told which ranking the reader has just asked for. */
  public interface Listener {
    void onRanked(int column, boolean descending);
  }

  private final View root;
  private final int[] labels;
  private final boolean[] opensDescending;
  private final Listener listener;

  private int column;
  private boolean descending;

  /**
   * @param root the view holding the strip, which is the screen or the table it was included in
   * @param labels one string per heading: the count, then the three figure columns. A label of 0
   *     is a column the rows do not carry, as {@link CaseRow#noCount()} leaves the count: it gets
   *     no heading and cannot be ranked by.
   * @param opensDescending for each, which end it opens at when it is first ranked by
   * @param column the column the table opens ranked by
   */
  public CaseTableHeadings(View root, int[] labels, boolean[] opensDescending, int column,
      Listener listener) {
    this.root = root;
    this.labels = labels;
    this.opensDescending = opensDescending;
    this.listener = listener;
    this.column = column;
    this.descending = opensDescending[column];
    for (int i = 0; i < HEADING_IDS.length; i++) {
      View heading = root.findViewById(HEADING_IDS[i]);
      if (labels[i] == 0) {
        heading.setVisibility(View.GONE);
        continue;
      }
      final int picked = i;
      heading.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          rank(picked);
        }
      });
    }
  }

  /**
   * Keeps the chevron's width at the end of the strip, for a table whose rows carry one. Without
   * it the headings sit a chevron to the right of the columns they name.
   */
  public void reserveChevron(boolean reserved) {
    root.findViewById(R.id.vCaseTableChevronSpace)
        .setVisibility(reserved ? View.INVISIBLE : View.GONE);
  }

  /** Names the list itself, on the line the headings share. */
  public void setLabel(int label) {
    ((TextView) root.findViewById(R.id.tvCaseTableLabel)).setText(label);
  }

  public int column() {
    return column;
  }

  public boolean descending() {
    return descending;
  }

  /** Draws the headings as they now stand. Called once the table has been ranked and rebuilt. */
  public void refresh() {
    for (int i = 0; i < HEADING_IDS.length; i++) {
      if (labels[i] == 0) {
        continue;
      }
      TextView heading = (TextView) root.findViewById(HEADING_IDS[i]);
      String label = root.getContext().getString(labels[i]);
      boolean ranked = i == column;
      heading.setText(ranked ? root.getContext().getString(R.string.drill_summary_column_sorted,
          label, descending ? DESCENDING : ASCENDING) : label);
      heading.setTextColor(ContextCompat.getColor(root.getContext(),
          ranked ? R.color.white : R.color.secondary_text));
    }
  }

  private void rank(int picked) {
    // The same heading again turns the table round; a fresh one opens at its own interesting end.
    descending = column == picked ? !descending : opensDescending[picked];
    column = picked;
    listener.onRanked(column, descending);
  }
}
