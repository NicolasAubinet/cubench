package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
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
  public void joinsTheHalvesOfTheAlgorithmsShownOnly() {
    assertTrue(AlgorithmTrack.isListed(false, "3", Arrays.asList("44", "45")));
    assertTrue(AlgorithmTrack.isListed(false, "4", Arrays.asList("44", "45")));
    assertTrue(AlgorithmTrack.isListed(false, "41", Arrays.asList("27", "45")));
    assertTrue(AlgorithmTrack.isListed(false, "42", Arrays.asList("26", "45")));
    assertTrue(AlgorithmTrack.isListed(false, "12", Arrays.asList("45", "45")));
    assertTrue(AlgorithmTrack.isListed(false, "2", Arrays.asList("45", "44")));
    // Low-voted spellings that are a plain two-look: not on the list a solver is shown.
    assertFalse(AlgorithmTrack.isListed(false, "35", Arrays.asList("44", "27")));
    assertFalse(AlgorithmTrack.isListed(false, "46", Arrays.asList("45", "27")));
    assertFalse(AlgorithmTrack.isListed(false, "50", Arrays.asList("26", "45")));
    // Halves of a listed algorithm, but turned on another case.
    assertFalse(AlgorithmTrack.isListed(false, "40", Arrays.asList("44", "45")));
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
  private static final String SUNE = "R U R' U R U2 R'";
  private static final String PI_RIGHT_BAR = "F R U R' U' R U R' U' F'";

  /** Every listed CMLL turned in face turns alone reads as one look, joined halves included. */
  @Test
  public void readsEveryListedCmllAsOne() {
    for (String code : CmllCases.codes()) {
      for (LastLayerCaseAlgorithms.Algorithm algorithm : LastLayerCaseAlgorithms.listed(code)) {
        String moves = algorithm.getMoves().replace("(", "").replace(")", "").trim();
        if (!moves.matches("[URFDLB2' ]+")) {
          continue; // a wide, slice or rotation reaches a cube as other turns
        }
        assertEquals(code + " " + moves, Integer.valueOf(1),
            RepAlgorithms.count(code, Notation.inverse(moves), timed(moves)));
      }
    }
  }

  @Test
  public void readsACmllTurnedThroughAnotherCaseAsTwo() {
    String scramble = Notation.inverse(SUNE + " " + PI_RIGHT_BAR);
    String code = CmllCases.PREFIX + CmllCases.of(Notation.apply(Cubies.SOLVED, scramble));
    assertEquals(Integer.valueOf(2),
        RepAlgorithms.count(code, scramble, timed(SUNE + " " + PI_RIGHT_BAR)));
  }

  /** The slice and the layer are the last six edges' business. */
  @Test
  public void readsACmllLeftWithTheSliceTurnedAsOne() {
    assertEquals(Integer.valueOf(1), RepAlgorithms.count("cmll_pirightbar",
        Notation.inverse(PI_RIGHT_BAR), timed(PI_RIGHT_BAR + " R L' U")));
  }

  @Test
  public void readsNothingForACmllNeverFinished() {
    assertNull(RepAlgorithms.count("cmll_pirightbar", Notation.inverse(PI_RIGHT_BAR),
        timed("F R U R'")));
  }

  private static String timed(String moves) {
    StringBuilder timed = new StringBuilder();
    int at = 100;
    for (String move : moves.trim().split(" +")) {
      timed.append(timed.length() == 0 ? "" : " ").append(move).append('@').append(at);
      at += 100;
    }
    return timed.toString();
  }
}
