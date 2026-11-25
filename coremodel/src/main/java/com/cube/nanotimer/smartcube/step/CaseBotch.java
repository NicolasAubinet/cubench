package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a last layer case the solve stopped inside was botched: the algorithm went wrong in the
 * solver's hands, rather than the cube popping or the solver giving up before starting.
 *
 * <p><b>Read from the moves turned in the step, once undone moves are folded away</b>
 * ({@code U R L2 L2 R' U'} is nothing), against every algorithm the case is listed with and the
 * solver's own executions of it, from any grip:
 * <ul>
 *   <li>fewer than {@link #MIN_TURNS} turns left: nothing was really tried, no verdict;</li>
 *   <li>the start of one of them, or all of it: a clean run cut short, which is a pop or a lock,
 *       not a slip;</li>
 *   <li>the start of a first look ({@link #firstLooks}): on a case the solver has not learnt, a
 *       two-look solve stopped before the look was done, which is just as clean; on one learnt,
 *       a fall back to two looks;</li>
 *   <li>all of another case's algorithm, or a long start of one ({@link #MISREAD_TURNS}): the
 *       case was read as that one;</li>
 *   <li>anything else: botched.</li>
 * </ul>
 *
 * <p>Every grip is tried because a partial execution cannot say which face its layer is on, so this
 * leans towards calling a short attempt clean. That is the safe side: a botch missed costs one
 * signal, a botch made up puts a case in front of the solver for no reason. No mirror is tried,
 * since a mirrored Aa is an Ab: a left-handed habit is in the solver's own executions instead.
 */
public final class CaseBotch {

  /** Below this many turns, alignment off both ends, the solver had not started an algorithm. */
  public static final int MIN_TURNS = 3;

  /** How much of another case's algorithm reads as having taken the case for that one. */
  public static final int MISREAD_TURNS = 6;

  /** Two-look OLL's first look: the edges, a line, an L or the dot (both). */
  private static final String[] EDGE_ORIENTATIONS = {
    "F R U R' U' F'", "f R U R' U' f'", "F U R U' R' F'", "F R U R' U' F' f R U R' U' f'"
  };

  /** The corner orientation cases, whose algorithms are two-look CMLL's first look. */
  private static final String[] CORNER_ORIENTATIONS = {
    "oll_21", "oll_22", "oll_23", "oll_24", "oll_25", "oll_26", "oll_27"
  };

  /** The PLLs two-look PLL's first look permutes the corners with. */
  private static final String[] CORNER_PERMUTATIONS = {
    "pll_t", "pll_jb", "pll_ja", "pll_y", "pll_aa", "pll_ab", "pll_v"
  };

  public enum Verdict {
    /** Too little turned, or a clean start of one of the case's algorithms. */
    CLEAN,
    /** A first look on a case the solver has learnt: one algorithm was not recalled. */
    TWO_LOOKED,
    /** Another case's algorithm: the case was misread. */
    MISREAD,
    /** None of the case's algorithms, nor a start of one. */
    BOTCHED;

    /** Stored under this, never under the enum's own name. */
    public String code() {
      return name().toLowerCase(java.util.Locale.US);
    }
  }

  private final Verdict verdict;
  private final String misreadAs;

  private CaseBotch(Verdict verdict, String misreadAs) {
    this.verdict = verdict;
    this.misreadAs = misreadAs;
  }

  public Verdict getVerdict() {
    return verdict;
  }

  /** The case the solver's moves belong to, for a {@link Verdict#MISREAD}; null otherwise. */
  public String getMisreadAs() {
    return misreadAs;
  }

  /** Whether the case goes down as gone wrong: botched, misread or two-looked. */
  public boolean counts() {
    return verdict != Verdict.CLEAN;
  }

  /**
   * @param caseCode the case the step was handed ({@code oll_45}, {@code pll_jb}, {@code cmll_h_1})
   * @param moves what was turned in the step until the solve stopped, in the solver's frame
   * @param own the solver's own executions of the case, which count as known algorithms
   * @param learned whether the solver has done the case in one look (known, or needing review)
   */
  public static CaseBotch judge(String caseCode, String moves, List<String> own,
      boolean learned) {
    List<List<String>> executed = new ArrayList<List<String>>();
    try {
      List<String> turns = AlgorithmForm.of(moves);
      int shortest = Integer.MAX_VALUE;
      for (char[] grip : AlgorithmForm.grips()) {
        List<String> stood = AlgorithmForm.withoutAlignment(AlgorithmForm.conjugatedBy(turns, grip));
        shortest = Math.min(shortest, stood.size());
        executed.add(stood);
      }
      if (shortest < MIN_TURNS) {
        return new CaseBotch(Verdict.CLEAN, null);
      }
    } catch (RuntimeException e) {
      return new CaseBotch(Verdict.CLEAN, null); // notation nothing can read says nothing
    }

    List<String> known = new ArrayList<String>(own == null ? new ArrayList<String>() : own);
    for (LastLayerCaseAlgorithms.Algorithm algorithm : LastLayerCaseAlgorithms.folded(caseCode)) {
      known.add(algorithm.getMoves());
    }
    if (startsAny(executed, formsOf(known), 0)) {
      return new CaseBotch(Verdict.CLEAN, null);
    }
    if (startsAny(executed, formsOf(firstLooks(caseCode)), 0)) {
      return new CaseBotch(learned ? Verdict.TWO_LOOKED : Verdict.CLEAN, null);
    }
    for (String other : LastLayerCaseAlgorithms.casesLike(caseCode)) {
      if (other.equals(caseCode)) {
        continue;
      }
      List<String> theirs = new ArrayList<String>();
      for (LastLayerCaseAlgorithms.Algorithm algorithm : LastLayerCaseAlgorithms.folded(other)) {
        theirs.add(algorithm.getMoves());
      }
      if (startsAny(executed, formsOf(theirs), MISREAD_TURNS)) {
        return new CaseBotch(Verdict.MISREAD, other);
      }
    }
    return new CaseBotch(Verdict.BOTCHED, null);
  }

  /**
   * What a two-look solver turns first on the case: the edges for an OLL, the corners for a PLL
   * and the corners' orientation for a CMLL.
   */
  static List<String> firstLooks(String caseCode) {
    List<String> looks = new ArrayList<String>();
    if (caseCode.startsWith("oll_")) {
      for (String look : EDGE_ORIENTATIONS) {
        looks.add(look);
      }
    } else if (caseCode.startsWith("pll_")) {
      addAll(looks, CORNER_PERMUTATIONS);
    } else if (caseCode.startsWith(CmllCases.PREFIX)) {
      addAll(looks, CORNER_ORIENTATIONS);
    }
    return looks;
  }

  private static void addAll(List<String> looks, String[] cases) {
    for (String look : cases) {
      for (LastLayerCaseAlgorithms.Algorithm algorithm : LastLayerCaseAlgorithms.folded(look)) {
        looks.add(algorithm.getMoves());
      }
    }
  }

  private static List<List<String>> formsOf(List<String> algorithms) {
    List<List<String>> forms = new ArrayList<List<String>>();
    for (String algorithm : algorithms) {
      List<String> form = AlgorithmForm.comparable(algorithm);
      if (!form.isEmpty()) {
        forms.add(form);
      }
    }
    return forms;
  }

  /**
   * Whether any grip of the execution is one of the forms, or the start of one.
   *
   * @param minStart how long a start has to be to count; a whole form always counts
   */
  private static boolean startsAny(List<List<String>> executed, List<List<String>> forms,
      int minStart) {
    for (List<String> stood : executed) {
      for (List<String> form : forms) {
        if (stood.equals(form) || (stood.size() >= minStart && isStartOf(stood, form))) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Whether the turns open the form. The last may be part of its turn ({@code R} of an {@code R2}),
   * or the second of a pair turning out of each other's way, which folding writes in one order.
   */
  static boolean isStartOf(List<String> turns, List<String> form) {
    int last = turns.size() - 1;
    if (turns.isEmpty() || last >= form.size()) {
      return false;
    }
    for (int i = 0; i < last; i++) {
      if (!turns.get(i).equals(form.get(i))) {
        return false;
      }
    }
    char face = turns.get(last).charAt(0);
    if (face == form.get(last).charAt(0)) {
      return true;
    }
    return last + 1 < form.size() && face == form.get(last + 1).charAt(0)
        && across(form.get(last).charAt(0), face);
  }

  private static boolean across(char face, char other) {
    String pairs = "RLUDFB";
    int a = pairs.indexOf(face);
    int b = pairs.indexOf(other);
    return a >= 0 && b >= 0 && a / 2 == b / 2 && a != b;
  }
}
