package com.cube.nanotimer.coach;

import com.cube.nanotimer.vo.StepStats;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StepFigureTest {

  @Test
  public void testFumblesSurviveTheRoundTrip() throws Exception {
    StepFigure figure = stepCase().withFumbles(0.18, Long.valueOf(6200));

    StepFigure read = StepFigure.fromJson(figure.toJson());

    Assert.assertEquals(0.18, read.getFumbleRate(), 1e-9);
    Assert.assertEquals(Long.valueOf(6200), read.getFumbleMeanMs());
    Assert.assertEquals(0.18, CoachPayload.field(read, "fumble_rate"), 1e-9);
    Assert.assertEquals(6200, CoachPayload.field(read, "fumble_mean_ms"), 1e-9);
    Assert.assertTrue(CoachCodes.isPath("cases.oll_45.fumble_rate"));
    Assert.assertTrue(CoachCodes.isPath("cases.oll_45.fumble_mean_ms"));
  }

  /** A rate of 0 is sent, so the server tells a clean case from an older app, which sends neither. */
  @Test
  public void testACaseWithoutFumblesSendsARateOfZeroAndNoMean() throws Exception {
    JSONObject json = stepCase().withFumbles(0, Long.valueOf(9000)).toJson();

    Assert.assertEquals(0, json.getDouble("fumble_rate"), 1e-9);
    Assert.assertFalse(json.has("fumble_mean_ms"));
    StepFigure read = StepFigure.fromJson(json);
    Assert.assertEquals(0, read.getFumbleRate(), 1e-9);
    Assert.assertNull(CoachPayload.field(read, "fumble_mean_ms"));
    Assert.assertNull(StepFigure.fromJson(stepCase().toJson()).getFumbleRate());
  }

  private static StepFigure stepCase() {
    StepStats stats = new StepStats("oll_45", 20, 40000, 8000, 1500, 2600, 20 * 2000.0 * 2000.0);
    return StepFigure.stepCase(stats, true, 0.2, 1200, 1800);
  }
}
