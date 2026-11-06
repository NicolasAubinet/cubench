package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
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
import com.cube.nanotimer.util.coach.CoachPractice;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.vo.SolveType;

import java.util.ArrayList;
import java.util.List;

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
  private SolveType solveType;
  /** When the plan whose day is on the card was written, -1 while it shows none. */
  private long shownPlanAt = -1;

  public CoachTodayView(Activity activity, View card) {
    this.activity = activity;
    this.card = card;
    this.inflater = LayoutInflater.from(activity);
    this.text = new CoachDayText(activity);
  }

  /** Reads today for the plan and draws it; null hides the card. */
  public void load(SolveType solveType, StoredCoachPlan plan) {
    final int read = ++reads;
    this.solveType = solveType;
    if (plan == null) {
      hide();
      return;
    }
    if (shownPlanAt != plan.getWrittenAt()) {
      showLoading(plan.getPlan().getFocus().size()); // a resume keeps the last figures meanwhile
    }
    shownPlanAt = plan.getWrittenAt();
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
    shownPlanAt = -1;
    card.setVisibility(View.GONE);
  }

  /** The card at its usual size with blank bars, from the moment the plan is drawn. */
  private void showLoading(int focusCount) {
    ((TextView) card.findViewById(R.id.tvCoachTodayDate)).setText(DateUtils.formatDateTime(
        activity, System.currentTimeMillis(), DateUtils.FORMAT_SHOW_WEEKDAY));
    LinearLayout figures = card.findViewById(R.id.llCoachTodayFigures);
    figures.removeAllViews();
    for (int i = 0; i < 3; i++) {
      figures.addView(inflater.inflate(R.layout.coach_today_figure_skeleton, figures, false));
    }
    figures.setVisibility(View.VISIBLE);
    card.findViewById(R.id.tvCoachTodayNone).setVisibility(View.GONE);
    LinearLayout rows = card.findViewById(R.id.llCoachTodayRows);
    rows.removeAllViews();
    for (int i = 0; i < focusCount; i++) {
      rows.addView(inflater.inflate(R.layout.coach_today_row_skeleton, rows, false));
    }
    ((TextView) card.findViewById(R.id.tvCoachTodaySinceSolves)).setText(null);
    card.findViewById(R.id.llCoachTodaySince)
        .setVisibility(focusCount > 0 ? View.VISIBLE : View.GONE);
    card.findViewById(R.id.llCoachTodaySets).setVisibility(View.GONE);
    card.setVisibility(View.VISIBLE);
  }

  void show(CoachDay day) {
    ((TextView) card.findViewById(R.id.tvCoachTodayDate)).setText(DateUtils.formatDateTime(
        activity, System.currentTimeMillis(), DateUtils.FORMAT_SHOW_WEEKDAY));
    showFigures(day);
    TextView none = card.findViewById(R.id.tvCoachTodayNone);
    none.setVisibility(day.getSolveCount() == 0 ? View.VISIBLE : View.GONE);
    none.setText(R.string.coach_day_none_yet);
    showSince(day);
    showSets(solveType == null ? null
        : CoachPractice.sinceYesterday(solveType, System.currentTimeMillis()));
    card.setVisibility(View.VISIBLE);
  }

  /** Joined cases with a plus in green, left ones struck out and turned down, in one wrapping line. */
  private void showSets(CoachPractice.Changes changes) {
    View section = card.findViewById(R.id.llCoachTodaySets);
    if (changes == null || changes.isEmpty()) {
      section.setVisibility(View.GONE);
      return;
    }
    SpannableStringBuilder line = new SpannableStringBuilder();
    List<String> joinedNames = new ArrayList<String>();
    for (String code : changes.getJoined()) {
      String name = Utils.toSmartCubeCaseHeadline(activity, code);
      joinedNames.add(name);
      append(line, "+ " + name, new ForegroundColorSpan(
          ContextCompat.getColor(activity, R.color.green_soft)));
    }
    List<String> leftNames = new ArrayList<String>();
    for (String code : changes.getLeft()) {
      String name = Utils.toSmartCubeCaseHeadline(activity, code);
      leftNames.add(name);
      append(line, name, new StrikethroughSpan(), new ForegroundColorSpan(
          ContextCompat.getColor(activity, R.color.secondary_text)));
    }
    TextView sets = card.findViewById(R.id.tvCoachTodaySets);
    sets.setText(line);
    // A strikethrough is not read aloud, so say which cases joined and which left.
    List<String> said = new ArrayList<String>();
    if (!joinedNames.isEmpty()) {
      said.add(activity.getString(R.string.coach_day_sets_joined,
          TextUtils.join(", ", joinedNames)));
    }
    if (!leftNames.isEmpty()) {
      said.add(activity.getString(R.string.coach_day_sets_left, TextUtils.join(", ", leftNames)));
    }
    sets.setContentDescription(TextUtils.join(". ", said));
    section.setVisibility(View.VISIBLE);
  }

  private static void append(SpannableStringBuilder line, String text, Object... spans) {
    if (line.length() > 0) {
      line.append("   ");
    }
    int start = line.length();
    // Non-breaking, so a case name never wraps in two.
    line.append(text.replace(' ', ' '));
    for (Object span : spans) {
      line.setSpan(span, start, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
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
