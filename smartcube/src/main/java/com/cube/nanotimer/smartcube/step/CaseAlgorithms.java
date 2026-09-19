package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The algorithms of a case, asked of whichever table holds them: {@link LastLayerCaseAlgorithms} for
 * a last layer case ({@code oll_21}, {@code pll_jb}), {@link F2LCaseAlgorithms} for an F2L pair's
 * ({@code pair_27}). One set of rules for both, so a screen showing either never has to know which.
 *
 * <p>An execution that is none of the listed algorithms keeps the faces the solver turned either
 * way. A pair's may need one rotation in front of it, since a pair turned into another slot only
 * solves the case as drawn, into front right, once the cube is stood that way.
 */
public final class CaseAlgorithms {

  private static final String PAIR_PREFIX = "pair_";

  private CaseAlgorithms() {
  }

  /** Whether the code names an F2L pair's case rather than a last layer one. */
  public static boolean isPair(String caseCode) {
    return caseCode != null && caseCode.startsWith(PAIR_PREFIX);
  }

  /** The algorithms the case is listed with, most used first: see {@link AlgorithmList}. */
  public static List<Listed> shown(String caseCode) {
    List<Listed> shown = new ArrayList<Listed>();
    if (isPair(caseCode)) {
      for (F2LCaseAlgorithms.Algorithm algorithm
          : F2LCaseAlgorithms.listed(pairCase(caseCode))) {
        shown.add(new Listed(algorithm.getMoves(), algorithm.isRecommended(),
            algorithm.getEmptySlot()));
      }
    } else {
      for (LastLayerCaseAlgorithms.Algorithm algorithm
          : LastLayerCaseAlgorithms.listed(caseCode)) {
        shown.add(new Listed(algorithm.getMoves(), algorithm.isRecommended(), null));
      }
    }
    return Collections.unmodifiableList(shown);
  }

  /**
   * How an execution is written where it is shown: the table's spelling where it is one of the
   * case's algorithms, and otherwise the execution itself, tidied. A mirror of a listed algorithm is
   * that algorithm when it comes to reading it, but is shown the way it was turned.
   */
  public static String asAlgorithm(String caseCode, String moves) {
    if (moves == null) {
      return null;
    }
    String matched = matchingMoves(caseCode, moves);
    if (matched != null) {
      if (sameTurning(caseCode, matched, moves)) {
        return matched;
      }
      String mirror = mirrorShown(caseCode, matched, moves);
      if (mirror != null) {
        return mirror;
      }
    }
    if (isPair(caseCode)) {
      String turned = F2LCaseAlgorithms.asTurned(pairCase(caseCode), moves);
      return turned == null ? moves : turned;
    }
    return LastLayerCaseAlgorithms.tidied(caseCode, moves);
  }

  /**
   * The listed algorithm's mirror, where that is what the moves turned: with a turn of the cube in
   * front where the mirror only solves the case stood another way, as an F2L pair's does.
   */
  private static String mirrorShown(String caseCode, String matched, String moves) {
    String mirror = AlgorithmForm.mirroredNotation(matched);
    for (String spin : new String[] {"", "y ", "y' ", "y2 "}) {
      String held = spin + mirror;
      if (solves(caseCode, held) && sameTurning(caseCode, held, moves)) {
        return held;
      }
    }
    return null;
  }

  private static String matchingMoves(String caseCode, String moves) {
    if (isPair(caseCode)) {
      F2LCaseAlgorithms.Algorithm matched = F2LCaseAlgorithms.matching(pairCase(caseCode), moves);
      return matched == null ? null : matched.getMoves();
    }
    LastLayerCaseAlgorithms.Algorithm matched = LastLayerCaseAlgorithms.matching(caseCode, moves);
    return matched == null ? null : matched.getMoves();
  }

  /** The slot ("fl", "br") that must be empty for the algorithm the moves are, or null for none. */
  public static String emptySlotOf(String caseCode, String moves) {
    if (!isPair(caseCode)) {
      return null;
    }
    F2LCaseAlgorithms.Algorithm matched = F2LCaseAlgorithms.matching(pairCase(caseCode), moves);
    // A mirror empties the slot across from the one its algorithm names.
    return matched == null || !sameTurning(caseCode, matched.getMoves(), moves) ? null
        : matched.getEmptySlot();
  }

  /** @see AlgorithmList#read */
  public static AlgorithmExecution read(String caseCode, String moves) {
    return isPair(caseCode) ? F2LCaseAlgorithms.read(pairCase(caseCode), moves)
        : LastLayerCaseAlgorithms.read(caseCode, moves);
  }

  /** Whether an execution is none of the algorithms its case is listed with: see {@link #read}. */
  public static boolean isWorthPointingOut(String caseCode, String moves) {
    return caseCode != null && moves != null && !moves.trim().isEmpty()
        && read(caseCode, moves).isUnusual();
  }

  /** @see LastLayerCaseAlgorithms#sameTurning */
  public static boolean sameTurning(String caseCode, String moves, String other) {
    return isPair(caseCode) ? F2LCaseAlgorithms.sameTurning(pairCase(caseCode), moves, other)
        : LastLayerCaseAlgorithms.sameTurning(caseCode, moves, other);
  }

  /** @see LastLayerCaseAlgorithms#keyAsDrawn */
  public static String keyAsDrawn(String caseCode, String moves) {
    return isPair(caseCode) ? F2LCaseAlgorithms.keyAsDrawn(pairCase(caseCode), moves)
        : LastLayerCaseAlgorithms.keyAsDrawn(caseCode, moves);
  }

  /** Whether an algorithm solves the case, as it must before one a user typed in is kept. */
  public static boolean solves(String caseCode, String algorithm) {
    return isPair(caseCode) ? F2LCaseAlgorithms.solves(pairCase(caseCode), algorithm)
        : LastLayerCaseAlgorithms.solves(caseCode, algorithm);
  }

  /** The case as {@link F2LCases} names it, {@code 27} for {@code pair_27}. */
  public static String pairCase(String caseCode) {
    return caseCode.substring(PAIR_PREFIX.length());
  }

  /** One algorithm as a screen lists it. */
  public static final class Listed {

    private final String moves;
    private final boolean recommended;
    private final String emptySlot;

    Listed(String moves, boolean recommended, String emptySlot) {
      this.moves = moves;
      this.recommended = recommended;
      this.emptySlot = emptySlot;
    }

    public String getMoves() {
      return moves;
    }

    /** The one to learn first, said only where the vote is not close. */
    public boolean isRecommended() {
      return recommended;
    }

    /** The slot ("fl", "br") that has to be empty for it, or null if none does. */
    public String getEmptySlot() {
      return emptySlot;
    }
  }
}
