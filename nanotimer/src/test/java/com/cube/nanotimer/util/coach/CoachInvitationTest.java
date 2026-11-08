package com.cube.nanotimer.util.coach;

import com.cube.nanotimer.coach.CoachPayloadBuilder;
import org.junit.Assert;
import org.junit.Test;

public class CoachInvitationTest {

  @Test
  public void aSolverWhoCanGetAPlanTodayIsInvited() {
    Assert.assertTrue(CoachInvitation.may(false, false, false, true, true));
  }

  @Test
  public void eachMissingConditionKeepsTheInvitationBack() {
    Assert.assertFalse("shown once already", CoachInvitation.may(true, false, false, true, true));
    Assert.assertFalse("beta full", CoachInvitation.may(false, true, false, true, true));
    Assert.assertFalse("has or had the coach", CoachInvitation.may(false, false, true, true, true));
    Assert.assertFalse("no cube connected", CoachInvitation.may(false, false, false, false, true));
    Assert.assertFalse("type the coach can't read", CoachInvitation.may(false, false, false, true, false));
  }

  @Test
  public void invitesAtTheSolvesAPlanNeeds() {
    Assert.assertFalse(CoachInvitation.due(CoachPayloadBuilder.PLAN_FLOOR - 1));
    Assert.assertTrue(CoachInvitation.due(CoachPayloadBuilder.PLAN_FLOOR));
  }
}
