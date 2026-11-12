package com.cube.nanotimer.session;

import com.cube.nanotimer.session.CaseKnowledge.Evidence;
import com.cube.nanotimer.session.CaseKnowledge.Status;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class CaseKnowledgeTest {

  @Test
  public void testACaseWithNothingBehindItHasNoStatusAtAll() {
    Assert.assertNull(CaseKnowledge.read(new ArrayList<Evidence>()));
    Assert.assertNull(CaseKnowledge.read(null));
  }

  /** One is enough: a rare OLL turns up once in sixty solves and should not wait for a second. */
  @Test
  public void testOneSolveGoingInUnaidedIsEnoughToBeKnown() {
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("U")));
  }

  @Test
  public void testOneSolveTakingMoreThanOneAlgorithmIsStillToLearn() {
    Assert.assertEquals(Status.TO_LEARN, CaseKnowledge.read(occurrences("H")));
  }

  /** One unaided occurrence promotes immediately, whatever the case did before it. */
  @Test
  public void testOneUnaidedOccurrenceIsEnoughHoweverBadTheRecordBeforeIt() {
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("HHHHHU")));
  }

  /**
   * An algorithm fired in reverse, or a case missed once, is a slip rather than knowledge lost: it
   * would be wrong to ask for a review of a case with nine clean executions behind it.
   */
  @Test
  public void testASingleLapseAgainstAnUnaidedRecordIsForgiven() {
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("UUUUUUUUUH")));
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("UUUUUH")));
  }

  /** Twice running is a pattern rather than a slip, and it is what demotes. */
  @Test
  public void testTwoLapsesInARowStopACaseReadingAsKnown() {
    Assert.assertEquals(Status.NEEDS_REVIEW, CaseKnowledge.read(occurrences("UUUUUHH")));
    Assert.assertEquals(Status.NEEDS_REVIEW, CaseKnowledge.read(occurrences("UHHHHHH")));
  }

  /**
   * The accepted cost of forgiving one: a case slipped on every other time still reads as known,
   * since no two of its lapses are ever adjacent.
   */
  @Test
  public void testAlternatingLapsesGoOnReadingAsKnown() {
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("UHUHUH")));
  }

  /** The whole point of the split: never once done in one algorithm is not the same as slipping. */
  @Test
  public void testACaseNeverPutInWithOneAlgorithmIsToLearnHoweverOftenItHasComeUp() {
    Assert.assertEquals(Status.TO_LEARN, CaseKnowledge.read(occurrences("HHHHHHH")));
    Assert.assertEquals(Status.TO_LEARN, CaseKnowledge.read(occurrences("HcHLH")));
  }

  /** One unaided occurrence, however long ago and however buried, is what separates the two. */
  @Test
  public void testACaseWithNoUnaidedOccurrenceAtAllIsToLearnRatherThanReview() {
    Assert.assertEquals(Status.TO_LEARN, CaseKnowledge.read(occurrences("HHHHHHH")));
    Assert.assertEquals(Status.NEEDS_REVIEW, CaseKnowledge.read(occurrences("UHHHHHH")));
  }

  @Test
  public void testCleanDrillRepsShortOfARunSayNothing() {
    Assert.assertNull(CaseKnowledge.read(occurrences("cccc")));
    Assert.assertNull(CaseKnowledge.read(occurrences("CC")));
    Assert.assertEquals(Status.TO_LEARN, CaseKnowledge.read(occurrences("Hcccc")));
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("Uccc")));
  }

  @Test
  public void testFiveCleanRepsInARowMakeACaseKnown() {
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("ccccc")));
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("HHccccc")));
  }

  /** Three from a large pool, but only three of those in a row: a small-pool rep breaks that count. */
  @Test
  public void testThreeCleanRepsFromALargePoolAreEnough() {
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("CCC")));
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("ccCCC")));
    Assert.assertNull(CaseKnowledge.read(occurrences("CCcC")));
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("CCcCc")));
  }

  @Test
  public void testALookupStartsTheRunAgain() {
    Assert.assertNull(CaseKnowledge.read(occurrences("ccccLc")));
    Assert.assertNull(CaseKnowledge.read(occurrences("CCLC")));
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("ccLccccc")));
  }

  /** The panel is opened to compare algorithms too, so a lookup alone never moves a case down. */
  @Test
  public void testALookupNeverMovesACaseDown() {
    Assert.assertNull(CaseKnowledge.read(occurrences("LLL")));
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("ULLLL")));
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("cccccLL")));
  }

  @Test
  public void testALapseInASolveBreaksTheRun() {
    Assert.assertEquals(Status.TO_LEARN, CaseKnowledge.read(occurrences("cccHcc")));
  }

  /** The one lapse is forgiven only where a solve has put the case in since the lapse before. */
  @Test
  public void testKnownFromDrillsAloneGetsNoGrace() {
    Assert.assertEquals(Status.NEEDS_REVIEW, CaseKnowledge.read(occurrences("cccccH")));
    Assert.assertEquals(Status.NEEDS_REVIEW, CaseKnowledge.read(occurrences("UHcccccH")));
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("UcccccH")));
  }

  @Test
  public void testARunCountsAsOneOccurrence() {
    Assert.assertEquals(1, CaseKnowledge.evidenceCount(occurrences("cccccccccc")));
    Assert.assertEquals(3, CaseKnowledge.evidenceCount(occurrences("UcccccH")));
    Assert.assertEquals(0, CaseKnowledge.evidenceCount(occurrences("ccccLLc")));
  }

  @Test
  public void testAStatusIsStoredUnderItsOwnCodeAndNotItsPosition() {
    Assert.assertEquals(Status.KNOWN, Status.forCode("known"));
    Assert.assertEquals(Status.NEEDS_REVIEW, Status.forCode("needs_review"));
    Assert.assertEquals(Status.TO_LEARN, Status.forCode("to_learn"));
    Assert.assertNull(Status.forCode("learning")); // the code the split replaced
    Assert.assertNull(Status.forCode("KNOWN"));
    Assert.assertNull(Status.forCode(""));
  }

  /** The owner's OLL 16 on 2026-11-09: restarted reps (read as L) kept every run under five. */
  @Test
  public void testRestartedRepsKeepADrilledCaseToLearn() {
    Assert.assertEquals(Status.TO_LEARN,
        CaseKnowledge.read(occurrences("HH" + "LLcc" + "LLccLcccL")));
  }

  /** A learning drill's clear is known whatever its reps say: a reset there only took a dot back. */
  @Test
  public void testALearningDrillClearIsKnownEvenWhenTheRepsBrokeTheRun() {
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("X")));
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("cccLcccX")));
  }

  @Test
  public void testAClearAfterARunAlreadyCountedIsNotCountedTwice() {
    Assert.assertEquals(1, CaseKnowledge.evidenceCount(occurrences("cccccX")));
    Assert.assertEquals(2, CaseKnowledge.evidenceCount(occurrences("cccccLX")));
  }

  @Test
  public void testAClearedCaseTakenInTwoLooksNeedsReview() {
    Assert.assertEquals(Status.NEEDS_REVIEW, CaseKnowledge.read(occurrences("XH")));
  }

  @Test
  public void testFirstLearnedPointsAtWhatFirstMadeTheCaseLearned() {
    Assert.assertEquals(-1, CaseKnowledge.firstLearned(occurrences("HHcc")));
    Assert.assertEquals(1, CaseKnowledge.firstLearned(occurrences("HUHU")));
    Assert.assertEquals(5, CaseKnowledge.firstLearned(occurrences("Hcccccc")));
    Assert.assertEquals(3, CaseKnowledge.firstLearned(occurrences("ccLXU")));
    Assert.assertEquals(CaseKnowledge.Source.LEARNING_DRILL,
        CaseKnowledge.sourceOf(Evidence.CLEARED));
    Assert.assertEquals(CaseKnowledge.Source.SOLVE, CaseKnowledge.sourceOf(Evidence.UNAIDED));
    Assert.assertEquals(CaseKnowledge.Source.DRILL,
        CaseKnowledge.sourceOf(Evidence.CLEAN_REP_LARGE_POOL));
  }

  /**
   * Oldest first: U and H are solves in one algorithm and in more, c and C clean drill reps from a
   * small and a large pool, L a rep with the algorithm on screen or restarted, X a learning drill's
   * clear.
   */
  private static List<Evidence> occurrences(String reading) {
    List<Evidence> occurrences = new ArrayList<Evidence>();
    for (int i = 0; i < reading.length(); i++) {
      switch (reading.charAt(i)) {
        case 'U': occurrences.add(Evidence.UNAIDED); break;
        case 'H': occurrences.add(Evidence.HELPED); break;
        case 'c': occurrences.add(Evidence.CLEAN_REP); break;
        case 'C': occurrences.add(Evidence.CLEAN_REP_LARGE_POOL); break;
        case 'X': occurrences.add(Evidence.CLEARED); break;
        default: occurrences.add(Evidence.LOOKED_UP); break;
      }
    }
    return occurrences;
  }
}
