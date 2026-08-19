package com.cube.nanotimer.cube;

import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.smartcube.step.AlgorithmForm;
import com.cube.nanotimer.smartcube.step.LastLayerCaseAlgorithms;
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
   * @return every distinct execution of each case there is an answer to, most turned first
   */
  public static Map<String, Spread> spreadFrom(List<SolveTime> solves) {
    Map<String, List<String>> answers = new LinkedHashMap<String, List<String>>();
    for (SolveTime solve : solves) {
      collect(solve, answers);
    }
    Map<String, Spread> spreads = new LinkedHashMap<String, Spread>();
    for (Map.Entry<String, List<String>> answer : answers.entrySet()) {
      spreads.put(answer.getKey(), spreadOf(answer.getValue()));
    }
    return spreads;
  }

  /**
   * What one case is recorded under, for asking the database which solves it came up in. Two codes:
   * the step that was handed the case, and the part naming the algorithm that answers it, since a
   * step taking more than one algorithm records all but the first under the part alone.
   */
  public static List<String> codesFor(String caseCode) {
    List<String> codes = new ArrayList<String>();
    if (caseCode == null) {
      return codes;
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
   * the same algorithm, since that is the one written the way the case is drawn, and the moves as
   * they were turned where they are not. Calling an execution the nearest listed algorithm would be
   * putting words in the solver's hands.
   */
  public static String asAlgorithm(String caseCode, String moves) {
    if (moves == null) {
      return null;
    }
    LastLayerCaseAlgorithms.Algorithm matched = LastLayerCaseAlgorithms.matching(caseCode, moves);
    return matched == null ? moves : matched.getMoves();
  }

  /** One solve's cases, or none of them where it cannot be read again. */
  private static void collect(SolveTime solve, Map<String, List<String>> answers) {
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
        SolveBreakdown.solvingDurationMs(solve), solve.getSmartcubeMoves(), reread.getMethod());
    SolveSolution solution = SolveSolution.from(solve.getSmartcubeMoves(), steps);
    for (int i = 0; i < steps.size() && i < solution.getSteps().size(); i++) {
      if (reread.getStoppedStep() != null && reread.getStoppedStep().intValue() == i) {
        continue; // the solve stopped inside it, so its moves are half of an answer
      }
      add(steps.get(i), solution.getSteps().get(i), answers);
    }
  }

  private static void add(SolveStep step, SolveSolution.Step turned,
      Map<String, List<String>> answers) {
    List<SolveStep> parts = step.getSubSteps();
    if (parts.isEmpty()) {
      // Nothing was recorded under it, which is how a step that took one algorithm is stored.
      keep(answers, caseOfStep(step.getName()), turned.getPartMoves(0));
      return;
    }
    for (int i = 0; i < parts.size() && i < turned.getGroups().size(); i++) {
      keep(answers, MethodStatistics.caseOfPart(parts.get(i).getName()), turned.getPartMoves(i));
    }
  }

  /** The case a last layer step was handed, or null for any other step. */
  private static String caseOfStep(String name) {
    String caseName = MethodStatistics.caseOf(name);
    if (caseName == null || MethodStatistics.SKIP.equals(caseName)) {
      return null;
    }
    String family = MethodStatistics.familyOf(name);
    return "oll".equals(family) || "pll".equals(family) ? name : null;
  }

  private static void keep(Map<String, List<String>> answers, String caseCode, String moves) {
    if (caseCode == null || moves == null || moves.trim().isEmpty()) {
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
  static Spread spreadOf(List<String> answers) {
    Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    Map<String, String> firstSeen = new LinkedHashMap<String, String>();
    for (String moves : answers) {
      String key = AlgorithmForm.key(moves);
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
