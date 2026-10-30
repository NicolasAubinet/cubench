package com.cube.nanotimer.gui.widget;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.cube.nanotimer.R;
import com.cube.nanotimer.coach.CoachPayload;
import com.cube.nanotimer.coach.FocusArea;
import com.cube.nanotimer.coach.StepShares;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.util.helper.Utils;

/**
 * What a focus leads with: one figure from the payload, what it measures, and what it is set
 * against, either as bars to one scale or as one quantity split in two.
 *
 * <p>Every figure is read off the payload, never off the plan, for the same reason the case rows
 * are: a coach that invented a number has nothing to print with it. A focus whose figures are
 * missing has no figure at all rather than a partial one.
 */
final class CoachFocusFigure {

  /** One side of a comparison: what it is, its value on the bars' scale, and how it is printed. */
  static final class Bar {
    final String label;
    final double value;
    final String shown;
    /** The solver's own figure, drawn in the step's full colour; the other is washed. */
    final boolean own;

    Bar(String label, double value, String shown, boolean own) {
      this.label = label;
      this.value = value;
      this.shown = shown;
      this.own = own;
    }
  }

  final String number;
  /** A trailing unit printed small after the number, such as %, or null. */
  final String suffix;
  final String unit;
  final String sub;
  /** The case drawn beside the figure, for a focus about one case. */
  final String chartCode;
  final List<Bar> bars;
  /** One quantity in two parts, the first washed: null where the figure is not split. */
  final Bar[] split;
  /** Whether the figure already says all the case rows would, so they are left out. */
  final boolean coversCases;

  private CoachFocusFigure(String number, String suffix, String unit, String sub, String chartCode,
      List<Bar> bars, Bar[] split, boolean coversCases) {
    this.number = number;
    this.suffix = suffix;
    this.unit = unit;
    this.sub = sub;
    this.chartCode = chartCode;
    this.bars = bars;
    this.split = split;
    this.coversCases = coversCases;
  }

  /** The step a focus is about, which colours it: null for one about no single step. */
  static String familyOf(FocusArea area) {
    if (area.getReason() == FocusArea.Reason.STUCK_PIECES
        || area.getReason() == FocusArea.Reason.SLOW_FIRST_PAIR
        || area.getReason() == FocusArea.Reason.LONG_PAIR
        || area.getReason() == FocusArea.Reason.F2L_ROTATIONS) {
      return "f2l";
    }
    if (area.getCodes().isEmpty()) {
      return null;
    }
    String family = MethodStatistics.familyOf(area.getCodes().get(0));
    return "pair".equals(family) ? "f2l" : family;
  }

  /** @return the figure, or null for a focus with nothing to lead with */
  static CoachFocusFigure of(Context context, FocusArea area, CoachPayload payload) {
    if (area.getReason() == null) {
      return null;
    }
    Reader read = new Reader(context, payload);
    switch (area.getReason()) {
      case SLOW_CASE:
        return read.slowCases(cases(area, payload));
      case SLOW_UNDER_PRESSURE:
        return read.underPressure(cases(area, payload));
      case INCONSISTENT_CASE:
        return read.inconsistent(cases(area, payload));
      case RECOGNITION_HEAVY:
        return read.recognition(first(area));
      case SLOW_STEP:
        return read.stepShare(first(area));
      case TWO_LOOK_OLL:
      case TWO_LOOK_PLL:
        return read.twoLook(first(area));
      case STUCK_PIECES:
        return read.stuck();
      case SLOW_FIRST_PAIR:
        return read.firstPair();
      case LONG_PAIR:
        return read.longPair();
      case STALLING:
        return read.stalling(first(area));
      case UNEVEN_STEP:
        return read.uneven(first(area));
      case F2L_ROTATIONS:
        return read.rotations();
      default:
        return null;
    }
  }

  private static List<String> cases(FocusArea area, CoachPayload payload) {
    List<String> cases = new ArrayList<String>();
    for (String code : area.getCodes()) {
      if (payload.value("cases." + code + ".mean_ms") != null) {
        cases.add(code);
      }
    }
    return cases;
  }

  private static String first(FocusArea area) {
    return area.getCodes().isEmpty() ? null : area.getCodes().get(0);
  }

  /** The payload read for one figure, with the formatting every figure shares. */
  private static final class Reader {
    private final Context context;
    private final CoachPayload payload;

    Reader(Context context, CoachPayload payload) {
      this.context = context;
      this.payload = payload;
    }

    CoachFocusFigure slowCases(List<String> cases) {
      double lost = 0;
      int count = 0;
      for (String code : cases) {
        Double caseLost = value("cases." + code + ".time_lost_ms");
        Double caseCount = value("cases." + code + ".count");
        if (caseLost == null || caseCount == null) {
          return null;
        }
        lost += caseLost.doubleValue();
        count += caseCount.intValue();
      }
      if (cases.isEmpty() || lost <= 0) {
        return null;
      }
      String unit = context.getString(R.string.coach_fig_seconds_lost);
      if (cases.size() > 1) {
        return new CoachFocusFigure(time(lost), null, unit,
            plural(R.plurals.coach_fig_cases_times, count, cases.size(), count), null,
            Collections.<Bar>emptyList(), null, false);
      }
      String code = cases.get(0);
      return new CoachFocusFigure(time(lost), null, unit,
          plural(R.plurals.coach_fig_case_times, count, name(code), count), code,
          againstRest(code), null, true);
    }

    CoachFocusFigure underPressure(List<String> cases) {
      if (cases.size() != 1) {
        return null;
      }
      String code = cases.get(0);
      Double gap = value("comparisons." + code + ".gap_ms");
      Double solved = value("comparisons." + code + ".solve_mean_ms");
      Double drilled = value("comparisons." + code + ".drill_mean_ms");
      if (gap == null || solved == null || drilled == null || gap.doubleValue() <= 0) {
        return null;
      }
      return new CoachFocusFigure("+" + time(gap), null,
          context.getString(R.string.coach_fig_slower_in_solve), name(code), code,
          bars(new Bar(context.getString(R.string.coach_bar_in_solve), solved, time(solved), true),
              new Bar(context.getString(R.string.coach_bar_drilled), drilled, time(drilled),
                  false)),
          null, true);
    }

    CoachFocusFigure inconsistent(List<String> cases) {
      if (cases.size() != 1) {
        return null;
      }
      String code = cases.get(0);
      Double rate = value("cases." + code + ".rejection_rate");
      Double count = value("cases." + code + ".count");
      if (rate == null || count == null) {
        return null;
      }
      return new CoachFocusFigure(String.valueOf(rounded(rate)), "%",
          context.getString(R.string.coach_fig_thrown_out),
          plural(R.plurals.coach_fig_case_times, count.intValue(), name(code), count.intValue()), code,
          againstRest(code), null, true);
    }

    CoachFocusFigure recognition(String family) {
      Double looking = family == null ? null : value("families." + family + ".recognition_ms");
      Double mean = family == null ? null : value("families." + family + ".mean_ms");
      if (looking == null || mean == null || mean.doubleValue() <= 0) {
        return null;
      }
      double turning = Math.max(mean.doubleValue() - looking.doubleValue(), 0);
      return new CoachFocusFigure(String.valueOf(rounded(looking / mean)), "%",
          context.getString(R.string.coach_fig_spent_looking, step(family)),
          context.getString(R.string.coach_fig_of_seconds, time(looking), time(mean)), null,
          Collections.<Bar>emptyList(), new Bar[] {
              new Bar(context.getString(R.string.coach_split_looking, time(looking)), looking,
                  time(looking), false),
              new Bar(context.getString(R.string.coach_split_turning, time(turning)), turning,
                  time(turning), true)}, false);
    }

    CoachFocusFigure stepShare(String family) {
      StepShares shares = StepShares.of(payload);
      Double took = family == null ? null : shares.actual(family);
      Double should = family == null ? null : shares.expected(family);
      if (took == null || should == null) {
        return null;
      }
      return new CoachFocusFigure(String.valueOf(rounded(took)), "%",
          context.getString(R.string.coach_fig_solve_share, step(family)),
          context.getString(R.string.coach_fig_usual_share, percent(should)), null,
          bars(new Bar(context.getString(R.string.coach_bar_you), took, percent(took), true),
              new Bar(context.getString(R.string.coach_bar_usual), should, percent(should), false)),
          null, false);
    }

    CoachFocusFigure twoLook(String family) {
      Double looks = family == null ? null : value(CoachPlanView.twoLookCountPath(family));
      Double solves = value("windows.families");
      if (looks == null || solves == null) {
        return null;
      }
      return new CoachFocusFigure(String.valueOf(looks.intValue()), null,
          context.getString(R.string.coach_fig_two_looks, step(family)),
          plural(R.plurals.coach_fig_in_last_solves, solves.intValue(), solves.intValue()), null,
          Collections.<Bar>emptyList(), null, false);
    }

    CoachFocusFigure stuck() {
      Double share = value("f2l_groups.f2l_stuck.share");
      Double stuck = value("f2l_groups.f2l_stuck.mean_ms");
      Double basic = value("f2l_groups.f2l_basic.mean_ms");
      if (share == null || stuck == null || basic == null || stuck <= basic) {
        return null;
      }
      return new CoachFocusFigure(String.valueOf(rounded(share)), "%",
          context.getString(R.string.coach_fig_pairs_stuck),
          context.getString(R.string.coach_fig_takes_longer, time(stuck - basic)), null,
          bars(new Bar(context.getString(R.string.coach_bar_stuck), stuck, time(stuck), true),
              new Bar(context.getString(R.string.coach_bar_basic), basic, time(basic), false)),
          null, false);
    }

    CoachFocusFigure firstPair() {
      Double first = value("f2l_groups.f2l_first.recognition_ms");
      Double later = value("f2l_groups.f2l_later.recognition_ms");
      if (first == null || later == null || first <= later) {
        return null;
      }
      return new CoachFocusFigure("+" + time(first - later), null,
          context.getString(R.string.coach_fig_first_pair),
          context.getString(R.string.coach_fig_first_pair_sub), null,
          bars(new Bar(context.getString(R.string.coach_bar_first_pair), first, time(first), true),
              new Bar(context.getString(R.string.coach_bar_later_pairs), later, time(later),
                  false)),
          null, false);
    }

    CoachFocusFigure longPair() {
      Double solves = value("f2l_moves.count");
      Double turned = value("f2l_moves.turned_moves");
      Double usual = value("f2l_moves.usual_moves");
      if (solves == null || turned == null || usual == null || turned <= usual) {
        return null;
      }
      return new CoachFocusFigure("+" + moves(turned - usual), null,
          context.getString(R.string.coach_fig_extra_moves),
          plural(R.plurals.coach_fig_over_solves, solves.intValue(), solves.intValue()), null,
          bars(new Bar(context.getString(R.string.coach_bar_you), turned, moves(turned), true),
              new Bar(context.getString(R.string.coach_bar_usual), usual, moves(usual), false)),
          null, false);
    }

    CoachFocusFigure stalling(String family) {
      Double stalls = family == null ? null : value("turning." + family + ".stalls");
      Double stopped = family == null ? null : value("turning." + family + ".stall_ms");
      Double mean = family == null ? null : value("families." + family + ".mean_ms");
      if (stalls == null || stopped == null || mean == null) {
        return null;
      }
      double turning = Math.max(mean - stopped, 0);
      return new CoachFocusFigure(moves(stalls), null,
          context.getString(R.string.coach_fig_freezes, step(family)),
          context.getString(R.string.coach_fig_frozen_of, time(stopped), time(mean)), null,
          // Waiting first and faded, turning second and full, as every other split draws it.
          Collections.<Bar>emptyList(), new Bar[] {
              new Bar(context.getString(R.string.coach_split_stopped, time(stopped)), stopped,
                  time(stopped), false),
              new Bar(context.getString(R.string.coach_split_turning, time(turning)), turning,
                  time(turning), true)}, false);
    }

    CoachFocusFigure uneven(String family) {
      Double mean = family == null ? null : value("families." + family + ".mean_ms");
      Double spread = family == null ? null : value("families." + family + ".std_dev_ms");
      Double best = family == null ? null : value("families." + family + ".best_ms");
      if (mean == null || spread == null || best == null) {
        return null;
      }
      return new CoachFocusFigure("±" + time(spread), null,
          context.getString(R.string.coach_fig_spread, step(family)),
          context.getString(R.string.coach_fig_average, time(mean)), null,
          bars(new Bar(context.getString(R.string.coach_bar_average), mean, time(mean), true),
              new Bar(context.getString(R.string.coach_bar_best), best, time(best), false)),
          null, false);
    }

    CoachFocusFigure rotations() {
      Double solves = value("f2l_rotations.solves");
      Double pairs = value("f2l_rotations.pairs");
      Double rotated = value("f2l_rotations.rotated_pairs");
      Double excess = value("f2l_rotations.excess_ms");
      if (solves == null || pairs == null || rotated == null || pairs <= 0 || rotated <= 0) {
        return null;
      }
      double share = rotated / pairs;
      double plain = 1 - share;
      return new CoachFocusFigure(String.valueOf(rounded(share)), "%",
          context.getString(R.string.coach_fig_pairs_rotated),
          excess != null && excess > 0
              ? context.getString(R.string.coach_fig_rotation_adds, time(excess))
              : plural(R.plurals.coach_fig_over_solves, solves.intValue(), solves.intValue()),
          null, Collections.<Bar>emptyList(), new Bar[] {
              new Bar(context.getString(R.string.coach_split_rotated, percent(share)), share,
                  percent(share), false),
              new Bar(context.getString(R.string.coach_split_unrotated, percent(plain)), plain,
                  percent(plain), true)}, false);
    }

    /** A case's mean against the rest of its family's. */
    private List<Bar> againstRest(String code) {
      Double mean = value("cases." + code + ".mean_ms");
      Double rest = value("cases." + code + ".family_mean_ms");
      if (mean == null || rest == null) {
        return Collections.emptyList();
      }
      return bars(new Bar(name(code), mean, time(mean), true),
          new Bar(context.getString(R.string.coach_bar_rest), rest, time(rest), false));
    }

    private Double value(String path) {
      return payload.value(path);
    }

    private String name(String code) {
      return Utils.toSmartCubeCaseHeadline(context, code);
    }

    private String step(String family) {
      return Utils.toSmartCubeStepLocalizedName(context, family, 0);
    }

    /** Seconds, never m:ss and never three decimals: every figure is read against "seconds". */
    private String time(double ms) {
      return String.format(Locale.US, "%.2f", Math.round(ms / 10) / 100d);
    }

    private String plural(int id, int count, Object... args) {
      return context.getResources().getQuantityString(id, count, args);
    }

    private String percent(double share) {
      return context.getString(R.string.coach_percent, Integer.valueOf(rounded(share)));
    }
  }

  private static List<Bar> bars(Bar own, Bar other) {
    List<Bar> bars = new ArrayList<Bar>();
    bars.add(own);
    bars.add(other);
    return bars;
  }

  private static int rounded(double share) {
    return (int) Math.round(share * 100);
  }

  /** A count of moves, to the tenth the payload carries. */
  private static String moves(double moves) {
    return String.format(Locale.getDefault(), "%.1f", moves);
  }
}
