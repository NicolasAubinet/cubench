package com.cube.nanotimer.vo;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class SolveTime implements Serializable {

  private int id;
  private long timestamp;
  private long time;
  private boolean plusTwo;
  private boolean pb;
  private String scramble;
  private String comment;
  private SolveType solveType;
  private Long[] stepsTimes;
  private CubeMethod smartcubeMethod;
  private List<SolveStep> smartcubeSteps;

  public SolveTime() {
  }

  public SolveTime(long timestamp, long time, boolean plusTwo, String scramble, SolveType solveType) {
    this.timestamp = timestamp;
    this.time = time;
    this.plusTwo = plusTwo;
    this.scramble = scramble;
    this.solveType = solveType;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public long getTime() {
    return time;
  }

  public void setTime(long time) {
    this.time = time;
  }

  public boolean isPlusTwo() {
    return plusTwo;
  }

  public boolean isPb() {
    return pb;
  }

  public void setPb(boolean pb) {
    this.pb = pb;
  }

  public String getScramble() {
    return scramble;
  }

  public void setScramble(String scramble) {
    this.scramble = scramble;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public SolveType getSolveType() {
    return solveType;
  }

  public void setSolveType(SolveType solveType) {
    this.solveType = solveType;
  }

  public Long[] getStepsTimes() {
    return stepsTimes;
  }

  public void setStepsTimes(Long[] stepsTimes) {
    this.stepsTimes = stepsTimes;
  }

  public boolean isDNF() {
    return time < 0;
  }

  public void setPlusTwo(boolean plusTwo, boolean adjustTime) {
    if (this.plusTwo != plusTwo) {
      this.plusTwo = plusTwo;

      if (adjustTime) {
        if (plusTwo) {
          setTime(time + 2000);
        } else {
          setTime(time - 2000);
        }
      }
    }
  }

  public boolean hasSteps() {
    return stepsTimes != null && stepsTimes.length > 0;
  }

  public CubeMethod getSmartcubeMethod() {
    return smartcubeMethod;
  }

  public void setSmartcubeMethod(CubeMethod smartcubeMethod) {
    this.smartcubeMethod = smartcubeMethod;
  }

  public List<SolveStep> getSmartcubeSteps() {
    return smartcubeSteps;
  }

  public void setSmartcubeSteps(List<SolveStep> smartcubeSteps) {
    this.smartcubeSteps = smartcubeSteps;
  }

  /** Whether this solve carries a smart cube method breakdown (matched the method when solved). */
  public boolean hasSmartcubeBreakdown() {
    return smartcubeSteps != null && !smartcubeSteps.isEmpty();
  }

  @Override
  public String toString() {
    return "SolveTime{" +
        ", time=" + time +
        ", stepsTimes=" + Arrays.toString(stepsTimes) +
        '}';
  }

}
