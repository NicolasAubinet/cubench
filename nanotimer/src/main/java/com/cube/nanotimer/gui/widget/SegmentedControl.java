package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.util.helper.GUIUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * A row of cells where exactly one is picked: the same control the cross solver's neutrality modes
 * are chosen with, made shared so a screen that needs three of them does not grow three copies.
 *
 * <p>The container is expected to carry {@code cross_segment_container} as its background, which is
 * the trough the picked cell sits in.
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
      TextView cell = cells.get(i);
      if (i == selection) {
        cell.setBackgroundResource(R.drawable.cross_segment_selected);
        cell.setTextColor(color(R.color.white));
        GUIUtils.setWeight(cell, Typeface.BOLD);
      } else {
        cell.setBackground(null);
        cell.setTextColor(color(R.color.secondary_text));
        GUIUtils.setWeight(cell, Typeface.NORMAL);
      }
    }
  }

  private int dp(int value) {
    return (int) (value * context.getResources().getDisplayMetrics().density);
  }

  private int color(int colorResId) {
    return ContextCompat.getColor(context, colorResId);
  }
}
