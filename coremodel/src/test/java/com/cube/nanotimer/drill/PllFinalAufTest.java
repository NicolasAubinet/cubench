package com.cube.nanotimer.drill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Reps recorded on the owner's phone, layer dealt onto D. */
public class PllFinalAufTest {

  private static final String T_SCRAMBLE = "D B R D' R' D R D R2 B' R D R D' R'";
  private static final String T_MOVES = "D@1601 D@1734 L@1896 D@2099 L'@2202 D'@2364 L'@2456 "
      + "F@2534 L@2667 L@2741 D'@2810 L'@2987 D'@3149 L@3216 D@3419 L'@3572 F'@3848 D@4700";

  @Test
  public void endsOnTheAlgorithmsLastTurn() {
    // Stored as 2804 ms: the F' at 3848 closes the algorithm, the look and the D after it go.
    assertEquals(Long.valueOf(3848 - 1896), PllFinalAuf.executionMs(T_SCRAMBLE, T_MOVES, 1896));
  }

  @Test
  public void takesOutAnAufOfSeveralTurns() {
    String moves = "D@3272 D@3399 L'@3995 L'@4081 U'@4200 F@4463 D'@4559 F@4696 D@4846 "
        + "F'@4992 U@5071 L@5305 L@5406 B@5594 D'@5739 B'@6041 D@6477 D@6701";
    assertEquals(Long.valueOf(6041 - 3995),
        PllFinalAuf.executionMs("F D F' R2 U' B D' B' D B' U R2", moves, 3995));
  }

  @Test
  public void leavesARepThatClosedOnItsAlgorithm() {
    String moves = "D'@7729 F'@8109 D'@8332 F@8575 L@8719 L@8816 U@8999 B'@9117 D@9619 "
        + "B@9994 D'@10127 B@10364 U'@10508 L'@10756 L'@10845";
    assertNull(PllFinalAuf.executionMs("D' B2 U R' D R' D' R U' B2 L' D L D2", moves, 8109));
  }

  @Test
  public void leavesARepItsTurnsDoNotSolve() {
    assertNull(PllFinalAuf.executionMs(T_SCRAMBLE, T_MOVES.replace(" D@4700", ""), 1896));
  }

  @Test
  public void findsTheLayerOnAnyFace() {
    // The same rep with the cube held the other way up (x2): its layer is now U.
    assertEquals(Long.valueOf(3848 - 1896),
        PllFinalAuf.executionMs(x2(T_SCRAMBLE), x2(T_MOVES), 1896));
  }

  private static String x2(String turns) {
    StringBuilder turned = new StringBuilder();
    for (char c : turns.toCharArray()) {
      turned.append(c == 'U' ? 'D' : c == 'D' ? 'U' : c == 'F' ? 'B' : c == 'B' ? 'F' : c);
    }
    return turned.toString();
  }
}
