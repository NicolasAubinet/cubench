package com.cube.nanotimer.util.coach;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.cube.nanotimer.drill.DrillSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class CoachPracticeChangesTest {

  @Test
  public void testACaseThatJoinedAndOneThatLeft() {
    CoachPractice.Changes changes = CoachPractice.changes(
        sets(set("pll", "pll_ga", "pll_t"), set("oll", "oll_27")),
        sets(set("pll", "pll_ga", "pll_ja"), set("oll", "oll_27", "oll_45")));

    Assert.assertEquals(Arrays.asList("pll_ja", "oll_45"), changes.getJoined());
    Assert.assertEquals(Collections.singletonList("pll_t"), changes.getLeft());
    Assert.assertFalse(changes.isEmpty());
  }

  /** A case moving from one set to another has neither joined nor left the practice. */
  @Test
  public void testACaseInAnySetCountsOnce() {
    CoachPractice.Changes changes = CoachPractice.changes(
        sets(set("oll", "oll_27")), sets(set("oll", "oll_45"), set("oll", "oll_27")));

    Assert.assertEquals(Collections.singletonList("oll_45"), changes.getJoined());
    Assert.assertTrue(changes.getLeft().isEmpty());
  }

  @Test
  public void testTheSameSetsSayNothing() {
    Assert.assertTrue(CoachPractice.changes(sets(set("pll", "pll_ga")),
        sets(set("pll", "pll_ga"))).isEmpty());
  }

  private static List<CoachPractice.Set> sets(CoachPractice.Set... sets) {
    return new ArrayList<CoachPractice.Set>(Arrays.asList(sets));
  }

  private static CoachPractice.Set set(String family, String... cases) {
    List<String> codes = Arrays.asList(cases);
    DrillSpec drill = new DrillSpec("coach-practice-" + family, DrillSpec.Type.CASE_RECOGNITION,
        DrillSpec.Delivery.VIRTUAL, codes, DrillSpec.Selection.WEIGHTED, 40, 0, null);
    return new CoachPractice.Set(family, codes, drill);
  }
}
