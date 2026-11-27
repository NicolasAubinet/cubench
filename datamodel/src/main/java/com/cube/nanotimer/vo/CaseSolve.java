package com.cube.nanotimer.vo;

import java.io.Serializable;

/**
 * One time a case came up in a solve: the case's own time in it, and the whole solve, loaded the way
 * the history loads one so it can be opened from a list of them. One solve can meet the same F2L
 * pair case in two slots, and is then listed twice.
 */
public class CaseSolve implements Serializable {

  /** Why a time is left out of the case's mean. */
  public enum Mark { DNF, STOPPED, TWO_LOOKS, RESTART }

  private final SolveTime solve;
  private final long recognitionMs;
  private final long totalMs;
  private final Mark mark;

  public CaseSolve(SolveTime solve, long recognitionMs, long totalMs, Mark mark) {
    this.solve = solve;
    this.recognitionMs = recognitionMs;
    this.totalMs = totalMs;
    this.mark = mark;
  }

  public SolveTime getSolve() {
    return solve;
  }

  public long getRecognitionMs() {
    return recognitionMs;
  }

  public long getExecutionMs() {
    return totalMs - recognitionMs;
  }

  /** The case's time, recognition and execution together. */
  public long getTotalMs() {
    return totalMs;
  }

  /** Null where the time counts in the case's mean. */
  public Mark getMark() {
    return mark;
  }
}
