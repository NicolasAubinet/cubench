package com.cube.nanotimer.smartcube.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import org.junit.Test;

public class StopSettleTest {

  private static final long TAP = 10_000;
  private static final boolean JUDGED = true;
  private static final boolean UNJUDGED = false;

  /** The state a solved cube is left in by a sequence, which is what a stop is judged on. */
  private static CubeState state(String moves) {
    CubieCube c = new CubieCube();
    for (String mv : moves.trim().split("\\s+")) {
      if (mv.isEmpty()) {
        continue;
      }
      Face face = Face.valueOf(mv.substring(0, 1));
      if (mv.endsWith("'")) {
        c.applyMove(face, true);
      } else if (mv.endsWith("2")) {
        c.applyMove(face, false);
        c.applyMove(face, false);
      } else {
        c.applyMove(face, false);
      }
    }
    return new CubeState(c.toFaceCube());
  }

  private static StopPenalty penalty(String movesLeftUndone) {
    return StopPenalty.of(state(movesLeftUndone));
  }

  private static StopPenalty dnf() {
    return penalty("R U"); // two turned faces: two misaligned interfaces
  }

  private static StopPenalty plusTwo() {
    return penalty("R");
  }

  @Test
  public void aPenaltyIsHeldOpenForTheWholeWindow() {
    StopSettle settle = new StopSettle();
    settle.onStop(dnf(), JUDGED, TAP);

    assertEquals(TAP + StopSettle.WINDOW_MS, settle.getVerdictOpenUntilMs());
    assertTrue(settle.acceptsReading(TAP));
    assertTrue(settle.acceptsReading(TAP + StopSettle.WINDOW_MS));
    assertFalse(settle.acceptsReading(TAP + StopSettle.WINDOW_MS + 1));
  }

  @Test
  public void theMoveThatFinishesTheSolveTakesTheVerdictAway() {
    StopSettle settle = new StopSettle();
    settle.onStop(plusTwo(), JUDGED, TAP);

    assertTrue(settle.onState(StopPenalty.none(), TAP + 300));

    assertTrue(settle.getPenalty().isNone());
    assertEquals("nothing left to wait for", 0, settle.getVerdictOpenUntilMs());
  }

  @Test
  public void aCubeReportingItselfSolvedShutsTheWindow() {
    StopSettle settle = new StopSettle();
    settle.onStop(dnf(), JUDGED, TAP);
    settle.onState(StopPenalty.none(), TAP + 300);

    assertFalse("nothing after it is the solve's", settle.acceptsReading(TAP + 301));
  }

  @Test
  public void aVerdictOnlyEverSoftens() {
    StopSettle settle = new StopSettle();
    settle.onStop(plusTwo(), JUDGED, TAP);

    assertFalse(settle.onState(dnf(), TAP + 100));

    assertTrue(settle.getPenalty().isPlusTwo());
  }

  @Test
  public void itSoftensOneStepAtATime() {
    StopSettle settle = new StopSettle();
    settle.onStop(dnf(), JUDGED, TAP);

    assertTrue(settle.onState(plusTwo(), TAP + 100));
    assertTrue(settle.getPenalty().isPlusTwo());

    assertTrue(settle.onState(StopPenalty.none(), TAP + 200));
    assertTrue(settle.getPenalty().isNone());
  }

  @Test
  public void aStateRepeatingTheVerdictChangesNothing() {
    StopSettle settle = new StopSettle();
    settle.onStop(dnf(), JUDGED, TAP);

    assertFalse(settle.onState(dnf(), TAP + 100));

    assertTrue(settle.getPenalty().isDnf());
    assertEquals(TAP + StopSettle.WINDOW_MS, settle.getVerdictOpenUntilMs());
  }

  @Test
  public void pastTheWindowTheCubeIsBeingHandled() {
    StopSettle settle = new StopSettle();
    settle.onStop(dnf(), JUDGED, TAP);

    assertFalse(settle.onState(StopPenalty.none(), TAP + StopSettle.WINDOW_MS + 1));

    assertTrue("a cube solved after the window was not solved at the tap",
        settle.getPenalty().isDnf());
  }

  /** The state is written as the packet lands, so it can be ahead of the callbacks behind the tap. */
  @Test
  public void aCubeAlreadyWhereItWasGoingStillDrainsWhatIsQueued() {
    StopSettle settle = new StopSettle();
    settle.onStop(StopPenalty.none(), JUDGED, TAP);

    assertTrue(settle.acceptsReading(TAP + StopSettle.DRAIN_MS));
    assertFalse(settle.acceptsReading(TAP + StopSettle.DRAIN_MS + 1));
    assertEquals("nothing to hold a verdict open for", 0, settle.getVerdictOpenUntilMs());
  }

  /** A practice state ends unsolved by design: no verdict, but its last moves are still its own. */
  @Test
  public void anUnjudgedStopWaitsForItsMovesAndNothingElse() {
    StopSettle settle = new StopSettle();
    settle.onStop(dnf(), UNJUDGED, TAP);

    assertTrue(settle.acceptsReading(TAP + StopSettle.WINDOW_MS));
    assertEquals("no wait is owed to a verdict nobody reads", 0, settle.getVerdictOpenUntilMs());
    assertFalse("and no state can soften one", settle.onState(StopPenalty.none(), TAP + 100));
  }

  @Test
  public void closingKeepsTheVerdictForTheHandover() {
    StopSettle settle = new StopSettle();
    settle.onStop(dnf(), JUDGED, TAP);
    settle.onState(plusTwo(), TAP + 100);

    settle.close();

    assertTrue(settle.getPenalty().isPlusTwo());
    assertEquals(0, settle.getVerdictOpenUntilMs());
    assertFalse(settle.acceptsReading(TAP + 200));
  }

  @Test
  public void theNextSolveStartsFromNothing() {
    StopSettle settle = new StopSettle();
    settle.onStop(dnf(), JUDGED, TAP);
    settle.close();

    settle.onStop(StopPenalty.none(), JUDGED, TAP + 30_000);

    assertTrue(settle.getPenalty().isNone());
  }
}
