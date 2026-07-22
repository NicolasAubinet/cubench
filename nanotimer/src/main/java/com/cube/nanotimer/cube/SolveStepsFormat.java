package com.cube.nanotimer.cube;

import com.cube.nanotimer.vo.SolveStep;
import java.util.List;

/**
 * The exported form of a solve's step rows: one token per row, mirroring the DB columns, as in
 * {@code "0:cross:790:1520 1.0:pair_rf:550:1480"} ({@code index[.part]:name:recognition:execution},
 * times in ms). Shared by the share payload and the CSV export so a solve travels in one format.
 */
public final class SolveStepsFormat {

  private static final char FIELD_SEPARATOR = ':';

  private SolveStepsFormat() {
  }

  public static String format(List<SolveStep> steps) {
    StringBuilder sb = new StringBuilder();
    for (SolveStep step : steps) {
      append(sb, String.valueOf(step.getStepIndex()), step);
      List<SolveStep> parts = step.getSubSteps();
      for (int i = 0; i < parts.size(); i++) {
        append(sb, step.getStepIndex() + "." + i, parts.get(i));
      }
    }
    return sb.toString();
  }

  private static void append(StringBuilder sb, String index, SolveStep step) {
    if (sb.length() > 0) {
      sb.append(' ');
    }
    sb.append(index).append(FIELD_SEPARATOR).append(step.getName())
        .append(FIELD_SEPARATOR).append(step.getRecognitionMs())
        .append(FIELD_SEPARATOR).append(step.getExecutionMs());
  }
}
