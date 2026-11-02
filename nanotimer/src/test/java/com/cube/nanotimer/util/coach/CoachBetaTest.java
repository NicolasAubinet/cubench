package com.cube.nanotimer.util.coach;

import com.cube.nanotimer.coach.CoachPlan;
import com.cube.nanotimer.coach.Evidence;
import com.cube.nanotimer.coach.FocusArea;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** The beta's answers, its rating hold, and the feedback a member sends. */
public class CoachBetaTest {

  private static final long NOW = 1_000_000_000_000L;

  @Test
  public void aStatusSaysThePhaseTheMembershipAndTheSeats() {
    CoachBetaReply reply = CoachBetaReply.parse(200,
        "{\"phase\":\"open\",\"member\":false,\"seats_left\":37}");

    Assert.assertEquals(CoachBetaReply.Kind.STATUS, reply.getKind());
    Assert.assertEquals(CoachBeta.Phase.OPEN, reply.getPhase());
    Assert.assertFalse(reply.isMember());
    Assert.assertEquals(37, reply.getSeatsLeft());
  }

  @Test
  public void aFullBetaIsARefusal() {
    CoachBetaReply reply = CoachBetaReply.parse(409,
        "{\"error\":{\"code\":\"beta_full\",\"message\":\"All 100 seats are taken\"}}");

    Assert.assertEquals(CoachBetaReply.Kind.REFUSED, reply.getKind());
    Assert.assertEquals(CoachRefusal.BETA_FULL, reply.getRefusal());
  }

  /** A phase a later service adds is no status at all: the last one this app read stands. */
  @Test
  public void anUnknownPhaseIsUnreadable() {
    for (String body : new String[] {"{\"phase\":\"paused\",\"member\":true}", "{}",
        "{\"phase\":\"open\"}", "Not found"}) {
      Assert.assertEquals(body, CoachBetaReply.Kind.UNREADABLE,
          CoachBetaReply.parse(200, body).getKind());
    }
  }

  @Test
  public void aStatusIsAskedAgainAfterSixHours() {
    Assert.assertTrue(CoachBeta.stale(0, NOW));
    Assert.assertFalse(CoachBeta.stale(NOW, NOW + CoachBeta.STALE_MS - 1));
    Assert.assertTrue(CoachBeta.stale(NOW, NOW + CoachBeta.STALE_MS));
    Assert.assertTrue(CoachBeta.stale(NOW + 1, NOW));
  }

  @Test
  public void anEndedBetaLetsNobodyIn() {
    Assert.assertTrue(CoachBeta.running(CoachBeta.Phase.OPEN));
    Assert.assertTrue(CoachBeta.running(CoachBeta.Phase.CLOSED));
    Assert.assertFalse(CoachBeta.running(CoachBeta.Phase.ENDED));
    Assert.assertFalse(CoachBeta.running(null));
  }

  @Test
  public void theLastPlanOwesItsStarsUntilRated() {
    Assert.assertTrue(CoachBeta.owes(true, "p2", "p1", false));
    Assert.assertTrue(CoachBeta.owes(true, "p2", null, false));
    Assert.assertFalse(CoachBeta.owes(true, "p2", "p2", false));
  }

  /** A rating that never reached the service is owed again when the service says so. */
  @Test
  public void theServiceIsBelievedOverTheAppsRecord() {
    Assert.assertTrue(CoachBeta.owes(true, "p2", "p2", true));
  }

  @Test
  public void nothingIsOwedOutsideTheBetaOrWithoutAnId() {
    Assert.assertFalse(CoachBeta.owes(false, "p2", "p1", true));
    Assert.assertFalse(CoachBeta.owes(true, null, null, true));
  }

  @Test
  public void aMemberDoesNotSendAPurchaseTheCoachRefused() {
    Assert.assertFalse(CoachClient.sendsPurchase(true, true));
    Assert.assertTrue(CoachClient.sendsPurchase(true, false));
    Assert.assertTrue(CoachClient.sendsPurchase(false, true));
  }

  @Test
  public void aPlanCarriesTheServicesId() {
    CoachReply reply = CoachReply.parse(200, "{\"status\":\"ready\",\"plan_id\":\"abc\","
        + "\"plan\":{\"plan_version\":1,\"source\":\"heuristic\",\"headline\":\"h\","
        + "\"focus\":[]}}");

    Assert.assertEquals(CoachReply.Kind.PLAN, reply.getKind());
    Assert.assertEquals("abc", reply.getPlanId());
  }

  @Test
  public void theHoldIsARefusalOfItsOwn() {
    CoachReply reply = CoachReply.parse(409,
        "{\"error\":{\"code\":\"feedback_owed\",\"message\":\"Plan x has no rating yet\"}}");

    Assert.assertEquals(CoachRefusal.FEEDBACK_OWED, reply.getRefusal());
    Assert.assertTrue(CoachPlanJob.waitAfter(reply) > 0);
  }

  @Test(expected = IllegalStateException.class)
  public void noBodyIsWrittenWithoutStars() {
    new CoachPlanFeedback().toBody(plan());
  }

  @Test
  public void theBodyNamesEachFocusAndCarriesTheSignals() throws Exception {
    CoachPlanFeedback feedback = new CoachPlanFeedback();
    feedback.opened(NOW);
    feedback.seen(0);
    feedback.answer(1, CoachPlanFeedback.Answer.NOT_QUITE);
    feedback.toggleReason(1, CoachPlanFeedback.Reason.ALREADY_DO);
    feedback.setFocusText(1, "  I drill these already  ");
    feedback.setDrillReps(0, 12);
    feedback.setLastWeek(CoachPlanFeedback.LastWeek.A_BIT);
    feedback.rate(4, NOW + 90_000);

    JSONObject body = new JSONObject(feedback.toBody(plan()));

    Assert.assertEquals(4, body.getInt("stars"));
    Assert.assertEquals("a_bit", body.getString("last_week"));
    JSONObject focus = body.getJSONArray("focuses").getJSONObject(0);
    Assert.assertEquals(1, focus.getInt("index"));
    Assert.assertEquals("not_quite", focus.getString("answer"));
    Assert.assertEquals("already_do", focus.getJSONArray("reasons").getString(0));
    Assert.assertEquals("I drill these already", focus.getString("text"));
    Assert.assertEquals("pll_t", focus.getJSONArray("codes").getString(0));
    JSONObject signals = body.getJSONObject("signals");
    Assert.assertEquals(90_000, signals.getLong("rated_after_ms"));
    Assert.assertEquals(2, signals.getInt("focus_count"));
    Assert.assertEquals(12, signals.getJSONObject("drill_reps").getInt("0"));
    Assert.assertFalse("a date never leaves the device", body.has("first_opened_at"));
  }

  /** The time to a rating is the first one's: correcting the stars later does not move it. */
  @Test
  public void aSecondRatingKeepsTheFirstTiming() throws Exception {
    CoachPlanFeedback feedback = new CoachPlanFeedback();
    feedback.opened(NOW);
    feedback.rate(2, NOW + 1_000);
    feedback.rate(5, NOW + 500_000);

    JSONObject signals = new JSONObject(feedback.toBody(plan())).getJSONObject("signals");

    Assert.assertEquals(1_000, signals.getLong("rated_after_ms"));
  }

  @Test
  public void aYesDropsTheReasonsANotQuiteHad() {
    CoachPlanFeedback feedback = new CoachPlanFeedback();
    feedback.answer(0, CoachPlanFeedback.Answer.NOT_QUITE);
    feedback.toggleReason(0, CoachPlanFeedback.Reason.NOT_CLEAR);
    feedback.setFocusText(0, "why");
    feedback.answer(0, CoachPlanFeedback.Answer.YES);

    Assert.assertTrue(feedback.getFocus(0).getReasons().isEmpty());
    Assert.assertNull(feedback.getFocus(0).getText());
  }

  @Test
  public void aDraftReadsBackAsItWasKept() {
    CoachPlanFeedback feedback = new CoachPlanFeedback();
    feedback.opened(NOW);
    feedback.seen(1);
    feedback.answer(1, CoachPlanFeedback.Answer.NOT_QUITE);
    feedback.toggleReason(1, CoachPlanFeedback.Reason.NOT_WORTH);
    feedback.setText("more please");
    feedback.setDrillReps(1, 3);
    feedback.rate(3, NOW + 5_000);

    CoachPlanFeedback read = CoachPlanFeedback.fromDraft(feedback.toDraft());

    Assert.assertEquals(feedback.toDraft(), read.toDraft());
    Assert.assertEquals(feedback.toBody(plan()), read.toBody(plan()));
  }

  @Test
  public void anUnreadableDraftIsAFreshOne() {
    Assert.assertFalse(CoachPlanFeedback.fromDraft("not json").isRated());
    Assert.assertFalse(CoachPlanFeedback.fromDraft(null).isRated());
  }

  @Test
  public void aLongTextIsCut() {
    CoachPlanFeedback feedback = new CoachPlanFeedback();
    char[] text = new char[CoachPlanFeedback.MAX_TEXT + 50];
    Arrays.fill(text, 'a');
    feedback.setText(new String(text));

    Assert.assertEquals(CoachPlanFeedback.MAX_TEXT, feedback.getText().length());
  }

  private static CoachPlan plan() {
    FocusArea first = new FocusArea("slow_case", Collections.singletonList("oll_21"),
        Collections.<Evidence>emptyList(), "first", null);
    FocusArea second = new FocusArea("slow_case", Collections.singletonList("pll_t"),
        Collections.<Evidence>emptyList(), "second", null);
    return new CoachPlan(1, CoachPlan.Source.HEURISTIC, "headline", Arrays.asList(first, second));
  }
}
