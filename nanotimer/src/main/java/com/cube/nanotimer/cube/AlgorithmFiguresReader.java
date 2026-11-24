package com.cube.nanotimer.cube;

import com.cube.nanotimer.session.AlgorithmFigures;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.CmllCases;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveTime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@link AlgorithmFigures} of a CFOP window's F2L, OLL and PLL, or a Roux window's CMLL, read
 * out of its solves.
 *
 * <p>Standard is what the reconstruction's bulb asks, {@link CaseAlgorithms#read}, with two
 * differences the figures need: a last layer case taken in more than one algorithm is not standard,
 * and the solver's say over the bulb (a muted, starred or typed-in algorithm) changes nothing here,
 * since an algorithm of their own is still not one of the usual ones. F2L counts its
 * {@link CaseAlgorithms#BASIC_PAIR_CASES basic cases} only.
 */
public final class AlgorithmFiguresReader {

  /** The families a reading names its executions with: the same strings, said in one place. */
  public static final String F2L = SolveReading.F2L;
  public static final String OLL = "oll";
  public static final String PLL = "pll";
  public static final String CMLL = "cmll";

  private AlgorithmFiguresReader() {
  }

  /**
   * @param solves the window's solves, newest first
   * @param readings where each solve's executions come from, read there only where none was kept
   * @return the figures of each of F2L, OLL and PLL, in solving order
   */
  public static Map<String, AlgorithmFigures> readFrom(List<SolveTime> solves,
      SolveReadings readings) {
    return readFrom(solves, readings, CubeMethod.CFOP);
  }

  /** As above, for the families of that method: Roux has its CMLL alone. */
  public static Map<String, AlgorithmFigures> readFrom(List<SolveTime> solves,
      SolveReadings readings, CubeMethod method) {
    Map<String, AlgorithmFigures> figures = empty(method);
    add(figures, solves, readings);
    return figures;
  }

  /** The figures of that method's families with nothing counted yet, for {@link #add} to fill. */
  public static Map<String, AlgorithmFigures> empty(CubeMethod method) {
    Map<String, AlgorithmFigures> figures = new LinkedHashMap<String, AlgorithmFigures>();
    if (method == CubeMethod.ROUX) {
      figures.put(CMLL, new AlgorithmFigures(CmllCases.codes().size()));
    } else {
      figures.put(F2L, new AlgorithmFigures(CaseAlgorithms.BASIC_PAIR_CASES));
      figures.put(OLL, new AlgorithmFigures(setSize(OLL)));
      figures.put(PLL, new AlgorithmFigures(setSize(PLL)));
    }
    return figures;
  }

  /**
   * Counts older solves into figures already holding newer ones, so history can be read a chunk at
   * a time.
   *
   * @param solves solves older than any counted so far, newest first
   * @return whether every case is now counted, so older solves can change nothing
   */
  public static boolean add(Map<String, AlgorithmFigures> figures, List<SolveTime> solves,
      SolveReadings readings) {
    for (SolveTime solve : solves) {
      if (complete(figures)) {
        break; // every case has had its latest say
      }
      for (SolveReading.Execution execution : readings.of(solve)) {
        AlgorithmFigures family = figures.get(execution.getFamily());
        // Counted whenever a case was named, even where there was nothing to set it against: a
        // case seen is a case seen, and it is the pair with no moves at all that is not one.
        if (family != null && execution.getCaseCode() != null) {
          // A last layer case taken in more than one algorithm is not standard, whatever each was.
          family.add(execution.getCaseCode(), execution.isListed() && execution.getAlgorithms() <= 1,
              execution.getMoves(), execution.getUsualMoves());
        }
      }
    }
    return complete(figures);
  }

  /** As above, reading every solve and keeping nothing. */
  public static Map<String, AlgorithmFigures> readFrom(List<SolveTime> solves) {
    return readFrom(solves, SolveReadings.none());
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
