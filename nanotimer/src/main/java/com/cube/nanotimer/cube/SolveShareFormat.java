package com.cube.nanotimer.cube;

import android.content.Context;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;
import java.util.List;

/**
 * The smart-cube section of a shared solve: the breakdown as readable text, and, when the sharer
 * asks for it, the raw fields that let whoever receives it replay the solve offline.
 *
 * <p><b>The two halves are shared on different terms.</b> The breakdown is what the owner was
 * looking at, so it goes with every shared solve, unasked: it is the solve told in step times, the
 * thing there was anything to share about. The raw fields are for one purpose only — looking into a
 * reconstruction that came out wrong — and are noise, and kilobytes of it, to anybody else. They go
 * only where the sharer ticked the box that says so.
 *
 * <p><b>Where they do go, they have to be enough to re-derive the breakdown, not merely to repeat
 * it.</b> The moves and the step rows are already the output of the frame reading, so a solve
 * spelled through the wrong frame shares that spelling and nothing that could contradict it. The
 * gyro track is the only stored thing the reading is derived <em>from</em>, so it goes too: without
 * it a wrongly-read solve cannot be told from a wrongly-turned one. A blind solve's buffers go with
 * it for the same reason: the frame is theirs to settle and they live in the settings rather than on
 * the solve, so a paste without them cannot be read again the way it was read here.
 *
 * <p>The breakdown itself is read again rather than taken from the store, the way the solve sheet
 * reads it, so what is pasted somewhere else is what the owner was looking at.
 */
public final class SolveShareFormat {

  private SolveShareFormat() {
  }

  /**
   * @param gyroTrack the solve's stored track, read separately, or null where it has none
   * @param cube the cube that recorded the solve, read separately, or null where it is not known
   * @param withDebug whether the sharer asked for the raw fields as well as the breakdown
   */
  public static String smartcubeSection(Context context, SolveTime solveTime, String gyroTrack,
      String cube, boolean withDebug) {
    long durationMs = SolveBreakdown.solvingDurationMs(solveTime);
    // Read again rather than shared out of the store, so what is pasted somewhere else is what the
    // solve sheet shows: the sheet re-reads too, and a blind solve read again is named through the
    // frame its own buffers ask for rather than the one the gyro guessed at.
    StoredSolveReplay.Result reread = StoredSolveReplay.reinterpret(solveTime.getScramble(),
        solveTime.getSmartcubeMoves(), SolveTypeMethod.of(solveTime.getSolveType()));
    boolean fresh = reread != null && reread.getMethod() != null;
    CubeMethod method = fresh ? reread.getMethod() : solveTime.getSmartcubeMethod();
    String moves = fresh ? reread.getMoves() : solveTime.getSmartcubeMoves();
    List<SolveStep> steps = SolveBreakdown.withTail(
        fresh ? reread.getSteps() : solveTime.getSmartcubeSteps(),
        fresh ? reread.getStoppedStep() : solveTime.getSmartcubeStoppedStep(), durationMs, moves,
        method);
    StringBuilder sb = new StringBuilder();
    // Solves recorded before the method breakdown stopped being kept for a solve type with its own
    // steps still carry one. It is not shown anywhere, so it is not shared either.
    if (steps != null && !steps.isEmpty() && !solveTime.hasSteps()) {
      appendBreakdown(context, sb, steps, SolveSolution.from(moves, steps, method), method);
    }
    if (withDebug) {
      appendRawData(context, sb, solveTime, gyroTrack, cube, method);
    }
    return sb.toString();
  }

  private static void appendBreakdown(Context context, StringBuilder sb, List<SolveStep> steps,
      SolveSolution solution, CubeMethod method) {
    sb.append(context.getString(R.string.breakdown));
    if (!solution.isEmpty()) {
      sb.append(" (")
          .append(context.getString(R.string.breakdown_moves_count, solution.getMoveCount()))
          .append(" · ")
          .append(context.getString(R.string.breakdown_tps,
              FormatterService.INSTANCE.formatTps(solution.getTps())));
      // A blind solve is read by how many algorithms it took; a sighted method's parts are fixed.
      if (method == CubeMethod.BLIND && solution.getPartCount() > 0) {
        sb.append(" · ")
            .append(context.getString(R.string.breakdown_algs, solution.getPartCount()));
      }
      sb.append(')');
    }
    sb.append(":\n");
    int[][] partPositions = Utils.getSmartCubeSubStepPositions(steps);
    for (int i = 0; i < steps.size(); i++) {
      SolveStep step = steps.get(i);
      appendStepLine(context, sb, "- ", Utils.toSmartCubeStepDisplayName(context, step, i), step);
      List<SolveStep> parts = step.getSubSteps();
      if (parts.isEmpty()) {
        appendMovesLine(sb, "    ", stepMoves(solution, i));
      } else {
        for (int j = 0; j < parts.size(); j++) {
          appendStepLine(context, sb, "  - ",
              Utils.toSmartCubeStepLocalizedName(context, parts.get(j).getName(),
                  partPositions[i][j]), parts.get(j));
          appendMovesLine(sb, "      ", partMoves(solution, i, j));
        }
        appendMovesLine(sb, "    ", partMoves(solution, i, parts.size())); // turning past the last part
      }
    }
  }

  private static void appendStepLine(Context context, StringBuilder sb, String indent, String name,
      SolveStep step) {
    sb.append(indent).append(name).append(": ")
        .append(FormatterService.INSTANCE.formatSolveTime(step.getTotalMs()))
        .append(" (").append(context.getString(R.string.breakdown_recognition)).append(' ')
        .append(FormatterService.INSTANCE.formatSolveTime(step.getRecognitionMs()))
        .append(" · ").append(context.getString(R.string.breakdown_execution)).append(' ')
        .append(FormatterService.INSTANCE.formatSolveTime(step.getExecutionMs()))
        .append(")\n");
  }

  private static void appendMovesLine(StringBuilder sb, String indent, String moves) {
    if (!moves.isEmpty()) {
      sb.append(indent).append(moves).append('\n');
    }
  }

  private static String stepMoves(SolveSolution solution, int stepIndex) {
    return stepIndex < solution.getSteps().size()
        ? solution.getSteps().get(stepIndex).getMoves() : "";
  }

  private static String partMoves(SolveSolution solution, int stepIndex, int part) {
    return stepIndex < solution.getSteps().size()
        ? solution.getSteps().get(stepIndex).getPartMoves(part) : "";
  }

  /**
   * The stored fields verbatim, not the derived display: what an offline replay starts from.
   *
   * @param method the method the breakdown above was read as, so the one input the reading takes
   *     from outside the solve is shared where it means anything
   */
  private static void appendRawData(Context context, StringBuilder sb, SolveTime solveTime,
      String gyroTrack, String cube, CubeMethod method) {
    if (sb.length() > 0) {
      sb.append('\n');
    }
    sb.append(context.getString(R.string.share_smartcube_data)).append('\n');
    if (cube != null) {
      sb.append("cube: ").append(cube).append('\n');
    }
    if (solveTime.getSmartcubeMethod() != null) {
      sb.append("method: ").append(solveTime.getSmartcubeMethod().getCode()).append('\n');
    }
    // The stream as recorded, grip and all. Not the one the reading settled on: what the gyro made
    // of the pick-up is the first thing to look at when a reconstruction is wrong.
    sb.append("moves: ").append(solveTime.getSmartcubeMoves()).append('\n');
    // Which makes the buffers an input like the scramble, since a blind solve is named through them
    // rather than through that grip. See BlindFrame.
    if (method == CubeMethod.BLIND) {
      sb.append("buffers: ").append(Options.INSTANCE.getBlindEdgeBuffer()).append(' ')
          .append(Options.INSTANCE.getBlindCornerBuffer()).append('\n');
    }
    if (solveTime.hasSmartcubeBreakdown()) {
      sb.append("steps: ").append(SolveStepsFormat.format(solveTime.getSmartcubeSteps())).append('\n');
    }
    if (solveTime.getSmartcubeStoppedStep() != null) {
      sb.append("stopped_step: ").append(solveTime.getSmartcubeStoppedStep()).append('\n');
    }
    // Last, being the one field nobody reads and by far the longest.
    if (gyroTrack != null && !gyroTrack.isEmpty()) {
      sb.append("gyro: ").append(gyroTrack).append('\n');
    }
  }
}
