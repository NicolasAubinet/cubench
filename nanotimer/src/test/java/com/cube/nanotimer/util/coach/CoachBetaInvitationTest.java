package com.cube.nanotimer.util.coach;

import org.junit.Assert;
import org.junit.Test;

public class CoachBetaInvitationTest {

  @Test
  public void aSolverWhoCanUseTheCoachTodayIsInvited() {
    Assert.assertTrue(CoachBetaInvitation.may(false, true, false, true, true));
  }

  @Test
  public void eachMissingConditionKeepsTheInvitationBack() {
    Assert.assertFalse("shown once already", CoachBetaInvitation.may(true, true, false, true, true));
    Assert.assertFalse("no seat, closed or ended", CoachBetaInvitation.may(false, false, false, true, true));
    Assert.assertFalse("already has the coach", CoachBetaInvitation.may(false, true, true, true, true));
    Assert.assertFalse("no cube connected", CoachBetaInvitation.may(false, true, false, false, true));
    Assert.assertFalse("type the coach can't read", CoachBetaInvitation.may(false, true, false, true, false));
  }

  @Test
  public void tenSolvesAreEnough() {
    Assert.assertFalse(CoachBetaInvitation.due(9));
    Assert.assertTrue(CoachBetaInvitation.due(10));
  }
}
