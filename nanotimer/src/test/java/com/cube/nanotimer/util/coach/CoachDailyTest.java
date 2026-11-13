package com.cube.nanotimer.util.coach;

import com.cube.nanotimer.coach.CoachPayload;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.TimeZone;

@RunWith(JUnit4.class)
public class CoachDailyTest {

  private static final TimeZone ZONE = TimeZone.getTimeZone("Europe/Brussels");
  private static final long HOUR = 60L * 60 * 1000;
  /** 2026-11-13 09:00 in Brussels. */
  private static final long MORNING = 1794556800000L;

  @Test
  public void aDayWithNothingDueIsStillAnAnswer() {
    Assert.assertTrue(CoachDaily.isAnswer("{\"blocks\":[]}"));
    Assert.assertFalse(CoachDaily.isAnswer("{\"sets\":[]}"));
    Assert.assertFalse(CoachDaily.isAnswer("not json"));
    Assert.assertFalse(CoachDaily.isAnswer(null));
  }

  @Test
  public void theAnswerIsKeptForTheLocalDay() {
    Assert.assertTrue(CoachDaily.sameDay(MORNING, MORNING + 14 * HOUR, ZONE));
    Assert.assertFalse(CoachDaily.sameDay(MORNING, MORNING + 16 * HOUR, ZONE)); // past midnight
    Assert.assertFalse(CoachDaily.sameDay(0, MORNING, ZONE));
  }

  /** The daily ask names the plan's cases as its focus; a practice ask, the day's to leave out. */
  @Test
  public void theDrilledCasesRideBesideThePayload() throws Exception {
    JSONObject sent = new JSONObject(CoachClient.requestBody(payload(), Arrays.asList("oll_26"),
        Arrays.asList("pll_t", "pll_h")));

    Assert.assertEquals(2, sent.getJSONArray("drilled_cases").length());
    Assert.assertEquals("pll_h", sent.getJSONArray("drilled_cases").getString(1));
    Assert.assertEquals(CoachPayload.VERSION,
        sent.getJSONObject("payload").getInt("schema_version"));
  }

  private static CoachPayload payload() {
    return CoachPayload.parse(
        "{\"schema_version\":" + CoachPayload.VERSION + ",\"puzzle\":\"3x3\",\"method\":\"cfop\"}");
  }
}
