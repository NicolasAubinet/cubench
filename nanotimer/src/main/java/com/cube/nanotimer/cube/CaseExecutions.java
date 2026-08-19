package com.cube.nanotimer.cube;

import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.smartcube.step.AlgorithmForm;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
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
    Map<String, List<String>> answers = new LinkedHashMap<String, List<String>>();
    for (SolveTime solve : solves) {
      collect(solve, answers);
    }
    Map<String, String> usual = new LinkedHashMap<String, String>();
    for (Map.Entry<String, List<String>> answer : answers.entrySet()) {
      usual.put(answer.getKey(), mostTurned(answer.getValue()));
    }
    return usual;
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

  /** The most turned of a case's answers, newest first, ties going to the more recent. */
  static String mostTurned(List<String> answers) {
    Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    Map<String, String> firstSeen = new LinkedHashMap<String, String>();
    for (String moves : answers) {
      String key = AlgorithmForm.key(moves);
      if (key == null) {
        continue;
      }
      Integer count = counts.get(key);
      counts.put(key, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
      if (!firstSeen.containsKey(key)) {
        firstSeen.put(key, moves); // the list is newest first, so this is the latest of its group
      }
    }
    String most = null;
    for (Map.Entry<String, Integer> count : counts.entrySet()) {
      if (most == null || count.getValue().intValue() > counts.get(most).intValue()) {
        most = count.getKey();
      }
    }
    return most == null ? answers.get(0) : firstSeen.get(most);
  }
}
