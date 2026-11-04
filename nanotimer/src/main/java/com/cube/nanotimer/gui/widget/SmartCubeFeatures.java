package com.cube.nanotimer.gui.widget;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cube.nanotimer.R;
import com.cube.nanotimer.util.coach.CoachBetaLook;

import java.util.ArrayList;
import java.util.List;

/**
 * What a smart cube brings, listed the same way wherever it is told: the news sheet after an update
 * and the connect dialog's intro. Fills the {@code smart_cube_features} layout.
 */
public final class SmartCubeFeatures {

  /** The smart cube's features, icon, title and sentence, in the order they are listed. */
  private static final int[][] FEATURES = {
      {R.drawable.ic_intro_timing, R.string.news_timing_title, R.string.news_timing_body},
      {R.drawable.ic_intro_scramble, R.string.news_scramble_title, R.string.news_scramble_body},
      {R.drawable.ic_intro_breakdown, R.string.news_breakdown_title, R.string.news_breakdown_body},
      {R.drawable.ic_solve_replay, R.string.news_replay_title, R.string.news_replay_body},
      {R.drawable.menu_analysis, R.string.news_analysis_title, R.string.news_analysis_body},
      {R.drawable.ic_menu_drill, R.string.news_drills_title, R.string.news_drills_body},
      {R.drawable.menu_coach, R.string.news_coach_title, R.string.news_coach_body}};

  private SmartCubeFeatures() {
  }

  /**
   * Fills the list under {@code root}, once.
   *
   * @return the feature rows, for an entrance animation
   */
  public static List<View> fill(View root, LayoutInflater inflater) {
    List<View> rows = new ArrayList<View>();
    LinearLayout features = root.findViewById(R.id.newsFeatures);
    for (int[] feature : FEATURES) {
      View row = inflater.inflate(R.layout.smart_cube_news_row, features, false);
      ((ImageView) row.findViewById(R.id.imgNewsRow)).setImageResource(feature[0]);
      ((TextView) row.findViewById(R.id.tvNewsRowTitle)).setText(feature[1]);
      ((TextView) row.findViewById(R.id.tvNewsRowBody)).setText(feature[2]);
      features.addView(row);
      rows.add(row);
    }
    // Read when drawn, so a beta that has ended takes its line away.
    CoachBetaLook look = CoachBetaLook.of(root.getContext());
    root.findViewById(R.id.llNewsCoachBeta).setVisibility(
        look != null && look.newsLine() ? View.VISIBLE : View.GONE);
    return rows;
  }
}
