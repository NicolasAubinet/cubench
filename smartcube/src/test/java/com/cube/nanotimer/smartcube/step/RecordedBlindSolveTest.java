package com.cube.nanotimer.smartcube.step;

import static com.cube.nanotimer.smartcube.step.PieceMark.HOME;
import static com.cube.nanotimer.smartcube.step.PieceMark.TOUCHED;
import static com.cube.nanotimer.smartcube.step.PieceMark.WRONG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * The detector against real blindfolded solves, replayed from their scrambles and stored moves.
 *
 * <p>These are what the design was settled on and what it has to keep clearing. The synthetic
 * fixtures next door are clean three-cycles played end to end; a real solve is not, and every rule
 * this detector has was put there by one of these solves breaking an earlier one.
 *
 * <p>Rotation tokens are skipped — a whole-cube rotation moves no piece, and the cube reports it
 * only so the moves can be written the way the solver made them.
 */
public class RecordedBlindSolveTest {

  /** The last move of solve 146, and the only moment its cube was ever actually solved. */
  private static final long SOLVED_AT_MS = 88164;

  private final CubieCube cube = new CubieCube();
  private final BlindStepDetector detector = new BlindStepDetector();

  @Test
  public void readsTheAlgorithmsOfASolveAndThePieceTypeEachBelongsTo() {
    replay(RecordedBlindSolve.SCRAMBLE, RecordedBlindSolve.MOVES, Long.MAX_VALUE);

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertEquals(3, detector.stepCount());
    assertEquals("memo", detector.stepName(0));
    assertEquals("edges", detector.stepName(1));
    assertEquals("corners", detector.stepName(2));
    // Memorisation holds no algorithm; the rest is one part per algorithm, named for what it solved.
    assertEquals(0, detector.subStepCount(0));
    assertEquals(6, detector.subStepCount(1));
    assertEquals(4, detector.subStepCount(2));
    assertEquals("UF-DR-LU", detector.subStepName(1, 0));
    // Three corners turned where they stand, the buffer among them and said first: a twist moving
    // as many pieces as a commutator, and read as one by what it left rather than what it touched.
    assertEquals("twist:FUR-LUF-BUL", detector.subStepName(2, 3));
    // The corners end where the cube came out, not where they first happened to line up.
    assertEquals(SOLVED_AT_MS, (long) detector.getStepTimestampMs(2));
  }

  /**
   * The corners come home 3.7 seconds before the end, while three edges are still out, and the
   * solver turns fourteen more moves after it. Counted rather than read as algorithms, that moment
   * says solved — and everything after it falls outside the breakdown.
   */
  @Test
  public void doesNotCallTheSolveFinishedWhereTheTwoTypesWereNeverHomeTogether() {
    replay(RecordedBlindSolve.SCRAMBLE, RecordedBlindSolve.MOVES, SOLVED_AT_MS - 1);

    assertFalse(detector.isComplete());
  }

  @Test
  public void readsASolveWithNoParityAsTwoPieceTypesAndNothingElse() {
    replay(RecordedBlindSolve.SCRAMBLE_163, RecordedBlindSolve.MOVES_163, Long.MAX_VALUE);

    assertTrue(detector.matchesMethod());
    assertEquals(3, detector.stepCount()); // an even permutation leaves no parity to fix
    assertEquals(6, detector.subStepCount(1));
    assertEquals(3, detector.subStepCount(2));
  }

  /** Solve 164 has an edge flip and a corner twist: algorithms that move two pieces, not three. */
  @Test
  public void countsAFlipAndATwistAsAlgorithmsOfTheirOwn() {
    replay(RecordedBlindSolve.SCRAMBLE_164, RecordedBlindSolve.MOVES_164, Long.MAX_VALUE);

    assertTrue(detector.matchesMethod());
    assertEquals(6, detector.subStepCount(1));
    assertEquals(4, detector.subStepCount(2));
    // Flipped and twisted where they stand: nothing was shot anywhere, so the buffer being among
    // them is the algorithm's doing and it is named, and the name says what was done to them.
    assertEquals("flip:UF-BL", detector.subStepName(1, 5)); // the two edges flipped in place
    assertEquals("twist:RUF-LDB", detector.subStepName(2, 3)); // the two corners twisted
  }

  /**
   * The solve of 2026-08-31, whose last corner algorithm twists the buffer and two others: it opens
   * on the buffer, and not wherever slot order happens to leave it.
   */
  @Test
  public void opensATwistOnTheBufferItTurned() {
    replay(RecordedBlindSolve.SCRAMBLE_TWIST_OFF_THE_BUFFER,
        RecordedBlindSolve.MOVES_TWIST_OFF_THE_BUFFER, Long.MAX_VALUE);

    assertEquals("UFR-DFL-UBR", detector.subStepName(2, 0)); // the buffer every other name opens on
    assertEquals("twist:RUF-BUL-LDB", detector.subStepName(2, 4));
  }

  /**
   * Solve 165 is the awkward one: an odd scramble, so it ends on a parity — and part way through the
   * edges the solver spotted a mistake and took the algorithm back. An undo is an algorithm like any
   * other and belongs to the piece type it interrupted; what it was worth is nothing, which is what
   * naming it says. Demanding that an algorithm make progress leaves it unread, and the state
   * everything after is compared against is then one the solve has abandoned.
   *
   * <p>The undo is the algorithm that <em>returns</em> the cube to where the one before it started,
   * not the one that gained nothing — those are two different algorithms, and the one that gained
   * nothing still shot a cycle, which is what it is named for.
   */
  @Test
  public void readsAParityAndTheAlgorithmsTheSolverUndid() {
    replay(RecordedBlindSolve.SCRAMBLE_165, RecordedBlindSolve.MOVES_165, Long.MAX_VALUE);

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertEquals(4, detector.stepCount());
    assertEquals("parity", detector.stepName(3));
    assertEquals(1, detector.subStepCount(3));
    // The one algorithm that puts back two of each, which is all a parity ever is — and it is said
    // as the two swaps it made rather than as one four-piece cycle, because that is what it was.
    assertEquals("UFR-DFL + UF-UR", detector.subStepName(3, 0));

    List<String> edges = new ArrayList<String>();
    for (int part = 0; part < detector.subStepCount(1); part++) {
      edges.add(detector.subStepName(1, part));
    }
    assertEquals(8, edges.size());
    assertEquals("UF-UL-UR", edges.get(2)); // the one that gained nothing, said by the cycle it shot
    assertEquals(1, Collections.frequency(edges, "undo")); // and the one that took it back
  }

  /**
   * A commutator is named by the cycle it shot — the buffer it started from and then the two
   * targets — and each of those is a sticker rather than a piece. Solve 163's last corner algorithm
   * closes a cycle, putting all three home, and is said in the order they were shot all the same.
   */
  @Test
  public void namesAnAlgorithmByTheStickersItsCycleWasShotTo() {
    replay(RecordedBlindSolve.SCRAMBLE_163, RecordedBlindSolve.MOVES_163, Long.MAX_VALUE);

    assertEquals("UFR-BUL-UBR", detector.subStepName(2, 2));
    // The same on the edges: three pieces home, the buffer among them.
    assertEquals("UF-UB-UR", detector.subStepName(1, 5));
    // A cycle break solves only its second target -- the first receives the buffer's piece, which
    // does not belong there. It is still where the buffer was shot, so it is still named.
    assertEquals("UF-RU-LU", detector.subStepName(1, 1));
  }

  /**
   * Solve 184 went wrong on its opening algorithm, before any piece type had got anywhere. That
   * leaves an algorithm gaining nothing with no stretch behind it to belong to, and standing alone
   * it opened the solve with a step that is not a piece type — it belongs to the stretch it
   * precedes. A cycle broken into on the first algorithm of a clean solve does the same thing.
   *
   * <p>The misfire is two algorithms, not one: the solver shot at a pair, took it straight back, and
   * then shot at the same pair again and kept it. Both of the first two gained nothing, so both
   * belong to the stretch they precede.
   *
   * <p>The names say what went wrong: the same cycle both times, but shot first to {@code RF} where
   * it wanted {@code FR} — the right slot, the wrong sticker, which leaves the piece flipped and is
   * why the algorithm gained nothing.
   */
  @Test
  public void keepsAMisfiredOpeningInsideTheStretchItPrecedes() {
    replay(RecordedBlindSolve.SCRAMBLE_184, RecordedBlindSolve.MOVES_184, Long.MAX_VALUE);

    assertEquals(4, detector.stepCount()); // memo, edges, corners, parity -- and nothing else
    assertEquals("edges", detector.stepName(1));
    assertEquals("UF-UR-RF", detector.subStepName(1, 0)); // the misfire, inside the edges it precedes
    assertEquals("undo", detector.subStepName(1, 1)); // taken back to exactly where it started
    assertEquals("UF-UR-FR", detector.subStepName(1, 2)); // and shot again, this time kept
    for (int step = 1; step < detector.stepCount(); step++) {
      for (int part = 0; part < detector.subStepCount(step); part++) {
        String name = detector.subStepName(step, part);
        assertTrue("every algorithm names what it was shot at: " + name,
            name.equals("undo") || name.contains("-"));
      }
    }
  }

  /**
   * Solve 211 is the first captured with a parity, and the one that says an orientation is pieces
   * <b>turned where they stand</b>, not pieces turned <b>at home</b>. Its edges end on a flip of the
   * buffer and {@code FL} — and on an odd solve the buffer holds a foreign piece right up until the
   * parity puts it right, so one of the two was nowhere near home.
   *
   * <p>Read as pieces-at-home it was no flip at all, and fell through to being named for the single
   * piece it happened to solve: {@code FL}, on its own. A flip of one piece does not exist, which is
   * how the owner spotted it.
   */
  @Test
  public void readsAFlipOfTheBufferOnASolveWhoseBufferIsNotHomeYet() {
    replay(RecordedBlindSolve.SCRAMBLE_211, RecordedBlindSolve.MOVES_211, Long.MAX_VALUE);

    assertEquals(4, detector.stepCount()); // memo, edges, corners, parity
    assertEquals("flip:UF-FL", detector.subStepName(1, 5));
    // Both pairs of the parity open on the buffer of their type, as every other name does.
    assertEquals("UFR-UBL + UF-UR", detector.subStepName(3, 0));
    // Both pieces, always: an orientation turns a pair, and naming one of them names nothing.
    for (int step = 1; step < detector.stepCount(); step++) {
      for (int part = 0; part < detector.subStepCount(step); part++) {
        String name = detector.subStepName(step, part);
        assertTrue("no algorithm is one piece: " + name, name.equals("undo") || name.contains("-"));
      }
    }
  }

  /**
   * Which of an algorithm's pieces it put home, on the solve that has one of everything. A normal
   * commutator lands both the targets it was shot at, and only they. One that <b>breaks into a new
   * cycle</b> lands one of the two: the break-in target takes a piece that belongs elsewhere, and
   * which of them it is says where the break fell — the second where the algorithm closed the old
   * cycle before breaking in, the first where the buffer was shot straight into the new one.
   *
   * <p><b>The buffer is never home before the parity</b> — it holds a foreign piece until the parity
   * puts it right — so no algorithm of this solve lands the piece it was shot from, the flip that
   * turns the buffer included. Correct, and the thing that reads as a bug first time.
   */
  @Test
  public void saysWhichOfAnAlgorithmsPiecesItPutHome() {
    replay(RecordedBlindSolve.SCRAMBLE_211, RecordedBlindSolve.MOVES_211, Long.MAX_VALUE);

    assertEquals("UF-DB-BR", detector.subStepName(1, 0));
    assertEquals(Arrays.asList(TOUCHED, HOME, HOME), detector.subStepPieceMarks(1, 0));
    assertEquals("UF-UB-RU", detector.subStepName(1, 4)); // closed at UB and broke in at RU
    assertEquals(Arrays.asList(TOUCHED, HOME, TOUCHED), detector.subStepPieceMarks(1, 4));
    assertEquals("UFR-UBR-LUB", detector.subStepName(2, 1)); // and the same on the corners
    assertEquals(Arrays.asList(TOUCHED, HOME, TOUCHED), detector.subStepPieceMarks(2, 1));
    assertEquals("flip:UF-FL", detector.subStepName(1, 5));
    assertEquals(Arrays.asList(TOUCHED, HOME), detector.subStepPieceMarks(1, 5));
    assertEquals("UFR-UBL + UF-UR", detector.subStepName(3, 0));
    assertEquals(Arrays.asList(HOME, HOME, HOME, HOME), detector.subStepPieceMarks(3, 0));
  }

  /**
   * A misfire puts nothing home, which is the whole of what it did wrong: solve 184 shoots the same
   * cycle twice, first to {@code RF} where it wanted {@code FR}, and only the second is worth
   * anything. An undo is not made of pieces at all, so it has none to mark.
   *
   * <p>The shot that replaced it broke into its cycle, this solve having none open yet: its first
   * target takes the buffer's piece, which does not belong there, and only its second lands.
   *
   * <p>A solve without a parity brings its buffer home, and then the piece it was shot from is
   * marked like any other — the closing cycle of solve 163 lands all three.
   */
  @Test
  public void marksNothingOnAnAlgorithmThatPutNothingHome() {
    replay(RecordedBlindSolve.SCRAMBLE_184, RecordedBlindSolve.MOVES_184, Long.MAX_VALUE);

    assertEquals("UF-UR-RF", detector.subStepName(1, 0));
    assertEquals(Arrays.asList(TOUCHED, TOUCHED, TOUCHED), detector.subStepPieceMarks(1, 0));
    assertTrue(detector.subStepPieceMarks(1, 1).isEmpty()); // the undo
    assertEquals("UF-UR-FR", detector.subStepName(1, 2));
    assertEquals(Arrays.asList(TOUCHED, TOUCHED, HOME), detector.subStepPieceMarks(1, 2));

    RecordedBlindSolveTest even = new RecordedBlindSolveTest();
    even.replay(RecordedBlindSolve.SCRAMBLE_163, RecordedBlindSolve.MOVES_163, Long.MAX_VALUE);
    assertEquals("UF-UB-UR", even.detector.subStepName(1, 5));
    assertEquals(Arrays.asList(HOME, HOME, HOME), even.detector.subStepPieceMarks(1, 5));
  }

  // Solve 146 ends on a three-corner twist, so stopped before that algorithm it stands with those
  // three corners turned where they belong.
  @Test
  public void saysWhatTheCubeWasLeftIn() {
    replay(RecordedBlindSolve.SCRAMBLE, RecordedBlindSolve.MOVES, Long.MAX_VALUE);
    assertEquals(BlindResidual.Shape.SOLVED, detector.getResidual().getShape());
    long lastLandingMs = detector.getSubStepTimestampMs(2, detector.subStepCount(2) - 2);
    assertEquals("twist:FUR-LUF-BUL", detector.subStepName(2, detector.subStepCount(2) - 1));

    RecordedBlindSolveTest stoppedShort = new RecordedBlindSolveTest();
    stoppedShort.replay(RecordedBlindSolve.SCRAMBLE, RecordedBlindSolve.MOVES, lastLandingMs);
    BlindResidual residual = stoppedShort.detector.getResidual();
    assertEquals(BlindResidual.Shape.TWISTED, residual.getShape());
    assertEquals(3, residual.getCount());
    assertEquals("UFL, UBL, UFR", residual.getPieces());
  }

  /**
   * The misfire of 2026-08-10: a corner commutator shot backwards, and a corner twist done after
   * it. The twist moved the buffer, so asking that nothing had touched the leftover since let the
   * algorithm that lost the solve off. Reversing that one algorithm solves the cube and reversing
   * any other does not, which is the whole claim the red makes.
   *
   * <p>Red for the two targets and not for the buffer: done right this algorithm lands those two and
   * leaves the buffer twisted, which is what the twist under it is for.
   */
  @Test
  public void marksTheAlgorithmAnotherOneMovedThePiecesOf() {
    replay(RecordedBlindSolve.SCRAMBLE_MISFIRE, RecordedBlindSolve.MOVES_MISFIRE, Long.MAX_VALUE);

    assertFalse(detector.isComplete());
    assertNull("the twist landed, so nothing went unread", detector.getLostReading());
    assertEquals(BlindResidual.Shape.CORNER_CYCLE, detector.getResidual().getShape());

    assertEquals("UFR-UBR-DBR", detector.subStepName(2, 2));
    assertEquals(Arrays.asList(TOUCHED, WRONG, WRONG), detector.subStepPieceMarks(2, 2));
    assertEquals("twist:UFR-FUL", detector.subStepName(2, 3)); // the algorithm after it, and clean
    for (int step = 1; step < detector.stepCount(); step++) {
      for (int part = 0; part < detector.subStepCount(step); part++) {
        assertEquals("only the misfire is red: " + detector.subStepName(step, part),
            step == 2 && part == 2, detector.subStepPieceMarks(step, part).contains(WRONG));
      }
    }
  }

  /**
   * Solve 165 cut at its last corner algorithm, so the parity it owes was never done: four pieces
   * out, one of them last shot at by the algorithm the solver took back. Nothing is red, since a
   * mistake taken back answers for nothing and a parity never done is the verdict line's to
   * explain.
   */
  @Test
  public void marksNothingWhereTheSolveStoppedOwingAParity() {
    RecordedBlindSolveTest read = new RecordedBlindSolveTest();
    read.replay(RecordedBlindSolve.SCRAMBLE_165, RecordedBlindSolve.MOVES_165, Long.MAX_VALUE);
    int corners = read.detector.subStepCount(2);

    replay(RecordedBlindSolve.SCRAMBLE_165, RecordedBlindSolve.MOVES_165,
        read.detector.getSubStepTimestampMs(2, corners - 1));

    assertEquals(BlindResidual.Shape.PARITY, detector.getResidual().getShape());
    assertNull("it stopped on a landing, so nothing went unread", detector.getLostReading());
    assertEquals("undo", detector.subStepName(1, 3));
    for (int step = 1; step < detector.stepCount(); step++) {
      for (int part = 0; part < detector.subStepCount(step); part++) {
        assertFalse(detector.subStepName(step, part),
            detector.subStepPieceMarks(step, part).contains(WRONG));
      }
    }
  }

  /**
   * The solve of 2026-08-10 that nothing proves. The buffer held the {@code FR} edge and the shot
   * went to {@code UL}, so from there the memo was executed from a buffer holding the wrong piece:
   * no single algorithm reversed would have brought the cube out, and the leftover matches nobody's
   * name.
   *
   * <p>What can still be said is that four shots never came home, so all four are marked. The two
   * that opened the solve are not, and neither are the corners, which were clean throughout.
   */
  @Test
  public void marksEveryShotThatNeverCameHomeWhereNothingProvesTheMisfire() {
    replay(RecordedBlindSolve.SCRAMBLE_WRONG_TARGET, RecordedBlindSolve.MOVES_WRONG_TARGET,
        Long.MAX_VALUE);

    assertFalse(detector.isComplete());
    assertEquals(BlindResidual.Shape.EDGE_CYCLE, detector.getResidual().getShape());

    assertEquals("UF-UL-DR", detector.subStepName(1, 3)); // shot at UL with FR in the buffer
    assertEquals(Arrays.asList(TOUCHED, WRONG, HOME), detector.subStepPieceMarks(1, 3));
    assertEquals("UF-UL-DR", detector.subStepName(1, 4)); // and again, the same algorithm twice
    assertEquals(Arrays.asList(TOUCHED, WRONG, TOUCHED), detector.subStepPieceMarks(1, 4));
    assertEquals("UF-DR-LB", detector.subStepName(1, 5));
    assertEquals(Arrays.asList(TOUCHED, HOME, WRONG), detector.subStepPieceMarks(1, 5));
    assertEquals("UF-DF-UL", detector.subStepName(1, 6)); // UL again, and left flipped in place
    assertEquals(Arrays.asList(TOUCHED, HOME, WRONG), detector.subStepPieceMarks(1, 6));
    for (int part = 0; part < 3; part++) { // the three that opened the edges landed everything
      assertFalse(detector.subStepPieceMarks(1, part).contains(WRONG));
    }
    for (int part = 0; part < detector.subStepCount(2); part++) {
      assertFalse(detector.subStepPieceMarks(2, part).contains(WRONG));
    }
  }

  /**
   * An algorithm shot at pieces already home, which is what a wrong target looks like: solve 163
   * with its last algorithm replaced by a repeat of the one two before it. Real turning, so the
   * buffer is read the way a solve's is — and the buffer is the one piece not marked, since taking
   * that out of a slot it had just come home to is what breaking into a new cycle is.
   */
  @Test
  public void marksAnAlgorithmThatShotAtPiecesAlreadyHome() {
    RecordedBlindSolveTest read = new RecordedBlindSolveTest();
    read.replay(RecordedBlindSolve.SCRAMBLE_163, RecordedBlindSolve.MOVES_163, Long.MAX_VALUE);
    List<Long> landings = read.landingTimes();
    int last = landings.size() - 1;
    replay(RecordedBlindSolve.SCRAMBLE_163,
        withLastAlgorithmRepeating(RecordedBlindSolve.MOVES_163, landings.get(last - 1),
            landings.get(last - 3), landings.get(last - 2)), Long.MAX_VALUE);

    assertFalse(detector.isComplete());
    assertEquals("UFR-UFL-DBR", detector.subStepName(2, 2));
    assertEquals(Arrays.asList(TOUCHED, WRONG, WRONG), detector.subStepPieceMarks(2, 2));
    assertEquals("UFR-UFL-DBR", detector.subStepName(2, 0)); // the same algorithm, done right
    assertFalse(detector.subStepPieceMarks(2, 0).contains(WRONG));
    for (int part = 0; part < detector.subStepCount(1); part++) {
      assertFalse(detector.subStepPieceMarks(1, part).contains(WRONG));
    }
  }

  /**
   * An algorithm made once a cycle has closed shot at nothing, so it answers for nothing. This solve
   * closes its cycle three algorithms from the end and breaks into a new one at {@code RU}, taking
   * that solved edge out and parking it in {@code BL}. Both slots are still out when the solve
   * stops, but what left them out is the last edge algorithm, which is where the red belongs.
   */
  @Test
  public void marksNothingOnTheAlgorithmThatBrokeIntoANewCycle() {
    replay(RecordedBlindSolve.SCRAMBLE_BROKE_IN, RecordedBlindSolve.MOVES_BROKE_IN, Long.MAX_VALUE);

    assertFalse(detector.isComplete());
    assertEquals(BlindResidual.Shape.EDGE_CYCLE, detector.getResidual().getShape());

    assertEquals("UF-RU-BL", detector.subStepName(1, 3));
    assertEquals(Arrays.asList(TOUCHED, TOUCHED, TOUCHED), detector.subStepPieceMarks(1, 3));
    assertEquals("UF-UL-BL", detector.subStepName(1, 5)); // the one that lost it, and the only red
    assertEquals(Arrays.asList(TOUCHED, WRONG, WRONG), detector.subStepPieceMarks(1, 5));
    for (int step = 1; step < detector.stepCount(); step++) {
      for (int part = 0; part < detector.subStepCount(step); part++) {
        if (step != 1 || part != 5) {
          assertFalse(detector.subStepName(step, part),
              detector.subStepPieceMarks(step, part).contains(WRONG));
        }
      }
    }
  }

  /**
   * A cycle is broken into at a piece still out; breaking into one already home is the memo read off
   * the wrong letter, and the one break-in there is anything to say about. Solve 163 with its first
   * algorithm made a second time: it finds its own piece in the buffer and shoots it straight back
   * at the edge the first put home.
   *
   * <p><b>Only where no parity is owed.</b> A parity ends by swapping the buffer with one other
   * piece, so breaking into that piece is a solver doing it deliberately — which is what the
   * recorded break-in solve does at {@code RU} while its parity is still to come, and it stays grey.
   */
  @Test
  public void marksABreakInThatTookASolvedPieceBackOut() {
    RecordedBlindSolveTest read = new RecordedBlindSolveTest();
    read.replay(RecordedBlindSolve.SCRAMBLE_163, RecordedBlindSolve.MOVES_163, Long.MAX_VALUE);
    long first = read.landingTimes().get(0);
    replay(RecordedBlindSolve.SCRAMBLE_163,
        withLastAlgorithmRepeating(RecordedBlindSolve.MOVES_163, first, 0, first), Long.MAX_VALUE);

    assertEquals("UF-DF-RF", detector.subStepName(1, 1)); // the same algorithm a second time
    assertEquals(Arrays.asList(TOUCHED, WRONG, TOUCHED), detector.subStepPieceMarks(1, 1));
    // Nothing was owed here to say against it: a cycle closed leaves the next one to open anywhere.
    assertNull(detector.subStepWantedName(1, 1));
    assertFalse(detector.subStepPieceMarks(1, 0).contains(WRONG));

    RecordedBlindSolveTest owed = new RecordedBlindSolveTest();
    owed.replay(RecordedBlindSolve.SCRAMBLE_BROKE_IN, RecordedBlindSolve.MOVES_BROKE_IN,
        Long.MAX_VALUE);
    assertEquals("UF-RU-BL", owed.detector.subStepName(1, 3)); // broken into a solved RU...
    assertEquals(Arrays.asList(TOUCHED, TOUCHED, TOUCHED), // ...with the parity still to come
        owed.detector.subStepPieceMarks(1, 3));
  }

  /**
   * The cycle the cube wanted, under the algorithm that lost the solve. Exact rather than guessed:
   * the {@code UF} buffer held the piece belonging at {@code LD}, and {@code LD} held the piece
   * belonging at {@code BL} — so the pair owed was {@code LD} then {@code BL}, and the solver shot
   * {@code UL} instead. The second target was right, which is what makes the first the mistake.
   *
   * <p>Nothing else is asked. The break-in has no expectation to fail — with its own piece in the
   * buffer the next cycle is the solver's to open anywhere — and an algorithm that went right is
   * shown nothing at all.
   */
  @Test
  public void saysWhatTheAlgorithmThatLostItShouldHaveShotAt() {
    replay(RecordedBlindSolve.SCRAMBLE_BROKE_IN, RecordedBlindSolve.MOVES_BROKE_IN, Long.MAX_VALUE);

    assertEquals("UF-UL-BL", detector.subStepName(1, 5));
    assertEquals("UF-LD-BL", detector.subStepWantedName(1, 5));
    for (int step = 1; step < detector.stepCount(); step++) {
      for (int part = 0; part < detector.subStepCount(step); part++) {
        if (step != 1 || part != 5) {
          assertNull(detector.subStepName(step, part), detector.subStepWantedName(step, part));
        }
      }
    }
  }

  /**
   * A cycle that closes after one shot is said as that target and a break-in: the piece waiting at
   * it is the buffer's own, so the algorithm owed lands one target and opens the next cycle with
   * the other, at whichever piece the solver pleases. Solve 3 of 2026-08-10 stops there — the buffer
   * held the {@code FR} edge, its home sticker was {@code RF}, and the solver shot {@code UL}, which
   * is where the whole solve went.
   *
   * <p><b>Said as two pieces and nothing else it read as a parity</b>, which it is not: the cycle
   * carrying the buffer having length two says nothing about the ones that do not, and here two
   * more were still out. The break-in is what a solve does there.
   */
  @Test
  public void saysTheOneTargetAndABreakInWhereTheCycleClosedAfterIt() {
    replay(RecordedBlindSolve.SCRAMBLE_WRONG_TARGET, RecordedBlindSolve.MOVES_WRONG_TARGET,
        Long.MAX_VALUE);

    assertEquals("UF-UL-DR", detector.subStepName(1, 3));
    assertEquals("breakin:UF-RF", detector.subStepWantedName(1, 3));
    // And a solve that came out is owed nothing anywhere: no algorithm of it carries red.
    RecordedBlindSolveTest solved = new RecordedBlindSolveTest();
    solved.replay(RecordedBlindSolve.SCRAMBLE_163, RecordedBlindSolve.MOVES_163, Long.MAX_VALUE);
    for (int step = 1; step < solved.detector.stepCount(); step++) {
      for (int part = 0; part < solved.detector.subStepCount(step); part++) {
        assertNull(solved.detector.subStepWantedName(step, part));
      }
    }
  }

  /**
   * The solve of 2026-08-25, and the one that shows a closing cycle is not a parity. Its third edge
   * algorithm found the {@code UF} buffer holding the {@code UL} edge and {@code UL} holding the
   * buffer's own, so the pair owed was {@code UL} and then a break-in — and the solver shot
   * {@code RD}, which is where the solve went.
   *
   * <p><b>Two edge three-cycles were still out at that moment</b>, which is what says the pair is
   * ordinary: the cube standing odd there is the parity this solve went on to do at the end, four
   * algorithms later, and not something the two-piece cycle announced.
   */
  @Test
  public void saysABreakInWhereOtherCyclesOfTheTypeAreStillOut() {
    replay(RecordedBlindSolve.SCRAMBLE_BREAK_IN_OWED, RecordedBlindSolve.MOVES_BREAK_IN_OWED,
        Long.MAX_VALUE);

    assertEquals("UF-RD-LB", detector.subStepName(1, 2));
    assertEquals("breakin:UF-UL", detector.subStepWantedName(1, 2));
    assertEquals(5, detector.subStepCount(1)); // two edge algorithms follow it...
    assertEquals("parity", detector.stepName(3)); // ...and the parity comes after the corners
  }

  /**
   * The break-in an algorithm makes while it is still shooting carries no red. Solve 3 of
   * 2026-08-26 closes its {@code RF} cycle and opens the next at {@code UR} in the one algorithm:
   * {@code UR} comes out holding the buffer's own piece and never comes home, and that is what a
   * break-in is rather than a shot that missed. What lost the cube was a flip the solver never did,
   * which the residual says and no algorithm is answerable for.
   */
  @Test
  public void leavesTheBreakInOfAClosingCycleUnblamed() {
    replay(RecordedBlindSolve.SCRAMBLE_PEEKED_WIDE, RecordedBlindSolve.MOVES_PEEKED_WIDE,
        Long.MAX_VALUE);

    assertFalse(detector.isComplete());
    assertEquals("UF-RF-UR", detector.subStepName(1, 2));
    assertEquals(Arrays.asList(TOUCHED, HOME, TOUCHED), detector.subStepPieceMarks(1, 2));
    assertEquals(BlindResidual.Shape.FLIPPED, detector.getResidual().getShape());
    assertEquals("UB, UR", detector.getResidual().getPieces());
    for (int step = 1; step < detector.stepCount(); step++) {
      for (int part = 0; part < detector.subStepCount(step); part++) {
        assertFalse(detector.subStepPieceMarks(step, part).contains(WRONG));
      }
    }
  }

  /**
   * A real solve whose edges are done in slices end to end, which is what a 3-style solve is made
   * of and what every other fixture here happens not to be. The whole of it is held, names and all,
   * because a slice-heavy solve is where a reading has the most room to go wrong: a slice rocks the
   * core, so every state is read against all 24 rotations, and states stand a clean three-cycle
   * from the last landing by coincidence far more often than face turns let them.
   */
  @Test
  public void readsASolveMadeOfSlicesWithoutFollowingAShadowOfIt() {
    replay(RecordedBlindSolve.SCRAMBLE_SLICES, RecordedBlindSolve.MOVES_SLICES, Long.MAX_VALUE);

    assertFalse(detector.isComplete()); // four edges from home, and the solver could not see it
    assertEquals(Arrays.asList("memo", "edges", "corners", "parity", "execution"), stepNames());
    assertEquals(Arrays.asList("UF-DB-DF", "UF-DL-RU", "UF-RF-FL", "UF-RB-BL", "UF-DR-BL"),
        subStepNames(1));
    assertEquals(Arrays.asList("UFR-BDL-UBL", "UFR-RDB-FDL", "UFR-LUB-BUR"), subStepNames(2));
    assertEquals(Arrays.asList("UFR-UBR + UF-UR"), subStepNames(3));
  }

  /** Every step of the solve, in order. */
  private List<String> stepNames() {
    List<String> names = new ArrayList<String>();
    for (int step = 0; step < detector.stepCount(); step++) {
      names.add(detector.stepName(step));
    }
    return names;
  }

  /** Every algorithm of one step, in order. */
  private List<String> subStepNames(int step) {
    List<String> names = new ArrayList<String>();
    for (int part = 0; part < detector.subStepCount(step); part++) {
      names.add(detector.subStepName(step, part));
    }
    return names;
  }

  /** Every landing of the solve, in order. */
  private List<Long> landingTimes() {
    List<Long> times = new ArrayList<Long>();
    for (int step = 1; step < detector.stepCount(); step++) {
      for (int part = 0; part < detector.subStepCount(step); part++) {
        times.add(detector.getSubStepTimestampMs(step, part));
      }
    }
    return times;
  }

  /** The solve up to {@code keepThroughMs}, then the face turns of the window given, replayed. */
  private static String withLastAlgorithmRepeating(String moves, long keepThroughMs, long fromMs,
      long toMs) {
    StringBuilder spliced = new StringBuilder();
    List<String> repeated = new ArrayList<String>();
    for (String token : moves.trim().split("\\s+")) {
      if (token.startsWith("[")) {
        spliced.append(token).append(' ');
        continue;
      }
      String notation = token.substring(0, token.indexOf('@'));
      long offsetMs = Long.parseLong(token.substring(token.indexOf('@') + 1));
      if (offsetMs <= keepThroughMs) {
        spliced.append(token).append(' ');
      }
      if (offsetMs > fromMs && offsetMs <= toMs && "xyz".indexOf(notation.charAt(0)) < 0) {
        repeated.add(notation);
      }
    }
    long at = keepThroughMs;
    for (String notation : repeated) {
      at += 150;
      spliced.append(notation).append('@').append(at).append(' ');
    }
    return spliced.toString().trim();
  }

  // Cut inside the last algorithm: what follows the landing before it is turning nothing was read
  // from, and the reading says where it stopped rather than ending quietly.
  @Test
  public void saysWhereTheReadingStopped() {
    replay(RecordedBlindSolve.SCRAMBLE, RecordedBlindSolve.MOVES, Long.MAX_VALUE);
    assertNull("a solve that came out lost nothing", detector.getLostReading());
    int last = detector.subStepCount(2) - 2;
    long lastLandingMs = detector.getSubStepTimestampMs(2, last);
    String lastRead = detector.subStepName(2, last);

    RecordedBlindSolveTest cut = new RecordedBlindSolveTest();
    cut.replay(RecordedBlindSolve.SCRAMBLE, RecordedBlindSolve.MOVES,
        nthMoveAfter(lastLandingMs, 2));
    LostReading lost = cut.detector.getLostReading();
    assertEquals(lastRead, lost.getAfter());
    assertEquals(2, lost.getMoves());
  }

  /** The offset of the {@code count}-th face turn made after {@code afterMs}. */
  private static long nthMoveAfter(long afterMs, int count) {
    int seen = 0;
    for (String token : RecordedBlindSolve.MOVES.trim().split("\s+")) {
      int at = token.indexOf('@');
      if (at < 0 || "xyz".indexOf(token.charAt(0)) >= 0) {
        continue;
      }
      long offsetMs = Long.parseLong(token.substring(at + 1));
      if (offsetMs > afterMs && ++seen == count) {
        return offsetMs;
      }
    }
    throw new IllegalStateException("no move that far into the solve");
  }

  /**
   * Solve 185 is the one that says an orientation cannot be recognised by how many pieces it moves.
   * The owner's account of it: the edges end on two flips, UF/UL and DL/DR, and the corners on three
   * corners twisted the same way — not one of them the buffer, which is the rare case.
   *
   * <p>Only the first flip was one algorithm. The second took two commutators, of which the first
   * could only take the pieces apart, and the twist took two with a setup turn between them that
   * left the first landing on nothing at all. So one orientation reads as two landings, one as two
   * algorithms inside a single landing, and both have to come out as the one memo item they were.
   */
  @Test
  public void readsAnOrientationHoweverManyAlgorithmsItTook() {
    replay(RecordedBlindSolve.SCRAMBLE_185, RecordedBlindSolve.MOVES_185, Long.MAX_VALUE);

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertEquals(3, detector.stepCount());
    assertEquals(6, detector.subStepCount(1));
    assertEquals(3, detector.subStepCount(2));
    assertEquals("UF-UR-BL", detector.subStepName(1, 3)); // the last commutator before the flips
    assertEquals("flip:UF-UL", detector.subStepName(1, 4)); // one algorithm
    assertEquals("flip:DL-DR", detector.subStepName(1, 5)); // two, joined by what they left
    // The flip is dated where it came out, and carries the whole of both its halves.
    assertEquals(49550, (long) detector.getSubStepTimestampMs(1, 5));
    // Three corners turned the same way with the buffer not among them: rare, and no shot at all.
    // Each is said from the face its U or D sticker was sitting on, and all three are one step
    // round from home the same way -- which is the solver's own account of it, all clockwise.
    assertEquals("twist:LUB-LDF-BDL", detector.subStepName(2, 2));
  }

  /**
   * A piece is said U/D first, then F/B, then R/L — the standard order, and not the order the
   * facelets are stored in, which runs round the piece. A target brings the sticker it was shot to
   * in front of that: the corner at B, U and R is {@code UBR}, and shot to its B sticker it is
   * {@code BUR}, never the {@code BRU} that reading round the piece gives.
   */
  @Test
  public void saysAPieceInTheOrderPiecesAreSaidIn() {
    replay(RecordedBlindSolve.SCRAMBLE_185, RecordedBlindSolve.MOVES_185, Long.MAX_VALUE);

    assertEquals("UFR-UFL-RDB", detector.subStepName(2, 0));
    assertEquals("UFR-FDR-BUR", detector.subStepName(2, 1));
  }

  /**
   * Solve 247 is the one that says a parity is a parity whatever it puts home. The owner missed an
   * edge cycle in the memo, so the pair the parity swapped were both edges still out — it gained no
   * edge, and the rule that asked it for one of each type left it unread. Seventeen moves then read
   * as turning nothing was read from, and the algorithm the solver did was reported as the solve
   * falling apart at the corners.
   *
   * <p>What it was left with is the cycle that was never memorised, which is the one thing here no
   * algorithm is answerable for.
   */
  @Test
  public void readsAParityThatBroughtNeitherOfItsEdgesHome() {
    replay(RecordedBlindSolve.SCRAMBLE_247, RecordedBlindSolve.MOVES_247, Long.MAX_VALUE);

    assertFalse(detector.isComplete());
    assertEquals(5, detector.stepCount()); // and the fifth is the tail an unfinished solve ends on
    assertEquals("parity", detector.stepName(3));
    assertEquals("UFR-UBL + UF-UR", detector.subStepName(3, 0));
    assertNull("the parity is the last algorithm, so nothing is left unread",
        detector.getLostReading());
    assertEquals(BlindResidual.Shape.EDGE_CYCLE, detector.getResidual().getShape());
  }

  /**
   * The corners of this one open on a break-in — the buffer's own corner parked in a new cycle and a
   * fresh one taken in — so the algorithm put nothing home and had no piece type of its own to be
   * read by. It went to the stretch it followed, and a corner commutator spelled in corner stickers
   * stood as the last algorithm of the edges, with its three seconds charged to them. What it turned
   * says which stretch it is: nothing came home, but the pieces it moved were corners.
   */
  @Test
  public void opensThePieceTypeAnAlgorithmThatGainedNothingBrokeInto() {
    replay(RecordedBlindSolve.SCRAMBLE_CORNERS_OPENED_ON_A_BREAK_IN,
        RecordedBlindSolve.MOVES_CORNERS_OPENED_ON_A_BREAK_IN, Long.MAX_VALUE);

    assertEquals(6, detector.subStepCount(1));
    assertEquals("flip:UF-UL", detector.subStepName(1, 5)); // the edges end where they came home
    assertEquals(41241, (long) detector.getStepTimestampMs(1));
    assertEquals("corners", detector.stepName(2));
    assertEquals(4, detector.subStepCount(2));
    assertEquals("UFR-LDB-RUB", detector.subStepName(2, 0));
  }

  /**
   * A cycle left on the cube is said as the shots that would fix it, so it names stickers: the edge
   * of solve 247 is owed to the L sticker of the FL slot, and {@code LF} and {@code FL} are two
   * different targets on a memo however much they are the same piece.
   */
  @Test
  public void saysALeftoverCycleAtTheStickersItIsOwedTo() {
    replay(RecordedBlindSolve.SCRAMBLE_247, RecordedBlindSolve.MOVES_247, Long.MAX_VALUE);

    assertEquals("UF-LF-UR", detector.getResidual().getPieces());
    // And nothing is red: the pieces left were never shot at, and the shots that did not land put
    // the buffer's own piece where they were aimed, which is a cycle broken into, not a miss.
    for (int step = 1; step < detector.stepCount(); step++) {
      for (int part = 0; part < detector.subStepCount(step); part++) {
        assertFalse(detector.subStepPieceMarks(step, part).contains(WRONG));
      }
    }
  }

  /**
   * Two things follow from the order pieces are said in, and both hold across every recorded solve.
   *
   * <p><b>A flipped edge never begins with R or L.</b> R/L is the last of the three axes said, and an
   * edge's two faces are on different axes — so whichever one it is not, the other comes first. The
   * owner noticed it on the phone; it is the ordering showing through, not a coincidence.
   *
   * <p><b>A twisted corner past the first never begins with U or D.</b> It is said from the face its
   * U/D sticker is sitting on, and a corner in its own slot that is twisted is exactly one whose U/D
   * sticker is off its U/D face. The piece a twist opens on is the buffer, which holds somebody
   * else's corner from the first shot until the parity, and a foreign corner may sit any way up: the
   * misfire twisted one that was already up, so its name reads {@code twist:UFR-FUL}.
   */
  @Test
  public void saysAFlipAndATwistFromAFaceThatMeansSomething() {
    for (String[] solve : RecordedBlindSolve.ALL) {
      for (String name : algorithmsOf(solve[0], solve[1])) {
        boolean flip = name.startsWith("flip:");
        if (!flip && !name.startsWith("twist:")) {
          continue;
        }
        String[] pieces = name.substring(name.indexOf(':') + 1).split("-");
        for (int i = flip ? 0 : 1; i < pieces.length; i++) {
          String never = flip ? "RL" : "UD";
          assertEquals(name + " says " + pieces[i] + " from a " + never + " face",
              -1, never.indexOf(pieces[i].charAt(0)));
        }
      }
    }
  }

  /**
   * Every algorithm is said from the piece it was shot from, and nothing tells the detector which
   * piece that is — it is read off the cycle, one algorithm at a time, so that a solver who floats
   * their buffer is read as well as one who does not. Across these solves it comes out as the owner's
   * own two buffers throughout, which is a check on the reading precisely because it was never told.
   *
   * <p><b>One buffer per type is a fact about these captures, not a rule.</b> They are all the same
   * solver's, and that solver does not float. A capture that does belongs in an assertion of its own
   * — the synthetic pair of cycles sharing no piece next door is what pins the floating case.
   */
  @Test
  public void readsTheBufferOffTheCycleWithoutBeingToldWhatItIs() {
    for (String[] solve : RecordedBlindSolve.ALL) {
      for (String name : algorithmsOf(solve[0], solve[1])) {
        String[] pieces = name.split("-");
        if (name.equals("undo") || name.contains(":") || name.contains(" + ")) {
          continue; // a flip, a twist and a parity are none of them a cycle shot from anywhere
        }
        assertEquals("a cycle is said as all three of its pieces: " + name, 3, pieces.length);
        assertEquals("and from the buffer of its own type: " + name,
            pieces[0].length() == 2 ? "UF" : "UFR", pieces[0]);
      }
    }
  }

  /**
   * The piece a cycle was shot from is never red, at any of the points any of these solves could
   * have been stopped at. A blind solve holds a foreign piece in its buffer nearly throughout, so
   * blaming a buffer for being out would redden the first letter of almost every algorithm of every
   * solve that did not come out.
   *
   * <p>Only a proved reversal can lay one, and only where executing that algorithm the right way
   * round would have brought the buffer home, which is a cycle that closes. None of these solves
   * has one, so the sweep is a floor rather than the whole rule.
   */
  @Test
  public void neverRedensThePieceACycleWasShotFrom() {
    List<String[]> solves = new ArrayList<String[]>(Arrays.asList(RecordedBlindSolve.ALL));
    solves.add(new String[] {RecordedBlindSolve.SCRAMBLE_MISFIRE, RecordedBlindSolve.MOVES_MISFIRE});
    for (String[] solve : solves) {
      RecordedBlindSolveTest whole = new RecordedBlindSolveTest();
      whole.replay(solve[0], solve[1], Long.MAX_VALUE);
      List<Long> stops = whole.landingTimes();
      stops.add(Long.MAX_VALUE);
      for (long stop : stops) {
        RecordedBlindSolveTest cut = new RecordedBlindSolveTest();
        cut.replay(solve[0], solve[1], stop);
        for (int step = 1; step < cut.detector.stepCount(); step++) {
          for (int part = 0; part < cut.detector.subStepCount(step); part++) {
            String name = cut.detector.subStepName(step, part);
            List<PieceMark> marks = cut.detector.subStepPieceMarks(step, part);
            if (marks.isEmpty() || name.contains(":") || name.contains("+")) {
              continue; // a flip, a twist and a parity are none of them shot from anywhere
            }
            assertFalse(name + " stopped at " + stop, marks.get(0) == WRONG);
          }
        }
      }
    }
  }

  /**
   * Solve 190 is the one whose grip the app read a quarter turn out. Read through the frame it was
   * really held in, its corners are four clean pairs, each shot from the buffer.
   *
   * <p>What the wrong frame used to do was worse than misspell them: it printed an algorithm shot at
   * a single target, and a corner named twice as though its own two stickers were two targets, since
   * the buffer it was told of sat on a piece those algorithms never touched. Named as the cycle it
   * shot, the same solve at the same wrong frame is still eleven three-cycles from one buffer — a
   * wrong frame moves every letter and invents nothing, and only the frame itself can fix it.
   */
  @Test
  public void readsAWrongFrameAsTheWrongSpellingAndNotTheWrongAlgorithm() {
    replay(RecordedBlindSolve.SCRAMBLE_190, RecordedBlindSolve.MOVES_190, Long.MAX_VALUE,
        Integer.valueOf(13)); // the grip the solve was really held in

    assertEquals("UFR-UBR-RDF", detector.subStepName(2, 0));
    assertEquals("UFR-LUB-DBL", detector.subStepName(2, 1));
    assertEquals("UFR-DFL-RDB", detector.subStepName(2, 2));
    assertEquals("UFR-LUF-RUB", detector.subStepName(2, 3));

    RecordedBlindSolveTest askew = new RecordedBlindSolveTest();
    askew.replay(RecordedBlindSolve.SCRAMBLE_190, RecordedBlindSolve.MOVES_190, Long.MAX_VALUE,
        Integer.valueOf(17)); // the frame the app derived from a first move taken mid-slice
    List<String> names = namesOf(askew.detector);
    assertEquals(11, names.size());
    for (String name : names) {
      String[] pieces = name.split("-");
      assertEquals("still a cycle, however it is spelled: " + name, 3, pieces.length);
      assertEquals("and still one buffer for the solve: " + name,
          pieces[0].length() == 2 ? "UB" : "UBR", pieces[0]);
    }
  }

  /**
   * Solve 195 fixes the parity between the corner commutators and the corner twist that closed the
   * solve, so its corners stand either side of it. The parity is not a piece type and divides
   * nothing: read as one it left the solve fitting no method at all, and a reading right about every
   * algorithm was thrown away for it.
   *
   * <p>It also swapped a corner into a slot it was still twisted in — the last algorithm is what
   * untwisted it — so that corner was never <em>solved</em> by the parity. Named by what it put home
   * the swap loses half of itself; a parity swaps two corners and two edges, always.
   */
  @Test
  public void readsAParityDoneInTheMiddleOfTheCorners() {
    replay(RecordedBlindSolve.SCRAMBLE_195, RecordedBlindSolve.MOVES_195, Long.MAX_VALUE);

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertEquals(5, detector.stepCount());
    assertEquals("edges", detector.stepName(1));
    assertEquals("corners", detector.stepName(2));
    assertEquals("parity", detector.stepName(3));
    assertEquals("corners", detector.stepName(4)); // the twist, done after the parity
    assertEquals("UFR-DFR + UF-UR", detector.subStepName(3, 0));
    assertEquals("twist:FUR-BDL", detector.subStepName(4, 0));
  }

  /**
   * Solve 227 remembered an edge flip after its corners had begun, so its piece types interleave:
   * edges, corners, that one flip, corners. A complete reading ending on a solved cube, and it used
   * to be thrown away whole — one stretch per type refused a type coming back, and what came back
   * was a flip. The solver's order is edges, edge flips, corners, corner twists, so a flip landing
   * late is the tail of the edges arriving out of order, not the edges being left unfinished.
   *
   * <p>It stands as a stretch of its own rather than joining the corners it interrupted: the flip is
   * edge work, and filing it under the corners would hand them four seconds they never spent.
   */
  @Test
  public void readsAFlipRememberedAfterTheCornersHadBegun() {
    replay(RecordedBlindSolve.SCRAMBLE_227, RecordedBlindSolve.MOVES_227, Long.MAX_VALUE);

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertEquals(5, detector.stepCount()); // memo, edges, corners, the flip, and the corners resumed
    assertEquals("edges", detector.stepName(1));
    assertEquals("corners", detector.stepName(2));
    assertEquals("edges", detector.stepName(3));
    assertEquals("corners", detector.stepName(4));
    assertEquals(1, detector.subStepCount(3));
    assertEquals("flip:UF-DF", detector.subStepName(3, 0));
  }

  /**
   * The solve that read as one algorithm. Its first is twelve turns of M-slice work and stands a
   * clean three-cycle from where it began after eight of them, so read greedily it was cut there —
   * and the four edges the rest of it moved are no algorithm, so nothing landed again and seven
   * algorithms went unread.
   *
   * <p>A landing held open until the solve lands from it reads the whole of it: five edge algorithms
   * from {@code UF} and three corner ones from {@code UFR}, every one a three-cycle.
   */
  @Test
  public void readsAnAlgorithmThatPassedThroughALandingOnItsWay() {
    replay(RecordedBlindSolve.SCRAMBLE_PASSED_THROUGH, RecordedBlindSolve.MOVES_PASSED_THROUGH,
        Long.MAX_VALUE);

    assertTrue(detector.matchesMethod());
    assertFalse(detector.isComplete()); // it was left on a three-cycle
    assertEquals(4, detector.stepCount()); // memo, edges, corners, and the tail it stopped in
    assertEquals("edges", detector.stepName(1));
    assertEquals("corners", detector.stepName(2));
    assertEquals(5, detector.subStepCount(1));
    assertEquals(3, detector.subStepCount(2));
    // The cut used to fall four turns early, on a cycle holding no buffer at all, and the name had
    // none to be spelled from: it read UB-DF-DB.
    assertEquals(38624L, (long) detector.getSubStepTimestampMs(1, 0));
    for (String name : namesOf(detector)) {
      String[] pieces = name.split("-");
      assertEquals("every algorithm is a cycle: " + name, 3, pieces.length);
      assertEquals("and every one shot from the buffer of its type: " + name,
          pieces[0].length() == 2 ? "UF" : "UFR", pieces[0]);
    }
  }

  /**
   * The solve of 2026-09-04, whose fourth edge algorithm broke into a new cycle and then missed. The
   * buffer held its own edge <em>flipped</em>, which is a cycle to break out of like any other — a
   * recorded solve does it and comes out — so opening at {@code UB} is the solver's own choice and
   * nothing is said against it. What the break-in displaces is a target all the same, and the cube
   * owed that edge to {@code RD}: the algorithm sent it to {@code BL} instead.
   *
   * <p><b>Nothing else in the solve could say so.</b> Two algorithms later the {@code BL} edge came
   * home, so it is not out at the end for a shot that never landed to be counted by, and reversing
   * any one algorithm leaves the cube nowhere near solved. The algorithm that lost the solve carried
   * no red at all, and the owner spotted the gap.
   */
  @Test
  public void marksTheTargetABreakInMissed() {
    replay(RecordedBlindSolve.SCRAMBLE_MISSED_AFTER_A_BREAK_IN,
        RecordedBlindSolve.MOVES_MISSED_AFTER_A_BREAK_IN, Long.MAX_VALUE);

    assertFalse(detector.isComplete());
    assertEquals("UF-UB-BL", detector.subStepName(1, 3));
    assertEquals(Arrays.asList(TOUCHED, TOUCHED, WRONG), detector.subStepPieceMarks(1, 3));
    // The break-in said back as the solver made it, and only the target after it named by the cube.
    assertEquals("UF-UB-RD", detector.subStepWantedName(1, 3));
    for (int step = 1; step < detector.stepCount(); step++) {
      for (int part = 0; part < detector.subStepCount(step); part++) {
        if (step != 1 || part != 3) {
          assertFalse(detector.subStepName(step, part),
              detector.subStepPieceMarks(step, part).contains(WRONG));
          assertNull(detector.subStepName(step, part), detector.subStepWantedName(step, part));
        }
      }
    }
  }

  /**
   * The corners of that same solve: one algorithm that breaks a cycle and one that closes it. The
   * break leaves two pieces out and the close leaves none, so neither is the single piece left out
   * that an ordinary shot is read from, and nothing after them settles the question either — they
   * were said from {@code UFL}, which is only where the cube happens to store the first of them.
   *
   * <p>A shot sends the buffer's piece home, so the break was shot from the corner that was holding
   * the one piece it landed, and the close follows from it.
   */
  @Test
  public void readsTheBufferOffTheOnePieceAnAlgorithmLanded() {
    replay(RecordedBlindSolve.SCRAMBLE_MISSED_AFTER_A_BREAK_IN,
        RecordedBlindSolve.MOVES_MISSED_AFTER_A_BREAK_IN, Long.MAX_VALUE);

    assertEquals(Arrays.asList("UFR-BUL-UFL", "UFR-BDL-LUF"), subStepNames(2));
  }

  /**
   * The solve whose edges open on a cycle that was already closed. Its first algorithm leaves no
   * piece out and its second finds the buffer holding its own piece, so neither says which piece it
   * was shot from; the third does, and the two before it are the same solver's, shot from the same
   * buffer. Left unnamed they printed as the pieces in the order the cube stores them, which reads
   * as a cycle and is not one — the first of them ran backwards.
   */
  @Test
  public void namesTheAlgorithmsBeforeTheOneThatSettledTheBuffer() {
    replay(RecordedBlindSolve.SCRAMBLE_OPENED_ON_A_CLOSED_CYCLE,
        RecordedBlindSolve.MOVES_OPENED_ON_A_CLOSED_CYCLE, Long.MAX_VALUE);

    assertEquals(Arrays.asList("UF-BR-RD", "UF-UB-BD", "UF-FR-DF", "UF-FL-LU", "UF-UB-DL",
        "UF-LB-DL"), subStepNames(1));
    assertEquals(Arrays.asList("UFR-RDF-UBR", "UFR-RUB-RDB"), subStepNames(2));
  }

  private static List<String> namesOf(BlindStepDetector detector) {
    List<String> names = new ArrayList<String>();
    for (int step = 1; step < detector.stepCount(); step++) {
      for (int part = 0; part < detector.subStepCount(step); part++) {
        names.add(detector.subStepName(step, part));
      }
    }
    return names;
  }

  /**
   * The solve of 2026-09-03 13:24:39, lost on a flip of the wrong pair. Two edges stood turned in front of
   * its last edge algorithm — the {@code UF} buffer and {@code UL} — and the solver flipped
   * {@code UL} and {@code UR}, so one of the two letters was right and the other was somebody else.
   *
   * <p>The right letter keeps its green: {@code UL} really did come home, and it is still standing
   * solved at the end. Only {@code UR} carries the red, and the wanted line says the pair that was
   * owed — which is the whole of the mistake in two words.
   */
  @Test
  public void reddensThePieceAFlipTurnedThatNeverCameHome() {
    replay(RecordedBlindSolve.SCRAMBLE_FLIPPED_THE_WRONG_PAIR,
        RecordedBlindSolve.MOVES_FLIPPED_THE_WRONG_PAIR, Long.MAX_VALUE);

    assertEquals("flip:UL-UR", detector.subStepName(1, 5));
    assertEquals(Arrays.asList(HOME, WRONG), detector.subStepPieceMarks(1, 5));
    assertEquals("flip:UF-UL", detector.subStepWantedName(1, 5));
    // And the cube says the same thing from the other end: the two it was left turned.
    assertEquals(BlindResidual.Shape.FLIPPED, detector.getResidual().getShape());
    assertEquals("UF, UR", detector.getResidual().getPieces());
    // Nothing else carries any: every shot of the solve landed what it was aimed at.
    for (int step = 1; step < detector.stepCount(); step++) {
      for (int part = 0; part < detector.subStepCount(step); part++) {
        assertEquals("only the flip is answerable: " + step + "." + part,
            step == 1 && part == 5, detector.subStepPieceMarks(step, part).contains(WRONG));
      }
    }
  }

  /** Every algorithm of a solve, memorisation aside, in the order they were executed. */
  private static List<String> algorithmsOf(String scramble, String moves) {
    RecordedBlindSolveTest solve = new RecordedBlindSolveTest();
    solve.replay(scramble, moves, Long.MAX_VALUE);
    List<String> names = new ArrayList<String>();
    for (int step = 1; step < solve.detector.stepCount(); step++) {
      for (int part = 0; part < solve.detector.subStepCount(step); part++) {
        names.add(solve.detector.subStepName(step, part));
      }
    }
    return names;
  }

  /**
   * With no frame established the names are spelled as the cube reports them: wrong about the
   * holding frame and so about every letter, but it is all a solve can be read as when nothing says
   * how the cube was picked up. Which cycle each algorithm shot is right either way — that is read
   * off the cube and not off the grip.
   */
  @Test
  public void spellsTheNamesAsReportedUntilTheFrameIsKnown() {
    replay(RecordedBlindSolve.SCRAMBLE_163, RecordedBlindSolve.MOVES_163, Long.MAX_VALUE,
        Integer.valueOf(BlindTargets.UNKNOWN_FRAME));

    assertEquals("UBR-LUF-UBL", detector.subStepName(2, 2)); // held, these are UFR-BUL-UBR
    assertEquals("UR-UL-UB", detector.subStepName(1, 5));
  }

  /**
   * The pickup rotation is taken from the solve itself: the gyro writes it as the rotation token
   * standing before the first face turn, and every one of these solves opens on {@code y} — the
   * scramble turned green in front, the solve turned red in front. Nothing is told to the test that
   * the app does not have, so the frame, its inverse and the names are all proved together.
   */
  private void replay(String scramble, String moves, long lastOffsetMs) {
    replay(scramble, moves, lastOffsetMs, null);
  }

  private void replay(String scramble, String moves, long lastOffsetMs, Integer frame) {
    if (frame != null) {
      detector.setHoldingFrame(frame);
    }
    boolean turned = false;
    for (String token : moves.trim().split("\\s+")) {
      // The stored grip, where the solve has one: it stands in brackets ahead of the moves and is
      // the pick-up itself, rather than the frame at the first move that the tokens give.
      if (token.startsWith("[")) {
        if (frame == null) {
          detector.setPickupRotation(
              CubeRotation.byNotation(token.substring(1, token.indexOf(']'))));
        }
        turned = true;
        break;
      }
      String notation = token.substring(0, token.indexOf('@'));
      if ("xyz".indexOf(notation.charAt(0)) < 0) {
        break; // the first face turn: any rotation after this one is the solve's, not the pick-up
      }
      if (frame == null) {
        detector.setPickupRotation(CubeRotation.byNotation(notation));
      }
      turned = true;
    }
    assertTrue("expected a pick-up rotation before the first turn", turned);
    for (String token : scramble.trim().split("\\s+")) {
      apply(token);
    }
    detector.reset(new CubeState(cube.toFaceCube()), 0);
    for (String token : moves.trim().split("\\s+")) {
      if (token.startsWith("[")) {
        continue; // the grip, already read above: it has no offset, so it is not a move
      }
      String notation = token.substring(0, token.indexOf('@'));
      long offsetMs = Long.parseLong(token.substring(token.indexOf('@') + 1));
      if (offsetMs > lastOffsetMs) {
        return;
      }
      if ("xyz".indexOf(notation.charAt(0)) >= 0) {
        continue;
      }
      Face face = Face.valueOf(notation.substring(0, 1));
      boolean prime = notation.endsWith("'");
      cube.applyMove(face, prime);
      detector.onState(new CubeState(cube.toFaceCube()), new CubeMove(face, prime, offsetMs));
    }
  }

  private void apply(String token) {
    Face face = Face.valueOf(token.substring(0, 1));
    for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
      cube.applyMove(face, token.endsWith("'"));
    }
  }
}
