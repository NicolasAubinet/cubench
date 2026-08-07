package com.cube.nanotimer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.cube.nanotimer.Options.ClockNotation;
import com.cube.nanotimer.vo.CubeType;

import org.junit.Test;

/**
 * Tests the pure scramble -> cubing.js notation conversion (the Options-free
 * overload), and the CubeType -> renderer-key mapping.
 */
public class ScrambleViewNotationTest {

  // Convenience for non-clock puzzles (clock notation is irrelevant there).
  private static String convert(String[] scramble, CubeType cubeType) {
    return ScrambleViewNotation.toCubingNotation(scramble, cubeType, null);
  }

  @Test
  public void rendererKeysCoverAllPuzzles() {
    assertEquals("222", ScrambleViewNotation.getRenderKey(CubeType.TWO_BY_TWO));
    assertEquals("333", ScrambleViewNotation.getRenderKey(CubeType.THREE_BY_THREE));
    assertEquals("444", ScrambleViewNotation.getRenderKey(CubeType.FOUR_BY_FOUR));
    assertEquals("555", ScrambleViewNotation.getRenderKey(CubeType.FIVE_BY_FIVE));
    assertEquals("666", ScrambleViewNotation.getRenderKey(CubeType.SIX_BY_SIX));
    assertEquals("777", ScrambleViewNotation.getRenderKey(CubeType.SEVEN_BY_SEVEN));
    assertEquals("minx", ScrambleViewNotation.getRenderKey(CubeType.MEGAMINX));
    assertEquals("pyram", ScrambleViewNotation.getRenderKey(CubeType.PYRAMINX));
    assertEquals("skewb", ScrambleViewNotation.getRenderKey(CubeType.SKEWB));
    assertEquals("sq1", ScrambleViewNotation.getRenderKey(CubeType.SQUARE1));
    assertEquals("clock", ScrambleViewNotation.getRenderKey(CubeType.CLOCK));
    assertEquals("fto", ScrambleViewNotation.getRenderKey(CubeType.FTO));
  }

  @Test
  public void rendererKeyNullForNull() {
    assertNull(ScrambleViewNotation.getRenderKey(null));
  }

  @Test
  public void cubePassthroughJoinsWithSpaces() {
    assertEquals("R U2 R' F", convert(new String[] {"R", "U2", "R'", "F"}, CubeType.THREE_BY_THREE));
  }

  @Test
  public void collapsesWhitespaceAndNewlines() {
    assertEquals("R U F", convert(new String[] {"R\nU", "F"}, CubeType.THREE_BY_THREE));
  }

  @Test
  public void megaminxElementsCollapseToOneLine() {
    // The Megaminx scrambler emits trailing spaces on each element and the raw
    // scramble has embedded newlines; we want a single clean line.
    assertEquals("R++ D-- U' U",
        convert(new String[] {"R++ ", "D-- ", "U'", "U "}, CubeType.MEGAMINX));
  }

  @Test
  public void square1GetsSlashSeparatorsAndNoInnerSpaces() {
    // Move.toString() formats as "( 3, 2)" (padded); cubing.js wants the slash form.
    assertEquals("(3,2) / (-2,-5)",
        convert(new String[] {"( 3, 2)", "(-2,-5)"}, CubeType.SQUARE1));
  }

  @Test
  public void clockWcaNotationPassesThrough() {
    String[] scramble = {"UR1+", "DR6+", "y2", "DR", "DL"};
    assertEquals("UR1+ DR6+ y2 DR DL",
        ScrambleViewNotation.toCubingNotation(scramble, CubeType.CLOCK, ClockNotation.URx_DRx_DLx));
  }

  @Test
  public void clockPinNotationsReturnNull() {
    String[] scramble = {"(UUdU, 2,-4)", "(UUdd, 1, 4)"};
    assertNull(ScrambleViewNotation.toCubingNotation(scramble, CubeType.CLOCK, ClockNotation.UUdU_x_x));
    assertNull(ScrambleViewNotation.toCubingNotation(scramble, CubeType.CLOCK, ClockNotation.UUdd_ux_dx));
  }

  @Test
  public void emptyAndNullScrambleGiveEmptyString() {
    assertEquals("", convert(new String[0], CubeType.THREE_BY_THREE));
    assertEquals("", convert(null, CubeType.THREE_BY_THREE));
  }

  /**
   * The two ids are different namespaces and are easy to swap by accident: a {@code twisty-player}
   * handed the render key {@code 333} fails outright with "Invalid puzzle ID".
   */
  @Test
  public void puzzleIdIsNotTheRenderKey() {
    // The NxN puzzles are the trap: a twisty-player handed the render key "333" fails outright
    // with "Invalid puzzle ID". Some of the others (skewb, clock, fto) do coincide.
    assertEquals("3x3x3", ScrambleViewNotation.getPuzzleId(CubeType.THREE_BY_THREE));
    assertEquals("333", ScrambleViewNotation.getRenderKey(CubeType.THREE_BY_THREE));
    assertNotEquals(ScrambleViewNotation.getRenderKey(CubeType.TWO_BY_TWO),
        ScrambleViewNotation.getPuzzleId(CubeType.TWO_BY_TWO));
    assertEquals("megaminx", ScrambleViewNotation.getPuzzleId(CubeType.MEGAMINX));
    assertEquals("pyraminx", ScrambleViewNotation.getPuzzleId(CubeType.PYRAMINX));
    assertEquals("square1", ScrambleViewNotation.getPuzzleId(CubeType.SQUARE1));
  }

  /** Anything the app can draw a diagram for should also name a puzzle a player can be given. */
  @Test
  public void everyDrawablePuzzleHasAPuzzleId() {
    for (CubeType cubeType : CubeType.values()) {
      if (ScrambleViewNotation.getRenderKey(cubeType) != null) {
        assertNotNull(cubeType.name(), ScrambleViewNotation.getPuzzleId(cubeType));
      }
    }
  }

  /** The two the bundle answers "2D" for whatever is asked of it, so there is no view to offer. */
  @Test
  public void clockAndSquare1HaveNo3DView() {
    assertNull(ScrambleViewNotation.get3DPuzzleId(CubeType.CLOCK));
    assertNull(ScrambleViewNotation.get3DPuzzleId(CubeType.SQUARE1));
  }

  @Test
  public void every3DPuzzleIdIsThePuzzleId() {
    for (CubeType cubeType : CubeType.values()) {
      String id = ScrambleViewNotation.get3DPuzzleId(cubeType);
      if (id != null) {
        assertEquals(cubeType.name(), ScrambleViewNotation.getPuzzleId(cubeType), id);
      }
    }
    assertEquals("3x3x3", ScrambleViewNotation.get3DPuzzleId(CubeType.THREE_BY_THREE));
    assertEquals("fto", ScrambleViewNotation.get3DPuzzleId(CubeType.FTO));
  }
}
