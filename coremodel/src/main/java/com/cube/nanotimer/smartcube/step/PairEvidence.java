package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which of an F2L pair's executions in solves show the solver putting the pair in with an algorithm
 * of their own rather than finding their way there.
 *
 * <p>An execution counts when it is one of the case's listed algorithms (free-slot tricks included,
 * as {@link CaseAlgorithms#read} reads them), or when the solver has turned that same execution at
 * least {@link #OWN_TURNS} times for the case. The second half is what keeps a case whose common
 * answer is missing from the vote table from staying unknown forever. "The same" is the grip-free
 * key the case sheet groups executions by, so one sequence turned from another angle is still one.
 *
 * <p>Nothing here counts against a pair: F2L is mostly turned by sight, and an execution turned once
 * and never again says nothing about whether the case is known (owner, 2026-11-16).
 */
public final class PairEvidence {

  /** How many times an unlisted execution has to be turned to be the solver's own algorithm. */
  public static final int OWN_TURNS = 2;

  private PairEvidence() {
  }

  /**
   * @param caseCode a pair's case, {@code pair_27}
   * @param executions every execution of the case in solves, in any order
   * @return for each execution, whether it counts as the case put in with one algorithm
   */
  public static boolean[] clean(String caseCode, List<String> executions) {
    List<String> keys = new ArrayList<String>();
    Map<String, Integer> turns = new HashMap<String, Integer>();
    for (String moves : executions) {
      String key = moves == null || moves.trim().isEmpty() || AlgorithmForm.key(moves) == null
          ? null : CaseAlgorithms.keyAsDrawn(caseCode, moves);
      keys.add(key);
      if (key != null) {
        Integer count = turns.get(key);
        turns.put(key, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
      }
    }
    boolean[] clean = new boolean[executions.size()];
    for (int i = 0; i < clean.length; i++) {
      String key = keys.get(i);
      if (key == null) {
        continue; // notation nothing can read says nothing
      }
      clean[i] = turns.get(key).intValue() >= OWN_TURNS
          || !CaseAlgorithms.read(caseCode, executions.get(i)).isUnusual();
    }
    return clean;
  }
}
