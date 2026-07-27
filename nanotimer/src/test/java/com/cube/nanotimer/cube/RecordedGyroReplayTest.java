package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Two captured solves replayed from the cube's own reports, so the reconstruction cannot change
 * under real gyro data without saying so. Both expected streams were computed independently, by the
 * Python model the rewrite was measured with, so this pins the Java against the design rather than
 * against itself: the Roux solve is turned about constantly and ends in nothing but slices, the CFOP
 * one never leaves the grip it was scrambled in.
 */
public class RecordedGyroReplayTest {

  /**
   * The 68-second Roux solve {@link RecordedSolveReplayTest} holds the stored form of. Its first
   * block is turned every which way, which is where the old pipeline lost the axis for good.
   */
  private static final String ROUX_140 =
      "y@0 x2@0 B'@0 U'@304 U'@486 z@2222 F'@2222 F'@2542 y2@5415 x'@5415 L@5415 y'@10945 x'@10945 "
      + "R@10945 B'@11206 L'@12128 x'@13185 R@13185 L'@13187 x'@13188 x@13495 D@13495 D@13691 "
      + "x'@14080 R@14080 x'@15656 F'@15656 R@16243 L'@16313 x'@16314 x@17607 R'@17607 U@17796 "
      + "y@21303 z@21303 L@21303 y@23676 x@23676 R'@23676 D@23873 x'@24099 R@24099 F@24416 L'@24961 "
      + "L'@25742 F'@27012 L'@27560 F@27700 L@27960 L@28111 F'@28260 x'@29327 L'@29327 x2@30306 "
      + "L'@30306 F'@30885 L@31175 x'@32220 F@32220 x@32638 R'@32638 x'@34135 R@34135 L'@35138 "
      + "F'@35627 x@36149 L@36149 R'@36227 D@36945 D@37105 x'@37625 L@37625 x@38380 D'@38380 D'@38789 "
      + "R'@38980 x@39346 B'@39346 x'@39866 R@39866 D'@40695 L@40946 D'@41094 D'@41179 L'@41300 "
      + "L'@41390 D'@41525 L@41652 L@41735 D'@41835 L'@42000 L'@42076 D'@42165 D'@42276 L@42674 "
      + "L@44815 D@44945 L'@45060 D'@45219 L'@45426 F@45521 L@45596 L@45677 D'@45751 L'@45874 "
      + "D'@46060 L@46143 D@46264 L'@46382 F'@46684 x'@48617 L'@48617 R@48620 x'@48621 x@48820 "
      + "F@48820 x'@49155 L'@49155 R@49157 x'@49158 x2@50110 L@50110 R'@50125 x@50126 x'@50490 "
      + "L@50490 R'@50503 x@50504 D@51520 D@51697 x'@52053 L'@52053 R@52061 x'@52062 x@52275 F@52275 "
      + "L@52377 x@52395 R'@52395 x@52396 x'@52936 D@52936 x'@53183 R@53183 L'@53185 x'@53186 x@53373 "
      + "F@53373 x'@53528 L'@53528 R@53530 x'@53531 x@55554 U'@55554 x'@56035 R@56035 L'@56036 "
      + "x'@56037 x@56207 B@56207 B@56352 B@58444 B@58724 L@59429 R'@59429 x@59430 U@60926 U@61055 "
      + "x'@61215 L'@61215 R@61229 x'@61230 x@61605 B@61605 B@61721 L@62505 R'@62541 x@62542 U@62730 "
      + "U@62872 x'@63058 L'@63058 R@63306 x'@63346 L'@63346 x'@63347 x@63387 R@63387 D@64557 "
      + "x'@67823 R@67823 L'@67833 x'@67834 x@68019 B'@68019 B'@68132 x@68366 L@68366 R'@68396 "
      + "x@68397 x'@68612 U'@68612 U'@68764";

  /** A 34-second CFOP solve of the same evening, turned throughout in the grip it was scrambled in. */
  private static final String CFOP_159 =
      "U'@0 R'@289 B@1029 L'@1670 F'@2188 L'@2563 D'@3166 D'@3319 U'@4891 R'@5847 U@6089 R@6268 "
      + "L@6739 U@6862 U@7043 L'@7266 L'@7425 U'@7655 L@8212 U@8513 R'@9193 U'@9945 R@10178 U@10782 "
      + "U@10954 L@11272 U@11556 L'@11823 U'@12522 U'@12983 R@13374 U@13788 R'@13992 R'@14146 "
      + "U'@14475 R@15317 R@17624 U'@17746 R'@17951 R@18887 U'@19460 R'@19653 U@19978 U@20385 "
      + "F'@20838 U'@21052 U'@21177 F@21580 U'@21907 U'@22095 F'@22471 U@22809 F@23333 F@25365 "
      + "R@25551 U@25718 R'@25804 U'@25936 F'@26145 U@26894 U@27033 F@27430 U@27692 R@27982 U'@28110 "
      + "R'@28321 F'@28498 U@29274 U@29721 U'@30256 R'@30598 U'@30761 F'@31006 R@31170 U@31338 "
      + "R'@31471 U'@31559 R'@31756 F@31872 R@32050 R@32181 U'@32295 R'@32450 U'@32638 R@32816 "
      + "U@32910 R'@33046 U@33158 R@33369 U@33782";

  @Test
  public void theRouxCaptureReplaysToTheStreamTheDesignPredicts() {
    assertEquals(ROUX_140, new RecordedGyroReplay("roux140.txt").getStoredMoves());
  }

  /** The stream is the whole solve, and the blocks say which pair of faces it was turned on. */
  @Test
  public void theRouxCaptureStillReplaysToASolvedCube() {
    assertEquals("left=R down=U solved=true", new RecordedGyroReplay("roux140.txt").detectedFrame());
  }

  /**
   * What the frame is for: the slices come out as the {@code M}s they were, where as stored at the
   * time this same solve showed fourteen {@code E}s and not one {@code M}.
   */
  @Test
  public void theRouxCaptureReadsAsTheRouxSolveItWas() {
    String shown = new RecordedGyroReplay("roux140.txt").display();
    assertEquals(shown, 0, count(shown, "E"));
    assertEquals(shown, 0, count(shown, "S"));
    assertEquals(shown, 15, count(shown, "M"));
    assertTrue(shown, shown.contains("R' U' R' F R2 U' R' U' R U R' F'")); // the CMLL, a T perm
  }

  @Test
  public void theCfopCaptureReplaysToTheStreamTheDesignPredicts() {
    assertEquals(CFOP_159, new RecordedGyroReplay("cfop159.txt").getStoredMoves());
  }

  /** A frame read fresh at every move must not invent turning where the solver did none. */
  @Test
  public void aSolveNeverLeavingItsGripRecordsNoRotations() {
    RecordedGyroReplay replay = new RecordedGyroReplay("cfop159.txt");
    for (SolveMovesFormat.Move move : SolveMovesFormat.parse(replay.getStoredMoves())) {
      assertFalse(move.getNotation(), SolveMovesFormat.isRotation(move.getNotation()));
    }
    // and the frame stays right to the end: the last layer reads as the T perm it was
    assertTrue(replay.display(), replay.display().contains("R U R' U' R' F R2 U' R' U' R U R'"));
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
