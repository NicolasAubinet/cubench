package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The two captures whose wide moves the solver can vouch for, which is what makes them worth
 * keeping: every wide in the older captures is the detector marking its own homework.
 *
 * <p>The first is a drill turned to order — all twelve wides in cancelling pairs, then four of one
 * and four of another, then a rotation and a face turn left deliberately apart. It is the whole
 * table exercised against a known answer. The second is a real solve with wides inside it, which
 * the drill cannot stand in for: there the frame is moving, and a wide has to be named through it.
 *
 * <p>Both are stored streams, so they pin the display rather than the sensing.
 */
public class WideCaptureTest {

  static final String SCRAMBLE_222 =
      "D2 L2 R2 D' B2 R2 D' L2 D' B' L D' F' R B' U' B' L2 B2 F' U2";

  static final String MOVES_222 =
      "[y' x'] y@0 z2@0 U'@0 z@416 R@416 R@542 F'@860 R'@1069 y'@2163 z'@2163 U'@2163 y@2697 F'@2697 "
      + "U@3052 z@4756 F@4756 z'@5001 D@5001 F'@5120 D@6039 B'@6381 D'@6875 D@7137 D@7394 B@7704 y@8737 "
      + "D'@8737 L'@8874 D@9053 L@9407 D@11429 D@11551 L@11980 D'@12321 D'@12613 L'@12852 y'@16064 "
      + "B'@16064 D@16291 B@16733 D'@18892 F@19307 D'@19586 F'@19747 F'@19866 D'@20045 F@20485 y'@21137 "
      + "U@21138 y@21451 U'@21452 D'@24431 y'@28628 D'@28628 y@29019 D@29020 y'@29928 R'@29928 D@30048 "
      + "D@30202 R@30467 D@30939 D@31071 R'@31226 D@31428 R@31663 L@35292 R'@35297 x@35298 z'@35466 "
      + "U@35466 B@35606 U'@35770 L'@35911 R@35916 x'@35917 D'@36077 B'@36247 z'@36322 L@36322 z@36701 "
      + "B@36701 L'@37101 L@37621 L'@38174 y@39443 z'@39443 R@39443 F@39666 L'@39843 F'@40020 L@40198 "
      + "R'@40201 x@40202 D@40397 L@40545 D'@40637 L'@40848 y'@42744 U@42745 y@43084 U'@43085 z@43792 "
      + "B@43793 z'@44230 B'@44231 D'@51595 D'@51800 z@52369 B@52370 D@52586 z'@52762 B'@52763 L'@53101 "
      + "L'@53210 x'@53334 U'@53334 y@53481 x@53481 F@53481 D'@53600 F'@53889 D@53980 F'@54130 y'@54291 "
      + "z@54291 U@54291 x@54596 L@54596 L@54806 D'@55011";

  static final String SCRAMBLE_224 =
      "U L2 R2 B2 U' F2 U2 R2 D' R2 U2 F' L R2 F D2 L' F2 D R2";

  static final String MOVES_224 =
      "[y] y@-1 D@0 y'@1613 D'@1614 y'@3205 U@3206 y@4914 U'@4915 x@6822 L@6823 x'@9021 L'@9022 "
      + "x'@11047 R@11048 x@13120 R'@13121 z@15562 B@15563 z'@17688 B'@17689 z'@19740 F@19741 z@21832 "
      + "F'@21833 x@23932 L@23933 x@25487 L@25488 x@27169 L@27170 x@28827 L@28828 y@30320 D@30321 "
      + "y@31933 D@31934 y@33678 D@33679 y@35319 D@35320 D@40320 D'@42999";

  /**
   * The drill, read back move for move. Twelve wides in their six pairs, then {@code r r r r} and
   * {@code u u u u} folded into half turns, then the {@code D D'} that must stay plain faces: a
   * rotation and the opposite face turned a second apart are two moves, not a wide.
   */
  @Test
  public void theDrillReadsBackAsTheMovesItWasTurnedWith() {
    String shown = new RecordedSolveReplay(SCRAMBLE_224, MOVES_224).display();

    assertEquals("u u' d d' r r' l l' f f' b b' r2 r2 u2 u2 D D'", shown);
  }

  /**
   * Every pair cancels and both runs are four of a kind, so the drill turns nothing: followed from
   * a solved cube it has to leave one. That is the reading being right about the whole sequence and
   * not merely about which letters to print.
   */
  @Test
  public void theDrillTurnsNothing() {
    String shown = new RecordedSolveReplay(SCRAMBLE_224, MOVES_224).display();

    assertTrue(shown, DisplayedSolutionReplay.solves("U U'", shown));
  }

  /** A sequence that turns nothing is every move cancelled, which is the whole drill struck out. */
  @Test
  public void theWholeDrillReadsAsCancelled() {
    String marked = new RecordedSolveReplay(SCRAMBLE_224, MOVES_224).marked();

    assertEquals("~u~ ~u'~ ~d~ ~d'~ ~r~ ~r'~ ~l~ ~l'~ ~f~ ~f'~ ~b~ ~b'~ ~r2~ ~r2~ ~u2~ ~u2~ ~D~ ~D'~",
        marked);
  }

  @Test
  public void theWidesTheSolverMadeAreTheWidesItShows() {
    String shown = new RecordedSolveReplay(SCRAMBLE_222, MOVES_222).display();

    // Turned and undone on the spot, which is how they were made without spoiling the solve.
    assertTrue(shown, shown.contains("u u'"));
    assertTrue(shown, shown.contains("f f'"));
    assertEquals(shown, 9, count(shown));
  }

  /** Those same wides were undone on the spot, so the solve shows them struck out. */
  @Test
  public void theWidesUndoneOnTheSpotReadAsCancelled() {
    String marked = new RecordedSolveReplay(SCRAMBLE_222, MOVES_222).marked();

    assertTrue(marked, marked.contains("~u~ ~u'~"));
    assertTrue(marked, marked.contains("~f~ ~f'~"));
  }

  /** The whole point: a reconstruction with wides in it still solves when followed literally. */
  @Test
  public void whatIsShownSolvesTheCube() {
    String shown = new RecordedSolveReplay(SCRAMBLE_222, MOVES_222).display();

    assertTrue(shown, DisplayedSolutionReplay.solves(SCRAMBLE_222, shown));
  }

  /** A wide is one move, so the spin it swallowed must not show as turning of its own. */
  @Test
  public void theSwallowedSpinsAreNotShownAsRotations() {
    String shown = new RecordedSolveReplay(SCRAMBLE_222, MOVES_222).display();
    int rotations = 0;
    for (String token : shown.split(" ")) {
      if (SolveMovesFormat.isRotation(token)) {
        rotations++;
      }
    }

    assertEquals(shown, 23, rotations); // the nine core spins are not among them
  }

  private static int count(String shown) {
    int wides = 0;
    for (String token : shown.split(" ")) {
      if ("udlrfb".indexOf(token.charAt(0)) >= 0) {
        wides++;
      }
    }
    return wides;
  }
}
