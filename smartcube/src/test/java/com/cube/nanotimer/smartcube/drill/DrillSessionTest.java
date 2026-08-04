package com.cube.nanotimer.smartcube.drill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

/**
 * What a drill rests on: the virtual cube driven straight from the turns a cube reports, with no
 * idea of how the cube is being held. The cases are fed here as the cube would report them, so an
 * algorithm's rotations never arrive at all and its slices arrive as pairs of outer turns.
 */
public class DrillSessionTest {

  private static final String FACES = "URFDLB";
  private static final int U = 0, R = 1, F = 2, D = 3, L = 4, B = 5;

  /** Where each face lands under a quarter turn of the whole cube, one row per axis. */
  private static final int[][] ROTATION_CYCLES = {
    {B, R, U, F, L, D}, // x
    {U, F, L, D, B, R}, // y
    {R, D, F, L, U, B}, // z
  };

  private static final int OUTER = 0, WIDE = 1, SLICE = 2, ROTATION = 3;

  /** Milliseconds between one reported turn and the next. */
  private static final long GAP_MS = 40;

  @Test
  public void everyCaseIsFinishedByUndoingItsScramble() {
    for (String code : LastLayerScrambles.cases()) {
      DrillSession session = new DrillSession(spec(code, 1), new Random(7));
      assertTrue(code, session.nextRep());
      DrillRep rep = new Hand(session).execute(inverse(session.getCurrentScramble()));
      assertNotNull(code, rep);
      assertEquals(code, rep.getCaseCode());
      assertTrue(code, session.isFinished());
    }
  }

  /**
   * The claim the whole delivery mode rests on. These algorithms are stuffed with the three things
   * that are supposed to need a frame: slices, which carry the centres with them; wide turns, which
   * are a rotation with a layer put back; and rotations, which the cube cannot see at all. Fed as
   * the cube reports them, every one still lands on a solved cube.
   *
   * <p>They are the algorithms each case is filed under, since a scramble is one of those undone. If
   * that table's first algorithm for a case is ever changed, the literal here has to change with it,
   * and this test failing is what says so.
   */
  @Test
  public void slicesWidesAndRotationsAllStillFinishTheRep() {
    assertFinishes("pll_h", "M2 U M2 U2 M2 U M2");
    assertFinishes("pll_z", "M2 U M2 U M' U2 M2 U2 M' U2");
    assertFinishes("pll_aa", "l' U R' D2 R U' R' D2 R2");
    assertFinishes("pll_ga", "R2 u R' U R' U' R u' R2 y' R' U R");
    assertFinishes("pll_gb", "L' U' L y' R2 u R' U R U' R u' R2");
    assertFinishes("pll_e", "x' R U' R' D R U R' D' R U R' D R U' R' D'");
  }

  /** The particular fact the above rides on, on its own: a slice is two outer turns and a lie. */
  @Test
  public void aSliceReachesTheCubeAsTwoOuterTurns() {
    assertEquals("R L'", notation(asReported("M")));
    assertEquals("R R L L", notation(asReported("M2")));
  }

  /** And a rotation reaches it as nothing, having only moved the cube through the air. */
  @Test
  public void aRotationReachesTheCubeAsNothing() {
    assertEquals("", notation(asReported("y")));
    assertEquals("", notation(asReported("x' z2")));
  }

  @Test
  public void recognitionRunsFromTheEndOfTheRepBefore() {
    DrillSession session = new DrillSession(spec("pll_t", 2), new Random(3));
    Hand hand = new Hand(session);

    assertTrue(session.nextRep());
    DrillRep first = hand.execute(inverse(session.getCurrentScramble()));
    assertFalse("nothing to measure the first rep's recognition from",
        first.isRecognitionMeasured());
    assertEquals(0, first.getRecognitionMs());
    assertEquals((first.getMoveCount() - 1) * GAP_MS, first.getExecutionMs());

    assertTrue(session.nextRep());
    hand.pause(1234);
    DrillRep second = hand.execute(inverse(session.getCurrentScramble()));
    assertTrue(second.isRecognitionMeasured());
    assertEquals(1234, second.getRecognitionMs());
  }

  @Test
  public void anUnknownCaseIsDroppedAndTheRestStillRun() {
    DrillSpec spec = new DrillSpec("mixed", DrillSpec.Type.CASE_EXECUTION,
        DrillSpec.Delivery.VIRTUAL, Arrays.asList("pll_ga", "pll_nonesuch", "oll_21"),
        DrillSpec.Selection.ROUND_ROBIN, 4, 0, null);
    DrillSession session = new DrillSession(spec, new Random(5));
    assertEquals(Collections.singletonList("pll_nonesuch"), session.getUnknownCases());
    assertTrue(session.isRunnable());
    assertTrue(session.nextRep());
    assertTrue(Arrays.asList("pll_ga", "oll_21").contains(session.getCurrentCase()));
  }

  @Test
  public void aDrillOfNothingKnownDoesNotRun() {
    DrillSpec spec = new DrillSpec("none", DrillSpec.Type.CASE_EXECUTION,
        DrillSpec.Delivery.VIRTUAL, Arrays.asList("pll_nonesuch"),
        DrillSpec.Selection.ROUND_ROBIN, 4, 0, null);
    DrillSession session = new DrillSession(spec, new Random(5));
    assertFalse(session.isRunnable());
    assertTrue(session.isFinished());
    assertFalse(session.nextRep());
  }

  /** Even coverage, and no order to learn: a recognition drill dies if the next case is guessable. */
  @Test
  public void everyCaseComesUpOnceBeforeAnyComesUpTwice() {
    List<String> codes = Arrays.asList("pll_h", "pll_z", "pll_t");
    DrillSpec spec = new DrillSpec("rr", DrillSpec.Type.CASE_EXECUTION,
        DrillSpec.Delivery.VIRTUAL, codes, DrillSpec.Selection.ROUND_ROBIN, 6, 0, null);
    DrillSession session = new DrillSession(spec, new Random(11));
    Hand hand = new Hand(session);
    List<String> seen = new ArrayList<String>();
    while (session.nextRep()) {
      seen.add(session.getCurrentCase());
      assertNotNull(session.getCurrentCase(), hand.execute(inverse(session.getCurrentScramble())));
    }
    assertEquals(6, seen.size());
    assertEquals(new HashSet<String>(codes), new HashSet<String>(seen.subList(0, 3)));
    assertEquals(new HashSet<String>(codes), new HashSet<String>(seen.subList(3, 6)));
  }

  @Test
  public void weightGoesToWhatIsCostingTheMost() {
    List<String> codes = Arrays.asList("pll_h", "pll_z");
    DrillSpec spec = new DrillSpec("w", DrillSpec.Type.CASE_EXECUTION, DrillSpec.Delivery.VIRTUAL,
        codes, DrillSpec.Selection.WEIGHTED, 40, 0, null);
    Map<String, Long> weights = new HashMap<String, Long>();
    weights.put("pll_h", 9L);
    weights.put("pll_z", 1L);
    DrillSession session = new DrillSession(spec, new Random(13), weights);
    Hand hand = new Hand(session);
    int costly = 0;
    while (session.nextRep()) {
      if ("pll_h".equals(session.getCurrentCase())) {
        costly++;
      }
      assertNotNull(session.getCurrentCase(), hand.execute(inverse(session.getCurrentScramble())));
    }
    assertEquals(40, session.getReps().size());
    assertTrue("nine times the weight should show: " + costly, costly > 25);
  }

  @Test
  public void givingUpSpendsTheRep() {
    DrillSession session = new DrillSession(spec("oll_21", 2), new Random(2));
    assertTrue(session.nextRep());
    DrillRep given = session.abandon();
    assertNotNull(given);
    assertTrue(given.isAbandoned());
    assertEquals(1, session.getReps().size());
    assertTrue(session.nextRep());
    assertFalse(session.isFinished());
  }

  private static void assertFinishes(String code, String algorithm) {
    DrillSession session = new DrillSession(spec(code, 1), noAlignment());
    assertTrue(code, session.nextRep());
    DrillRep rep = new Hand(session).execute(algorithm);
    assertNotNull(code + " was not finished by " + algorithm, rep);
    assertEquals(code, rep.getCaseCode());
  }

  private static DrillSpec spec(String code, int reps) {
    return new DrillSpec("test", DrillSpec.Type.CASE_EXECUTION, DrillSpec.Delivery.VIRTUAL,
        Collections.singletonList(code), DrillSpec.Selection.ROUND_ROBIN, reps, 0, null);
  }

  /** A draw that always takes the first option, so a case is scrambled without alignment turns. */
  private static Random noAlignment() {
    return new Random() {
      @Override
      public int nextInt(int bound) {
        return 0;
      }
    };
  }

  /** The user's hands: turns arrive evenly spaced, and the clock runs on between reps. */
  private static final class Hand {

    private final DrillSession session;
    private long clock = 10_000;
    private long lastMoveMs = 10_000;

    Hand(DrillSession session) {
      this.session = session;
    }

    /** Sit and look at the case for a while before the next rep's first turn. */
    void pause(long ms) {
      clock = lastMoveMs + ms;
    }

    /** Execute an algorithm, stopping the moment the rep is finished. */
    DrillRep execute(String algorithm) {
      for (CubeMove move : asReported(algorithm)) {
        CubeMove timed = new CubeMove(move.getFace(), move.isPrime(), clock);
        lastMoveMs = clock;
        clock += GAP_MS;
        DrillRep rep = session.onMove(timed);
        if (rep != null) {
          return rep;
        }
      }
      return null;
    }
  }

  /**
   * An algorithm as a cube reports it: outer turns only, named against the cube's own centres.
   *
   * <p>A rotation is reported as nothing and only renames what comes after it. A wide turn is the
   * whole cube turned with the far layer put back, and a slice is that with the near layer put back
   * too, which is why {@code M} arrives as {@code R L'} and drags everything after it a quarter turn
   * round. Double turns arrive as two quarters, the way a cube sends them.
   */
  private static List<CubeMove> asReported(String algorithm) {
    int[] at = {U, R, F, D, L, B};
    List<CubeMove> reported = new ArrayList<CubeMove>();
    for (String token : algorithm.trim().split("\\s+")) {
      if (token.isEmpty()) {
        continue;
      }
      int quarters = token.endsWith("'") ? 3 : token.indexOf('2') >= 0 ? 2 : 1;
      int kind = kindOf(token);
      int face = faceOf(token);
      if (kind != OUTER) {
        rotate(at, face, quarters);
      }
      switch (kind) {
        case OUTER:
          emit(reported, at[face], quarters);
          break;
        case WIDE:
          emit(reported, at[opposite(face)], quarters);
          break;
        case SLICE:
          emit(reported, at[opposite(face)], quarters);
          emit(reported, at[face], 4 - quarters);
          break;
        default: // a rotation moves the cube through the air and nothing else
          break;
      }
    }
    return reported;
  }

  private static int kindOf(String token) {
    char letter = token.charAt(0);
    if (FACES.indexOf(Character.toUpperCase(letter)) >= 0) {
      return Character.isLowerCase(letter) || token.indexOf('w') > 0 ? WIDE : OUTER;
    }
    return letter == 'x' || letter == 'y' || letter == 'z' ? ROTATION : SLICE;
  }

  /** The face a turn is read against: M follows L, E follows D, S follows F, and x, y, z R, U, F. */
  private static int faceOf(String token) {
    char letter = token.charAt(0);
    int face = FACES.indexOf(Character.toUpperCase(letter));
    if (face >= 0) {
      return face;
    }
    switch (letter) {
      case 'M': return L;
      case 'E': return D;
      case 'S': return F;
      case 'x': return R;
      case 'y': return U;
      default: return F;
    }
  }

  private static int opposite(int face) {
    return (face + 3) % 6;
  }

  /** The cube turned in the direction the given face turns, which renames every face after it. */
  private static void rotate(int[] at, int face, int quarters) {
    int axis = face == R || face == L ? 0 : face == U || face == D ? 1 : 2;
    boolean near = face == U || face == R || face == F;
    int[] cycle = ROTATION_CYCLES[axis];
    for (int turn = 0; turn < (near ? quarters : 4 - quarters); turn++) {
      int[] before = at.clone();
      for (int position = 0; position < 6; position++) {
        at[cycle[position]] = before[position];
      }
    }
  }

  private static void emit(List<CubeMove> reported, int face, int quarters) {
    Face turned = Face.valueOf(String.valueOf(FACES.charAt(face)));
    for (int quarter = 0; quarter < (quarters == 2 ? 2 : 1); quarter++) {
      reported.add(new CubeMove(turned, quarters == 3, 0));
    }
  }

  private static String inverse(String algorithm) {
    String[] tokens = algorithm.trim().split("\\s+");
    StringBuilder inverted = new StringBuilder();
    for (int i = tokens.length - 1; i >= 0; i--) {
      if (tokens[i].isEmpty()) {
        continue;
      }
      if (inverted.length() > 0) {
        inverted.append(' ');
      }
      inverted.append(tokens[i].indexOf('2') >= 0 ? tokens[i]
          : tokens[i].endsWith("'") ? tokens[i].substring(0, tokens[i].length() - 1)
          : tokens[i] + "'");
    }
    return inverted.toString();
  }

  private static String notation(List<CubeMove> moves) {
    StringBuilder written = new StringBuilder();
    for (CubeMove move : moves) {
      if (written.length() > 0) {
        written.append(' ');
      }
      written.append(move.getNotation());
    }
    return written.toString();
  }
}
