package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The frame a blind solve is named through, settled by the pieces it shot from. */
public class BlindFrameTest {

  private static final int UF = Cubies.slotNamed("UF"), UR = Cubies.slotNamed("UR");
  private static final int UB = Cubies.slotNamed("UB"), DF = Cubies.slotNamed("DF");
  private static final int UFR = Cubies.slotNamed("UFR"), UBR = Cubies.slotNamed("UBR");
  private static final int DFR = Cubies.slotNamed("DFR");

  private static final int[] THREE_STYLE = {UF, UFR};

  /** A solve nothing is known about the turning of, which is all the frame ever asked of before. */
  private static final int[] NOTHING_TURNED = new int[6];

  /** A solver none of whose solves has settled a grip yet. */
  private static final int NO_HABIT = BlindTargets.UNKNOWN_FRAME;

  /** Both types settled leaves exactly one way to hold the cube, whatever frame was handed in. */
  @Test
  public void oneBufferOfEachTypeSettlesTheFrameOutright() {
    int frame = BlindFrame.of(
        new int[] {UR, UBR}, THREE_STYLE, NO_HABIT, FaceletRotations.IDENTITY, NOTHING_TURNED);

    assertEquals(UF, heldSlotOf(frame, UR));
    assertEquals(UFR, heldSlotOf(frame, UBR));
  }

  /** Which is the whole point: the frame handed in is overruled where the buffers disagree. */
  @Test
  public void overrulesTheFrameItIsGiven() {
    for (int given = 0; given < FaceletRotations.COUNT; given++) {
      int frame = BlindFrame.of(new int[] {UR, UBR}, THREE_STYLE, NO_HABIT, given, NOTHING_TURNED);
      assertEquals("from frame " + given, UFR, heldSlotOf(frame, UBR));
    }
  }

  /** The 2026-09-09 solve: shot from the cube's own UR and UBR, stored as a grip an x' out. */
  @Test
  public void settlesTheGripTheGyroTookMidTilt() {
    int askew = FaceletRotations.inverse(
        FaceletRotations.of(com.cube.nanotimer.smartcube.model.CubeRotation.byNotation("y x'")));

    int frame = BlindFrame.of(new int[] {UR, UBR}, THREE_STYLE, NO_HABIT, askew, NOTHING_TURNED);

    assertEquals("y", FaceletRotations.rotationOf(FaceletRotations.inverse(frame)).getNotation());
  }

  /**
   * A solve whose buffers are the ones declared is left exactly where it was, which is what keeps
   * every solve that already reads right byte identical.
   */
  @Test
  public void leavesAFrameThatAlreadyNamesTheDeclaredBuffersAlone() {
    int frame = BlindFrame.of(
        new int[] {UF, UFR}, THREE_STYLE, NO_HABIT, FaceletRotations.IDENTITY, NOTHING_TURNED);

    assertEquals(FaceletRotations.IDENTITY, frame);
  }

  /** A type nothing settled asks nothing, so a solve read no further than its edges still helps. */
  @Test
  public void settlesWhatItCanFromOneTypeAlone() {
    int given = FaceletRotations.IDENTITY;

    int frame = BlindFrame.of(
        new int[] {UR, BlindTargets.NO_BUFFER}, THREE_STYLE, NO_HABIT, given, NOTHING_TURNED);

    assertEquals(UF, heldSlotOf(frame, UR));
  }

  /**
   * One type alone leaves two ways up and no cube state tells them apart — the reading rotates with
   * the frame and comes out just as consistent either way. The turning tells them apart: whichever
   * of the two puts the quiet axis at front and back is the one the solve was held in, and it is
   * taken whatever the gyro made of the pick-up.
   */
  @Test
  public void readsTheWayUpOffTheTurningWhereOnlyOneTypeWasRead() {
    int[] edgesOnly = {UR, BlindTargets.NO_BUFFER};
    List<Integer> waysUp = waysUp(UR, UF);
    assertEquals(2, waysUp.size());

    for (int wayUp : waysUp) {
      for (int given = 0; given < FaceletRotations.COUNT; given++) {
        assertEquals("from frame " + given, wayUp,
            BlindFrame.of(edgesOnly, THREE_STYLE, NO_HABIT, given, turnedEverywhereBut(wayUp)));
      }
    }
  }

  /**
   * The solver's own habit outranks both: a grip is a fact about them, so a solve that can settle
   * only half of one is read through the frame their whole solves settled on. This is what stops a
   * part-solve being a different reading from a normal one.
   */
  @Test
  public void takesTheWayUpFromTheSolversHabitWhereOnlyOneTypeWasRead() {
    int[] edgesOnly = {UR, BlindTargets.NO_BUFFER};
    List<Integer> waysUp = waysUp(UR, UF);

    for (int habit : waysUp) {
      // The turning is pointed at the other way up, and the gyro at every way up in turn.
      int[] against = turnedEverywhereBut(waysUp.get(0) == habit ? waysUp.get(1) : waysUp.get(0));
      for (int given = 0; given < FaceletRotations.COUNT; given++) {
        assertEquals("from frame " + given, habit,
            BlindFrame.of(edgesOnly, THREE_STYLE, habit, given, against));
      }
    }
  }

  /** A habit that is not one of the ways up says nothing: a solver who has changed their hold. */
  @Test
  public void ignoresAHabitTheSolveCannotHaveBeenHeldIn() {
    int[] edgesOnly = {UR, BlindTargets.NO_BUFFER};
    int wayUp = waysUp(UR, UF).get(0);
    int impossible = waysUp(UR, UB).get(0); // names the shot slot UB, not what was declared

    assertEquals(wayUp, BlindFrame.of(edgesOnly, THREE_STYLE, impossible,
        FaceletRotations.IDENTITY, turnedEverywhereBut(wayUp)));
  }

  /**
   * And a solve that reads both types is untouched by it, which is the point: the habit is only ever
   * the answer their own whole solves gave, so it can never contradict one.
   */
  @Test
  public void neverOverrulesASolveThatSettlesItsOwnFrame() {
    int wrongHabit = waysUp(UR, UB).get(0);

    int frame = BlindFrame.of(new int[] {UR, UBR}, THREE_STYLE, wrongHabit,
        FaceletRotations.IDENTITY, NOTHING_TURNED);

    assertEquals(UF, heldSlotOf(frame, UR));
    assertEquals(UFR, heldSlotOf(frame, UBR));
  }

  /** Which is also the only shape worth learning a habit from. */
  @Test
  public void saysOnlyASolveThatPinnedItsOwnFrameIsWorthLearningFrom() {
    assertTrue(BlindFrame.settles(new int[] {UR, UBR}, THREE_STYLE));
    assertFalse(BlindFrame.settles(new int[] {UR, BlindTargets.NO_BUFFER}, THREE_STYLE));
    assertFalse(BlindFrame.settles(
        new int[] {BlindTargets.NO_BUFFER, BlindTargets.NO_BUFFER}, THREE_STYLE));
  }

  /** ⚠️ Habit silent and the turning unable to choose: the way up is the gyro's guess as before. */
  @Test
  public void leavesTheWayUpToTheGyroWhereTheTurningCannotChoose() {
    int[] edgesOnly = {UR, BlindTargets.NO_BUFFER};

    int upright = BlindFrame.of(
        edgesOnly, THREE_STYLE, NO_HABIT, FaceletRotations.IDENTITY, NOTHING_TURNED);
    int over = BlindFrame.of(edgesOnly, THREE_STYLE, NO_HABIT,
        FaceletRotations.of(com.cube.nanotimer.smartcube.model.CubeRotation.byNotation("x2")),
        NOTHING_TURNED);

    assertEquals(UF, heldSlotOf(upright, UR));
    assertEquals(UF, heldSlotOf(over, UR));
    assertTrue("the two ways up are told apart by the frame handed in", upright != over);
  }

  /** A solver who floats their buffer settles nothing, and the gyro's answer stands. */
  @Test
  public void keepsTheFrameWhereNothingSettled() {
    int[] nothing = {BlindTargets.NO_BUFFER, BlindTargets.NO_BUFFER};

    assertEquals(7, BlindFrame.of(nothing, THREE_STYLE, NO_HABIT, 7, NOTHING_TURNED));
    assertEquals(BlindTargets.UNKNOWN_FRAME,
        BlindFrame.of(nothing, THREE_STYLE, NO_HABIT, BlindTargets.UNKNOWN_FRAME, NOTHING_TURNED));
  }

  /** And a solver who really does shoot from elsewhere is named from there. */
  @Test
  public void namesASolveThroughWhateverBuffersAreDeclared() {
    int frame = BlindFrame.of(new int[] {UR, UBR}, new int[] {DF, DFR}, NO_HABIT,
        FaceletRotations.IDENTITY, NOTHING_TURNED);

    assertEquals(DF, heldSlotOf(frame, UR));
    assertEquals(DFR, heldSlotOf(frame, UBR));
  }

  /** A buffer that is not a piece is no declaration at all: nothing is asked of the frame. */
  @Test
  public void ignoresABufferItCannotName() {
    int[] unnamed = {Cubies.slotNamed("XY"), Cubies.slotNamed("XYZ")};

    assertEquals(5, BlindFrame.of(new int[] {UB, UBR}, unnamed, NO_HABIT, 5, NOTHING_TURNED));
  }

  /** Every way of holding the cube that puts the slot a type shot from at the one declared. */
  private static List<Integer> waysUp(int shot, int declared) {
    List<Integer> ways = new ArrayList<Integer>();
    for (int frame = 0; frame < FaceletRotations.COUNT; frame++) {
      if (heldSlotOf(frame, shot) == declared) {
        ways.add(frame);
      }
    }
    return ways;
  }

  /** A solve that turned every face but the two a frame calls front and back. */
  private static int[] turnedEverywhereBut(int frame) {
    int[] turns = new int[6];
    Arrays.fill(turns, 10);
    turns[FaceletRotations.face(frame, Cubies.FACES.indexOf('F'))] = 0;
    turns[FaceletRotations.face(frame, Cubies.FACES.indexOf('B'))] = 0;
    return turns;
  }

  private static int heldSlotOf(int frame, int slot) {
    return Cubies.slotOf(
        FaceletRotations.apply(FaceletRotations.inverse(frame), Cubies.PIECES[slot][0]));
  }
}
