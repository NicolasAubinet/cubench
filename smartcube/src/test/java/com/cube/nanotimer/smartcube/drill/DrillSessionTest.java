package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.drill.DrillSpec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.F2LPairScrambles;
import com.cube.nanotimer.smartcube.step.FaceTurns;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
      Hand hand = new Hand(session);
      assertTrue(code, hand.next());
      DrillRep rep = hand.execute(inverse(session.getCurrentScramble()));
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

  /** Including the first rep, which had nothing to measure from while this ran between reps. */
  @Test
  public void recognitionRunsFromWhenTheCaseAppeared() {
    DrillSession session = new DrillSession(spec("pll_t", 2), noAlignment());
    Hand hand = new Hand(session);

    assertTrue(hand.next());
    hand.pause(600);
    DrillRep first = hand.execute(inverse(session.getCurrentScramble()));
    assertEquals(600, first.getRecognitionMs());
    assertEquals((first.getMoveCount() - 1) * GAP_MS, first.getExecutionMs());

    assertTrue(hand.next());
    hand.pause(1234);
    DrillRep second = hand.execute(inverse(session.getCurrentScramble()));
    assertEquals(1234, second.getRecognitionMs());
  }

  /**
   * A case is the same case whatever way round it is read, so the U that squares it up is the user
   * still reading it. Charging it to the turning made every drill of a case dealt off-square look
   * like a slower algorithm than the same case dealt straight.
   */
  @Test
  public void theAufBeforeAnAlgorithmIsLooking() {
    DrillSession session = new DrillSession(spec("pll_t", 2), noAlignment());
    Hand hand = new Hand(session);

    assertTrue(hand.next());
    hand.pause(500);
    DrillRep straight = hand.execute(inverse(session.getCurrentScramble()));

    assertTrue(hand.next());
    hand.pause(500);
    // Squared up and squared back: the cube is where it was, so the same algorithm still solves it.
    DrillRep aligned = hand.execute("U U' " + inverse(session.getCurrentScramble()));

    assertEquals("the AUF was charged to the turning",
        straight.getExecutionMs(), aligned.getExecutionMs());
    assertEquals("and it belongs to the looking",
        straight.getRecognitionMs() + 2 * GAP_MS, aligned.getRecognitionMs());
  }

  /** The AUF that finishes a case is the case being solved, so it stays inside the turning. */
  @Test
  public void theAufThatFinishesACaseIsTurning() {
    DrillSession session = new DrillSession(spec("pll_ja", 12), new Random(3));
    Hand hand = new Hand(session);
    while (hand.next()) {
      String solution = inverse(session.getCurrentScramble());
      int alignment = leadingAufMoves(solution);
      DrillRep rep = hand.execute(solution);
      assertNotNull(rep);
      assertEquals("execution runs to the last turn, whatever that turn was",
          (rep.getMoveCount() - alignment - 1) * GAP_MS, rep.getExecutionMs());
    }
  }

  /** How many turns at the head of a solution only put the case where it could be read. */
  private static int leadingAufMoves(String algorithm) {
    int alignment = 0;
    for (CubeMove move : asReported(algorithm)) {
      if (move.getFace() != Face.U) {
        break;
      }
      alignment++;
    }
    return alignment;
  }

  /**
   * The point of measuring from the case rather than from the rep before: a screen that holds the
   * solved cube for a beat must not bill that beat to the user's looking.
   */
  @Test
  public void aPauseBetweenRepsIsNobodysRecognition() {
    DrillSession session = new DrillSession(spec("pll_t", 2), noAlignment());
    Hand hand = new Hand(session);

    assertTrue(hand.next());
    hand.execute(inverse(session.getCurrentScramble()));

    hand.idle(5000); // the screen dwelling on the finish, nothing to do with the next case
    assertTrue(hand.next());
    hand.pause(300);
    assertEquals(300, hand.execute(inverse(session.getCurrentScramble())).getRecognitionMs());
  }

  /**
   * The turn a cube makes without ever saying so. A QiYi skips a state change during fast slices,
   * which is exactly what an H perm turned on M2 is made of, and the drill that missed one drew a
   * scrambled cube from there on and never read the case as solved. The state the cube does report
   * is what puts it back, whichever turn went missing.
   */
  @Test
  public void aTurnTheCubeNeverReportedIsRecoveredFromTheStateItDoes() {
    List<CubeMove> algorithm = asReported(inverse(scrambleFor("pll_h")));
    for (int dropped = 0; dropped < algorithm.size(); dropped++) {
      DrillSession session = new DrillSession(spec("pll_h", 1), new Random(5));
      assertTrue(session.nextRep());
      session.markCaseShown(10_000);
      CubieCube hands = new CubieCube(); // the user's own cube, wherever the drill's one stands
      assertFalse("the first state is only a seed", session.onState(state(hands)).isRepaired());

      DrillRep rep = null;
      long clock = 10_000;
      for (int i = 0; i < algorithm.size() && rep == null; i++) {
        CubeMove move = algorithm.get(i);
        hands.applyMove(move.getFace(), move.isPrime());
        if (i != dropped) {
          rep = session.onMove(new CubeMove(move.getFace(), move.isPrime(), clock += GAP_MS));
        }
        DrillSession.Correction correction = session.onState(state(hands));
        assertEquals("only the missed turn is a repair", i == dropped, correction.isRepaired());
        if (i == dropped) { // handed on, so whatever follows the turns can take it too
          CubieCube turn = new CubieCube();
          turn.applyMove(move.getFace(), move.isPrime());
          assertEquals(turn, correction.getMissed());
        }
        rep = rep == null ? correction.getRep() : rep;
      }
      assertNotNull("turn " + dropped + " went missing and the rep never ended", rep);
      assertTrue(session.isFinished());
    }
  }

  /** And the case is still the case: a repaired cube is the one the turns would have left. */
  @Test
  public void aRecoveredTurnLeavesTheSameCubeAsAReportedOne() {
    DrillSession reported = new DrillSession(spec("pll_z", 1), new Random(5));
    DrillSession missed = new DrillSession(spec("pll_z", 1), new Random(5));
    assertTrue(reported.nextRep());
    assertTrue(missed.nextRep());
    reported.markCaseShown(10_000);
    missed.markCaseShown(10_000);
    CubieCube hands = new CubieCube();
    missed.onState(state(hands));

    long clock = 10_000;
    List<CubeMove> algorithm = asReported(inverse(reported.getCurrentScramble()));
    for (int i = 0; i + 1 < algorithm.size(); i++) { // stopping short, so neither rep is over
      CubeMove move = new CubeMove(
          algorithm.get(i).getFace(), algorithm.get(i).isPrime(), clock += GAP_MS);
      hands.applyMove(move.getFace(), move.isPrime());
      reported.onMove(move);
      if (i % 3 != 0) { // every third turn is one the cube never sent
        missed.onMove(move);
      }
      missed.onState(state(hands));
    }
    assertEquals(reported.getFacelets(), missed.getFacelets());
  }

  /** A turn recovered while the case is still being drawn is dropped, the same as a reported one. */
  @Test
  public void aRecoveredTurnBeforeTheCaseIsShownIsDroppedToo() {
    DrillSession session = new DrillSession(spec("pll_t", 1), new Random(3));
    assertTrue(session.nextRep());
    String facelets = session.getFacelets();
    CubieCube hands = new CubieCube();
    session.onState(state(hands));
    for (CubeMove move : asReported(inverse(session.getCurrentScramble()))) {
      hands.applyMove(move.getFace(), move.isPrime());
    }
    assertFalse(session.onState(state(hands)).isRepaired());
    assertEquals("the case is still up, untouched", facelets, session.getFacelets());
  }

  /**
   * Turns made just before a case went up, delivered after it: the previous case's last turns, or
   * a start made too soon. They reach neither the case nor the state its dropped turns are measured
   * against, which is seeded by the next state the cube sends, a state that already holds them.
   */
  @Test
  public void turnsDeliveredAfterTheCaseWentUpLeaveItAsDealt() {
    DrillSession session = new DrillSession(spec("pll_t", 1), new Random(3));
    assertTrue(session.nextRep());
    String dealt = session.getFacelets();
    CubieCube hands = new CubieCube();
    hands.applyMove(Face.R, false);
    hands.applyMove(Face.U, false);

    session.markCaseShown(10_000);
    CubeMove r = new CubeMove(Face.R, false, 9_900);
    CubeMove u = new CubeMove(Face.U, false, 9_950);
    assertTrue(session.isFromBeforeTheCase(r));
    assertNull(session.onMove(r));
    assertNull(session.onMove(u));
    assertFalse(session.onState(state(hands)).isRepaired());

    assertEquals(dealt, session.getFacelets());
    assertFalse(session.isFromBeforeTheCase(new CubeMove(Face.R, false, 10_100)));
  }

  /** The scramble a case is dealt with, for a test that has to know it before the rep starts. */
  private static String scrambleFor(String code) {
    DrillSession session = new DrillSession(spec(code, 1), new Random(5));
    session.nextRep();
    return session.getCurrentScramble();
  }

  private static CubeState state(CubieCube cube) {
    return new CubeState(cube.toFaceCube());
  }

  /** A turn made before the case was drawn belongs to no rep, and must not start one. */
  @Test
  public void turnsBeforeTheCaseIsShownAreDropped() {
    DrillSession session = new DrillSession(spec("pll_t", 1), new Random(3));
    assertTrue(session.nextRep());
    for (CubeMove move : asReported(inverse(session.getCurrentScramble()))) {
      assertNull(session.onMove(new CubeMove(move.getFace(), move.isPrime(), 10_000)));
    }
    assertTrue("the case is still up, untouched", session.getReps().isEmpty());
  }

  /**
   * A cube stamps its moves on its own clock, fitted to host time when it connects and left alone
   * until it is seconds out. Read against a raw host timestamp, whatever the fit was out by came
   * off the recognition and went onto the execution: the rep this is drawn from came back as
   * nothing to recognise and four seconds to turn.
   */
  @Test
  public void recognitionIsReadOnTheClockTheMovesAreStampedOn() {
    DrillSession session = new DrillSession(spec("pll_t", 1), noAlignment());
    assertTrue(session.nextRep());
    long hostShownAt = 100_000;
    long cubeBehind = 1_900; // the cube's clock, still within its fit's tolerance of host time
    session.markCaseShown(hostShownAt);

    DrillRep rep = null;
    long at = hostShownAt - cubeBehind + 700; // turned 700ms after looking at the case
    for (CubeMove move : asReported(inverse(session.getCurrentScramble()))) {
      rep = session.onMove(new CubeMove(move.getFace(), move.isPrime(), at,
          Long.valueOf(at + cubeBehind)));
      at += GAP_MS;
    }
    assertNotNull(rep);
    assertEquals(700, rep.getRecognitionMs());
    assertEquals((rep.getMoveCount() - 1) * GAP_MS, rep.getExecutionMs());
  }

  /**
   * The turns that finish one case keep coming while the screen holds it, and one made then can
   * still be in flight when the next case goes up. It has to reach the cube, which really was
   * turned, but it is not the rep's first move: counted as one it left nothing to recognise.
   */
  @Test
  public void aTurnFromBeforeTheCaseWentUpDoesNotStartTheRep() {
    DrillSession session = new DrillSession(spec("pll_t", 1), noAlignment());
    assertTrue(session.nextRep());
    long shownAt = 50_000;
    session.markCaseShown(shownAt);

    // A turn of the previous case, made a beat before this one was dealt and delivered after it.
    assertNull(session.onMove(new CubeMove(Face.R, false, shownAt - 200)));
    assertNull(session.onMove(new CubeMove(Face.R, true, shownAt - 160)));

    long at = shownAt + 800;
    DrillRep rep = null;
    for (CubeMove move : asReported(inverse(session.getCurrentScramble()))) {
      rep = session.onMove(new CubeMove(move.getFace(), move.isPrime(), at));
      at += GAP_MS;
    }
    assertNotNull(rep);
    assertEquals(800, rep.getRecognitionMs());
    assertEquals((rep.getMoveCount() - 1) * GAP_MS, rep.getExecutionMs());
  }

  /**
   * The same case must not keep arriving the same way round, or the user learns the picture instead
   * of the case and a recognition drill measures nothing. Every draw takes its own two alignment
   * turns, so a Ja comes up facing all four ways.
   */
  @Test
  public void oneCaseIsDealtInVaryingAlignments() {
    DrillSession session = new DrillSession(spec("pll_ja", 24), new Random(3));
    Hand hand = new Hand(session);
    Set<String> positions = new HashSet<String>();
    while (hand.next()) {
      positions.add(session.getFacelets());
      hand.execute(inverse(session.getCurrentScramble()));
    }
    assertTrue("a Ja always stood the same way up: " + positions.size(), positions.size() >= 4);
  }

  /** A slip is not the same as not knowing the case, so putting it back costs nothing but time. */
  @Test
  public void resettingDealsTheSamePositionAgainAndSpendsNoRep() {
    DrillSession session = new DrillSession(spec("pll_t", 2), new Random(3));
    Hand hand = new Hand(session);
    assertTrue(hand.next());
    String scramble = session.getCurrentScramble();

    String position = session.getFacelets();

    assertNull("this must not finish it", hand.execute("R U R'"));
    hand.reset();
    assertEquals("the same scramble, not another draw of the same case",
        scramble, session.getCurrentScramble());
    assertEquals("and so the same way round, which a redo has to be",
        position, session.getFacelets());
    assertTrue("no rep spent", session.getReps().isEmpty());

    DrillRep rep = hand.execute(inverse(scramble));
    assertNotNull("the case was really put back", rep);
    assertEquals(1, rep.getResetCount());
    assertEquals(1, session.getReps().size());
  }

  /** The redo is the rep that counts, so the abandoned attempt's looking is not charged to it. */
  @Test
  public void recognitionRunsFromTheResetNotFromTheFirstAttempt() {
    DrillSession session = new DrillSession(spec("pll_t", 1), noAlignment());
    Hand hand = new Hand(session);
    assertTrue(hand.next());
    hand.pause(4000);
    hand.execute("R U R'");
    hand.reset();
    hand.pause(250);
    assertEquals(250, hand.execute(inverse(session.getCurrentScramble())).getRecognitionMs());
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
    while (hand.next()) {
      seen.add(session.getCurrentCase());
      assertNotNull(session.getCurrentCase(), hand.execute(inverse(session.getCurrentScramble())));
    }
    assertEquals(6, seen.size());
    assertEquals(new HashSet<String>(codes), new HashSet<String>(seen.subList(0, 3)));
    assertEquals(new HashSet<String>(codes), new HashSet<String>(seen.subList(3, 6)));
  }

  /**
   * A case whose rep measured nothing is dealt again inside the same pass, so that a drill is not
   * left with nothing to say about it: the figures leave out a rep that was restarted, and one that
   * was solved with the algorithm on screen. Five reps over four cases, so the pass is dealt out
   * whole and the extra showing has to be the one put back.
   */
  @Test
  public void aCaseWhoseRepMeasuredNothingComesRoundAgain() {
    assertEquals(2, timesDealt(true, false));
    assertEquals(2, timesDealt(false, true));
    assertEquals(1, timesDealt(false, false)); // and a clean one is spent, as it always was
  }

  /** How often the first case dealt comes up over a five-rep drill of four cases. */
  private static int timesDealt(boolean reset, boolean reveal) {
    DrillSpec spec = new DrillSpec("again", DrillSpec.Type.CASE_EXECUTION,
        DrillSpec.Delivery.VIRTUAL, Arrays.asList("pll_h", "pll_z", "pll_t", "pll_ua"),
        DrillSpec.Selection.ROUND_ROBIN, 5, 0, null);
    DrillSession session = new DrillSession(spec, new Random(7));
    Hand hand = new Hand(session);
    List<String> seen = new ArrayList<String>();
    while (hand.next()) {
      seen.add(session.getCurrentCase());
      if (seen.size() == 1) {
        if (reset) {
          hand.execute("R U R'");
          hand.reset();
        }
        if (reveal) {
          session.markRevealed();
        }
      }
      assertNotNull(session.getCurrentCase(), hand.execute(inverse(session.getCurrentScramble())));
    }
    assertEquals(5, seen.size());
    assertNotEquals("never as the very next case, which is what a reset already offers",
        seen.get(0), seen.get(1));
    return Collections.frequency(seen, seen.get(0));
  }

  /**
   * A skipped case is not dealt again, even where it was restarted or looked up first. Giving up on
   * a case is an answer about it, and the rep spends it.
   */
  @Test
  public void aSkippedCaseIsNotDealtAgain() {
    List<String> codes = Arrays.asList("pll_h", "pll_z", "pll_t", "pll_ua");
    DrillSpec spec = new DrillSpec("skip", DrillSpec.Type.CASE_EXECUTION,
        DrillSpec.Delivery.VIRTUAL, codes, DrillSpec.Selection.ROUND_ROBIN, 5, 0, null);
    DrillSession session = new DrillSession(spec, new Random(7));
    Hand hand = new Hand(session);
    List<String> seen = new ArrayList<String>();
    while (hand.next()) {
      seen.add(session.getCurrentCase());
      if (seen.size() == 1) {
        hand.execute("R U R'");
        hand.reset();
        session.markRevealed();
        assertTrue(session.abandon().isAbandoned());
      } else {
        assertNotNull(session.getCurrentCase(), hand.execute(inverse(session.getCurrentScramble())));
      }
    }
    assertEquals(5, seen.size());
    assertEquals("the skipped case is spent, not put back", 1,
        Collections.frequency(seen, seen.get(0)));
  }

  /** A case left to look its algorithms up spends the rep but comes round again, unlike a skip. */
  @Test
  public void aCaseLeftToLookUpComesRoundAgain() {
    DrillSpec spec = new DrillSpec("look", DrillSpec.Type.CASE_EXECUTION,
        DrillSpec.Delivery.VIRTUAL, Arrays.asList("pll_h", "pll_z", "pll_t", "pll_ua"),
        DrillSpec.Selection.ROUND_ROBIN, 5, 0, null);
    DrillSession session = new DrillSession(spec, new Random(7));
    Hand hand = new Hand(session);
    List<String> seen = new ArrayList<String>();
    while (hand.next()) {
      seen.add(session.getCurrentCase());
      if (seen.size() == 1) {
        DrillRep rep = session.lookUp();
        assertTrue(rep.isAbandoned());
        assertTrue(rep.wasRevealed());
      } else {
        assertNotNull(session.getCurrentCase(), hand.execute(inverse(session.getCurrentScramble())));
      }
    }
    assertEquals(5, seen.size());
    assertEquals(2, Collections.frequency(seen, seen.get(0)));
  }

  /**
   * <b>A drill of one rep per case deals every one of them, redo or no redo.</b> Putting a botched
   * case back costs the drill a rep, and where there was no spare one that rep was taken off the
   * end of the pass, leaving a case the user picked never dealt at all. A case still owed its first
   * turn outranks a second showing of one that has already had it.
   */
  @Test
  public void aDrillOfOneRepPerCaseDealsEveryCase() {
    List<String> codes = Arrays.asList("pll_h", "pll_z", "pll_t", "pll_ua");
    for (int seed = 0; seed < 20; seed++) {
      DrillSpec spec = new DrillSpec("all", DrillSpec.Type.CASE_EXECUTION,
          DrillSpec.Delivery.VIRTUAL, codes, DrillSpec.Selection.ROUND_ROBIN, codes.size(), 0,
          null);
      DrillSession session = new DrillSession(spec, new Random(seed));
      Hand hand = new Hand(session);
      Set<String> seen = new HashSet<String>();
      while (hand.next()) {
        seen.add(session.getCurrentCase());
        if (seen.size() == 1) {
          hand.execute("R U R'");
          hand.reset();
        }
        assertNotNull(session.getCurrentCase(), hand.execute(inverse(session.getCurrentScramble())));
      }
      assertEquals("seed " + seed, new HashSet<String>(codes), seen);
    }
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
    while (hand.next()) {
      if ("pll_h".equals(session.getCurrentCase())) {
        costly++;
      }
      assertNotNull(session.getCurrentCase(), hand.execute(inverse(session.getCurrentScramble())));
    }
    assertEquals(40, session.getReps().size());
    assertTrue("nine times the weight should show: " + costly, costly > 25);
  }

  private static final List<String> FIVE_PLLS =
      Arrays.asList("pll_h", "pll_z", "pll_t", "pll_ua", "pll_rb");

  /** The coach's drill: five cases, weighted with no weights, 3 wins over 5 clean reps to clear. */
  private static DrillSpec coachDrill(long rbTarget) {
    Map<String, Long> targets = new HashMap<String, Long>();
    for (String code : FIVE_PLLS) {
      targets.put(code, 60_000L);
    }
    targets.put("pll_rb", rbTarget);
    return new DrillSpec("coach", DrillSpec.Type.CASE_EXECUTION, DrillSpec.Delivery.VIRTUAL,
        FIVE_PLLS, DrillSpec.Selection.WEIGHTED, 40, 0, targets, 3, 5, null);
  }

  /** The order a whole drill dealt its cases in, every rep solved at once. */
  private static List<String> dealOrder(DrillSession session) {
    List<String> order = new ArrayList<String>();
    Hand hand = new Hand(session);
    while (hand.next()) {
      order.add(session.getCurrentCase());
      hand.pause(300);
      assertNotNull(hand.execute(inverse(session.getCurrentScramble())));
    }
    return order;
  }

  /** A weighted drill deals in passes too: a case cannot sit out while the others clear. */
  @Test
  public void aWeightedDrillDealsEveryCaseInEachPass() {
    for (int seed = 0; seed < 200; seed++) {
      List<String> order = dealOrder(new DrillSession(coachDrill(60_000), new Random(seed)));
      // The fifth pass is where cases clear and cleared ones are mixed back in
      for (int start = 0; start < 20; start += 5) {
        assertEquals("seed " + seed + ": " + order, new HashSet<String>(FIVE_PLLS),
            new HashSet<String>(order.subList(start, start + 5)));
      }
    }
  }

  @Test
  public void noCaseIsDealtTwiceInARowWhileThreeOrMoreAreLeft() {
    for (int seed = 0; seed < 200; seed++) {
      List<String> order = dealOrder(new DrillSession(coachDrill(60_000), new Random(seed)));
      for (int i = 1; i <= 20; i++) { // up to the fifth pass's first case: five still left
        assertNotEquals("seed " + seed + ": " + order, order.get(i - 1), order.get(i));
      }
    }
  }

  /** The last case left is mixed with cleared ones, so its turn cannot be seen coming. */
  @Test
  public void theLastCaseLeftIsMixedWithClearedOnes() {
    int last = 0;
    int mixedIn = 0;
    for (int seed = 0; seed < 100; seed++) {
      DrillSession session = new DrillSession(coachDrill(1), new Random(seed));
      List<String> order = dealOrder(session);
      assertEquals(40, order.size()); // Rb never beats 1 ms, so the ceiling ends it
      int alone = 0;
      while (!othersCleared(order, alone)) {
        alone++;
      }
      for (String code : order.subList(alone, order.size())) {
        if ("pll_rb".equals(code)) {
          last++;
        } else {
          mixedIn++;
        }
      }
    }
    double share = (double) last / (last + mixedIn);
    assertTrue("the last case should come up about half the time: " + share,
        share > 0.4 && share < 0.6);
  }

  /** Whether every case but Rb has had its five winning reps, and so cleared, by this point. */
  private static boolean othersCleared(List<String> order, int at) {
    for (String code : FIVE_PLLS) {
      if (!"pll_rb".equals(code) && Collections.frequency(order.subList(0, at), code) < 5) {
        return false;
      }
    }
    return true;
  }

  /** Weight puts extra copies of a case in a pass, never so many that the rest wait long. */
  @Test
  public void aHeavyCaseTakesAtMostThreeTurnsAPass() {
    List<String> codes = Arrays.asList("pll_h", "pll_z", "pll_t");
    Map<String, Long> weights = new HashMap<String, Long>();
    weights.put("pll_h", 20L);
    for (int seed = 0; seed < 50; seed++) {
      DrillSpec spec = new DrillSpec("w", DrillSpec.Type.CASE_EXECUTION,
          DrillSpec.Delivery.VIRTUAL, codes, DrillSpec.Selection.WEIGHTED, 15, 0, null);
      List<String> order = dealOrder(new DrillSession(spec, new Random(seed), weights));
      for (int start = 0; start < 15; start += 5) {
        List<String> pass = order.subList(start, start + 5);
        assertEquals("seed " + seed + ": " + order, 3, Collections.frequency(pass, "pll_h"));
        assertEquals(new HashSet<String>(codes), new HashSet<String>(pass));
      }
    }
  }

  @Test
  public void anUntouchedCaseLeftForPracticeIsDealtNextInAWeightedDrillToo() {
    DrillSession session = new DrillSession(coachDrill(60_000), new Random(5));
    Hand hand = new Hand(session);
    hand.next();
    String left = session.getCurrentCase();
    hand.practise("pll_aa");
    session.endPractice();
    hand.next();
    assertEquals(left, session.getCurrentCase());
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

  /** A case that keeps beating its target leaves, and the drill ends when none is left. */
  @Test
  public void aRetiringDrillEndsWhenEveryCaseHasBeatenItsTarget() {
    DrillSession session = new DrillSession(retiring(60_000, 60_000, 40, 3, 5), new Random(7));
    Map<String, Integer> dealt = solveAll(session, false);
    assertEquals("five showings each, the minimum, not three wins", 5, dealt.get("pll_h").intValue());
    assertEquals(5, dealt.get("pll_z").intValue());
    assertTrue(session.isFinished());
    assertTrue(session.getActiveCases().isEmpty());
  }

  /** A case that never beats its target stays, the one that does stops being dealt. */
  @Test
  public void aSlowCaseStaysUntilTheCeiling() {
    DrillSession session = new DrillSession(retiring(60_000, 1, 20, 3, 3), new Random(7));
    Map<String, Integer> dealt = solveAll(session, false);
    assertEquals(3, dealt.get("pll_h").intValue());
    assertEquals(20, session.getReps().size());
    assertEquals(0, session.getTally().getWins("pll_z"));
    assertEquals(Collections.singletonList("pll_z"), session.getActiveCases());
  }

  /** A rep solved with the algorithm on screen is no showing and no win. */
  @Test
  public void aRevealedRepDoesNotCountTowardsRetiring() {
    DrillSession session = new DrillSession(retiring(60_000, 60_000, 12, 1, 1), new Random(7));
    Map<String, Integer> dealt = solveAll(session, true);
    assertEquals(12, dealt.get("pll_h") + dealt.get("pll_z"));
    assertFalse(session.isRetired("pll_h"));
    assertEquals(0, session.getTally().getShowings("pll_h"));
  }

  /** Each case retires on three reps turned without the algorithm, from its very first rep. */
  @Test
  public void aLearningDrillRetiresEachCaseWithoutLooking() {
    DrillSession session = new DrillSession(learning(), new Random(7));
    Hand hand = new Hand(session);
    int reps = 0;
    while (hand.next()) {
      reps += session.isRetired(session.getCurrentCase()) ? 0 : 1;
      hand.pause(300);
      assertTrue(CaseTally.isClean(hand.execute(inverse(session.getCurrentScramble()))));
    }
    assertEquals("no time to beat, so every clean rep wins", 3,
        session.getTally().getWins("pll_h"));
    assertEquals(6, reps);
    assertTrue(session.isFinished());
  }

  /** Looking the algorithm up empties the case's run of dots. */
  @Test
  public void aLookUpStartsTheRunOver() {
    DrillSession session = new DrillSession(learning(), new Random(7));
    Hand hand = new Hand(session);
    int reps = 0;
    while (hand.next()) {
      String code = session.getCurrentCase();
      // The third rep is looked up, once, for pll_h.
      if ("pll_h".equals(code) && session.getTally().getWins(code) == 2
          && session.getTally().getShowings(code) == 2) {
        session.markRevealed();
      }
      reps += session.isRetired(code) ? 0 : 1; // a cleared case mixed back in is not its run
      hand.pause(300);
      hand.execute(inverse(session.getCurrentScramble()));
    }
    assertEquals("three each, plus the lost run of two and its look-up", 9, reps);
    assertTrue(session.isRetired("pll_h"));
  }

  /** A rep turned some other way than the algorithm being learned does not count. */
  @Test
  public void onlyTheTaughtAlgorithmCounts() {
    DrillSession session = new DrillSession(learning(), new Random(7));
    session.setTaughtAlgorithm("pll_h", "R U R' U'");
    Hand hand = new Hand(session);
    while (hand.next()) {
      hand.pause(300);
      DrillRep rep = hand.execute(inverse(session.getCurrentScramble()));
      assertEquals("pll_h".equals(rep.getCaseCode()), rep.isOffTaught());
    }
    assertFalse(session.isRetired("pll_h"));
    assertTrue(session.isRetired("pll_z"));
    assertEquals(0, session.getTally().getWins("pll_h"));
  }

  /**
   * Choosing an algorithm, the case is dealt at that algorithm's own angle, never at a random turn
   * of the layer, so it is run at once: whatever the draw, and on a yellow layer too.
   */
  @Test
  public void practisingAnAlgorithmDealsTheCaseAtItsAngle() {
    DrillSpec spec = DrillSpec.learning("coach-learn", DrillSpec.Type.CASE_EXECUTION,
        Arrays.asList("oll_33"), DrillSpec.Selection.ROUND_ROBIN, 40, 3,
        Collections.<String, String>emptyMap(), null);
    for (String algorithm : new String[] {"R U R' U' R' F R F'", "L' U' L U L F' L' F"}) {
      for (String layer : new String[] {"U", "D"}) {
        for (int seed = 0; seed < 8; seed++) {
          DrillSession session = new DrillSession(spec, new Random(seed), null, layer);
          Hand hand = new Hand(session);
          hand.practise("oll_33", algorithm);
          assertNotNull(algorithm + " on " + layer + ", seed " + seed,
              hand.execute(LayerRotation.toFace(algorithm, layer)));
        }
      }
    }
  }

  private static DrillSpec learning() {
    return DrillSpec.learning("coach-learn", DrillSpec.Type.CASE_EXECUTION,
        Arrays.asList("pll_h", "pll_z"), DrillSpec.Selection.ROUND_ROBIN, 40, 3,
        Collections.<String, String>emptyMap(), null);
  }

  /** How often each case was dealt before it cleared, every rep solved at once. */
  private static Map<String, Integer> solveAll(DrillSession session, boolean reveal) {
    Map<String, Integer> dealt = new HashMap<String, Integer>();
    Hand hand = new Hand(session);
    while (hand.next()) {
      String code = session.getCurrentCase();
      if (!session.isRetired(code)) {
        dealt.put(code, dealt.containsKey(code) ? dealt.get(code) + 1 : 1);
      }
      if (reveal) {
        session.markRevealed();
      }
      hand.pause(300);
      assertNotNull(code, hand.execute(inverse(session.getCurrentScramble())));
    }
    return dealt;
  }

  private static DrillSpec retiring(long targetH, long targetZ, int ceiling, int wins,
      int showings) {
    Map<String, Long> targets = new HashMap<String, Long>();
    targets.put("pll_h", targetH);
    targets.put("pll_z", targetZ);
    return new DrillSpec("coach", DrillSpec.Type.CASE_EXECUTION, DrillSpec.Delivery.VIRTUAL,
        Arrays.asList("pll_h", "pll_z"), DrillSpec.Selection.ROUND_ROBIN, ceiling, 0, targets,
        wins, showings, null);
  }

  private static void assertFinishes(String code, String algorithm) {
    DrillSession session = new DrillSession(spec(code, 1), noAlignment());
    Hand hand = new Hand(session);
    assertTrue(code, hand.next());
    DrillRep rep = hand.execute(algorithm);
    assertNotNull(code + " was not finished by " + algorithm, rep);
    assertEquals(code, rep.getCaseCode());
  }

  /** On every face a solver may finish on, and in whichever slot the pair was dealt. */
  @Test
  public void everyPairIsFinishedByPuttingItIn() {
    for (String face : new String[] {"U", "D", "R", "L", "F", "B"}) {
      Random random = new Random(face.hashCode());
      for (String code : F2LPairScrambles.cases()) {
        DrillSession session = new DrillSession(pairSpec(code), random, null, face);
        Hand hand = new Hand(session);
        assertTrue(code, hand.next());
        DrillRep rep = hand.execute(inverse(session.getCurrentScramble()));
        assertNotNull(code + " on " + face, rep);
        assertEquals(code, rep.getCaseCode());
      }
    }
  }

  /**
   * The rep ends when the pair is in, not when the cube is solved: undoing the scramble puts the
   * pair in before it gets to the last layer, and the turns after that are not the rep's.
   */
  @Test
  public void aPairRepEndsBeforeTheLastLayerIsSolved() {
    DrillSession session = new DrillSession(pairSpec("pair_a12"), new Random(4), null, "D");
    Hand hand = new Hand(session);
    assertTrue(hand.next());
    String solution = inverse(session.getCurrentScramble());
    DrillRep rep = hand.execute(solution);
    assertNotNull(rep);
    assertTrue(rep.getMoves().size() < asReported(solution).size());
    assertFalse(new CubieCube().toFaceCube().equals(session.getFacelets()));
  }

  /** A turn that leaves the pair out does not end the rep, wherever the pair was dealt. */
  @Test
  public void aPairRepDoesNotEndOnAnUnrelatedTurn() {
    for (int seed = 0; seed < 12; seed++) {
      DrillSession session = new DrillSession(pairSpec("pair_a12"), new Random(seed), null, "D");
      Hand hand = new Hand(session);
      assertTrue(hand.next());
      assertNull(hand.execute("U"));
    }
  }

  /**
   * A basic case colours the cross, the three slots already in and the pair; a stuck one leaves the
   * slot its piece is trapped in grey, so one slot fewer.
   */
  @Test
  public void aPairRepColoursTheCrossTheSlotsInAndThePair() {
    for (String face : new String[] {"U", "D", "R", "F"}) {
      DrillSession basic = new DrillSession(pairSpec("pair_12"), new Random(3), null, face);
      assertTrue(basic.nextRep());
      assertEquals(face, 4 + 3 + 1, basic.getShownEdges().length);
      assertEquals(face, 3 + 1, basic.getShownCorners().length);

      DrillSession stuck = new DrillSession(pairSpec("pair_a25"), new Random(3), null, face);
      assertTrue(stuck.nextRep());
      assertEquals(face, 4 + 2 + 1, stuck.getShownEdges().length);
      assertEquals(face, 2 + 1, stuck.getShownCorners().length);
    }
    DrillSession lastLayer = new DrillSession(spec("pll_t", 1), new Random(3));
    assertTrue(lastLayer.nextRep());
    assertNull(lastLayer.getShownEdges());
  }

  /**
   * On every face, two pairs are dealt and undoing the scramble puts both in, F2L whole and the
   * last layer still out. The case shown is the cross, the two slots in and the two dealt pairs.
   */
  @Test
  public void aLookAheadRepEndsWhenBothPairsAreIn() {
    for (String face : new String[] {"U", "D", "R", "L", "F", "B"}) {
      Random random = new Random(face.hashCode());
      for (int rep = 0; rep < 20; rep++) {
        DrillSession session = new DrillSession(lookAheadSpec(1), random, null, face);
        Hand hand = new Hand(session);
        assertTrue(hand.next());
        assertEquals(DrillSession.LOOKAHEAD_CASE, session.getCurrentCase());
        assertEquals(face, 4 + 2 + 2, session.getShownEdges().length);
        assertEquals(face, 2 + 2, session.getShownCorners().length);
        String solution = inverse(session.getCurrentScramble());
        DrillRep done = hand.execute(solution);
        assertNotNull(face, done);
        assertEquals(DrillSession.LOOKAHEAD_CASE, done.getCaseCode());
        assertNotNull(done.getPauseMs());
        assertTrue("the last layer is left", done.getMoves().size() < asReported(solution).size());
        assertTrue(session.isFinished());
      }
    }
  }

  /**
   * First version deals basic pairs only: exactly two slots out, the cross in, and no piece of
   * either pair sitting in a slot, its own excepted. Read off the cube with the cross on D.
   */
  @Test
  public void aLookAheadDealIsTwoBasicPairs() {
    Random random = new Random(11);
    for (int deal = 0; deal < 300; deal++) {
      DrillSession session = new DrillSession(lookAheadSpec(1), random, null, "U");
      assertTrue(session.nextRep());
      CubieCube cube = new CubieCube();
      FaceTurns.apply(cube, session.getCurrentScramble());
      int[] cp = new int[8];
      int[] co = new int[8];
      int[] ep = new int[12];
      int[] eo = new int[12];
      cube.toPermutation(cp, co, ep, eo);
      for (int edge = 4; edge < 8; edge++) {
        assertTrue("cross edge " + edge, ep[edge] == edge && eo[edge] == 0);
      }
      int slotsIn = 0;
      for (int slot = 0; slot < 4; slot++) {
        int corner = 4 + slot; // DFR DLF DBL DRB, and FR FL BL BR in the same order
        int edge = 8 + slot;
        if (cp[corner] == corner && co[corner] == 0 && ep[edge] == edge && eo[edge] == 0) {
          slotsIn++;
        }
        assertTrue("no other slot's corner in " + corner, cp[corner] == corner || cp[corner] < 4);
        assertTrue("no other slot's edge in " + edge, ep[edge] == edge || ep[edge] < 4);
      }
      assertEquals(session.getCurrentScramble(), 2, slotsIn);
    }
  }

  /**
   * A 700 ms look, the first pair, a 900 ms pause, the second pair: the four add up to the rep, and
   * it is judged up to the second pair's first turn.
   */
  @Test
  public void aLookAheadRepTimesThePauseBetweenThePairs() {
    DrillSession session = new DrillSession(lookAheadSpec(1), new Random(5), null, "U");
    Hand hand = new Hand(session);
    assertTrue(hand.next());
    hand.pause(700);
    DrillRep rep = hand.executeWithPause(inverse(session.getCurrentScramble()), 900);
    assertNotNull(rep);
    assertEquals(700, rep.getRecognitionMs());
    assertEquals(GAP_MS + 900, rep.getPauseMs().longValue());
    assertEquals(GAP_MS * (hand.turnsToFirstPair - 1), rep.getPairOneMs().longValue());
    assertEquals(rep.getExecutionMs(),
        rep.getPairOneMs() + rep.getPauseMs() + rep.getPairTwoMs());
    assertEquals(700 + rep.getPairOneMs() + rep.getPauseMs(),
        rep.getTimedMs(DrillSpec.Type.F2L_LOOKAHEAD));
  }

  /** Squaring the layer up first is part of the first pair, as in a solve's F2L, not the look. */
  @Test
  public void aLookAheadRepCountsALayerTurnInTheFirstPair() {
    DrillSession session = new DrillSession(lookAheadSpec(1), new Random(5), null, "U");
    Hand hand = new Hand(session);
    assertTrue(hand.next());
    hand.pause(500);
    DrillRep rep = hand.execute("U U' " + inverse(session.getCurrentScramble()));
    assertNotNull(rep);
    assertEquals(500, rep.getRecognitionMs());
  }

  /** Given up on before a pair is in, there is no pair time and no pause to report. */
  @Test
  public void aLookAheadRepGivenUpEarlyHasNoPause() {
    DrillSession session = new DrillSession(lookAheadSpec(2), new Random(5), null, "U");
    Hand hand = new Hand(session);
    assertTrue(hand.next());
    assertNull(hand.execute("U"));
    assertFalse(session.isFirstPairIn());
    DrillRep rep = session.abandon();
    assertTrue(rep.isAbandoned());
    assertNull(rep.getPairOneMs());
    assertNull(rep.getPauseMs());
    assertNull(rep.getPairTwoMs());
    assertEquals(rep.getTotalMs(), rep.getTimedMs(DrillSpec.Type.F2L_LOOKAHEAD));
  }

  /** Other drills carry none of the look-ahead times. */
  @Test
  public void otherDrillsHaveNoPairTimes() {
    DrillSession session = new DrillSession(pairSpec("pair_12"), new Random(3), null, "U");
    Hand hand = new Hand(session);
    assertTrue(hand.next());
    DrillRep rep = hand.execute(inverse(session.getCurrentScramble()));
    assertNull(rep.getPairOneMs());
    assertNull(rep.getPauseMs());
    assertFalse(session.isFirstPairIn());
  }

  /** Only the first few cases are dealt, and the next one comes in as one retires. */
  @Test
  public void aWorkingSetDealsFromTheHeaviestCasesFirst() {
    List<String> codes = Arrays.asList("pll_t", "pll_h", "pll_z", "pll_ua");
    DrillSpec spec = new DrillSpec("coach", DrillSpec.Type.CASE_EXECUTION,
        DrillSpec.Delivery.VIRTUAL, codes, DrillSpec.Selection.WEIGHTED, 40, 60_000,
        Collections.<String, Long>emptyMap(), 1, 1, null).withWorkingSet(2);
    Map<String, Long> weights = new HashMap<String, Long>();
    weights.put("pll_z", 9L);
    weights.put("pll_ua", 5L);
    DrillSession session = new DrillSession(spec, new Random(2), weights);
    assertEquals(Arrays.asList("pll_z", "pll_ua"), session.getActiveCases());

    Hand hand = new Hand(session);
    Set<String> dealt = new HashSet<String>();
    while (hand.next()) {
      dealt.add(session.getCurrentCase());
      assertTrue("in the set, or cleared and mixed back in",
          session.getActiveCases().contains(session.getCurrentCase())
              || session.isRetired(session.getCurrentCase()));
      assertNotNull(hand.execute(inverse(session.getCurrentScramble())));
    }
    assertEquals(new HashSet<String>(codes), dealt);
  }

  @Test
  public void aWorkingSetRidesInTheSpec() {
    DrillSpec spec = pairSpec("pair_a1").withWorkingSet(5);
    DrillSpec read = DrillSpec.fromJson(spec.toJson());
    assertEquals("a working set needs 4, not the learning drill's 5", 4, read.getSpecVersion());
    assertEquals(5, read.getWorkingSet());
    assertEquals(DrillSpec.Type.F2L_PAIR, read.getType());
    assertEquals(0, DrillSpec.fromJson(spec("pll_t", 1).toJson()).getWorkingSet());
  }

  /** Each case is dealt as two of its own, one per back slot; a code with no scramble is dropped. */
  @Test
  public void aBackSlotDrillDealsEachCaseIntoBothBackSlots() {
    DrillSession session = new DrillSession(backSlotSpec("pair_4", "pair_17", "pair_99"),
        new Random(1));
    assertEquals(Arrays.asList("pair_4_br", "pair_4_bl", "pair_17_br", "pair_17_bl"),
        session.getCases());
    assertEquals(Collections.singletonList("pair_99"), session.getUnknownCases());
  }

  /** The slot's own algorithm, turned from the grip the case is dealt in, puts the pair home. */
  @Test
  public void everyBackSlotAlgorithmFinishesItsDealtCase() {
    for (String code : F2LPairScrambles.basicCases()) {
      for (String slot : new String[] {"br", "bl"}) {
        String backCode = CaseAlgorithms.inBackSlot(code, slot);
        for (String face : new String[] {"U", "D"}) {
          DrillSession session = new DrillSession(backSlotSpec(code), noAlignment(), null, face);
          Hand hand = new Hand(session);
          while (hand.next() && !backCode.equals(session.getCurrentCase())) {
            session.abandon();
          }
          DrillRep rep = hand.execute(LayerRotation.toFace(
              CaseAlgorithms.shortestListed(backCode), face));
          assertNotNull(backCode + " on " + face, rep);
          assertEquals(backCode, rep.getCaseCode());
        }
      }
    }
  }

  /** Practice deals the case at the algorithm's own angle, so it is run straight away. */
  @Test
  public void aBackSlotCaseIsPractisedAtItsAlgorithmsAngle() {
    for (String alg : new String[] {"d L U' L'", "U L U' L'"}) {
      String code = alg.startsWith("d") ? "pair_1_br" : "pair_1_bl";
      DrillSession session = new DrillSession(backSlotSpec("pair_1"), new Random(5));
      Hand hand = new Hand(session);
      hand.practise(code, alg);
      DrillRep rep = hand.execute(alg);
      assertNotNull(alg, rep);
      assertTrue(rep.isPractice());
    }
  }

  /** A rep the cube was turned in is solved, and counts towards nothing. */
  @Test
  public void aRotatedBackSlotRepIsNoWin() {
    for (boolean turned : new boolean[] {false, true}) {
      DrillSession session = new DrillSession(backSlotSpec("pair_4"), noAlignment());
      session.setRotationReader((moves, shownHostMs, skewMs) -> turned);
      Hand hand = new Hand(session);
      assertTrue(hand.next());
      DrillRep rep = hand.execute(CaseAlgorithms.shortestListed(session.getCurrentCase()));
      assertNotNull(rep);
      assertEquals(turned, rep.isRotated());
      assertEquals(!turned, session.getTally().isWin(rep));
    }
  }

  private static DrillSpec backSlotSpec(String... codes) {
    return DrillSpec.learning("test", DrillSpec.Type.F2L_BACK_SLOT, Arrays.asList(codes),
        DrillSpec.Selection.ROUND_ROBIN, 400, 5, Collections.<String, String>emptyMap(), null);
  }

  private static DrillSpec pairSpec(String code) {
    return new DrillSpec("test", DrillSpec.Type.F2L_PAIR, DrillSpec.Delivery.VIRTUAL,
        Collections.singletonList(code), DrillSpec.Selection.ROUND_ROBIN, 1, 0, null);
  }

  private static DrillSpec lookAheadSpec(int reps) {
    return DrillSpec.lookAhead("test", reps, null);
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
  /**
   * A rep keeps what was turned, which is the difference between "you took twelve moves" and "you
   * used the wrong algorithm for it".
   */
  @Test
  public void aRepKeepsTheTurnsThatSolvedIt() {
    DrillSession session = new DrillSession(spec("pll_t", 1), new Random(5));
    Hand hand = new Hand(session);
    assertTrue(hand.next());
    String solution = inverse(session.getCurrentScramble());

    DrillRep rep = hand.execute(solution);
    assertNotNull(rep);
    assertEquals(rep.getMoveCount(), rep.getMoves().size());
    assertEquals(notation(asReported(solution)), notation(rep.getMoves()));
  }

  /** And keeps the last attempt's, as it keeps the last attempt's times. */
  @Test
  public void aResetRepKeepsOnlyTheTurnsThatFinallyWorked() {
    DrillSession session = new DrillSession(spec("pll_t", 1), new Random(3));
    Hand hand = new Hand(session);
    assertTrue(hand.next());
    assertNull("this must not finish it", hand.execute("R U R'"));
    hand.reset();
    String solution = inverse(session.getCurrentScramble());

    DrillRep rep = hand.execute(solution);
    assertNotNull(rep);
    assertEquals(notation(asReported(solution)), notation(rep.getMoves()));
  }

  /**
   * The rep's turns are stamped on the cube's clock, so the instant they are measured from has to
   * be on that clock too. Read against the raw host time the case was shown at, every stored offset
   * comes out by whatever the cube's fit to host time was out by, which the rep's own two figures
   * would not show: they are both differences and the error cancels.
   */
  @Test
  public void theOffsetsAMoveIsStoredAgainstAreOnTheCubesClock() {
    DrillSession session = new DrillSession(spec("pll_t", 1), noAlignment());
    assertTrue(session.nextRep());
    long hostShownAt = 100_000;
    long cubeBehind = 1_900;
    session.markCaseShown(hostShownAt);

    DrillRep rep = null;
    long at = hostShownAt - cubeBehind + 700;
    for (CubeMove move : asReported(inverse(session.getCurrentScramble()))) {
      rep = session.onMove(new CubeMove(move.getFace(), move.isPrime(), at,
          Long.valueOf(at + cubeBehind)));
      at += GAP_MS;
    }
    assertNotNull(rep);
    assertEquals(700, rep.getMoves().get(0).getCubeTimestampMs() - rep.getShownAtMs());
    assertEquals(rep.getRecognitionMs(),
        rep.getMoves().get(0).getCubeTimestampMs() - rep.getShownAtMs());
  }

  /** A skipped rep keeps whatever was tried before it was given up on, which may be nothing. */
  @Test
  public void anAbandonedRepKeepsWhatWasTried() {
    DrillSession session = new DrillSession(spec("pll_t", 1), new Random(3));
    Hand hand = new Hand(session);
    assertTrue(hand.next());
    hand.execute("R U R'");

    DrillRep rep = session.abandon();
    assertNotNull(rep);
    assertEquals(notation(asReported("R U R'")), notation(rep.getMoves()));
  }

  @Test
  public void aPracticeRepSpendsNoneOfTheDrillAndTheCaseComesBack() {
    DrillSession session = new DrillSession(spec("pll_t", 1), new Random(3));
    Hand hand = new Hand(session);

    hand.practise("pll_t");
    DrillRep rep = hand.execute(inverse(session.getCurrentScramble()));
    assertNotNull(rep);
    assertTrue(rep.isPractice());
    assertTrue(session.getReps().isEmpty());
    assertFalse(session.isFinished());

    assertTrue(hand.next()); // dealt again, however many reps the drill had
    assertEquals("pll_t", session.getCurrentCase());
    assertTrue(hand.execute(inverse(session.getCurrentScramble())).isPractice());

    session.endPractice();
    assertNull(session.getCurrentCase());
    assertTrue(hand.next());
    assertFalse(hand.execute(inverse(session.getCurrentScramble())).isPractice());
    assertTrue(session.isFinished());
  }

  /** Leaving for practice is no answer about the case that was up, so it is dealt next. */
  @Test
  public void anUntouchedCaseLeftForPracticeIsDealtNext() {
    DrillSession session = new DrillSession(
        new DrillSpec("test", DrillSpec.Type.CASE_EXECUTION, DrillSpec.Delivery.VIRTUAL,
            Arrays.asList("pll_t", "pll_y", "pll_v"), DrillSpec.Selection.ROUND_ROBIN, 3, 0,
            null), new Random(5));
    Hand hand = new Hand(session);
    hand.next();
    String left = session.getCurrentCase();

    hand.practise("pll_aa");
    session.endPractice();
    hand.next();

    assertEquals(left, session.getCurrentCase());
  }

  @Test
  public void aRepsTurnsAreWrittenAsTheCubeReportedThem() {
    DrillSession session = new DrillSession(spec("pll_t", 1), new Random(3));
    Hand hand = new Hand(session);
    hand.next();
    DrillRep rep = hand.execute("M2 U M2 U2 M2 U M2"); // an H: it never finishes a T

    assertNull(rep);
    rep = session.abandon();
    assertEquals(notation(asReported("M2 U M2 U2 M2 U M2")), rep.getTurned());
  }

  /** OLL 37's algorithm, and another of its algorithms as listed, led by a turn of the cube. */
  private static final String OLL_37 = "F R' F' R U R U' R'";
  private static final String OLL_37_LISTED_Y = "y F' r U r' U' r' F r";

  @Test
  public void anAlgorithmTurnedAloneCountsItsExecutionTime() {
    DrillSession session = new DrillSession(spec("oll_37", 1), new Random(3));
    Hand hand = new Hand(session);
    hand.practise("oll_37", OLL_37);
    DrillRep rep = hand.execute(OLL_37);

    assertNotNull(rep);
    assertEquals(AlgorithmTimes.Verdict.COUNTED, AlgorithmTimes.judge(rep, OLL_37, false));
    assertEquals("a slip the follower saw, even put right, is not the algorithm's time",
        AlgorithmTimes.Verdict.WRONG_MOVES, AlgorithmTimes.judge(rep, OLL_37, true));
  }

  /** Dealt from the front, judged against the spelling the list has, leading y and all. */
  @Test
  public void aListedSpellingWithItsTurnOfTheCubeStillCounts() {
    String fromTheFront = "F' r U r' U' r' F r";
    DrillSession session = new DrillSession(spec("oll_37", 1), new Random(3));
    Hand hand = new Hand(session);
    hand.practise("oll_37", fromTheFront);
    DrillRep rep = hand.execute(fromTheFront);

    assertNotNull(rep);
    assertEquals(AlgorithmTimes.Verdict.COUNTED,
        AlgorithmTimes.judge(rep, OLL_37_LISTED_Y, null));
    assertEquals("another algorithm's turns are not this one's time",
        AlgorithmTimes.Verdict.WRONG_MOVES, AlgorithmTimes.judge(rep, OLL_37, null));
    assertEquals("a follower that kept up, dropped turns and all, outranks the recorded turns",
        AlgorithmTimes.Verdict.COUNTED, AlgorithmTimes.judge(rep, OLL_37, false));
  }

  @Test
  public void aResetRepIsNotTheAlgorithmsTime() {
    DrillSession session = new DrillSession(spec("oll_37", 1), new Random(3));
    Hand hand = new Hand(session);
    hand.practise("oll_37", OLL_37);
    assertNull(hand.execute("F R'"));
    hand.reset();
    DrillRep rep = hand.execute(OLL_37);

    assertNotNull(rep);
    assertEquals(AlgorithmTimes.Verdict.RESET, AlgorithmTimes.judge(rep, OLL_37, false));
  }

  /**
   * In practice the same case comes next, so a fast user turns it before it is up: those turns are
   * the next rep's, timed from the first of them, and the case is not left behind the user's hands.
   */
  @Test
  public void turnsBeforeTheNextPracticeRepIsShownAreItsOwn() {
    DrillSession session = new DrillSession(spec("oll_37", 1), new Random(3));
    Hand hand = new Hand(session);
    hand.practise("oll_37", OLL_37);
    assertNotNull(hand.execute(OLL_37));
    String dealt = scrambledBy(session, OLL_37);

    long startedAt = hand.clock;
    assertNull(hand.execute("F R'")); // during the beat, no case up
    assertTrue(session.nextRep());
    DrillSession.Shown shown = session.showCase(hand.clock + 300);
    assertEquals(2, shown.getTurns().size());
    assertNull(shown.getRep());
    assertNotEquals("the case has the two turns on it", dealt, session.getFacelets());

    DrillRep rep = hand.execute("F' R U R U' R'");
    assertNotNull(rep);
    assertEquals(8, rep.getMoveCount());
    assertEquals(0, rep.getRecognitionMs());
    assertEquals(7 * GAP_MS, rep.getExecutionMs());
    assertEquals("shown no later than its first turn", startedAt, rep.getShownAtMs());
    assertEquals(AlgorithmTimes.Verdict.COUNTED, AlgorithmTimes.judge(rep, OLL_37, false));
  }

  /** One made in the beat but delivered once the case is up is the rep's too, not a stray. */
  @Test
  public void aBeatTurnDeliveredLateStillCountsInPractice() {
    DrillSession session = new DrillSession(spec("oll_37", 1), new Random(3));
    Hand hand = new Hand(session);
    hand.practise("oll_37", OLL_37);
    assertNotNull(hand.execute(OLL_37));

    assertTrue(session.nextRep());
    long turnedAt = hand.clock;
    assertTrue(session.showCase(turnedAt + 200).getTurns().isEmpty());
    CubeMove late = new CubeMove(Face.F, false, turnedAt);
    assertFalse(session.isFromBeforeTheCase(late));
    assertNull(session.onMove(late));
    hand.clock = turnedAt + GAP_MS;
    DrillRep rep = hand.execute("R' F' R U R U' R'");
    assertNotNull(rep);
    assertEquals(8, rep.getMoveCount());
  }

  /** A turn the cube never reported in the beat is recovered for the next rep like any other. */
  @Test
  public void aTurnMissedBetweenPracticeRepsIsRecovered() {
    DrillSession session = new DrillSession(spec("oll_37", 1), new Random(3));
    Hand hand = new Hand(session);
    hand.practise("oll_37", OLL_37);
    CubieCube hands = new CubieCube();
    session.onState(state(hands)); // seeds what a dropped turn is measured against
    for (CubeMove move : asReported(OLL_37)) {
      hands.applyMove(move.getFace(), move.isPrime());
    }
    assertNotNull(hand.execute(OLL_37));

    hands.applyMove(Face.F, false);
    assertNull(session.onMove(new CubeMove(Face.F, false, hand.clock)));
    hands.applyMove(Face.R, true); // turned, never reported
    assertFalse(session.onState(state(hands)).isRepaired());
    assertTrue(session.hasCarriedTurns());

    assertTrue(session.nextRep());
    DrillSession.Shown shown = session.showCase(hand.clock + 100);
    assertEquals(2, shown.getTurns().size());
    assertNotNull(shown.getTurns().get(1).getMissed());
    hand.clock += 2 * GAP_MS;
    assertNotNull("the recovered turn is on the case", hand.execute("F' R U R U' R'"));
  }

  /** Outside practice the next case is unknown, so a turn before it is up is still not for it. */
  @Test
  public void turnsBetweenDrillRepsAreStillDropped() {
    DrillSession session = new DrillSession(spec("oll_37", 2), new Random(3));
    Hand hand = new Hand(session);
    assertTrue(hand.next());
    assertNotNull(hand.execute(inverse(session.getCurrentScramble())));
    assertFalse(session.isCarrying());

    assertNull(hand.execute("F R'"));
    assertTrue(session.nextRep());
    String dealt = session.getFacelets();
    assertTrue(session.showCase(hand.clock).getTurns().isEmpty());
    assertEquals(dealt, session.getFacelets());
  }

  /** A case dealt afresh, for another algorithm tried, is not the one the beat's turns were for. */
  @Test
  public void aPracticeCaseDealtAfreshDropsTheBeatsTurns() {
    DrillSession session = new DrillSession(spec("oll_37", 1), new Random(3));
    Hand hand = new Hand(session);
    hand.practise("oll_37", OLL_37);
    assertNotNull(hand.execute(OLL_37));
    assertNull(hand.execute("F R'"));

    session.dropCarriedTurns();
    assertTrue(session.nextRep());
    String dealt = session.getFacelets();
    assertTrue(session.showCase(hand.clock).getTurns().isEmpty());
    assertEquals(dealt, session.getFacelets());
    assertNotNull(hand.execute(OLL_37));
  }

  /** A listed spelling led by a turn of the cube is dealt so its turns run without that turn. */
  @Test
  public void aListedSpellingLedByATurnOfTheCubeIsDealtFromItsFront() {
    for (int seed = 0; seed < 8; seed++) {
      DrillSession session = new DrillSession(spec("oll_37", 1), new Random(seed));
      Hand hand = new Hand(session);
      hand.practise("oll_37", OLL_37_LISTED_Y);
      assertNotNull("seed " + seed, hand.execute("F' r U r' U' r' F r"));
    }
  }

  /**
   * Every F2L algorithm, practised, is dealt so its turns after the leading y run as written: the
   * pair is spun into the slot that y would have brought to front right. On D, only the ones with
   * no rotation left, which the test cannot relabel.
   */
  @Test
  public void everyPairAlgorithmIsDealtSoItRunsWithoutItsLeadingTurn() {
    StringBuilder wrong = new StringBuilder();
    for (String code : F2LPairScrambles.cases()) {
      for (CaseAlgorithms.Listed listed : CaseAlgorithms.shown(code)) {
        String algorithm = listed.getMoves();
        String run = LastLayerScrambles.withoutLeadingY(algorithm);
        for (String layer : new String[] {"U", "D"}) {
          if (layer.equals("D") && run.matches(".*[xyz].*")) {
            continue;
          }
          DrillSession session = new DrillSession(pairSpec(code), new Random(7), null, layer);
          Hand hand = new Hand(session);
          hand.practise(code, algorithm);
          if (hand.execute(LayerRotation.toFace(run, layer)) == null) {
            wrong.append(code).append(' ').append(algorithm).append(" on ").append(layer)
                .append('\n');
          }
        }
      }
    }
    assertEquals("", wrong.toString());
  }

  /** The case an algorithm finishes, as the session deals it for practice. */
  private static String scrambledBy(DrillSession session, String algorithm) {
    DrillSession fresh = new DrillSession(session.getSpec(), new Random(3));
    fresh.startPractice(session.getPracticeCase(), algorithm);
    return fresh.getFacelets();
  }

  private static final class Hand {

    private final DrillSession session;
    private long clock = 10_000;
    private long shownAtMs;

    Hand(DrillSession session) {
      this.session = session;
    }

    /** Take the next case and put it in front of the user, which is when its rep starts. */
    boolean next() {
      if (!session.nextRep()) {
        return false;
      }
      shownAtMs = clock;
      session.markCaseShown(shownAtMs);
      return true;
    }

    /** Practise one case, which is up at once. */
    void practise(String code) {
      practise(code, null);
    }

    /** Practise one case with an algorithm being tried for it, dealt at that algorithm's angle. */
    void practise(String code, String algorithm) {
      session.startPractice(code, algorithm);
      shownAtMs = clock;
      session.markCaseShown(shownAtMs);
    }

    /** Put the case back and look at it afresh, which is where its recognition now runs from. */
    void reset() {
      session.resetRep();
      shownAtMs = clock;
      session.markCaseShown(shownAtMs);
    }

    /** Time passing with no case up, which belongs to nobody's recognition. */
    void idle(long ms) {
      clock += ms;
    }

    /** Sit and look at the case for a while before the rep's first turn. */
    void pause(long ms) {
      clock = shownAtMs + ms;
    }

    /** Execute an algorithm, stopping the moment the rep is finished. */
    DrillRep execute(String algorithm) {
      for (CubeMove move : asReported(algorithm)) {
        CubeMove timed = new CubeMove(move.getFace(), move.isPrime(), clock);
        clock += GAP_MS;
        DrillRep rep = session.onMove(timed);
        if (rep != null) {
          return rep;
        }
      }
      return null;
    }

    /** Turns the moves needed to put the first look-ahead pair in, before the pause. */
    int turnsToFirstPair;

    /** {@link #execute}, stopping to look for {@code pauseMs} once the first pair is in. */
    DrillRep executeWithPause(String algorithm, long pauseMs) {
      int turns = 0;
      for (CubeMove move : asReported(algorithm)) {
        CubeMove timed = new CubeMove(move.getFace(), move.isPrime(), clock);
        clock += GAP_MS;
        turns++;
        boolean wasIn = session.isFirstPairIn();
        DrillRep rep = session.onMove(timed);
        if (rep != null) {
          return rep;
        }
        if (!wasIn && session.isFirstPairIn()) {
          turnsToFirstPair = turns;
          clock += pauseMs;
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
