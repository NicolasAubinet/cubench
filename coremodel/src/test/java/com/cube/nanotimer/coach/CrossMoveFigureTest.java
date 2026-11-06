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
public class CrossMoveFigureTest {

  @Test
  public void testMeansThePlainCrossesAndLeavesXCrossesOut() {
    CrossMoveFigure figure = CoachPayloadBuilder.crossMoves(history(12));

    Assert.assertEquals(12, figure.getSolves());
    Assert.assertEquals(2, figure.getXCrosses());
    Assert.assertEquals(8.0, figure.getTurnedMoves(), 1e-9); // 6 of 7 moves, 6 of 9
    Assert.assertEquals(6.0, figure.getOptimalMoves(), 1e-9);
    Assert.assertEquals(2.0, figure.getExtraMoves(), 1e-9);
    Assert.assertEquals(6, figure.getLongCrosses());
  }

  @Test
  public void testTooFewPlainCrossesSendNothing() {
    List<CrossTurn> crosses = history(CoachPayloadBuilder.FAMILY_FLOOR - 1);
    Assert.assertNull(CoachPayloadBuilder.crossMoves(crosses));
    Assert.assertNull(CoachPayloadBuilder.crossMoves(Collections.<CrossTurn>emptyList()));
  }

  @Test
  public void testCrossesSurviveARoundTripAndCanBeCited() {
    CoachPayload payload = CoachPayload.parse(
        payload().withCrossMoves(CoachPayloadBuilder.crossMoves(history(12))).toJson());

    Assert.assertEquals(12, payload.getCrossMoves().getSolves());
    Assert.assertEquals(8.0, payload.value("cross_moves.turned_moves"), 1e-9);
    Assert.assertEquals(6.0, payload.value("cross_moves.optimal_moves"), 1e-9);
    Assert.assertEquals(2.0, payload.value("cross_moves.extra_moves"), 1e-9);
    Assert.assertEquals(6, payload.value("cross_moves.long_crosses"), 1e-9);
    Assert.assertEquals(2, payload.value("cross_moves.x_crosses"), 1e-9);
    Assert.assertNull(payload.value("cross_moves.nothing"));
  }

  @Test
  public void testAPayloadWithoutCrossesSaysNothingAboutThem() {
    CoachPayload payload = CoachPayload.parse(payload().toJson());

    Assert.assertNull(payload.getCrossMoves());
    Assert.assertNull(payload.value("cross_moves.solves"));
    Assert.assertFalse(payload().toJson().contains("cross_moves"));
  }

  /** The copying withers rebuild the payload by hand, so a field one forgets would vanish. */
  @Test
  public void testTheOtherWithersKeepTheCrosses() throws Exception {
    CoachPayload payload = payload().withCrossMoves(CoachPayloadBuilder.crossMoves(history(12)));
    JSONObject after = new JSONObject(payload.withLanguage("fr").withF2lRotations(null).toJson());
    after.remove("language");

    Assert.assertTrue(new JSONObject(payload.toJson()).similar(after));
  }

  @Test
  public void testEveryFieldIsAPathWordAndSurvivesCleaning() throws Exception {
    CoachPayload payload = payload().withCrossMoves(CoachPayloadBuilder.crossMoves(history(12)));
    JSONObject json = new JSONObject(payload.toJson());
    JSONObject cross = json.getJSONObject("cross_moves");
    for (Iterator<String> keys = cross.keys(); keys.hasNext(); ) {
      String key = keys.next();
      Assert.assertTrue(key, CoachCodes.isPath("cross_moves." + key));
    }
    Assert.assertEquals(Collections.<String>emptyList(), CoachCodes.clean(json));
    Assert.assertEquals(payload.toJson(), CoachPayload.parse(json.toString()).toJson());
  }

  /** Each solve's cross: half 6 optimal in 7, half 6 in 9, and one X-cross in every six. */
  private static List<CrossTurn> history(int plain) {
    List<CrossTurn> crosses = new ArrayList<CrossTurn>();
    for (int i = 0; i < plain; i++) {
      crosses.add(new CrossTurn(i, 6, i % 2 == 0 ? 7 : 9, 0));
      if (i % 6 == 5) {
        crosses.add(new CrossTurn(100 + i, 5, 9, 1));
      }
    }
    return crosses;
  }

  private static CoachPayload payload() {
    return CoachPayloadBuilder.build("3x3", "cfop", Collections.<Long>emptyList(),
        Collections.<StepSample>emptyList(), Collections.<StepSample>emptyList(), 0,
        Collections.<StepSample>emptyList(), 0, null);
  }
}
