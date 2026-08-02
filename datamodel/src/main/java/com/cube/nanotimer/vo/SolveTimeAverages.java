package com.cube.nanotimer.vo;

public class SolveTimeAverages extends SolveTime {

  private Long avgOf5;
  private Long avgOf12;
  private Long avgOf50;
  private Long avgOf100;

  private int rank;
  private int rankedCount;
  private Long runnerUp;

  public SolveTimeAverages() {
  }

  public Long getAvgOf5() {
    return avgOf5;
  }

  public void setAvgOf5(Long avgOf5) {
    this.avgOf5 = avgOf5;
  }

  public Long getAvgOf12() {
    return avgOf12;
  }

  public void setAvgOf12(Long avgOf12) {
    this.avgOf12 = avgOf12;
  }

  public Long getAvgOf50() {
    return avgOf50;
  }

  public void setAvgOf50(Long avgOf50) {
    this.avgOf50 = avgOf50;
  }

  public Long getAvgOf100() {
    return avgOf100;
  }

  public void setAvgOf100(Long avgOf100) {
    this.avgOf100 = avgOf100;
  }

  /**
   * Where the solve stands among every solve of its solve type, 1 for the best ever, 0 when it has
   * no standing to hold: a DNF, which the ranking leaves out along with every other one.
   *
   * <p>Lifetime rather than windowed, unlike the averages above: the averages say how you were
   * solving around then, the rank says what the solve was worth against everything you have done.
   */
  public int getRank() {
    return rank;
  }

  public void setRank(int rank) {
    this.rank = rank;
  }

  /** How many solves the rank was taken among, so a reader can tell 4th of 500 from 4th of 5. */
  public int getRankedCount() {
    return rankedCount;
  }

  public void setRankedCount(int rankedCount) {
    this.rankedCount = rankedCount;
  }

  /** The best time other than this one, set only for the best ever, null when it stands alone. */
  public Long getRunnerUp() {
    return runnerUp;
  }

  public void setRunnerUp(Long runnerUp) {
    this.runnerUp = runnerUp;
  }

}
