package com.cube.nanotimer.session;

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
  public void testACaseSeenTooFewTimesHasNoStatusAtAll() {
    Assert.assertNull(CaseKnowledge.read(occurrences("11")));
    Assert.assertNull(CaseKnowledge.read(occurrences("00")));
    Assert.assertNull(CaseKnowledge.read(new ArrayList<Boolean>()));
  }

  @Test
  public void testACaseGoingInUnaidedIsKnown() {
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("111")));
  }

  @Test
  public void testACaseTakingMoreThanOneAlgorithmIsBeingLearned() {
    Assert.assertEquals(Status.LEARNING, CaseKnowledge.read(occurrences("000")));
  }

  /** The whole point of the hysteresis: one bad solve is a bad solve. */
  @Test
  public void testOneOccurrenceAgainstTheStatusDoesNotFlipIt() {
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("11111110")));
    Assert.assertEquals(Status.LEARNING, CaseKnowledge.read(occurrences("00000001")));
  }

  @Test
  public void testTwoInARowFlipIt() {
    Assert.assertEquals(Status.LEARNING, CaseKnowledge.read(occurrences("11111100")));
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("00000011")));
  }

  /** A case that never goes in twice running was never shown to be known. */
  @Test
  public void testACaseAlternatingIsNotCalledKnown() {
    Assert.assertEquals(Status.LEARNING, CaseKnowledge.read(occurrences("1010101010")));
  }

  /** Learned since: the old occurrences are in the window and still lose to the recent ones. */
  @Test
  public void testACaseLearnedPartWayThroughTheWindowIsKnown() {
    Assert.assertEquals(Status.KNOWN, CaseKnowledge.read(occurrences("0000011111")));
  }

  @Test
  public void testACaseDroppedPartWayThroughTheWindowIsNotKnown() {
    Assert.assertEquals(Status.LEARNING, CaseKnowledge.read(occurrences("1111100000")));
  }

  @Test
  public void testAStatusIsStoredUnderItsOwnCodeAndNotItsPosition() {
    Assert.assertEquals(Status.KNOWN, Status.forCode("known"));
    Assert.assertEquals(Status.LEARNING, Status.forCode("learning"));
    Assert.assertNull(Status.forCode("KNOWN"));
    Assert.assertNull(Status.forCode(""));
  }

  /** Oldest first, 1 for a case that went in unaided. */
  private static List<Boolean> occurrences(String reading) {
    List<Boolean> occurrences = new ArrayList<Boolean>();
    for (int i = 0; i < reading.length(); i++) {
      occurrences.add(Boolean.valueOf(reading.charAt(i) == '1'));
    }
    return occurrences;
  }
}
