package com.cube.nanotimer.coach;

import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.session.CaseKnowledge.Evidence;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@RunWith(JUnit4.class)
public class CaseRecallTest {

  private static final TimeZone ZONE = TimeZone.getTimeZone("Europe/Brussels");
  private static final long DAY = 24L * 60 * 60 * 1000;
  /** 2026-11-13 12:00 in Brussels. */
  private static final long NOW = 1794567600000L;

  @Test
  public void testCountsCleanDaysSinceTheNewestSlipOnly() {
    CaseRecall recall = recall(CaseKnowledge.Status.KNOWN,
        at(Evidence.UNAIDED, 40), at(Evidence.HELPED, 20), at(Evidence.UNAIDED, 12),
        at(Evidence.CLEAN_REP, 5), at(Evidence.CLEAN_REP, 5), at(Evidence.UNAIDED, 3));

    Assert.assertEquals(3, recall.getCleanDays()); // days 12, 5 and 3; two reps on day 5 are one
    Assert.assertEquals(Integer.valueOf(3), recall.getCleanDaysAgo());
    Assert.assertEquals(Integer.valueOf(20), recall.getSlipDaysAgo());
    Assert.assertEquals(CaseRecall.TWO_LOOKS, recall.getSlipKind());
    Assert.assertEquals(0, recall.getSlips()); // nothing in the last week
    Assert.assertEquals(3, recall.getSeenDaysAgo());
  }

  @Test
  public void testCountsTheWeeksSlipsAndNamesTheNewest() {
    CaseRecall recall = recall(CaseKnowledge.Status.NEEDS_REVIEW,
        at(Evidence.UNAIDED, 30), at(Evidence.HELPED, 6), at(Evidence.LOOKED_UP, 2));

    Assert.assertEquals(2, recall.getSlips());
    Assert.assertEquals(Integer.valueOf(2), recall.getSlipDaysAgo());
    Assert.assertEquals(CaseRecall.LOOKED_UP, recall.getSlipKind());
    Assert.assertEquals(0, recall.getCleanDays());
    Assert.assertNull(recall.getCleanDaysAgo());
    Assert.assertTrue(recall.needsReview());
  }

  @Test
  public void testDaysAreLocalCalendarDays() {
    long lateYesterday = NOW - 12L * 60 * 60 * 1000 - 60 * 1000; // 23:59 the day before
    CaseRecall recall = CaseRecall.of("pll_t", CaseKnowledge.Status.KNOWN,
        Collections.singletonList(new CaseRecall.Occurrence(Evidence.UNAIDED, lateYesterday)),
        null, NOW, ZONE);

    Assert.assertEquals(1, recall.getSeenDaysAgo());
  }

  @Test
  public void testRecentRecognitionIsTheMedianOfTheNewestThree() {
    CaseRecall recall = CaseRecall.of("pll_t", CaseKnowledge.Status.KNOWN,
        Collections.singletonList(new CaseRecall.Occurrence(Evidence.UNAIDED, NOW)),
        Arrays.asList(900L, 4000L, 700L, 100L), NOW, ZONE);

    Assert.assertEquals(Long.valueOf(900), recall.getRecentRecognitionMs());
    Assert.assertNull(CaseRecall.of("pll_t", CaseKnowledge.Status.KNOWN,
        Collections.singletonList(new CaseRecall.Occurrence(Evidence.UNAIDED, NOW)),
        Arrays.asList(900L, 700L), NOW, ZONE).getRecentRecognitionMs());
  }

  @Test
  public void testBuilderSendsOnlyLearnedLastLayerCases() {
    List<CaseKnowledge> knowledge = Arrays.asList(
        new CaseKnowledge("pll", "t", CaseKnowledge.Status.KNOWN, 3, NOW),
        new CaseKnowledge("pll", "gb", CaseKnowledge.Status.NEEDS_REVIEW, 3, NOW),
        new CaseKnowledge("oll", "53", CaseKnowledge.Status.TO_LEARN, 3, NOW),
        new CaseKnowledge("pll", "aa", CaseKnowledge.Status.KNOWN, 3, NOW));
    Map<String, List<CaseRecall.Occurrence>> history =
        new HashMap<String, List<CaseRecall.Occurrence>>();
    for (String code : Arrays.asList("pll_t", "pll_gb", "oll_53")) {
      history.put(code, Collections.singletonList(new CaseRecall.Occurrence(Evidence.UNAIDED,
          NOW - DAY)));
    }
    List<StepSample> samples = new ArrayList<StepSample>();
    for (int i = 0; i < 3; i++) {
      samples.add(new StepSample("pll_t", 2000, 500 + i * 100, false, i));
      samples.add(new StepSample("pll_t", 1000, 9000, true, i)); // a part says nothing
    }

    List<CaseRecall> recall =
        CoachPayloadBuilder.caseRecall(knowledge, history, samples, NOW, ZONE);

    Assert.assertEquals(2, recall.size()); // no to-learn case, none without history
    Assert.assertEquals("pll_t", recall.get(0).getCode());
    Assert.assertEquals(Long.valueOf(600), recall.get(0).getRecentRecognitionMs());
    Assert.assertEquals("needs_review", recall.get(1).getStatus());
  }

  @Test
  public void testRecallSurvivesARoundTripAndOtherWithers() {
    CoachPayload payload = payload().withCaseRecall(Collections.singletonList(recall(
        CaseKnowledge.Status.NEEDS_REVIEW, at(Evidence.UNAIDED, 30), at(Evidence.HELPED, 2))))
        .withLanguage("fr").withFinalAuf(null);

    CoachPayload read = CoachPayload.parse(payload.toJson());

    Assert.assertEquals(CoachPayload.VERSION, read.getSchemaVersion());
    Assert.assertEquals(1, read.getCaseRecall().size());
    CaseRecall one = read.getCaseRecall().get(0);
    Assert.assertEquals("pll_t", one.getCode());
    Assert.assertTrue(one.needsReview());
    Assert.assertEquals(1, one.getSlips());
    Assert.assertEquals(Integer.valueOf(2), one.getSlipDaysAgo());
    Assert.assertEquals(CaseRecall.TWO_LOOKS, one.getSlipKind());
    Assert.assertNull(one.getCleanDaysAgo());
    Assert.assertNull(one.getRecentRecognitionMs());
  }

  @Test
  public void testAPayloadWithoutRecallSendsNoneAndAnOlderOneStillReads() {
    Assert.assertFalse(payload().toJson().contains("case_recall"));
    String older = payload().toJson().replace("\"schema_version\":" + CoachPayload.VERSION,
        "\"schema_version\":17");
    Assert.assertTrue(CoachPayload.parse(older).getCaseRecall().isEmpty());
  }

  private static CaseRecall recall(CaseKnowledge.Status status,
      CaseRecall.Occurrence... occurrences) {
    return CaseRecall.of("pll_t", status, Arrays.asList(occurrences), null, NOW, ZONE);
  }

  private static CaseRecall.Occurrence at(Evidence says, int daysAgo) {
    return new CaseRecall.Occurrence(says, NOW - daysAgo * DAY);
  }

  private static CoachPayload payload() {
    return CoachPayloadBuilder.build("3x3", "cfop", Collections.<Long>emptyList(),
        Collections.<StepSample>emptyList(), Collections.<StepSample>emptyList(), 0,
        Collections.<StepSample>emptyList(), 0, Collections.<CaseKnowledge>emptyList());
  }
}
