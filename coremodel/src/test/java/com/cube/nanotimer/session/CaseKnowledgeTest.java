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
  public void testOneSolveTakingMoreThanOneAlgorithmIsBeingLearned() {
    Assert.assertEquals(Status.LEARNING, CaseKnowledge.read(occurrences("H")));
  }

  /** The most recent occurrence decides, in both directions. */
  @Test
  public void testTheLatestOccurrenceIsTheOneThatCounts() {
    Assert.assertEquals(Status.LEARNING, CaseKnowledge.read(occurrences("UUUUUH")));
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("HHHHHU")));
  }

  /**
   * The cost of that, accepted deliberately: a case put in unaided nine times reads as being
   * learned after one two-look. It is what the solver last did with it.
   */
  @Test
  public void testOneLapseIsEnoughToStopACaseReadingAsKnown() {
    Assert.assertEquals(Status.LEARNING, CaseKnowledge.read(occurrences("UUUUUUUUUH")));
  }

  /** A drill hands the case over with nothing to recognise, so a clean rep promotes nothing. */
  @Test
  public void testACleanDrillRepSaysNothingEitherWay() {
    Assert.assertNull(CaseKnowledge.read(occurrences("SSS")));
    Assert.assertEquals(Status.LEARNING, CaseKnowledge.read(occurrences("HSSS")));
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("USSS")));
  }

  /** Asking to be shown the algorithm is the plainest thing in the database, drill or not. */
  @Test
  public void testBeingShownTheAlgorithmIsTheLastWord() {
    Assert.assertEquals(Status.LEARNING, CaseKnowledge.read(occurrences("USH")));
  }

  @Test
  public void testAStatusIsStoredUnderItsOwnCodeAndNotItsPosition() {
    Assert.assertEquals(Status.KNOWN, Status.forCode("known"));
    Assert.assertEquals(Status.LEARNING, Status.forCode("learning"));
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
