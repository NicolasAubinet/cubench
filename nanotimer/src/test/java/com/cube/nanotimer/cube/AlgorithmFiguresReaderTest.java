package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.session.AlgorithmFigures;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveTime;

import java.util.List;
import java.util.Map;

import org.junit.Test;

/** The standard-algorithm figures, off solves built as {@link CaseExecutionsTest} builds them. */
public class AlgorithmFiguresReaderTest {

  private static final String SUNE = "R U R' U R U U R'";
  private static final String TPERM = "R U R' U' R' F R R U' R' U' R U R' F'";
  /** F2L case 4 into each slot in turn, front right first. */
  private static final String PAIRS = "R U R' B U B' L U L' F U F'";
  /** Edges then corners: a two-look OLL of a case one algorithm would have done. */
  private static final String TWO_LOOK = "F R U R' U' F' " + SUNE;

  @Test
  public void countsEachStepAgainstItsWholeSet() {
    Map<String, AlgorithmFigures> figures =
        AlgorithmFiguresReader.readFrom(CaseExecutionsTest.solves(SUNE + " " + TPERM));

    assertFigures(figures.get(AlgorithmFiguresReader.OLL), 57, 1, 0);
    assertFigures(figures.get(AlgorithmFiguresReader.PLL), 21, 1, 0);
    assertFigures(figures.get(AlgorithmFiguresReader.F2L), 41, 0, 0);
    assertEquals(0, figures.get(AlgorithmFiguresReader.PLL).getExtraMoves());
  }

  @Test
  public void countsACaseTakenInTwoAlgorithmsAsNotStandard() {
    AlgorithmFigures oll = AlgorithmFiguresReader
        .readFrom(CaseExecutionsTest.solves(TWO_LOOK + " " + TPERM))
        .get(AlgorithmFiguresReader.OLL);

    assertFigures(oll, 57, 0, 1);
    assertTrue(oll.getExtraMoves() > 0);
  }

  @Test
  public void countsACaseOnceHoweverOftenItCameUp() {
    AlgorithmFigures f2l = AlgorithmFiguresReader
        .readFrom(CaseExecutionsTest.solves(PAIRS + " " + SUNE + " " + TPERM))
        .get(AlgorithmFiguresReader.F2L);

    assertFigures(f2l, 41, 1, 0);
  }

  @Test
  public void readsNothingOutOfASolveThatIsNotCfop() {
    List<SolveTime> solves = CaseExecutionsTest.solves(SUNE + " " + TPERM);
    solves.get(0).getSolveType().setMethod(CubeMethod.ROUX);

    assertFigures(AlgorithmFiguresReader.readFrom(solves).get(AlgorithmFiguresReader.OLL),
        57, 0, 0);
  }

  private static void assertFigures(AlgorithmFigures figures, int set, int standard,
      int notStandard) {
    assertEquals(set, figures.getSetSize());
    assertEquals(standard, figures.getStandard());
    assertEquals(notStandard, figures.getNotStandard());
    assertEquals(set - standard - notStandard, figures.getNotSeen());
  }
}
