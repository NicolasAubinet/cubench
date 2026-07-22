package com.cube.nanotimer.cube;

import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
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
    String name = step.getName();
    if (name == null || name.isEmpty() || name.indexOf(' ') >= 0 || name.indexOf(FIELD_SEPARATOR) >= 0) {
      // A name the grammar cannot carry must fail here, loudly, not produce a token parse() rejects.
      throw new IllegalArgumentException("Step name not serializable: \"" + name + "\"");
    }
    if (sb.length() > 0) {
      sb.append(' ');
    }
    sb.append(index).append(FIELD_SEPARATOR).append(name)
        .append(FIELD_SEPARATOR).append(step.getRecognitionMs())
        .append(FIELD_SEPARATOR).append(step.getExecutionMs());
  }

  /**
   * Parses the exported form back, strictly: an import must reject a corrupt record with a clear
   * error rather than quietly load part of a breakdown. Null or blank parses to an empty list.
   *
   * @throws IllegalArgumentException naming the offending token when the input is malformed
   */
  public static List<SolveStep> parse(String stored) {
    List<SolveStep> steps = new ArrayList<SolveStep>();
    if (stored == null || stored.trim().isEmpty()) {
      return steps;
    }
    int parentIndex = -1;
    List<SolveStep> parts = null;
    SolveStep pending = null;
    for (String token : stored.trim().split(" +")) {
      String[] fields = token.split(String.valueOf(FIELD_SEPARATOR));
      if (fields.length != 4) {
        throw new IllegalArgumentException("Invalid step token: \"" + token + "\"");
      }
      String name = fields[1];
      if (name.isEmpty()) {
        throw new IllegalArgumentException("Empty step name in token: \"" + token + "\"");
      }
      long recognitionMs = parseTime(fields[2], token);
      long executionMs = parseTime(fields[3], token);
      int dot = fields[0].indexOf('.');
      if (dot < 0) { // a step: close the pending one, then open this one
        int index = parseIndex(fields[0], token);
        if (index <= parentIndex) {
          throw new IllegalArgumentException("Step indexes must increase, got: \"" + token + "\"");
        }
        closePending(steps, pending, parts);
        parentIndex = index;
        parts = new ArrayList<SolveStep>();
        pending = new SolveStep(index, name, recognitionMs, executionMs, new ArrayList<SolveStep>());
      } else { // a part: must belong to the step being read, in order
        if (pending == null || parseIndex(fields[0].substring(0, dot), token) != parentIndex) {
          throw new IllegalArgumentException("Part does not follow its step: \"" + token + "\"");
        }
        if (parseIndex(fields[0].substring(dot + 1), token) != parts.size()) {
          throw new IllegalArgumentException("Parts must be numbered in order, got: \"" + token + "\"");
        }
        parts.add(new SolveStep(parentIndex, name, recognitionMs, executionMs,
            new ArrayList<SolveStep>()));
      }
    }
    closePending(steps, pending, parts);
    return steps;
  }

  /** The pending step is rebuilt on close so its parts land inside the constructor's copy. */
  private static void closePending(List<SolveStep> steps, SolveStep pending, List<SolveStep> parts) {
    if (pending != null) {
      steps.add(new SolveStep(pending.getStepIndex(), pending.getName(),
          pending.getRecognitionMs(), pending.getExecutionMs(), parts));
    }
  }

  private static long parseTime(String value, String token) {
    long time = parseNumber(value, token);
    if (time < 0) {
      throw new IllegalArgumentException("Negative step time in token: \"" + token + "\"");
    }
    return time;
  }

  private static int parseIndex(String value, String token) {
    long index = parseNumber(value, token);
    if (index < 0 || index > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Invalid step index in token: \"" + token + "\"");
    }
    return (int) index;
  }

  private static long parseNumber(String value, String token) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid number in token: \"" + token + "\"");
    }
  }
}
