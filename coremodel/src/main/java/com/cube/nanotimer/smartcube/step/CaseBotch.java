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
 *   <li>a whole first look ({@link #firstLooks}) and at least {@link #MIN_TURNS} turns of a
 *       second: on a case the solver has learnt, a fall back to two looks; on one not learnt, a
 *       two-look solve as planned, which is clean. Not a look one of the case's own algorithms
 *       opens with ({@code oll_3}): going wrong after it is a slip in that algorithm;</li>
 *   <li>a first look alone, with less of a second, or the start of one: clean, since nothing says
 *       the solver was taking two looks rather than stopping;</li>
 *   <li>all of another case's algorithm, a long start of one ({@link #MISREAD_TURNS}), or all of
 *       it then more (a repair, as a finished solve has it) where none of the case's own opens
 *       with it: the case was read as that one;</li>
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

  /** The PLLs a two-look solver takes in two: neither a corner algorithm nor an edge one. */
  private static final String[] TWO_LOOK_PLLS = {
    "pll_e", "pll_f", "pll_ga", "pll_gb", "pll_gc", "pll_gd", "pll_ra", "pll_rb", "pll_na", "pll_nb"
  };

  public enum Verdict {
    /** Too little turned, or a clean start of one of the case's algorithms. */
    CLEAN,
    /** A whole first look then a second, on a case the solver has learnt: one was not recalled. */
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
    List<List<String>> executed = gripsOf(moves);
    if (executed == null) {
      return new CaseBotch(Verdict.CLEAN, null);
    }

    List<String> known = new ArrayList<String>(own == null ? new ArrayList<String>() : own);
    for (LastLayerCaseAlgorithms.Algorithm algorithm : LastLayerCaseAlgorithms.folded(caseCode)) {
      known.add(algorithm.getMoves());
    }
    List<List<String>> knownForms = formsOf(known);
    if (startsAny(executed, knownForms, 0)) {
      return new CaseBotch(Verdict.CLEAN, null);
    }
    List<List<String>> looks = formsOf(firstLooks(caseCode));
    int second = afterFirstLook(executed, notOpening(looks, knownForms));
    if (second >= MIN_TURNS) {
      return new CaseBotch(learned ? Verdict.TWO_LOOKED : Verdict.CLEAN, null);
    }
    if (second >= 0 || startsAny(executed, looks, 0)) {
      return new CaseBotch(Verdict.CLEAN, null);
    }
    for (String other : LastLayerCaseAlgorithms.casesLike(caseCode)) {
      if (other.equals(caseCode)) {
        continue;
      }
      List<String> theirs = new ArrayList<String>();
      for (LastLayerCaseAlgorithms.Algorithm algorithm : LastLayerCaseAlgorithms.folded(other)) {
        theirs.add(algorithm.getMoves());
      }
      List<List<String>> theirForms = formsOf(theirs);
      // all of theirs and then a repair, as a finished solve has it; not one opening ours
      if (startsAny(executed, theirForms, MISREAD_TURNS)
          || afterFirstLook(executed, notOpening(theirForms, knownForms)) >= 0) {
        return new CaseBotch(Verdict.MISREAD, other);
      }
    }
    return new CaseBotch(Verdict.BOTCHED, null);
  }

  /** Whether the moves are all of one of the case's first looks, from any grip. */
  public static boolean isFirstLook(String caseCode, String moves) {
    List<List<String>> executed = gripsOf(moves);
    if (executed == null) {
      return false;
    }
    List<List<String>> looks = formsOf(firstLooks(caseCode));
    for (List<String> stood : executed) {
      if (looks.contains(stood)) {
        return true;
      }
    }
    return false;
  }

  /** The moves in every grip, also as held after a regrip; null when too short or unreadable. */
  private static List<List<String>> gripsOf(String moves) {
    List<List<String>> executed = new ArrayList<List<String>>();
    try {
      if (addGrips(executed, AlgorithmForm.of(moves)) < MIN_TURNS) {
        return null;
      }
      // a regrip the solver did not take back renames the letters after it, as tidied() reads them
      addGrips(executed, AlgorithmForm.asHeld(AlgorithmForm.withoutOpeningGrip(moves)));
    } catch (RuntimeException e) {
      return null; // notation nothing can read says nothing
    }
    return executed;
  }

  /** How many turns the moves come to, undone ones folded away and alignment off both ends. */
  public static int turnCount(String moves) {
    return AlgorithmForm.withoutAlignment(AlgorithmForm.of(moves)).size();
  }

  /** Adds the turns as stood in every grip, alignment off both ends; returns the shortest. */
  private static int addGrips(List<List<String>> executed, List<String> turns) {
    int shortest = Integer.MAX_VALUE;
    for (char[] grip : AlgorithmForm.grips()) {
      List<String> stood = AlgorithmForm.withoutAlignment(AlgorithmForm.conjugatedBy(turns, grip));
      shortest = Math.min(shortest, stood.size());
      executed.add(stood);
    }
    return shortest;
  }

  /** A two-look solver's first look on the case; none for a case not taken in two looks. */
  static List<String> firstLooks(String caseCode) {
    List<String> looks = new ArrayList<String>();
    if (caseCode.startsWith("oll_") && !contains(CORNER_ORIENTATIONS, caseCode)) {
      for (String look : EDGE_ORIENTATIONS) {
        looks.add(look);
      }
    } else if (contains(TWO_LOOK_PLLS, caseCode)) {
      addAll(looks, CORNER_PERMUTATIONS);
    } else if (caseCode.startsWith(CmllCases.PREFIX)
        && !caseCode.startsWith(CmllCases.PREFIX + "o")) {
      addAll(looks, CORNER_ORIENTATIONS);
    }
    return looks;
  }

  private static boolean contains(String[] codes, String code) {
    for (String one : codes) {
      if (one.equals(code)) {
        return true;
      }
    }
    return false;
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

  /** The forms none of the case's own algorithms opens with, in any grip: after one, a slip. */
  private static List<List<String>> notOpening(List<List<String>> looks,
      List<List<String>> known) {
    List<List<String>> opened = new ArrayList<List<String>>();
    for (List<String> form : known) {
      for (char[] grip : AlgorithmForm.grips()) {
        opened.add(AlgorithmForm.withoutAlignment(AlgorithmForm.conjugatedBy(form, grip)));
      }
    }
    List<List<String>> kept = new ArrayList<List<String>>();
    for (List<String> look : looks) {
      boolean opens = false;
      for (List<String> form : opened) {
        opens |= form.size() > look.size() && form.subList(0, look.size()).equals(look);
      }
      if (!opens) {
        kept.add(look);
      }
    }
    return kept;
  }

  /** The most turns after a whole one of the forms, alignment off, or -1 where none opens it. */
  private static int afterFirstLook(List<List<String>> executed, List<List<String>> forms) {
    int most = -1;
    for (List<String> stood : executed) {
      for (List<String> form : forms) {
        if (stood.size() > form.size() && stood.subList(0, form.size()).equals(form)) {
          most = Math.max(most,
              AlgorithmForm.withoutAlignment(stood.subList(form.size(), stood.size())).size());
        }
      }
    }
    return most;
  }

  /** Whether a grip of the execution is one of the forms, or a start of at least minStart turns. */
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

  /** Whether the turns open the form; the last may be half a double turn or swapped with its opposite. */
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
    String turn = turns.get(last);
    if (partOf(turn, form.get(last))) {
      return true;
    }
    return last + 1 < form.size() && partOf(turn, form.get(last + 1))
        && across(form.get(last).charAt(0), turn.charAt(0));
  }

  private static boolean partOf(String turn, String of) {
    return turn.equals(of) || (of.endsWith("2") && turn.charAt(0) == of.charAt(0));
  }

  private static boolean across(char face, char other) {
    String pairs = "RLUDFB";
    int a = pairs.indexOf(face);
    int b = pairs.indexOf(other);
    return a >= 0 && b >= 0 && a / 2 == b / 2 && a != b;
  }
}
