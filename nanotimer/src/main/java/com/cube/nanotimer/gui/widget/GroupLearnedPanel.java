package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.cube.nanotimer.R;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.step.LastLayerAlgorithms;
import com.cube.nanotimer.step.OllShapes;
import com.cube.nanotimer.util.OllGroups;
import com.cube.nanotimer.util.view.GroupLearnedEntrance;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The moment a coach learning drill completes a shape group: the group's cases together, a line
 * saying the shape is now always solvable in one algorithm, how far into OLL that brings the
 * solver, and a pip per shape group with the one just completed lighting last. Laid over the drill,
 * which waits until it is closed.
 */
public final class GroupLearnedPanel {

  private static final int CASES_PER_ROW = 4;

  private GroupLearnedPanel() {
  }

  /**
   * @param learned every case the solver knows, this group's included ({@code oll_7} codes)
   */
  public static void show(Activity activity, String group, Set<String> learned,
      final Runnable onClosed) {
    final ViewGroup root = activity.findViewById(android.R.id.content);
    final View panel = LayoutInflater.from(activity).inflate(R.layout.group_learned_panel, root,
        false);
    List<String> cases = OllShapes.casesOf(group);
    int learnedOlls = 0;
    for (String[] row : LastLayerAlgorithms.ORIENTATIONS) {
      learnedOlls += learned.contains("oll_" + row[0]) ? 1 : 0;
    }
    int total = LastLayerAlgorithms.ORIENTATIONS.length;
    ((TextView) panel.findViewById(R.id.tvGroupLearnedTitle)).setText(group);
    ((TextView) panel.findViewById(R.id.tvGroupLearnedBody)).setText(activity.getResources()
        .getQuantityString(R.plurals.group_learned_body, cases.size(), cases.size(), group));
    ((TextView) panel.findViewById(R.id.tvGroupLearnedProgress))
        .setText(activity.getString(R.string.group_learned_progress, learnedOlls, total));
    ((TextView) panel.findViewById(R.id.tvGroupLearnedGroups)).setText(activity.getString(
        R.string.group_learned_groups, OllGroups.countComplete(learned),
        OllGroups.getAll().size()));
    View newPip = addPips(activity, (LinearLayout) panel.findViewById(R.id.llGroupLearnedGroups),
        group, learned);
    List<View> charts = addCases(activity, (LinearLayout) panel.findViewById(
        R.id.llGroupLearnedCases), cases);
    ProgressBar bar = panel.findViewById(R.id.pbGroupLearned);
    int before = Math.round(1000f * (learnedOlls - cases.size()) / total);
    int after = Math.round(1000f * learnedOlls / total);
    bar.setProgress(before);
    panel.findViewById(R.id.btGroupLearnedContinue).setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            v.setEnabled(false); // a second tap would close it twice and skip a case
            GroupLearnedEntrance.leave(panel, new Runnable() {
              @Override
              public void run() {
                root.removeView(panel);
                if (onClosed != null) {
                  onClosed.run();
                }
              }
            });
          }
        });
    root.addView(panel);
    GroupLearnedEntrance.play(panel, panel.findViewById(R.id.llGroupLearnedCard),
        panel.findViewById(R.id.flGroupLearnedBadge), charts, bar, after, newPip,
        panel.findViewById(R.id.vGroupLearnedConfetti));
  }

  /** @return the pip of the group just completed, drawn unlit until the entrance lights it */
  private static View addPips(Activity activity, LinearLayout row, String group,
      Set<String> learned) {
    int size = activity.getResources().getDimensionPixelSize(R.dimen.group_learned_pip);
    int gap = activity.getResources().getDimensionPixelSize(R.dimen.group_learned_pip_gap);
    View newPip = null;
    for (String each : OllGroups.getAll()) {
      View pip = new View(activity);
      boolean isNew = each.equals(group);
      pip.setBackgroundResource(OllGroups.isComplete(each, learned) && !isNew
          ? R.drawable.group_learned_pip_on : R.drawable.group_learned_pip_off);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
      params.setMargins(gap / 2, 0, gap / 2, 0);
      row.addView(pip, params);
      if (isNew) {
        newPip = pip;
      }
    }
    return newPip;
  }

  private static List<View> addCases(Activity activity, LinearLayout rows, List<String> cases) {
    int size = activity.getResources().getDimensionPixelSize(R.dimen.group_learned_case);
    int gap = activity.getResources().getDimensionPixelSize(R.dimen.group_learned_case_gap);
    List<View> charts = new ArrayList<View>();
    LinearLayout row = null;
    for (int i = 0; i < cases.size(); i++) {
      if (i % CASES_PER_ROW == 0) {
        row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        rows.addView(row, params);
      }
      LastLayerCaseView chart = new LastLayerCaseView(activity);
      chart.setDiagram(LastLayerDiagram.forCase(cases.get(i)));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
      params.setMargins(gap, gap, gap, gap);
      row.addView(chart, params);
      charts.add(chart);
    }
    return charts;
  }
}
