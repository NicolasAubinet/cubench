package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class RotationTrackerTest {

  /** Orientations captured off a real V10: at rest, after a y, and after a further x. */
  private static final CubeOrientation REST = new CubeOrientation(0.99661, -0.08238, -0.00879, 0.00311);
  private static final CubeOrientation AFTER_Y = new CubeOrientation(0.73423, -0.00566, -0.00588, -0.67894);
  private static final CubeOrientation AFTER_Y_X = new CubeOrientation(0.48876, -0.51064, -0.51134, -0.48876);

  /** A gyro-frame quaternion for a turn about the cube's U axis, per the measured axis map. */
  private static CubeOrientation aboutCubeU(double degrees) {
    double half = Math.toRadians(degrees) / 2;
    // cube (w, 0, s, 0) sits in gyro axes (R=+X, U=+Z, F=−Y) as (w, 0, 0, s)
    return new CubeOrientation(Math.cos(half), 0, 0, Math.sin(half));
  }

  /** A gyro-frame quaternion for a turn about the cube's R axis, per the measured axis map. */
  private static CubeOrientation aboutCubeR(double degrees) {
    double half = Math.toRadians(degrees) / 2;
    // cube (w, s, 0, 0) sits in gyro axes (R=+X, U=+Z, F=−Y) as (w, s, 0, 0)
    return new CubeOrientation(Math.cos(half), Math.sin(half), 0, 0);
  }

  /** The orientation the gyro reports after turning the cube by {@code gyroDelta} from {@code from}. */
  private static CubeOrientation turnedFrom(CubeOrientation from, CubeOrientation gyroDelta) {
    return from.multiply(gyroDelta); // the gyro zero multiplies on the left, deltas on the right
  }

  /** A tracker whose reference is {@code grip}: the cube read that way at the first scramble move. */
  private static RotationTracker anchoredAt(CubeOrientation grip) {
    RotationTracker tracker = new RotationTracker();
    tracker.anchor(grip);
    return tracker;
  }

  @Test
  public void withoutAReferenceNothingIsRecorded() {
    RotationTracker tracker = new RotationTracker();
    tracker.onMove(AFTER_Y, 100);
    assertTrue(tracker.getRotations().isEmpty());
  }

  @Test
  public void holdingTheGripTheScrambleBeganInRecordsNothing() {
    RotationTracker tracker = anchoredAt(REST);
    tracker.onMove(REST, 100);
    assertTrue(tracker.getRotations().isEmpty());
  }

  /** The opening is the plain difference from the grip the scramble was begun in. */
  @Test
  public void theOpeningIsTheDeltaFromTheGripTheScrambleBeganIn() {
    RotationTracker tracker = anchoredAt(REST);
    tracker.onMove(AFTER_Y, 250);
    assertEquals(1, tracker.getRotations().size());
    assertEquals("y", tracker.getRotations().get(0).getNotation());
    assertEquals(250, tracker.getRotations().get(0).getTimestampMs());
  }

  /**
   * The first scramble move is the only grip whose label can be known, because it is the one the
   * solver can be asked for. Turned two moves in and left there, the solve is a {@code y} away from
   * where it started, and that is what has to be recorded.
   */
  @Test
  public void theReferenceIsTheFirstScrambleMoveAndNotTheLast() {
    RotationTracker tracker = anchoredAt(REST);
    tracker.anchor(AFTER_Y); // the cube turned partway through the scramble and stayed there
    tracker.onMove(AFTER_Y, 100);
    assertEquals(1, tracker.getRotations().size());
    assertEquals("y", tracker.getRotations().get(0).getNotation());
  }

  /** After a mid-scramble break the cube can come back any way up: the reference restarts. */
  @Test
  public void restartAnchorRepinsOnTheNextScrambleMove() {
    RotationTracker tracker = anchoredAt(REST);
    tracker.restartAnchor();
    tracker.anchor(AFTER_Y); // the grip the scramble was picked back up in
    tracker.onMove(AFTER_Y, 100);
    assertTrue(tracker.getRotations().isEmpty());
  }

  /** The "y R y R" pattern: a rotation before every single move, each one recorded. */
  @Test
  public void aRotationBeforeEveryMoveIsRecordedEachTime() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    CubeOrientation reading = grip;
    for (int i = 0; i < 4; i++) {
      reading = turnedFrom(reading, aboutCubeU(-90));
      tracker.onMove(reading, 100 * (i + 1));
    }
    assertEquals(4, tracker.getRotations().size());
    for (RotationTracker.Rotation rotation : tracker.getRotations()) {
      assertEquals("y", rotation.getNotation()); // each is the step from the frame before it
    }
  }

  /**
   * A frame read per move has no memory to be poisoned, so a rotation taken back is simply a second
   * rotation. Requiring a later move to confirm the first was measured: the frame fell 78% to 66%.
   */
  @Test
  public void aRotationTheCubeComesBackFromIsRecordedBothWays() {
    RotationTracker tracker = anchoredAt(REST);
    tracker.onMove(AFTER_Y, 100);
    tracker.onMove(REST, 300);
    assertEquals(Arrays.asList("y@100", "y'@300"), tokens(tracker.getRotations()));
  }

  /** Short of the halfway point between two grips the cube is still in the one it started in. */
  @Test
  public void aWobbleShortOfHalfAQuarterTurnIsNotARotation() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    tracker.onMove(turnedFrom(grip, aboutCubeU(-40)), 100);
    assertTrue(tracker.getRotations().isEmpty());
  }

  /**
   * The 23.50s regression: differencing two tilted grips puts <em>both</em> tilts into the rotation
   * between them. Gravity takes each reading's tilt out before the two are ever compared.
   */
  @Test
  public void aRegripBetweenTiltedGripsIsStillAQuarterTurn() {
    CubeOrientation square = new CubeOrientation(1, 0, 0, 0);
    CubeOrientation held = turnedFrom(square, aboutCubeR(25)); // the solver's own tilt
    RotationTracker tracker = anchoredAt(held);
    tracker.onMove(held, 100);
    tracker.onMove(turnedFrom(turnedFrom(square, aboutCubeU(-90)), aboutCubeR(-25)), 300);
    assertEquals(Arrays.asList("y@300"), tokens(tracker.getRotations()));
  }

  /** Several rotations with no move between them are the composite the solver effectively did. */
  @Test
  public void rotationsBetweenTwoMovesCollapseIntoOne() {
    RotationTracker tracker = anchoredAt(REST);
    tracker.onMove(REST, 100);
    tracker.onMove(AFTER_Y_X, 400);
    assertEquals(Arrays.asList("y x@400"), tokens(tracker.getRotations()));
  }

  /** A rotation is written in the frame the cube is already in, since that is how a replay reads it. */
  @Test
  public void aRotationIsWrittenInTheFrameTheCubeIsAlreadyIn() {
    RotationTracker tracker = anchoredAt(REST);
    tracker.onMove(AFTER_Y, 100);
    tracker.onMove(AFTER_Y_X, 300);
    // A physical x on the y-rotated cube: the cube's own z', which display maps back to an x.
    assertEquals(Arrays.asList("y@100", "z'@300"), tokens(tracker.getRotations()));
  }

  /**
   * The gyro rides the core, so a slice turns the frame exactly as a regrip does. Folding the spins
   * in first writes that turning once, as the slice's own spin, not again as a regrip.
   */
  @Test
  public void aFrameTurnedByACoreSpinIsNotWrittenDownTwice() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    tracker.onMove(grip, 1000);
    tracker.onMove(turnedFrom(grip, aboutCubeR(-90)), 1200); // the core has rocked an x under it
    assertEquals(Arrays.asList("x@1031"), tokens(tracker.getRotations(spins("x", 1031))));
  }

  /** What the spins do not account for is the solver's own turning, and is written down. */
  @Test
  public void whatTheSpinsDoNotAccountForIsARegrip() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    tracker.onMove(grip, 1000);
    tracker.onMove(turnedFrom(grip, aboutCubeR(-180)), 1200); // half a turn, of which one rock
    assertEquals(Arrays.asList("x@1031", "x@1200"), tokens(tracker.getRotations(spins("x", 1031))));
  }

  /**
   * Both faces of a pair are reported while the core is still turning, so the frame read at either
   * is part-rocked. Writing that would print a slice as {@code x M' x'} — and split the pair, which
   * makes the display fold give up and show the raw {@code L R'} instead of the {@code M}.
   */
  @Test
  public void aFrameReadMidRockIsNotTheSolverRegripping() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    tracker.onMove(grip, 1000); // the pair's first face
    tracker.onMove(turnedFrom(grip, aboutCubeR(-90)), 1030); // its second, the rock already read
    tracker.onMove(turnedFrom(grip, aboutCubeR(-90)), 1200);
    assertEquals(Arrays.asList("x@1031"), tokens(tracker.getRotations(spins("x", 1031, 1000))));
  }

  /** Even a frame the rock cannot explain is mid-rock, not a regrip: writing it splits the pair. */
  @Test
  public void aFrameTheRockCannotExplainIsStillNotARegripInsideThePair() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    tracker.onMove(grip, 1000); // the pair's first face
    tracker.onMove(turnedFrom(grip, aboutCubeR(-180)), 1030); // its second, read a half turn out
    assertEquals(Arrays.asList("x@1031"), tokens(tracker.getRotations(spins("x", 1031, 1000))));
  }

  /** Only the pair is forgiven: the solver's own turning, once past it, is written down. */
  @Test
  public void aRegripAfterASliceIsStillRecorded() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    tracker.onMove(grip, 1000);
    tracker.onMove(turnedFrom(grip, aboutCubeR(-90)), 1030); // the pair, mid-rock: nothing written
    CubeOrientation regripped =
        turnedFrom(turnedFrom(grip, aboutCubeR(-90)), aboutCubeU(-90)); // and then a real y
    tracker.onMove(regripped, 1400);
    assertEquals(Arrays.asList("x@1031", "y@1400"),
        tokens(tracker.getRotations(spins("x", 1031, 1000))));
  }

  /** A Roux solve usually ends on an M2: the last rock has no move after it to be read against. */
  @Test
  public void aSpinAfterTheLastMoveIsStillKept() {
    RotationTracker tracker = anchoredAt(REST);
    tracker.onMove(REST, 1000);
    assertEquals(Arrays.asList("x@1031"), tokens(tracker.getRotations(spins("x", 1031))));
  }

  /** Without a reading there is nothing to read a frame from, so the last one stands. */
  @Test
  public void aMissingReadingLeavesTheFrameAsItWas() {
    RotationTracker tracker = anchoredAt(REST);
    tracker.onMove(AFTER_Y, 100);
    tracker.onMove(null, 200);
    assertEquals(Arrays.asList("y@100"), tokens(tracker.getRotations()));
  }

  /** With nothing turned inside a pair, the pick-up is the frame of the very first move. */
  @Test
  public void thePickupIsTheFrameTheSolveOpenedIn() {
    RotationTracker tracker = anchoredAt(REST);
    tracker.onMove(AFTER_Y, 1000);
    tracker.onMove(AFTER_Y, 1400);

    assertEquals("y", tracker.getPickupRotation(noPairs()).getNotation());
  }

  /**
   * A solve can open on a slice — one captured blind solve opens on two faces 3 ms apart — and the
   * reading there is mid-rock, landing the frame a quarter turn out. Every blind target is then
   * spelled through a grip the solver never held, and nothing about the names looks wrong. So the
   * pick-up is read past the pair, at the first move whose frame the core was not still turning
   * under.
   */
  @Test
  public void thePickupIsNotReadInsideASlicePair() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    tracker.onMove(turnedFrom(grip, aboutCubeR(-50)), 1000); // the pair's first face, mid-rock
    tracker.onMove(turnedFrom(grip, aboutCubeR(-90)), 1003); // its second, the rock nearly done
    tracker.onMove(turnedFrom(grip, aboutCubeU(-90)), 1400); // settled: the grip it was picked up in

    assertEquals("x", tracker.getPickupRotation(noPairs()).getNotation()); // read at the pair
    assertEquals("y", tracker.getPickupRotation(pairs(1000, 1003)).getNotation());
  }

  /** Asked before the solve has turned anything the pair does not cover, the best there is stands. */
  @Test
  public void thePickupInsideAPairStandsUntilAMoveComesOutsideOne() {
    CubeOrientation grip = new CubeOrientation(1, 0, 0, 0);
    RotationTracker tracker = anchoredAt(grip);
    tracker.onMove(turnedFrom(grip, aboutCubeR(-90)), 1000);
    tracker.onMove(turnedFrom(grip, aboutCubeR(-90)), 1003);

    assertEquals("x", tracker.getPickupRotation(pairs(1000, 1003)).getNotation());
  }

  @Test
  public void withoutAMoveThereIsNoPickup() {
    assertEquals(null, anchoredAt(REST).getPickupRotation(noPairs()));
  }

  @Test
  public void resetDropsTheReferenceAndTheFrames() {
    RotationTracker tracker = anchoredAt(REST);
    tracker.onMove(AFTER_Y, 100);
    tracker.reset();
    assertTrue(tracker.getRotations().isEmpty());
    tracker.onMove(AFTER_Y, 200); // and with no reference nothing is read until one is anchored
    assertTrue(tracker.getRotations().isEmpty());
  }

  private static List<RotationTracker.Rotation> noPairs() {
    return new ArrayList<RotationTracker.Rotation>();
  }

  /** A pair of faces as {@link SliceSpinDetector#possiblePairs} reports one, by its shape alone. */
  private static List<RotationTracker.Rotation> pairs(long fromMs, long toMs) {
    return spins("x", toMs, fromMs);
  }

  private static List<RotationTracker.Rotation> spins(String notation, long timestampMs) {
    return new ArrayList<RotationTracker.Rotation>(
        Arrays.asList(new RotationTracker.Rotation(notation, timestampMs)));
  }

  /** A spin that knows the pair it was measured across, as {@link SliceSpinDetector} reports it. */
  private static List<RotationTracker.Rotation> spins(String notation, long timestampMs,
      long pairFromMs) {
    return new ArrayList<RotationTracker.Rotation>(
        Arrays.asList(new RotationTracker.Rotation(notation, timestampMs, pairFromMs)));
  }

  private static List<String> tokens(List<RotationTracker.Rotation> rotations) {
    List<String> tokens = new ArrayList<String>();
    for (RotationTracker.Rotation rotation : rotations) {
      tokens.add(rotation.getNotation() + "@" + rotation.getTimestampMs());
    }
    return tokens;
  }
}
