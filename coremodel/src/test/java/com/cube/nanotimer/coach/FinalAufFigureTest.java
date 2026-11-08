package com.cube.nanotimer.coach;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@RunWith(JUnit4.class)
public class FinalAufFigureTest {

  @Test
  public void testMeansThePausesAndCountsThePllsWithoutOne() {
    FinalAufFigure figure = CoachPayloadBuilder.finalAuf(16, pauses(12));

    Assert.assertEquals(16, figure.getSolves());
    Assert.assertEquals(12, figure.getAufs());
    Assert.assertEquals(300, figure.getPauseMs()); // half 200 ms, half 400 ms
    Assert.assertTrue(figure.isLong());
  }

  @Test
  public void testAPauseUnderTheBarIsNotLong() {
    List<Long> quick = new ArrayList<Long>(Collections.nCopies(12, Long.valueOf(150)));
    Assert.assertFalse(CoachPayloadBuilder.finalAuf(12, quick).isLong());
  }

  @Test
  public void testTooFewFinalAufsSendNothing() {
    Assert.assertNull(CoachPayloadBuilder.finalAuf(40, pauses(CoachPayloadBuilder.FAMILY_FLOOR - 1)));
    Assert.assertNull(CoachPayloadBuilder.finalAuf(0, Collections.<Long>emptyList()));
  }

  @Test
  public void testFinalAufSurvivesARoundTripAndCanBeCited() {
    CoachPayload payload = CoachPayload.parse(
        payload().withFinalAuf(CoachPayloadBuilder.finalAuf(16, pauses(12))).toJson());

    Assert.assertEquals(12, payload.getFinalAuf().getAufs());
    Assert.assertEquals(16, payload.value("final_auf.solves"), 1e-9);
    Assert.assertEquals(12, payload.value("final_auf.aufs"), 1e-9);
    Assert.assertEquals(300, payload.value("final_auf.pause_ms"), 1e-9);
    Assert.assertNull(payload.value("final_auf.nothing"));
  }

  @Test
  public void testAPayloadWithoutFinalAufSaysNothingAboutIt() {
    CoachPayload payload = CoachPayload.parse(payload().toJson());

    Assert.assertNull(payload.getFinalAuf());
    Assert.assertNull(payload.value("final_auf.pause_ms"));
    Assert.assertFalse(payload().toJson().contains("final_auf"));
  }

  /** The copying withers rebuild the payload by hand, so a field one forgets would vanish. */
  @Test
  public void testTheOtherWithersKeepTheFinalAuf() throws Exception {
    CoachPayload payload = payload().withFinalAuf(CoachPayloadBuilder.finalAuf(16, pauses(12)));
    JSONObject after = new JSONObject(payload.withLanguage("fr").withF2lRotations(null)
        .withCrossMoves(null).toJson());
    after.remove("language");

    Assert.assertTrue(new JSONObject(payload.toJson()).similar(after));
  }

  @Test
  public void testEveryFieldIsAPathWordAndSurvivesCleaning() throws Exception {
    CoachPayload payload = payload().withFinalAuf(CoachPayloadBuilder.finalAuf(16, pauses(12)));
    JSONObject json = new JSONObject(payload.toJson());
    JSONObject auf = json.getJSONObject("final_auf");
    for (Iterator<String> keys = auf.keys(); keys.hasNext(); ) {
      String key = keys.next();
      Assert.assertTrue(key, CoachCodes.isPath("final_auf." + key));
    }
    Assert.assertEquals(Collections.<String>emptyList(), CoachCodes.clean(json));
    Assert.assertEquals(payload.toJson(), CoachPayload.parse(json.toString()).toJson());
  }

  /** Alternating 200 ms and 400 ms pauses. */
  private static List<Long> pauses(int count) {
    List<Long> pauses = new ArrayList<Long>();
    for (int i = 0; i < count; i++) {
      pauses.add(Long.valueOf(i % 2 == 0 ? 200 : 400));
    }
    return pauses;
  }

  private static CoachPayload payload() {
    return CoachPayloadBuilder.build("3x3", "cfop", Collections.<Long>emptyList(),
        Collections.<StepSample>emptyList(), Collections.<StepSample>emptyList(), 0,
        Collections.<StepSample>emptyList(), 0, null);
  }
}
