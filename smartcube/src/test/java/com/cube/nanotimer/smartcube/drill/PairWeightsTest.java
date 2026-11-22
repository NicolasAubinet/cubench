package com.cube.nanotimer.smartcube.drill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.smartcube.step.F2LPairScrambles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

public class PairWeightsTest {

  /** Four basic pairs, their usual lengths 3, 7, 7 and 11 moves, and a stuck one. */
  private static final List<String> CASES =
      Arrays.asList("pair_1", "pair_2", "pair_3", "pair_4", "pair_a1");
  private static final int[] LENGTHS = {3, 7, 7, 11};

  @Test
  public void withNoSolvesACaseIsDealtByHowOftenItComesUpInTheSpecsOrder() {
    Map<String, Long> weights = PairWeights.of(spec(), null);

    assertEquals(CASES, new ArrayList<String>(weights.keySet()));
    for (String code : CASES) {
      assertEquals(code, placements(code), weights.get(code).longValue());
    }
  }

  @Test
  public void aPairWithMovesTakenBackIsDealtMoreOftenAndOpensTheDrill() {
    List<PairSighting> sightings = onTheLine(8);
    mark(sightings, "pair_3", 4, true);

    Map<String, Long> weights = PairWeights.of(spec(), sightings);

    assertEquals("pair_3", weights.keySet().iterator().next());
    assertEquals(PairWeights.LEAN * placements("pair_3"), weights.get("pair_3").longValue());
    assertEquals(placements("pair_2"), weights.get("pair_2").longValue());
  }

  /** A long pair is slow for its length, not for the solver: only time over the line counts. */
  @Test
  public void aPairIsTimedAgainstWhatItsLengthTakesTheSolver() {
    List<PairSighting> sightings = onTheLine(8);
    for (int i = 0; i < sightings.size(); i++) {
      PairSighting one = sightings.get(i);
      if (one.getCaseCode().equals("pair_1")) {
        sightings.set(i, new PairSighting("pair_1_bl", one.getTimeMs() + 800, 3, false, false));
      }
    }

    Map<String, Double> scores = PairWeights.scores(sightings);

    assertEquals("pair_1", PairWeights.of(spec(), sightings).keySet().iterator().next());
    assertTrue(scores.get("pair_1") > scores.get("pair_4"));
  }

  @Test
  public void aPairSeenUnderSixTimesIsNotRanked() {
    List<PairSighting> sightings = onTheLine(8);
    sightings.removeIf(one -> one.getCaseCode().equals("pair_3"));
    for (int i = 0; i < PairWeights.MIN_SIGHTINGS - 1; i++) {
      sightings.add(new PairSighting("pair_3", 3100, 7, true, true));
    }

    assertEquals(placements("pair_3"), PairWeights.of(spec(), sightings).get("pair_3").longValue());
  }

  /** Stuck pairs do not rank from solves: the drill's targets measure them. */
  @Test
  public void aStuckPairIsLeftToTheDrill() {
    List<PairSighting> sightings = onTheLine(8);
    for (int i = 0; i < 8; i++) {
      sightings.add(new PairSighting("pair_a1", 6000, 9, true, true));
    }

    Map<String, Long> weights = PairWeights.of(spec(), sightings);

    assertEquals(placements("pair_a1"), weights.get("pair_a1").longValue());
  }

  /** What a card shows as a pair drill's opening is what the session deals first. */
  @Test
  public void theWeightsOrderIsTheOrderAWorkingSetOpensOn() {
    List<PairSighting> sightings = onTheLine(8);
    mark(sightings, "pair_4", 3, true);
    mark(sightings, "pair_2", 6, false);
    DrillSpec spec = spec().withWorkingSet(2);
    Map<String, Long> weights = PairWeights.of(spec, sightings);

    DrillSession session = new DrillSession(spec, new Random(1), weights, null);

    assertEquals(PairWeights.openingOrder(CASES, weights).subList(0, 2),
        session.getActiveCases());
    assertEquals("pair_4", session.getActiveCases().get(0));
  }

  /** Each basic pair seen {@code times} times, right on a 1 s + 0.3 s a move line. */
  private static List<PairSighting> onTheLine(int times) {
    List<PairSighting> sightings = new ArrayList<PairSighting>();
    for (int i = 0; i < times; i++) {
      for (int c = 0; c < LENGTHS.length; c++) {
        sightings.add(new PairSighting(CASES.get(c), 1000 + 300 * LENGTHS[c], LENGTHS[c], false,
            false));
      }
    }
    return sightings;
  }

  private static void mark(List<PairSighting> sightings, String code, int count, boolean undone) {
    for (int i = 0; i < sightings.size() && count > 0; i++) {
      PairSighting one = sightings.get(i);
      if (one.getCaseCode().equals(code)) {
        sightings.set(i, new PairSighting(code, one.getTimeMs(), one.getUsualMoves(), undone,
            !undone));
        count--;
      }
    }
  }

  private static long placements(String code) {
    return Math.max(1, F2LPairScrambles.placements(code));
  }

  private static DrillSpec spec() {
    return new DrillSpec("test", DrillSpec.Type.F2L_PAIR, DrillSpec.Delivery.VIRTUAL, CASES,
        DrillSpec.Selection.WEIGHTED, 40, 0, Collections.<String, Long>emptyMap(), 0, 0, null);
  }
}
