package com.cube.nanotimer.cube;

import com.cube.nanotimer.App;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The mean pause time of the steps planned without a recognition of their own (CFOP's cross, Roux's
 * two blocks) over a window's solves, for the bars that draw a solve type's averages: what a solve's
 * own bar pales inside those steps, averaged. Read off the kept readings, so a solve already read
 * costs nothing.
 */
public final class StepPauses {

  private static final Set<String> CFOP_STEPS = Collections.singleton("cross");
  private static final Set<String> ROUX_STEPS = new HashSet<String>(Arrays.asList("fb", "sb"));

  private StepPauses() {
  }

  /** Whether the method has any such step, which is whether there is anything to read. */
  public static boolean pales(CubeMethod method) {
    return method == CubeMethod.CFOP || method == CubeMethod.ROUX;
  }

  /** Each step's mean over the solves that reached it, a step met twice counted once. */
  public static Map<String, Long> fromReadings(List<SolveTime> solves, SolveReadings readings,
      CubeMethod method) {
    Set<String> paled = method == CubeMethod.CFOP ? CFOP_STEPS
        : method == CubeMethod.ROUX ? ROUX_STEPS : Collections.<String>emptySet();
    Map<String, Long> totals = new HashMap<String, Long>();
    Map<String, Integer> counts = new HashMap<String, Integer>();
    for (SolveTime solve : solves) {
      Map<String, Long> solvePauses = new HashMap<String, Long>();
      for (SolveReading.Turning turning : readings.readingOf(solve).getTurnings()) {
        if (paled.contains(turning.getCode())) {
          solvePauses.put(turning.getCode(), plus(solvePauses.get(turning.getCode()),
              turning.getStallMs()));
        }
      }
      for (Map.Entry<String, Long> step : solvePauses.entrySet()) {
        totals.put(step.getKey(), plus(totals.get(step.getKey()), step.getValue()));
        Integer count = counts.get(step.getKey());
        counts.put(step.getKey(), count == null ? 1 : count + 1);
      }
    }
    Map<String, Long> pauses = new HashMap<String, Long>();
    for (Map.Entry<String, Long> total : totals.entrySet()) {
      pauses.put(total.getKey(), total.getValue() / counts.get(total.getKey()));
    }
    return pauses;
  }

  private static long plus(Long sofar, long more) {
    return (sofar == null ? 0 : sofar) + more;
  }

  /**
   * Reads the means over the solve type's last {@code window} solves, keeping what had to be read.
   * The callback runs off the main thread, with an empty map for a method that pales nothing.
   */
  public static void read(SolveType solveType, final CubeMethod method, int window,
      final DataCallback<Map<String, Long>> callback) {
    if (!pales(method)) {
      callback.onData(Collections.<String, Long>emptyMap());
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
                    Map<String, Long> pauses = fromReadings(solves, readings, method);
                    App.INSTANCE.getService().saveSolveReadings(readings.unkept(),
                        SolveReading.VERSION, null);
                    callback.onData(pauses);
                  }
                });
          }
        });
  }
}
