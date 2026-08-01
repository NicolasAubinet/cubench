package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * What pins the case tables down. Two things are checked, and between them a wrong entry cannot pass:
 * the standard algorithm for each named case must land on that name, and the tables must account for
 * every last-layer state there is, each under its own name. A mistyped algorithm therefore lands on
 * some other case's key, which shows up as that case being claimed twice and another not at all.
 *
 * <p>The a/b halves of a family (Aa/Ab, Ua/Ub, Ga…Gd) are the entries a table like this gets wrong
 * silently, since swapping the two names leaves it complete and consistent. So those carry a second
 * algorithm from another source, and the two have to agree.
 */
public class LastLayerCasesTest {

  /** The last layer is up, so the cross is on D. */
  private static final int CROSS = Cubies.D;

  /** Turns are compared on a scrambled cube: on a solved one every wrong answer still looks solved. */
  private static final String SCRAMBLED =
      Notation.apply(CubeState.SOLVED_FACELETS, "R U2 D' B L' F2 R' U D F");

  /**
   * One row per PLL: its name, then the algorithms that solve it. The first is the everyday one, the
   * second (where a family's naming turns on it) comes from a different source.
   */
  private static final String[][] PLL = {
    {"aa", "l' U R' D2 R U' R' D2 R2", "R' F R' B2 R F' R' B2 R2"},
    {"ab", "l U' R D2 R' U R D2 R2", "L F' L B2 L' F L B2 L2"},
    {"e", "x' R U' R' D R U R' D' R U R' D R U' R' D'",
        "R B' R' F R B R' F' R B R' F R B' R' F'"},
    {"f", "R' U' F' R U R' U' R' F R2 U' R' U' R U R' U R",
        "R' U2 R' d' R' F' R2 U' R' U R' F R U' F"},
    {"ga", "R2 u R' U R' U' R u' R2 y' R' U R", "R2 U R' U R' U' R U' R2 U' D R' U R D'"},
    {"gb", "L' U' L y' R2 u R' U R U' R u' R2", "R' U' R U D' R2 U R' U R U' R U' R2 D"},
    {"gc", "R2 u' R U' R U R' u R2 y R U' R'", "R2 U' R U' R U R' U R2 U D' R U' R' D"},
    {"gd", "R U R' y' R2 u' R U' R' U R' u R2", "R U R' U' D R2 U' R U' R' U R' U R2 D'"},
    {"h", "M2 U M2 U2 M2 U M2", "R2 U2 R U2 R2 U2 R2 U2 R U2 R2"},
    {"ja", "R' U L' U2 R U' R' U2 L R U'", "x R2 F R F' R U2 r' U r U2"},
    {"jb", "R U R' F' R U R' U' R' F R2 U' R' U'"},
    {"na", "L U' R U2 L' U R' L U' R U2 L' U R' U",
        "R U R' U R U R' F' R U R' U' R' F R2 U' R' U2 R U' R'"},
    {"nb", "R' U L' U2 R U' L R' U L' U2 R U' L U'"},
    {"ra", "L U2 L' U2 L F' L' U' L U L F L2 U"},
    {"rb", "R' U2 R U2 R' F R U R' U' R' F' R2 U'", "R2 F R U R U' R' F' R U2 R' U2 R"},
    {"t", "R U R' U' R' F R2 U' R' U' R U R' F'"},
    {"ua", "R U' R U R U R U' R' U' R2", "M2 U M U2 M' U M2"},
    {"ub", "R2 U R U R' U' R' U' R' U R'", "M2 U' M U2 M' U' M2"},
    {"v", "R' U R' U' y R' F' R2 U' R' U R' F R F"},
    {"y", "F R U' R' U' R U R' F' R U R' U' R' F R F'"},
    {"z", "M2 U M2 U M' U2 M2 U2 M' U2", "M' U M2 U M2 U M' U2 M2"},
  };

  /** One row per OLL, in the standard 1-57 numbering. */
  private static final String[][] OLL = {
    {"1", "R U2 R2 F R F' U2 R' F R F'"},
    {"2", "R U' R2 D' r U r' D R2 U R'"},
    {"3", "f R U R' U' f' U' F R U R' U' F'"},
    {"4", "f R U R' U' f' U F R U R' U' F'"},
    {"5", "l' U2 L U L' U l"},
    {"6", "r U2 R' U' R U' r'"},
    {"7", "r U R' U R U2 r'"},
    {"8", "l' U' L U' L' U2 l"},
    {"9", "R U R' U' R' F R2 U R' U' F'"},
    {"10", "R U R' U R' F R F' R U2 R'"},
    {"11", "r' R2 U R' U R U2 R' U M'"},
    {"12", "r R2 U' R U' R' U2 R U' r' R"},
    {"13", "F U R U' R2 F' R U R U' R'"},
    {"14", "R' F R U R' F' R F U' F'"},
    {"15", "l' U' l L' U' L U l' U l"},
    {"16", "r U r' R U R' U' r U' r'"},
    {"17", "R U R' U R' F R F' U2 R' F R F'"},
    {"18", "R U2 R2 F R F' U2 M' U R U' r'"},
    {"19", "S' R U R' S U' R' F R F'"},
    {"20", "r' R U R U R' U' r R' M' U R U' r'"},
    {"21", "R U R' U R U' R' U R U2 R'"},
    {"22", "R U2 R2 U' R2 U' R2 U2 R"},
    {"23", "R2 D' R U2 R' D R U2 R"},
    {"24", "r U R' U' r' F R F'"},
    {"25", "F R' F' r U R U' r'"},
    {"26", "R' U' R U' R' U2 R"},
    {"27", "R U R' U R U2 R'"},
    {"28", "r U R' U' r' R U R U' R'"},
    {"29", "R U R' U' R U' R' F' U' F R U R'"},
    {"30", "F U R U2 R' U' R U2 R' U' F'"},
    {"31", "R' U' F U R U' R' F' R"},
    {"32", "S R U R' U' R' F R f'"},
    {"33", "R U R' U' R' F R F'"},
    {"34", "R U R2 U' R' F R U R U' F'"},
    {"35", "R U2 R2 F R F' R U2 R'"},
    {"36", "L' U' L U' L' U L U L F' L' F"},
    {"37", "F R' F' R U R U' R'"},
    {"38", "R U R' U R U' R' U' R' F R F'"},
    {"39", "L F' L' U' L U F U' L'"},
    {"40", "R' F R U R' U' F' U R"},
    {"41", "R U R' U R U2 R' F R U R' U' F'"},
    {"42", "R' U' R U' R' U2 R F R U R' U' F'"},
    {"43", "R' U' F' U F R"},
    {"44", "F U R U' R' F'"},
    {"45", "F R U R' U' F'"},
    {"46", "R' U' R' F R F' U R"},
    {"47", "F R' F' R U2 R U' R' U R U2 R'"},
    {"48", "F R U R' U' R U R' U' F'"},
    {"49", "r U' r2 U r2 U r2 U' r"},
    {"50", "R' F R2 B' R2 F' R2 B R'"},
    {"51", "F U R U' R' U R U' R' F'"},
    {"52", "R U R' U R U' B U' B' R'"},
    {"53", "l' U' L U' L' U L U' L' U2 l"},
    {"54", "r U R' U R U' R' U R U2 r'"},
    {"55", "R' F R U R U' R2 F' R2 U' R' U R U R'"},
    {"56", "r U r' U R U' R' U R U' R' r U' r'"},
    {"57", "R U R' U' M' U R U' r'"},
  };

  @Test
  public void turnsTheFacesTheWayTheCubeModelDoes() {
    for (String face : new String[] {"U", "R", "F", "D", "L", "B"}) {
      for (String suffix : new String[] {"", "'", "2"}) {
        CubieCube cube = new CubieCube();
        cube.fromFacelet(CubieCube.SOLVED_FACELET);
        for (int i = 0; i < (suffix.equals("2") ? 2 : 1); i++) {
          cube.applyMove(Face.valueOf(face), suffix.equals("'"));
        }
        assertEquals(face + suffix, cube.toFaceCube(),
            Notation.apply(CubeState.SOLVED_FACELETS, face + suffix));
      }
    }
  }

  @Test
  public void rotatesTheWholeCubeTheWayTheFrameTablesDo() {
    assertSameAsFrame("x", Cubies.F, Cubies.U, Cubies.U, Cubies.B);
    assertSameAsFrame("y", Cubies.F, Cubies.L, Cubies.R, Cubies.F);
    assertSameAsFrame("z", Cubies.U, Cubies.R, Cubies.R, Cubies.D);
  }

  /** The rotation must move the facelets the way the frame tables the detectors read say it does. */
  private static void assertSameAsFrame(String rotation, int face, int to, int other, int otherTo) {
    for (int frame = 0; frame < FaceletRotations.COUNT; frame++) {
      if (FaceletRotations.face(frame, face) != to || FaceletRotations.face(frame, other) != otherTo) {
        continue;
      }
      char[] turned = new char[54];
      for (int facelet = 0; facelet < 54; facelet++) {
        turned[FaceletRotations.apply(frame, facelet)] = SCRAMBLED.charAt(facelet);
      }
      assertEquals(rotation, new String(turned), Notation.apply(SCRAMBLED, rotation));
      return;
    }
    throw new IllegalStateException("No frame for " + rotation);
  }

  @Test
  public void buildsTheWideAndSliceTurnsOutOfTheOnesItIsSureOf() {
    // A wide turn is the whole cube turned with the far layer put back, and a slice is a wide turn
    // with the near layer put back too — so face turns and rotations, both pinned above, define all
    // six of the rest.
    assertSameTurn("r", "x L");
    assertSameTurn("u", "y D");
    assertSameTurn("f", "z B");
    assertSameTurn("M'", "r R'");
    assertSameTurn("E'", "u U'");
    assertSameTurn("S", "f F'");
  }

  private static void assertSameTurn(String turn, String equivalent) {
    assertEquals(turn + " = " + equivalent,
        Notation.apply(SCRAMBLED, equivalent), Notation.apply(SCRAMBLED, turn));
  }

  @Test
  public void everyPllAlgorithmLandsOnTheCaseItIsNamedFor() {
    for (String[] row : PLL) {
      for (int i = 1; i < row.length; i++) {
        String state = Notation.caseState(row[i]);
        assertEquals(row[0] + " (" + row[i] + ")", row[0], LastLayerCases.permutation(state, CROSS));
      }
    }
  }

  @Test
  public void everyOllAlgorithmLandsOnTheCaseItIsNamedFor() {
    for (String[] row : OLL) {
      String state = Notation.caseState(row[1]);
      assertEquals("OLL " + row[0] + " (" + row[1] + ")", row[0],
          LastLayerCases.orientation(state, CROSS));
    }
  }

  @Test
  public void namesEveryPermutationTheLastLayerCanBeLeftIn() {
    Set<String> named = new HashSet<String>();
    int states = 0;
    for (int[] corners : permutations()) {
      for (int[] edges : permutations()) {
        if (isOdd(corners) != isOdd(edges)) {
          continue; // the two parities always agree on a cube that can be solved
        }
        states++;
        String name = LastLayerCases.permutation(permutationState(corners, edges), CROSS);
        assertNotNull("unnamed permutation " + reading(corners) + "/" + reading(edges), name);
        named.add(name);
      }
    }
    assertEquals(288, states);
    assertEquals(22, named.size()); // the 21 cases, and the skip
    for (String[] row : PLL) {
      assertTrue(row[0], named.contains(row[0]));
    }
  }

  @Test
  public void namesEveryOrientationTheLastLayerCanBeLeftIn() {
    Set<String> named = new HashSet<String>();
    int states = 0;
    for (int[] corners : twists(3)) {
      if (sum(corners) % 3 != 0) {
        continue;
      }
      for (int[] edges : twists(2)) {
        if (sum(edges) % 2 != 0) {
          continue;
        }
        states++;
        String name = LastLayerCases.orientation(orientationState(corners, edges), CROSS);
        assertNotNull("unnamed orientation " + reading(corners) + "/" + reading(edges), name);
        named.add(name);
      }
    }
    assertEquals(216, states);
    assertEquals(58, named.size()); // the 57 cases, and the skip
  }

  @Test
  public void readsTheSameCaseWhicheverFaceTheCrossWasOn() {
    // The same Ja, done on each layer in turn: the algorithm is wrapped in a rotation and its undo,
    // which leaves the case standing on another face with the centres where the cube reports them.
    int[][] layers = {
      {Cubies.D, 0}, {Cubies.B, 1}, {Cubies.F, 2}, {Cubies.U, 3}, {Cubies.R, 4}, {Cubies.L, 5},
    };
    String[] rotations = {"", "x", "x'", "x2", "z", "z'"};
    for (int[] layer : layers) {
      String rotation = rotations[layer[1]];
      String alg = rotation.isEmpty()
          ? PLL[9][1] : rotation + " " + PLL[9][1] + " " + Notation.inverse(rotation);
      assertEquals(rotation, "ja", LastLayerCases.permutation(Notation.caseState(alg), layer[0]));
    }
  }

  @Test
  public void readsTheSameCaseThroughAnAlignmentTurn() {
    String pll = Notation.caseState(PLL[16][1]); // a Ua
    String oll = Notation.caseState(OLL[26][1]); // a Sune
    for (String auf : new String[] {"U", "U2", "U'"}) {
      assertEquals(auf, "ua", LastLayerCases.permutation(Notation.apply(pll, auf), CROSS));
      assertEquals(auf, "27", LastLayerCases.orientation(Notation.apply(oll, auf), CROSS));
    }
  }

  @Test
  public void readsNoCaseOffAStateWhoseLastLayerIsNotThere() {
    String scrambled = Notation.apply(CubeState.SOLVED_FACELETS, "R U R' F2 D B");
    assertNull(LastLayerCases.permutation(scrambled, CROSS));
    assertNull(LastLayerCases.orientation(scrambled, CROSS));
  }

  /** The last layer left holding the given pieces, everything else solved. */
  private static String permutationState(int[] corners, int[] edges) {
    char[] facelets = Cubies.SOLVED.toCharArray();
    for (int slot = 0; slot < 4; slot++) {
      for (int i = 0; i < 3; i++) {
        facelets[Cubies.CORNERS[slot][i]] = Cubies.SOLVED.charAt(Cubies.CORNERS[corners[slot]][i]);
      }
      for (int i = 0; i < 2; i++) {
        facelets[Cubies.EDGES[slot][i]] = Cubies.SOLVED.charAt(Cubies.EDGES[edges[slot]][i]);
      }
    }
    return new String(facelets);
  }

  /** The last layer left turned by the given amounts, every piece home. */
  private static String orientationState(int[] corners, int[] edges) {
    char[] facelets = Cubies.SOLVED.toCharArray();
    for (int slot = 0; slot < 4; slot++) {
      for (int i = 0; i < 3; i++) {
        facelets[Cubies.CORNERS[slot][i]] =
            Cubies.SOLVED.charAt(Cubies.CORNERS[slot][(i + corners[slot]) % 3]);
      }
      for (int i = 0; i < 2; i++) {
        facelets[Cubies.EDGES[slot][i]] =
            Cubies.SOLVED.charAt(Cubies.EDGES[slot][(i + edges[slot]) % 2]);
      }
    }
    return new String(facelets);
  }

  private static int[][] permutations() {
    int[][] all = new int[24][];
    int found = 0;
    for (int a = 0; a < 4; a++) {
      for (int b = 0; b < 4; b++) {
        for (int c = 0; c < 4; c++) {
          for (int d = 0; d < 4; d++) {
            if ((1 << a | 1 << b | 1 << c | 1 << d) == 0b1111) {
              all[found++] = new int[] {a, b, c, d};
            }
          }
        }
      }
    }
    return all;
  }

  private static int[][] twists(int values) {
    int[][] all = new int[values * values * values * values][];
    int found = 0;
    for (int a = 0; a < values; a++) {
      for (int b = 0; b < values; b++) {
        for (int c = 0; c < values; c++) {
          for (int d = 0; d < values; d++) {
            all[found++] = new int[] {a, b, c, d};
          }
        }
      }
    }
    return all;
  }

  private static boolean isOdd(int[] permutation) {
    int swaps = 0;
    for (int i = 0; i < permutation.length; i++) {
      for (int j = i + 1; j < permutation.length; j++) {
        if (permutation[i] > permutation[j]) {
          swaps++;
        }
      }
    }
    return swaps % 2 != 0;
  }

  private static int sum(int[] values) {
    int total = 0;
    for (int value : values) {
      total += value;
    }
    return total;
  }

  private static String reading(int[] values) {
    StringBuilder sb = new StringBuilder();
    for (int value : values) {
      sb.append(value);
    }
    return sb.toString();
  }

  /**
   * Every case has its own key, and no two share one. The table is a map, so a duplicate would
   * quietly leave one of the two cases unreachable rather than fail — this is what says otherwise.
   */
  @Test
  public void givesEveryCaseAKeyOfItsOwn() {
    Set<String> keys = new HashSet<String>();
    keys.add(LastLayerCases.permutationKey(Cubies.SOLVED, CROSS));
    for (String[] row : PLL) {
      assertTrue(row[0], keys.add(LastLayerCases.permutationKey(Notation.caseState(row[1]), CROSS)));
    }
    assertEquals(22, keys.size());

    keys.clear();
    keys.add(LastLayerCases.orientationKey(Cubies.SOLVED, CROSS));
    for (String[] row : OLL) {
      assertTrue(row[0], keys.add(LastLayerCases.orientationKey(Notation.caseState(row[1]), CROSS)));
    }
    assertEquals(58, keys.size());
  }
}
