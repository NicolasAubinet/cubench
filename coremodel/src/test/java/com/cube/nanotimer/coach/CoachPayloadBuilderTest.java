package com.cube.nanotimer.coach;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.session.MethodStatistics;

import java.util.ArrayList;
import java.util.Arrays;
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

  /** A part names the algorithm run and not the case dealt, so it is no case to quote: the case it
   * answers is already sent under its own step code, and there is no scramble to deal one from. */
  @Test
  public void testAnAlgorithmIsNotSentAsACase() {
    CoachPayload payload = build();

    Assert.assertNull(figure(payload.getCases(), "ollalg_45"));
    Assert.assertNull(figure(payload.getCases(), "alg_gb"));
    for (StepFigure stepCase : payload.getCases()) {
      Assert.assertTrue(stepCase.getCode(),
          stepCase.getCode().startsWith("oll_") || stepCase.getCode().startsWith("pll_"));
    }
  }

  /** Only the per-case split is cut: what a last layer algorithm costs still rides as a part. */
  @Test
  public void testTheAlgorithmFamilySurvivesTheCut() {
    CoachPayload payload = build();

    Assert.assertNotNull(figure(payload.getParts(), "ollalg"));
    Assert.assertNotNull(figure(payload.getParts(), "alg"));
  }

  /** A case is quoted only where a drill could be dealt one, and no other method's sub-step can. */
  @Test
  public void testAnotherMethodsSubStepIsNotSentAsACase() {
    CoachPayload payload = build();

    Assert.assertNull(figure(payload.getCases(), "lse_eo"));
    Assert.assertNull(figure(payload.getCases(), "corner_dfr"));
  }

  /** A restart solved nothing, so it is no case either: its code is one word and names none. */
  @Test
  public void testARestartIsNotSentAsACase() {
    CoachPayload payload = build();

    Assert.assertNull(MethodStatistics.caseOfPart("pllrestart"));
    Assert.assertNull(MethodStatistics.caseOfPart("ollrestart"));
    for (StepFigure stepCase : payload.getCases()) {
      Assert.assertFalse(stepCase.getCode(), stepCase.getCode().endsWith("restart"));
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
        Collections.<StepSample>emptyList(), 0, knowledge());

    Assert.assertNull(payload.getSolve());
    Assert.assertTrue(payload.getFamilies().isEmpty()); // four solves is under the family floor too
  }

  /** A solve that took two algorithms to orient the layer looked twice; one that took one did not. */
  @Test
  public void testATwoLookIsCountedPerSolveAndNotPerAlgorithm() {
    CoachPayload payload = build();

    Assert.assertEquals(TWO_LOOKS, payload.getTwoLookCount().intValue());
    Assert.assertEquals(TWO_LOOKS,
        CoachPayload.parse(payload.toJson()).getTwoLookCount().intValue());
  }

  /** Solves recorded before an OLL was split by algorithm still say they were taken in two looks. */
  @Test
  public void testASolveInTheOlderTwoLookCodesStillCounts() {
    List<StepSample> samples = new ArrayList<StepSample>(familySamples(20));
    samples.add(new StepSample("edges", 2100, 900, true, 20));
    samples.add(new StepSample("corners", 2400, 800, true, 20));
    CoachPayload payload = CoachPayloadBuilder.build("3x3", "cfop", solveTimes(21), samples,
        Collections.<StepSample>emptyList(), 0, Collections.<StepSample>emptyList(), 0,
        knowledge());

    Assert.assertEquals(TWO_LOOKS + 1, payload.getTwoLookCount().intValue());
  }

  /** The status rides as codes, and only for the sets this payload's own method is solved in. */
  @Test
  public void testTheKnownSetCarriesThisMethodsCasesOnly() {
    CoachPayload payload = build();

    Assert.assertEquals(Arrays.asList("pll_t", "oll_33"), payload.getKnownCases());
    Assert.assertEquals(Collections.singletonList("oll_21"), payload.getLearningCases());
    Assert.assertEquals(payload.getKnownCases(),
        CoachPayload.parse(payload.toJson()).getKnownCases());
  }

  /** A case under the floor has no status, and nothing may read that silence as not knowing it. */
  @Test
  public void testACaseWithNoStatusIsInNeitherList() {
    CoachPayload payload = build();

    Assert.assertNotNull(figure(payload.getCases(), "pll_gb")); // quoted as a figure...
    Assert.assertFalse(payload.getKnownCases().contains("pll_gb")); // ...and said nothing of here
    Assert.assertFalse(payload.getLearningCases().contains("pll_gb"));
  }

  /**
   * A payload stored before the two-look was counted holds no count, and none is not zero: read as
   * zero, a plan kept beside it would come back saying the solver two-looks none of their solves.
   */
  @Test
  public void testAPayloadWrittenBeforeTheTwoLookWasCountedHoldsNoCount() {
    String older = build().toJson()
        .replace("\"schema_version\":" + CoachPayload.VERSION, "\"schema_version\":1")
        .replace("\"two_look_count\":" + TWO_LOOKS + ",", "");
    CoachPayload payload = CoachPayload.parse(older);

    Assert.assertEquals(1, payload.getSchemaVersion());
    Assert.assertNull(payload.getTwoLookCount());
    Assert.assertNull(payload.value("two_look_count")); // so the card that reads it stays quiet
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
    // the algorithms a last layer step took, each well clear of its own family's mean so that
    // leaving them in would rank them above every real case, and two restarts beside them
    cases.addAll(parts("ollalg_45", 6, 4200, 900));
    cases.addAll(parts("ollalg_26", 8, 1500, 500));
    cases.addAll(parts("alg_gb", 6, 4500, 900));
    cases.addAll(parts("alg_t", 9, 1400, 500));
    cases.addAll(parts("pllrestart", 6, 3000, 400));
    cases.addAll(parts("ollrestart", 5, 2600, 300));
    // sub-steps of other methods, which reach this builder unguarded: only the screen refuses them
    cases.addAll(parts("lse_eo", 7, 3800, 800));
    cases.addAll(parts("corner_dfr", 6, 3300, 700));

    List<StepSample> drills = new ArrayList<StepSample>();
    drills.addAll(steps("pll_gb", 8, 1700, 600));
    drills.addAll(steps("pll_t", 7, 1480, 690));
    drills.addAll(steps("oll_21", 6, 1900, 800));

    return CoachPayloadBuilder.build("3x3", "cfop", solveTimes(20), familySamples(20), cases, 40,
        drills, 3, knowledge());
  }

  /** Two cases that go in unaided, one that does not, and one of a method this payload is not for. */
  private static List<CaseKnowledge> knowledge() {
    return Arrays.asList(
        new CaseKnowledge("pll", "t", CaseKnowledge.Status.KNOWN, 8, 5000),
        new CaseKnowledge("oll", "33", CaseKnowledge.Status.KNOWN, 6, 4000),
        new CaseKnowledge("oll", "21", CaseKnowledge.Status.TO_LEARN, 5, 3000),
        new CaseKnowledge("cmll", "sune", CaseKnowledge.Status.KNOWN, 9, 2000));
  }

  private static List<Long> solveTimes(int solves) {
    List<Long> times = new ArrayList<Long>();
    for (int i = 0; i < solves; i++) {
      times.add(Long.valueOf(20000 + i * 100));
    }
    return times;
  }

  /** How many of the window's solves orient the last layer in two algorithms rather than one. */
  private static final int TWO_LOOKS = 6;

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
            true, sample.getSolveId()));
      }
    }
    for (int solve = 0; solve < Math.min(TWO_LOOKS, solves); solve++) {
      samples.add(new StepSample("ollalg_45", 2100, 900, true, solve));
      samples.add(new StepSample("ollalg_26", 2400, 800, true, solve));
    }
    if (solves > TWO_LOOKS) { // one algorithm, which is a solve that read the case in one look
      samples.add(new StepSample("ollalg_27", 1900, 700, true, TWO_LOOKS));
    }
    for (int solve = 0; solve < Math.min(TWO_LOOKS, solves); solve++) { // a PLL that took two
      samples.add(new StepSample("alg_ua", 1800, 700, true, solve));
      samples.add(new StepSample("alg_t", 1600, 600, true, solve));
    }
    return samples;
  }

  /** Occurrences around a mean, spread a little so there is a spread to judge an outlier against. */
  private static List<StepSample> steps(String code, int count, long meanMs, long recognitionMs) {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (int i = 0; i < count; i++) {
      long offset = (i % 2 == 0 ? 1 : -1) * (i + 1) * 20L;
      samples.add(new StepSample(code, meanMs + offset, recognitionMs, false, i));
    }
    return samples;
  }

  /** The same occurrences, flagged as the parts of a step rather than as steps of the method. */
  private static List<StepSample> parts(String code, int count, long meanMs, long recognitionMs) {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (StepSample sample : steps(code, count, meanMs, recognitionMs)) {
      samples.add(new StepSample(sample.getCode(), sample.getTimeMs(), sample.getRecognitionMs(),
          true, sample.getSolveId()));
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
