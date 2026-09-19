package com.cube.nanotimer.cube;

import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.smartcube.step.CFOPStepDetector;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The moves the solver actually turns for a case, read back out of their solves.
 *
 * <p>Nothing stores them. A solve keeps its moves and where its steps fell, so what was turned for
 * one case is a slice of one solve, cut the way {@link SolveSolution} cuts it for the breakdown. The
 * solves are read again from their scrambles rather than taken as stored, exactly as the solve
 * detail screen reads them, which is also what lets a solve recorded before an OLL was split by
 * algorithm say which algorithms it took.
 *
 * <p><b>The usual execution, not the last one.</b> A case answered five times gives five sets of
 * moves that are mostly the same algorithm with a different regrip, and one odd solve should not be
 * able to rename what the solver knows. So they are grouped by what they turn, the biggest group
 * wins, and the most recent of that group is what is shown, since a real execution spelled the way
 * it was turned is more use than an average nobody performed.
 *
 * <p><b>But a solver really can have two answers to one case</b>, picked by the angle it came up
 * at or by which hand is free, and calling one of them the algorithm they use would be wrong about
 * the other half of their solves. So the groups are kept rather than thrown away: {@link #spreadFrom}
 * hands back every distinct execution with how many of the answers it was, and it is up to the
 * screen whether it has room to say so. {@link #readFrom} is the same reading with only the winner.
 */
public final class CaseExecutions {

  /** How many of a case's answers are looked at. Enough to see which is the usual one. */
  private static final int PER_CASE = 5;

  /** A pair placed in a way no case is named for ({@code pair_other_rf}). */
  private static final String UNNAMED_PAIR = "other";

  private CaseExecutions() {
  }

  /**
   * @param solves the most recent solves, newest first, whatever they were solved as
   * @return the moves usually turned for each case there is an answer to, newest solve first
   */
  public static Map<String, String> readFrom(List<SolveTime> solves) {
    Map<String, String> usual = new LinkedHashMap<String, String>();
    for (Map.Entry<String, Spread> spread : spreadFrom(solves).entrySet()) {
      usual.put(spread.getKey(), spread.getValue().getTurned().get(0).getMoves());
    }
    return usual;
  }

  /**
   * @param solves the most recent solves, newest first, whatever they were solved as
   * @return every distinct execution of each last layer case there is an answer to, most turned
   *     first
   */
  public static Map<String, Spread> spreadFrom(List<SolveTime> solves) {
    return spreadsOf(solves, null);
  }

  /**
   * One case's executions, a last layer case or an F2L pair's ({@code pair_27}), or null where the
   * solves hold no answer to it. Only that case is read: an F2L pair is four to a solve, and each
   * of them is stood up 24 ways to be grouped.
   *
   * @param solves the most recent solves, newest first, whatever they were solved as
   */
  public static Spread spreadFrom(List<SolveTime> solves, String caseCode) {
    return spreadsOf(solves, caseCode).get(caseCode);
  }

  /** @param only the one case to read, or null for every last layer case */
  private static Map<String, Spread> spreadsOf(List<SolveTime> solves, String only) {
    Map<String, List<String>> answers = new LinkedHashMap<String, List<String>>();
    for (SolveTime solve : solves) {
      collect(solve, answers, only);
    }
    Map<String, Spread> spreads = new LinkedHashMap<String, Spread>();
    for (Map.Entry<String, List<String>> answer : answers.entrySet()) {
      spreads.put(answer.getKey(), spreadOf(answer.getKey(), answer.getValue()));
    }
    return spreads;
  }

  /**
   * What one case is recorded under, for asking the database which solves it came up in. For a
   * last layer case, two codes: the step that was handed the case, and the part naming the
   * algorithm that answers it, since a step taking more than one algorithm records all but the
   * first under the part alone. For an F2L pair's, the pair in every slot it can go into.
   */
  public static List<String> codesFor(String caseCode) {
    List<String> codes = new ArrayList<String>();
    if (caseCode == null) {
      return codes;
    }
    if (CaseAlgorithms.isPair(caseCode)) {
      return CFOPStepDetector.pairCodes(CaseAlgorithms.pairCase(caseCode));
    }
    codes.add(caseCode);
    String part = MethodStatistics.partOfCase(caseCode);
    if (part != null) {
      codes.add(part);
    }
    return codes;
  }

  /**
   * How an execution is written where it is shown: the table's spelling where the two really are
   * the same algorithm, since that is the one written the way the case is drawn, and the execution
   * tidied into that same form where they are not. Calling an execution the nearest listed
   * algorithm would be putting words in the solver's hands.
   *
   * <p><b>Tidied, not replaced.</b> An execution comes back with the turn that squared the case up
   * on the front, whatever the last algorithm left over on the back, and a regrip turned and turned
   * again in the middle, so shown raw it reads as longer and stranger than what the solver did.
   * {@link CaseAlgorithms#asAlgorithm} takes those off without touching which pieces moved, so an
   * algorithm of their own still comes back theirs.
   */
  public static String asAlgorithm(String caseCode, String moves) {
    return CaseAlgorithms.asAlgorithm(caseCode, moves);
  }

  /**
   * A case's executions as a screen shows them: each named the way it will be written, any two that
   * come out written alike added together, most turned first, and the ones turned only once left out.
   *
   * <p><b>A case answered once is not an algorithm the solver uses.</b> Misread a case, wreck the
   * cube putting it right, and the moves that got there are recorded against the case all the same:
   * they really did solve it, so nothing in the moves themselves says they were a scramble and a
   * rebuild. What says so is that they happened once while the real answer happened more often,
   * which is a thing counting can see and no reading of a move sequence can.
   *
   * <p>The one turned most is kept whatever its count, so a case with any answer at all still has
   * one. The cost of the rest is deliberate and small: an algorithm just learnt waits for the second
   * time the case comes up, and a solver with two real answers sees only the commoner one until the
   * other repeats. One more solve fixes both. Being told they use something they turned once by
   * accident is not fixed by anything.
   *
   * <p>{@link Spread#getOf} is left alone. "2 of your last 5" is what those five answers were,
   * whether or not the other three earned a row.
   */
  public static List<Shown> shownFrom(String caseCode, Spread spread) {
    List<Shown> shown = new ArrayList<Shown>();
    if (spread == null) {
      return shown;
    }
    for (Turned one : spread.getTurned()) {
      String moves = asAlgorithm(caseCode, one.getMoves());
      // Two executions written the same way are one row, or the counts on the rows drawn would not
      // add up to the answers they were counted out of.
      Shown already = writtenAs(shown, moves);
      if (already == null) {
        shown.add(new Shown(moves, one.getMoves(), one.getTimes(), spread.getOf()));
      } else {
        already.times += one.getTimes();
      }
    }
    // Adding two together can carry a row past the one in front of it, and the row in front is the
    // one the words go on.
    Collections.sort(shown, new Comparator<Shown>() {
      @Override
      public int compare(Shown one, Shown other) {
        return other.times - one.times;
      }
    });
    List<Shown> kept = new ArrayList<Shown>();
    for (Shown one : shown) {
      if (kept.isEmpty() || one.times > 1) {
        kept.add(one);
      }
    }
    return kept;
  }

  private static Shown writtenAs(List<Shown> shown, String moves) {
    for (Shown one : shown) {
      if (one.moves.equals(moves)) {
        return one;
      }
    }
    return null;
  }

  /** One solve's cases, or none of them where it cannot be read again. */
  private static void collect(SolveTime solve, final Map<String, List<String>> answers,
      final String only) {
    readSteps(solve, new StepReader() {
      @Override
      public void read(SolveStep step, SolveSolution.Step turned) {
        add(step, turned, answers, only);
      }
    });
  }

  /** Each step of a CFOP solve read again from its scramble, with its moves; none otherwise. */
  static void readSteps(SolveTime solve, StepReader reader) {
    CubeMethod method = SolveTypeMethod.of(solve.getSolveType());
    if (method != CubeMethod.CFOP) {
      return; // only CFOP is solved in these cases: nothing else would be read, it would be guessed
    }
    StoredSolveReplay.Result reread = StoredSolveReplay.reinterpret(solve.getScramble(),
        solve.getSmartcubeMoves(), method);
    if (reread == null) {
      return;
    }
    List<SolveStep> steps = SolveBreakdown.withTail(reread.getSteps(), reread.getStoppedStep(),
        SolveBreakdown.solvingDurationMs(solve), reread.getMoves(), reread.getMethod());
    SolveSolution solution = SolveSolution.from(reread.getMoves(), steps);
    for (int i = 0; i < steps.size() && i < solution.getSteps().size(); i++) {
      if (reread.getStoppedStep() != null
          && reread.getStoppedStep().intValue() == steps.get(i).getStepIndex()) {
        continue; // the solve stopped inside it, so its moves are half of an answer
      }
      reader.read(steps.get(i), solution.getSteps().get(i));
    }
  }

  /** One step of a solve, as {@link #readSteps} hands it over. */
  interface StepReader {
    void read(SolveStep step, SolveSolution.Step turned);
  }

  private static void add(SolveStep step, SolveSolution.Step turned,
      Map<String, List<String>> answers, String only) {
    List<SolveStep> parts = step.getSubSteps();
    if (parts.isEmpty()) {
      // Nothing was recorded under it, which is how a step that took one algorithm is stored.
      keep(answers, caseOfStep(step.getName()), turned.getPartMoves(0), only);
      return;
    }
    for (int i = 0; i < parts.size() && i < turned.getGroups().size(); i++) {
      keep(answers, caseOfPart(parts.get(i).getName()), turned.getPartMoves(i), only);
    }
  }

  /**
   * The case a part answers, or null: a last layer algorithm's ({@code oll_45}), or the one an F2L
   * pair was handed ({@code pair_27}), which a pair placed no named way or skipped has not.
   */
  public static String caseOfPart(String name) {
    String lastLayer = MethodStatistics.caseOfPart(name);
    if (lastLayer != null || !CaseAlgorithms.isPair(name)) {
      return lastLayer;
    }
    String pair = MethodStatistics.caseCodeOf(name); // null for a pair stored with its slot alone
    String caseName = pair == null ? null : CaseAlgorithms.pairCase(pair);
    return caseName == null || UNNAMED_PAIR.equals(caseName)
        || MethodStatistics.SKIP.equals(caseName) ? null : pair;
  }

  /** The case a last layer step was handed, or null for any other step. */
  public static String caseOfStep(String name) {
    String caseName = MethodStatistics.caseOf(name);
    if (caseName == null || MethodStatistics.SKIP.equals(caseName)) {
      return null;
    }
    String family = MethodStatistics.familyOf(name);
    return "oll".equals(family) || "pll".equals(family) ? name : null;
  }

  private static void keep(Map<String, List<String>> answers, String caseCode, String moves,
      String only) {
    if (caseCode == null || moves == null || moves.trim().isEmpty()) {
      return;
    }
    if (only == null ? CaseAlgorithms.isPair(caseCode) : !only.equals(caseCode)) {
      return;
    }
    List<String> kept = answers.get(caseCode);
    if (kept == null) {
      kept = new ArrayList<String>();
      answers.put(caseCode, kept);
    }
    if (kept.size() < PER_CASE) {
      kept.add(moves);
    }
  }

  /**
   * A case's answers grouped by what they turn, most turned first, ties going to the more recent.
   * Each group is spelled as the most recent of its own answers, since a real execution written the
   * way it was turned is more use than an average nobody performed.
   */
  static Spread spreadOf(String caseCode, List<String> answers) {
    Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    Map<String, String> firstSeen = new LinkedHashMap<String, String>();
    for (String moves : answers) {
      // From the frame the case is drawn in, so the same algorithm regripped between solves and
      // left facing another way is one answer rather than two or three.
      String key = CaseAlgorithms.keyAsDrawn(caseCode, moves);
      if (key == null) {
        continue; // notation nothing can read groups with nothing, not with everything
      }
      Integer count = counts.get(key);
      counts.put(key, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
      if (!firstSeen.containsKey(key)) {
        firstSeen.put(key, moves); // the list is newest first, so this is the latest of its group
      }
    }
    if (counts.isEmpty()) {
      return new Spread(Collections.singletonList(new Turned(answers.get(0), 1)), 1);
    }
    List<Turned> turned = new ArrayList<Turned>();
    int of = 0;
    for (Map.Entry<String, Integer> count : counts.entrySet()) {
      turned.add(new Turned(firstSeen.get(count.getKey()), count.getValue().intValue()));
      of += count.getValue().intValue();
    }
    Collections.sort(turned, new Comparator<Turned>() {
      @Override
      public int compare(Turned one, Turned other) {
        return other.times - one.times;
      }
    });
    return new Spread(Collections.unmodifiableList(turned), of);
  }

  /** One thing the solver turns for a case, and how many of the answers looked at it was. */
  public static final class Turned {

    private final String moves;
    private final int times;

    Turned(String moves, int times) {
      this.moves = moves;
      this.times = times;
    }

    public String getMoves() {
      return moves;
    }

    public int getTimes() {
      return times;
    }
  }

  /**
   * One answer a screen may show: how it is written there, the moves it was read from, and how many
   * of the case's answers it was.
   */
  public static final class Shown {

    private final String moves;
    private final String executed;
    private int times;
    private final int of;

    Shown(String moves, String executed, int times, int of) {
      this.moves = moves;
      this.executed = executed;
      this.times = times;
      this.of = of;
    }

    /** The way it is written where it is shown, which is the table's spelling where it has one. */
    public String getMoves() {
      return moves;
    }

    /** The moves as the solver turned them, which is what anything reading the execution wants. */
    public String getExecuted() {
      return executed;
    }

    public int getTimes() {
      return times;
    }

    /** How many of the case's answers these were counted out of, the ones left out included. */
    public int getOf() {
      return of;
    }
  }

  /** Everything the solver turns for one case, most turned first. */
  public static final class Spread {

    private final List<Turned> turned;
    private final int of;

    Spread(List<Turned> turned, int of) {
      this.turned = turned;
      this.of = of;
    }

    /** Never empty: a case with no answer at all has no spread rather than an empty one. */
    public List<Turned> getTurned() {
      return turned;
    }

    /** How many of the case's answers these were counted out of, which is at most five. */
    public int getOf() {
      return of;
    }
  }
}
