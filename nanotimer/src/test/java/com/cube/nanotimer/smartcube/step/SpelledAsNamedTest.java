package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;

import com.cube.nanotimer.cube.SolveSolution;
import com.cube.nanotimer.cube.StoredSolveReplay;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * The invariant nothing asserted before: a blind algorithm's spelled moves shift exactly the slots
 * its own name names.
 *
 * <p>It is worth having because the two sides are arrived at independently — the name from the
 * detector, which reads cube states, and the spelling from the move stream resolved through the
 * gyro — and because a <b>constant</b> rotation error is invisible to every other check in the
 * suite: a reconstruction spelled a whole-cube rotation out still solves the cube read as ordinary
 * notation, and the bridge {@code BlindCaptureTest} builds between the blind and sighted readings
 * allows one rotation by design. The 2026-08-25 solve was spelled 120° out for as long as the blind
 * spelling has existed and no test noticed.
 *
 * <p>Neither side of it needs the scramble: a sequence of moves permutes slots whatever is sitting
 * in them.
 */
public class SpelledAsNamedTest {

  /**
   * The solve that found this: a slice the gyro reported no spin for and whose halves land 678 ms
   * apart, and two wides that are the gyro landing a millisecond ahead of a face turn. Read greedily
   * its second algorithm moves all 20 pieces and everything after it comes out a fixed 120° round.
   */
  @Test
  public void theSolveWhoseSecondAlgorithmMovedTheWholeCubeIsSpelledAsItsNames() {
    assertEquals("", mismatches(RecordedBlindSolve.SCRAMBLE_BREAK_IN_OWED,
        RecordedBlindSolve.MOVES_BREAK_IN_OWED));
  }

  /** And it is 3-style rather than the sequence nobody uses that the greedy reading printed. */
  @Test
  public void theAlgorithmTheSliceWasLostInIsSpelledAsTheSliceItTurned() {
    assertEquals("L U L2 S' L2 S U' L'", algorithm(RecordedBlindSolve.SCRAMBLE_BREAK_IN_OWED,
        RecordedBlindSolve.MOVES_BREAK_IN_OWED, 1));
  }

  /**
   * The 2026-08-26 solve, whose opening algorithm ends on a wide the peek rule wrote off: its spin
   * is cancelled four moves later by a rotation token, and nothing but the algorithm's own name
   * says the core really did rock. Read without it the frame is a quarter turn out from the second
   * algorithm to the last.
   */
  @Test
  public void theSolveWhosePeekWasAWideIsSpelledAsItsNames() {
    assertEquals("", mismatches(RecordedBlindSolve.SCRAMBLE_PEEKED_WIDE,
        RecordedBlindSolve.MOVES_PEEKED_WIDE));
  }

  /** Which is to say its first algorithm ends on the wide, and not on the bare face turn. */
  @Test
  public void theWideThePeekRuleDroppedIsSpelledAsTheWideItTurned() {
    assertEquals("y E' d' F' E' F2 E F' E d", algorithm(RecordedBlindSolve.SCRAMBLE_PEEKED_WIDE,
        RecordedBlindSolve.MOVES_PEEKED_WIDE, 0));
  }

  /** Every blind solve on record, so a reading that repairs one at the cost of another is caught. */
  @Test
  public void everyRecordedBlindSolveIsSpelledAsItsNames() {
    StringBuilder mismatches = new StringBuilder();
    int checked = 0;
    for (String[] solve : RecordedBlindSolve.ALL) {
      List<Algorithm> algorithms = algorithmsOf(solve[0], solve[1]);
      mismatches.append(mismatches(algorithms));
      checked += algorithms.size();
    }
    assertEquals("", mismatches.toString());
    assertEquals(77, checked); // or the sweep passed by reading nothing
  }

  private static String mismatches(String scramble, String moves) {
    return mismatches(algorithmsOf(scramble, moves));
  }

  /** One line per algorithm whose moves and name disagree, empty where they all agree. */
  private static String mismatches(List<Algorithm> algorithms) {
    StringBuilder mismatches = new StringBuilder();
    for (Algorithm algorithm : algorithms) {
      if (!Arrays.equals(algorithm.named, AlgorithmSlots.shiftedBy(algorithm.moves))) {
        mismatches.append(algorithm.name).append(" is spelled ").append(algorithm.moves)
            .append(", which shifts ").append(Arrays.toString(
                AlgorithmSlots.shiftedBy(algorithm.moves)))
            .append(" and not ").append(Arrays.toString(algorithm.named)).append('\n');
      }
    }
    return mismatches.toString();
  }

  private static String algorithm(String scramble, String moves, int index) {
    return algorithmsOf(scramble, moves).get(index).moves;
  }

  /** Every named algorithm of a stored solve, read again from its scramble and its moves. */
  private static List<Algorithm> algorithmsOf(String scramble, String moves) {
    StoredSolveReplay.Result read =
        StoredSolveReplay.reinterpret(scramble, moves, CubeMethod.BLIND);
    List<Algorithm> algorithms = new ArrayList<Algorithm>();
    if (read == null || read.getMethod() == null) {
      return algorithms; // a solve this cannot read again names no algorithm to check
    }
    SolveSolution solution = SolveSolution.from(moves, read.getSteps(), CubeMethod.BLIND);
    for (int s = 0; s < read.getSteps().size(); s++) {
      List<SolveStep> parts = read.getSteps().get(s).getSubSteps();
      for (int p = 0; p < parts.size(); p++) {
        int[] named = AlgorithmSlots.named(parts.get(p).getName());
        if (named != null) {
          algorithms.add(new Algorithm(parts.get(p).getName(), named,
              solution.getSteps().get(s).getPartMoves(p)));
        }
      }
    }
    return algorithms;
  }

  private static final class Algorithm {

    private final String name;
    private final int[] named;
    private final String moves;

    private Algorithm(String name, int[] named, String moves) {
      this.name = name;
      this.named = named;
      this.moves = moves;
    }
  }
}
