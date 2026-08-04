package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    // Three corners turned where they stand, the buffer among them: a twist moving as many pieces
    // as a commutator, and read as one by what it left rather than by how many it touched.
    assertEquals("twist:LUF-BUL-FUR", detector.subStepName(2, 3));
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
    assertEquals(home(false, true, true), detector.subStepSolvedPieces(1, 0));
    assertEquals("UF-UB-RU", detector.subStepName(1, 4)); // closed at UB and broke in at RU
    assertEquals(home(false, true, false), detector.subStepSolvedPieces(1, 4));
    assertEquals("UFR-UBR-LUB", detector.subStepName(2, 1)); // and the same on the corners
    assertEquals(home(false, true, false), detector.subStepSolvedPieces(2, 1));
    assertEquals("flip:UF-FL", detector.subStepName(1, 5));
    assertEquals(home(false, true), detector.subStepSolvedPieces(1, 5));
    assertEquals("UFR-UBL + UF-UR", detector.subStepName(3, 0));
    assertEquals(home(true, true, true, true), detector.subStepSolvedPieces(3, 0));
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
    assertEquals(home(false, false, false), detector.subStepSolvedPieces(1, 0));
    assertTrue(detector.subStepSolvedPieces(1, 1).isEmpty()); // the undo
    assertEquals("UF-UR-FR", detector.subStepName(1, 2));
    assertEquals(home(false, false, true), detector.subStepSolvedPieces(1, 2));

    RecordedBlindSolveTest even = new RecordedBlindSolveTest();
    even.replay(RecordedBlindSolve.SCRAMBLE_163, RecordedBlindSolve.MOVES_163, Long.MAX_VALUE);
    assertEquals("UF-UB-UR", even.detector.subStepName(1, 5));
    assertEquals(home(true, true, true), even.detector.subStepSolvedPieces(1, 5));
  }

  private static List<Boolean> home(Boolean... pieces) {
    return Arrays.asList(pieces);
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
   * Two things follow from the order pieces are said in, and both hold across every recorded solve.
   *
   * <p><b>A flipped edge never begins with R or L.</b> R/L is the last of the three axes said, and an
   * edge's two faces are on different axes — so whichever one it is not, the other comes first. The
   * owner noticed it on the phone; it is the ordering showing through, not a coincidence.
   *
   * <p><b>A twisted corner never begins with U or D.</b> It is said from the face its U/D sticker is
   * sitting on, and a corner that is twisted is exactly one whose U/D sticker is off its U/D face.
   * Which also catches the direction going unread: failing to find the sticker would fall back to
   * the piece's own U/D facelet, and the name would open with a U or a D.
   */
  @Test
  public void saysAFlipAndATwistFromAFaceThatMeansSomething() {
    for (String[] solve : RecordedBlindSolve.ALL) {
      for (String name : algorithmsOf(solve[0], solve[1])) {
        boolean flip = name.startsWith("flip:");
        if (!flip && !name.startsWith("twist:")) {
          continue;
        }
        for (String piece : name.substring(name.indexOf(':') + 1).split("-")) {
          String never = flip ? "RL" : "UD";
          assertEquals(name + " says " + piece + " from a " + never + " face",
              -1, never.indexOf(piece.charAt(0)));
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

  private static List<String> namesOf(BlindStepDetector detector) {
    List<String> names = new ArrayList<String>();
    for (int step = 1; step < detector.stepCount(); step++) {
      for (int part = 0; part < detector.subStepCount(step); part++) {
        names.add(detector.subStepName(step, part));
      }
    }
    return names;
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
