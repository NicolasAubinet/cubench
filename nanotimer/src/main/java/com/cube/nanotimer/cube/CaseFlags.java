package com.cube.nanotimer.cube;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;

import java.util.List;

/**
 * The solver's own say over which executions are pointed out: an algorithm off a case's list
 * ({@link CaseAlgorithms#isWorthPointingOut}) is not, when it is one they picked, typed in, or asked
 * not to be flagged for. Muted per algorithm rather than per case, so a slip on a case they turn
 * their own way still shows.
 */
public final class CaseFlags {

  private CaseFlags() {
  }

  /** Whether the solver asked not to be flagged for these moves. */
  public static boolean isMuted(String caseCode, String moves) {
    return indexOfMuted(caseCode, moves, Options.INSTANCE.getMutedCaseAlgorithms(caseCode)) >= 0;
  }

  public static void setMuted(String caseCode, String moves, boolean muted) {
    List<String> kept = Options.INSTANCE.getMutedCaseAlgorithms(caseCode);
    int at = indexOfMuted(caseCode, moves, kept);
    if (muted && at < 0) {
      kept.add(moves);
    } else if (!muted && at >= 0) {
      kept.remove(at);
    } else {
      return;
    }
    Options.INSTANCE.setMutedCaseAlgorithms(caseCode, kept);
  }

  /** Whether the moves are ones the solver picked, typed in, or muted for the case. */
  public static boolean isTheirs(String caseCode, String moves) {
    return isMuted(caseCode, moves)
        || CaseAlgorithms.sameTurning(caseCode, moves, Options.INSTANCE.getCaseAlgorithm(caseCode))
        || CaseAlgorithms.sameTurning(caseCode, moves,
            Options.INSTANCE.getOwnCaseAlgorithm(caseCode));
  }

  private static int indexOfMuted(String caseCode, String moves, List<String> muted) {
    for (int i = 0; i < muted.size(); i++) {
      if (CaseAlgorithms.sameTurning(caseCode, muted.get(i), moves)) {
        return i;
      }
    }
    return -1;
  }
}
