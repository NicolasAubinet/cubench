package com.cube.nanotimer.vo;

public class ExportResult {

  private int solveTimeId;
  private int cubeTypeId;
  private String cubeTypeName;
  private int solveTypeId;
  private String solveTypeName;
  private long time;
  private Long timeBeforeDnf;
  private long timestamp;
  private boolean plusTwo;
  private boolean blindType;
  private String scrambleTypeName;
  private String scramble;
  private String comment;

  private String[] stepsNames;
  private Long[] stepsTimes;

  public ExportResult(int solveTimeId, int cubeTypeId, String cubeTypeName, int solveTypeId, String solveTypeName,
                      long time, long timestamp, boolean plusTwo, boolean blindType, String scrambleTypeName, String scramble, String comment) {
    this(cubeTypeName, solveTypeName, time, timestamp, plusTwo, blindType, scrambleTypeName, scramble, comment);
    this.solveTimeId = solveTimeId;
    this.cubeTypeId = cubeTypeId;
    this.solveTypeId = solveTypeId;
  }

  public ExportResult(String cubeTypeName, String solveTypeName, long time, long timestamp, boolean plusTwo, boolean blindType,
                      String scrambleTypeName, String scramble, String comment) {
    this.cubeTypeName = cubeTypeName;
    this.solveTypeName = solveTypeName;
    this.time = time;
    this.timestamp = timestamp;
    this.plusTwo = plusTwo;
    this.blindType = blindType;
    this.scrambleTypeName = scrambleTypeName;
    this.scramble = scramble;
    this.comment = comment;
  }

  public int getSolveTimeId() {
    return solveTimeId;
  }

  public void setSolveTimeId(int solveTimeId) {
    this.solveTimeId = solveTimeId;
  }

  public long getTime() {
    return time;
  }

  public void setTime(long time) {
    this.time = time;
  }

  /** The time a DNF replaced, null when there is none to restore (see {@link SolveTime}). */
  public Long getTimeBeforeDnf() {
    return timeBeforeDnf;
  }

  public void setTimeBeforeDnf(Long timeBeforeDnf) {
    this.timeBeforeDnf = timeBeforeDnf;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
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

  public int getCubeTypeId() {
    return cubeTypeId;
  }

  public void setCubeTypeId(int cubeTypeId) {
    this.cubeTypeId = cubeTypeId;
  }

  public String getCubeTypeName() {
    return cubeTypeName;
  }

  public void setCubeTypeName(String cubeTypeName) {
    this.cubeTypeName = cubeTypeName;
  }

  public int getSolveTypeId() {
    return solveTypeId;
  }

  public void setSolveTypeId(int solveTypeId) {
    this.solveTypeId = solveTypeId;
  }

  public String getSolveTypeName() {
    return solveTypeName;
  }

  public void setSolveTypeName(String solveTypeName) {
    this.solveTypeName = solveTypeName;
  }

  public boolean isPlusTwo() {
    return plusTwo;
  }

  public void setPlusTwo(boolean plusTwo) {
    this.plusTwo = plusTwo;
  }

  public boolean isBlindType() {
    return blindType;
  }

  public void setBlindType(boolean blindType) {
    this.blindType = blindType;
  }

  public String getScrambleTypeName() {
    return scrambleTypeName;
  }

  public void setScrambleTypeName(String scrambleTypeName) {
    this.scrambleTypeName = scrambleTypeName;
  }

  public String[] getStepsNames() {
    return stepsNames;
  }

  public void setStepsNames(String[] stepsNames) {
    this.stepsNames = stepsNames;
  }

  public Long[] getStepsTimes() {
    return stepsTimes;
  }

  public void setStepsTimes(Long[] stepsTimes) {
    this.stepsTimes = stepsTimes;
  }

  public boolean hasSteps() {
    return stepsNames != null && stepsTimes != null;
  }

}
