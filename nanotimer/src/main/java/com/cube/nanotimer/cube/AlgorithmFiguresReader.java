package com.cube.nanotimer.cube;

import com.cube.nanotimer.session.AlgorithmFigures;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.smartcube.step.AlgorithmExecution;
import com.cube.nanotimer.smartcube.step.AlgorithmForm;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@link AlgorithmFigures} of a CFOP window's F2L, OLL and PLL, read out of its solves.
 *
 * <p>Standard is what the reconstruction's bulb asks, {@link CaseAlgorithms#read}, with two
 * differences the figures need: a last layer case taken in more than one algorithm is not standard,
 * and the solver's say over the bulb (a muted, starred or typed-in algorithm) changes nothing here,
 * since an algorithm of their own is still not one of the usual ones. F2L counts its
 * {@link CaseAlgorithms#BASIC_PAIR_CASES basic cases} only.
 */
public final class AlgorithmFiguresReader {

  public static final String F2L = "f2l";
  public static final String OLL = "oll";
  public static final String PLL = "pll";

  private AlgorithmFiguresReader() {
  }

  /**
   * @param solves the window's solves, newest first
   * @return the figures of each of F2L, OLL and PLL, in solving order
   */
  public static Map<String, AlgorithmFigures> readFrom(List<SolveTime> solves) {
    final Map<String, AlgorithmFigures> figures = new LinkedHashMap<String, AlgorithmFigures>();
    figures.put(F2L, new AlgorithmFigures(CaseAlgorithms.BASIC_PAIR_CASES));
    figures.put(OLL, new AlgorithmFigures(setSize(OLL)));
    figures.put(PLL, new AlgorithmFigures(setSize(PLL)));
    CaseExecutions.StepReader reader = new CaseExecutions.StepReader() {
      @Override
      public void read(SolveStep step, SolveSolution.Step turned) {
        if (F2L.equals(step.getName())) {
          readPairs(step, turned, figures.get(F2L));
        } else {
          readLastLayer(step, turned, figures);
        }
      }
    };
    for (SolveTime solve : solves) {
      if (complete(figures)) {
        break; // every case has had its latest say
      }
      CaseExecutions.readSteps(solve, reader);
    }
    return figures;
  }

  private static void readPairs(SolveStep step, SolveSolution.Step turned,
      AlgorithmFigures figures) {
    List<SolveStep> parts = step.getSubSteps();
    for (int i = 0; i < parts.size() && i < turned.getGroups().size(); i++) {
      String caseCode = CaseExecutions.caseOfPart(parts.get(i).getName());
      if (CaseAlgorithms.isBasicPair(caseCode)) {
        count(figures, caseCode, turned.getPartMoves(i), false);
      }
    }
  }

  private static void readLastLayer(SolveStep step, SolveSolution.Step turned,
      Map<String, AlgorithmFigures> figures) {
    String caseCode = CaseExecutions.caseOfStep(step.getName());
    AlgorithmFigures family = caseCode == null ? null
        : figures.get(MethodStatistics.familyOf(caseCode));
    if (family == null) {
      return;
    }
    // Every algorithm the step took, run together, so a two-look is measured against the one.
    StringBuilder moves = new StringBuilder();
    for (int i = 0; i < turned.getGroups().size(); i++) {
      moves.append(' ').append(turned.getPartMoves(i));
    }
    count(family, caseCode, moves.toString().trim(), step.getSubSteps().size() > 1);
  }

  private static void count(AlgorithmFigures figures, String caseCode, String moves,
      boolean twoAlgorithms) {
    if (moves.isEmpty() || AlgorithmForm.key(moves) == null) {
      return; // nothing turned, or nothing that can be read: an older execution may say more
    }
    AlgorithmExecution read = CaseAlgorithms.read(caseCode, moves);
    figures.add(caseCode, !twoAlgorithms && !read.isUnusual(), read.getMoves(),
        read.getUsualMoves());
  }

  private static int setSize(String family) {
    int size = 0;
    for (String caseCode : LastLayerScrambles.cases()) {
      if (family.equals(MethodStatistics.familyOf(caseCode))) {
        size++;
      }
    }
    return size;
  }

  private static boolean complete(Map<String, AlgorithmFigures> figures) {
    for (AlgorithmFigures family : figures.values()) {
      if (!family.isComplete()) {
        return false;
      }
    }
    return true;
  }
}
