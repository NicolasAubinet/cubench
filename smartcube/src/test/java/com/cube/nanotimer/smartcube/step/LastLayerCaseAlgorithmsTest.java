package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * What makes a table taken off a website safe to ship: every algorithm in it is applied to a solved
 * cube backwards and handed to {@link LastLayerCases}, which must name the case it is filed under.
 * A mistyped turn, a dropped prime or a row filed against the wrong case all land somewhere else and
 * fail here rather than in front of someone trying to learn it.
 *
 * <p>The vote counts cannot be checked this way, and are not: they only ever decide the order and
 * how many of the algorithms are shown.
 */
public class LastLayerCaseAlgorithmsTest {

  /** The last layer is up, so the cross is on D. */
  private static final int CROSS = Cubies.D;

  /** The stickers the picture shows: the nine cells of the layer, then the twelve down its sides. */
  private static final int[] LAYER = {
    0, 1, 2, 3, 4, 5, 6, 7, 8, 45, 46, 47, 9, 10, 11, 18, 19, 20, 36, 37, 38,
  };

  /** A permutation is solved whichever way the layer is left facing. */
  private static final String[] AUFS = {"", "U", "U2", "U'"};

  @Test
  public void everyAlgorithmSolvesTheCaseItIsFiledUnder() {
    List<String> wrong = new ArrayList<String>();
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      String named;
      try {
        named = nameOf(row[0], Notation.caseState(row[1]));
      } catch (RuntimeException e) {
        named = e.getClass().getSimpleName() + ": " + e.getMessage();
      }
      if (!row[0].equals(named)) {
        wrong.add(row[0] + " | " + row[1] + " | landed on " + named);
      }
    }
    assertEquals(join(wrong), 0, wrong.size());
  }

  @Test
  public void namesOnlyCasesThereAreScramblesFor() {
    Set<String> cases = new HashSet<String>(LastLayerScrambles.cases());
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      assertTrue(row[0], cases.contains(row[0]));
    }
  }

  @Test
  public void hasAtLeastOneAlgorithmForEveryCase() {
    for (String code : LastLayerScrambles.cases()) {
      assertFalse(code, LastLayerCaseAlgorithms.forCase(code).isEmpty());
    }
  }

  /** Most used first, and never anything but the first one recommended. */
  @Test
  public void putsTheMostUsedFirst() {
    for (String code : LastLayerScrambles.cases()) {
      List<LastLayerCaseAlgorithms.Algorithm> shown = LastLayerCaseAlgorithms.listed(code);
      int previous = shown.get(0).getShare();
      for (int i = 1; i < shown.size(); i++) {
        assertFalse(code, shown.get(i).isRecommended());
        assertTrue(code, shown.get(i).getShare() <= previous);
        previous = shown.get(i).getShare();
      }
    }
  }

  /** A case the world is split on is not handed a recommendation it does not have. */
  @Test
  public void recommendsOnlyWhereTheVoteIsNotClose() {
    // OLL 13's top two are level; the Na's first is more than twice its second.
    assertFalse(LastLayerCaseAlgorithms.listed("oll_13").get(0).isRecommended());
    assertTrue(LastLayerCaseAlgorithms.listed("pll_na").get(0).isRecommended());
    // Nothing to be clearer than, so nothing is claimed.
    List<LastLayerCaseAlgorithms.Algorithm> alone = LastLayerCaseAlgorithms.listed("pll_jb");
    assertEquals(1, alone.size());
    assertFalse(alone.get(0).isRecommended());
  }

  /** What guards an algorithm a user types in: it has to solve the case it is being filed under. */
  @Test
  public void acceptsOnlyAnAlgorithmThatSolvesTheCase() {
    assertTrue(LastLayerCaseAlgorithms.solves("oll_27", "R U R' U R U2 R'"));
    assertTrue(LastLayerCaseAlgorithms.solves("pll_t", "R U R' U' R' F R2 U' R' U' R U R' F'"));
    // Right algorithm, wrong case.
    assertFalse(LastLayerCaseAlgorithms.solves("oll_26", "R U R' U R U2 R'"));
    // An OLL is done when the layer is oriented, so another correct algorithm still passes.
    assertTrue(LastLayerCaseAlgorithms.solves("oll_27", "y' R' U2 R U R' U R"));
    assertFalse(LastLayerCaseAlgorithms.solves("oll_27", "R U R' U R U2 R"));
    assertFalse(LastLayerCaseAlgorithms.solves("oll_27", "R U R' banana"));
    assertFalse(LastLayerCaseAlgorithms.solves("oll_27", ""));
    assertFalse(LastLayerCaseAlgorithms.solves("oll_27", null));
    assertFalse(LastLayerCaseAlgorithms.solves(null, "R U R' U R U2 R'"));
  }

  /** Every algorithm on file passes the check the user's own entries are put through. */
  @Test
  public void acceptsEveryAlgorithmItAlreadyHolds() {
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      assertTrue(row[0] + " | " + row[1], LastLayerCaseAlgorithms.solves(row[0], row[1]));
    }
  }

  /** Listed until most of the votes are held, and not one algorithm further. */
  @Test
  public void listsTheFewestAlgorithmsHoldingMostOfTheVotes() {
    for (String code : LastLayerScrambles.cases()) {
      List<LastLayerCaseAlgorithms.Algorithm> listed = LastLayerCaseAlgorithms.listed(code);
      int held = 0;
      for (LastLayerCaseAlgorithms.Algorithm algorithm : listed) {
        held += algorithm.getShare();
      }
      int beforeLast = held - listed.get(listed.size() - 1).getShare();
      assertTrue(code + " holds " + held, held >= AlgorithmList.COVERED_VOTES - 2); // rounding
      assertTrue(code + " already held " + beforeLast, beforeLast < AlgorithmList.COVERED_VOTES + 2);
    }
  }

  /** A case everyone solves the same way is listed one way, whatever else is on file. */
  @Test
  public void listsOneAlgorithmForACaseWithOneAnswer() {
    assertEquals(1, LastLayerCaseAlgorithms.listed("pll_jb").size());
    assertEquals(1, LastLayerCaseAlgorithms.listed("oll_27").size());
    assertTrue(LastLayerCaseAlgorithms.listed("pll_ua").size() > 1);
  }

  /**
   * What the picture beside them promises: every algorithm of a case is written for the cube held
   * one way, so the reader turns nothing between recognising the case and starting. The first one is
   * the one {@link LastLayerDiagram} draws, so the rest are held to it.
   */
  @Test
  public void writesEveryAlgorithmForThePicture() {
    List<String> wrong = new ArrayList<String>();
    String code = null;
    String picture = null;
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      if (!row[0].equals(code)) {
        code = row[0];
        picture = Notation.caseState(row[1]);
      } else if (!worksFrom(picture, row[1], code.startsWith("oll_"))) {
        wrong.add(code + " | " + row[1]);
      }
    }
    assertEquals(join(wrong), 0, wrong.size());
  }

  /** A turn of the cube before the most used algorithm would be a turn of the picture. */
  @Test
  public void turnsNothingBeforeTheFirstAlgorithm() {
    String code = null;
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      if (row[0].equals(code)) {
        continue;
      }
      code = row[0];
      assertFalse(code + " | " + row[1], row[1].split(" ")[0].matches("y('|2)?"));
    }
  }

  /**
   * Whether an algorithm does its job on the case as it is drawn: an orientation has to find the
   * same stickers facing up, and a permutation the same pieces in the same places. An orientation is
   * held to where the layer starts rather than where it ends, since it is the picture it has to
   * agree with; a permutation may leave the layer facing any way it likes.
   */
  /** Every algorithm on file is recognised from its own moves, which is the floor for the rest. */
  @Test
  public void recognisesEveryAlgorithmItHolds() {
    List<String> missed = new ArrayList<String>();
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      LastLayerCaseAlgorithms.Algorithm matched = LastLayerCaseAlgorithms.matching(row[0], row[1]);
      if (matched == null || !(sameAlgorithm(matched.getMoves(), row[1])
          || mirrorOf(matched.getMoves(), row[1]))) {
        missed.add(row[0] + " | " + row[1] + " | read as "
            + (matched == null ? "none of them" : matched.getMoves()));
      }
    }
    assertEquals(join(missed), 0, missed.size());
  }

  /** What an execution actually looks like: aligned, held somewhere, and turned into the next case. */
  @Test
  public void recognisesOneThroughAnAlignmentAndAGrip() {
    String tperm = "R U R' U' R' F R2 U' R' U' R U R' F'";
    assertNotNull(LastLayerCaseAlgorithms.matching("pll_t", "U " + tperm + " U2"));
    assertNotNull(LastLayerCaseAlgorithms.matching("pll_t", "y2 " + tperm));
    assertNotNull(LastLayerCaseAlgorithms.matching("pll_t", "U' y " + tperm + " y' U"));
    // The regrip in the middle of it, turned back before the algorithm goes on.
    assertNotNull(LastLayerCaseAlgorithms.matching("pll_t", "R U R' U' R' F R2 U' R' U' R U R' D D' F'"));
  }

  /** A misread is the whole point of asking: the algorithm run is not the case it was handed. */
  @Test
  public void doesNotRecogniseAnAlgorithmOfAnotherCase() {
    assertNull(LastLayerCaseAlgorithms.matching("oll_21", "R U R' U R U2 R'")); // that is OLL 27
    assertNull(LastLayerCaseAlgorithms.matching("oll_27", "R U R' banana"));
    assertNull(LastLayerCaseAlgorithms.matching("oll_27", ""));
    assertNull(LastLayerCaseAlgorithms.matching("oll_27", null));
    assertNull(LastLayerCaseAlgorithms.matching(null, "R U R' U R U2 R'"));
  }

  /**
   * A sixth of the table is one algorithm written twice, and the mirrored spelling turns the cube
   * exactly as the first one does. Nothing reading the moves can tell those apart, so the most used
   * of them answers for all of them rather than a coin being tossed.
   */
  @Test
  public void answersWithTheMostUsedOfTheSpellingsThatTurnTheCubeAlike() {
    String mirrored = "y2 l' U2 L U L' U l"; // OLL 5's most used, mirrored behind the rotation
    assertTrue(sameAlgorithm("r' U2 R U R' U r", mirrored));
    assertEquals("r' U2 R U R' U r", LastLayerCaseAlgorithms.matching("oll_5", mirrored).getMoves());
    assertEquals("r' U2 R U R' U r",
        LastLayerCaseAlgorithms.matching("oll_5", "r' U2 R U R' U r").getMoves());
    // And for a cube that was standing on its side when the case came up.
    assertEquals("r' U2 R U R' U r",
        LastLayerCaseAlgorithms.matching("oll_5", "z r' U2 R U R' U r z'").getMoves());
  }

  /**
   * The fold: OLL 45's four rows are one algorithm, turned with either hand and with the wide or the
   * slice, so the case has one answer rather than the two its rows suggest.
   */
  @Test
  public void foldsTheRowsThatAreOneAlgorithmSaidTwice() {
    List<LastLayerCaseAlgorithms.Algorithm> folded = LastLayerCaseAlgorithms.folded("oll_45");

    assertEquals(4, rowsFor("oll_45"));
    assertEquals(1, folded.size());
    assertEquals("F R U R' U' F'", folded.get(0).getMoves());
    assertEquals(100, folded.get(0).getShare());
  }

  /**
   * How much of the table it is, pinned: 75 of the 304 rows are another row said differently, a
   * mirror included. A new row changing these two numbers is expected; one changing only the second
   * is a duplicate that was not spotted when it was added.
   */
  @Test
  public void foldsAwayASixthOfTheTable() {
    int folded = 0;
    for (String caseCode : LastLayerScrambles.cases()) {
      folded += LastLayerCaseAlgorithms.folded(caseCode).size();
    }

    assertEquals(304, LastLayerCaseAlgorithms.rows().size());
    assertEquals(229, folded);
  }

  /** Folding moves votes between spellings of a case and never in or out of it. */
  @Test
  public void foldingAddsUpToTheWholeCase() {
    for (String caseCode : LastLayerScrambles.cases()) {
      int share = 0;
      for (LastLayerCaseAlgorithms.Algorithm algorithm : LastLayerCaseAlgorithms.folded(caseCode)) {
        share += algorithm.getShare();
      }
      assertTrue(caseCode + " came to " + share, Math.abs(share - 100) <= 2); // rounding, per row
    }
  }

  /** A top-heavy case: nine solvers in ten turn one algorithm, and the tail is what is unusual. */
  @Test
  public void readsTheRareSpellingOfATopHeavyCaseAsUnusual() {
    assertFalse(LastLayerCaseAlgorithms
        .read("pll_jb", "R U R' F' R U R' U' R' F R2 U' R'").isUnusual());
    assertTrue(LastLayerCaseAlgorithms
        .read("pll_jb", "R U2 R' U' R U2 L' U R' U' L").isUnusual());
    assertTrue(LastLayerCaseAlgorithms
        .read("pll_jb", "r' F R F' r U2 R' U R U2 R'").isUnusual());
  }

  /** The Z perm's four rows are two algorithms, each also written with U and U' swapped: a mirror. */
  @Test
  public void foldsAMirrorIntoTheAlgorithmItMirrors() {
    assertEquals(2, LastLayerCaseAlgorithms.folded("pll_z").size());
    assertEquals(2, LastLayerCaseAlgorithms.listed("pll_z").size());
    for (String z : new String[] {"M' U M2 U M2 U M' U2 M2", "M2 U' M2 U' M' U2 M2 U2 M'",
        "M' U' M2 U' M2 U' M' U2 M2", "M2 U M2 U M' U2 M2 U2 M'"}) {
      assertFalse(z, LastLayerCaseAlgorithms.read("pll_z", z).isUnusual());
    }
  }

  /** What most people turn is never unusual, which is the rule {@code forCase} already follows. */
  @Test
  public void neverReadsWhatMostPeopleTurnAsUnusual() {
    for (String caseCode : LastLayerScrambles.cases()) {
      String mostUsed = LastLayerCaseAlgorithms.folded(caseCode).get(0).getMoves();
      assertFalse(caseCode, LastLayerCaseAlgorithms.read(caseCode, mostUsed).isUnusual());
    }
  }

  /** An algorithm the table has not got is unusual too, and this one is two algorithms. */
  @Test
  public void readsAnAlgorithmTheTableHasNotGotAsUnusual() {
    String twoSunes = "R U R' U R U2 R' U2 R U R' U R U2 R'";
    AlgorithmExecution execution = LastLayerCaseAlgorithms.read("oll_45", twoSunes);

    assertTrue(execution.isUnusual());
    assertTrue(execution.isLonger());
    assertEquals(15, execution.getMoves());
    assertEquals(6, execution.getUsualMoves());
  }

  /** Rare is not long: a spelling few people turn can be the shortest thing on the list. */
  @Test
  public void saysNothingAboutTheLengthOfAShortRareAlgorithm() {
    AlgorithmExecution execution =
        LastLayerCaseAlgorithms.read("pll_jb", "R U2 R' U' R U2 L' U R' U' L");

    assertTrue(execution.isUnusual());
    assertFalse(execution.isLonger());
  }

  @Test
  public void hasNothingToSayAboutNothing() {
    assertFalse(LastLayerCaseAlgorithms.read("oll_27", null).isUnusual());
    assertFalse(LastLayerCaseAlgorithms.read(null, "R U R' U R U2 R'").isUnusual());
    assertFalse(LastLayerCaseAlgorithms.read("oll_27", "R U R' banana").isLonger());
    assertTrue(LastLayerCaseAlgorithms.folded("pll_nothing").isEmpty());
  }

  /**
   * An execution comes back named from wherever the solver was holding the cube, and counting it
   * there reads the turns that align the layer as part of the algorithm. All 24 ways of holding the
   * same eleven-move Jb have to come to eleven, or a solver is told their own algorithm is long
   * because of how they picked the cube up.
   */
  @Test
  public void countsAnExecutionFromTheFrameTheCaseIsDrawnIn() {
    String jb = "R U2 R' U' R U2 L' U R' U' L";
    for (char[] grip : AlgorithmForm.grips()) {
      String stood = written(AlgorithmForm.conjugatedBy(AlgorithmForm.of(jb), grip));
      AlgorithmExecution execution = LastLayerCaseAlgorithms.read("pll_jb", stood);

      assertEquals(stood, 11, execution.getMoves());
      assertFalse(stood, execution.isLonger());
    }
  }

  /** And the alignment still comes off, wherever the layer was pointing when it was turned. */
  @Test
  public void takesTheAlignmentOffAnExecutionInAnyFrame() {
    String tperm = "R U R' U' R' F R2 U' R' U' R U R' F'";
    int plain = LastLayerCaseAlgorithms.read("pll_t", tperm).getMoves();

    assertEquals(plain, LastLayerCaseAlgorithms.read("pll_t", "U " + tperm + " U2").getMoves());
    assertEquals(plain, LastLayerCaseAlgorithms.read("pll_t", "y " + tperm).getMoves());
    assertEquals(plain, LastLayerCaseAlgorithms.read("pll_t", "z " + tperm + " z'").getMoves());
  }

  /**
   * It cannot call an execution unusual when it could not read the execution at all: that would be
   * a claim about something it knows nothing about. Unreadable, not merely unrecognised, since
   * {@code b} is a wide B and a great deal of nonsense parses.
   */
  @Test
  public void saysNothingAboutNotationItCannotRead() {
    AlgorithmExecution execution = LastLayerCaseAlgorithms.read("oll_27", "R U 7");

    assertFalse(execution.isUnusual());
    assertFalse(execution.isLonger());
    assertEquals(0, execution.getMoves());
  }

  private static String written(List<String> turns) {
    StringBuilder written = new StringBuilder();
    for (String turn : turns) {
      written.append(written.length() == 0 ? "" : " ").append(turn);
    }
    return written.toString();
  }

  private static int rowsFor(String caseCode) {
    int rows = 0;
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      if (row[0].equals(caseCode)) {
        rows++;
      }
    }
    return rows;
  }

  private static boolean mirrorOf(String one, String other) {
    String mirror = AlgorithmForm.written(AlgorithmForm.mirrored(AlgorithmForm.of(other)));
    return AlgorithmForm.keyFromAnyGrip(one).equals(AlgorithmForm.keyFromAnyGrip(mirror));
  }

  private static boolean sameAlgorithm(String one, String other) {
    return AlgorithmForm.withoutAlignment(AlgorithmForm.of(one))
        .equals(AlgorithmForm.withoutAlignment(AlgorithmForm.of(other)));
  }

  private static boolean worksFrom(String picture, String alg, boolean orientation) {
    for (String auf : orientation ? new String[] {""} : AUFS) {
      String state = Notation.caseState((alg + " " + auf).trim());
      if (orientation ? sameStickersUp(state, picture) : state.equals(picture)) {
        return true;
      }
    }
    return false;
  }

  private static boolean sameStickersUp(String state, String picture) {
    for (int facelet : LAYER) {
      if ((state.charAt(facelet) == 'U') != (picture.charAt(facelet) == 'U')) {
        return false;
      }
    }
    return true;
  }

  private static String nameOf(String code, String state) {
    String name = code.startsWith("oll_") ? LastLayerCases.orientation(state, CROSS)
        : LastLayerCases.permutation(state, CROSS);
    assertNotNull(code, name);
    return (code.startsWith("oll_") ? "oll_" : "pll_") + name;
  }

  private static String join(List<String> lines) {
    StringBuilder joined = new StringBuilder("\n");
    for (String line : lines) {
      joined.append(line).append("\n");
    }
    return joined.toString();
  }

  /**
   * The whole point of folding a list: a case may not offer a choice between an algorithm and
   * itself. The unfolded list does, which is what says this test is testing something.
   */
  @Test
  public void aFoldedListNeverShowsTheSameAlgorithmTwice() {
    int twiceUnfolded = 0;
    for (String code : LastLayerScrambles.cases()) {
      assertFalse(code, saysOneAlgorithmTwice(code, LastLayerCaseAlgorithms.listed(code)));
      if (saysOneAlgorithmTwice(code, LastLayerCaseAlgorithms.forCase(code))) {
        twiceUnfolded++;
      }
    }
    assertTrue("nothing to fold, so this proves nothing", twiceUnfolded > 0);
  }

  /** And folding is what puts the algorithm most people turn in front, not just what tidies up. */
  @Test
  public void foldingCanChangeWhichRowComesFirst() {
    int moved = 0;
    for (String code : LastLayerScrambles.cases()) {
      List<LastLayerCaseAlgorithms.Algorithm> folded = LastLayerCaseAlgorithms.folded(code);
      List<LastLayerCaseAlgorithms.Algorithm> unfolded = LastLayerCaseAlgorithms.forCase(code);
      if (!folded.isEmpty() && !unfolded.isEmpty()
          && !LastLayerCaseAlgorithms.sameTurning(code, folded.get(0).getMoves(),
              unfolded.get(0).getMoves())) {
        moved++;
      }
    }
    assertTrue("folding moved no case's first row", moved > 0);
  }

  private static boolean saysOneAlgorithmTwice(String code,
      List<LastLayerCaseAlgorithms.Algorithm> shown) {
    for (int i = 0; i < shown.size(); i++) {
      for (int j = i + 1; j < shown.size(); j++) {
        if (LastLayerCaseAlgorithms.sameTurning(code, shown.get(i).getMoves(),
            shown.get(j).getMoves())) {
          return true;
        }
      }
    }
    return false;
  }

  /** A pick is kept as text, so the row it belongs to has to be found by what it turns. */
  @Test
  public void aSpellingFoldedAwayIsStillTheRowItWasFoldedInto() {
    assertTrue(LastLayerCaseAlgorithms.sameTurning("oll_27", "R U R' U R U2 R'",
        "y' L U L' U L U2 L'"));
    assertTrue(LastLayerCaseAlgorithms.sameTurning("oll_27", "U R U R' U R U2 R' U'",
        "R U R' U R U2 R'"));
    assertFalse(LastLayerCaseAlgorithms.sameTurning("oll_27", "R U R' U R U2 R'",
        "R U2 R' U' R U' R'"));
    assertFalse(LastLayerCaseAlgorithms.sameTurning("oll_27", "R U R' U R U2 R'", null));
  }

  /** What an execution loses on its way to being shown, and what it must never lose. */
  @Test
  public void tidyingTakesTheAlignmentAndTheRegripOffAndNothingElse() {
    // The turn that squared the case up, the one left for the next case, and a regrip turned back.
    assertEquals("R U R' U R U2 R'",
        LastLayerCaseAlgorithms.tidied("oll_27", "U R U R' U R U2 R' D D' U'"));
    // The grip it was picked up in is not part of the algorithm.
    assertEquals("R U R' U R U2 R'",
        LastLayerCaseAlgorithms.tidied("oll_27", "y y R U R' U R U2 R'"));
    // But the faces the solver turned stay the faces they turned.
    assertEquals("L U L' U L U2 L'",
        LastLayerCaseAlgorithms.tidied("oll_27", "L U L' U L U2 L' U"));
  }

  /**
   * A rotation inside an algorithm is part of how it is spelled, and folding it away names the
   * later turns from before it — which moves the layer off the top, so what looked like alignment
   * was not. Such an execution is handed back rather than tidied into something else.
   */
  @Test
  public void anExecutionItCannotTidyWithoutChangingItIsHandedBack() {
    String executed = "x' R U' R' D R U R' D' R U R' D R U' R' D' x";
    assertEquals(executed, LastLayerCaseAlgorithms.tidied("pll_e", executed));
    assertEquals("R U R' banana", LastLayerCaseAlgorithms.tidied("oll_27", "R U R' banana"));
    assertNull(LastLayerCaseAlgorithms.tidied("oll_27", null));
  }

  /**
   * A recorded Ua of 2026-09-03, whose gyro put a {@code y2} in the middle: every move after it is
   * named from the solver's new frame, so keeping them is keeping the R moves they turned. Folded
   * instead, the same execution came back written on L, which is the one thing a solver can tell is
   * wrong about their own solve.
   */
  @Test
  public void tidiesARegrippedExecutionOntoTheFacesItWasTurnedOn() {
    assertEquals("R U' R U R U R U' R' U' R2", LastLayerCaseAlgorithms.tidied("pll_ua",
        "x' U y2 R x B' x' R U R U R U' R' x B' R x' R"));
    // The same algorithm turned without the regrip, which is what it has to agree with.
    assertEquals("R U' R U R U R U' R' U' R2",
        LastLayerCaseAlgorithms.tidied("pll_ua", "R U' R U R U R U' R' U' R2 U2"));
  }

  /**
   * An execution is written as one of the table's own spellings of the row it matched, and as one
   * that was turned the way it was: never as the mirror of it, and never as text the table does not
   * hold. A row is one algorithm and several spellings of it, and the spellings are not
   * interchangeable to the solver — one of them is written for the other hand.
   *
   * <p>The row it is written as need not be spelled the way the list draws it, and the dialog
   * compares turnings rather than text throughout so that the two halves still meet.
   */
  @Test
  public void anExecutionIsWrittenAsASpellingOfItsOwnTurning() {
    int checked = 0;
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      for (char[] grip : AlgorithmForm.grips()) {
        String executed =
            String.join(" ", AlgorithmForm.conjugatedBy(AlgorithmForm.of(row[1]), grip));
        LastLayerCaseAlgorithms.Algorithm written =
            LastLayerCaseAlgorithms.matching(row[0], executed);
        if (written == null) {
          // Alignment comes off the face named U, so a grip that stands the layer elsewhere
          // leaves turns on it that no algorithm of the case has. Nothing is claimed about those.
          continue;
        }
        checked++;
        assertTrue(row[0] + " turned as " + executed + " is written as " + written.getMoves(),
            LastLayerCaseAlgorithms.sameTurning(row[0], executed, written.getMoves()));
        assertTrue(written.getMoves() + " is no spelling the table holds",
            holdsSpelling(row[0], written.getMoves()));
      }
    }
    assertTrue("no execution was written as a row, so this proves nothing", checked > 0);
  }

  private static boolean holdsSpelling(String caseCode, String moves) {
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      if (row[0].equals(caseCode) && row[1].equals(moves)) {
        return true;
      }
    }
    return false;
  }
}
