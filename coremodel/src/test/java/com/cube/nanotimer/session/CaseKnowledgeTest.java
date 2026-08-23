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
    Assert.assertEquals(Status.TO_LEARN, CaseKnowledge.read(occurrences("HSHSH")));
  }

  /** One unaided occurrence, however long ago and however buried, is what separates the two. */
  @Test
  public void testACaseWithNoUnaidedOccurrenceAtAllIsToLearnRatherThanReview() {
    Assert.assertEquals(Status.TO_LEARN, CaseKnowledge.read(occurrences("HHHHHHH")));
    Assert.assertEquals(Status.NEEDS_REVIEW, CaseKnowledge.read(occurrences("UHHHHHH")));
  }

  /** A drill hands the case over with nothing to recognise, so a clean rep promotes nothing. */
  @Test
  public void testACleanDrillRepSaysNothingEitherWay() {
    Assert.assertNull(CaseKnowledge.read(occurrences("SSS")));
    Assert.assertEquals(Status.TO_LEARN, CaseKnowledge.read(occurrences("HSSS")));
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("USSS")));
  }

  /** Asking to be shown counts as a lapse, drill or not, and is forgiven or not on the same terms. */
  @Test
  public void testBeingShownTheAlgorithmCountsAsALapse() {
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("USH")));
    Assert.assertEquals(Status.NEEDS_REVIEW, CaseKnowledge.read(occurrences("USHH")));
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

  /** Oldest first: U went in unaided, H needed help, S said nothing. */
  private static List<Evidence> occurrences(String reading) {
    List<Evidence> occurrences = new ArrayList<Evidence>();
    for (int i = 0; i < reading.length(); i++) {
      char said = reading.charAt(i);
      occurrences.add(said == 'U' ? Evidence.UNAIDED : said == 'H' ? Evidence.HELPED
          : Evidence.SILENT);
    }
    return occurrences;
  }
}
