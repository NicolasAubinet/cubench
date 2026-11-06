package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.coach.FocusProgress;
import com.cube.nanotimer.coach.StoredCoachPlan;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.CoachDay;
import com.cube.nanotimer.util.coach.CoachDayText;
import com.cube.nanotimer.util.coach.CoachDays;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.vo.SolveType;

/**
 * The Plan tab's Today card: today's solves of the plan's solve type and how each focus has moved
 * since the plan, read on the phone ({@link CoachDays}) each time the tab is drawn.
 */
public final class CoachTodayView {

  private final Activity activity;
  private final View card;
  private final LayoutInflater inflater;
  private final CoachDayText text;
  /** Bumped by every read, so a slow read cannot draw over a newer one. */
  private int reads;

  public CoachTodayView(Activity activity, View card) {
    this.activity = activity;
    this.card = card;
    this.inflater = LayoutInflater.from(activity);
    this.text = new CoachDayText(activity);
  }

  /** Reads today for the plan and draws it; null hides the card. */
  public void load(SolveType solveType, StoredCoachPlan plan) {
    final int read = ++reads;
    if (plan == null) {
      card.setVisibility(View.GONE);
      return;
    }
    long now = System.currentTimeMillis();
    CoachDays.read(solveType, plan, CoachDays.dayStart(now), new DataCallback<CoachDay>() {
      @Override
      public void onData(final CoachDay day) {
        activity.runOnUiThread(() -> {
          if (read == reads && !activity.isFinishing()) {
            show(day);
          }
        });
      }
    });
  }

  public void hide() {
    reads++;
    card.setVisibility(View.GONE);
  }

  void show(CoachDay day) {
    ((TextView) card.findViewById(R.id.tvCoachTodayDate)).setText(DateUtils.formatDateTime(
        activity, System.currentTimeMillis(), DateUtils.FORMAT_SHOW_WEEKDAY));
    showFigures(day);
    TextView none = card.findViewById(R.id.tvCoachTodayNone);
    none.setVisibility(day.getSolveCount() == 0 ? View.VISIBLE : View.GONE);
    none.setText(R.string.coach_day_none_yet);
    showSince(day);
    card.setVisibility(View.VISIBLE);
  }

  private void showFigures(CoachDay day) {
    LinearLayout figures = card.findViewById(R.id.llCoachTodayFigures);
    figures.removeAllViews();
    int solves = day.getSolveCount();
    if (solves > 0) {
      figures.addView(figure(figures, String.valueOf(solves),
          activity.getResources().getQuantityString(R.plurals.coach_day_solves, solves)));
      if (day.getMean() > 0) {
        figures.addView(figure(figures, text.time(day.getMean()),
            activity.getString(R.string.coach_day_mean)));
      }
      if (day.getBestAo5() > 0) {
        figures.addView(figure(figures, text.time(day.getBestAo5()),
            activity.getString(R.string.coach_day_best_ao5)));
      }
    }
    if (day.getDrillReps() > 0) {
      figures.addView(figure(figures, String.valueOf(day.getDrillReps()),
          activity.getResources().getQuantityString(R.plurals.coach_day_reps_label,
              day.getDrillReps())));
    }
    figures.setVisibility(figures.getChildCount() > 0 ? View.VISIBLE : View.GONE);
  }

  private View figure(LinearLayout parent, String number, String what) {
    View figure = inflater.inflate(R.layout.coach_today_figure, parent, false);
    ((TextView) figure.findViewById(R.id.tvCoachTodayNumber)).setText(number);
    ((TextView) figure.findViewById(R.id.tvCoachTodayWhat)).setText(what);
    return figure;
  }

  private void showSince(CoachDay day) {
    LinearLayout rows = card.findViewById(R.id.llCoachTodayRows);
    rows.removeAllViews();
    StepPalette palette = StepPalette.cfop(activity);
    for (FocusProgress progress : day.getProgress()) {
      String value = text.value(progress);
      if (value == null) {
        continue;
      }
      View row = inflater.inflate(R.layout.coach_today_row, rows, false);
      String family = CoachFocusFigure.familyOf(progress.getArea());
      GradientDrawable pip = new GradientDrawable();
      pip.setShape(GradientDrawable.OVAL);
      pip.setColor(family != null ? palette.colorFor(family)
          : ContextCompat.getColor(activity, R.color.white_wash_50));
      row.findViewById(R.id.vCoachTodayPip).setBackground(pip);
      ((TextView) row.findViewById(R.id.tvCoachTodayLabel)).setText(text.label(progress));
      TextView number = row.findViewById(R.id.tvCoachTodayValue);
      number.setText(value);
      number.setTextColor(ContextCompat.getColor(activity, colourOf(progress)));
      rows.addView(row);
    }
    card.findViewById(R.id.llCoachTodaySince)
        .setVisibility(rows.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    ((TextView) card.findViewById(R.id.tvCoachTodaySinceSolves)).setText(activity.getResources()
        .getQuantityString(R.plurals.coach_day_since_solves, day.getSolvesSince(),
            day.getSolvesSince()));
  }

  /** Green or orange only for a clear move; a figure still waiting for solves is turned down. */
  private static int colourOf(FocusProgress progress) {
    if (progress.getKind() == FocusProgress.Kind.WAITING) {
      return R.color.secondary_text;
    }
    switch (progress.getChange()) {
      case BETTER:
        return R.color.green_soft;
      case WORSE:
        return R.color.moved_worse;
      default:
        return R.color.white;
    }
  }
}
