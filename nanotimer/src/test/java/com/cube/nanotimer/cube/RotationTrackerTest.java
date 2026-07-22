package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.StillnessTracker.Window;
import org.junit.Test;

public class RotationTrackerTest {

  /** Orientations captured off a real V10: at rest, after a y, and after a further x. */
  private static final CubeOrientation REST = new CubeOrientation(0.99661, -0.08238, -0.00879, 0.00311);
  private static final CubeOrientation AFTER_Y = new CubeOrientation(0.73423, -0.00566, -0.00588, -0.67894);
  private static final CubeOrientation AFTER_Y_X = new CubeOrientation(0.48876, -0.51064, -0.51134, -0.48876);

  private static final long SCRAMBLE_DONE_MS = 5000;

  /** A gyro-frame quaternion for a turn about the cube's U axis, per the measured axis map. */
  private static CubeOrientation aboutCubeU(double degrees) {
    double half = Math.toRadians(degrees) / 2;
    // cube (w, 0, s, 0) sits in gyro axes (R=+X, U=+Z, F=−Y) as (w, 0, 0, s)
    return new CubeOrientation(Math.cos(half), 0, 0, Math.sin(half));
  }

  /** The orientation the gyro reports after turning the cube by {@code gyroDelta} from {@code from}. */
  private static CubeOrientation turnedFrom(CubeOrientation from, CubeOrientation gyroDelta) {
    return from.multiply(gyroDelta); // the gyro zero multiplies on the left, deltas on the right
  }

  private static Window still(CubeOrientation orientation, long startMs) {
    return new Window(orientation, startMs, startMs + 200);
  }

  private static Window still(CubeOrientation orientation, long startMs, long endMs) {
    return new Window(orientation, startMs, endMs);
  }

  /** A tracker that saw {@code grip} held through the scramble, which is now complete. */
  private static RotationTracker anchoredAt(CubeOrientation grip) {
    RotationTracker tracker = new RotationTracker();
    tracker.anchor(still(grip, 1000, 4000));
    tracker.scrambleComplete(SCRAMBLE_DONE_MS);
    return tracker;
  }

  /** A move made while holding the window's grip: the reading at the move is the grip itself. */
  private static void moveAt(RotationTracker tracker, Window window, long timestampMs) {
    tracker.onMove(window, window == null ? null : window.getOrientation(), timestampMs);
  }

  @Test
  public void withoutAScrambleGripNothingIsRecorded() {
    RotationTracker tracker = new RotationTracker();
    tracker.scrambleComplete(SCRAMBLE_DONE_MS);
    moveAt(tracker, still(AFTER_Y, 6000), 100);
    assertFalse(tracker.isAnchored());
    assertTrue(tracker.getRotations().isEmpty());
  }

  @Test
  public void beforeTheScrambleCompletesNothingIsRecorded() {
    RotationTracker tracker = new RotationTracker();
    tracker.anchor(still(REST, 1000, 4000));
    moveAt(tracker, still(AFTER_Y, 6000), 100);
    assertTrue(tracker.getRotations().isEmpty());
  }

  @Test
  public void holdingTheScrambleGripRecordsNoOpening() {
    RotationTracker tracker = anchoredAt(REST);
    moveAt(tracker, still(REST, 6000), 100);
    assertTrue(tracker.getRotations().isEmpty());
  }

  /** A still window is wobble-free, so one is enough: no confirmation by later moves needed. */
  @Test
  public void aSingleStillWindowResolvesTheOpening() {
    RotationTracker tracker = anchoredAt(REST);
    moveAt(tracker, still(AFTER_Y, 6000), 250);
    assertEquals(1, tracker.getRotations().size());
    assertEquals("y", tracker.getRotations().get(0).getNotation());
    assertEquals(250, tracker.getRotations().get(0).getTimestampMs());
  }

  /** The scramble's own still grip must not be mistaken for the inspection settle. */
  @Test
  public void theOpeningWaitsForAWindowAfterTheScramble() {
    RotationTracker tracker = anchoredAt(REST);
    moveAt(tracker, still(AFTER_Y, 4500), 100); // latest window still predates scramble completion
    assertTrue(tracker.getRotations().isEmpty());
    moveAt(tracker, still(AFTER_Y, 6000), 400);
    assertEquals("y", tracker.getRotations().get(0).getNotation());
    assertEquals(400, tracker.getRotations().get(0).getTimestampMs());
  }

  /** A B executed at an angle owns a still window; the grip held longest is the standard one. */
  @Test
  public void aBriefScrambleTiltLosesToTheDominantGrip() {
    RotationTracker tracker = new RotationTracker();
    tracker.anchor(still(REST, 1000, 4000));     // the scrambling grip, held 3 s
    tracker.anchor(still(AFTER_Y, 4100, 4300));  // a brief tilt near the end
    tracker.scrambleComplete(SCRAMBLE_DONE_MS);
    moveAt(tracker, still(AFTER_Y, 6000), 100);
    assertEquals(1, tracker.getRotations().size()); // measured from REST, not from the tilt
    assertEquals("y", tracker.getRotations().get(0).getNotation());
  }

  /** A window re-fed as it extends is one window at its full span, not several summed. */
  @Test
  public void anExtendedScrambleWindowCountsOnceAtItsFullSpan() {
    RotationTracker tracker = new RotationTracker();
    tracker.anchor(still(AFTER_Y, 1000, 1200)); // a tilt, re-fed as its window grows to 600 ms
    tracker.anchor(still(AFTER_Y, 1000, 1400));
    tracker.anchor(still(AFTER_Y, 1000, 1600));
    tracker.anchor(still(REST, 2000, 2700));    // the real grip, one 700 ms window
    tracker.scrambleComplete(SCRAMBLE_DONE_MS);
    moveAt(tracker, still(AFTER_Y, 6000), 100);
    assertEquals(1, tracker.getRotations().size()); // REST dominated, so the opening is the y
    assertEquals("y", tracker.getRotations().get(0).getNotation());
  }

  /** After a mid-scramble break the old grips say nothing: the anchor restarts at the pickup. */
  @Test
  public void restartAnchorDropsTheEarlierScrambleGrips() {
    RotationTracker tracker = new RotationTracker();
    tracker.anchor(still(REST, 1000, 4000)); // held long before the break
    tracker.restartAnchor();
    tracker.anchor(still(AFTER_Y, 4500, 4800)); // the grip after picking the cube back up
    tracker.scrambleComplete(SCRAMBLE_DONE_MS);
    moveAt(tracker, still(AFTER_Y, 6000), 100);
    assertTrue(tracker.getRotations().isEmpty()); // same grip as the pickup: no opening
  }

  /** The opening snaps with no tolerance: whatever grip the solve started in is the opening. */
  @Test
  public void theOpeningCommitsDespiteANoisyScrambleGrip() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(turnedFrom(grip, aboutCubeU(-25))); // hand-noised grip
    moveAt(tracker, still(turnedFrom(grip, aboutCubeU(-90)), 6000), 100); // a y: reads as 65°
    assertEquals(1, tracker.getRotations().size());
    assertEquals("y", tracker.getRotations().get(0).getNotation());
  }

  /** The "y R y R" pattern: a rotation before every single move, each one recorded. */
  @Test
  public void aRotationBeforeEveryMoveIsRecordedEachTime() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    CubeOrientation orientation = grip;
    for (int i = 0; i < 4; i++) {
      orientation = turnedFrom(orientation, aboutCubeU(-90));
      moveAt(tracker, still(orientation, 6000 + 500 * i), 100 * (i + 1));
    }
    assertEquals(4, tracker.getRotations().size());
    for (RotationTracker.Rotation rotation : tracker.getRotations()) {
      assertEquals("y", rotation.getNotation());
    }
  }

  /** A tilt that came and went between moves never reaches a move, so nothing is recorded. */
  @Test
  public void aPeekWithNoMoveInItVanishes() {
    RotationTracker tracker = anchoredAt(REST);
    moveAt(tracker, still(REST, 6000), 100);
    // the solver peeked (a tilted window at 7000) and settled back before the next move
    moveAt(tracker, still(REST, 8000), 300);
    assertTrue(tracker.getRotations().isEmpty());
  }

  /** The phantom-x' fix: an OLL read at ~50° matches nothing and must not become a rotation. */
  @Test
  public void aSustainedAmbiguousTiltIsNotARotation() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    moveAt(tracker, still(grip, 6000), 100);
    moveAt(tracker, still(turnedFrom(grip, aboutCubeU(-50)), 7000), 200); // held tilted through OLL
    moveAt(tracker, still(turnedFrom(grip, aboutCubeU(-50)), 7000), 300);
    moveAt(tracker, still(grip, 8000), 400); // straightened back for PLL
    assertTrue(tracker.getRotations().isEmpty());
    moveAt(tracker, still(turnedFrom(grip, aboutCubeU(-90)), 9000), 500); // a real y still lands
    assertEquals(1, tracker.getRotations().size());
    assertEquals("y", tracker.getRotations().get(0).getNotation());
  }

  /**
   * The section-boundary regression from a real test solve: a slow rotation eased through a
   * briefly-steady angle, minting a window mid-way. By the move the cube was well past it, and
   * committing it mis-spelt the composite; the move-time reading must veto such a window.
   */
  @Test
  public void aWindowTheCubeHasMovedAwayFromIsVetoed() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    moveAt(tracker, still(grip, 6000), 100);
    CubeOrientation midRotation = turnedFrom(grip, aboutCubeU(-25)); // early in a slow y
    CubeOrientation settled = turnedFrom(grip, aboutCubeU(-90));
    tracker.onMove(still(midRotation, 7000), settled, 200); // moved on: the window is stale
    assertTrue(tracker.getRotations().isEmpty());
    moveAt(tracker, still(settled, 8000), 300); // the settled grip commits the real rotation
    assertEquals(1, tracker.getRotations().size());
    assertEquals("y", tracker.getRotations().get(0).getNotation());
  }

  /**
   * The regression that poisoned a real solve: a rotation crept back ~10° a window, each step
   * reading as noise. The reference must stay frozen between commits so the way back adds up.
   */
  @Test
  public void aSlowCreepBackIsStillRecorded() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    moveAt(tracker, still(grip, 6000), 100);
    CubeOrientation orientation = turnedFrom(grip, aboutCubeU(-90));
    moveAt(tracker, still(orientation, 7000), 200); // a y
    for (int i = 1; i <= 9; i++) { // back to the grip in 10° steps
      moveAt(tracker, still(turnedFrom(orientation, aboutCubeU(10 * i)), 7000 + 500 * i), 200 + 100 * i);
    }
    assertEquals(2, tracker.getRotations().size());
    assertEquals("y", tracker.getRotations().get(0).getNotation());
    assertEquals("y'", tracker.getRotations().get(1).getNotation());
  }

  /**
   * The 34.38s regression: the V10's yaw fusion snapped 180° mid-burst, twice, and each snap
   * was recorded as a y2 the solver never made. A half turn surfacing while moves were being
   * made re-zeroes the reference silently instead of recording.
   */
  @Test
  public void aHalfTurnSurfacingMidBurstIsAYawSnapNotARotation() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    moveAt(tracker, still(grip, 6000), 100);
    CubeOrientation snapped = turnedFrom(grip, aboutCubeU(-180));
    // mid-alg moves already reading at the snapped orientation, window not yet formed
    tracker.onMove(still(grip, 6000), snapped, 200);
    tracker.onMove(still(grip, 6000), snapped, 300);
    moveAt(tracker, still(snapped, 8000), 400); // the snapped grip's window: a would-be y2
    assertTrue(tracker.getRotations().isEmpty());
    // the reference re-zeroed on the snap, so a real rotation after it still reads right
    moveAt(tracker, still(turnedFrom(snapped, aboutCubeU(-90)), 9000), 500);
    assertEquals(1, tracker.getRotations().size());
    assertEquals("y", tracker.getRotations().get(0).getNotation());
  }

  /** A real y2 is made in a pause: no moves in transit, so it still commits. */
  @Test
  public void aHalfTurnAfterAPauseIsARealRotation() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    moveAt(tracker, still(grip, 6000), 100);
    moveAt(tracker, still(turnedFrom(grip, aboutCubeU(-180)), 8000), 300);
    assertEquals(1, tracker.getRotations().size());
    assertEquals("y2", tracker.getRotations().get(0).getNotation());
  }

  /** The window that committed a rotation must not commit it again at the next move. */
  @Test
  public void aStaleWindowNeverRecommits() {
    RotationTracker tracker = anchoredAt(REST);
    moveAt(tracker, still(AFTER_Y, 6000), 100);
    moveAt(tracker, still(AFTER_Y, 6000), 200);
    moveAt(tracker, still(AFTER_Y, 6000), 300);
    assertEquals(1, tracker.getRotations().size());
  }

  /** Several rotations with no move between them are the composite the solver effectively did. */
  @Test
  public void rotationsBetweenTwoMovesCollapseIntoOne() {
    RotationTracker tracker = anchoredAt(REST);
    moveAt(tracker, still(REST, 6000), 100);
    moveAt(tracker, still(AFTER_Y_X, 7000), 400); // both rotations happened before this move
    assertEquals(1, tracker.getRotations().size());
    assertEquals("y x", tracker.getRotations().get(0).getNotation());
    assertEquals(400, tracker.getRotations().get(0).getTimestampMs());
  }

  @Test
  public void aRotationMidSolveIsRecordedInTheCubesFrame() {
    RotationTracker tracker = anchoredAt(REST);
    moveAt(tracker, still(AFTER_Y, 6000), 100);   // opening rotation
    moveAt(tracker, still(AFTER_Y_X, 7000), 300); // then a second rotation
    assertEquals(2, tracker.getRotations().size());
    // A physical x on the y-rotated cube: the cube's own z', which display maps back to an x.
    assertEquals("z'", tracker.getRotations().get(1).getNotation());
    assertEquals(300, tracker.getRotations().get(1).getTimestampMs());
  }

  @Test
  public void resetDropsTheScrambleGripAndTheRotations() {
    RotationTracker tracker = anchoredAt(REST);
    moveAt(tracker, still(AFTER_Y, 6000), 100);
    tracker.reset();
    assertFalse(tracker.isAnchored());
    assertTrue(tracker.getRotations().isEmpty());
    tracker.anchor(still(REST, 8000, 8500)); // and anchoring starts over from scratch
    assertTrue(tracker.isAnchored());
    moveAt(tracker, still(AFTER_Y, 9000), 200); // but records nothing until the scramble completes
    assertTrue(tracker.getRotations().isEmpty());
  }

  /** A missing window or reading must not be mistaken for anything, nor crash. */
  @Test
  public void aMissingWindowOrReadingIsIgnored() {
    RotationTracker tracker = anchoredAt(REST);
    tracker.anchor(null);
    moveAt(tracker, null, 100);
    assertTrue(tracker.getRotations().isEmpty());
    tracker.onMove(still(AFTER_Y, 6000), null, 200); // no reading at the move: trust the window
    moveAt(tracker, null, 250);
    assertEquals(1, tracker.getRotations().size());
    assertEquals("y", tracker.getRotations().get(0).getNotation());
    assertEquals(200, tracker.getRotations().get(0).getTimestampMs());
  }
}
