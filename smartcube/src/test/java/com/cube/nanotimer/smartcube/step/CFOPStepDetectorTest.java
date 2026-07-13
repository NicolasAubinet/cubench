package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class CFOPStepDetectorTest {

  private static final String T_PERM = "R U R' U' R' F R2 U' R' U' R U R' F'"; // its own inverse
  private static final String SUNE = "R U R' U R U2 R'";
  private static final String ANTI_SUNE = "R U2 R' U' R U' R'";

  private final CubieCube cube = new CubieCube();
  private final CFOPStepDetector detector = new CFOPStepDetector();

  private long timestampMs;

  /** Scramble the cube with the given moves, then arm the detector on the resulting state. */
  private void startFrom(String... scramble) {
    for (String moves : scramble) {
      for (String token : moves.split(" ")) {
        applyToken(token);
      }
    }
    detector.reset(state(), timestampMs);
  }

  /** Play the moves back into the detector, one quarter turn per state, 100ms apart. */
  private List<StepBoundaryEvent> play(String... solve) {
    List<StepBoundaryEvent> events = new ArrayList<>();
    for (String moves : solve) {
      for (String token : moves.split(" ")) {
        Face face = Face.valueOf(token.substring(0, 1));
        boolean prime = token.endsWith("'");
        for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
          cube.applyMove(face, prime);
          timestampMs += 100;
          events.addAll(detector.onState(state(), new CubeMove(face, prime, timestampMs)));
        }
      }
    }
    return events;
  }

  private void applyToken(String token) {
    Face face = Face.valueOf(token.substring(0, 1));
    for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
      cube.applyMove(face, token.endsWith("'"));
    }
  }

  private CubeState state() {
    return new CubeState(cube.toFaceCube());
  }

  private static Long timeOf(List<StepBoundaryEvent> events, int step) {
    for (StepBoundaryEvent event : events) {
      if (event.getStepIndex() == step) {
        return event.getCubeTimestampMs();
      }
    }
    return null;
  }

  @Test
  public void reachesTheStepsInOrderThroughASolve() {
    // Built backwards from solved, so the solve is: cross (F), an F2L pair, OLL, PLL.
    startFrom(T_PERM, SUNE, "R U' R'", "F'");
    assertNull(detector.getStepTimestampMs(CFOPStepDetector.CROSS));

    List<StepBoundaryEvent> cross = play("F");
    assertEquals(Long.valueOf(100), timeOf(cross, CFOPStepDetector.CROSS));
    assertNull(timeOf(cross, CFOPStepDetector.F2L));

    List<StepBoundaryEvent> f2l = play("R U R'");
    assertEquals(Long.valueOf(400), timeOf(f2l, CFOPStepDetector.F2L));

    List<StepBoundaryEvent> oll = play(ANTI_SUNE);
    assertEquals(Long.valueOf(1200), timeOf(oll, CFOPStepDetector.OLL));
    assertFalse(detector.isComplete());

    List<StepBoundaryEvent> pll = play(T_PERM);
    assertEquals(Long.valueOf(2700), timeOf(pll, CFOPStepDetector.PLL));
    assertTrue(detector.isComplete());
    assertEquals(Face.D, detector.getCrossFace());

    // Later steps disturb earlier ones in passing; their times must not move.
    assertEquals(Long.valueOf(100), detector.getStepTimestampMs(CFOPStepDetector.CROSS));
    assertEquals(Long.valueOf(400), detector.getStepTimestampMs(CFOPStepDetector.F2L));
  }

  @Test
  public void datesStepsAlreadyCompleteAtTheStartToTheSolveStart() {
    startFrom(T_PERM, SUNE); // last-layer algs: cross and F2L are untouched
    assertEquals(Long.valueOf(0), detector.getStepTimestampMs(CFOPStepDetector.CROSS));
    assertEquals(Long.valueOf(0), detector.getStepTimestampMs(CFOPStepDetector.F2L));
    assertNull(detector.getStepTimestampMs(CFOPStepDetector.OLL));

    assertEquals(Long.valueOf(800), timeOf(play(ANTI_SUNE), CFOPStepDetector.OLL));
    assertEquals(Long.valueOf(2300), timeOf(play(T_PERM), CFOPStepDetector.PLL));
  }

  @Test
  public void treatsATrailingAufAsTheFinalStep() {
    startFrom("U"); // everything but the alignment of the last layer
    assertEquals(Long.valueOf(0), detector.getStepTimestampMs(CFOPStepDetector.OLL));
    assertFalse(detector.isComplete());

    assertEquals(Long.valueOf(100), timeOf(play("U'"), CFOPStepDetector.PLL));
    assertTrue(detector.isComplete());
  }

  @Test
  public void revisesACrossClaimedByARivalFaceOnceF2lConfirmsTheRealOne() {
    // Here the cross of another face is briefly complete before the D cross is: the solve's own
    // cross lands on the second move, and only F2L can tell the two apart.
    startFrom(T_PERM, SUNE, "R U' R'", "R' F'");

    play("F");
    assertFalse(Face.D.equals(detector.getCrossFace())); // a rival face claims the cross

    play("R");
    play("R U R'"); // F2L confirms D, and the cross time reverts to D's own
    assertEquals(Face.D, detector.getCrossFace());
    assertEquals(Long.valueOf(200), detector.getStepTimestampMs(CFOPStepDetector.CROSS));
    assertEquals(Long.valueOf(500), detector.getStepTimestampMs(CFOPStepDetector.F2L));
  }

  @Test
  public void detectsTheCrossFaceTheSolveWasBuiltOn() {
    startFrom("D"); // breaks the D cross, leaves the whole U side of the cube in place
    assertEquals(Face.U, detector.getCrossFace());
    assertEquals(Long.valueOf(0), detector.getStepTimestampMs(CFOPStepDetector.F2L));

    assertEquals(Long.valueOf(100), timeOf(play("D'"), CFOPStepDetector.PLL));
    // Solving also completes the D cross, 100ms in. The confirmed face is U, so it must not count.
    assertEquals(Face.U, detector.getCrossFace());
    assertEquals(Long.valueOf(0), detector.getStepTimestampMs(CFOPStepDetector.CROSS));
  }
}
