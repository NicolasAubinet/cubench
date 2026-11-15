package com.cube.nanotimer.cube;

import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;

import java.util.List;

/**
 * How a window's solves were turned as a whole: moves per solve, turns per second and time spent
 * paused, counted the way each solve's own detail sheet counts them.
 *
 * <p>Only solves read to the end count, so a solve stopped halfway cannot pull the move count down.
 * CFOP comes off the kept readings; the other methods are read again here, which is cheap without
 * the case matching that makes a CFOP reading worth keeping. Blind solve types never get here: the
 * method solves query leaves them out, and a memo would be most of any pause figure anyway.
 */
public final class TurningFigures {

  private final int solves;
  private final long moves;
  private final long turningMs;
  private final long pauseMs;

  private TurningFigures(int solves, long moves, long turningMs, long pauseMs) {
    this.solves = solves;
    this.moves = moves;
    this.turningMs = turningMs;
    this.pauseMs = pauseMs;
  }

  /** Fixed figures, for the example window. */
  public static TurningFigures of(int solves, double meanMoves, double tps, long meanPauseMs) {
    long moves = Math.round(meanMoves * solves);
    return new TurningFigures(solves, moves, Math.round(moves * 1000d / tps), meanPauseMs * solves);
  }

  /**
   * @param readings where a CFOP solve's steps come from, read there only where none was kept
   * @param method what the solves are read as
   */
  public static TurningFigures readFrom(List<SolveTime> solves, SolveReadings readings,
      CubeMethod method) {
    int counted = 0;
    long moves = 0;
    long turningMs = 0;
    long pauseMs = 0;
    for (SolveTime solve : solves) {
      if (method == CubeMethod.CFOP) {
        SolveReading reading = readings.readingOf(solve);
        if (!reading.isWhole()) {
          continue;
        }
        for (SolveReading.Turning turning : reading.getTurnings()) {
          moves += turning.getMoves();
          if (turning.getMoves() > 0) { // as SolveSolution.getTps: a step that turned nothing is not turning time
            turningMs += turning.getTimeMs();
          }
          pauseMs += turning.getStallMs();
        }
        counted++;
      } else {
        SolveSolution solution = solutionOf(solve, method);
        if (solution == null || solution.getMoveCount() == 0 || solution.getTps() <= 0) {
          continue;
        }
        moves += solution.getMoveCount();
        turningMs += Math.round(solution.getMoveCount() * 1000d / solution.getTps());
        for (SolveSolution.Step step : solution.getSteps()) {
          pauseMs += step.getPauseMs();
        }
        counted++;
      }
    }
    return new TurningFigures(counted, moves, turningMs, pauseMs);
  }

  /** Read again from the scramble, as the detail sheet does; null unless it ran to the solved cube. */
  private static SolveSolution solutionOf(SolveTime solve, CubeMethod method) {
    StoredSolveReplay.Result reread = StoredSolveReplay.reinterpret(solve.getScramble(),
        solve.getSmartcubeMoves(), method, SolveTypeMethod.blindMethodOf(solve.getSolveType()));
    if (reread == null || reread.getMethod() == null || reread.getStoppedStep() != null
        || !reread.reachedSolved()) {
      return null;
    }
    List<SolveStep> steps = SolveBreakdown.withTail(reread.getSteps(), null,
        SolveBreakdown.solvingDurationMs(solve), reread.getMoves(), reread.getMethod());
    return SolveSolution.from(reread.getMoves(), steps, reread.getMethod());
  }

  /** How many solves the figures were read from. 0 when none was read to the end. */
  public int getSolves() {
    return solves;
  }

  public double getMeanMoves() {
    return solves == 0 ? 0 : (double) moves / solves;
  }

  /** Over every turning second of the window at once, so a short solve weighs what it lasted. */
  public double getTps() {
    return turningMs == 0 ? 0 : moves * 1000d / turningMs;
  }

  public long getMeanPauseMs() {
    return solves == 0 ? 0 : pauseMs / solves;
  }
}
