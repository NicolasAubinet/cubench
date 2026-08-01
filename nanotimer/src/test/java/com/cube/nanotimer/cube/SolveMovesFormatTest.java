package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * The stored stream, and the pick-up grip that rides in front of it. The grip is not a move and has
 * no offset, so the thing to prove is that no reader of a solution has to know it is there.
 */
public class SolveMovesFormatTest {

  private static final List<CubeMove> MOVES = Arrays.asList(
      new CubeMove(Face.R, false, 1_000),
      new CubeMove(Face.U, true, 1_200),
      new CubeMove(Face.F, false, 1_450));

  @Test
  public void writesTheGripInFrontOfTheMoves() {
    assertEquals("[y] R@0 U'@200 F@450",
        SolveMovesFormat.format(MOVES, Collections.<RotationTracker.Rotation>emptyList(), 1_000,
            "y"));
  }

  @Test
  public void readsBackTheGripItWrote() {
    for (String pickup : new String[] {"y", "y x", "z'"}) {
      String stored = SolveMovesFormat.format(MOVES,
          Collections.<RotationTracker.Rotation>emptyList(), 1_000, pickup);
      assertEquals(pickup, SolveMovesFormat.pickupOf(stored));
    }
  }

  /** Every solve recorded before the grip was kept, and any cube with no gyro to read one from. */
  @Test
  public void hasNoGripToGiveForAStreamThatCarriesNone() {
    assertNull(SolveMovesFormat.pickupOf("R@0 U'@200"));
    assertNull(SolveMovesFormat.pickupOf(""));
    assertNull(SolveMovesFormat.pickupOf(null));
    assertNull(SolveMovesFormat.pickupOf("[] R@0")); // nothing between the brackets is nothing
  }

  @Test
  public void writesNoBracketsForASolveWithNoGrip() {
    String stored = SolveMovesFormat.format(MOVES,
        Collections.<RotationTracker.Rotation>emptyList(), 1_000, null);
    assertEquals("R@0 U'@200 F@450", stored);
  }

  /**
   * The grip costs a solution nothing: it has no offset, and {@link SolveMovesFormat#parse} already
   * drops what it cannot read as a move. So the display, the step split and the share text all see
   * the same solve whether or not the stream carries one.
   */
  @Test
  public void isInvisibleToEveryReaderOfTheMoves() {
    String bare = "R@0 U'@200 F@450";

    assertEquals(parsed(bare), parsed("[y x] " + bare));
  }

  /** The solution a reader walks: the notation and offset of every move, in order. */
  private static String parsed(String stored) {
    StringBuilder sb = new StringBuilder();
    for (SolveMovesFormat.Move move : SolveMovesFormat.parse(stored)) {
      sb.append(move.getNotation()).append('@').append(move.getOffsetMs()).append(' ');
    }
    return sb.toString();
  }
}
