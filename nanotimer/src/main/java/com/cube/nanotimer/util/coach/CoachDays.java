package com.cube.nanotimer.util.coach;

import com.cube.nanotimer.App;
import com.cube.nanotimer.coach.CoachPayloadBuilder;
import com.cube.nanotimer.coach.FocusArea;
import com.cube.nanotimer.coach.StepTurning;
import com.cube.nanotimer.coach.StoredCoachPlan;
import com.cube.nanotimer.cube.SolveReading;
import com.cube.nanotimer.cube.SolveReadings;
import com.cube.nanotimer.cube.TurningReader;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.CoachDay;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Reads a {@link CoachDay}, for the Plan tab's Today card and the evening recap alike. */
public final class CoachDays {

  private CoachDays() {
  }

  /**
   * Reads today for the solve type. The solves are replayed for their turning only when a focus of
   * the plan is about freezes, the one figure that needs it; replays are kept, so only new solves
   * cost anything. The callback runs off the main thread.
   *
   * @param plan the solve type's plan, null when there is none
   */
  public static void read(final SolveType solveType, StoredCoachPlan plan, final long dayStart,
      final DataCallback<CoachDay> callback) {
    if (!needsTurning(plan)) {
      App.INSTANCE.getService().getCoachDay(solveType, CubeMethod.CFOP, dayStart,
          Collections.<StepTurning>emptyList(), callback);
      return;
    }
    App.INSTANCE.getService().getMethodSolves(solveType, CubeMethod.CFOP,
        CoachPayloadBuilder.FAMILY_WINDOW, new DataCallback<List<SolveTime>>() {
          @Override
          public void onData(final List<SolveTime> solves) {
            App.INSTANCE.getService().getSolveReadings(SolveReadings.idsOf(solves),
                SolveReading.VERSION, new DataCallback<Map<Long, String>>() {
                  @Override
                  public void onData(Map<Long, String> kept) {
                    SolveReadings readings = new SolveReadings(kept);
                    List<StepTurning> turnings = TurningReader.readFrom(solves, readings);
                    App.INSTANCE.getService().saveSolveReadings(readings.unkept(),
                        SolveReading.VERSION, null);
                    App.INSTANCE.getService().getCoachDay(solveType, CubeMethod.CFOP, dayStart,
                        turnings, callback);
                  }
                });
          }
        });
  }

  /** Local midnight at the start of the day holding the given time. */
  public static long dayStart(long time) {
    Calendar day = Calendar.getInstance();
    day.setTimeInMillis(time);
    day.set(Calendar.HOUR_OF_DAY, 0);
    day.set(Calendar.MINUTE, 0);
    day.set(Calendar.SECOND, 0);
    day.set(Calendar.MILLISECOND, 0);
    return day.getTimeInMillis();
  }

  /** The local day holding the given time, as yyyymmdd. */
  public static int dayKey(long time) {
    Calendar day = Calendar.getInstance();
    day.setTimeInMillis(time);
    return day.get(Calendar.YEAR) * 10000 + (day.get(Calendar.MONTH) + 1) * 100
        + day.get(Calendar.DAY_OF_MONTH);
  }

  private static boolean needsTurning(StoredCoachPlan plan) {
    if (plan == null) {
      return false;
    }
    for (FocusArea area : plan.getPlan().getFocus()) {
      if (area.getReason() == FocusArea.Reason.STALLING) {
        return true;
      }
    }
    return false;
  }
}
