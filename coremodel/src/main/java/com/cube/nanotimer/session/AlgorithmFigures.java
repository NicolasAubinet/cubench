package com.cube.nanotimer.session;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * How one step's cases were turned against the algorithms each case is usually taught with: how many
 * of the set went in with one of them, how many did not, and how many moves they cost over the usual.
 *
 * <p><b>Each case counts once, as it was turned the last time it came up</b>, the rule
 * {@link CaseKnowledge} reads a case by, so relearning a case moves the figure the next time it comes
 * up. Executions are therefore added newest first, and a case already counted keeps its later one.
 * Every case of the set is in one of three groups, standard, not standard or not seen, so the three
 * always add up to the set.
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

  private final int setSize;
  private final Set<String> counted = new HashSet<String>();
  private final Set<String> notStandardCases = new HashSet<String>();
  private int standard;
  private int extraMoves;

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
   * Counts one execution of a case, unless a later one of that case was counted already.
   *
   * @param moves the turns it took, and {@code usualMoves} those of the shortest listed algorithm,
   *     both counted as the tables count them
   */
  public void add(String caseCode, boolean isStandard, int moves, int usualMoves) {
    if (counted.size() >= setSize || !counted.add(caseCode)) {
      return;
    }
    if (isStandard) {
      standard++;
    } else {
      notStandardCases.add(caseCode);
    }
    extraMoves += extraMoves(isStandard, moves, usualMoves);
  }

  /** Whether every case of the set has been counted, so older solves can change nothing. */
  public boolean isComplete() {
    return counted.size() >= setSize;
  }

  public int getSetSize() {
    return setSize;
  }

  public int getStandard() {
    return standard;
  }

  public int getNotStandard() {
    return counted.size() - standard;
  }

  /** The counted cases that were not standard, by case code. */
  public Set<String> getNotStandardCases() {
    return Collections.unmodifiableSet(notStandardCases);
  }

  public int getNotSeen() {
    return setSize - counted.size();
  }

  /** The extra moves of every counted case, added up. */
  public int getExtraMoves() {
    return extraMoves;
  }
}
