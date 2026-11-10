package com.cube.nanotimer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;

public class LearnPickTest {

  @Test
  public void clearedCaseLeavesThePickInItsOrder() {
    Set<String> left = Options.withoutLearned(codes("pll_ga", "pll_t", "pll_ua"),
        codes("pll_t", "oll_21"));
    assertEquals(Arrays.asList("pll_ga", "pll_ua"), Arrays.asList(left.toArray()));
  }

  @Test
  public void pickWithEveryCaseClearedFollowsTheSuggestion() {
    assertNull(Options.withoutLearned(codes("pll_ga", "pll_t"), codes("pll_t", "pll_ga")));
  }

  @Test
  public void noPickStaysNoPick() {
    assertNull(Options.withoutLearned(null, codes("pll_t")));
  }

  @Test
  public void pickWithNothingClearedIsUnchanged() {
    assertEquals(codes("oll_21", "oll_22"),
        Options.withoutLearned(codes("oll_21", "oll_22"), Collections.<String>emptySet()));
  }

  private static Set<String> codes(String... codes) {
    return new LinkedHashSet<String>(Arrays.asList(codes));
  }
}
