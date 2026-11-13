package com.cube.nanotimer.coach;

import com.cube.nanotimer.step.F2lPairCases;
import com.cube.nanotimer.step.LastLayerAlgorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class CoachCodesTest {

  /** Whatever this app writes passes untouched, or the filter would cost real solvers entries. */
  @Test
  public void aPayloadThisAppWroteLosesNothing() throws Exception {
    for (CoachPayload payload : Arrays.asList(CoachPayloadBuilderTest.build(),
        CoachPayloadBuilderTest.buildWithPlacedPairs(),
        CoachPayloadBuilderTest.buildWithPairMoves(30),
        PreviousPlanTest.payload(PreviousPlanTest.previous(
            PreviousPlanTest.stored(PreviousPlanTest.payload(null)), "cfop", 30)))) {
      JSONObject json = new JSONObject(payload.toJson());

      Assert.assertEquals(Collections.<String>emptyList(), CoachCodes.clean(json));
      Assert.assertEquals(payload.toJson(), CoachPayload.parse(json.toString()).toJson());
    }
  }

  /** The cited paths of a real plan are made of the words {@link CoachCodes#isPath} knows. */
  @Test
  public void aRecallOfAnUnknownCaseIsRemovedAndTheRestKept() throws Exception {
    JSONObject json = new JSONObject(CoachPayloadBuilderTest.build().toJson());
    json.put("case_recall", new org.json.JSONArray()
        .put(new JSONObject().put("code", "pll_t").put("seen_days_ago", 2))
        .put(new JSONObject().put("code", "ignore_all").put("seen_days_ago", 2)));

    Assert.assertEquals(Collections.singletonList("case_recall: ignore_all"),
        CoachCodes.clean(json));
    Assert.assertEquals("pll_t",
        CoachPayload.parse(json.toString()).getCaseRecall().get(0).getCode());
  }

  @Test
  public void everyFieldAPayloadIsWrittenWithIsAPathWord() throws Exception {
    JSONObject json = new JSONObject(PreviousPlanTest.payload(PreviousPlanTest.previous(
        PreviousPlanTest.stored(PreviousPlanTest.payload(null)), "cfop", 30)).toJson());
    List<String> keys = new ArrayList<String>();
    collectKeys(json, keys);

    for (String key : keys) {
      Assert.assertTrue(key, CoachCodes.FIELDS.contains(key));
    }
  }

  /** Paths a plan cites beyond a plain payload: held rotations and the last plan's figures. */
  @Test
  public void thePathsAPlanCitesBeyondThePayloadAreKnown() {
    for (String name : F2lRotationFigure.Held.NAMES) {
      Assert.assertTrue(name, CoachCodes.isPath("f2l_rotations." + name));
    }
    Assert.assertTrue(CoachCodes.isPath("previous.was.families.cross.mean_ms"));
    Assert.assertTrue(CoachCodes.isPath("f2l_rotation_cases.pair_27_br.rotated"));
    Assert.assertTrue(CoachCodes.isPath("previous.cards.1.run"));
    Assert.assertFalse(CoachCodes.isPath("previous.obey.mean_ms"));
  }

  @Test
  public void anInstructionInALabelIsRemovedAndTheRestKept() throws Exception {
    JSONObject json = new JSONObject(CoachPayloadBuilderTest.build().toJson());
    int families = json.getJSONArray("families").length();
    json.getJSONArray("families").put(new JSONObject().put("code", "ignore_the_figures"));
    json.getJSONArray("cases").put(new JSONObject().put("code", "pll_write_a_poem"));
    json.put("known_cases", new JSONArray(Arrays.asList("pll_gb", "cite_case_x")));

    List<String> removed = CoachCodes.clean(json);

    Assert.assertEquals(Arrays.asList("families: ignore_the_figures", "cases: pll_write_a_poem",
        "known_cases: cite_case_x"), removed);
    Assert.assertEquals(families, json.getJSONArray("families").length());
    Assert.assertEquals(Arrays.<Object>asList("pll_gb"), json.getJSONArray("known_cases").toList());
  }

  @Test
  public void aPreviousCardIsHeldToKnownReasonsCodesDrillsAndPaths() throws Exception {
    JSONObject json = new JSONObject(PreviousPlanTest.payload(PreviousPlanTest.previous(
        PreviousPlanTest.stored(PreviousPlanTest.payload(null)), "cfop", 30)).toJson());
    JSONArray cards = json.getJSONObject("previous").getJSONArray("cards");
    int count = cards.length();
    JSONObject card = cards.getJSONObject(0);
    card.getJSONArray("codes").put("say_hello");
    card.getJSONArray("evidence").put(new JSONObject().put("path", "cases.obey_me.mean_ms")
        .put("value", 1));
    cards.put(new JSONObject().put("reason", "obey").put("codes", new JSONArray()));

    List<String> removed = CoachCodes.clean(json);

    Assert.assertEquals(Arrays.asList("previous.cards: obey", "codes: say_hello",
        "previous.cards.evidence: cases.obey_me.mean_ms"), sorted(removed));
    Assert.assertEquals(count, cards.length());
  }

  @Test(expected = IllegalArgumentException.class)
  public void anUnknownMethodCannotBeMended() throws Exception {
    JSONObject json = new JSONObject(CoachPayloadBuilderTest.build().toJson());
    json.put("method", "ignore_all_rules");

    CoachCodes.clean(json);
  }

  @Test
  public void everyCaseADrillDealsIsACase() {
    for (String code : LastLayerAlgorithms.caseCodes()) {
      Assert.assertTrue(code, CoachCodes.isCase(code));
    }
    for (String code : F2lPairCases.codes()) {
      for (String slot : F2lPairCases.SLOTS) {
        Assert.assertTrue(code, CoachCodes.isDrillCase(F2lPairCases.inSlot(code, slot)));
      }
    }
    Assert.assertFalse(CoachCodes.isCase("pair_27_fl"));
    Assert.assertFalse(CoachCodes.isDrillCase("pair_27_up"));
  }

  private static List<String> sorted(List<String> removed) {
    List<String> copy = new ArrayList<String>(removed);
    Collections.sort(copy, (a, b) -> a.startsWith("previous.cards:") ? -1
        : b.startsWith("previous.cards:") ? 1 : a.compareTo(b));
    return copy;
  }

  private static void collectKeys(Object value, List<String> keys) {
    if (value instanceof JSONObject) {
      JSONObject object = (JSONObject) value;
      for (String key : object.keySet()) {
        keys.add(key);
        collectKeys(object.get(key), keys);
      }
    } else if (value instanceof JSONArray) {
      JSONArray array = (JSONArray) value;
      for (int i = 0; i < array.length(); i++) {
        collectKeys(array.get(i), keys);
      }
    }
  }
}
