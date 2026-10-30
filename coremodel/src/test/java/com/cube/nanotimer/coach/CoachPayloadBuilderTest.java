package com.cube.nanotimer.coach;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.json.JSONObject;

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

  /**
   * Why the floor cannot simply be lowered to buy a learner a case layer, which is what the figures
   * look like they are asking for. Under {@link StepTallies#MIN_SAMPLES_TO_FILTER} a code's spread
   * cannot be measured, so nothing is thrown out: a lower floor sends means the outlier rule never
   * inspected, and one knocked cube in three occurrences is a third of the mean.
   */
  @Test
  public void testTheCaseFloorCannotGoUnderWhatTheOutlierRuleNeeds() {
    Assert.assertTrue(CoachPayloadBuilder.CASE_FLOOR + " < " + StepTallies.MIN_SAMPLES_TO_FILTER,
        CoachPayloadBuilder.CASE_FLOOR >= StepTallies.MIN_SAMPLES_TO_FILTER);
  }

  /** A history at the plan floor has to be one the step floors quote, or the plan says nothing. */
  @Test
  public void testThePlanFloorIsNeverUnderTheStepFloors() {
    Assert.assertTrue(CoachPayloadBuilder.PLAN_FLOOR >= CoachPayloadBuilder.SOLVE_FLOOR);
    Assert.assertTrue(CoachPayloadBuilder.PLAN_FLOOR >= CoachPayloadBuilder.FAMILY_FLOOR);
  }

  @Test
  public void testACaseUnderItsFloorIsNotSent() {
    CoachPayload payload = build();

    Assert.assertNotNull(figure(payload.getCases(), "pll_gb"));
    Assert.assertNull(figure(payload.getCases(), "oll_21")); // seen four times
  }

  @Test
  public void testRecentMeanReadsOnlyTheLatestOccurrences() {
    List<StepSample> cases = new ArrayList<StepSample>();
    cases.addAll(steps("pll_t", CoachPayloadBuilder.RECENT_CASE_OCCURRENCES, 1500, 700));
    cases.addAll(steps("pll_t", 20, 3000, 700)); // older and slower
    cases.addAll(steps("pll_gb", 6, 2900, 1000));
    CoachPayload payload = CoachPayload.parse(CoachPayloadBuilder.build("3x3", "cfop",
        solveTimes(20), familySamples(20), cases, 40, Collections.<StepSample>emptyList(), 0,
        knowledge()).toJson());

    StepFigure trained = figure(payload.getCases(), "pll_t");
    Assert.assertEquals(1500, trained.getRecentMeanMs().longValue(), 50);
    Assert.assertTrue(trained.getMeanMs() > 2400);
    Assert.assertEquals(figure(payload.getCases(), "pll_gb").getMeanMs(),
        figure(payload.getCases(), "pll_gb").getRecentMeanMs().longValue());
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
  public void testTheLanguageSurvivesARoundTrip() {
    CoachPayload payload = build().withLanguage("pt-BR");
    CoachPayload read = CoachPayload.parse(payload.toJson());

    Assert.assertEquals(CoachGlossary.Language.PT, read.getLanguage());
    Assert.assertTrue(payload.toJson().contains("\"language\":\"pt\""));
    Assert.assertEquals(payload.getSolve().getMeanMs(), read.getSolve().getMeanMs());
    Assert.assertEquals(payload.getPllLooks().size(), read.getPllLooks().size());
  }

  /** withLanguage copies every field by hand, so a field it forgets would vanish from requests. */
  @Test
  public void testSettingTheLanguageChangesNothingElse() throws Exception {
    for (CoachPayload payload : Arrays.asList(build(), buildWithPlacedPairs(),
        buildWithPairMoves(12, 400), buildWithLastLayerMoves(12), buildWithTurnings(20))) {
      JSONObject before = new JSONObject(payload.toJson());
      JSONObject after = new JSONObject(payload.withLanguage("fr").toJson());
      after.remove("language");

      Assert.assertTrue(before.toString(), before.similar(after));
    }
  }

  /** A payload from before version 10, or one with no language set, is planned in English. */
  @Test
  public void testNoLanguageIsEnglish() {
    CoachPayload payload = build();

    Assert.assertFalse(payload.toJson().contains("\"language\""));
    Assert.assertEquals(CoachGlossary.Language.EN, CoachPayload.parse(payload.toJson()).getLanguage());
    Assert.assertEquals(CoachGlossary.Language.EN, payload.withLanguage("de").getLanguage());
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
  public void testF2lGroupsRideInThePayloadAndSurviveARoundTrip() {
    CoachPayload payload = CoachPayload.parse(buildWithPlacedPairs().toJson());

    Assert.assertEquals(4, payload.getF2lGroups().size());
    Assert.assertEquals(0.25, payload.value("f2l_groups.f2l_stuck.share"), 1e-9);
    Assert.assertEquals(5000, payload.value("f2l_groups.f2l_stuck.mean_ms"), 50);
    Assert.assertEquals(3000, payload.value("f2l_groups.f2l_basic.mean_ms"), 50);
    Assert.assertEquals(2000, payload.value("f2l_groups.f2l_first.recognition_ms"), 1e-9);
    Assert.assertEquals(600, payload.value("f2l_groups.f2l_later.recognition_ms"), 1e-9);
    Assert.assertNull(payload.value("f2l_groups.f2l_basic.share"));
    Assert.assertTrue(payload.holds("f2l_stuck"));
    Assert.assertTrue(payload.holds("f2l_first"));
  }

  /**
   * An F2L case is sent as a code in one place only, its moves, where a card can name it without its
   * number (owner, 2026-09-19); the groups and the timings carry none.
   */
  @Test
  public void testAnF2lCaseIsSentOnlyWithItsMoves() {
    String json = buildWithPairMoves(12).toJson();
    Assert.assertTrue(json, json.contains("\"pair_23\""));

    String rest = json.replaceAll(",?\"f2l_habits\":\\[[^\\]]*\\],?", "");
    Assert.assertFalse(rest, rest.matches(".*\"pair_a?\\d.*"));
  }

  @Test
  public void testF2lMovesRideInThePayloadAndSurviveARoundTrip() {
    CoachPayload payload = CoachPayload.parse(buildWithPairMoves(12).toJson());

    Assert.assertEquals(CoachPayload.VERSION, payload.getSchemaVersion());
    Assert.assertEquals(12, payload.value("f2l_moves.count"), 1e-9);
    Assert.assertEquals(19, payload.value("f2l_moves.turned_moves"), 1e-9);
    Assert.assertEquals(18, payload.value("f2l_moves.usual_moves"), 1e-9);
    Assert.assertEquals(1, payload.value("f2l_moves.extra_moves"), 1e-9);
    Assert.assertNull(payload.value("f2l_moves.off_list_rate"));
    Assert.assertEquals(0, payload.value("f2l_habits.pair_23.extra_moves"), 1e-9);
    Assert.assertEquals(0, payload.value("f2l_habits.pair_23.off_list_rate"), 1e-9);
    Assert.assertEquals(1, payload.value("f2l_habits.pair_4.extra_moves"), 1e-9);
    Assert.assertEquals(0.25, payload.value("f2l_habits.pair_4.off_list_rate"), 1e-9);
    Assert.assertTrue(payload.holds("pair_23"));
    Assert.assertFalse(payload.holds("pair_9"));
  }

  @Test
  public void testLastLayerMovesRideInThePayloadAndSurviveARoundTrip() {
    CoachPayload payload = CoachPayload.parse(buildWithLastLayerMoves(12).toJson());

    Assert.assertEquals(12, payload.value("ll_moves.pll.count"), 1e-9);
    Assert.assertEquals(16, payload.value("ll_moves.pll.turned_moves"), 1e-9);
    Assert.assertEquals(14, payload.value("ll_moves.pll.usual_moves"), 1e-9);
    Assert.assertEquals(2, payload.value("ll_moves.pll.extra_moves"), 1e-9);
    Assert.assertEquals(0, payload.value("ll_moves.oll.extra_moves"), 1e-9);
    Assert.assertEquals(2, payload.value("ll_habits.pll_t.extra_moves"), 1e-9);
    Assert.assertEquals(0.5, payload.value("ll_habits.pll_t.off_list_rate"), 1e-9);
    Assert.assertEquals(12, payload.value("windows.ll_moves"), 1e-9);
    Assert.assertTrue(payload.holds("pll_t"));
  }

  /** A last layer case goes to its own list and never into the F2L's, which it would skew. */
  @Test
  public void testALastLayerCaseIsNotAnF2lHabit() {
    CoachPayload payload = buildWithLastLayerMoves(12);

    Assert.assertEquals(2, payload.value("f2l_moves.extra_moves"), 1e-9);
    Assert.assertNull(payload.value("f2l_habits.pll_t.extra_moves"));
    Assert.assertNull(payload.value("ll_habits.pair_4.extra_moves"));
  }

  /** Nothing of the last layer replayed: no step, no case and no window, not zeros. */
  @Test
  public void testAPayloadWithoutLastLayerMovesSaysNothingAboutThem() {
    CoachPayload payload = CoachPayload.parse(buildWithPairMoves(12).toJson());

    Assert.assertTrue(payload.getLlMoves().isEmpty());
    Assert.assertNull(payload.value("ll_moves.pll.extra_moves"));
    Assert.assertNull(payload.value("windows.ll_moves"));
    Assert.assertFalse(payload.toJson(), payload.toJson().contains("\"ll_"));
  }

  /**
   * The F2L figures are replayed from a shorter window than the cases are read from, so how far
   * back they go rides with them rather than being inferred from the case window.
   */
  @Test
  public void testTheF2lMoveWindowRidesBesideTheFigures() {
    CoachPayload payload = CoachPayload.parse(buildWithPairMoves(12, 400).toJson());

    Assert.assertEquals(12, payload.getF2lMoveWindow().intValue());
    Assert.assertEquals(12, payload.value("windows.f2l_moves"), 1e-9);
    Assert.assertEquals(400, payload.value("windows.cases"), 1e-9);
  }

  /** Nothing replayed, nothing to say how far back: the window goes when the figures do. */
  @Test
  public void testAPayloadWithoutF2lMovesCarriesNoF2lWindow() {
    CoachPayload payload = CoachPayload.parse(buildWithPlacedPairs().toJson());

    Assert.assertNull(payload.getF2lMoveWindow());
    Assert.assertNull(payload.value("windows.f2l_moves"));
  }

  /** Moves are charged to the pair that finishes next, so a case never goes without the total. */
  @Test
  public void testNoF2lCaseIsSentWithoutTheWholeF2l() {
    CoachPayload payload = buildWithPairMoves(CoachPayloadBuilder.FAMILY_FLOOR - 1);

    Assert.assertNull(payload.getF2lMoves());
    Assert.assertTrue(payload.getF2lHabits().isEmpty());
    Assert.assertFalse(payload.toJson(), payload.toJson().contains("f2l_habits"));
    Assert.assertFalse(payload.holds("pair_23"));
  }

  /** A payload with nothing replayed, or from before version 5, has no F2L moves: not zero. */
  @Test
  public void testAPayloadWithoutF2lMovesSaysNothingAboutThem() {
    String json = buildWithPlacedPairs().toJson();
    Assert.assertFalse(json, json.contains("f2l_moves"));
    CoachPayload payload = CoachPayload.parse(json
        .replace("\"schema_version\":" + CoachPayload.VERSION, "\"schema_version\":4"));

    Assert.assertEquals(4, payload.getSchemaVersion());
    Assert.assertNull(payload.getF2lMoves());
    Assert.assertNull(payload.value("f2l_moves.extra_moves"));
    Assert.assertNull(payload.value("f2l_habits.pair_23.extra_moves"));
  }

  /** A payload from before the groups existed has none, and none is not measured, not zero. */
  @Test
  public void testAPayloadWrittenBeforeTheF2lGroupsHoldsNone() {
    String older = buildWithPlacedPairs().toJson()
        .replace("\"schema_version\":" + CoachPayload.VERSION, "\"schema_version\":3")
        .replaceAll(",\"f2l_groups\":\\[[^\\]]*\\]", "");
    Assert.assertFalse(older, older.contains("f2l_groups"));
    CoachPayload payload = CoachPayload.parse(older);

    Assert.assertEquals(3, payload.getSchemaVersion());
    Assert.assertTrue(payload.getF2lGroups().isEmpty());
    Assert.assertNull(payload.value("f2l_groups.f2l_stuck.share"));
    Assert.assertFalse(payload.holds("f2l_stuck"));
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

  /** Each step in solving order, then the whole solve, each with its moves and stops a solve. */
  @Test
  public void testEachStepsTurningRidesInSolvingOrderThenTheWholeSolve() {
    CoachPayload payload = buildWithTurnings(20);

    List<String> codes = new ArrayList<String>();
    for (TurningFigure figure : payload.getTurning()) {
      codes.add(figure.getCode());
    }
    Assert.assertEquals(Arrays.asList("cross", "f2l", "oll", "pll", CoachPayloadBuilder.SOLVE),
        codes);
    TurningFigure cross = payload.getTurning().get(0);
    Assert.assertEquals(20, cross.getCount());
    Assert.assertEquals(8, cross.getMoves(), 1e-9);
    Assert.assertEquals(1, cross.getStalls(), 1e-9);
    Assert.assertEquals(600, cross.getStallMs());
    Assert.assertEquals(400, cross.getStopMs());
  }

  /** A skipped PLL turned nothing of its own, and counted in it would read as efficiency. */
  @Test
  public void testASkipIsNotCountedAsTurningTheStep() {
    TurningFigure pll = turning(buildWithTurnings(20), "pll");

    Assert.assertEquals(14, pll.getCount()); // 20, less 4 skips and 2 solves that stopped early
    Assert.assertEquals(14, pll.getMoves(), 1e-9);
  }

  /** A solve missing a step would be a step short, so the whole solve reads only whole solves. */
  @Test
  public void testTheWholeSolveIsReadOverTheSolvesReadToTheEnd() {
    TurningFigure solve = turning(buildWithTurnings(20), CoachPayloadBuilder.SOLVE);

    Assert.assertEquals(18, solve.getCount());
    Assert.assertEquals(1060 / 18.0, solve.getMoves(), 1e-9); // 4 skips at 48, 14 at 62
    Assert.assertEquals(68 / 18.0, solve.getStalls(), 1e-9);
    Assert.assertEquals(2489, solve.getStallMs());
  }

  /** An app older than stop_ms sent whole gaps over 400 ms as stall_ms, which is its stop time. */
  @Test
  public void testAnOlderAppsStallTimeIsReadAsItsStopTime() throws Exception {
    TurningFigure figure = TurningFigure.fromJson(new JSONObject(
        "{\"code\":\"cross\",\"count\":20,\"moves\":8,\"stalls\":1,\"stall_ms\":700,"
            + "\"rejection_rate\":0}"));

    Assert.assertEquals(700, figure.getStopMs());
  }

  @Test
  public void testTurningUnderTheFloorIsNotSent() {
    CoachPayload payload = buildWithTurnings(CoachPayloadBuilder.FAMILY_FLOOR - 1);

    Assert.assertTrue(payload.getTurning().isEmpty());
    Assert.assertNull(payload.getTurningWindow());
    Assert.assertFalse(payload.toJson(), payload.toJson().contains("turning"));
  }

  /** What rides is what a plan can cite, with the window it was replayed over beside it. */
  @Test
  public void testTurningSurvivesTheRoundTripAndCanBeCited() {
    CoachPayload payload = CoachPayload.parse(buildWithTurnings(20).toJson());

    Assert.assertEquals(CoachPayload.VERSION, payload.getSchemaVersion());
    Assert.assertEquals(600, payload.value("turning.cross.stall_ms"), 1e-9);
    Assert.assertEquals(400, payload.value("turning.cross.stop_ms"), 1e-9);
    Assert.assertEquals(58.9, payload.value("turning.solve.moves"), 1e-9);
    Assert.assertEquals(3.8, payload.value("turning.solve.stalls"), 1e-9);
    Assert.assertEquals(20, payload.value("windows.turning"), 1e-9);
    Assert.assertNull(payload.value("turning.cross.tps"));
    Assert.assertNull(payload.value("turning.lse.moves"));
  }

  /** A payload from before version 6 has no turning: not zero moves and no stops. */
  @Test
  public void testAPayloadWithoutTurningSaysNothingAboutIt() {
    CoachPayload payload = CoachPayload.parse(buildWithPlacedPairs().toJson()
        .replace("\"schema_version\":" + CoachPayload.VERSION, "\"schema_version\":5"));

    Assert.assertTrue(payload.getTurning().isEmpty());
    Assert.assertNull(payload.value("turning.cross.moves"));
    Assert.assertNull(payload.value("windows.turning"));
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

  /**
   * Thirty solves whose pairs carry their case and their place: the first basic and slow to find,
   * then two more basic and one stuck.
   */
  private static CoachPayload buildWithPlacedPairs() {
    List<StepSample> samples = new ArrayList<StepSample>();
    samples.addAll(steps("cross", 30, 6500, 0));
    samples.addAll(steps("f2l", 30, 15000, 4000));
    for (int solve = 0; solve < 30; solve++) {
      long spread = (solve % 2 == 0 ? 1 : -1) * solve * 5L;
      samples.add(new StepSample("pair_27_rf", 3000 + spread, 2000, true, solve, 0));
      samples.add(new StepSample("pair_4_fl", 3000 + spread, 600, true, solve, 1));
      samples.add(new StepSample("pair_a12_br", 5000 + spread, 600, true, solve, 2));
      samples.add(new StepSample("pair_9_lb", 3000 + spread, 600, true, solve, 3));
    }
    return CoachPayloadBuilder.build("3x3", "cfop", solveTimes(30), samples, samples, 30,
        Collections.<StepSample>emptyList(), 0, Collections.<CaseKnowledge>emptyList());
  }

  /**
   * Solves of two pairs each: case 23 always with a listed algorithm 4 moves over the shortest, which
   * costs nothing, case 4 with its usual one except a quarter of the time, off the list 4 over.
   */
  private static CoachPayload buildWithPairMoves(int solves) {
    return buildWithPairMoves(solves, solves);
  }

  /** As above, with the case layer read from a window of its own, longer than the F2L one. */
  private static CoachPayload buildWithPairMoves(int solves, int caseSolves) {
    List<PairExecution> pairs = new ArrayList<PairExecution>();
    for (int solve = 0; solve < solves; solve++) {
      long spread = (solve % 2 == 0 ? 1 : -1) * solve * 20L;
      boolean odd = solve % 4 == 0;
      pairs.add(PairExecution.of(solve, "pair_23", 3000 + spread, 11, 7, true));
      pairs.add(PairExecution.of(solve, "pair_4", 3000 + spread, odd ? 11 : 7, 7, !odd));
    }
    return CoachPayloadBuilder.build("3x3", "cfop", solveTimes(solves), familySamples(solves),
        familySamples(solves), caseSolves, Collections.<StepSample>emptyList(), 0,
        Collections.<CaseKnowledge>emptyList(), pairs, solves);
  }

  /**
   * An F2L of one case always turned 2 over, an OLL always its listed algorithm, and a PLL T that
   * goes in 4 over every other solve, all in the one list the app hands over.
   */
  private static CoachPayload buildWithLastLayerMoves(int solves) {
    List<PairExecution> executions = new ArrayList<PairExecution>();
    for (int solve = 0; solve < solves; solve++) {
      long spread = (solve % 2 == 0 ? 1 : -1) * solve * 20L;
      boolean odd = solve % 2 == 0;
      executions.add(PairExecution.of(solve, "pair_4", 3000 + spread, 9, 7, false));
      executions.add(PairExecution.of(solve, "oll_27", 1500 + spread, 7, 7, true));
      executions.add(PairExecution.of(solve, "pll_t", 2000 + spread, odd ? 18 : 14, 14, !odd));
    }
    return CoachPayloadBuilder.build("3x3", "cfop", solveTimes(solves), familySamples(solves),
        familySamples(solves), solves, Collections.<StepSample>emptyList(), 0,
        Collections.<CaseKnowledge>emptyList(), executions, solves);
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

  /**
   * Solves turned the same way every time: a cross of 8 moves with one 600 ms stop, an F2L of 30
   * with two, an OLL of 10 with none, a PLL of 14 with one, except that every fifth PLL is a skip
   * and every tenth solve stopped before its PLL was read.
   */
  private static CoachPayload buildWithTurnings(int solves) {
    List<StepTurning> turnings = new ArrayList<StepTurning>();
    for (int solve = 0; solve < solves; solve++) {
      boolean whole = solve % 10 != 9;
      turnings.add(new StepTurning(solve, "cross", 3000, 8, 1, 600, 400, whole));
      turnings.add(new StepTurning(solve, "f2l", 12000, 30, 2, 1500, 1500, whole));
      turnings.add(new StepTurning(solve, "oll_21", 2500, 10, 0, 0, 0, whole));
      if (!whole) {
        continue;
      }
      turnings.add(solve % 5 == 0 ? new StepTurning(solve, "pll_skip", 400, 0, 0, 0, 0, true)
          : new StepTurning(solve, "pll_t", 2000, 14, 1, 500, 500, true));
    }
    return CoachPayloadBuilder.build("3x3", "cfop", solveTimes(solves), familySamples(solves),
        familySamples(solves), solves, Collections.<StepSample>emptyList(), 0,
        Collections.<CaseKnowledge>emptyList(), Collections.<PairExecution>emptyList(), 0,
        turnings, solves);
  }

  private static TurningFigure turning(CoachPayload payload, String code) {
    for (TurningFigure figure : payload.getTurning()) {
      if (figure.getCode().equals(code)) {
        return figure;
      }
    }
    throw new AssertionError("no turning for " + code);
  }
}
