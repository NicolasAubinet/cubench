package com.cube.nanotimer.cube;

import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;

import java.util.List;

/**
 * How a window's solves were turned as a whole: moves per solve and turns per second, counted the
 * way each solve's own detail sheet counts them.
 *
 * <p>Only solves read to the end count, so a solve stopped halfway cannot pull the move count down.
 * CFOP and Roux come off the kept readings; the other methods are read again here every time,
 * since nothing keeps their readings. Blind solve types never get here: the method solves query
 * leaves them out.
 */
public final class TurningFigures {

  private final int solves;
  private final long moves;
  private final long turningMs;

  private TurningFigures(int solves, long moves, long turningMs) {
    this.solves = solves;
    this.moves = moves;
    this.turningMs = turningMs;
  }

  /** Fixed figures, for the example window. */
  public static TurningFigures of(int solves, double meanMoves, double tps) {
    long moves = Math.round(meanMoves * solves);
    return new TurningFigures(solves, moves, Math.round(moves * 1000d / tps));
  }

  /**
   * @param readings where a CFOP or Roux solve's steps come from, read there only where none
   *     was kept
   * @param method what the solves are read as
   */
  public static TurningFigures readFrom(List<SolveTime> solves, SolveReadings readings,
      CubeMethod method) {
    int counted = 0;
    long moves = 0;
    long turningMs = 0;
    for (SolveTime solve : solves) {
      if (method == CubeMethod.CFOP || method == CubeMethod.ROUX) {
        SolveReading reading = readings.readingOf(solve);
        if (!reading.isWhole()) {
          continue;
        }
        for (SolveReading.Turning turning : reading.getTurnings()) {
          moves += turning.getMoves();
          // As SolveSolution.getTps: a step that turned nothing is not turning time.
          if (turning.getMoves() > 0) {
            turningMs += turning.getTimeMs();
          }
        }
        counted++;
      } else {
        SolveSolution solution = solutionOf(solve, method);
        if (solution == null || solution.getMoveCount() == 0 || solution.getTps() <= 0) {
          continue;
        }
        moves += solution.getMoveCount();
        turningMs += Math.round(solution.getMoveCount() * 1000d / solution.getTps());
        counted++;
      }
    }
    return new TurningFigures(counted, moves, turningMs);
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

  /**
   * Over the whole of every solve at once, so a short solve weighs what it lasted. A sighted solve
   * turns in every step, so its looking counts too.
   */
  public double getTps() {
    return turningMs == 0 ? 0 : moves * 1000d / turningMs;
  }
}
