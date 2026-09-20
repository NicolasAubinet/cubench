package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The frame a blind solve is named through, settled by the pieces it shot from. */
public class BlindFrameTest {

  private static final int UF = Cubies.slotNamed("UF"), UR = Cubies.slotNamed("UR");
  private static final int UB = Cubies.slotNamed("UB"), DF = Cubies.slotNamed("DF");
  private static final int UFR = Cubies.slotNamed("UFR"), UBR = Cubies.slotNamed("UBR");
  private static final int DFR = Cubies.slotNamed("DFR");

  private static final int[] THREE_STYLE = {UF, UFR};

  /** Both types settled leaves exactly one way to hold the cube, whatever frame was handed in. */
  @Test
  public void oneBufferOfEachTypeSettlesTheFrameOutright() {
    int frame = BlindFrame.of(new int[] {UR, UBR}, THREE_STYLE, FaceletRotations.IDENTITY);

    assertEquals(UF, heldSlotOf(frame, UR));
    assertEquals(UFR, heldSlotOf(frame, UBR));
  }

  /** Which is the whole point: the frame handed in is overruled where the buffers disagree. */
  @Test
  public void overrulesTheFrameItIsGiven() {
    for (int given = 0; given < FaceletRotations.COUNT; given++) {
      int frame = BlindFrame.of(new int[] {UR, UBR}, THREE_STYLE, given);
      assertEquals("from frame " + given, UFR, heldSlotOf(frame, UBR));
    }
  }

  /** The 2026-09-09 solve: shot from the cube's own UR and UBR, stored as a grip an x' out. */
  @Test
  public void settlesTheGripTheGyroTookMidTilt() {
    int askew = FaceletRotations.inverse(
        FaceletRotations.of(com.cube.nanotimer.smartcube.model.CubeRotation.byNotation("y x'")));

    int frame = BlindFrame.of(new int[] {UR, UBR}, THREE_STYLE, askew);

    assertEquals("y", FaceletRotations.rotationOf(FaceletRotations.inverse(frame)).getNotation());
  }

  /**
   * A solve whose buffers are the ones declared is left exactly where it was, which is what keeps
   * every solve that already reads right byte identical.
   */
  @Test
  public void leavesAFrameThatAlreadyNamesTheDeclaredBuffersAlone() {
    int frame = BlindFrame.of(new int[] {UF, UFR}, THREE_STYLE, FaceletRotations.IDENTITY);

    assertEquals(FaceletRotations.IDENTITY, frame);
  }

  /** A type nothing settled asks nothing, so a solve read no further than its edges still helps. */
  @Test
  public void settlesWhatItCanFromOneTypeAlone() {
    int given = FaceletRotations.IDENTITY;

    int frame = BlindFrame.of(new int[] {UR, BlindTargets.NO_BUFFER}, THREE_STYLE, given);

    assertEquals(UF, heldSlotOf(frame, UR));
  }

  /**
   * ⚠️ One type alone leaves two ways up and no cube state tells them apart: the whole reading
   * rotates with the frame and comes out just as consistent either way. What the solver declares is
   * what chooses between them, and an edge buffer read as {@code UF} either way is all the pieces
   * themselves promise.
   */
  @Test
  public void leavesTheWayUpToTheDeclarationWhereOnlyOneTypeWasRead() {
    int[] edgesOnly = {UR, BlindTargets.NO_BUFFER};

    int upright = BlindFrame.of(edgesOnly, THREE_STYLE, FaceletRotations.IDENTITY);
    int over = BlindFrame.of(edgesOnly, THREE_STYLE,
        FaceletRotations.of(com.cube.nanotimer.smartcube.model.CubeRotation.byNotation("x2")));

    assertEquals(UF, heldSlotOf(upright, UR));
    assertEquals(UF, heldSlotOf(over, UR));
    assertTrue("the two ways up are told apart by the frame handed in", upright != over);
  }

  /**
   * A solver who floats their buffer settles nothing of their own, and the orientation they declare
   * is the whole answer. Which is the shape it has to serve: nothing else here has an opinion.
   */
  @Test
  public void keepsTheFrameWhereNothingSettled() {
    int[] nothing = {BlindTargets.NO_BUFFER, BlindTargets.NO_BUFFER};

    assertEquals(7, BlindFrame.of(nothing, THREE_STYLE, 7));
    assertEquals(BlindTargets.UNKNOWN_FRAME,
        BlindFrame.of(nothing, THREE_STYLE, BlindTargets.UNKNOWN_FRAME));
  }

  /**
   * And where the declaration is one of the ways up a single type leaves, it is taken as given, with
   * nothing measured in between. This is the edges-only solve of 2026-09-19, whose every name came
   * out mirrored because the gyro had guessed the other one.
   */
  @Test
  public void takesTheWayUpTheSolverDeclaresWhereOnlyOneTypeWasRead() {
    int[] edgesOnly = {UR, BlindTargets.NO_BUFFER};
    int declarations = 0;

    for (int declared = 0; declared < FaceletRotations.COUNT; declared++) {
      if (heldSlotOf(declared, UR) != UF) {
        continue; // a hold this solve cannot have been in: its buffer would not be where it is
      }
      declarations++;
      assertEquals(declared, BlindFrame.of(edgesOnly, THREE_STYLE, declared));
    }
    assertEquals("an edge slot leaves two ways up", 2, declarations);
  }

  /**
   * A solve that reads both types is untouched by it, which is what keeps a declaration left at a
   * hold the solver has since changed off every solve that has an answer of its own.
   */
  @Test
  public void neverOverrulesASolveThatSettlesItsOwnFrame() {
    int askew = FaceletRotations.inverse(FaceletRotations.of(
        com.cube.nanotimer.smartcube.model.CubeRotation.byNotation("z2")));

    int frame = BlindFrame.of(new int[] {UR, UBR}, THREE_STYLE, askew);

    assertEquals(UF, heldSlotOf(frame, UR));
    assertEquals(UFR, heldSlotOf(frame, UBR));
  }

  /** And a solver who really does shoot from elsewhere is named from there. */
  @Test
  public void namesASolveThroughWhateverBuffersAreDeclared() {
    int frame = BlindFrame.of(new int[] {UR, UBR}, new int[] {DF, DFR}, FaceletRotations.IDENTITY);

    assertEquals(DF, heldSlotOf(frame, UR));
    assertEquals(DFR, heldSlotOf(frame, UBR));
  }

  /** A buffer that is not a piece is no declaration at all: nothing is asked of the frame. */
  @Test
  public void ignoresABufferItCannotName() {
    int[] unnamed = {Cubies.slotNamed("XY"), Cubies.slotNamed("XYZ")};

    assertEquals(5, BlindFrame.of(new int[] {UB, UBR}, unnamed, 5));
  }

  private static int heldSlotOf(int frame, int slot) {
    return Cubies.slotOf(
        FaceletRotations.apply(FaceletRotations.inverse(frame), Cubies.PIECES[slot][0]));
  }
}
