package com.cube.nanotimer.smartcube.drill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/**
 * Dealing a case onto the face the user actually finishes on, and knowing when they have finished.
 *
 * <p>Both halves are the same mistake caught from two directions. A case written onto U puts the
 * last layer on white, which is the one colour a white-cross solver never sees it on; and an OLL
 * judged by a solved cube asks for the algorithm the scramble was built from rather than the one the
 * user knows.
 */
public class LayerFaceTest {

  private static final String[] FACES = {"U", "D", "L", "R", "F", "B"};

  /** Which edges belong to each face's layer, in {@link CubieCube}'s slot order. */
  private static final int[][] LAYER_EDGES = {
    {0, 1, 2, 3},   // U
    {4, 5, 6, 7},   // D
    {2, 6, 9, 10},  // L
    {0, 4, 8, 11},  // R
    {1, 5, 8, 9},   // F
    {3, 7, 10, 11}, // B
  };

  /** And which corners, in its corner order (URF UFL ULB UBR DFR DLF DBL DRB). */
  private static final int[][] LAYER_CORNERS = {
    {0, 1, 2, 3},   // U
    {4, 5, 6, 7},   // D
    {1, 2, 5, 6},   // L
    {0, 3, 4, 7},   // R
    {0, 1, 4, 5},   // F
    {2, 3, 6, 7},   // B
  };

  /**
   * The scramble really does leave the case on the chosen face: everything outside that layer is
   * home and the right way up, which is what "last layer" means.
   */
  @Test
  public void aCaseIsDealtOntoTheFaceItWasAskedFor() {
    for (int face = 0; face < FACES.length; face++) {
      for (String code : new String[] {"pll_ga", "oll_21", "pll_h", "oll_45"}) {
        // Dealt over and over rather than run: a rep that is never finished is never spent, so
        // this is eight draws of the same case, each with its own alignment.
        DrillSession session = session(code, 8, FACES[face]);
        for (int draw = 0; draw < 8; draw++) {
          assertTrue(session.nextRep());
          CubieCube cube = new CubieCube();
          assertTrue(cube.fromFacelet(session.getFacelets()));
          assertOnlyLayerDisturbed(FACES[face] + " " + code, cube, face);
        }
      }
    }
  }

  /** Every case still finishes on every face, which is the rotation not having broken anything. */
  @Test
  public void everyCaseStillFinishesOnEveryFace() {
    for (String face : FACES) {
      for (String code : new String[] {"pll_ga", "pll_e", "oll_21", "oll_57"}) {
        DrillSession session = session(code, 1, face);
        Hand hand = new Hand(session);
        assertTrue(hand.next());
        assertNotNull(face + " " + code,
            hand.execute(inverse(session.getCurrentScramble())));
      }
    }
  }

  /**
   * The turn that squares a case up follows the layer: on a cube finished on D it is a D turn, and
   * charging it to the turning is what made a case dealt off-square read as a slower algorithm.
   */
  @Test
  public void theAlignmentTurnFollowsTheLayer() {
    DrillSession session = session("pll_t", 2, "D");
    Hand hand = new Hand(session);

    assertTrue(hand.next());
    hand.pause(500);
    DrillRep straight = hand.execute(inverse(session.getCurrentScramble()));

    assertTrue(hand.next());
    hand.pause(500);
    DrillRep aligned = hand.execute("D D' " + inverse(session.getCurrentScramble()));

    assertEquals("the alignment was charged to the turning",
        straight.getExecutionMs(), aligned.getExecutionMs());
  }

  private static void assertOnlyLayerDisturbed(String what, CubieCube cube, int face) {
    int[] cp = new int[8];
    int[] co = new int[8];
    int[] ep = new int[12];
    int[] eo = new int[12];
    cube.toPermutation(cp, co, ep, eo);

    List<Integer> layerEdges = boxed(LAYER_EDGES[face]);
    for (int slot = 0; slot < ep.length; slot++) {
      if (!layerEdges.contains(slot)) {
        assertEquals(what + ": edge " + slot + " moved", slot, ep[slot]);
        assertEquals(what + ": edge " + slot + " flipped", 0, eo[slot]);
      }
    }
    List<Integer> layerCorners = boxed(LAYER_CORNERS[face]);
    for (int slot = 0; slot < cp.length; slot++) {
      if (!layerCorners.contains(slot)) {
        assertEquals(what + ": corner " + slot + " moved", slot, cp[slot]);
        assertEquals(what + ": corner " + slot + " twisted", 0, co[slot]);
      }
    }
  }

  private static List<Integer> boxed(int[] values) {
    List<Integer> list = new ArrayList<Integer>();
    for (int value : values) {
      list.add(value);
    }
    return list;
  }

  private static DrillSession session(String code, int reps, String face) {
    DrillSpec spec = new DrillSpec("test", DrillSpec.Type.CASE_EXECUTION, DrillSpec.Delivery.VIRTUAL,
        Collections.singletonList(code), DrillSpec.Selection.ROUND_ROBIN, reps, 0, null);
    return new DrillSession(spec, new Random(7), null, face);
  }

  /** The user's hands, feeding face turns as a cube reports them. */
  private static final class Hand {

    private final DrillSession session;
    private long clock = 10_000;
    private long shownAtMs;

    Hand(DrillSession session) {
      this.session = session;
    }

    boolean next() {
      if (!session.nextRep()) {
        return false;
      }
      shownAtMs = clock;
      session.markCaseShown(shownAtMs);
      return true;
    }

    void pause(long ms) {
      clock = shownAtMs + ms;
    }

    DrillRep execute(String algorithm) {
      for (CubeMove move : quarterTurns(algorithm)) {
        CubeMove timed = new CubeMove(move.getFace(), move.isPrime(), clock);
        clock += 40;
        DrillRep rep = session.onMove(timed);
        if (rep != null) {
          return rep;
        }
      }
      return null;
    }
  }

  private static List<CubeMove> quarterTurns(String algorithm) {
    List<CubeMove> moves = new ArrayList<CubeMove>();
    for (String token : algorithm.trim().split("\\s+")) {
      if (token.isEmpty()) {
        continue;
      }
      Face face = Face.valueOf(token.substring(0, 1));
      boolean prime = token.endsWith("'");
      int quarters = token.indexOf('2') >= 0 ? 2 : 1;
      for (int quarter = 0; quarter < quarters; quarter++) {
        moves.add(new CubeMove(face, prime, 0));
      }
    }
    return moves;
  }

  private static String inverse(String algorithm) {
    List<String> tokens = new ArrayList<String>();
    for (String token : algorithm.trim().split("\\s+")) {
      if (!token.isEmpty()) {
        tokens.add(token.endsWith("'") ? token.substring(0, token.length() - 1)
            : token.contains("2") ? token : token + "'");
      }
    }
    Collections.reverse(tokens);
    StringBuilder sb = new StringBuilder();
    for (String token : tokens) {
      sb.append(sb.length() == 0 ? "" : " ").append(token);
    }
    return sb.toString();
  }
}
