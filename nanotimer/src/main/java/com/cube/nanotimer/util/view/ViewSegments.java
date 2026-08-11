package com.cube.nanotimer.util.view;

import android.graphics.Typeface;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.util.helper.GUIUtils;

/**
 * Paints one option of a segmented control (the {@code ViewSegment} styles, inside a
 * {@code view_segment_group} container): the chosen one is filled and picked out, the rest carry
 * no background at all. Shared so every such control in the app reads the same way.
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
    segment.setTextColor(ContextCompat.getColor(segment.getContext(),
        chosen ? R.color.white : R.color.secondary_text));
    GUIUtils.setWeight(segment, chosen ? Typeface.BOLD : Typeface.NORMAL);
  }
}
