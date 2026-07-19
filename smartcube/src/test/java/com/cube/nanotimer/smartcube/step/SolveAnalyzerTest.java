package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.List;
import org.junit.Test;

public class SolveAnalyzerTest {

  private static final String T_PERM = "R U R' U' R' F R2 U' R' U' R U R' F'"; // its own inverse
  private static final String SUNE = "R U R' U R U2 R'";
  private static final String ANTI_SUNE = "R U2 R' U' R U' R'";

  private static final long SOLVE_START_MS = 1000;

  private final CubieCube cube = new CubieCube();
  private final SolveAnalyzer analyzer = new SolveAnalyzer(new CFOPStepDetector());

  private long timestampMs = SOLVE_START_MS;

  /** Scramble the cube, then start the solve at {@link #SOLVE_START_MS}. */
  private void startFrom(String... scramble) {
    for (String moves : scramble) {
      for (String token : moves.split(" ")) {
        Face face = Face.valueOf(token.substring(0, 1));
        for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
          cube.applyMove(face, token.endsWith("'"));
        }
      }
    }
    analyzer.start(new CubeState(cube.toFaceCube()), SOLVE_START_MS);
  }

  /** Play a step: {@code pauseMs} before its first move, then a move every {@code intervalMs}. */
  private void play(String moves, long pauseMs, long intervalMs) {
    boolean firstMove = true;
    for (String token : moves.split(" ")) {
      Face face = Face.valueOf(token.substring(0, 1));
      boolean prime = token.endsWith("'");
      for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
        timestampMs += firstMove ? pauseMs : intervalMs;
        firstMove = false;
        cube.applyMove(face, prime);
        analyzer.onMove(new CubeMove(face, prime, timestampMs));
        analyzer.onState(new CubeState(cube.toFaceCube()));
      }
    }
  }

  private List<StepTime> stepTimes() {
    return analyzer.getStepTimes();
  }

  private static void assertStep(StepTime step, String name, long recognitionMs, long executionMs) {
    assertEquals(name, step.getStepName());
    assertEquals(recognitionMs, step.getRecognitionMs());
    assertEquals(executionMs, step.getExecutionMs());
  }

  @Test
  public void splitsEachStepIntoRecognitionAndExecution() {
    // Built backwards from solved: the solve is cross (F R), an F2L pair, OLL, PLL.
    startFrom(T_PERM, SUNE, "R U' R'", "R' F'");

    play("F R", 0, 600); // the first move is what started the solve: no recognition
    play("R U R'", 500, 100);
    play(ANTI_SUNE, 800, 100);
    play(T_PERM, 400, 100);

    List<StepTime> steps = stepTimes();
    assertEquals(4, steps.size());
    assertStep(steps.get(0), "cross", 0, 600);
    assertStep(steps.get(1), "f2l", 500, 200);
    assertStep(steps.get(2), "oll", 800, 700);
    assertStep(steps.get(3), "pll", 400, 1400);
    assertTrue(analyzer.isComplete());

    long total = 0;
    for (StepTime step : steps) {
      total += step.getTotalMs();
    }
    assertEquals(timestampMs - SOLVE_START_MS, total); // the steps account for the whole solve
  }

  @Test
  public void recordsEveryMoveSoTheSplitRebuildsFromTheStepDurationsAlone() {
    startFrom(T_PERM, SUNE, "R U' R'", "R' F'");

    play("F R", 0, 600);
    play("R U R'", 500, 100);
    play(ANTI_SUNE, 800, 100);
    play(T_PERM, 400, 100);

    List<CubeMove> moves = analyzer.getMoves();
    assertEquals(2 + 3 + 8 + 15, moves.size());

    // Partition the way a reader with only the persisted durations can: walk the cumulative step
    // totals against each move's offset into the solve.
    List<StepTime> steps = stepTimes();
    int[] perStep = new int[steps.size()];
    long boundaryMs = 0;
    int move = 0;
    for (int i = 0; i < steps.size(); i++) {
      boundaryMs += steps.get(i).getTotalMs();
      while (move < moves.size()
          && moves.get(move).getCubeTimestampMs() - analyzer.getSolveStartMs() <= boundaryMs) {
        perStep[i]++;
        move++;
      }
    }
    assertArrayEquals(new int[] {2, 3, 8, 15}, perStep);
    assertEquals(moves.size(), move); // every move landed in a step
  }

  @Test
  public void countsThePausesBetweenF2lSlotsAsRecognition() {
    // Each scramble chunk pulls one pair out of its slot; the solve puts them back one at a time,
    // with a think before each. All four pauses are recognition.
    startFrom(T_PERM, SUNE, "R U' R'", "R' U R", "L' U L", "L U' L'", "R' F'");

    play("F R", 0, 600); // cross
    play("L U L'", 500, 100);
    play("L' U' L", 400, 100);
    play("R' U' R", 300, 100);
    play("R U R'", 200, 100);

    List<StepTime> steps = stepTimes();
    StepTime f2l = steps.get(1);
    assertEquals(500 + 400 + 300 + 200, f2l.getRecognitionMs());
    assertEquals(4, f2l.getSubSteps().size());
    assertEquals(500, f2l.getSubSteps().get(0).getRecognitionMs());
    assertEquals(200, f2l.getSubSteps().get(3).getRecognitionMs());
    assertEquals(f2l.getTotalMs(),
        f2l.getSubSteps().stream().mapToLong(StepTime::getTotalMs).sum());
  }

  @Test
  public void countsThePauseBetweenTheTwoLooksOfOll() {
    // Orient the edges, think, then orient the corners: the pause between is OLL recognition.
    startFrom(T_PERM, SUNE, "F U R U' R' F'"); // the inverse of the edge-orientation alg below

    play("F R U R' U' F'", 600, 100); // edge orientation
    play(ANTI_SUNE, 900, 100); // corner orientation

    StepTime oll = stepTimes().get(2);
    assertEquals(2, oll.getSubSteps().size());
    assertEquals("edges", oll.getSubSteps().get(0).getStepName());
    assertEquals(600, oll.getSubSteps().get(0).getRecognitionMs());
    assertEquals("corners", oll.getSubSteps().get(1).getStepName());
    assertEquals(900, oll.getSubSteps().get(1).getRecognitionMs());
    assertEquals(600 + 900, oll.getRecognitionMs()); // both pauses, not just the first
  }

  @Test
  public void reportsAOneLookStepAsASingleStep() {
    startFrom(T_PERM, SUNE); // a one-look OLL: anti-Sune orients edges and corners together

    play(ANTI_SUNE, 700, 100);

    StepTime oll = stepTimes().get(2);
    assertEquals(700, oll.getRecognitionMs()); // the single pause, counted once
    assertEquals(700, oll.getExecutionMs()); // 8 moves, 100ms apart
    assertTrue(oll.getSubSteps().isEmpty()); // only one part was needed: it is the step
  }

  @Test
  public void countsAnAufBeforeAnAlgorithmAsRecognition() {
    // The last layer needs squaring up before the PLL can be read: that U is part of recognising it.
    startFrom(T_PERM, "U'");

    play("U", 800, 100); // AUF
    play(T_PERM, 300, 100); // the algorithm proper

    StepTime pll = stepTimes().get(3);
    assertEquals(800 + 300, pll.getRecognitionMs()); // the think, the AUF, and the think after it
    assertEquals(1400, pll.getExecutionMs()); // only the algorithm
  }

  @Test
  public void matchesARealCfopSolve() {
    // Cross, then F2L, then the last layer: the cross completes before F2L, so this is CFOP.
    startFrom(T_PERM, SUNE, "R U' R'", "R' F'");

    play("F R", 0, 600);
    play("R U R'", 500, 100);
    play(ANTI_SUNE, 800, 100);
    play(T_PERM, 400, 100);

    assertTrue(analyzer.isComplete());
    assertTrue(analyzer.matchesMethod());
  }

  @Test
  public void matchesWhenTheScrambleLeftTheFirstTwoLayersSolved() {
    // An OLL/PLL drill: cross and F2L come free with the scramble (both skips, done at the start).
    startFrom(T_PERM);

    play(T_PERM, 900, 100);

    assertTrue(analyzer.isComplete());
    assertTrue(analyzer.matchesMethod());
  }

  @Test
  public void doesNotMatchWhenTheFirstTwoLayersAreFinishedAllAtOnce() {
    // Both layers are assembled together at the end (cross does not precede F2L), the shape a
    // Roux or freestyle solve takes when seen through a CFOP detector — no CFOP breakdown.
    startFrom("L R");

    play("R' L'", 500, 100);

    assertTrue(analyzer.isComplete());
    assertFalse(analyzer.matchesMethod());
  }

  @Test
  public void keepsTheFinishedPairsOfAnF2lTheSolveStoppedInside() {
    startFrom(T_PERM, SUNE, "R U' R'", "R' U R", "L' U L", "L U' L'", "R' F'");

    play("F R", 0, 600); // cross
    play("L U L'", 500, 100); // pair 1
    play("L' U' L", 400, 100); // pair 2
    play("U U U", 700, 100); // then it went nowhere: U turns finish no slot

    List<StepTime> steps = stepTimes();
    assertFalse(analyzer.isComplete());
    assertEquals(Integer.valueOf(CFOPStepDetector.F2L), analyzer.getStoppedStep());

    assertEquals(2, steps.size()); // no tail here: it is derived from the stopped step at display
    assertTrue(steps.get(0).isComplete());
    StepTime f2l = steps.get(1);
    assertEquals("f2l", f2l.getStepName());
    assertFalse(f2l.isComplete()); // two of its four pairs: it never reached its milestone
    assertEquals(2, f2l.getSubSteps().size());

    // The steps stop at the last pair, leaving the U turns after it to the tail.
    long total = 0;
    for (StepTime step : steps) {
      total += step.getTotalMs();
    }
    assertEquals(600 + 500 + 200 + 400 + 200, total);
  }

  @Test
  public void reportsNoStoppedStepWhenTheSolveRanToTheEnd() {
    startFrom(T_PERM, SUNE, "R U' R'", "R' F'");

    play("F R", 0, 600);
    play("R U R'", 500, 100);
    play(ANTI_SUNE, 800, 100);
    play(T_PERM, 400, 100);

    assertTrue(analyzer.isComplete());
    assertEquals(null, analyzer.getStoppedStep()); // nothing left over, so no tail to draw
  }

  @Test
  public void keepsASoloPairRatherThanCollapsingIt() {
    // One pair is the whole of what the F2L got done. It has to survive as a part: an incomplete step
    // writes its parts and nothing else, so collapsing it would lose the step altogether.
    startFrom(T_PERM, SUNE, "R U' R'", "R' U R", "L' U L", "L U' L'", "R' F'");

    play("F R", 0, 600);
    play("L U L'", 500, 100);
    play("U U", 400, 100);

    StepTime f2l = stepTimes().get(1);
    assertFalse(f2l.isComplete());
    assertEquals(1, f2l.getSubSteps().size());
  }

  @Test
  public void matchesOnTheFirstPairWhenTheSolveStoppedInsideF2l() {
    // The cross went in before the first pair: CFOP-shaped, even though F2L never finished.
    startFrom(T_PERM, SUNE, "R U' R'", "R' U R", "L' U L", "L U' L'", "R' F'");

    play("F R", 0, 600);
    play("L U L'", 500, 100);

    assertFalse(analyzer.isComplete());
    assertTrue(analyzer.matchesMethod());
  }

  @Test
  public void doesNotMatchOnACrossAlone() {
    // Every method builds a cross eventually, so one on its own says nothing about which was used —
    // and a breakdown resting on it would be a guess.
    startFrom(T_PERM, SUNE, "R U' R'", "R' U R", "L' U L", "L U' L'", "R' F'");

    play("F R", 0, 600);
    play("U U", 500, 100);

    assertFalse(analyzer.isComplete());
    assertFalse(analyzer.matchesMethod());

    List<StepTime> steps = stepTimes();
    assertEquals(1, steps.size()); // just the cross: no F2L step was invented for the U turns
    assertEquals("cross", steps.get(0).getStepName());
  }

  @Test
  public void reportsASkippedStepAsZero() {
    startFrom(T_PERM); // cross, F2L and OLL are all already done: only PLL is left

    play(T_PERM, 900, 100);

    List<StepTime> steps = stepTimes();
    assertEquals(4, steps.size());
    assertStep(steps.get(0), "cross", 0, 0);
    assertStep(steps.get(1), "f2l", 0, 0);
    assertStep(steps.get(2), "oll", 0, 0);
    assertStep(steps.get(3), "pll", 900, 1400);
  }
}
