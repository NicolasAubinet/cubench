package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * A blind capture the solver can vouch for: 106 face turns of 3-style, five edge algorithms from
 * {@code UF} and three corner ones from {@code UFR}, held with white on top and red in front
 * throughout, so the grip is {@code y} and they can say so. A DNF, six pieces out.
 *
 * <p>It is here because it <b>opens on a slice</b>, which is the shape that breaks the grip: no
 * frame is read at a move inside a slice pair, so the grip goes unwritten and surfaces several
 * moves later as a regrip nobody made. This solve spelled its opening {@code M'} as {@code S'} and
 * its twin four moves on as {@code M'}, and that split ran through everything after it.
 */
public class BlindCaptureTest {

  static final String SCRAMBLE_268 =
      "B2 U R2 D' R2 D' R2 F2 U2 B2 D R2 L' U' L2 D2 F2 U R F' L'";

  static final String MOVES_268 =
      "[y] B'@37008 F@37010 z'@37011 x@37198 R@37198 R@37302 F@37448 B'@37457 z'@37458 D@37647 "
      + "D@37762 z'@37939 B'@37940 F@38527 B'@38557 z'@38558 z'@38623 F@38624 U@39110 U@39224 z@39378 "
      + "B@39378 U'@39497 D@39513 y@39514 x'@39711 L'@39712 U@39788 L@39973 D'@40082 U@40083 y'@40084 "
      + "z@40288 B'@40288 U@40614 L@42037 R'@42038 x@42039 F@42512 D@42704 F'@42857 B@42861 z@42862 "
      + "x'@42988 R'@42988 x@43117 F'@43117 R@43223 B'@43389 F@43390 z'@43391 D'@43559 R@43937 L'@43938 "
      + "x'@43939 U'@44997 B'@45207 U@45328 D'@45403 y'@45404 R@45516 U@45619 R'@45759 U'@46123 D@46136 "
      + "y@46137 B@46504 B@46991 y'@47132 D'@47133 R'@47388 U'@47604 D@47613 y@47614 B@47770 B@47918 "
      + "D'@48143 U@48148 y'@48149 R'@48405 D@48497 B'@48689 R@49663 y@49936 B@49936 U'@50031 B@50146 "
      + "D'@50298 B'@50453 U@50616 B@50737 D@50854 z'@50980 B'@50981 B'@51093 R'@51247 z@51773 U@51773 "
      + "z@51894 B@51895 U'@51981 R'@52272 R'@52423 U@52597 z'@52775 B'@52776 U'@52871 z@53018 B@53019 "
      + "R'@53217 R'@53436 z'@54164 B'@54165 B@60146 D@60790 B'@61001 B@61578 D'@61785 B'@61940 B@62654 "
      + "D'@62784 B'@62979 U@63071 B'@63193 D@63273 B@63436 U'@63493 B'@63612 D'@63757 B@63884 U'@63976 "
      + "B@64160 D@64281 B'@64457 U@64612 ";

  /** Held in one grip and read as one, the same slice comes out the same way both times. */
  @Test
  public void theSolveOpensOnTheSliceItWasTurnedWith() {
    assertTrue(blind(), blind().startsWith("y M' U2 M' U2 "));
  }

  /** Read as any other solve it does not, which is the whole of what the grip is worth here. */
  @Test
  public void readAsAnyOtherSolveTheOpeningSplitsInTwo() {
    assertTrue(sighted(), sighted().startsWith("S' y U2 M' U2 "));
  }

  /**
   * The blind spelling turns the cube exactly as the reading every other solve gets, which
   * {@code WideCaptureTest} pins against moves the solver dictated. It is the same solve held two
   * ways: the blind reading drops the rotations, so it ends in a frame of its own, and one
   * whole-cube rotation bridges the two. Nothing else checks the blind path against a real solve.
   */
  @Test
  public void theBlindSpellingIsTheSameSolveAsEveryOtherReadingOfIt() {
    assertTrue(DisplayedSolutionReplay.sameSolve(sighted(), blind()));
  }

  /**
   * And the check can fail: a solve is not the same as itself with its last turn missing. It has
   * to be a turn that goes, since dropping a rotation is exactly what the bridge absorbs.
   */
  @Test
  public void theCheckIsNotSatisfiedByJustAnything() {
    String shortened = blind().substring(0, blind().lastIndexOf(' '));

    assertEquals(false, DisplayedSolutionReplay.sameSolve(sighted(), shortened));
  }

  private static String blind() {
    return shown(CubeMethod.BLIND);
  }

  private static String sighted() {
    return shown(null);
  }

  private static String shown(CubeMethod method) {
    List<SolveStep> whole =
        Arrays.asList(new SolveStep(0, "execution", 0, 100000, Arrays.<SolveStep>asList()));
    return SolveSolution.from(MOVES_268, whole, method).getSteps().get(0).getMoves();
  }
}
