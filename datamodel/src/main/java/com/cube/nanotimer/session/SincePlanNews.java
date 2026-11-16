package com.cube.nanotimer.session;

import com.cube.nanotimer.coach.FocusProgress;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which focus of the plan the evening recap may report as moved since the plan, so it is news and
 * not the same line every evening. A focus is news once its figure is clearly better than before
 * the plan over at least {@link #MIN_SOLVES} solves, and then again only once it is
 * clearly better than what the recap last said of it. What was said is kept per plan, as text in
 * the form {@code writtenAt|index=since;index=since}.
 */
public final class SincePlanNews {

  /**
   * The solves a plan is written from: a recap claim needs more than the Today card's floor. Solves,
   * not occurrences, since a stuck-pairs share that improves has fewer occurrences.
   */
  public static final int MIN_SOLVES = 25;

  private SincePlanNews() {
  }

  /**
   * The index of the focus to report, the one that moved most among those that are news, or -1.
   *
   * @param solvesSince the solves of the plan's method since it was written
   * @param said what earlier recaps said, as {@link #said} wrote it, or null
   */
  public static int pick(List<FocusProgress> progress, int solvesSince, long planWrittenAt,
      String said) {
    if (solvesSince < MIN_SOLVES) {
      return -1;
    }
    Map<Integer, Double> before = read(said, planWrittenAt);
    int best = -1;
    for (int i = 0; i < progress.size(); i++) {
      FocusProgress one = progress.get(i);
      if (one.getChange() != FocusProgress.Change.BETTER) {
        continue;
      }
      Double last = before.get(Integer.valueOf(i));
      if (last != null && !clearlyBetter(one.getUnit(), last.doubleValue(), one.getSince())) {
        continue;
      }
      if (best < 0 || one.getGain() > progress.get(best).getGain()) {
        best = i;
      }
    }
    return best;
  }

  /** What was said, with the given focus's figure added; a new plan starts it afresh. */
  public static String said(String said, long planWrittenAt, int index, double since) {
    Map<Integer, Double> figures = read(said, planWrittenAt);
    figures.put(Integer.valueOf(index), Double.valueOf(since));
    StringBuilder text = new StringBuilder().append(planWrittenAt).append('|');
    boolean first = true;
    for (Map.Entry<Integer, Double> entry : figures.entrySet()) {
      if (!first) {
        text.append(';');
      }
      first = false;
      text.append(entry.getKey()).append('=')
          .append(String.format(Locale.US, "%.4f", entry.getValue()));
    }
    return text.toString();
  }

  private static boolean clearlyBetter(FocusProgress.Unit unit, double from, double to) {
    double clear = unit == FocusProgress.Unit.SHARE ? FocusProgress.CLEAR_SHARE_POINTS
        : from * FocusProgress.CLEAR_TIME_RATIO;
    return from - to >= clear && to < from;
  }

  private static Map<Integer, Double> read(String said, long planWrittenAt) {
    Map<Integer, Double> figures = new HashMap<Integer, Double>();
    if (said == null) {
      return figures;
    }
    int bar = said.indexOf('|');
    try {
      if (bar < 0 || Long.parseLong(said.substring(0, bar)) != planWrittenAt) {
        return figures;
      }
      for (String pair : said.substring(bar + 1).split(";")) {
        int eq = pair.indexOf('=');
        if (eq > 0) {
          figures.put(Integer.valueOf(pair.substring(0, eq)),
              Double.valueOf(pair.substring(eq + 1)));
        }
      }
    } catch (NumberFormatException e) {
      figures.clear(); // unreadable: say it afresh rather than never
    }
    return figures;
  }
}
