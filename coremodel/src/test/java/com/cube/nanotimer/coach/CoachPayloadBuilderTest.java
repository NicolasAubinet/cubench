package com.cube.nanotimer.coach;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class CoachPayloadBuilderTest {

  @Test
  public void testCrossCarriesNoRecognition() {
    CoachPayload payload = build();

    Assert.assertNull(figure(payload.getFamilies(), "cross").getRecognitionMs());
    Assert.assertNotNull(figure(payload.getFamilies(), "pll").getRecognitionMs());
  }

  @Test
  public void testACaseUnderItsFloorIsNotSent() {
    CoachPayload payload = build();

    Assert.assertNotNull(figure(payload.getCases(), "pll_gb"));
    Assert.assertNull(figure(payload.getCases(), "oll_21")); // seen four times
  }

  /** A slot is named relative to the cross face, so the family is worth sending and the slots are not. */
  @Test
  public void testF2lSlotsAreCutAndTheFamilySurvives() {
    CoachPayload payload = build();

    Assert.assertNotNull(figure(payload.getParts(), "pair"));
    for (StepFigure stepCase : payload.getCases()) {
      Assert.assertFalse(stepCase.getCode(), stepCase.getCode().startsWith("pair_"));
    }
  }

  @Test
  public void testACaseCarriesTheMeanItsCostWasWorkedOutAgainst() {
    CoachPayload payload = build();
    StepFigure worst = figure(payload.getCases(), "pll_gb");

    Assert.assertNotNull(worst.getFamilyMeanMs());
    Assert.assertEquals(worst.getTimeLostMs().longValue(),
        (worst.getMeanMs() - worst.getFamilyMeanMs().longValue()) * worst.getCount());
  }

  @Test
  public void testCasesComeOutWorstCostFirst() {
    CoachPayload payload = build();

    Assert.assertEquals("pll_gb", payload.getCases().get(0).getCode());
    long worst = payload.getCases().get(0).getTimeLostMs().longValue();
    Assert.assertTrue(worst > payload.getCases().get(1).getTimeLostMs().longValue());
  }

  @Test
  public void testASolveDrillGapIsSentOnlyWhenItClearsTheNoise() {
    CoachPayload payload = build();

    Assert.assertNotNull(comparison(payload, "pll_gb")); // 2.9s solved against 1.7s drilled
    Assert.assertNull(comparison(payload, "pll_t"));     // the same either way
  }

  @Test
  public void testACaseDrilledButNotSolvedEnoughIsNotCompared() {
    CoachPayload payload = build();

    Assert.assertNotNull(figure(payload.getDrillCases(), "oll_21"));
    Assert.assertNull(comparison(payload, "oll_21"));
  }

  @Test
  public void testNothingIsSaidAboutTheSolveUnderItsFloor() {
    CoachPayload payload = CoachPayloadBuilder.build("3x3", "cfop", solveTimes(4),
        familySamples(4), Collections.<StepSample>emptyList(), 4,
        Collections.<StepSample>emptyList(), 0);

    Assert.assertNull(payload.getSolve());
    Assert.assertTrue(payload.getFamilies().isEmpty()); // four solves is under the family floor too
  }

  @Test
  public void testWindowsCarryWhatWasActuallyRead() {
    CoachPayload payload = build();

    Assert.assertEquals(20, payload.getFamilyWindow());
    Assert.assertEquals(40, payload.getCaseWindow());
    Assert.assertEquals(3, payload.getDrillWindow());
  }

  @Test
  public void testAPayloadSurvivesARoundTrip() {
    CoachPayload payload = build();
    CoachPayload read = CoachPayload.parse(payload.toJson());

    Assert.assertEquals(CoachPayload.VERSION, read.getSchemaVersion());
    Assert.assertEquals("3x3", read.getPuzzle());
    Assert.assertEquals("cfop", read.getMethod());
    Assert.assertEquals(payload.getSolve().getMeanMs(), read.getSolve().getMeanMs());
    Assert.assertEquals(payload.getFamilies().size(), read.getFamilies().size());
    Assert.assertEquals(payload.getCases().size(), read.getCases().size());
    Assert.assertNull(figure(read.getFamilies(), "cross").getRecognitionMs());
    Assert.assertEquals(payload.getComparisons().get(0).getGapMs(),
        read.getComparisons().get(0).getGapMs());
  }

  @Test
  public void testAPayloadFromANewerVersionIsRefused() {
    String newer = build().toJson().replace("\"schema_version\":" + CoachPayload.VERSION,
        "\"schema_version\":" + (CoachPayload.VERSION + 1));
    try {
      CoachPayload.parse(newer);
      Assert.fail("A payload written by a newer version should be refused");
    } catch (IllegalArgumentException e) {
      Assert.assertTrue(e.getMessage(), e.getMessage().contains("version"));
    }
  }

  /** Twenty solves of steps, forty of cases, three drills of the same two PLLs. */
  private static CoachPayload build() {
    List<StepSample> cases = new ArrayList<StepSample>();
    cases.addAll(steps("pll_gb", 6, 2900, 1000));
    cases.addAll(steps("pll_t", 8, 1500, 700));
    cases.addAll(steps("oll_21", 4, 2000, 900));
    cases.addAll(steps("oll_33", 7, 1600, 700));

    List<StepSample> drills = new ArrayList<StepSample>();
    drills.addAll(steps("pll_gb", 8, 1700, 600));
    drills.addAll(steps("pll_t", 7, 1480, 690));
    drills.addAll(steps("oll_21", 6, 1900, 800));

    return CoachPayloadBuilder.build("3x3", "cfop", solveTimes(20), familySamples(20), cases, 40,
        drills, 3);
  }

  private static List<Long> solveTimes(int solves) {
    List<Long> times = new ArrayList<Long>();
    for (int i = 0; i < solves; i++) {
      times.add(Long.valueOf(20000 + i * 100));
    }
    return times;
  }

  /** One solve's worth of steps, with the F2L built out of slots the way the breakdown stores it. */
  private static List<StepSample> familySamples(int solves) {
    List<StepSample> samples = new ArrayList<StepSample>();
    samples.addAll(steps("cross", solves, 6500, 0));
    samples.addAll(steps("f2l", solves, 15000, 5000));
    samples.addAll(steps("oll", solves, 5400, 2400));
    samples.addAll(steps("pll", solves, 7300, 3800));
    for (String slot : new String[] { "pair_rf", "pair_fl", "pair_lb", "pair_br" }) {
      for (StepSample sample : steps(slot, solves, 3700, 1200)) {
        samples.add(new StepSample(sample.getCode(), sample.getTimeMs(), sample.getRecognitionMs(),
            true));
      }
    }
    return samples;
  }

  /** Occurrences around a mean, spread a little so there is a spread to judge an outlier against. */
  private static List<StepSample> steps(String code, int count, long meanMs, long recognitionMs) {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (int i = 0; i < count; i++) {
      long offset = (i % 2 == 0 ? 1 : -1) * (i + 1) * 20L;
      samples.add(new StepSample(code, meanMs + offset, recognitionMs, false));
    }
    return samples;
  }

  private static StepFigure figure(List<StepFigure> figures, String code) {
    for (StepFigure figure : figures) {
      if (figure.getCode().equals(code)) {
        return figure;
      }
    }
    return null;
  }

  private static CaseComparison comparison(CoachPayload payload, String code) {
    for (CaseComparison comparison : payload.getComparisons()) {
      if (comparison.getCode().equals(code)) {
        return comparison;
      }
    }
    return null;
  }
}
