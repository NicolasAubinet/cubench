package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.cube.nanotimer.R;

/**
 * Puts the two halves of the cross drill screen — the cube, and everything there is to read about
 * the rep — one above the other standing up, and side by side on the phone's side.
 *
 * <p>It moves them between two containers rather than being two layout files, because the drill
 * screens declare {@code configChanges} for orientation: a rotation there never re-inflates, so a
 * {@code layout-land} variant would only ever reach a drill that was already lying down. Moving
 * the views keeps the rep, the drawn cube and the WebView page it is built into alive across the
 * turn, where re-inflating would reload the page under a running rep.
 *
 * <p>The reason there are two containers, rather than one whose orientation flips, is the scroll.
 * A ScrollView measures its child with no height limit, and inside one {@code layout_weight}
 * cannot shrink anything: that is how the cube well took the height the WebView had built at in
 * portrait and grew past three landscape screens. So the scrolling column is used standing up,
 * where the pieces fit and the scroll is only insurance, and lying down the halves sit in a plain
 * row that cannot grow, with the reading half scrolling inside itself.
 */
public class DrillPanesLayout extends FrameLayout {

  /** Two fifths of the width to the cube against three: at that height the well comes out square. */
  private static final float CUBE_SHARE = 2f;
  private static final float READING_SHARE = 3f;

  /** Between the two halves. */
  private static final int GAP_DP = 12;

  /** Between the pieces inside the reading half, as the layout writes them. */
  private static final int ROW_GAP_DP = 10;

  private int arrangedFor = Configuration.ORIENTATION_UNDEFINED;

  public DrillPanesLayout(Context context) {
    super(context);
  }

  public DrillPanesLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public DrillPanesLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    arrange();
  }

  @Override
  protected void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    arrange();
  }

  private void arrange() {
    int orientation = getResources().getConfiguration().orientation;
    if (orientation == arrangedFor) {
      return;
    }
    arrangedFor = orientation;
    boolean sideBySide = orientation == Configuration.ORIENTATION_LANDSCAPE;

    View scroll = findViewById(R.id.drillPanesScroll);
    ViewGroup row = findViewById(R.id.drillPanesRow);
    ViewGroup home = sideBySide ? row : (ViewGroup) findViewById(R.id.drillPanesStack);

    View cube = findViewById(R.id.drillCubePane);
    View reading = findViewById(R.id.drillReadingPane);
    int gap = Math.round(GAP_DP * getResources().getDisplayMetrics().density);

    LinearLayout.LayoutParams cubeParams = new LinearLayout.LayoutParams(
        sideBySide ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
        sideBySide ? LinearLayout.LayoutParams.MATCH_PARENT : 0,
        sideBySide ? CUBE_SHARE : 1f);

    LinearLayout.LayoutParams readingParams = new LinearLayout.LayoutParams(
        sideBySide ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
        sideBySide ? LinearLayout.LayoutParams.MATCH_PARENT
            : LinearLayout.LayoutParams.WRAP_CONTENT,
        sideBySide ? READING_SHARE : 0f);
    if (sideBySide) {
      readingParams.leftMargin = gap;
    }

    moveTo(home, cube, cubeParams);
    moveTo(home, reading, readingParams);

    scroll.setVisibility(sideBySide ? GONE : VISIBLE);
    row.setVisibility(sideBySide ? VISIBLE : GONE);

    arrangeStatus(sideBySide);

    // Lying down, the reading half is what has to give: the row it sits in cannot grow.
    View readingScroll = findViewById(R.id.drillReadingScroll);
    LinearLayout.LayoutParams scrollParams =
        (LinearLayout.LayoutParams) readingScroll.getLayoutParams();
    scrollParams.height = sideBySide ? 0 : LinearLayout.LayoutParams.WRAP_CONTENT;
    scrollParams.weight = sideBySide ? 1f : 0f;
    readingScroll.setLayoutParams(scrollParams);
  }

  /**
   * The state of the drill and the figure the last rep scored: a line each standing up, and one
   * line between them lying down, where the column is three times as wide as it is tall and the
   * 40dp that saves is what the solution below them needs to arrive whole.
   */
  private void arrangeStatus(boolean sideBySide) {
    View pill = findViewById(R.id.drillStatusPill);
    View lastRep = findViewById(R.id.llDrillLastRep);
    ViewGroup statusRow = findViewById(R.id.drillStatusRow);
    ViewGroup column = (ViewGroup) statusRow.getParent();
    int gap = Math.round(ROW_GAP_DP * getResources().getDisplayMetrics().density);

    statusRow.setVisibility(sideBySide ? VISIBLE : GONE);
    if (sideBySide) {
      moveTo(statusRow, pill, side(1f));
      moveTo(statusRow, lastRep, side(1f));
      return;
    }
    // Back under one another, in the order they are written in the layout.
    moveTo(column, pill, stacked(gap), column.indexOfChild(statusRow) + 1);
    moveTo(column, lastRep, stacked(gap), column.indexOfChild(statusRow) + 2);
  }

  private static LinearLayout.LayoutParams side(float weight) {
    return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
  }

  private static LinearLayout.LayoutParams stacked(int topMargin) {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.topMargin = topMargin;
    return params;
  }

  private void moveTo(ViewGroup home, View pane, ViewGroup.LayoutParams params) {
    moveTo(home, pane, params, -1);
  }

  private void moveTo(ViewGroup home, View pane, ViewGroup.LayoutParams params, int index) {
    ViewGroup parent = (ViewGroup) pane.getParent();
    if (parent != home) {
      if (parent != null) {
        parent.removeView(pane);
      }
      home.addView(pane, index);
    }
    pane.setLayoutParams(params);
  }
}
