package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Reps recorded on a phone, layer dealt onto D. */
public class RepAlgorithmsTest {

  private static final String OLL_40 = "U R' U' F U R U' R' F' R";

  @Test
  public void readsOneAlgorithm() {
    String moves = "U'@3932 R@8870 U'@10746 L'@11062 U@11486 B@11710 U@12310 B'@12506 U'@12705 "
        + "L@12973 R'@12992";
    assertEquals(Integer.valueOf(1), RepAlgorithms.count("oll_40", OLL_40, moves));
  }

  @Test
  public void readsTwoLooks() {
    // F R U R' U' F' for the edges, then a second algorithm for the corners.
    String moves = "F@2082 R@2252 U@2389 R'@2455 U'@2529 F'@2783 R@3581 U'@3658 L'@4420 U@4642 "
        + "R'@4801 U'@4896 L@5163";
    assertEquals(Integer.valueOf(2),
        RepAlgorithms.count("oll_40", "U R' U' F U R U' R' F' R U2", moves));
  }

  @Test
  public void readsAListedAlgorithmMadeOfTwoAsOne() {
    // OLL 3 as f R U R' U' f' U' F R U R' U' F', 2.6 s between the halves.
    String moves = "R@5177 U@5441 B@5513 U'@5662 B'@5773 R'@6301 U'@7873 L@8920 F@9117 U@9224 "
        + "F'@9278 U'@9374 L'@9566";
    assertEquals(Integer.valueOf(1),
        RepAlgorithms.count("oll_3", "U2 F U R U' R' F' U B L U L' U' B' U", moves));
  }

  @Test
  public void leavesOutATurnUndoneByHand() {
    String moves = "R@100 R'@200 U'@3932 R@8870 U'@10746 L'@11062 U@11486 B@11710 U@12310 "
        + "B'@12506 U'@12705 L@12973 R'@12992";
    assertEquals(Integer.valueOf(1), RepAlgorithms.count("oll_40", OLL_40, moves));
  }

  @Test
  public void readsAPllInTwoLooks() {
    // A T-perm then a U-perm for an H-perm, layer on U.
    String tPerm = "R U R' U' R' F R2 U' R' U' R U R' F'";
    String uPerm = "R2 U R U R' U' R' U' R' U R'";
    String scramble = invert(tPerm + " " + uPerm);
    assertEquals(Integer.valueOf(2),
        RepAlgorithms.count("pll_h", scramble, stamped(tPerm + " " + uPerm, 1000)));
    assertEquals(Integer.valueOf(1), RepAlgorithms.count("pll_t", invert(tPerm),
        stamped(tPerm, 1000)));
  }

  @Test
  public void leavesARepItsTurnsDoNotFinish() {
    assertNull(RepAlgorithms.count("oll_40", OLL_40, "U'@3932 R@8870"));
  }

  @Test
  public void leavesAnythingButTheLastLayer() {
    assertNull(RepAlgorithms.count("pair_27", OLL_40, "R@100"));
  }

  /** Quarter turns 100 ms apart, as the cube reports them. */
  private static String stamped(String algorithm, long startMs) {
    StringBuilder moves = new StringBuilder();
    long at = startMs;
    for (String turn : algorithm.split(" ")) {
      String quarter = turn.replace("2", "");
      for (int i = turn.endsWith("2") ? 2 : 1; i > 0; i--) {
        moves.append(moves.length() == 0 ? "" : " ").append(quarter).append('@').append(at);
        at += 100;
      }
    }
    return moves.toString();
  }

  private static String invert(String algorithm) {
    String[] turns = algorithm.split(" ");
    StringBuilder inverted = new StringBuilder();
    for (int i = turns.length - 1; i >= 0; i--) {
      String turn = turns[i];
      String back = turn.endsWith("2") ? turn : turn.endsWith("'") ? turn.substring(0, 1)
          : turn + "'";
      inverted.append(inverted.length() == 0 ? "" : " ").append(back);
    }
    return inverted.toString();
  }
}
