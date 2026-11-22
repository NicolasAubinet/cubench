package com.cube.nanotimer.smartcube.drill;

import static org.junit.Assert.assertEquals;

import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.smartcube.step.F2LPairScrambles;
import com.cube.nanotimer.vo.StepStats;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

public class PairWeightsTest {

  private static final List<String> CASES = Arrays.asList("pair_a1", "pair_a25", "pair_a12");

  @Test
  public void withNoSolvesACaseIsDealtByHowOftenItComesUp() {
    Map<String, Long> weights = PairWeights.of(spec(CASES, Collections.<String, Long>emptyMap()),
        null);
    for (String code : CASES) {
      assertEquals(code, F2LPairScrambles.placements(code), weights.get(code).longValue());
    }
  }

  /** With no target, over the basic mean leans the draw; under it, or never met, is frequency. */
  @Test
  public void aCaseRecentlyOverTheBasicMeanIsDealtMoreOften() {
    MethodStatistics statistics = recent();
    assertEquals(Long.valueOf(1200), PairWeights.basicMean(statistics.getCases("pair")));

    Map<String, Long> weights = PairWeights.of(spec(CASES, Collections.<String, Long>emptyMap()),
        statistics);
    assertEquals(PairWeights.LEAN * F2LPairScrambles.placements("pair_a1"),
        weights.get("pair_a1").longValue());
    assertEquals(F2LPairScrambles.placements("pair_a25"), weights.get("pair_a25").longValue());
    assertEquals(F2LPairScrambles.placements("pair_a12"), weights.get("pair_a12").longValue());
  }

  /** Each case leans on its own target, so a slower group is not dealt first for being slower. */
  @Test
  public void aCaseLeansOnItsOwnTargetWhereTheDrillSetsOne() {
    Map<String, Long> targets = new HashMap<String, Long>();
    targets.put("pair_7", 1500L);
    targets.put("pair_a1", 2700L);
    targets.put("pair_a25", 800L);

    Map<String, Long> weights = PairWeights.of(
        spec(Arrays.asList("pair_7", "pair_a1", "pair_a25"), targets), recent());

    assertEquals(F2LPairScrambles.placements("pair_7"), weights.get("pair_7").longValue());
    assertEquals(F2LPairScrambles.placements("pair_a1"), weights.get("pair_a1").longValue());
    assertEquals(PairWeights.LEAN * F2LPairScrambles.placements("pair_a25"),
        weights.get("pair_a25").longValue());
  }

  /** What a card shows as a pair drill's opening is what the session deals first. */
  @Test
  public void heaviestFirstIsTheOrderAWorkingSetOpensOn() {
    DrillSpec spec = spec(CASES, Collections.<String, Long>emptyMap()).withWorkingSet(2);
    Map<String, Long> weights = PairWeights.of(spec, recent());

    DrillSession session = new DrillSession(spec, new Random(1), weights, null);

    assertEquals(PairWeights.heaviestFirst(CASES, weights).subList(0, 2),
        session.getActiveCases());
  }

  /** Two basic pairs averaging 1200, a stuck one at 2500 and another at 900. */
  private static MethodStatistics recent() {
    return new MethodStatistics(Collections.<StepStats>emptyList(),
        Arrays.asList(stats("pair_3_fr", 10, 1000), stats("pair_7_bl", 10, 1400),
            stats("pair_a1_fr", 1, 2500), stats("pair_a25_fl", 2, 900)), 30);
  }

  private static DrillSpec spec(List<String> cases, Map<String, Long> targets) {
    return new DrillSpec("test", DrillSpec.Type.F2L_PAIR, DrillSpec.Delivery.VIRTUAL, cases,
        DrillSpec.Selection.WEIGHTED, 40, 0, targets, targets.isEmpty() ? 0 : 3,
        targets.isEmpty() ? 0 : 5, null);
  }

  private static StepStats stats(String code, int count, long meanMs) {
    return new StepStats(code, count, meanMs * count, 0, meanMs, meanMs, 0);
  }
}
