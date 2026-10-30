package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.step.F2lPairCases;
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
 *
 * <p>A pair dealt into a back slot to be put in with no rotation ({@code pair_27_br}) is a case of
 * its own here, with {@link F2LBackSlotAlgorithms} for its table: its algorithms are not the front
 * one's turned, and the front one turned is exactly what it is there to replace.
 */
public final class CaseAlgorithms {

  private static final String PAIR_PREFIX = "pair_";

  private CaseAlgorithms() {
  }

  /** How many F2L cases the basic set holds, the ones with neither piece in another slot. */
  public static final int BASIC_PAIR_CASES = F2LCases.BASIC;

  /** Whether the code names an F2L pair's case rather than a last layer one. */
  public static boolean isPair(String caseCode) {
    return caseCode != null && caseCode.startsWith(PAIR_PREFIX);
  }

  /** Whether the code names one of the {@link #BASIC_PAIR_CASES} F2L cases ({@code pair_27}). */
  public static boolean isBasicPair(String caseCode) {
    return isPair(caseCode) && F2LCases.isBasic(pairCase(caseCode));
  }

  /** The back slot a pair is dealt into to be put in without a rotation, or null for none. */
  public static String backSlotOf(String caseCode) {
    return F2lPairCases.backSlotOf(caseCode);
  }

  /** The pair's case dealt into a back slot, {@code pair_27_br} for {@code pair_27}. */
  public static String inBackSlot(String pairCode, String slot) {
    return F2lPairCases.inBackSlot(pairCode, slot);
  }

  /** The case a back-slot code deals, {@code pair_27} for {@code pair_27_br}; others unchanged. */
  public static String frontCode(String caseCode) {
    String slot = backSlotOf(caseCode);
    return slot == null ? caseCode
        : caseCode.substring(0, caseCode.length() - slot.length() - 1);
  }

  /** The algorithms the case is listed with, most used first: see {@link AlgorithmList}. */
  public static List<Listed> shown(String caseCode) {
    List<Listed> shown = new ArrayList<Listed>();
    String backSlot = backSlotOf(caseCode);
    if (backSlot != null) {
      for (String moves : F2LBackSlotAlgorithms.forCase(pairCase(caseCode), backSlot)) {
        shown.add(new Listed(moves, shown.isEmpty(), null));
      }
    } else if (isPair(caseCode)) {
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
    if (backSlotOf(caseCode) != null) {
      for (Listed listed : shown(caseCode)) {
        if (sameTurning(caseCode, listed.getMoves(), moves)) {
          return listed.getMoves();
        }
      }
      return moves;
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
    if (!isPair(caseCode) || backSlotOf(caseCode) != null) {
      return null;
    }
    F2LCaseAlgorithms.Algorithm matched = F2LCaseAlgorithms.matching(pairCase(caseCode), moves);
    // A mirror empties the slot across from the one its algorithm names.
    return matched == null || !sameTurning(caseCode, matched.getMoves(), moves) ? null
        : matched.getEmptySlot();
  }

  /** The shortest algorithm the case is listed with, the one extra moves are counted over. */
  public static String shortestListed(String caseCode) {
    if (backSlotOf(caseCode) != null) {
      List<Listed> listed = shown(caseCode);
      return listed.isEmpty() ? null : listed.get(0).getMoves();
    }
    return isPair(caseCode) ? F2LCaseAlgorithms.shortestListed(pairCase(caseCode))
        : LastLayerCaseAlgorithms.shortestListed(caseCode);
  }

  /** @see AlgorithmList#read */
  public static AlgorithmExecution read(String caseCode, String moves) {
    if (backSlotOf(caseCode) != null) {
      return new AlgorithmExecution(false, 0, 0); // a rotation is what this drill reads, not length
    }
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
    if (backSlotOf(caseCode) != null) {
      return F2LBackSlotAlgorithms.sameTurning(moves, other);
    }
    return isPair(caseCode) ? F2LCaseAlgorithms.sameTurning(pairCase(caseCode), moves, other)
        : LastLayerCaseAlgorithms.sameTurning(caseCode, moves, other);
  }

  /** @see LastLayerCaseAlgorithms#keyAsDrawn */
  public static String keyAsDrawn(String caseCode, String moves) {
    if (backSlotOf(caseCode) != null) {
      List<String> turned = AlgorithmForm.comparable(moves);
      return turned.isEmpty() ? null : AlgorithmForm.written(turned);
    }
    return isPair(caseCode) ? F2LCaseAlgorithms.keyAsDrawn(pairCase(caseCode), moves)
        : LastLayerCaseAlgorithms.keyAsDrawn(caseCode, moves);
  }

  /** Whether an algorithm solves the case, as it must before one a user typed in is kept. */
  public static boolean solves(String caseCode, String algorithm) {
    String backSlot = backSlotOf(caseCode);
    if (backSlot != null) {
      return F2LBackSlotAlgorithms.solves(pairCase(caseCode), backSlot, algorithm);
    }
    return isPair(caseCode) ? F2LCaseAlgorithms.solves(pairCase(caseCode), algorithm)
        : LastLayerCaseAlgorithms.solves(caseCode, algorithm);
  }

  /** The case as {@link F2LCases} names it, {@code 27} for {@code pair_27} and {@code pair_27_br}. */
  public static String pairCase(String caseCode) {
    return frontCode(caseCode).substring(PAIR_PREFIX.length());
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
