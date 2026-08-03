package com.cube.nanotimer.cube;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Guards the instrument, not the product: {@link DisplayedSolutionReplay} decides whether a
 * reconstruction solves, so it has to answer known cases first.
 *
 * <p>Half of these hold the cube square and half do not, which is the point. The first version
 * conjugated a slice's spin twice — invisible under the identity, so six upright controls passed
 * and it then reported that a real solve did not solve.
 */
public class DisplayedSolutionReplayTest {

  @Test
  public void followsAPlainTurn() {
    assertTrue(DisplayedSolutionReplay.solves("R", "R'"));
    assertFalse(DisplayedSolutionReplay.solves("R", "R"));
  }

  /** After a y the cube's R is on the solver's front, so undoing an R is a turn they write F'. */
  @Test
  public void readsFaceLettersThroughARotation() {
    assertTrue(DisplayedSolutionReplay.solves("R", "y F'"));
    assertFalse(DisplayedSolutionReplay.solves("R", "y R'"));
  }

  /** {@code R L'} leaves the cube where {@code M} does, so {@code M'} puts it back. */
  @Test
  public void followsASlice() {
    assertTrue(DisplayedSolutionReplay.solves("R L'", "M'"));
  }

  /** {@code y2} swaps left and right, and {@code M} follows the left, so the unprimed one solves. */
  @Test
  public void followsASliceThroughAFrame() {
    assertTrue(DisplayedSolutionReplay.solves("R L'", "y2 M"));
    assertFalse(DisplayedSolutionReplay.solves("R L'", "y2 M'"));
  }

  /** A wide turns its far layer, so an {@code r} undoes an {@code L'} whatever else it does. */
  @Test
  public void followsAWide() {
    assertTrue(DisplayedSolutionReplay.solves("L'", "r"));
    assertTrue(DisplayedSolutionReplay.solves("R", "r r' R'"));
  }

  /** After an {@code r} the core has carried the cube's U to the back, so undoing U' reads as B. */
  @Test
  public void followsAWideAndTheFrameItCarries() {
    assertTrue(DisplayedSolutionReplay.solves("U' L'", "r B"));
    assertFalse(DisplayedSolutionReplay.solves("U' L'", "r U"));
  }

  /** A wide read in a frame of its own: after a y the solver's f is the cube's r. */
  @Test
  public void followsAWideThroughAFrame() {
    assertTrue(DisplayedSolutionReplay.solves("L'", "y f"));
    assertFalse(DisplayedSolutionReplay.solves("L'", "y r"));
  }
}
