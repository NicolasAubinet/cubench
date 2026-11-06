package com.cube.nanotimer.session;

import com.cube.nanotimer.coach.FocusProgress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A coach subscriber's day, worked out on the phone between plans: today's solves of the plan's
 * solve type, the drill reps done today, and how each focus has moved since the plan. Nothing here
 * leaves the device or needs the coach service.
 *
 * <p>Today's figures show from the first solve, with no floor: the mean is of the solves that were
 * not DNFs, and the best Ao5 is the best of every five in a row today, counted as the timer counts
 * an Ao5.
 */
public final class CoachDay {

  private static final int AO5 = 5;

  private final List<Long> todayTimes;
  private final int drillCount;
  private final int drillReps;
  private final int solvesSince;
  private final List<FocusProgress> progress;

  /**
   * @param todayTimes today's times, newest first, -1 for a DNF
   * @param drillCount drills done today with at least one rep counted, any drill
   * @param drillReps drill reps done today, any drill
   * @param solvesSince how many solves of the plan's method followed the plan, 0 with no plan
   * @param progress each focus of the plan in its order, empty with no plan
   */
  public CoachDay(List<Long> todayTimes, int drillCount, int drillReps, int solvesSince,
      List<FocusProgress> progress) {
    this.todayTimes = Collections.unmodifiableList(new ArrayList<Long>(todayTimes));
    this.drillCount = drillCount;
    this.drillReps = drillReps;
    this.solvesSince = solvesSince;
    this.progress = progress == null ? Collections.<FocusProgress>emptyList()
        : Collections.unmodifiableList(new ArrayList<FocusProgress>(progress));
  }

  public int getSolveCount() {
    return todayTimes.size();
  }

  /** The mean of today's solves that were not DNFs, or -1 when every one was. */
  public long getMean() {
    long mean = new TimesStatistics(todayTimes).getSuccessMeanOf(todayTimes.size(), true);
    return mean > 0 ? mean : -1;
  }

  /** The best Ao5 of today, -1 when there are fewer than five solves or every Ao5 is a DNF. */
  public long getBestAo5() {
    long best = -1;
    for (int i = 0; i + AO5 <= todayTimes.size(); i++) {
      long average = new TimesStatistics(todayTimes.subList(i, i + AO5)).getAverageOf(AO5);
      if (average > 0 && (best < 0 || average < best)) {
        best = average;
      }
    }
    return best;
  }

  public int getDrillCount() {
    return drillCount;
  }

  public int getDrillReps() {
    return drillReps;
  }

  /** Whether anything was done today: a solve of the plan's type or a drill rep. */
  public boolean isActive() {
    return !todayTimes.isEmpty() || drillReps > 0;
  }

  public int getSolvesSince() {
    return solvesSince;
  }

  public List<FocusProgress> getProgress() {
    return progress;
  }

  /** The focus that moved most for the better, or null when none moved clearly for the better. */
  public FocusProgress getBestMove() {
    FocusProgress best = null;
    for (FocusProgress one : progress) {
      if (one.getChange() == FocusProgress.Change.BETTER
          && (best == null || one.getGain() > best.getGain())) {
        best = one;
      }
    }
    return best;
  }
}
