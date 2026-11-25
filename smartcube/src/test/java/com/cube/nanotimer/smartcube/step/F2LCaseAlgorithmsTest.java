package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.Test;

public class F2LCaseAlgorithmsTest {

  @Test
  public void everyAlgorithmSolvesTheCaseItIsFiledUnder() {
    StringBuilder wrong = new StringBuilder();
    for (String[] row : F2LCaseAlgorithms.rows()) {
      String read = F2LCases.pairCase(Notation.caseState(row[1]), Cubies.D, Cubies.DFR, Cubies.FR);
      if (!row[0].equals(read)) {
        wrong.append(row[0]).append(": ").append(row[1]).append(" reads ").append(read).append('\n');
      }
    }
    assertEquals("", wrong.toString());
  }

  /** A slot's rotation glued onto a sheet's own read as "y y'" on screen. */
  @Test
  public void noAlgorithmStartsWithTwoRotations() {
    StringBuilder wrong = new StringBuilder();
    for (String[] row : F2LCaseAlgorithms.rows()) {
      if (row[1].matches("y['2]? y['2]?( .*)?")) {
        wrong.append(row[0]).append(": ").append(row[1]).append('\n');
      }
    }
    assertEquals("", wrong.toString());
  }

  @Test
  public void onlyTheNamedEmptySlotIsDisturbed() {
    StringBuilder wrong = new StringBuilder();
    for (String[] row : F2LCaseAlgorithms.rows()) {
      if (row[0].startsWith("a")) {
        continue; // an advanced case's trapped piece belongs to another slot, which it leaves out
      }
      String state = Notation.caseState(row[1]);
      for (String slot : new String[] {"fl", "bl", "br"}) {
        if (!slot.equals(row[2]) && !pairIn(state, slot)) {
          wrong.append(row[0]).append(": ").append(row[1]).append(" needs ").append(slot).append('\n');
        }
      }
    }
    assertEquals("", wrong.toString());
  }

  @Test
  public void matchesAnAlgorithmTurnedAsItIsListed() {
    assertEquals("R U R'", F2LCaseAlgorithms.matching("4", "R U R'").getMoves());
  }

  @Test
  public void matchesThroughTheSetUpTurnAndMovesTakenBack() {
    assertEquals("R U R'", F2LCaseAlgorithms.matching("4", "U U' R U2 U' R'").getMoves());
    assertEquals("U R U' R'", F2LCaseAlgorithms.matching("1", "U2 R U' R' U").getMoves());
  }

  @Test
  public void matchesAPairTurnedIntoAnotherSlot() {
    assertEquals("R U R'", F2LCaseAlgorithms.matching("4", "F U F'").getMoves()); // front left
    assertEquals("R U R'", F2LCaseAlgorithms.matching("4", "y2 R U R'").getMoves());
  }

  @Test
  public void matchesASliceAlgorithmTurnedFromEveryGrip() {
    for (char[] grip : AlgorithmForm.grips()) {
      String stood = String.join(" ",
          AlgorithmForm.conjugatedBy(AlgorithmForm.of("M U r U' r' U' M'"), grip));
      assertNotNull(stood, F2LCaseAlgorithms.matching("15", stood));
    }
  }

  @Test
  public void matchesNothingForAnExecutionOffTheList() {
    assertNull(F2LCaseAlgorithms.matching("4", "R U2 R' U' R U R'"));
    assertNull(F2LCaseAlgorithms.matching("4", "R U R' U R U' R'"));
    assertNull(F2LCaseAlgorithms.matching("4", "L' U' L")); // a mirror is not a grip
    assertNull(F2LCaseAlgorithms.matching("4", "garbage"));
  }

  @Test
  public void listsACaseMostVotedFirst() {
    Set<String> cases = new LinkedHashSet<>();
    for (String[] row : F2LCaseAlgorithms.rows()) {
      cases.add(row[0]);
    }
    for (String pairCase : cases) {
      int previous = Integer.MAX_VALUE;
      for (F2LCaseAlgorithms.Algorithm algorithm : F2LCaseAlgorithms.forCase(pairCase)) {
        assertTrue(pairCase, algorithm.getShare() <= previous);
        previous = algorithm.getShare();
      }
    }
    assertEquals("U R U' R'", F2LCaseAlgorithms.forCase("1").get(0).getMoves());
  }

  /** A basic case holds each spelling once; a stuck-piece case, each algorithm once. */
  @Test
  public void holdsEachSpellingOnce() {
    for (String[] row : F2LCaseAlgorithms.rows()) {
      boolean basic = row.length > 4;
      int found = 0;
      for (F2LCaseAlgorithms.Algorithm algorithm : F2LCaseAlgorithms.forCase(row[0])) {
        if (basic ? algorithm.getMoves().equals(row[1]) : AlgorithmForm.comparable(
            algorithm.getMoves()).equals(AlgorithmForm.comparable(row[1]))) {
          found++;
        }
      }
      assertEquals(row[0] + ": " + row[1], 1, found);
    }
  }

  /** Case 28: one algorithm three ways, each ranked by its own front-right votes. */
  @Test
  public void ranksEachSpellingByItsOwnFrontRightVotes() {
    List<F2LCaseAlgorithms.Algorithm> shown = F2LCaseAlgorithms.listed("28");

    assertEquals("R U R' U' F R' F' R", shown.get(0).getMoves());
    assertTrue(shown.get(0).isRecommended());
    assertEquals("y L' U L U' L' U L", shown.get(1).getMoves());
    assertEquals("F' U F U' F' U F", shown.get(2).getMoves());
  }

  /** Case 2's back-right R' U R holds no front-right votes of its own, so it ranks no spelling. */
  @Test
  public void neverRanksAFrontRightSpellingByAnotherSlotsVotes() {
    assertEquals("F R' F' R", F2LCaseAlgorithms.listed("2").get(0).getMoves());
  }

  /** Inserted into back right the way people do there, a pair is not flagged on any slot. */
  @Test
  public void readsAnotherSlotsUsualAlgorithmAsListed() {
    assertFalse(F2LCaseAlgorithms.read("1", "y' U f R' f'").isUnusual());
    assertFalse(F2LCaseAlgorithms.read("28", "y' R' U R U' R' U R").isUnusual());
  }

  @Test
  public void everyBasicCaseHasAnAlgorithm() {
    for (int basic = 1; basic <= 41; basic++) {
      assertFalse(String.valueOf(basic), F2LCaseAlgorithms.forCase(String.valueOf(basic)).isEmpty());
    }
  }

  @Test
  public void everyAdvancedCaseHasAnAlgorithm() {
    for (int advanced = 1; advanced <= 42; advanced++) {
      assertFalse("a" + advanced, F2LCaseAlgorithms.forCase("a" + advanced).isEmpty());
    }
    for (int advanced = 1; advanced <= 9; advanced++) {
      assertFalse("a" + advanced + "a", F2LCaseAlgorithms.forCase("a" + advanced + "a").isEmpty());
    }
  }

  private static boolean pairIn(String state, String slot) {
    int corner = Cubies.slotNamed(("d" + slot).toUpperCase(Locale.US)) - Cubies.EDGES.length;
    int edge = Cubies.slotNamed(slot.toUpperCase(Locale.US));
    return Cubies.inPlace(state, Cubies.CORNERS[corner]) && Cubies.inPlace(state, Cubies.EDGES[edge]);
  }

  /** Case 7: 37 against 29 front-right votes is too close to recommend either. */
  @Test
  public void listsUntilMostVotesAreHeldWithoutARecommendationOnACloseVote() {
    List<F2LCaseAlgorithms.Algorithm> shown = F2LCaseAlgorithms.listed("7");

    assertEquals(3, shown.size());
    assertEquals("U' R U2 R' U' R U2 R'", shown.get(0).getMoves());
    assertFalse(shown.get(0).isRecommended());
  }

  @Test
  public void showsOnlyTheMostVotedWhereNothingElseComesClose() {
    assertEquals(1, F2LCaseAlgorithms.listed("17").size());
  }

  /** A spelling a handful of voters picked is not shown, but still reads as its algorithm. */
  @Test
  public void showsNoSpellingUnderItsShareOfTheVotes() {
    for (int basic = 1; basic <= 41; basic++) {
      AlgorithmList list = F2LCaseAlgorithms.listOf(String.valueOf(basic));
      List<AlgorithmList.Row> shown = list.listed();
      for (AlgorithmList.Row row : shown.subList(1, shown.size())) {
        assertTrue(basic + ": " + row.getMoves(),
            row.getVotes() * 100 >= AlgorithmList.SHOWN_SHARE * list.votes());
      }
    }
  }

  /** Into front left: the regrips folded into the faces turned, one rotation stands it in FR. */
  @Test
  public void writesAPairTurnedInAnotherSlotInTheSolversOwnFaces() {
    assertEquals("y U R U2 R' U' F U' F'",
        F2LCaseAlgorithms.asTurned("7", "y' U R U2 R' U' x U y' z U' R'"));
  }

  @Test
  public void writesAPairTurnedIntoFrontRightWithNoRotation() {
    assertEquals("U R U2 R' U2 R U' R'", F2LCaseAlgorithms.asTurned("7", "y U R U2 R' U2 R U' R'"));
    assertNull(F2LCaseAlgorithms.asTurned("7", "R U R'"));
  }

  @Test
  public void groupsOneWayOfTurningACaseWhicheverSlotItWasTurnedIn() {
    assertTrue(F2LCaseAlgorithms.sameTurning("7", "y' U R U2 R' U' x U y' z U' R'",
        "U' R U2 R' U' F U' y' R'"));
    assertFalse(F2LCaseAlgorithms.sameTurning("7", "U' R U2 R' U' R U2 R'",
        "U' R U2 R' U2 R U' R'"));
  }

  @Test
  public void readsAnExecutionNoAlgorithmMatchesAsUnusual() {
    assertTrue(F2LCaseAlgorithms.read("4", "R U2 R' U' R U R'").isUnusual());
    assertFalse(F2LCaseAlgorithms.read("7", "U' R U2 R' U' R U2 R'").isUnusual());
  }

  /** Two pairs built through a free slot, both off every list and neither longer than the usual. */
  @Test
  public void readsAPairBuiltThroughAFreeSlotInAsFewTurnsAsUsual() {
    assertNull(F2LCaseAlgorithms.matching("6", "R' U' R U' F' U F"));
    assertFalse(F2LCaseAlgorithms.read("6", "R' U' R U' F' U F").isUnusual());
    assertFalse(F2LCaseAlgorithms.read("5", "U' L U R L' U' R'").isUnusual());
    assertFalse(F2LCaseAlgorithms.read("7", "U B U2 B' U' R U' R'").isUnusual());
    assertFalse(CaseAlgorithms.isWorthPointingOut("pair_6", "R' U' R U' F' U F"));
  }

  @Test
  public void readsAPairBuiltThroughAFreeSlotInMoreTurnsAsUnusual() {
    assertTrue(F2LCaseAlgorithms.read("6", "F' U' F R' U R U F' U F").isUnusual());
    assertTrue(CaseAlgorithms.isWorthPointingOut("pair_6", "F' U' F R' U R U F' U F"));
  }

  @Test
  public void readsNoFreeSlotInAnAlgorithmThatLeavesTheOtherSlotsAlone() {
    assertFalse(F2LCaseAlgorithms.usesFreeSlot("6", "d R' U' R U2 R' U R"));
    assertTrue(F2LCaseAlgorithms.usesFreeSlot("6", "R' U' R U' F' U F"));
  }

  /** The picture's state: turned over and solved with the case's own algorithm, it is solved. */
  @Test
  public void drawsTheCaseTheMostVotedAlgorithmSolves() {
    String facelets = F2LCaseAlgorithms.crossUpFacelets("7");

    assertEquals(CubeState.SOLVED_FACELETS,
        Notation.apply(facelets, "z2 U' R U2 R' U' R U2 R' z2"));
  }

  /** A stuck-piece case reads as the sheets write it, from the stuck piece in front right. */
  @Test
  public void stuckPieceAlgorithmsReadFromThePicture() {
    assertEquals("S R' S'", CaseAlgorithms.asPictured("pair_a1", "y' S R' S'"));
    assertEquals("y L' U' L U R U' R'",
        CaseAlgorithms.asPictured("pair_a1", "L' U' L U R U' R'"));
    assertEquals("L F' U2 F L'", CaseAlgorithms.asPictured("pair_a2", "y2 L F' U2 F L'"));
    assertEquals("R U' R' U F' r U r'", CaseAlgorithms.asPictured("pair_a3", "y R U' R' U F' r U r'"));
    assertEquals("U R U' R'", CaseAlgorithms.asPictured("pair_1", "U R U' R'"));
    for (String code : CaseAlgorithms.pairCases()) {
      for (CaseAlgorithms.Listed listed : CaseAlgorithms.shown(code)) {
        String back = CaseAlgorithms.fromPictured(code,
            CaseAlgorithms.asPictured(code, listed.getMoves()));
        assertTrue(code + ": " + back, CaseAlgorithms.solves(code, back));
      }
    }
  }

  /** Each named case's picture marks the pair's five stickers, greys the last layer, reads as it. */
  @Test
  public void picturesEveryNamedCaseWithOnlyThePairInColour() {
    StringBuilder wrong = new StringBuilder();
    for (String code : CaseAlgorithms.pairCases()) {
      String pairCase = CaseAlgorithms.pairCase(code);
      String picture = CaseAlgorithms.pairPicture(code);
      int bright = 0;
      int grey = 0;
      boolean strayInColour = false;
      for (int i = 0; i < picture.length(); i++) {
        char sticker = picture.charAt(i);
        bright += Character.isUpperCase(sticker) ? 1 : 0;
        grey += sticker == '.' ? 1 : 0;
        // A coloured piece other than the pair must be solved, or it reads as a second pair.
        strayInColour |= Character.isLowerCase(sticker) && sticker != picture.charAt(i / 9 * 9 + 4);
      }
      // Grey: the 8 last-layer pieces' 20 stickers and the top centre, plus a stuck case's
      // knocked-out pieces.
      boolean greyed = CaseAlgorithms.isBasicPair(code) ? grey == 21 : grey > 21;
      int slot = F2LSlotAlgorithms.SLOTS.indexOf(CaseAlgorithms.pictureSlot(code));
      int[] corners = {Cubies.DFR, Cubies.DLF, Cubies.DRB, Cubies.DBL};
      int[] edges = {Cubies.FR, Cubies.FL, Cubies.BR, Cubies.BL};
      String read = F2LCases.pairCase(picture.toUpperCase(Locale.ROOT).replace('.', 'U'), Cubies.D,
          corners[slot], edges[slot]);
      // Each pair piece shows from front right: a sticker on the top, right or front face.
      boolean hidden = false;
      for (int[] piece : Cubies.PIECES) {
        boolean pair = false;
        boolean seen = false;
        for (int facelet : piece) {
          pair |= Character.isUpperCase(picture.charAt(facelet));
          seen |= facelet / 9 <= 2; // U, R or F
        }
        hidden |= pair && !seen;
      }
      if (bright != 5 || !greyed || strayInColour || !pairCase.equals(read) || hidden) {
        wrong.append(code).append(": ").append(picture).append('\n');
      }
    }
    assertEquals(92, CaseAlgorithms.pairCases().size());
    assertEquals("", wrong.toString());
  }

  /** A sheet's setup moves, from solved with the cross down, deal the case its algorithm solves. */
  @Test
  public void setUpMovesDealEveryListedAlgorithmsCase() {
    StringBuilder wrong = new StringBuilder();
    for (String code : CaseAlgorithms.pairCases()) {
      for (CaseAlgorithms.Listed listed : CaseAlgorithms.shown(code)) {
        String setup = LastLayerScrambles.forAlgorithm(listed.getMoves());
        String dealt = Notation.apply(CubeState.SOLVED_FACELETS, setup);
        if (!CaseAlgorithms.pairCase(code).equals(
            F2LCases.pairCase(dealt, Cubies.D, Cubies.DFR, Cubies.FR))) {
          wrong.append(code).append(": ").append(listed.getMoves()).append(" <- ").append(setup)
              .append('\n');
        }
      }
    }
    assertEquals("", wrong.toString());
  }

  /** Past the shown rows, every other spelling once, by its votes in all four slots. */
  @Test
  public void offersEveryOtherSpellingBehindMoreByAllItsVotes() {
    for (int basic = 1; basic <= 41; basic++) {
      String code = "pair_" + basic;
      List<String> offered = new ArrayList<String>();
      for (CaseAlgorithms.Listed one : concat(CaseAlgorithms.shown(code),
          CaseAlgorithms.more(code))) {
        assertFalse(code + ": " + one.getMoves(), offered.contains(one.getMoves()));
        offered.add(one.getMoves());
      }
      List<String> table = new ArrayList<String>();
      for (F2LCaseAlgorithms.Algorithm algorithm : F2LCaseAlgorithms.forCase(String.valueOf(basic))) {
        table.add(algorithm.getMoves());
      }
      assertEquals(code, new HashSet<String>(table), new HashSet<String>(offered));
      AlgorithmList list = F2LCaseAlgorithms.listOf(String.valueOf(basic));
      int last = Integer.MAX_VALUE;
      for (CaseAlgorithms.Listed one : CaseAlgorithms.more(code)) {
        int votes = -1;
        for (AlgorithmList.Row row : list.all()) {
          if (row.getMoves().equals(one.getMoves())) {
            votes = row.getVotes() + row.otherVotes();
          }
        }
        assertTrue(code + ": " + one.getMoves(), votes <= last);
        last = votes;
      }
    }
    assertTrue(CaseAlgorithms.more("pair_1_fl").isEmpty());
  }

  private static List<CaseAlgorithms.Listed> concat(List<CaseAlgorithms.Listed> one,
      List<CaseAlgorithms.Listed> other) {
    List<CaseAlgorithms.Listed> both = new java.util.ArrayList<CaseAlgorithms.Listed>(one);
    both.addAll(other);
    return both;
  }

  @Test
  public void keepsOnlyWhatSolvesTheCase() {
    assertTrue(F2LCaseAlgorithms.solves("7", "U' R U2 R' U' R U2 R'"));
    assertFalse(F2LCaseAlgorithms.solves("7", "R U R'"));
    assertFalse(F2LCaseAlgorithms.solves("7", "R U 7"));
    assertFalse(F2LCaseAlgorithms.solves("7", " "));
  }
}
