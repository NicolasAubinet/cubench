package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
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

    List<StepTime> steps = analyzer.getStepTimes();
    assertEquals(4, steps.size());
    assertStep(steps.get(0), "Cross", 0, 600);
    assertStep(steps.get(1), "F2L", 500, 200);
    assertStep(steps.get(2), "OLL", 800, 700);
    assertStep(steps.get(3), "PLL", 400, 1400);
    assertTrue(analyzer.isComplete());

    long total = 0;
    for (StepTime step : steps) {
      total += step.getTotalMs();
    }
    assertEquals(timestampMs - SOLVE_START_MS, total); // the steps account for the whole solve
  }

  @Test
  public void reportsASkippedStepAsZero() {
    startFrom(T_PERM); // cross, F2L and OLL are all already done: only PLL is left

    play(T_PERM, 900, 100);

    List<StepTime> steps = analyzer.getStepTimes();
    assertEquals(4, steps.size());
    assertStep(steps.get(0), "Cross", 0, 0);
    assertStep(steps.get(1), "F2L", 0, 0);
    assertStep(steps.get(2), "OLL", 0, 0);
    assertStep(steps.get(3), "PLL", 900, 1400);
  }
}
