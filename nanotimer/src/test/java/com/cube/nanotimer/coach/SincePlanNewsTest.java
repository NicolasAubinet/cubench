package com.cube.nanotimer.coach;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.session.SincePlanNews;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class SincePlanNewsTest {

  private static final long WRITTEN_AT = 1000;

  @Test
  public void testAClearMoveOverEnoughSolvesIsNews() {
    Assert.assertEquals(0, SincePlanNews.pick(stuck(20, 1), WRITTEN_AT, null));
  }

  /** Ten solves are enough for the Today card, not for the recap to claim progress. */
  @Test
  public void testTooFewSolvesAreNotNews() {
    Assert.assertEquals(-1, SincePlanNews.pick(stuck(12, 1), WRITTEN_AT, null));
  }

  @Test
  public void testAMoveThatHeldIsNotNews() {
    Assert.assertEquals(-1, SincePlanNews.pick(stuck(30, 2), WRITTEN_AT, null));
  }

  /** Said once, it waits until it clearly beats what was said. */
  @Test
  public void testTheSameFigureIsNotSaidTwice() {
    List<FocusProgress> progress = stuck(20, 1);
    String said = SincePlanNews.said(null, WRITTEN_AT, 0, progress.get(0).getSince());

    Assert.assertEquals(-1, SincePlanNews.pick(stuck(25, 1), WRITTEN_AT, said));
    Assert.assertEquals(-1, SincePlanNews.pick(stuck(25, 1), WRITTEN_AT,
        SincePlanNews.said(null, WRITTEN_AT, 0, 0.28)));
    Assert.assertEquals(0, SincePlanNews.pick(stuck(25, 1), WRITTEN_AT,
        SincePlanNews.said(null, WRITTEN_AT, 0, 0.40))); // 0.25 now clearly beats 0.40
  }

  @Test
  public void testANewPlanStartsAfresh() {
    String said = SincePlanNews.said(null, WRITTEN_AT, 0, 0.25);

    Assert.assertEquals(0, SincePlanNews.pick(stuck(20, 1), WRITTEN_AT + 1, said));
    Assert.assertEquals("2000|0=0.2500", SincePlanNews.said(said, 2000, 0, 0.25));
  }

  @Test
  public void testUnreadableTextIsSaidAfresh() {
    Assert.assertEquals(0, SincePlanNews.pick(stuck(20, 1), WRITTEN_AT, "1000|x=y"));
    Assert.assertEquals(0, SincePlanNews.pick(stuck(20, 1), WRITTEN_AT, "garbage"));
  }

  /** A stuck pieces plan written at two stuck pairs of four, read over solves with {@code stuck}. */
  private static List<FocusProgress> stuck(int solvesSince, int stuck) {
    CoachPlan plan = new CoachPlan(CoachPlan.VERSION, CoachPlan.Source.COACH, null,
        Arrays.asList(new FocusArea(FocusArea.Reason.STUCK_PIECES,
            Collections.<String>emptyList(), Collections.<Evidence>emptyList(), null, null)));
    List<StepSample> written = pairs(40, 2);
    CoachPayload payload = CoachPayloadBuilder.build("3x3", "cfop", times(40), written, written,
        40, Collections.<StepSample>emptyList(), 0, Collections.<CaseKnowledge>emptyList());
    StoredCoachPlan stored = new StoredCoachPlan(plan, payload, WRITTEN_AT);
    PreviousPlan sincePlan = CoachPayloadBuilder.sincePlan(stored, "cfop", solvesSince,
        times(solvesSince), pairs(solvesSince, stuck), Collections.<StepTurning>emptyList(),
        null);
    return FocusProgress.of(stored, sincePlan);
  }

  private static List<StepSample> pairs(int solves, int stuck) {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (int solve = 0; solve < solves; solve++) {
      samples.add(new StepSample("f2l", 12000, 4000, false, solve));
      for (int pair = 0; pair < 4; pair++) {
        samples.add(new StepSample(pair < stuck ? "pair_a3" : "pair_5", 3000, 1000, true, solve,
            Integer.valueOf(pair)));
      }
    }
    return samples;
  }

  private static List<Long> times(int count) {
    List<Long> times = new ArrayList<Long>();
    for (int i = 0; i < count; i++) {
      times.add(Long.valueOf(20000));
    }
    return times;
  }
}
