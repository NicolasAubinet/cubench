package com.cube.nanotimer.util.coach;

import com.cube.nanotimer.R;
import com.cube.nanotimer.util.coach.CoachBeta.FeedbackMode;
import com.cube.nanotimer.util.coach.CoachBeta.Phase;
import com.cube.nanotimer.util.coach.CoachBetaLook.Drawer;
import com.cube.nanotimer.util.coach.CoachBetaLook.Sheet;

import org.junit.Assert;
import org.junit.Test;

/**
 * Each beta screen against its paid twin, for every phase and mode: the beta version only while
 * the service says the beta runs, and the paid one back on its own once it says it has ended.
 */
public class CoachBetaLookTest {

  private static CoachBetaLook look(Phase phase, boolean member, FeedbackMode mode) {
    return new CoachBetaLook(phase, member, mode, 40, false, false);
  }

  /** The whole switch in one sweep: nothing beta survives an ended or never-heard beta. */
  @Test
  public void anEndedOrUnknownBetaDrawsEveryPaidScreen() {
    for (Phase phase : new Phase[] {Phase.ENDED, null}) {
      for (boolean member : new boolean[] {false, true}) {
        CoachBetaLook look = look(phase, member, FeedbackMode.HIDDEN);
        String at = phase + (member ? " member" : " newcomer");
        Assert.assertEquals(at, Sheet.PAID, look.sheet());
        Assert.assertFalse(at, look.pitchIsBeta());
        Assert.assertEquals(at, R.string.analysis_plan_point_support, look.pitchPointText());
        Assert.assertEquals(at, R.drawable.ic_coach_point_support, look.pitchPointIcon());
        Assert.assertEquals(at, R.string.analysis_plan_private, look.pitchPrivateText());
        Assert.assertEquals(at, Drawer.PREMIUM, look.drawer());
        Assert.assertFalse(at, look.newsLine());
        Assert.assertFalse(at, look.asksFeedback());
        Assert.assertFalse(at, look.holds());
        Assert.assertFalse(at, look.planTag());
      }
    }
  }

  @Test
  public void anOpenBetaOffersANewcomerToJoinForFree() {
    CoachBetaLook look = look(Phase.OPEN, false, FeedbackMode.HIDDEN);

    Assert.assertEquals(Sheet.JOIN, look.sheet());
    Assert.assertTrue(look.pitchIsBeta());
    Assert.assertEquals(R.string.coach_beta_point_ratings, look.pitchPointText());
    Assert.assertEquals(R.drawable.ic_coach_point_star, look.pitchPointIcon());
    Assert.assertEquals(R.string.coach_beta_private, look.pitchPrivateText());
    Assert.assertEquals(Drawer.BETA_FREE, look.drawer());
    Assert.assertTrue(look.newsLine());
  }

  /** No purchase while the coach is in beta: a full or closed beta only says so. */
  @Test
  public void aFullOrClosedBetaOffersNothingToANewcomer() {
    Assert.assertEquals(Sheet.FULL,
        new CoachBetaLook(Phase.OPEN, false, FeedbackMode.HIDDEN, 0, false, false).sheet());
    Assert.assertEquals(Sheet.FULL, look(Phase.CLOSED, false, FeedbackMode.HIDDEN).sheet());
    Assert.assertTrue(look(Phase.CLOSED, false, FeedbackMode.HIDDEN).pitchIsBeta());
  }

  @Test
  public void aMemberIsAskedEverythingHeldAndTaggedWithoutTheFreeLine() {
    CoachBetaLook look = look(Phase.OPEN, true, FeedbackMode.REQUIRED);

    Assert.assertEquals(Sheet.PAID, look.sheet());
    Assert.assertEquals(Drawer.BETA, look.drawer());
    Assert.assertTrue(look.asksFeedback());
    Assert.assertTrue(look.holds());
    Assert.assertEquals(R.string.coach_fb_stars_held, look.starsSubtitle());
    Assert.assertTrue(look.planTag());
  }

  /** The owner's own phone and family: the same questions, nothing required, nothing held. */
  @Test
  public void optionalModeAsksWithoutHolding() {
    CoachBetaLook look = new CoachBetaLook(Phase.OPEN, false, FeedbackMode.OPTIONAL, 40, true,
        false);

    Assert.assertTrue(look.asksFeedback());
    Assert.assertFalse(look.holds());
    Assert.assertEquals(R.string.coach_fb_stars_free, look.starsSubtitle());
    Assert.assertEquals(Sheet.PAID, look.sheet());
    Assert.assertEquals(Drawer.BETA, look.drawer());
  }

  /** A grant keeps its mode after the beta: the questions stay, the Beta tag goes. */
  @Test
  public void aGrantAskedAfterTheBetaLosesOnlyTheTag() {
    CoachBetaLook look = new CoachBetaLook(Phase.ENDED, false, FeedbackMode.REQUIRED, 0, true,
        false);

    Assert.assertTrue(look.asksFeedback());
    Assert.assertTrue(look.holds());
    Assert.assertFalse(look.planTag());
    Assert.assertEquals(Drawer.PREMIUM, look.drawer());
  }

  @Test
  public void theThanksIsForMembersOnceTheBetaHasEndedAndOnlyOnce() {
    Assert.assertTrue(look(Phase.ENDED, true, FeedbackMode.HIDDEN).endCard());
    Assert.assertFalse(look(Phase.OPEN, true, FeedbackMode.REQUIRED).endCard());
    Assert.assertFalse(look(Phase.ENDED, false, FeedbackMode.HIDDEN).endCard());
    Assert.assertFalse(
        new CoachBetaLook(Phase.ENDED, true, FeedbackMode.HIDDEN, 0, false, true).endCard());
    Assert.assertFalse("a member who has since subscribed",
        new CoachBetaLook(Phase.ENDED, true, FeedbackMode.HIDDEN, 0, true, false).endCard());
  }

  @Test
  public void aStatusCarriesTheModeAndAnOlderServiceImpliesIt() {
    CoachBetaReply reply = CoachBetaReply.parse(200,
        "{\"phase\":\"open\",\"member\":false,\"seats_left\":3,\"feedback_mode\":\"optional\"}");
    CoachBetaReply member = CoachBetaReply.parse(200,
        "{\"phase\":\"open\",\"member\":true,\"seats_left\":3}");
    CoachBetaReply ended = CoachBetaReply.parse(200,
        "{\"phase\":\"ended\",\"member\":true,\"seats_left\":0}");

    Assert.assertEquals(FeedbackMode.OPTIONAL, CoachBeta.modeOf(reply));
    Assert.assertEquals(FeedbackMode.REQUIRED, CoachBeta.modeOf(member));
    Assert.assertEquals(FeedbackMode.HIDDEN, CoachBeta.modeOf(ended));
  }
}
