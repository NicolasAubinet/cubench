package com.cube.nanotimer.cube;

import android.content.Context;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;
import java.util.List;

/**
 * The smart-cube section of a shared solve: the breakdown as readable text, then the raw fields
 * that let whoever receives it replay the solve offline — sharing one is how a user hands over
 * everything needed to look into a solve, which is the route a reconstruction bug report takes.
 *
 * <p><b>The raw fields have to be enough to re-derive the breakdown, not merely to repeat it.</b>
 * The moves and the step rows are already the output of the frame reading, so a solve spelled
 * through the wrong frame shares that spelling and nothing that could contradict it. The gyro track
 * is the only stored thing the reading is derived <em>from</em>, so it goes too, kilobytes and all:
 * without it a wrongly-read solve cannot be told from a wrongly-turned one.
 */
public final class SolveShareFormat {

  private SolveShareFormat() {
  }

  /** @param gyroTrack the solve's stored track, read separately, or null where it has none */
  public static String smartcubeSection(Context context, SolveTime solveTime, String gyroTrack) {
    long durationMs = SolveBreakdown.solvingDurationMs(solveTime);
    CubeMethod method = solveTime.getSmartcubeMethod();
    List<SolveStep> steps = SolveBreakdown.withTail(solveTime.getSmartcubeSteps(),
        solveTime.getSmartcubeStoppedStep(), durationMs, solveTime.getSmartcubeMoves(), method);
    StringBuilder sb = new StringBuilder();
    // Solves recorded before the method breakdown stopped being kept for a solve type with its own
    // steps still carry one. It is not shown anywhere, so it is not shared either.
    if (steps != null && !steps.isEmpty() && !solveTime.hasSteps()) {
      appendBreakdown(context, sb, steps,
          SolveSolution.from(solveTime.getSmartcubeMoves(), steps, method), method);
    }
    appendRawData(context, sb, solveTime, gyroTrack);
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

  /** The stored fields verbatim, not the derived display: what an offline replay starts from. */
  private static void appendRawData(Context context, StringBuilder sb, SolveTime solveTime,
      String gyroTrack) {
    if (sb.length() > 0) {
      sb.append('\n');
    }
    sb.append(context.getString(R.string.share_smartcube_data)).append('\n');
    if (solveTime.getSmartcubeMethod() != null) {
      sb.append("method: ").append(solveTime.getSmartcubeMethod().getCode()).append('\n');
    }
    sb.append("moves: ").append(solveTime.getSmartcubeMoves()).append('\n');
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
