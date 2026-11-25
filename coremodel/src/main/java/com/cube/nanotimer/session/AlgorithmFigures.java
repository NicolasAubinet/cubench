package com.cube.nanotimer.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How one step's cases were turned against the algorithms each case is usually taught with: how many
 * of the set went in with one of them, how many did not, and how many moves they cost over the usual.
 *
 * <p><b>Each case counts once, as it is usually turned: the majority of its last
 * {@link #RECENT} executions</b>, so one odd execution (a case met from an unusual angle) does not
 * flip it, and switching to a new algorithm shows after two. A case seen twice with two verdicts
 * goes by the later one. Executions are added newest first. Every case of the set is in one of three
 * groups, standard, not standard or not seen, so the three always add up to the set.
 *
 * <p><b>Standard means on the case's list and nothing else.</b> A case taken in two algorithms is not
 * standard, and neither is an algorithm of the solver's own, even one they asked not to be flagged
 * for. Which algorithms a case is listed with is the tables' business, not this class's.
 *
 * <p><b>Extra moves</b> is one definition, shared by every screen and by the coach: none for an
 * execution that is one of its case's listed algorithms, whichever of them, since a solver turning a
 * recommended algorithm is not wasting moves because a shorter one is also listed (owner,
 * 2026-09-19). Any other execution costs the turns it took over the shortest listed algorithm, both
 * counted the same way, and never below zero, since a shorter execution is not a cost and must not
 * cancel a longer one out of a total. The word "optimal" is deliberately absent: the lists hold the
 * algorithms a case is usually taught with, not every algorithm there is.
 */
public final class AlgorithmFigures {

  /** How many of a case's latest executions its verdict is the majority of. */
  static final int RECENT = 3;

  private final int setSize;
  /** Each case's latest executions, newest first: {standard 1/0, extra moves}. */
  private final Map<String, List<int[]>> recent = new LinkedHashMap<String, List<int[]>>();
  private int full;

  /** @param setSize how many cases the step's set holds, seen or not */
  public AlgorithmFigures(int setSize) {
    this.setSize = setSize;
  }

  /**
   * The turns taken over the usual ones: 0 for a listed algorithm, or where there is nothing to
   * compare with.
   *
   * @param listed whether the execution is one of its case's listed algorithms
   * @param usualMoves the turns of the shortest listed algorithm
   */
  public static int extraMoves(boolean listed, int moves, int usualMoves) {
    return listed || usualMoves <= 0 ? 0 : Math.max(0, moves - usualMoves);
  }

  /**
   * Counts one execution of a case, unless {@link #RECENT} later ones of that case were counted
   * already.
   *
   * @param moves the turns it took, and {@code usualMoves} those of the shortest listed algorithm,
   *     both counted as the tables count them
   */
  public void add(String caseCode, boolean isStandard, int moves, int usualMoves) {
    List<int[]> executions = recent.get(caseCode);
    if (executions == null) {
      if (recent.size() >= setSize) {
        return;
      }
      executions = new ArrayList<int[]>();
      recent.put(caseCode, executions);
    }
    if (executions.size() >= RECENT) {
      return;
    }
    executions.add(new int[] {isStandard ? 1 : 0, extraMoves(isStandard, moves, usualMoves)});
    if (executions.size() == RECENT) {
      full++;
    }
  }

  /** The execution a case is judged by: the latest one agreeing with the majority. */
  private static int[] usual(List<int[]> executions) {
    int standard = 0;
    for (int[] execution : executions) {
      standard += execution[0];
    }
    int other = executions.size() - standard;
    // A tie, only possible over two, goes to the later one
    int verdict = standard == other ? executions.get(0)[0] : standard > other ? 1 : 0;
    for (int[] execution : executions) {
      if (execution[0] == verdict) {
        return execution;
      }
    }
    return executions.get(0);
  }

  /** Whether every case has been counted {@link #RECENT} times, so older solves change nothing. */
  public boolean isComplete() {
    return full >= setSize;
  }

  public int getSetSize() {
    return setSize;
  }

  public int getStandard() {
    int standard = 0;
    for (List<int[]> executions : recent.values()) {
      standard += usual(executions)[0];
    }
    return standard;
  }

  public int getNotStandard() {
    return recent.size() - getStandard();
  }

  /** The counted cases that were not standard, by case code. */
  public Set<String> getNotStandardCases() {
    Set<String> cases = new HashSet<String>();
    for (Map.Entry<String, List<int[]>> entry : recent.entrySet()) {
      if (usual(entry.getValue())[0] == 0) {
        cases.add(entry.getKey());
      }
    }
    return Collections.unmodifiableSet(cases);
  }

  public int getNotSeen() {
    return setSize - recent.size();
  }

  /** The extra moves of every counted case, added up. */
  public int getExtraMoves() {
    int extra = 0;
    for (List<int[]> executions : recent.values()) {
      extra += usual(executions)[1];
    }
    return extra;
  }
}
