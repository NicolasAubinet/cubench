package com.cube.nanotimer.cube;

import static com.cube.nanotimer.vo.PieceMark.HOME;
import static com.cube.nanotimer.vo.PieceMark.TOUCHED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Reading a stored solve's breakdown again from its scramble and its moves — the same 68-second Roux
 * solve {@link RecordedSolveReplayTest} holds the database form of.
 *
 * <p>That fixture is the point rather than a convenience: it was stored with a wrong frame, every
 * slice recorded on the wrong axis. The detectors read <em>states</em> and never move letters, so a
 * breakdown re-read from it comes out right regardless — which is exactly why re-interpretation is
 * safe to do on history that was recorded by an older pipeline.
 */
public class StoredSolveReplayTest {

  private static final String SCRAMBLE = RecordedSolveReplayTest.SCRAMBLE;
  private static final String MOVES = RecordedSolveReplayTest.MOVES;

  /** Two edge three-cycles and then two corner ones: a blind solve's shape, in face turns. */
  private static final String BLIND_SOLVE = "R2 U R U R' U' R' U' R' U R'"
      + " F2 R2 U R U R' U' R' U' R' U R' F2"
      + " R' F R' B2 R F' R' B2 R2"
      + " U2 R' F R' B2 R F' R' B2 R2 U2";
  private static final String BLIND_SCRAMBLE = inverted(BLIND_SOLVE);
  /** Memorised for nine seconds, which is the offset the first turn carries. */
  private static final String BLIND_MOVES = played(BLIND_SOLVE, 9_000);

  @Test
  public void readsAStoredRouxSolveAsRoux() {
    StoredSolveReplay.Result result =
        StoredSolveReplay.reinterpret(SCRAMBLE, MOVES, CubeMethod.ROUX);

    assertNotNull(result);
    assertEquals(CubeMethod.ROUX, result.getMethod());
    assertEquals(4, result.getSteps().size());
    assertEquals("fb", result.getSteps().get(0).getName());
    assertEquals("sb", result.getSteps().get(1).getName());
    assertEquals("cmll", result.getSteps().get(2).getName());
    assertEquals("lse", result.getSteps().get(3).getName());
    assertNull("the cube saw this solve finish", result.getStoppedStep());
  }

  /** The steps are the solve's own, not a fresh reading of a solve that never happened. */
  @Test
  public void theStepsRunInOrderAndCoverTheSolve() {
    StoredSolveReplay.Result result =
        StoredSolveReplay.reinterpret(SCRAMBLE, MOVES, CubeMethod.ROUX);

    long total = 0;
    for (int i = 0; i < result.getSteps().size(); i++) {
      long step = result.getSteps().get(i).getTotalMs();
      assertTrue("step " + i + " lasted " + step, step > 0);
      total += step;
    }
    // The stream's last move is at 68764 ms; the steps account for the solve up to its last milestone.
    assertTrue("steps totalled " + total, total > 60_000 && total <= 68_764);
  }

  /**
   * A blind solve is named in the grip it was memorised in. Written in front of its moves, it is
   * read again like any other solve; recorded before the grip was kept, it keeps the breakdown it
   * was recorded with rather than one spelled through a grip nobody knows.
   */
  @Test
  public void doesNotRereadABlindSolveRecordedWithoutItsGrip() {
    assertNull(StoredSolveReplay.reinterpret(BLIND_SCRAMBLE, BLIND_MOVES, CubeMethod.BLIND));
  }

  @Test
  public void readsAStoredBlindSolveThroughTheGripItWasMemorisedIn() {
    StoredSolveReplay.Result result =
        StoredSolveReplay.reinterpret(BLIND_SCRAMBLE, heldIn("y", BLIND_MOVES), CubeMethod.BLIND);

    assertNotNull(result);
    assertEquals(CubeMethod.BLIND, result.getMethod());
    assertEquals(3, result.getSteps().size());
    assertEquals("memo", result.getSteps().get(0).getName());
    assertEquals("edges", result.getSteps().get(1).getName());
    assertEquals("corners", result.getSteps().get(2).getName());
    assertNull("the cube saw this solve finish", result.getStoppedStep());
  }

  /**
   * The grip is the whole reason it is kept: read through a different one, the same solve is the
   * same algorithms spelled at different pieces. Which is also why it is never guessed at.
   */
  @Test
  public void spellsTheTargetsThroughTheStoredGripAndNotAnother() {
    String held = firstAlgorithm(StoredSolveReplay.reinterpret(
        BLIND_SCRAMBLE, heldIn("y", BLIND_MOVES), CubeMethod.BLIND));
    String askew = firstAlgorithm(StoredSolveReplay.reinterpret(
        BLIND_SCRAMBLE, heldIn("x", BLIND_MOVES), CubeMethod.BLIND));

    assertNotNull(held);
    assertEquals(3, held.split("-").length); // a cycle either way: only the letters move
    assertEquals(3, askew.split("-").length);
    assertNotEquals(held, askew);
  }

  /**
   * The pieces an algorithm put home travel with it out of the re-read: nothing about them is
   * stored, so the sheet either reads them off the solve or shows none at all.
   *
   * <p>Each piece type here is a five-cycle done in two algorithms. The first closes on a break-in,
   * landing its first target and leaving its second holding a piece that belongs elsewhere; the
   * second closes the cycle and lands all three, the buffer included.
   */
  @Test
  public void carriesThePiecesEachAlgorithmPutHomeOutOfTheReRead() {
    StoredSolveReplay.Result result =
        StoredSolveReplay.reinterpret(BLIND_SCRAMBLE, heldIn("y", BLIND_MOVES), CubeMethod.BLIND);

    List<SolveStep> edges = result.getSteps().get(1).getSubSteps();
    assertEquals("UF-UL-UB", edges.get(0).getName());
    assertEquals(Arrays.asList(TOUCHED, HOME, TOUCHED), edges.get(0).getPieceMarks());
    assertEquals("UF-UB-DL", edges.get(1).getName());
    assertEquals(Arrays.asList(HOME, HOME, HOME), edges.get(1).getPieceMarks());

    // And there is one mark per piece the name says, which is what the sheet colours them by.
    for (int step = 1; step < result.getSteps().size(); step++) {
      for (SolveStep part : result.getSteps().get(step).getSubSteps()) {
        assertEquals(part.getName(), Utils.getSmartCubeNamedPieces(part.getName()).length,
            part.getPieceMarks().size());
      }
    }
  }

  private static String firstAlgorithm(StoredSolveReplay.Result result) {
    return result.getSteps().get(1).getSubSteps().get(0).getName();
  }

  /** The stored stream as the recorder writes it for a solve picked up in a given grip. */
  private static String heldIn(String pickup, String moves) {
    return "[" + pickup + "] " + moves;
  }

  /**
   * The last layer cases come back with the breakdown, so a solve stored before they were read shows
   * them the next time it is opened.
   */
  @Test
  public void readsTheLastLayerCasesOfACfopSolve() {
    String tPerm = "R U R' U' R' F R R U' R' U' R U R' F'"; // its own inverse, so it is the scramble
    StoredSolveReplay.Result result =
        StoredSolveReplay.reinterpret(tPerm, played(tPerm), CubeMethod.CFOP);

    assertNotNull(result);
    assertEquals(CubeMethod.CFOP, result.getMethod());
    assertEquals("cross", result.getSteps().get(0).getName());
    assertEquals("f2l", result.getSteps().get(1).getName());
    assertEquals("oll_skip", result.getSteps().get(2).getName());
    assertEquals("pll_t", result.getSteps().get(3).getName());
  }

  /** The algorithm as a stored move stream, a fifth of a second per turn. */
  private static String played(String alg) {
    return played(alg, 0);
  }

  private static String played(String alg, int firstOffsetMs) {
    StringBuilder sb = new StringBuilder();
    int offsetMs = firstOffsetMs;
    for (String token : alg.split(" ")) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(token).append('@').append(offsetMs);
      offsetMs += 200;
    }
    return sb.toString();
  }

  /** The scramble that leaves a cube needing exactly these moves: the solve, taken backwards. */
  private static String inverted(String moves) {
    String[] tokens = moves.split(" ");
    StringBuilder sb = new StringBuilder();
    for (int i = tokens.length - 1; i >= 0; i--) {
      String token = tokens[i];
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(token.endsWith("2") ? token
          : token.endsWith("'") ? token.substring(0, 1) : token + "'");
    }
    return sb.toString();
  }

  @Test
  public void hasNothingToSayAboutASolveWithNoMoves() {
    assertNull(StoredSolveReplay.reinterpret(SCRAMBLE, null, CubeMethod.CFOP));
    assertNull(StoredSolveReplay.reinterpret(SCRAMBLE, "", CubeMethod.CFOP));
    assertNull(StoredSolveReplay.reinterpret(null, MOVES, CubeMethod.CFOP));
  }

  /** A scramble in another puzzle's notation must fall back, not bring the detail dialog down. */
  @Test
  public void fallsBackRatherThanThrowingOnAScrambleItCannotRead() {
    assertNull(StoredSolveReplay.reinterpret("R++ D-- U'", MOVES, CubeMethod.CFOP));
  }

  /**
   * The walk starts from the scramble, which is only where the cube really was if the scramble was
   * performed. Started anywhere else, these same moves cannot end solved — so a reading that claims
   * the solve finished is proof the start was wrong, and the stored breakdown is kept instead.
   */
  @Test
  public void refusesAReadingThatFinishesOnACubeThatIsNotSolved() {
    assertNull(StoredSolveReplay.reinterpret(SCRAMBLE + " R", MOVES, CubeMethod.ROUX));
  }
}
