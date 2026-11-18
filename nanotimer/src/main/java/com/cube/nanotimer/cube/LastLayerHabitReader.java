package com.cube.nanotimer.cube;

import com.cube.nanotimer.coach.PairExecution;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.List;

/**
 * Every OLL and PLL of a window's solves taken in one algorithm, set against the algorithms its case
 * is usually taught with, for the coach's last layer moves. Read off the same readings as
 * {@link F2lHabitReader}, so it costs no replay of its own.
 *
 * <p>A step taken in more than one algorithm is left out: that is a two-look, counted and carded on
 * its own, and set against the one-look algorithm it would read as a long algorithm instead.
 */
public final class LastLayerHabitReader {

  private LastLayerHabitReader() {
  }

  /**
   * @param solves the window's solves, whatever they were solved as: only CFOP is read
   * @param readings where each solve's executions come from, read there only where none was kept
   */
  public static List<PairExecution> readFrom(List<SolveTime> solves, SolveReadings readings) {
    List<PairExecution> executions = new ArrayList<PairExecution>();
    for (SolveTime solve : solves) {
      for (SolveReading.Execution execution : readings.of(solve)) {
        boolean lastLayer = AlgorithmFiguresReader.OLL.equals(execution.getFamily())
            || AlgorithmFiguresReader.PLL.equals(execution.getFamily()); // a Roux CMLL is not
        if (!lastLayer || !execution.isRead() || execution.getAlgorithms() > 1) {
          continue;
        }
        executions.add(PairExecution.of(solve.getId(), execution.getCaseCode(),
            execution.getTimeMs(), execution.getMoves(), execution.getUsualMoves(),
            execution.isListed()));
      }
    }
    return executions;
  }
}
