package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.vo.CubeMethod;
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
 * <p>Read the way the case popup reads "the one you use" ({@link CaseExecutions#recentOf}): the
 * case's last {@link CaseExecutions#RECENT} answers, the majority one spelled as the popup spells
 * it, so two solvers' spellings of one algorithm land on one row. A last layer case counts each
 * solve it was handed in, a two-look as an answer of its own, so a case mostly two-looked casts no
 * vote; a false start ({@code ollrestart}) is not a look, as in {@link SolveReading}. One thing is
 * stricter than the popup: <b>turned at least twice</b>, since the popup marks a case answered once
 * and a vote from one solve would be a fumble as often as a habit.
 * F2L pairs are read too, each pair being one algorithm by construction, and so is a Roux solve's
 * CMLL, under the same one-look rule: a two-look CMLL is stored with an orient and a permute part.
 */
public final class AlgorithmVotes {

  /** How many of a case's answers the top one must be before it is a vote. */
  static final int MIN_TIMES = 2;

  private AlgorithmVotes() {
  }

  /**
   * @param solves the most recent solves, newest first, whatever they were solved as: only smart-cube
   *     CFOP and Roux solves are read
   * @return each case's vote ({@code oll_27}, {@code pll_t}, {@code pair_4}, {@code cmll_suneleftbar}
   *     to its algorithm), sorted by case code
   */
  public static Map<String, String> readFrom(List<SolveTime> solves) {
    final Map<String, List<String>> answers = new LinkedHashMap<String, List<String>>();
    for (SolveTime solve : solves) {
      CaseExecutions.readSteps(replay(solve), new CaseExecutions.StepReader() {
        @Override
        public void read(SolveStep step, SolveSolution.Step turned) {
          collect(step, turned, answers);
        }
      });
    }
    Map<String, String> votes = new TreeMap<String, String>();
    for (Map.Entry<String, List<String>> answer : answers.entrySet()) {
      String caseCode = answer.getKey();
      CaseExecutions.Shown usual = CaseExecutions.recentOf(caseCode, answer.getValue()).getUsual();
      if (usual != null && usual.getTimes() >= MIN_TIMES) {
        votes.put(caseCode, usual.getMoves());
      }
    }
    return votes;
  }

  /**
   * The cases whose vote is new or turns differently from the one last reported, to send. Compared
   * by turning, since an unusual algorithm is spelled from its latest execution and a regrip can
   * respell it. A case missing from {@code current} is left out rather than withdrawn: its last vote
   * still stands on the server.
   */
  public static Map<String, String> changedSince(Map<String, String> current,
      Map<String, String> reported) {
    Map<String, String> changed = new TreeMap<String, String>();
    for (Map.Entry<String, String> vote : current.entrySet()) {
      String before = reported == null ? null : reported.get(vote.getKey());
      if (before == null || !(before.equals(vote.getValue())
          || CaseAlgorithms.sameTurning(vote.getKey(), before, vote.getValue()))) {
        changed.put(vote.getKey(), vote.getValue());
      }
    }
    return Collections.unmodifiableMap(changed);
  }

  private static CaseExecutions.Replay replay(SolveTime solve) {
    return SolveTypeMethod.of(solve.getSolveType()) == CubeMethod.ROUX
        ? CaseExecutions.replayAs(solve, CubeMethod.ROUX) : CaseExecutions.replay(solve);
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
      if (name != null && name.endsWith(SolveReading.RESTART_PART_SUFFIX)) {
        continue;
      }
      if (only >= 0) {
        keep(answers, caseCode, null); // a second algorithm: a two-look
        return;
      }
      only = i;
    }
    if (only >= 0 && only < turned.getGroups().size()
        && caseCode.equals(CaseExecutions.caseOfPart(parts.get(only).getName()))) {
      keep(answers, caseCode, turned.getPartMoves(only));
    }
  }

  /** @param moves the answer, or null for a two-look */
  private static void keep(Map<String, List<String>> answers, String caseCode, String moves) {
    if (moves != null && moves.trim().isEmpty()) {
      return;
    }
    List<String> kept = answers.get(caseCode);
    if (kept == null) {
      kept = new ArrayList<String>();
      answers.put(caseCode, kept);
    }
    if (kept.size() < CaseExecutions.RECENT) {
      kept.add(moves);
    }
  }
}
