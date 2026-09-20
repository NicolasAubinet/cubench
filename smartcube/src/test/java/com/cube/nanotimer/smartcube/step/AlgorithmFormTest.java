package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * What makes the form trustworthy: an algorithm and its form have to turn the cube the same way.
 * Everything else here is a spelling, and a spelling that is wrong in the same way twice would still
 * compare equal, so the whole table is run through that one check.
 */
public class AlgorithmFormTest {

  @Test
  public void everyAlgorithmInTheTableTurnsTheCubeTheWayItsFormDoes() {
    List<String> wrong = new ArrayList<String>();
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      String turned = Notation.apply(CubeState.SOLVED_FACELETS, row[1]);
      String form = written(AlgorithmForm.of(row[1]));
      // Up to standing: an algorithm written with a rotation in it leaves the cube facing elsewhere,
      // and that is the one thing the form deliberately drops.
      if (!sameUpToStanding(turned, Notation.apply(CubeState.SOLVED_FACELETS, form))) {
        wrong.add(row[0] + " | " + row[1] + " | read as " + form);
      }
    }
    assertEquals(join(wrong), 0, wrong.size());
  }

  @Test
  public void anAlgorithmLeavingTheCubeWhereItFoundItHasNothingLeftOver() {
    List<String> wrong = new ArrayList<String>();
    int checked = 0;
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      String turned = Notation.apply(CubeState.SOLVED_FACELETS, row[1]);
      if (!standsUpright(turned)) {
        continue; // it is left facing elsewhere: the row above is what can be asked of it
      }
      checked++;
      if (!turned.equals(Notation.apply(CubeState.SOLVED_FACELETS, written(AlgorithmForm.of(row[1]))))) {
        wrong.add(row[0] + " | " + row[1]);
      }
    }
    assertEquals(join(wrong), 0, wrong.size());
    assertTrue(checked > 100); // most of the table, or this proves nothing
  }

  @Test
  public void aWideIsTheOuterTurnItIsMadeOf() {
    assertEquals(Arrays.asList("L"), AlgorithmForm.of("r")); // Rw = x L, and the x is not written
    assertEquals(Arrays.asList("L"), AlgorithmForm.of("Rw"));
    assertEquals(Arrays.asList("L'"), AlgorithmForm.of("r'"));
    assertEquals(Arrays.asList("L2"), AlgorithmForm.of("r2"));
    assertEquals(Arrays.asList("R'"), AlgorithmForm.of("l'")); // Lw' = R' x
    assertEquals(Arrays.asList("D"), AlgorithmForm.of("u"));
  }

  @Test
  public void aSliceIsTheTwoOuterTurnsItIsMadeOf() {
    assertEquals(Arrays.asList("R", "L'"), AlgorithmForm.of("M")); // M = x' R L'
    assertEquals(Arrays.asList("R'", "L"), AlgorithmForm.of("M'"));
    assertEquals(Arrays.asList("U'", "D"), AlgorithmForm.of("E'"));
  }

  @Test
  public void perTokenLinesUpWithTheTokens() {
    assertEquals(Arrays.asList(Arrays.asList("R"), new ArrayList<String>(), Arrays.asList("R", "L'"),
        Arrays.asList("B")), AlgorithmForm.perToken("R", "", "M", "U"));
    assertEquals(Arrays.asList(Arrays.asList("L"), Arrays.asList("F'")),
        AlgorithmForm.perToken("r", "U'")); // r carries x, which stands F where U was
  }

  /** Checked against the turns themselves: the cube a scramble leaves, stood back on its centres. */
  @Test
  public void perTokenIsWhatTheCubeEndsOn() {
    String[] scrambles = {"R U M", "U R' M'", "F r M", "U r' M'", "L r2", "D l", "B l' M",
        "R2 R r", "L2 L l", "M U r' F", "E R S2 b"};
    for (String scramble : scrambles) {
      StringBuilder turns = new StringBuilder();
      for (List<String> tokenTurns : AlgorithmForm.perToken(scramble.split(" "))) {
        for (String turn : tokenTurns) {
          turns.append(' ').append(turn);
        }
      }
      assertEquals(scramble, Notation.apply(CubeState.SOLVED_FACELETS, turns.toString()),
          standing(Notation.apply(CubeState.SOLVED_FACELETS, scramble)));
    }
  }

  private static String standing(String facelets) {
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      char[] turned = new char[facelets.length()];
      for (int facelet = 0; facelet < turned.length; facelet++) {
        turned[FaceletRotations.apply(rotation, facelet)] = facelets.charAt(facelet);
      }
      boolean home = true;
      for (int face = 0; face < 6; face++) {
        home &= turned[face * 9 + 4] == CubeState.SOLVED_FACELETS.charAt(face * 9 + 4);
      }
      if (home) {
        return new String(turned);
      }
    }
    throw new IllegalStateException(facelets);
  }

  @Test
  public void aRotationIsNotWrittenDownButTheTurnsAfterItAreItsOwn() {
    assertEquals(new ArrayList<String>(), AlgorithmForm.of("y"));
    assertEquals(Arrays.asList("B"), AlgorithmForm.of("y R")); // y brings the back face to the right
    assertEquals(Arrays.asList("R"), AlgorithmForm.of("x R x'")); // x leaves R where it was
    assertEquals(Arrays.asList("D"), AlgorithmForm.of("z2 U"));
  }

  @Test
  public void turnsThatUndidEachOtherAreFoldedAway() {
    assertEquals(new ArrayList<String>(), AlgorithmForm.of("R U U' R'"));
    assertEquals(Arrays.asList("R'"), AlgorithmForm.of("R U2 U2 R2"));
    assertEquals(Arrays.asList("L"), AlgorithmForm.of("R L R'")); // nothing between them to stop it
  }

  @Test
  public void oppositeFacesAreAlwaysWrittenTheSameWayRound() {
    assertEquals(AlgorithmForm.of("R L'"), AlgorithmForm.of("L' R"));
    assertEquals(AlgorithmForm.of("U D F B"), AlgorithmForm.of("D U B F"));
  }

  @Test
  public void alignmentComesOffBothEndsAndNowhereElse() {
    List<String> turns = AlgorithmForm.of("U R U' R' U2");
    assertEquals(Arrays.asList("R", "U'", "R'"), AlgorithmForm.withoutAlignment(turns));
  }

  @Test
  public void thereAreTwentyFourGripsAndNoRepeats() {
    Set<String> grips = new HashSet<String>();
    for (char[] grip : AlgorithmForm.grips()) {
      grips.add(new String(grip));
    }
    assertEquals(24, AlgorithmForm.grips().size());
    assertEquals(24, grips.size());
  }

  @Test
  public void aGripSaysTheSameAlgorithmInOtherLetters() {
    List<String> sexy = AlgorithmForm.of("R U R' U'");
    List<String> heldElsewhere = AlgorithmForm.of("y R U R' U' y'");
    assertTrue(gripsOf(sexy).contains(heldElsewhere));
  }

  /** Whether the two cubes are the same one, one of them possibly picked up differently. */
  private static boolean sameUpToStanding(String turned, String form) {
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      char[] stood = new char[form.length()];
      for (int facelet = 0; facelet < stood.length; facelet++) {
        stood[FaceletRotations.apply(rotation, facelet)] = form.charAt(facelet);
      }
      if (turned.equals(new String(stood))) {
        return true;
      }
    }
    return false;
  }

  private static boolean standsUpright(String facelets) {
    for (int face = 0; face < 6; face++) {
      if (facelets.charAt(face * 9 + 4) != Cubies.FACES.charAt(face)) {
        return false;
      }
    }
    return true;
  }

  private static List<List<String>> gripsOf(List<String> turns) {
    List<List<String>> all = new ArrayList<List<String>>();
    for (char[] grip : AlgorithmForm.grips()) {
      all.add(AlgorithmForm.conjugatedBy(turns, grip));
    }
    return all;
  }

  /**
   * A regrip in an execution says what the letters after it mean, and turns nothing: folding it
   * writes the moves that follow on the opposite faces. One the solver takes back is a tilt of the
   * wrist and is still folded, since only the moves inside it were named from the frame it opened.
   */
  @Test
  public void keepsTheFacesTurnedAfterARegripAndFoldsATiltTakenBack() {
    assertEquals(Arrays.asList("R", "U", "R'"), AlgorithmForm.asHeld("y2 R U R'"));
    assertEquals(Arrays.asList("U'", "R"), AlgorithmForm.asHeld("x B' x' R"));
    assertEquals(Arrays.asList("R", "U", "R'"), AlgorithmForm.asHeld("R U R'"));
  }

  private static String written(List<String> turns) {
    StringBuilder sb = new StringBuilder();
    for (String turn : turns) {
      sb.append(sb.length() == 0 ? "" : " ").append(turn);
    }
    return sb.length() == 0 ? "U U'" : sb.toString(); // notation for turning nothing
  }

  private static String join(List<String> lines) {
    StringBuilder sb = new StringBuilder();
    for (String line : lines) {
      sb.append('\n').append(line);
    }
    return sb.toString();
  }
}
