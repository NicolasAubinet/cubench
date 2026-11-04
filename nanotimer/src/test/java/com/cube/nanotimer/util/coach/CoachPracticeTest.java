package com.cube.nanotimer.util.coach;

import com.cube.nanotimer.coach.CoachPayload;
import com.cube.nanotimer.coach.CoachPlan;
import com.cube.nanotimer.coach.Evidence;
import com.cube.nanotimer.coach.FocusArea;
import com.cube.nanotimer.coach.StoredCoachPlan;
import com.cube.nanotimer.drill.DrillSpec;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONObject;

import org.junit.Assert;
import org.junit.Test;

/** The practice sets the coach service answers with, read without a network. */
public class CoachPracticeTest {

  private static final String PLL_DRILL = "{\"spec_version\":3,\"id\":\"coach-practice-pll\","
      + "\"puzzle\":\"3x3\",\"method\":\"cfop\",\"type\":\"case_execution\","
      + "\"delivery\":\"virtual\",\"cases\":[\"pll_rb\",\"pll_v\"],\"selection\":\"weighted\","
      + "\"reps\":40,\"target_ms\":4300,\"case_targets_ms\":{\"pll_rb\":4800,\"pll_v\":4700},"
      + "\"retire_after_wins\":3,\"min_showings\":5}";

  @Test
  public void eachSetCarriesItsFamilyCasesAndARunnableDrill() {
    List<CoachPractice.Set> sets = CoachPractice.parse("{\"sets\":[{\"family\":\"pll\","
        + "\"cases\":[\"pll_rb\",\"pll_v\"],\"cost_ms\":120,\"drill\":" + PLL_DRILL + "}]}");

    Assert.assertEquals(1, sets.size());
    Assert.assertEquals("pll", sets.get(0).getFamily());
    Assert.assertEquals(Arrays.asList("pll_rb", "pll_v"), sets.get(0).getCases());
    Assert.assertTrue(sets.get(0).getDrill().retiresCases());
    Assert.assertEquals(4800, sets.get(0).getDrill().getTargetMs("pll_rb"));
  }

  /** A drill from a newer service is one set this app cannot run, not a section that cannot draw. */
  @Test
  public void aSetWhoseDrillThisAppCannotRunIsLeftOut() {
    String tooNew = PLL_DRILL.replace("\"spec_version\":3",
        "\"spec_version\":" + (DrillSpec.VERSION + 1));
    List<CoachPractice.Set> sets = CoachPractice.parse("{\"sets\":[{\"family\":\"oll\","
        + "\"drill\":" + tooNew + "},{\"family\":\"pll\",\"drill\":" + PLL_DRILL + "}]}");

    Assert.assertEquals(1, sets.size());
    Assert.assertEquals("pll", sets.get(0).getFamily());
  }

  /** The solver picks how many of the next OLLs to see; one cleared since is skipped. */
  @Test
  public void aLearningSetShowsItsFirstCasesWithTheirAlgorithms() {
    DrillSpec learning = DrillSpec.learning("coach-learn-oll", DrillSpec.Type.CASE_EXECUTION,
        Arrays.asList("oll_26", "oll_27", "oll_21", "oll_45"), DrillSpec.Selection.ROUND_ROBIN, 40,
        5, Collections.singletonMap("oll_26", "R U2 R' U' R U' R'"), null);
    CoachPractice.Set set = CoachPractice.parse("{\"sets\":[{\"family\":\"oll\",\"drill\":"
        + learning.toJson() + "}]}").get(0);

    CoachPractice.Set shown = set.firstToLearn(2, Collections.singleton("oll_27"));

    Assert.assertTrue(set.isLearning());
    Assert.assertEquals(3, set.countToLearn(Collections.singleton("oll_27")));
    Assert.assertEquals(Arrays.asList("oll_26", "oll_21"), shown.getCases());
    Assert.assertTrue(shown.getDrill().isLearning());
    Assert.assertEquals(5, shown.getDrill().getWinsToRetire());
    Assert.assertEquals("R U2 R' U' R U' R'", shown.getDrill().getCaseAlgorithm("oll_26"));
    Assert.assertNull(set.firstToLearn(3, set.getCases()));
  }

  @Test
  public void nothingKeptOrTextThatIsNoAnswerReadsAsNoSets() {
    Assert.assertTrue(CoachPractice.parse(null).isEmpty());
    Assert.assertTrue(CoachPractice.parse("<html>502</html>").isEmpty());
  }

  private static final TimeZone ZONE = TimeZone.getTimeZone("Europe/Brussels");
  /** 2026-11-04 09:00 in Brussels. */
  private static final long MORNING = 1793779200000L;
  private static final long HOUR = 60L * 60 * 1000;

  /** Once a day: the first visit of a new local day asks, any later one that day does not. */
  @Test
  public void keptSetsAreAskedForAgainOnTheFirstVisitOfANewDay() {
    String with = CoachPractice.askedWith(Arrays.asList("pll_t"), Arrays.asList("pll_gb"));

    Assert.assertFalse(CoachPractice.due("{}", MORNING, with, MORNING + 14 * HOUR, with, ZONE));
    Assert.assertTrue(CoachPractice.due("{}", MORNING, with, MORNING + 15 * HOUR + 1, with, ZONE));
  }

  @Test
  public void nothingKeptOrNeverAskedIsDue() {
    Assert.assertTrue(CoachPractice.due(null, MORNING, "|", MORNING, "|", ZONE));
    Assert.assertTrue(CoachPractice.due("{}", 0, null, MORNING, "|", ZONE));
  }

  /** A cleared case or a new plan changes the answer, so it is asked for the same day. */
  @Test
  public void otherClearedOrDrilledCasesAreDueAtOnce() {
    String before = CoachPractice.askedWith(Collections.<String>emptyList(),
        Arrays.asList("pll_gb"));

    Assert.assertTrue(CoachPractice.due("{}", MORNING, before, MORNING + HOUR,
        CoachPractice.askedWith(Arrays.asList("oll_26"), Arrays.asList("pll_gb")), ZONE));
    Assert.assertTrue(CoachPractice.due("{}", MORNING, before, MORNING + HOUR,
        CoachPractice.askedWith(Collections.<String>emptyList(), Arrays.asList("pll_v")), ZONE));
  }

  @Test
  public void theOrderTheCasesComeInChangesNothing() {
    Assert.assertEquals(CoachPractice.askedWith(Arrays.asList("b", "a"), Arrays.asList("d", "c")),
        CoachPractice.askedWith(Arrays.asList("a", "b"), Arrays.asList("c", "d")));
    Assert.assertNotEquals(CoachPractice.askedWith(Arrays.asList("a"), Collections.<String>emptyList()),
        CoachPractice.askedWith(Collections.<String>emptyList(), Arrays.asList("a")));
  }

  /** What the plan's drills run is sent, so the service need not read the plan back. */
  @Test
  public void theStoredPlansDrilledCasesGoInTheRequest() throws Exception {
    FocusArea drilled = new FocusArea("slow_case", Arrays.asList("pll"),
        Collections.<Evidence>emptyList(), "text", PLL_DRILL);
    FocusArea plain = new FocusArea("slow_case", Arrays.asList("oll"),
        Collections.<Evidence>emptyList(), "text", null);
    StoredCoachPlan stored = new StoredCoachPlan(new CoachPlan(CoachPlan.VERSION,
        CoachPlan.Source.COACH, "a headline", Arrays.asList(drilled, plain)), null, MORNING);

    List<String> cases = CoachPractice.drilledCases(stored);

    Assert.assertEquals(Arrays.asList("pll_rb", "pll_v"), cases);
    Assert.assertEquals(Collections.<String>emptyList(), CoachPractice.drilledCases(null));
    JSONArray sent = new JSONObject(CoachClient.practiceBody(payload(),
        Collections.<String>emptyList(), cases)).getJSONArray("drilled_cases");
    Assert.assertEquals("pll_rb", sent.getString(0));
    Assert.assertEquals(2, sent.length());
  }

  private static CoachPayload payload() {
    return CoachPayload.parse(
        "{\"schema_version\":" + CoachPayload.VERSION + ",\"puzzle\":\"3x3\",\"method\":\"cfop\"}");
  }
}
