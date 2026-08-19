package com.cube.nanotimer.vo;

import com.cube.nanotimer.session.CaseKnowledge;

import java.util.Collections;
import java.util.List;

/**
 * What the solver's cases are read from: the status of every case that has one, and the solves the
 * moves behind them are read back out of.
 *
 * <p>The two travel together because they are one answer to one question and are read in one pass
 * of the database. The statuses come from a table; the moves do not, since what a solver turned is
 * only in the solve, so the solves come along and whoever asked splits their moves up.
 */
public class CaseHistory {

  private final List<CaseKnowledge> cases;
  private final List<SolveTime> solves;

  public CaseHistory(List<CaseKnowledge> cases, List<SolveTime> solves) {
    this.cases = Collections.unmodifiableList(cases);
    this.solves = Collections.unmodifiableList(solves);
  }

  /** Every case with a status, most recently seen first. */
  public List<CaseKnowledge> getCases() {
    return cases;
  }

  /** The most recent solves a cube recorded the moves of, newest first. */
  public List<SolveTime> getSolves() {
    return solves;
  }
}
