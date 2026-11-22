package com.cube.nanotimer.cube;

import com.cube.nanotimer.App;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.smartcube.drill.PairSighting;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The F2L pairs of a solve type's recent solves, as {@code PairWeights} ranks them for a pair drill
 * and the coach card showing where it opens.
 *
 * <p>The window is {@link #WINDOW} solves, longer than the coach's family window: a pair case comes
 * up 5 to 19 times in 100 solves, too few to tell a rate from noise.
 */
public final class PairSightings {

  public static final int WINDOW = 300;

  private PairSightings() {
  }

  /** Every pair a reading could set against its case's algorithms, the others left out. */
  public static List<PairSighting> fromReadings(List<SolveTime> solves, SolveReadings readings) {
    List<PairSighting> sightings = new ArrayList<PairSighting>();
    for (SolveTime solve : solves) {
      for (SolveReading.Execution execution : readings.of(solve)) {
        if (SolveReading.F2L.equals(execution.getFamily()) && execution.isRead()) {
          sightings.add(new PairSighting(execution.getCaseCode(), execution.getTimeMs(),
              execution.getUsualMoves(), execution.isUndone(), execution.isStopped()));
        }
      }
    }
    return sightings;
  }

  /**
   * Reads the solve type's last {@link #WINDOW} solves, keeping what had to be read. The callback
   * runs off the main thread.
   */
  public static void read(SolveType solveType, final DataCallback<List<PairSighting>> callback) {
    App.INSTANCE.getService().getMethodSolves(solveType, SolveTypeMethod.of(solveType), WINDOW,
        new DataCallback<List<SolveTime>>() {
          @Override
          public void onData(final List<SolveTime> solves) {
            App.INSTANCE.getService().getSolveReadings(SolveReadings.idsOf(solves),
                SolveReading.VERSION, new DataCallback<Map<Long, String>>() {
                  @Override
                  public void onData(Map<Long, String> kept) {
                    SolveReadings readings = new SolveReadings(kept);
                    List<PairSighting> sightings = fromReadings(solves, readings);
                    App.INSTANCE.getService().saveSolveReadings(readings.unkept(),
                        SolveReading.VERSION, null);
                    callback.onData(sightings);
                  }
                });
          }
        });
  }
}
