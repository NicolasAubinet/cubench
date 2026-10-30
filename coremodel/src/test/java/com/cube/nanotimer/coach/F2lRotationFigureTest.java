package com.cube.nanotimer.coach;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class F2lRotationFigureTest {

  @Test
  public void testCountsPairsRotationsAndCases() {
    F2lRotationFigure figure = CoachPayloadBuilder.f2lRotations(history(12));

    Assert.assertEquals(12, figure.getSolves());
    Assert.assertEquals(48, figure.getPairs());
    Assert.assertEquals(36, figure.getRotations()); // pair_27 twice each solve, pair_9 once
    Assert.assertEquals(24, figure.getRotatedPairs());
    Assert.assertEquals(36, figure.getBasicPairs());
    Assert.assertEquals(24, figure.getBasicRotatedPairs());
    Assert.assertEquals(4, figure.getCases().size());
    Assert.assertEquals("pair_27", figure.getCases().get(0).getCode());
    Assert.assertEquals(12, figure.getCases().get(0).getRotated());
    Assert.assertEquals(0, figure.getCases().get(3).getRotated());
  }

  /** Each rotation is set against the plain gaps of its own place in the pair. */
  @Test
  public void testTheExcessComparesGapsInTheSamePlace() {
    F2lRotationFigure figure = CoachPayloadBuilder.f2lRotations(history(12));

    // 24 starts at 1100 against 700 plain, 12 inside at 600 against 200 plain: 400 either way.
    Assert.assertEquals(Long.valueOf(400), figure.getExcessMs());
  }

  @Test
  public void testTooFewGyroSolvesSendNothing() {
    Assert.assertNull(CoachPayloadBuilder.f2lRotations(history(CoachPayloadBuilder.SOLVE_FLOOR - 1)));
    Assert.assertNull(CoachPayloadBuilder.f2lRotations(Collections.<PairRotation>emptyList()));
  }

  @Test
  public void testTooFewRotationsSendNoExcess() {
    List<PairRotation> pairs = new ArrayList<PairRotation>();
    for (int solve = 0; solve < 12; solve++) {
      pairs.add(pair(solve, "pair_4", solve == 0 ? 1 : 0, new PairRotation.Gaps(
          solve == 0 ? 1 : 0, solve == 0 ? 1000 : 0, solve == 0 ? 0 : 1, solve == 0 ? 0 : 700),
          PairRotation.Gaps.NONE));
    }
    F2lRotationFigure figure = CoachPayloadBuilder.f2lRotations(pairs);

    Assert.assertNotNull(figure);
    Assert.assertNull(figure.getExcessMs());
    Assert.assertNull(payload().withF2lRotations(figure).value("f2l_rotations.excess_ms"));
  }

  @Test
  public void testALostLookIsNotAGap() {
    PairRotation.Gaps gaps = PairRotation.Gaps.NONE.with(PairRotation.LONG_GAP_MS + 1, true)
        .with(500, false);

    Assert.assertEquals(0, gaps.getRotated());
    Assert.assertEquals(1, gaps.getPlain());
  }

  @Test
  public void testRotationsSurviveARoundTripAndCanBeCited() {
    CoachPayload payload = CoachPayload.parse(
        payload().withF2lRotations(CoachPayloadBuilder.f2lRotations(history(12))).toJson());

    Assert.assertEquals(12, payload.getF2lRotations().getSolves());
    Assert.assertEquals(24, payload.value("f2l_rotations.rotated_pairs"), 1e-9);
    Assert.assertEquals(36, payload.value("f2l_rotations.basic_pairs"), 1e-9);
    Assert.assertEquals(400, payload.value("f2l_rotations.excess_ms"), 1e-9);
    Assert.assertEquals(12, payload.value("f2l_rotation_cases.pair_27.rotated"), 1e-9);
    Assert.assertEquals(12, payload.value("f2l_rotation_cases.pair_9.count"), 1e-9);
    Assert.assertNull(payload.value("f2l_rotation_cases.pair_4.count"));
    Assert.assertTrue(payload.holds("pair_27"));
  }

  @Test
  public void testAPayloadWithoutRotationsSaysNothingAboutThem() {
    CoachPayload payload = CoachPayload.parse(payload().withF2lRotations(null).toJson());

    Assert.assertNull(payload.getF2lRotations());
    Assert.assertNull(payload.value("f2l_rotations.rotations"));
    Assert.assertFalse(payload().toJson().contains("f2l_rotations"));
    Assert.assertFalse(payload.holds("pair_27"));
  }

  /** withLanguage copies every field by hand, so a field it forgets would vanish from requests. */
  @Test
  public void testSettingTheLanguageKeepsTheRotations() throws Exception {
    CoachPayload payload =
        payload().withF2lRotations(CoachPayloadBuilder.f2lRotations(history(12)));
    JSONObject after = new JSONObject(payload.withLanguage("fr").toJson());
    after.remove("language");

    Assert.assertTrue(new JSONObject(payload.toJson()).similar(after));
  }

  /**
   * Four pairs a solve: pair_27 rotated twice at its start, pair_9 once inside, pair_33 and an
   * advanced pair_a16 never. Plain gaps 700 ms at a start and 200 ms inside.
   */
  private static List<PairRotation> history(int solves) {
    List<PairRotation> pairs = new ArrayList<PairRotation>();
    for (int solve = 0; solve < solves; solve++) {
      pairs.add(pair(solve, "pair_27", 2, new PairRotation.Gaps(2, 2200, 0, 0),
          new PairRotation.Gaps(0, 0, 4, 800)));
      pairs.add(pair(solve, "pair_9", 1, new PairRotation.Gaps(0, 0, 1, 700),
          new PairRotation.Gaps(1, 600, 3, 600)));
      pairs.add(pair(solve, "pair_33", 0, new PairRotation.Gaps(0, 0, 1, 700),
          new PairRotation.Gaps(0, 0, 4, 800)));
      pairs.add(new PairRotation(solve, "pair_a16", false, 0, new PairRotation.Gaps(0, 0, 1, 700),
          new PairRotation.Gaps(0, 0, 4, 800)));
    }
    return pairs;
  }

  private static PairRotation pair(long solve, String caseCode, int rotations,
      PairRotation.Gaps start, PairRotation.Gaps inside) {
    return new PairRotation(solve, caseCode, true, rotations, start, inside);
  }

  private static CoachPayload payload() {
    return CoachPayloadBuilder.build("3x3", "cfop", Collections.<Long>emptyList(),
        Collections.<StepSample>emptyList(), Collections.<StepSample>emptyList(), 0,
        Collections.<StepSample>emptyList(), 0, null);
  }
}
