package com.cube.nanotimer.vo;

import java.util.ArrayList;
import java.util.List;

public class SessionDetails {

  private int totalSolvesCount;
  private List<SolveTime> solves = new ArrayList<SolveTime>(); // newest first
  private long sessionStart;

  public SessionDetails() {
  }

  public int getTotalSolvesCount() {
    return totalSolvesCount;
  }

  public void setTotalSolvesCount(int totalSolvesCount) {
    this.totalSolvesCount = totalSolvesCount;
  }

  public List<SolveTime> getSolves() {
    return solves;
  }

  public void setSolves(List<SolveTime> solves) {
    this.solves = solves;
  }

  public List<Long> getSessionTimes() {
    List<Long> times = new ArrayList<Long>();
    for (SolveTime solve : solves) {
      times.add(solve.getTime());
    }
    return times;
  }

  public int getSessionSolvesCount() {
    return solves.size();
  }

  public long getSessionStart() {
    return sessionStart;
  }

  public void setSessionStart(long sessionStart) {
    this.sessionStart = sessionStart;
  }

}
