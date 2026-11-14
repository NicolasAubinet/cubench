package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The algorithm a solver uses for each case, as one vote per case for the shared usage counts.
 *
 * <p>Read the way the case popup reads "the one you use" ({@link CaseExecutions}): the case's five
 * latest answers grouped by what they turn, the top group spelled as the popup spells it, so two
 * solvers' spellings of one algorithm land on one row. Two things are stricter than the popup:
 * <ul>
 *   <li><b>Turned at least twice.</b> The popup shows its top answer whatever its count, so a case
 *   solved once still has a row; a vote from one solve would be a fumble as often as a habit.
 *   <li><b>One look only.</b> A last layer step counts only when it took one algorithm, under the
 *   case it was handed. The popup also reads the second algorithm of a two-look ({@code ollalg_27})
 *   as an answer to that case, which is right for showing what was turned but would count a two-look
 *   OLL as a vote for the one-look algorithm of the case it passed through. A false start
 *   ({@code ollrestart}) is not an algorithm, as in {@link SolveReading}.
 * </ul>
 * F2L pairs are read too, each pair being one algorithm by construction.
 */
public final class AlgorithmVotes {

  /** How many of a case's answers the top one must be before it is a vote. */
  static final int MIN_TIMES = 2;

  private static final String RESTART_PART_SUFFIX = "restart";

  private AlgorithmVotes() {
  }

  /**
   * @param solves the most recent solves, newest first, whatever they were solved as: only smart-cube
   *     CFOP solves are read
   * @return each case's vote ({@code oll_27}, {@code pll_t}, {@code pair_4} to its algorithm), sorted
   *     by case code
   */
  public static Map<String, String> readFrom(List<SolveTime> solves) {
    final Map<String, List<String>> answers = new LinkedHashMap<String, List<String>>();
    for (SolveTime solve : solves) {
      CaseExecutions.readSteps(solve, new CaseExecutions.StepReader() {
        @Override
        public void read(SolveStep step, SolveSolution.Step turned) {
          collect(step, turned, answers);
        }
      });
    }
    Map<String, String> votes = new TreeMap<String, String>();
    for (Map.Entry<String, List<String>> answer : answers.entrySet()) {
      String caseCode = answer.getKey();
      List<CaseExecutions.Shown> shown = CaseExecutions.shownFrom(caseCode,
          CaseExecutions.spreadOf(caseCode, answer.getValue()));
      if (!shown.isEmpty() && shown.get(0).getTimes() >= MIN_TIMES) {
        votes.put(caseCode, shown.get(0).getMoves());
      }
    }
    return votes;
  }

  /**
   * The cases whose vote is new or differs from the one last reported, to send. A case missing from
   * {@code current} is left out rather than withdrawn: its last vote still stands on the server.
   */
  public static Map<String, String> changedSince(Map<String, String> current,
      Map<String, String> reported) {
    Map<String, String> changed = new TreeMap<String, String>();
    for (Map.Entry<String, String> vote : current.entrySet()) {
      String before = reported == null ? null : reported.get(vote.getKey());
      if (!vote.getValue().equals(before)) {
        changed.put(vote.getKey(), vote.getValue());
      }
    }
    return Collections.unmodifiableMap(changed);
  }

  private static void collect(SolveStep step, SolveSolution.Step turned,
      Map<String, List<String>> answers) {
    List<SolveStep> parts = step.getSubSteps();
    if (SolveReading.F2L.equals(step.getName())) {
      for (int i = 0; i < parts.size() && i < turned.getGroups().size(); i++) {
        String caseCode = CaseExecutions.caseOfPart(parts.get(i).getName());
        if (caseCode != null && CaseAlgorithms.isPair(caseCode)) {
          keep(answers, caseCode, turned.getPartMoves(i));
        }
      }
      return;
    }
    String caseCode = CaseExecutions.caseOfStep(step.getName());
    if (caseCode == null) {
      return;
    }
    if (parts.isEmpty()) {
      keep(answers, caseCode, turned.getPartMoves(0)); // one algorithm is stored with no parts
      return;
    }
    int only = -1;
    for (int i = 0; i < parts.size(); i++) {
      String name = parts.get(i).getName();
      if (name != null && name.endsWith(RESTART_PART_SUFFIX)) {
        continue;
      }
      if (only >= 0) {
        return; // a second algorithm: a two-look
      }
      only = i;
    }
    if (only >= 0 && only < turned.getGroups().size()
        && caseCode.equals(CaseExecutions.caseOfPart(parts.get(only).getName()))) {
      keep(answers, caseCode, turned.getPartMoves(only));
    }
  }

  private static void keep(Map<String, List<String>> answers, String caseCode, String moves) {
    if (moves == null || moves.trim().isEmpty()) {
      return;
    }
    List<String> kept = answers.get(caseCode);
    if (kept == null) {
      kept = new ArrayList<String>();
      answers.put(caseCode, kept);
    }
    if (kept.size() < CaseExecutions.PER_CASE) {
      kept.add(moves);
    }
  }
}
