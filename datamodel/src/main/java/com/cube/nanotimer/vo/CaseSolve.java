package com.cube.nanotimer.vo;

import java.io.Serializable;

/**
 * One time a case came up in a solve: the case's own time in it, and the whole solve, loaded the way
 * the history loads one so it can be opened from a list of them. One solve can meet the same F2L
 * pair case in two slots, and is then listed twice.
 */
public class CaseSolve implements Serializable {

  private final SolveTime solve;
  private final long recognitionMs;
  private final long totalMs;

  public CaseSolve(SolveTime solve, long recognitionMs, long totalMs) {
    this.solve = solve;
    this.recognitionMs = recognitionMs;
    this.totalMs = totalMs;
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
}
