package com.cube.nanotimer.smartcube.drill;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * What a cross drill rests on, and it is one claim: the rep is over when four edges are home, which
 * happens long before the cube is solved and is the whole difference from the case drills.
 */
public class CrossDrillSessionTest {

  private static final long GAP_MS = 40;

  private static final String[] FACES = {"U", "D", "L", "R", "F", "B"};
  private static final String[] OPPOSITES = {"D", "U", "R", "L", "B", "F"};

  /**
   * The distinguishing fact. A cube with only its top layer turned is nowhere near solved, and its
   * bottom cross has not been touched, so a cross drill on D has nothing left to ask for.
   */
  @Test
  public void aCrossIsBuiltLongBeforeTheCubeIs() {
    CrossDrillSession session = session("D", 1);
    assertTrue(session.nextRep("U"));
    assertTrue("the D cross survived a U turn", session.isCrossBuilt());
  }

  /** So a rep ends there: the last turn built the cross, and the cube is still scrambled. */
  @Test
  public void aRepEndsOnTheCrossAndNotOnTheCube() {
    CrossDrillSession session = session("D", 1);
    Hand hand = new Hand(session);
    assertTrue(hand.next("U D"));
    CrossDrillRep rep = hand.turn("D'");
    assertNotNull("the cross was rebuilt and the rep did not end", rep);
    assertTrue(rep.isBuilt());
    assertEquals(1, rep.getMoveCount());
    assertFalse("and the cube itself is still turned on top",
        CubieCube.SOLVED_FACELET.equals(session.getFacelets()));
  }

  /** Each face is asked about its own four edges, and about nobody else's. */
  @Test
  public void everyFaceIsCheckedAgainstItsOwnEdges() {
    for (int i = 0; i < FACES.length; i++) {
      CrossDrillSession turned = session(FACES[i], 1);
      turned.nextRep(FACES[i]);
      assertFalse(FACES[i] + " cross survived its own turn", turned.isCrossBuilt());

      CrossDrillSession opposite = session(OPPOSITES[i], 1);
      opposite.nextRep(FACES[i]);
      assertTrue(OPPOSITES[i] + " cross was broken by a " + FACES[i] + " turn",
          opposite.isCrossBuilt());
    }
  }

  /**
   * A cross that is wrong stays wrong however long the user turns, so nothing here can end that rep.
   * The user announcing it is the only end it has, and the rep it produces is a result.
   */
  @Test
  public void aWrongCrossEndsOnlyWhenTheUserSaysSo() {
    CrossDrillSession session = session("D", 1);
    Hand hand = new Hand(session);
    assertTrue(hand.next("R U R' F2"));
    assertNull("nothing built a cross", hand.turn("U R U' L"));

    CrossDrillRep rep = session.declareFinished();
    assertNotNull(rep);
    assertFalse(rep.isBuilt());
    assertEquals("the user's four turns, not the scramble's", 4, rep.getMoveCount());
    assertEquals("a rep all the same", 1, session.getReps().size());
    assertTrue(session.isFinished());
  }

  /** Planning is the looking, from the scramble appearing to the first turn. */
  @Test
  public void theTimeSplitsAtTheFirstTurn() {
    CrossDrillSession session = session("D", 1);
    Hand hand = new Hand(session);
    assertTrue(hand.next("U D"));
    hand.look(2500);
    CrossDrillRep rep = hand.turn("D D D");
    assertNotNull(rep);
    assertEquals(2500, rep.getPlanningMs());
    assertEquals("three turns, so two gaps", 2 * GAP_MS, rep.getExecutionMs());
  }

  /** The score: how far over the shortest way it was. Extra moves are still a finish. */
  @Test
  public void extraMovesAreCountedAgainstTheOptimal() {
    CrossDrillSession session = session("D", 1);
    Hand hand = new Hand(session);
    assertTrue(hand.next("U D"));
    session.setOptimalLength(1);
    CrossDrillRep rep = hand.turn("R R' D'");
    assertNotNull(rep);
    assertTrue(rep.isBuilt());
    assertEquals(3, rep.getMoveCount());
    assertEquals(2, rep.getExtraMoves());
  }

  /**
   * The cube reports a half turn as two quarters, and counting both put every cross holding one a
   * move over an optimal that spells the same turn as one.
   */
  @Test
  public void aHalfTurnIsOneMove() {
    CrossDrillSession session = session("D", 1);
    Hand hand = new Hand(session);
    assertTrue(hand.next("U D2"));
    session.setOptimalLength(1);
    assertNull(hand.turn("D"));
    assertEquals("a quarter of the way through it, one move is what has been made",
        1, session.getMoveCount());

    CrossDrillRep rep = hand.turn("D");
    assertNotNull(rep);
    assertEquals(1, rep.getMoveCount());
    assertEquals("both quarter turns were kept", 2, rep.getMoves().size());
    assertEquals("the shortest way there was", 0, rep.getExtraMoves());
  }

  /** Only the same turn twice folds: a turn taken back is two moves that came to nothing. */
  @Test
  public void aTurnAndItsUndoAreTwoMoves() {
    CrossDrillSession session = session("D", 1);
    Hand hand = new Hand(session);
    assertTrue(hand.next("U D"));
    CrossDrillRep rep = hand.turn("R R' D'");
    assertNotNull(rep);
    assertEquals("the R and the R' are two of the three", 3, rep.getMoveCount());
  }

  /** An optimal length the search has not handed over yet must not read as a perfect rep. */
  @Test
  public void anUnknownOptimalIsNotAPerfectRep() {
    CrossDrillSession session = session("D", 1);
    Hand hand = new Hand(session);
    assertTrue(hand.next("U D"));
    CrossDrillRep rep = hand.turn("D D D");
    assertEquals(0, rep.getOptimalLength());
    assertEquals(0, rep.getExtraMoves());
  }

  /** A cross found in one move beats a search that has to build its table first, so it can. */
  @Test
  public void anAnswerThatLandsAfterItsRepStillReachesIt() {
    CrossDrillSession session = session("D", 1);
    Hand hand = new Hand(session);
    assertTrue(hand.next("U D"));
    CrossDrillRep rep = hand.turn("R R' D'");
    session.setOptimalLength(1);
    assertEquals(1, rep.getOptimalLength());
    assertEquals(2, rep.getExtraMoves());
  }

  /**
   * The four edges a cross is made of, which is the same four whatever the scramble did with them:
   * the screen masks pieces, and the player carries a piece's stickers to wherever it sits.
   */
  @Test
  public void aCrossIsTheSameFourEdgesWhereverTheyAre() {
    CrossDrillSession session = session("D", 1);
    assertTrue(session.nextRep(""));
    assertArrayEquals(new int[] {4, 5, 6, 7}, session.getCrossEdges());

    assertTrue(session.nextRep("R U R' F2"));
    assertArrayEquals(new int[] {4, 5, 6, 7}, session.getCrossEdges());
  }

  /** A turn made before the scramble was drawn belongs to no rep, and must not start one. */
  @Test
  public void turnsBeforeTheScrambleIsShownAreDropped() {
    CrossDrillSession session = session("D", 1);
    assertTrue(session.nextRep("U D"));
    assertNull(session.onMove(new CubeMove(Face.D, true, 10_000)));
    assertTrue("the scramble is still up, untouched", session.getReps().isEmpty());
  }

  /** A scramble whose cross is already there is no rep, and the caller deals another. */
  @Test
  public void aScrambleCanBeReplacedBeforeTheRepStarts() {
    CrossDrillSession session = session("D", 2);
    assertTrue(session.nextRep("U"));
    assertTrue(session.isCrossBuilt());
    assertTrue(session.nextRep("U D"));
    assertFalse(session.isCrossBuilt());
    assertTrue("no rep was spent by the redeal", session.getReps().isEmpty());
  }

  @Test
  public void theDrillEndsAfterItsReps() {
    CrossDrillSession session = session("D", 2);
    Hand hand = new Hand(session);
    assertTrue(hand.next("U D"));
    assertNotNull(hand.turn("D'"));
    assertFalse(session.isFinished());
    assertTrue(hand.next("U D"));
    assertNotNull(hand.turn("D'"));
    assertTrue(session.isFinished());
    assertFalse(session.nextRep("U D"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void aCaseDrillIsNotRunHere() {
    new CrossDrillSession(new DrillSpec("t", DrillSpec.Type.CASE_EXECUTION,
        DrillSpec.Delivery.VIRTUAL, java.util.Collections.singletonList("pll_t"),
        DrillSpec.Selection.ROUND_ROBIN, 1, 0, null));
  }

  /**
   * A rep keeps what was turned. The count is the score, but only the sequence says where the moves
   * over the shortest way went, which is the whole of what there is to work on after a cross.
   */
  @Test
  public void aRepKeepsTheTurnsThatBuiltTheCross() {
    CrossDrillSession session = session("D", 1);
    Hand hand = new Hand(session);
    assertTrue(hand.next("U D"));

    CrossDrillRep rep = hand.turn("D D D");
    assertNotNull(rep);
    assertEquals("every quarter turn, though the count folds two of them", 3, rep.getMoves().size());
    assertEquals("D D D", notation(rep.getMoves()));
  }

  /** Including a rep announced finished with no cross there, whose moves went somewhere else. */
  @Test
  public void anAnnouncedRepKeepsTheTurnsThatMissed() {
    CrossDrillSession session = session("D", 1);
    Hand hand = new Hand(session);
    assertTrue(hand.next("U D"));
    assertNull(hand.turn("R U"));

    CrossDrillRep rep = session.declareFinished();
    assertNotNull(rep);
    assertFalse(rep.isBuilt());
    assertEquals("R U", notation(rep.getMoves()));
  }

  /**
   * The turns are stamped on the cube's clock, so the instant the stored offsets are measured from
   * has to be on that clock too. The rep's own two figures would not show the error, both being
   * differences it cancels out of.
   */
  @Test
  public void theOffsetsAMoveIsStoredAgainstAreOnTheCubesClock() {
    CrossDrillSession session = session("D", 1);
    long hostShownAt = 100_000;
    long cubeBehind = 1_900;
    assertTrue(session.nextRep("U D"));
    session.markCaseShown(hostShownAt);

    long at = hostShownAt - cubeBehind + 700;
    CrossDrillRep rep = null;
    for (String token : "D D D".split(" ")) {
      rep = session.onMove(new CubeMove(Face.valueOf(token), false, at,
          Long.valueOf(at + cubeBehind)));
      at += GAP_MS;
    }
    assertNotNull(rep);
    assertEquals(700, rep.getMoves().get(0).getCubeTimestampMs() - rep.getShownAtMs());
    assertEquals(rep.getPlanningMs(),
        rep.getMoves().get(0).getCubeTimestampMs() - rep.getShownAtMs());
  }

  /** Turns made after a rep, trying the short way that was shown, belong to no rep. */
  @Test
  public void exploringAfterARepAddsToNothing() {
    CrossDrillSession session = session("D", 2);
    Hand hand = new Hand(session);
    assertTrue(hand.next("U D"));
    CrossDrillRep rep = hand.turn("D'");
    assertNotNull(rep);

    session.explore(new CubeMove(Face.R, false, 0));
    assertEquals("D'", notation(rep.getMoves()));
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

  private static CrossDrillSession session(String face, int reps) {
    return new CrossDrillSession(DrillSpec.cross("test", face, reps, 0, null));
  }

  /** The user's hands: turns arrive evenly spaced, and the clock runs on between reps. */
  private static final class Hand {

    private final CrossDrillSession session;
    private long clock = 10_000;
    private long shownAtMs;

    Hand(CrossDrillSession session) {
      this.session = session;
    }

    boolean next(String scramble) {
      if (!session.nextRep(scramble)) {
        return false;
      }
      shownAtMs = clock;
      session.markCaseShown(shownAtMs);
      return true;
    }

    /** Sit and look at the scramble before the first turn. */
    void look(long ms) {
      clock = shownAtMs + ms;
    }

    /** Turn, stopping the moment the cross is there. */
    CrossDrillRep turn(String moves) {
      for (CubeMove move : asReported(moves)) {
        CubeMove timed = new CubeMove(move.getFace(), move.isPrime(), clock);
        clock += GAP_MS;
        CrossDrillRep rep = session.onMove(timed);
        if (rep != null) {
          return rep;
        }
      }
      return null;
    }
  }

  /** Face turns as a cube reports them: a half turn is two quarters. */
  private static List<CubeMove> asReported(String moves) {
    List<CubeMove> reported = new ArrayList<CubeMove>();
    for (String token : moves.trim().split("\\s+")) {
      if (token.isEmpty()) {
        continue;
      }
      Face face = Face.valueOf(token.substring(0, 1));
      boolean prime = token.endsWith("'");
      int quarters = token.indexOf('2') >= 0 ? 2 : 1;
      for (int quarter = 0; quarter < quarters; quarter++) {
        reported.add(new CubeMove(face, prime, 0));
      }
    }
    return reported;
  }
}
