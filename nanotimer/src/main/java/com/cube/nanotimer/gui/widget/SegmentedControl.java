package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cube.nanotimer.R;
import com.cube.nanotimer.util.helper.GUIUtils;
import com.cube.nanotimer.util.view.ViewSegments;

import java.util.ArrayList;
import java.util.List;

/**
 * A row of cells where exactly one is picked: the same control the cross solver's neutrality modes
 * are chosen with, made shared so a screen that needs three of them does not grow three copies.
 *
 * <p>The container is expected to carry {@code view_segment_group} as its background, which is the
 * trough the picked cell sits in.
 */
public class SegmentedControl {

  public interface Listener {
    void onSegmentPicked(int index);
  }

  private final Context context;
  private final Listener listener;
  private final List<TextView> cells = new ArrayList<TextView>();

  private int selection;

  public SegmentedControl(Context context, LinearLayout container, String[] labels,
      Listener listener) {
    this.context = context;
    this.listener = listener;
    for (int i = 0; i < labels.length; i++) {
      final int index = i;
      TextView cell = GUIUtils.newTextView(context);
      cell.setText(labels[i]);
      cell.setTextSize(14);
      cell.setGravity(Gravity.CENTER);
      cell.setPadding(dp(8), dp(8), dp(8), dp(8));
      cell.setLayoutParams(
          new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      cell.setClickable(true);
      cell.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (selection != index) {
            setSelection(index);
            listener.onSegmentPicked(index);
          }
        }
      });
      cells.add(cell);
      container.addView(cell);
    }
    refresh();
  }

  /**
   * Marks a cell as a paid one, with the same star the drawer puts on a paid row. The mark rides at
   * the cell's own edge rather than beside the label, so a marked segment is the same width as the
   * rest.
   */
  public void setSegmentMarked(int index, boolean marked) {
    cells.get(index).setCompoundDrawablesWithIntrinsicBounds(0, 0,
        marked ? R.drawable.premium_mark : 0, 0);
  }

  /** Takes a cell out of the row, for a choice that does not apply to what is being set up. */
  public void setSegmentVisible(int index, boolean visible) {
    cells.get(index).setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  /** Shows a pick without announcing it, for a caller restoring one. */
  public void setSelection(int index) {
    selection = index;
    refresh();
  }

  public int getSelection() {
    return selection;
  }

  private void refresh() {
    for (int i = 0; i < cells.size(); i++) {
      ViewSegments.style(cells.get(i), i == selection);
    }
  }

  private int dp(int value) {
    return (int) (value * context.getResources().getDisplayMetrics().density);
  }
}
