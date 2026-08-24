package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * A blind capture whose parity algorithm the solver wrote down afterwards, which is what makes it
 * worth keeping: {@code S U' R U R2' F R f' U R U R' U'}, turned with white up and red in front
 * throughout. Nothing else in the tree has a blind algorithm from outside the reconstruction.
 *
 * <p>It is here because it <b>peeks</b>. Twice the solver tips the cube and tips it back, and both
 * times the detector reads the half of the swing that lands next to a face turn as that face being
 * wide. Each invented wide leaves the naming frame a quarter turn out, and by the parity the two
 * had made half a turn of it: the algorithm above printed as {@code S' D' R D R2 B R b' D R D R'
 * D'}, the same thirteen moves named upside down.
 *
 * <p>Its stored gyro track was read offline to settle it. Over the 400 ms either side of the two
 * invented wides the core moved 24° and 14° — it was back where it started, so nothing rocked — and
 * every wide the solver really turned moved it 57° to 106°, centred within 20 ms of its own face.
 * That measurement is not repeatable from the stored stream, which is why it is written here rather
 * than tested.
 */
public class BlindPeekCaptureTest {

  static final String MOVES_249 =
      "[y] y@66957 U'@66957 R'@67718 L@67726 x@67727 U'@67960 y@68111 L@68111 y'@68301 U@68301 "
      + "L'@68436 R@68437 x'@68438 B'@68606 L'@68936 B@69107 U@69338 B'@70038 F@70059 z'@70060 "
      + "B'@70170 F@70182 z'@70183 D'@70282 F'@70454 B@70456 z@70457 R'@70685 R'@70790 B'@70909 "
      + "F@70917 z'@70918 D'@71152 B'@71383 F@71402 z'@71403 B'@71611 F@71643 z'@71644 B'@72067 "
      + "U'@72229 B@72343 B@72447 R'@72566 L@72571 x@72572 y@72776 U'@72777 U'@72956 R@73100 "
      + "L'@73102 x'@73103 U@73269 z@73565 B@73565 U@73922 L@74374 R'@74389 x@74390 y@74663 D@74663 "
      + "R@74897 y'@75362 D'@75362 R@75576 L'@75589 x'@75590 z'@75762 F@75762 R'@75869 z@76028 "
      + "F'@76028 U'@76456 B'@77765 U'@77914 B@78050 U'@78181 D@78215 y@78216 L'@78355 U@78496 "
      + "L@78641 D'@78791 U@78809 y'@78810 U@79492 U@79605 B'@79729 U@79874 D'@79906 y'@79907 "
      + "R@80047 U@80164 R'@80301 y@80547 D@80548 U'@80578 B@80743 U@81586 B@82961 U@83182 B@83326 "
      + "D@83419 B'@83536 U'@83655 B@83757 D'@83870 B'@84082 B'@84189 D@84721 U@84782 B@84962 "
      + "U'@85050 R'@85307 z@85428 R'@85428 U@85599 z'@85761 B'@85762 U'@85901 z@86056 B@86057 "
      + "R'@86254 R'@86382 z'@86673 B'@86674 D'@87205 D@89686 B@89863 U'@89929 B@90058 D'@90196 "
      + "B'@90302 U@90410 B@90516 D@90617 B'@90747 U@90824 B'@90951 D'@91067 B@91302 U'@91681 "
      + "B'@91899 R'@92575 L@92579 x@92580 F'@92705 U@92843 F@93020 U'@93146 U'@93345 y@93430 "
      + "R@93430 y'@93610 U@93610 x'@93952 L'@93953 U@94242 B@94409 U@94550 B'@94723 U'@95232 "
      + "B'@95891 D'@96103 B@96214 D@96324 B'@96387 D'@96822 B@97181 U'@97285 B'@97420 D@97509 "
      + "B@97669 D'@97740 B'@97854 D@97952 B@98107 U@98198";

  private static final String STEPS_249 =
      "0:memo:66957:0 1:edges:4234:10395 1.0:UF-RD-DB:761:1620 1.1:UF-UL-UR:700:1605 "
      + "1.2:UF-FR-UB:424:1498 1.3:UF-FD-LD:357:2534 1.4:UF-BR-LB:1309:1044 1.5:UF-LF-UL:683:2094 "
      + "2:corners:4388:5925 2.0:UFR-UBR-LUB:1375:1228 2.1:UFR-LDF-DBL:532:2484 "
      + "2.2:UFR-DBR-RDF:2481:2213 3:parity:676:2657 3.0:UFR-UBR%20+%20UF-UR:676:2657 "
      + "4:corners:659:2307 4.0:twist%3ALUF-RUF:659:2307";

  /** The one row with an answer from outside: the algorithm the solver says they turned. */
  @Test
  public void theParityIsTheAlgorithmTheSolverTurned() {
    assertEquals("S U' R U R2 F R f' U R U R' U'", row(3, 0));
  }

  /** And the wide inside it is the solver's own, so a peek must not take every wide with it. */
  @Test
  public void theWideTheSolverTurnedSurvives() {
    assertTrue(row(3, 0), row(3, 0).contains(" f' "));
  }

  /**
   * The first edge algorithm is a commutator either way, so it says nothing about the parity being
   * right — but it is what a peek rule would break first, being three slices from the pick-up grip.
   */
  @Test
  public void theFirstEdgeAlgorithmStaysTheCommutatorItWas() {
    assertEquals("y U' S R' B R S' R' B' R U", row(1, 0));
  }

  /**
   * The near half of a peek was a regrip minted between two turns of the same face, which stopped
   * them reading as the half turn they were. Dropping it puts them back together.
   */
  @Test
  public void theHalfTurnThePeekSplitReadsAsOne() {
    assertEquals("D U R U' F2 U R' U' l D2 l' D'", row(2, 1));
  }

  /**
   * A wide is its far face and a whole-cube rotation, and a rotation moves no stickers — so
   * disbelieving one renames the solve without changing a single turn of it.
   */
  @Test
  public void demotingAWideLeavesTheSolveItSpellsAlone() {
    assertTrue(DisplayedSolutionReplay.sameSolve(whole(null), whole(CubeMethod.BLIND)));
  }

  /** Nothing here may reach a solve the solver was looking at: they turn the cube on purpose. */
  @Test
  public void aSightedReadingKeepsEveryWideTheDetectorClaimed() {
    assertEquals(whole(null), 6, wides(whole(null)));
    assertEquals(whole(CubeMethod.BLIND), 4, wides(whole(CubeMethod.BLIND)));
  }

  /**
   * A solve opening on a wide dates that wide's spin a millisecond ahead of the first move, so its
   * offset is {@code -1}: a real offset, and one no sentinel may quietly stand for. Read as a
   * rotation token instead of a wide, it pairs with the wide that takes it back and eats it.
   */
  @Test
  public void aWideAtTheOpeningIsAWideAndNotATokenStandingInForOne() {
    String opening = "[y] y@-1 D@0 y'@1613 D'@1614";
    List<SolveStep> one =
        Arrays.asList(new SolveStep(0, "execution", 0, 5000, Arrays.<SolveStep>asList()));

    assertEquals("y u u'",
        SolveSolution.from(opening, one, CubeMethod.BLIND).getSteps().get(0).getMoves());
  }

  /**
   * A peek's two halves are the accounting's word and its next word on the matter. Something
   * unaccounted for standing between them, here a wide of its own, is a chance to notice that was
   * taken and did not correct: reaching past it would cost the solver a wide they really turned.
   */
  @Test
  public void aTokenMayNotReachPastAWideToTakeADistantOne() {
    List<SolveStep> one =
        Arrays.asList(new SolveStep(0, "execution", 0, 5000, Arrays.<SolveStep>asList()));
    String reaching = "[y] z@1000 R@1000 y@2000 D@2001 x'@2999 R@3000";

    assertEquals("y F u r",
        SolveSolution.from(reaching, one, CubeMethod.BLIND).getSteps().get(0).getMoves());
  }

  private static int wides(String shown) {
    int wides = 0;
    for (String token : shown.split(" ")) {
      if ("udlrfb".indexOf(token.charAt(0)) >= 0) {
        wides++;
      }
    }
    return wides;
  }

  private static String row(int step, int part) {
    return SolveSolution.from(MOVES_249, SolveStepsFormat.parse(STEPS_249), CubeMethod.BLIND)
        .getSteps().get(step).getPartMoves(part);
  }

  private static String whole(CubeMethod method) {
    List<SolveStep> one =
        Arrays.asList(new SolveStep(0, "execution", 0, 200000, Arrays.<SolveStep>asList()));
    return SolveSolution.from(MOVES_249, one, method).getSteps().get(0).getMoves();
  }
}
