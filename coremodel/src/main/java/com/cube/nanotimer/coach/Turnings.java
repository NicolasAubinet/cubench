package com.cube.nanotimer.coach;

import com.cube.nanotimer.session.MethodStatistics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Each step's turning, and the whole solve's, folded into the figures a payload carries.
 *
 * <p>A step's figure is read over the solves it was turned in: a skip turned nothing of its own, so
 * it is left out as the step's time figures leave it out, and a mean of moves with the skips in it
 * would read as efficiency. The whole solve is read over the solves every step of which was read,
 * or its moves would be a solve short of a step. The outlier rule judges by time, as everywhere
 * else.
 */
final class Turnings {

  private Turnings() {
  }

  /** Each step in solving order, then the whole solve, each where {@code floor} solves hold it. */
  static List<TurningFigure> of(List<StepTurning> turnings, int floor) {
    Map<String, List<long[]>> byCode = new LinkedHashMap<String, List<long[]>>();
    Map<Long, long[]> wholeSolves = new LinkedHashMap<Long, long[]>();
    for (StepTurning turning : turnings) {
      if (turning.isWholeSolve()) {
        long[] solve = wholeSolves.get(Long.valueOf(turning.getSolveId()));
        if (solve == null) {
          solve = new long[5];
          wholeSolves.put(Long.valueOf(turning.getSolveId()), solve);
        }
        add(solve, turning);
      }
      if (MethodStatistics.SKIP.equals(MethodStatistics.caseOf(turning.getCode()))) {
        continue;
      }
      String family = MethodStatistics.familyOf(turning.getCode());
      List<long[]> ofFamily = byCode.get(family);
      if (ofFamily == null) {
        ofFamily = new ArrayList<long[]>();
        byCode.put(family, ofFamily);
      }
      long[] step = new long[5];
      add(step, turning);
      ofFamily.add(step);
    }
    byCode.put(CoachPayloadBuilder.SOLVE, new ArrayList<long[]>(wholeSolves.values()));
    List<TurningFigure> figures = new ArrayList<TurningFigure>();
    for (Map.Entry<String, List<long[]>> entry : byCode.entrySet()) {
      TurningFigure figure = figure(entry.getKey(), entry.getValue(), floor);
      if (figure != null) {
        figures.add(figure);
      }
    }
    return figures;
  }

  /** Time, moves, stalls, stall time and stop time, added up. */
  private static void add(long[] into, StepTurning turning) {
    into[0] += turning.getTimeMs();
    into[1] += turning.getMoves();
    into[2] += turning.getStalls();
    into[3] += turning.getStallMs();
    into[4] += turning.getStopMs();
  }

  private static TurningFigure figure(String code, List<long[]> occurrences, int floor) {
    double[] times = new double[occurrences.size()];
    for (int i = 0; i < times.length; i++) {
      times[i] = occurrences.get(i)[0];
    }
    boolean[] kept = StepTallies.kept(times);
    int count = 0;
    double moves = 0;
    double stalls = 0;
    double stallMs = 0;
    double stopMs = 0;
    for (int i = 0; i < kept.length; i++) {
      if (kept[i]) {
        count++;
        moves += occurrences.get(i)[1];
        stalls += occurrences.get(i)[2];
        stallMs += occurrences.get(i)[3];
        stopMs += occurrences.get(i)[4];
      }
    }
    if (count < floor || count == 0) {
      return null;
    }
    return new TurningFigure(code, count, moves / count, stalls / count,
        Math.round(stallMs / count), Math.round(stopMs / count),
        (double) (kept.length - count) / kept.length);
  }
}
