package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.vo.CubeMethod;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Whether a solve bears out the method its solve type is read as. The solves are the detectors'
 * own: a CFOP one, a Roux one built backwards so its milestones are in Roux order by construction,
 * a last-layer drill that contradicts neither method, and a scramble undone by its own inverse,
 * which fits neither.
 */
public class MethodAnalyzersTest {

  private static final String SUNE = "R U R' U R U2 R'";
  private static final String ANTI_SUNE = "R U2 R' U' R U' R'";
  private static final String T_PERM = "R U R' U' R' F R2 U' R' U' R U R' F'"; // its own inverse

  private static final String SCRAMBLE = "R U2 F' L D B2 R' U F2 D'";

  private static final String[] ROUX_SOLVE = {
    "U R' F R", "R U R' U M U' R U R'", SUNE, T_PERM, "M2 U M", "U M' U2 M", "M2",
  };

  private final CubieCube cube = new CubieCube();
  private MethodAnalyzers analyzers = new MethodAnalyzers(CubeMethod.CFOP);

  /** Quarter turns of drift the slices have put between the solver's frame and the cube's. */
  private int drift;
  private long timestampMs;

  @Test
  public void readsACfopSolveOnACfopSolveTypeAsCfop() {
    scramble(T_PERM, SUNE, "R U' R'", "F'");
    play("F", "R U R'", ANTI_SUNE, T_PERM);

    assertEquals(CubeMethod.CFOP, analyzers.resolve());
  }

  @Test
  public void readsARouxSolveOnARouxSolveTypeAsRoux() {
    analyzers = new MethodAnalyzers(CubeMethod.ROUX);
    scramble(invert(join(ROUX_SOLVE)));
    play(ROUX_SOLVE);

    assertEquals(CubeMethod.ROUX, analyzers.resolve());
    assertEquals(4, analyzers.get(CubeMethod.ROUX).getStepTimes().size());
    assertEquals("fb", analyzers.get(CubeMethod.ROUX).getStepTimes().get(0).getStepName());
  }

  /**
   * A Roux solve on a CFOP solve type is not a Roux solve to be filed as one — it is a solve on the
   * wrong solve type, and it earns no breakdown. What the type says its solves are is not a guess
   * for the moves to overrule.
   */
  @Test
  public void givesNoMethodToASolveDoneWithAnotherMethodThanItsSolveTypes() {
    scramble(invert(join(ROUX_SOLVE)));
    play(ROUX_SOLVE);

    assertFalse(analyzers.get(CubeMethod.CFOP).matchesMethod());
    assertNull(analyzers.resolve());
  }

  /**
   * A last-layer drill builds nothing, so it contradicts neither method and would once have been a
   * tie to break. There is nothing to break now: it is read as its solve type's method, and the
   * same solve on a Roux type is read as Roux.
   */
  @Test
  public void asksOnlyWhetherTheSolveFitsItsOwnSolveTypesMethod() {
    scramble(T_PERM, SUNE);
    play(ANTI_SUNE, T_PERM);

    assertEquals(CubeMethod.CFOP, analyzers.resolve());
  }

  @Test
  public void givesNoMethodToASolveThatFitsNone() {
    // A scramble undone by its own inverse: nothing is built in any order, and the cube falls
    // solved at the end all at once. Its own method's milestones do not fit, so none is stored.
    scramble(SCRAMBLE);
    play(invert(SCRAMBLE));

    assertNull(analyzers.resolve());
  }

  @Test
  public void keepsTheMovesOfASolveThatFitsNoMethod() {
    scramble(SCRAMBLE);
    play(invert(SCRAMBLE));

    // The controller stores the moves whether or not a method was read: an unrecognised solve still
    // has a solution worth keeping.
    assertNull(analyzers.resolve());
    assertEquals(13, analyzers.moves().getMoves().size()); // quarter turns, doubles as two
  }

  private void scramble(String... moves) {
    for (String token : model(join(moves))) {
      apply(token);
    }
    analyzers.start(new CubeState(cube.toFaceCube()), timestampMs);
  }

  /** Feed each move and then the state it produced, the order the analyzers are fed on the screen. */
  private void play(String... moves) {
    for (String token : model(join(moves))) {
      Face face = Face.valueOf(token.substring(0, 1));
      boolean prime = token.endsWith("'");
      for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
        timestampMs += 100;
        analyzers.onMove(new CubeMove(face, prime, timestampMs));
        cube.applyMove(face, prime);
        analyzers.onState(new CubeState(cube.toFaceCube()));
      }
    }
  }

  private void apply(String token) {
    Face face = Face.valueOf(token.substring(0, 1));
    for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
      cube.applyMove(face, token.endsWith("'"));
    }
  }

  private static String join(String... moves) {
    return String.join(" ", moves);
  }

  /**
   * What the cube reports for moves the solver makes: a slice as the two face turns it registers —
   * the core turned, not a face — and every later turn under the letter of the face the core has
   * since carried it to.
   */
  private String[] model(String moves) {
    List<String> reported = new ArrayList<String>();
    for (String token : moves.trim().split("\\s+")) {
      if (token.charAt(0) == 'M') {
        reported.add(token.endsWith("2") ? "R2" : token.endsWith("'") ? "R'" : "R");
        reported.add(token.endsWith("2") ? "L2" : token.endsWith("'") ? "L" : "L'");
        drift = (drift + (token.endsWith("2") ? 2 : token.endsWith("'") ? 3 : 1)) % 4;
      } else {
        int face = "UBDF".indexOf(token.charAt(0));
        reported.add(face == -1 ? token
            : "UBDF".charAt((face + drift) % 4) + token.substring(1));
      }
    }
    return reported.toArray(new String[0]);
  }

  private static String invert(String moves) {
    String[] tokens = moves.trim().split("\\s+");
    StringBuilder inverted = new StringBuilder();
    for (int i = tokens.length - 1; i >= 0; i--) {
      String token = tokens[i];
      inverted.append(token.endsWith("2") ? token
          : token.endsWith("'") ? token.substring(0, token.length() - 1) : token + "'");
      inverted.append(' ');
    }
    return inverted.toString().trim();
  }
}
