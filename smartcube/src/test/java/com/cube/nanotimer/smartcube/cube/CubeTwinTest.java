package com.cube.nanotimer.smartcube.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import org.junit.Test;

/** The turns a cube made without saying so, read back out of the state it reports. */
public class CubeTwinTest {

  private final CubeTwin twin = new CubeTwin();
  /** The cube in the hands, which is the one the states are taken from. */
  private final CubieCube hands = new CubieCube();

  @Test
  public void theFirstStateIsASeedAndNeverARepair() {
    turn(Face.R, false);
    turn(Face.U, true);
    assertNull("nothing to have missed before it said where it stood", twin.missing(state()));
  }

  @Test
  public void aTurnThatWasReportedIsMissingNothing() {
    twin.missing(state());
    CubeMove move = new CubeMove(Face.F, false, 0);
    twin.onMove(move);
    turn(move.getFace(), move.isPrime());
    assertNull(twin.missing(state()));
  }

  @Test
  public void aTurnThatWasNotReportedComesBackAsAState() {
    twin.missing(state());
    turn(Face.R, false); // the notification that never arrived

    CubieCube missed = twin.missing(state());
    assertNotNull(missed);

    CubieCube drawn = new CubieCube(); // a cube of its own, which took the same turns
    assertEquals(hands.toFaceCube(), drawn.multiply(missed).toFaceCube());
  }

  /** Every turn since the last state, however many of them went missing. */
  @Test
  public void severalMissedTurnsComeBackAtOnce() {
    twin.missing(state());
    turn(Face.R, false);
    turn(Face.U, true);
    turn(Face.F, false);
    assertEquals(hands.toFaceCube(), new CubieCube().multiply(twin.missing(state())).toFaceCube());
  }

  /** A repair is measured from the last state, not from the last turn that was reported. */
  @Test
  public void theStateThatRepairedIsWhereTheNextOneIsMeasuredFrom() {
    twin.missing(state());
    turn(Face.R, false);
    assertNotNull(twin.missing(state()));

    CubeMove move = new CubeMove(Face.U, false, 0);
    twin.onMove(move);
    turn(move.getFace(), move.isPrime());
    assertNull("the missed turn was already handed over", twin.missing(state()));
  }

  @Test
  public void faceletsThatWillNotParseAreDroppedRatherThanGuessedAt() {
    twin.missing(state());
    assertNull(twin.missing(new CubeState("nonsense")));
    turn(Face.R, false);
    assertNotNull("and leave the twin where it was", twin.missing(state()));
  }

  @Test
  public void aTwinThatWasResetSeedsAgain() {
    twin.missing(state());
    twin.reset();
    turn(Face.R, false);
    assertNull(twin.missing(state()));
  }

  private void turn(Face face, boolean prime) {
    hands.applyMove(face, prime);
  }

  private CubeState state() {
    return new CubeState(hands.toFaceCube());
  }
}
