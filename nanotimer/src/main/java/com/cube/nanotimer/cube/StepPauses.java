package com.cube.nanotimer.cube;

import com.cube.nanotimer.App;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.MethodStatistics;
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
 * The mean pause time of the steps planned without a recognition of their own, over a window's
 * solves, for the bars that draw a solve type's averages: what a solve's own bar pales inside those
 * steps, averaged. CFOP's cross comes off the kept readings the coach's stall figures come from;
 * Roux's two blocks are read again from the stored moves.
 */
public final class StepPauses {

  public static final String CROSS = "cross";
  private static final Set<String> ROUX_BLOCKS = new HashSet<String>(Arrays.asList("fb", "sb"));

  private StepPauses() {
  }

  /** CFOP: the cross's mean over the solves whose cross was read. Empty when none was. */
  public static Map<String, Long> fromReadings(List<SolveTime> solves, SolveReadings readings) {
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
    Map<String, Long> pauses = new HashMap<String, Long>();
    if (counted > 0) {
      pauses.put(CROSS, total / counted);
    }
    return pauses;
  }

  /** Roux: each block's mean over the solves that built it, a block built twice counted once. */
  public static Map<String, Long> fromSolutions(List<SolveSolution> solutions) {
    Map<String, Long> totals = new HashMap<String, Long>();
    Map<String, Integer> counts = new HashMap<String, Integer>();
    for (SolveSolution solution : solutions) {
      Map<String, Long> solve = new HashMap<String, Long>();
      for (SolveSolution.Step step : solution.getSteps()) {
        String family = MethodStatistics.familyOf(step.getName());
        if (ROUX_BLOCKS.contains(family)) {
          Long sofar = solve.get(family);
          solve.put(family, (sofar == null ? 0 : sofar) + step.getPauseMs());
        }
      }
      for (Map.Entry<String, Long> block : solve.entrySet()) {
        Long total = totals.get(block.getKey());
        Integer count = counts.get(block.getKey());
        totals.put(block.getKey(), (total == null ? 0 : total) + block.getValue());
        counts.put(block.getKey(), (count == null ? 0 : count) + 1);
      }
    }
    Map<String, Long> pauses = new HashMap<String, Long>();
    for (Map.Entry<String, Long> total : totals.entrySet()) {
      pauses.put(total.getKey(), total.getValue() / counts.get(total.getKey()));
    }
    return pauses;
  }

  /**
   * Reads the means over the solve type's last {@code window} solves, keeping what had to be read.
   * The callback runs off the main thread, with an empty map for a method that pales no pauses.
   */
  public static void read(SolveType solveType, final CubeMethod method, int window,
      final DataCallback<Map<String, Long>> callback) {
    if (method != CubeMethod.CFOP && method != CubeMethod.ROUX) {
      callback.onData(Collections.<String, Long>emptyMap());
      return;
    }
    App.INSTANCE.getService().getMethodSolves(solveType, method, window,
        new DataCallback<List<SolveTime>>() {
          @Override
          public void onData(final List<SolveTime> solves) {
            if (method == CubeMethod.ROUX) {
              callback.onData(fromSolutions(TurningFigures.solutionsOf(solves, method)));
              return;
            }
            App.INSTANCE.getService().getSolveReadings(SolveReadings.idsOf(solves),
                SolveReading.VERSION, new DataCallback<Map<Long, String>>() {
                  @Override
                  public void onData(Map<Long, String> kept) {
                    SolveReadings readings = new SolveReadings(kept);
                    Map<String, Long> pauses = fromReadings(solves, readings);
                    App.INSTANCE.getService().saveSolveReadings(readings.unkept(),
                        SolveReading.VERSION, null);
                    callback.onData(pauses);
                  }
                });
          }
        });
  }
}
