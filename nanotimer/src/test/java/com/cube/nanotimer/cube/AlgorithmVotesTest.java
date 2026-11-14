package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/** The per-case votes for the shared usage counts, from solves built as in {@link CaseExecutionsTest}. */
public class AlgorithmVotesTest {

  private static final String SUNE = "R U R' U R U U R'";
  private static final String TPERM = "R U R' U' R' F R R U' R' U' R U R' F'";
  /** OLL 45's algorithm, which leaves a Sune: with SUNE after it, a two-look OLL. */
  private static final String EDGES_FIRST = "F R U R' U' F'";
  private static final String PAIRS = "R U R' B U B' L U L' F U F'";

  @Test
  public void votesForWhatWasTurnedTwice() {
    Map<String, String> votes = AlgorithmVotes.readFrom(times(2, SUNE + " " + TPERM));

    assertEquals("R U R' U R U2 R'", votes.get("oll_27"));
    assertEquals("R U R' U' R' F R2 U' R' U' R U R' F'", votes.get("pll_t"));
  }

  @Test
  public void aCaseSolvedOnceIsNoVote() {
    assertTrue(AlgorithmVotes.readFrom(times(1, SUNE + " " + TPERM)).isEmpty());
  }

  /** The popup reads the Sune ending a two-look as an OLL 27 answer; a vote must not. */
  @Test
  public void aTwoLookOllNeverVotesForAOneLookCase() {
    List<SolveTime> solves = times(3, EDGES_FIRST + " " + SUNE + " " + TPERM);
    assertTrue(CaseExecutions.readFrom(solves).containsKey("oll_27"));

    Map<String, String> votes = AlgorithmVotes.readFrom(solves);

    for (String caseCode : votes.keySet()) {
      assertFalse(caseCode, caseCode.startsWith("oll_"));
    }
    assertTrue(votes.containsKey("pll_t"));
  }

  @Test
  public void votesForAnF2lPairToo() {
    assertEquals("R U R'", AlgorithmVotes.readFrom(times(1, PAIRS + " " + SUNE)).get("pair_4"));
  }

  @Test
  public void readsNothingOutOfASolveThatIsNotCfop() {
    List<SolveTime> solves = times(2, SUNE + " " + TPERM);
    for (SolveTime solve : solves) {
      solve.getSolveType().setMethod(CubeMethod.ROUX);
    }

    assertTrue(AlgorithmVotes.readFrom(solves).isEmpty());
  }

  @Test
  public void sendsOnlyTheVotesThatChanged() {
    Map<String, String> reported = new HashMap<String, String>();
    reported.put("oll_27", "R U R' U R U2 R'");
    reported.put("pll_t", "old");
    reported.put("pll_y", "kept");
    Map<String, String> current = new HashMap<String, String>();
    current.put("oll_27", "R U R' U R U2 R'");
    current.put("pll_t", "new");
    current.put("pll_aa", "first");

    Map<String, String> changed = AlgorithmVotes.changedSince(current, reported);

    assertEquals(2, changed.size());
    assertEquals("new", changed.get("pll_t"));
    assertEquals("first", changed.get("pll_aa"));
    assertEquals(current, AlgorithmVotes.changedSince(current, null));
  }

  private static List<SolveTime> times(int count, String moves) {
    List<SolveTime> solves = new ArrayList<SolveTime>();
    for (int i = 0; i < count; i++) {
      SolveTime solve = CaseExecutionsTest.solves(moves).get(0);
      solve.setId(i + 1);
      solves.add(solve);
    }
    return solves;
  }
}
