package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.coach.CrossTurn;
import com.cube.nanotimer.scrambler.cross.CrossSolver;
import com.cube.nanotimer.vo.SolveTime;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Solves are built as {@link CaseExecutionsTest} builds them: the scramble is the solve undone. */
public class CrossMovesTest {

  /** From a D cross, a pair into each slot and a last layer: what follows each cross below. */
  private static final String PAIRS = "R U R' B U B' L U L' F U F'";
  private static final String LAST_LAYER = "R U R' U R U U R' R U R' U' R' F R R U' R' U' R U R' F'";

  private static CrossTurn crossOf(String moves) {
    SolveTime solve = CaseExecutionsTest.solves(moves).get(0);
    SolveReading reading = SolveReading.read(solve);
    assertTrue("the fixture should read as a whole CFOP solve", reading.isWhole());
    return reading.getCross();
  }

  @Test
  public void countsTheCrossAgainstTheOptimalOnItsScramble() {
    String moves = "U F R " + PAIRS + " " + LAST_LAYER;
    CrossTurn cross = crossOf(moves);

    assertNotNull(cross);
    assertEquals(3, cross.getTurnedMoves());
    String scramble = CaseExecutionsTest.solves(moves).get(0).getScramble();
    assertEquals(CrossSolver.dCross().optimalLength(scramble), cross.getOptimalMoves());
    assertTrue(cross.getOptimalMoves() <= 3);
    assertFalse(cross.isXCross());
  }

  @Test
  public void marksACrossThatPutAPairInWithItAsAnXCross() {
    // The R that ends the cross also closes the pair the fixture's first R U R' builds.
    String pairsAfterFirst = PAIRS.substring("R U R' ".length());
    CrossTurn cross = crossOf("R " + pairsAfterFirst + " " + LAST_LAYER);

    assertNotNull(cross);
    assertTrue(cross.isXCross());
    assertEquals(1, cross.getTurnedMoves());
  }

  @Test
  public void keptReadingGivesTheSameCross() {
    List<SolveTime> solves = CaseExecutionsTest.solves("U F R " + PAIRS + " " + LAST_LAYER);
    SolveReading fresh = SolveReading.read(solves.get(0));
    SolveReading kept = SolveReading.parse(SolveReading.toJson(fresh), 1);

    assertEquals(fresh.getCross().getOptimalMoves(), kept.getCross().getOptimalMoves());
    assertEquals(fresh.getCross().getTurnedMoves(), kept.getCross().getTurnedMoves());
    assertEquals(fresh.getCross().getSlotsWithCross(), kept.getCross().getSlotsWithCross());
    assertEquals(1, kept.getCross().getSolveId());
  }

  @Test
  public void foldsAQuarterTurnPairIntoAHalfTurnAndNothingElse() {
    assertEquals(1, CrossMoves.htm(Arrays.asList("R", "R")));
    assertEquals(1, CrossMoves.htm(Collections.singletonList("R2")));
    assertEquals(2, CrossMoves.htm(Arrays.asList("U", "U'")));
    assertEquals(2, CrossMoves.htm(Arrays.asList("R", "R", "R")));
    assertEquals(2, CrossMoves.htm(Arrays.asList("R2", "R2")));
  }
}
