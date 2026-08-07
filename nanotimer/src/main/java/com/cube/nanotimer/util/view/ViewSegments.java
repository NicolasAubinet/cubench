package com.cube.nanotimer.util.view;

import android.graphics.Typeface;
import android.widget.TextView;

import com.cube.nanotimer.R;

/**
 * Paints one option of a segmented control (the {@code ViewSegment} styles, inside a
 * {@code view_segment_group} container): the chosen one is filled and picked out, the rest carry
 * no background at all. Shared so every such control on a dialog reads the same way.
 */
public class ViewSegments {

  private ViewSegments() {
  }

  public static void style(TextView segment, boolean chosen) {
    if (chosen) {
      segment.setBackgroundResource(R.drawable.view_segment_active);
    } else {
      segment.setBackground(null);
    }
    segment.setTextColor(segment.getResources().getColor(
        chosen ? R.color.white : R.color.secondary_text));
    segment.setTypeface(null, chosen ? Typeface.BOLD : Typeface.NORMAL);
  }
}
