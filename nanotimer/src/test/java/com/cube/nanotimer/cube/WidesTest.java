package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeRotation;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Derives the wide table rather than restating it, from one more primitive fact: which whole-cube
 * rotation a face turn <em>is</em> ({@code U} sweeps its layer the way {@code y} sweeps the cube).
 * Both halves of every row then fall out, so nothing here asserts a pair by hand.
 */
public class WidesTest {

  /** The whole-cube rotation a face turn sweeps its own layer by. The one input to all of this. */
  private static final Map<String, String> AS_ROTATION = asRotation();

  private static Map<String, String> asRotation() {
    Map<String, String> rotations = new HashMap<String, String>();
    rotations.put("U", "y");
    rotations.put("D", "y'");
    rotations.put("R", "x");
    rotations.put("L", "x'");
    rotations.put("F", "z");
    rotations.put("B", "z'");
    return rotations;
  }

  /** The far layer stands still, so the reported face undoes the core swing exactly. */
  @Test
  public void theReportedFaceUndoesTheCoreSwing() {
    for (String face : everyFaceTurn()) {
      CubeRotation reported = rotationOf(face);
      CubeRotation spin = CubeRotation.byNotation(Wides.spinFor(face));
      assertTrue(face + " with spin " + spin + " does not leave the far layer still",
          reported.then(spin).isIdentity());
    }
  }

  /** A wide is named for the layer the core swung; the cube, feeling the far one, says the other. */
  @Test
  public void theWideIsNamedForTheLayerTheCoreSwungWith() {
    for (String face : everyFaceTurn()) {
      String spin = Wides.spinFor(face);
      String wide = Wides.forFaceAndSpin(face, spin);
      // The wide's own letter, read back as a face turn, must be the very rotation the core made.
      String asFace = wide.substring(0, 1).toUpperCase() + wide.substring(1);
      assertEquals("the wide's layer is not the one that swung", spin, rotationOf(asFace)
          .getNotation());
      assertEquals("a wide names a layer opposite the face reported",
          opposite(face.substring(0, 1)), wide.substring(0, 1).toUpperCase());
    }
  }

  @Test
  public void everyFaceTurnHasExactlyOneWideItCouldBe() {
    for (String face : everyFaceTurn()) {
      int matches = 0;
      for (CubeRotation rotation : CubeRotation.all()) {
        if (Wides.forFaceAndSpin(face, rotation.getNotation()) != null) {
          matches++;
        }
      }
      assertEquals("exactly one of the 24 makes " + face + " a wide", 1, matches);
    }
  }

  @Test
  public void refusesASpinThatIsNotTheOneThatFaceWouldHaveMade() {
    // x is the same axis the wrong way: a cube rotated one way and a layer the other, so two moves.
    assertEquals("l", Wides.forFaceAndSpin("R", "x'"));
    assertNull(Wides.forFaceAndSpin("R", "x"));
    assertNull(Wides.forFaceAndSpin("R", "y"));
    assertNull(Wides.forFaceAndSpin("R", "x2"));
  }

  /**
   * A slice and a wide on one axis make the same core spin, told apart only by the second face —
   * which is why a face goes to the wide test only once no slice has claimed it.
   */
  @Test
  public void aSliceAndAWideOnTheSameAxisMakeTheSameSpin() {
    assertEquals("M", Slices.forPair("R", "L'")[0]);
    assertEquals("x'", Slices.forPair("R", "L'")[1]);
    assertEquals("l", Wides.forFaceAndSpin("R", "x'"));
    assertEquals("x'", Wides.spinFor("R"));
  }

  private static CubeRotation rotationOf(String faceTurn) {
    String rotation = AS_ROTATION.get(faceTurn.substring(0, 1));
    return CubeRotation.byNotation(faceTurn.endsWith("'") ? inverse(rotation) : rotation);
  }

  private static String inverse(String rotation) {
    return rotation.endsWith("'") ? rotation.substring(0, 1) : rotation + "'";
  }

  private static String[] everyFaceTurn() {
    return new String[] {"U", "U'", "D", "D'", "L", "L'", "R", "R'", "F", "F'", "B", "B'"};
  }

  private static String opposite(String face) {
    switch (face) {
      case "U": return "D";
      case "D": return "U";
      case "L": return "R";
      case "R": return "L";
      case "F": return "B";
      default: return "F";
    }
  }
}
