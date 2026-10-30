package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.drill.DrillSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a drill that retires its cases has counted so far, case by case: the clean reps, the ones
 * that beat the target, and whether that clears the case.
 *
 * <p>Read off the reps rather than kept alongside them, so the session deciding what to deal and the
 * summary a rep can be thrown out of apply one rule to whatever reps they hold.
 */
public final class CaseTally {

  private final DrillSpec spec;
  private final Map<String, Integer> showings = new HashMap<String, Integer>();
  private final Map<String, Integer> wins = new HashMap<String, Integer>();
  private final Map<String, Boolean> cleared = new HashMap<String, Boolean>();

  private CaseTally(DrillSpec spec) {
    this.spec = spec;
  }

  public static CaseTally of(DrillSpec spec, List<DrillRep> reps) {
    CaseTally tally = new CaseTally(spec);
    for (DrillRep rep : reps) {
      String code = rep.getCaseCode();
      if (spec.isLearning()) {
        tally.countStreak(rep);
        continue;
      }
      if (!isClean(rep)) {
        continue;
      }
      tally.showings.put(code, countOf(tally.showings, code) + 1);
      if (tally.isWin(rep)) {
        tally.wins.put(code, countOf(tally.wins, code) + 1);
      }
    }
    return tally;
  }

  /**
   * A learning drill's dots are a run: each rep turned without looking, with the algorithm being
   * learned, adds one, and anything else empties them. Reaching the target clears the case for good.
   */
  private void countStreak(DrillRep rep) {
    String code = rep.getCaseCode();
    if (cleared.containsKey(code)) {
      return;
    }
    if (isWin(rep)) {
      showings.put(code, countOf(showings, code) + 1);
      wins.put(code, countOf(wins, code) + 1);
      if (countOf(wins, code) >= spec.getWinsToRetire()) {
        cleared.put(code, Boolean.TRUE);
      }
    } else {
      wins.put(code, 0);
    }
  }

  /**
   * A rep that measured the case: not skipped, not restarted, not solved with the answer looked up.
   */
  public static boolean isClean(DrillRep rep) {
    return !rep.isAbandoned() && rep.getResetCount() == 0 && !rep.wasRevealed();
  }

  /**
   * Whether a clean rep came in at or under its case's target, looking and turning together: the
   * drill is about the case getting faster, whichever half it lost. In a learning drill a clean rep
   * wins when it was the algorithm being learned, whatever the time.
   */
  public boolean isWin(DrillRep rep) {
    if (spec.isLearning()) {
      return isClean(rep) && !rep.isOffTaught() && !rep.isRotated();
    }
    return isClean(rep) && !rep.isRotated() && rep.getTotalMs() <= spec.getTargetMs(rep.getCaseCode());
  }

  public int getShowings(String code) {
    return countOf(showings, code);
  }

  /** Wins against the target; in a learning drill, the current run. */
  public int getWins(String code) {
    return countOf(wins, code);
  }

  /** Beaten its target often enough, over enough clean reps, to leave the drill. */
  public boolean isCleared(String code) {
    if (spec.isLearning()) {
      return cleared.containsKey(code);
    }
    return spec.retiresCases() && getWins(code) >= spec.getWinsToRetire()
        && getShowings(code) >= spec.getMinShowings();
  }

  private static int countOf(Map<String, Integer> counts, String code) {
    Integer count = counts.get(code);
    return count == null ? 0 : count.intValue();
  }
}
