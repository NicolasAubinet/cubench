package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.DrillActivity;
import com.cube.nanotimer.gui.DrillScreenActivity;
import com.cube.nanotimer.gui.DrillSetupActivity;
import com.cube.nanotimer.smartcube.drill.DailyRun;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.SolveType;

import java.util.List;
import java.util.Locale;

/**
 * The daily drill's part of the Today card: each block's cases with why each is here, and the
 * button that runs them. Drawn from {@code CoachDaily}'s kept answer.
 */
public final class CoachDailyView {

  /** About how long a rep takes with its pause, for the minutes the card quotes. */
  private static final int SECONDS_A_REP = 20;

  private final Activity activity;
  private final View section;
  private final LayoutInflater inflater;

  public CoachDailyView(Activity activity, View section) {
    this.activity = activity;
    this.section = section;
    this.inflater = LayoutInflater.from(activity);
  }

  /** Today's blocks; empty says nothing is due, null hides the section. */
  public void show(final SolveType solveType, final List<DailyRun.Block> blocks) {
    if (blocks == null) {
      section.setVisibility(View.GONE);
      return;
    }
    section.setVisibility(View.VISIBLE);
    LinearLayout rows = section.findViewById(R.id.llCoachDailyBlocks);
    rows.removeAllViews();
    Button start = section.findViewById(R.id.buCoachDailyStart);
    TextView reps = section.findViewById(R.id.tvCoachDailyReps);
    section.findViewById(R.id.tvCoachDailyNone)
        .setVisibility(blocks.isEmpty() ? View.VISIBLE : View.GONE);
    start.setVisibility(blocks.isEmpty() ? View.GONE : View.VISIBLE);
    reps.setVisibility(blocks.isEmpty() ? View.GONE : View.VISIBLE);
    if (blocks.isEmpty()) {
      return;
    }
    int total = 0;
    for (DailyRun.Block block : blocks) {
      addBlock(rows, block);
      for (DailyRun.Entry entry : block.getEntries()) {
        total += entry.isReview() ? DailyRun.REVIEW_RUN : entry.getReps();
      }
    }
    reps.setText(activity.getString(R.string.coach_daily_reps, total,
        Math.max(1, Math.round(total * SECONDS_A_REP / 60f))));
    start.setText(R.string.coach_daily_start);
    start.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(activity, DrillActivity.class);
        intent.putExtra(DrillActivity.EXTRA_SPEC,
            DailyRun.spec(blocks, activity.getString(R.string.coach_daily_title)).toJson());
        intent.putExtra(DrillActivity.EXTRA_DAILY, DailyRun.toJson(blocks));
        intent.putExtra(DrillActivity.EXTRA_LAYER_FACE, DrillSetupActivity.layerFace().name());
        intent.putExtra(DrillActivity.EXTRA_SOLVE_TYPE, solveType);
        intent.putExtra(DrillScreenActivity.EXTRA_RECORDING, true);
        activity.startActivity(intent);
      }
    });
  }

  private void addBlock(LinearLayout parent, DailyRun.Block block) {
    LinearLayout column = new LinearLayout(activity);
    column.setOrientation(LinearLayout.VERTICAL);
    TextView family = new TextView(activity);
    family.setText(Utils.toSmartCubeStepLocalizedName(activity, block.getFamily(), 0));
    family.setTextColor(activity.getResources().getColor(R.color.secondary_text));
    family.setTextSize(12.5f);
    LinearLayout.LayoutParams familyAt = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    familyAt.topMargin = activity.getResources().getDimensionPixelSize(R.dimen.space_m);
    column.addView(family, familyAt);
    LinearLayout row = new LinearLayout(activity);
    row.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout.LayoutParams rowAt = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    rowAt.topMargin = activity.getResources().getDimensionPixelSize(R.dimen.space_s);
    column.addView(row, rowAt);
    for (DailyRun.Entry entry : block.getEntries()) {
      View cell = inflater.inflate(R.layout.coach_daily_case, row, false);
      LastLayerDiagram diagram = LastLayerDiagram.forCase(entry.getCode());
      LastLayerCaseView chart = cell.findViewById(R.id.vCoachDailyChart);
      chart.setDiagram(diagram);
      chart.setVisibility(diagram == null ? View.INVISIBLE : View.VISIBLE);
      ((TextView) cell.findViewById(R.id.tvCoachDailyName)).setText(shortName(entry.getCode()));
      ((TextView) cell.findViewById(R.id.tvCoachDailyReason)).setText(
          reason(activity, entry, "\n"));
      row.addView(cell);
    }
    // Fewer than five cases keep a chart's width rather than stretching across the card.
    for (int i = block.getEntries().size(); i < 5; i++) {
      View spacer = new View(activity);
      row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
    }
    parent.addView(column);
  }

  /** Why a case is in today's drill and the figure saying so, joined by {@code between}. */
  public static String reason(Context context, DailyRun.Entry entry, String between) {
    String word;
    String figure = null;
    String reason = entry.getReason();
    if (DailyRun.REVIEW.equals(reason) || DailyRun.SLIP.equals(reason)) {
      word = context.getString(DailyRun.REVIEW.equals(reason)
          ? R.string.known_algorithms_status_review : R.string.coach_daily_slip);
      figure = entry.getDays() == null ? null : daysAgo(context, entry.getDays().intValue());
    } else if (DailyRun.PAUSE.equals(reason)) {
      word = context.getString(R.string.coach_daily_pause);
      figure = entry.getRecognitionMs() == null ? null
          : String.format(Locale.getDefault(), "%.1f s", entry.getRecognitionMs() / 1000.0);
    } else if (DailyRun.DUE.equals(reason)) {
      word = context.getString(R.string.coach_daily_due);
      figure = entry.getDays() == null ? null
          : context.getString(R.string.coach_daily_days, entry.getDays());
    } else if (DailyRun.FOCUS.equals(reason)) {
      word = context.getString(R.string.coach_daily_focus);
    } else {
      word = context.getString(R.string.coach_daily_cost);
    }
    return figure == null ? word : word + between + figure;
  }

  private static String daysAgo(Context context, int days) {
    return days <= 0 ? context.getString(R.string.coach_daily_today)
        : days == 1 ? context.getString(R.string.coach_daily_yesterday)
        : context.getString(R.string.coach_daily_days_ago, days);
  }

  private static String shortName(String code) {
    String name = code.substring(code.indexOf('_') + 1);
    return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.US) + name.substring(1);
  }
}
