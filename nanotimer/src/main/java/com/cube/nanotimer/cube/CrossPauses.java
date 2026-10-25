package com.cube.nanotimer.cube;

import com.cube.nanotimer.App;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;

import java.util.List;
import java.util.Map;

/**
 * The cross's mean stops over a window's solves, for the bars that draw a solve type's averages:
 * the stops a solve's own bar pales inside its cross, averaged. Read off the kept readings the
 * coach's stall figures come from, so a solve already read costs nothing.
 */
public final class CrossPauses {

  public static final String CROSS = "cross";

  private CrossPauses() {
  }

  /** The mean over the solves whose cross was read; 0 when none was. */
  public static long meanOf(List<SolveTime> solves, SolveReadings readings) {
    long total = 0;
    int counted = 0;
    for (SolveTime solve : solves) {
      for (SolveReading.Turning turning : readings.readingOf(solve).getTurnings()) {
        if (CROSS.equals(turning.getCode())) {
          total += turning.getStallMs();
          counted++;
          break;
        }
      }
    }
    return counted == 0 ? 0 : total / counted;
  }

  /**
   * Reads the mean over the solve type's last {@code window} solves, keeping what had to be read.
   * The callback runs off the main thread, with 0 for a method other than CFOP, which nothing reads.
   */
  public static void read(SolveType solveType, CubeMethod method, int window,
      final DataCallback<Long> callback) {
    if (method != CubeMethod.CFOP) {
      callback.onData(Long.valueOf(0));
      return;
    }
    App.INSTANCE.getService().getMethodSolves(solveType, method, window,
        new DataCallback<List<SolveTime>>() {
          @Override
          public void onData(final List<SolveTime> solves) {
            App.INSTANCE.getService().getSolveReadings(SolveReadings.idsOf(solves),
                SolveReading.VERSION, new DataCallback<Map<Long, String>>() {
                  @Override
                  public void onData(Map<Long, String> kept) {
                    SolveReadings readings = new SolveReadings(kept);
                    long mean = meanOf(solves, readings);
                    App.INSTANCE.getService().saveSolveReadings(readings.unkept(),
                        SolveReading.VERSION, null);
                    callback.onData(Long.valueOf(mean));
                  }
                });
          }
        });
  }
}
