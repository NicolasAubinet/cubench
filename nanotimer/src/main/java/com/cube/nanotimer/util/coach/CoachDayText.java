package com.cube.nanotimer.util.coach;

import android.content.Context;

import com.cube.nanotimer.R;
import com.cube.nanotimer.coach.FocusArea;
import com.cube.nanotimer.coach.FocusProgress;
import com.cube.nanotimer.session.CoachDay;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.Utils;

import java.util.List;
import java.util.Locale;

/** The words of a {@link CoachDay}, shared by the Today card and the evening recap. */
public final class CoachDayText {

  private final Context context;

  public CoachDayText(Context context) {
    this.context = context;
  }

  /** What a focus row is about, e.g. "Looking in F2L" or "PLL cases" for drill reps. */
  public String label(FocusProgress progress) {
    FocusArea area = progress.getArea();
    String code = area.getCodes().isEmpty() ? null : area.getCodes().get(0);
    FocusArea.Reason reason = area.getReason();
    if (progress.getKind() != FocusProgress.Kind.REPS && reason != null) {
      switch (reason) {
        case RECOGNITION_HEAVY:
          return context.getString(R.string.coach_day_looking, step(code));
        case UNEVEN_STEP:
          return context.getString(R.string.coach_day_spread, step(code));
        case STALLING:
          return context.getString(R.string.coach_day_freezes, step(code));
        case SLOW_FIRST_PAIR:
          return context.getString(R.string.coach_day_first_pair);
        case STUCK_PIECES:
          return context.getString(R.string.coach_day_stuck);
        case SLOW_STEP:
          return context.getString(R.string.coach_day_share, step(code));
        case TWO_LOOK_OLL:
        case TWO_LOOK_PLL:
          return context.getString(R.string.coach_day_two_looks, step(code));
        default:
          break;
      }
    }
    boolean aboutCases = reason == FocusArea.Reason.SLOW_CASE
        || reason == FocusArea.Reason.SLOW_UNDER_PRESSURE
        || reason == FocusArea.Reason.INCONSISTENT_CASE;
    String family = aboutCases ? caseFamily(area.getCodes()) : null;
    return family != null ? context.getString(R.string.drill_cases_family, step(family))
        : reasonTitle(reason);
  }

  /** The set every case code is in, e.g. pll, or null when they span several or there are none. */
  static String caseFamily(List<String> codes) {
    String family = null;
    for (String code : codes) {
      String one = MethodStatistics.familyOf(code);
      if (family != null && !family.equals(one)) {
        return null;
      }
      family = one;
    }
    return "pair".equals(family) ? null : family; // a pair's step name carries its number
  }

  /** The row's number: before and since, the solves still needed, or the drill reps. */
  public String value(FocusProgress progress) {
    switch (progress.getKind()) {
      case FIGURE:
        if (progress.getUnit() == FocusProgress.Unit.SHARE) {
          return context.getString(R.string.coach_day_from_to, percent(progress.getBefore()),
              percent(progress.getSince()));
        }
        return context.getString(R.string.coach_day_seconds, seconds(progress.getBefore()),
            seconds(progress.getSince()));
      case WAITING:
        return plural(R.plurals.coach_day_more_solves, progress.getSolvesNeeded());
      case REPS:
        return plural(R.plurals.coach_day_drill_reps, progress.getReps());
      default:
        return null;
    }
  }

  /** The recap's title, or null on a day with no solves. */
  public String recapTitle(CoachDay day) {
    int solves = day.getSolveCount();
    if (solves == 0) {
      return null;
    }
    long mean = day.getMean();
    return mean > 0
        ? context.getResources().getQuantityString(R.plurals.coach_recap_title, solves, solves,
            time(mean))
        : plural(R.plurals.coach_recap_title_no_mean, solves);
  }

  /** The recap's body: the focus that moved most, the best Ao5 and the drill reps. */
  public String recapText(CoachDay day) {
    StringBuilder text = new StringBuilder();
    FocusProgress best = day.getBestMove();
    if (best != null) {
      text.append(context.getString(R.string.coach_recap_moved, label(best), value(best)));
    }
    if (day.getBestAo5() > 0) {
      append(text, context.getString(R.string.coach_recap_best_ao5, time(day.getBestAo5())));
    }
    if (day.getDrillReps() > 0) {
      append(text, plural(R.plurals.coach_recap_drilled, day.getDrillReps()));
    }
    if (best == null) {
      append(text, context.getString(R.string.coach_recap_open));
    }
    return text.toString();
  }

  public String time(long ms) {
    return FormatterService.INSTANCE.formatSolveTime(ms);
  }

  private String reasonTitle(FocusArea.Reason reason) {
    return reasonTitle(context, reason);
  }

  /** The kind of focus as a focus card's title says it; null is a reason this app does not know. */
  public static String reasonTitle(Context context, FocusArea.Reason reason) {
    if (reason == null) {
      return context.getString(R.string.coach_reason_unknown);
    }
    switch (reason) {
      case SLOW_CASE:
        return context.getString(R.string.coach_reason_slow_case);
      case SLOW_UNDER_PRESSURE:
        return context.getString(R.string.coach_reason_slow_under_pressure);
      case RECOGNITION_HEAVY:
        return context.getString(R.string.coach_reason_recognition_heavy);
      case TWO_LOOK_OLL:
      case TWO_LOOK_PLL:
        return context.getString(R.string.coach_reason_two_look_oll);
      case SLOW_STEP:
        return context.getString(R.string.coach_reason_slow_step);
      case STUCK_PIECES:
        return context.getString(R.string.coach_reason_stuck_pieces);
      case SLOW_FIRST_PAIR:
        return context.getString(R.string.coach_reason_slow_first_pair);
      case LONG_PAIR:
        return context.getString(R.string.coach_reason_long_pair);
      case STALLING:
        return context.getString(R.string.coach_reason_stalling);
      case UNEVEN_STEP:
        return context.getString(R.string.coach_reason_uneven_step);
      case F2L_ROTATIONS:
        return context.getString(R.string.coach_reason_f2l_rotations);
      default:
        return context.getString(R.string.coach_reason_inconsistent_case);
    }
  }

  private String step(String code) {
    return code == null ? "" : Utils.toSmartCubeStepLocalizedName(context, code, 0);
  }

  private String plural(int id, int count) {
    return context.getResources().getQuantityString(id, count, count);
  }

  /** Seconds to two decimals, as the focus cards print them. */
  private static String seconds(double ms) {
    return String.format(Locale.US, "%.2f", Math.round(ms / 10) / 100d);
  }

  private String percent(double share) {
    return context.getString(R.string.coach_percent, Integer.valueOf((int) Math.round(share * 100)));
  }

  private static void append(StringBuilder text, String sentence) {
    if (text.length() > 0) {
      text.append(' ');
    }
    text.append(sentence);
  }
}
