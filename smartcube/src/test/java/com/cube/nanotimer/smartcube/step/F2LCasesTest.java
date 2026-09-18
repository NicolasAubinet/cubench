package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * What pins the F2L case table down, the way {@link LastLayerCasesTest} pins the last layer's: one
 * algorithm per named case has to take the state it solves to that name, and every placement of a
 * pair's two pieces has to read under one name at most. A mistyped key then shows up as one case
 * claimed twice and another not at all.
 */
public class F2LCasesTest {

  private static final int CROSS = Cubies.D;

  /** Case, the slot its algorithm puts the pair in, and the algorithm. An advanced case's algorithm
   * inserts a pair other than front right's, since front right is where its trapped piece sits. */
  private static final String[][] CASES = {
    {"1", "fr", "U R U' R'"},
    {"2", "fr", "F R' F' R"},
    {"3", "fr", "F' U' F"},
    {"4", "fr", "R U R'"},
    {"5", "fr", "U' R U R' U2 R U' R'"},
    {"6", "fr", "U' r U' R' U R U r'"},
    {"7", "fr", "U' R U2 R' U' R U2 R'"},
    {"8", "fr", "r' U2 R2 U R2 U r"},
    {"9", "fr", "U' R U' R' U F' U' F"},
    {"10", "fr", "U' R U R' U R U R'"},
    {"11", "fr", "U' R U2 R' U F' U' F"},
    {"12", "fr", "R U' R' U R U' R' U2 R U' R'"},
    {"13", "fr", "M' U' R U R' U2 R U' r'"},
    {"14", "fr", "U' R U' R' U R U R'"},
    {"15", "fr", "M U r U' r' U' M'"},
    {"16", "fr", "R U' R' U2 F' U' F"},
    {"17", "fr", "R U2 R' U' R U R'"},
    {"18", "fr", "F' U2 F U F' U' F"},
    {"19", "fr", "U R U2 R' U R U' R'"},
    {"20", "fr", "U' R U' R2 F R F' R U' R'"},
    {"21", "fr", "U2 R U R' U R U' R'"},
    {"22", "fr", "r U' r' U2 r U r'"},
    {"23", "fr", "U R U' R' U' R U' R' U R U' R'"},
    {"24", "fr", "F U R U' R' F' R U' R'"},
    {"25", "fr", "U' R' F R F' R U R'"},
    {"26", "fr", "U R U' R' F R' F' R"},
    {"27", "fr", "R U' R' U R U' R'"},
    {"28", "fr", "R U R' U' F R' F' R"},
    {"29", "fr", "R' F R F' U R U' R'"},
    {"30", "fr", "R U R' U' R U R'"},
    {"31", "fr", "U' R' F R F' R U' R'"},
    {"32", "fr", "U R U' R' U R U' R' U R U' R'"},
    {"33", "fr", "U' R U' R' U2 R U' R'"},
    {"34", "fr", "U R U R' U2 R U R'"},
    {"35", "fr", "U' R U R' U F' U' F"},
    {"36", "fr", "U F' U' F U' R U R'"},
    {"37", "fr", "R2 U2 F R2 F' U2 R' U R'"},
    {"38", "fr", "R U' R' U' R U R' U2 R U' R'"},
    {"39", "fr", "R U' R' U R U2 R' U R U' R'"},
    {"40", "fr", "r U' r' U2 r U r' R U R'"},
    {"41", "fr", "R U' R' r U' r' U2 r U r'"},
    {"a1", "br", "S R' S'"},
    {"a2", "bl", "L F' U2 F L'"},
    {"a3", "fl", "R U' R' U F' r U r'"},
    {"a4", "br", "F R' F' R U R' U2 R"},
    {"a5", "bl", "R U R' U L U L'"},
    {"a6", "fl", "R U R' F U F'"},
    {"a7", "br", "U' F' U' f R S'"},
    {"a8", "bl", "U' F' U2 L U L' F"},
    {"a9", "fl", "U R' F' R F' R' F R"},
    {"a10", "br", "U' R U R2 U' R"},
    {"a11", "bl", "U' R U' R' L U' L'"},
    {"a12", "fl", "U2 R U R' L' U L"},
    {"a13", "br", "U R' F R F' R' U' R"},
    {"a14", "bl", "U' F R' F' R U L U L'"},
    {"a15", "fl", "U R' F R2 U' R' U2 F'"},
    {"a16", "br", "U' F' R' U' R F"},
    {"a17", "bl", "U2 R B' U' B R'"},
    {"a18", "fl", "U F' U2 F L' U' L"},
    {"a19", "br", "U' R U2 R' f R f'"},
    {"a20", "bl", "U R U R' L U L'"},
    {"a21", "fl", "U R F U F' R'"},
    {"a22", "br", "R U' R2 U R"},
    {"a23", "bl", "L F' U F L'"},
    {"a24", "fl", "R U' R' U2 L' U L"},
    {"a25", "br", "R' F R F' U R' U2 R"},
    {"a26", "bl", "L R U2 R' L'"},
    {"a27", "fl", "R' F R2 U R' F'"},
    {"a28", "br", "R U' R2 U' R U' R' U' R"},
    {"a29", "bl", "F' U L' U L U' L U L' F"},
    {"a30", "fl", "R U' R' U' L' U L U L' U' L"},
    {"a31", "br", "R U R' U' R U' R' U R' U' R"},
    {"a32", "bl", "R U R' U' R U' R' f' L' f"},
    {"a33", "fl", "R U' R' U' L' U' L U' L' U L"},
    {"a34", "br", "F' R' U R U' R' U' R F"},
    {"a35", "bl", "R U' R' U' R U R' L U' L'"},
    {"a36", "fl", "R' F R2 U' R' U' R U R' U2 F'"},
    {"a37", "br", "R U R' U2 R U' R' f R f'"},
    {"a38", "bl", "R U' R' U R U2 R' L U2 L'"},
    {"a39", "fl", "R U R' L' U L U' L F' L' F L' U L"},
    {"a40", "br", "R U' R2 U2 R U R' U2 R"},
    {"a41", "bl", "F' U2 L' U2 L2 U L' F"},
    {"a42", "fl", "R U' R' U2 L' U2 L U2 L' U L"},
    {"a1a", "br", "U2 R U' R' U' R' U' R"},
    {"a2a", "bl", "R' F R F' L U2 L'"},
    {"a3a", "fl", "R' F R2 U' R' U F'"},
    {"a4a", "br", "U R U R2 U2 R"},
    {"a5a", "bl", "U l U' F2 U l'"},
    {"a6a", "fl", "U R L' U L R'"},
    {"a7a", "br", "U2 R U' R' U2 R' U R"},
    {"a8a", "bl", "R U2 R' f' L' f"},
    {"a9a", "fl", "U2 R U' R' L' U L"},
  };

  /** Listed elsewhere as advanced cases of their own, yet placing the pair exactly as these do. */
  private static final String[][] SAME_AS = {
    {"a16", "br", "U' R U' R' U f R' f'"},
    {"a17", "bl", "U' R U R' U' f' L' f"},
    {"a18", "fl", "U' R U' R' U2 F' r U r'"},
  };

  @Test
  public void namesEveryCaseByItsOwnAlgorithm() {
    for (String[] row : CASES) {
      assertEquals(row[2], row[0], caseOf(row));
    }
  }

  @Test
  public void readsTheRepeatedCasesAsTheOnesTheyRepeat() {
    for (String[] row : SAME_AS) {
      assertEquals(row[2], row[0], caseOf(row));
    }
  }

  @Test
  public void everyAlgorithmPutsItsPairIn() {
    for (String[] row : CASES) {
      String after = Notation.apply(Notation.caseState(row[2]), row[2]);
      assertTrue(row[2], Cubies.crossDone(after, CROSS));
      assertTrue(row[2], Cubies.inPlace(after, Cubies.CORNERS[corner(row[1])])
          && Cubies.inPlace(after, Cubies.EDGES[edge(row[1])]));
    }
  }

  @Test
  public void namesEachPlacementOnceAtMost() {
    Map<String, String> keys = new HashMap<>();
    for (String[] row : CASES) {
      String key = F2LCases.key(Notation.caseState(row[2]), CROSS, corner(row[1]), edge(row[1]));
      assertNull(row[0] + " is listed twice", keys.put(row[0], key));
    }
    assertEquals(CASES.length, new HashSet<>(keys.values()).size());
  }

  @Test
  public void tellsApartEveryPlacementThereIs() {
    Set<String> keys = new HashSet<>();
    Map<String, Set<String>> keysByName = new HashMap<>();
    for (int corner = 0; corner < Cubies.CORNERS.length; corner++) {
      for (int twist = 0; twist < 3; twist++) {
        for (int edge : new int[] {0, 1, 2, 3, Cubies.FR, Cubies.FL, Cubies.BL, Cubies.BR}) {
          for (int flip = 0; flip < 2; flip++) {
            String state = placed(corner, twist, edge, flip);
            String key = F2LCases.key(state, CROSS, Cubies.DFR, Cubies.FR);
            assertNotNull(state, key);
            keys.add(key);
            String name = F2LCases.pairCase(state, CROSS, Cubies.DFR, Cubies.FR);
            if (!keysByName.containsKey(name)) {
              keysByName.put(name, new HashSet<String>());
            }
            keysByName.get(name).add(key);
          }
        }
      }
    }
    assertEquals(168, keys.size());
    assertEquals(CASES.length + 2, keysByName.size()); // the skip and the unnamed rest besides
    for (Map.Entry<String, Set<String>> named : keysByName.entrySet()) {
      if (!F2LCases.OTHER.equals(named.getKey())) {
        assertEquals(named.getKey(), 1, named.getValue().size());
      }
    }
    // The 72 no set names, and 3 trapped edges the advanced set leaves out.
    assertEquals(75, keysByName.get(F2LCases.OTHER).size());
  }

  @Test
  public void readsACaseTheSameHoweverTheCubeIsHeld() {
    for (String[] row : CASES) {
      String state = Notation.caseState(row[2]);
      int[] cornerFacelets = Cubies.CORNERS[corner(row[1])];
      int[] edgeFacelets = Cubies.EDGES[edge(row[1])];
      for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
        String held = LastLayerCases.inFrame(state, rotation);
        int cross = FaceletRotations.face(rotation, CROSS);
        int corner = pieceAt(Cubies.CORNERS, FaceletRotations.apply(rotation, cornerFacelets[0]));
        int edge = pieceAt(Cubies.EDGES, FaceletRotations.apply(rotation, edgeFacelets[0]));
        assertEquals(row[2] + " held " + rotation, row[0],
            F2LCases.pairCase(held, cross, corner, edge));
      }
    }
  }

  @Test
  public void readsNothingWithoutTheCross() {
    String state = Notation.apply(Notation.caseState(CASES[0][2]), "F");
    assertNull(F2LCases.pairCase(state, CROSS, Cubies.DFR, Cubies.FR));
  }

  @Test
  public void readsAPairAlreadyInAsASkip() {
    assertEquals(F2LCases.SKIP,
        F2LCases.pairCase(CubeState.SOLVED_FACELETS, CROSS, Cubies.DFR, Cubies.FR));
  }

  private static String caseOf(String[] row) {
    return F2LCases.pairCase(Notation.caseState(row[2]), CROSS, corner(row[1]), edge(row[1]));
  }

  /** A solved cube with the front right pair moved, and whatever sat there moved into its slot. The
   * rest need not be a cube anyone could turn to: only the pair is read. */
  private static String placed(int corner, int twist, int edge, int flip) {
    char[] state = CubeState.SOLVED_FACELETS.toCharArray();
    swap(state, Cubies.CORNERS[Cubies.DFR], Cubies.CORNERS[corner], twist);
    swap(state, Cubies.EDGES[Cubies.FR], Cubies.EDGES[edge], flip);
    return new String(state);
  }

  private static void swap(char[] state, int[] home, int[] slot, int shift) {
    char[] homeColours = new char[home.length];
    char[] slotColours = new char[slot.length];
    for (int i = 0; i < home.length; i++) {
      homeColours[i] = state[home[i]];
      slotColours[i] = state[slot[i]];
    }
    for (int i = 0; i < home.length; i++) {
      state[home[i]] = slotColours[i];
    }
    for (int i = 0; i < home.length; i++) {
      state[slot[(i + shift) % slot.length]] = homeColours[i];
    }
  }

  private static int pieceAt(int[][] pieces, int facelet) {
    for (int piece = 0; piece < pieces.length; piece++) {
      for (int candidate : pieces[piece]) {
        if (candidate == facelet) {
          return piece;
        }
      }
    }
    throw new IllegalArgumentException("No piece at " + facelet);
  }

  private static int corner(String slot) {
    return Cubies.slotNamed(("d" + slot).toUpperCase(java.util.Locale.US)) - Cubies.EDGES.length;
  }

  private static int edge(String slot) {
    return Cubies.slotNamed(slot.toUpperCase(java.util.Locale.US));
  }
}
