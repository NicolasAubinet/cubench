package com.cube.nanotimer.vo;

import java.io.Serializable;

/**
 * How much a database holds, in the four figures a backup is described by.
 *
 * <p>Written into a backup's manifest, and read back out of it, so a restore can show what is in
 * the file next to what is about to be replaced. Deleted drill reps are counted like any other:
 * they are still in the file and still travel.
 */
public class BackupCounts implements Serializable {

  private final int solves;
  private final int solveTypes;
  private final int drills;
  private final int drillReps;

  public BackupCounts(int solves, int solveTypes, int drills, int drillReps) {
    this.solves = solves;
    this.solveTypes = solveTypes;
    this.drills = drills;
    this.drillReps = drillReps;
  }

  public int getSolves() {
    return solves;
  }

  public int getSolveTypes() {
    return solveTypes;
  }

  public int getDrills() {
    return drills;
  }

  /** Case reps and cross reps together: the screens say "reps", not which kind. */
  public int getDrillReps() {
    return drillReps;
  }

}
