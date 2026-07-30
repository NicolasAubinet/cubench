package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * A 68-second Roux solve exactly as the database holds it, recorded on 2026-07-26 by the pipeline of
 * the time — a fixture for the display, which rebuilds the frame from the stored stream alone.
 *
 * <p>It is also the record of what a wrong frame does: the old grip tracking waited eleven seconds
 * for a still window it could name, and everything turned in between came out on the wrong axis.
 * {@link RecordedGyroReplayTest} replays the same solve from the capture through the pipeline as it
 * stands now — a stored solve keeps the stream it was recorded with, so that repairs the solves
 * recorded from here on, not this one.
 */
public class RecordedSolveReplayTest {

  static final String SCRAMBLE =
      "R2 F2 L2 B2 U B2 L2 U L2 D' R2 D2 L' D' L2 D F R2 U2 L' D'";

  static final String MOVES =
      "y@0 x2@0 B'@0 U'@304 U'@486 F'@2222 F'@2542 L@5415 R@10945 B'@11206 L'@12128 R@13185 "
      + "L'@13187 x'@13188 y@13495 D@13495 D@13691 R@14080 F'@15656 R@16243 L'@16313 x'@16314 "
      + "R'@17607 U@17796 L@21303 R'@23676 D@23873 R@24099 x@24416 F@24416 L'@24961 L'@25742 "
      + "F'@27012 L'@27560 F@27700 L@27960 L@28111 F'@28260 L'@29327 L'@30306 F'@30885 L@31175 "
      + "F@32220 R'@32638 R@34135 L'@35138 F'@35627 L@36149 R'@36227 x@36945 D@36945 D@37105 "
      + "L@37625 D'@38380 D'@38789 R'@38980 B'@39346 R@39866 D'@40695 L@40946 D'@41094 D'@41179 "
      + "L'@41300 L'@41390 D'@41525 L@41652 L@41735 D'@41835 L'@42000 L'@42076 D'@42165 D'@42276 "
      + "L@42674 L@44815 D@44945 L'@45060 D'@45219 L'@45426 F@45521 L@45596 L@45677 D'@45751 "
      + "L'@45874 D'@46060 L@46143 D@46264 L'@46382 F'@46684 L'@48617 R@48620 x'@48621 F@48820 "
      + "L'@49155 R@49157 x'@49158 L@50110 R'@50125 x@50126 L@50490 R'@50503 x@50504 D@51520 "
      + "D@51697 L'@52053 R@52061 x'@52062 F@52275 L@52377 R'@52395 x@52396 D@52936 R@53183 "
      + "L'@53185 x'@53186 F@53373 L'@53528 R@53530 x'@53531 U'@55554 R@56035 L'@56036 x'@56037 "
      + "B@56207 B@56352 x@58444 B@58444 B@58724 L@59429 R'@59429 U@60926 U@61055 L'@61215 R@61229 "
      + "x'@61230 B@61605 B@61721 L@62505 R'@62541 x@62542 U@62730 U@62872 L'@63058 R@63306 "
      + "L'@63346 x'@63347 R@63387 x'@64557 D@64557 R@67823 L'@67833 x'@67834 B'@68019 B'@68132 "
      + "L@68366 R'@68396 x@68397 U'@68612 U'@68764";

  /**
   * The frame the solve was really turned in, read off the states with no gyro involved at all:
   * the blocks were built on the cube's own R and U faces, whatever the tracker made of the grip.
   */
  @Test
  public void theBlocksNameTheFrameTheSolveWasTurnedIn() {
    assertEquals("left=R down=U solved=true",
        new RecordedSolveReplay(SCRAMBLE, MOVES).detectedFrame());
  }

  /** As stored: the solver's every M came out an E, because the regrip was recorded 11 s late. */
  @Test
  public void asRecordedTheSlicesAreNamedOnTheWrongAxis() {
    String shown = new RecordedSolveReplay(SCRAMBLE, MOVES).display();
    assertEquals(shown, 0, count(shown, "M"));
    assertEquals(shown, 1, count(shown, "S")); // the first slice, before the frame drifted again
    assertEquals(shown, 15, count(shown, "E")); // and every one after it
  }

  private static int count(String moves, String prefix) {
    int found = 0;
    for (String token : moves.split(" ")) {
      if (token.startsWith(prefix)) {
        found++;
      }
    }
    return found;
  }
}
