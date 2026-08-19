package com.cube.nanotimer.cube;

import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads a solve type's stored solves again under the method it is now read as, and says which of
 * them to write back.
 *
 * <p>Worth doing at all because the stored breakdown is a cache and not a record: the scramble and
 * the move stream are what was kept, and the steps are a conclusion drawn from them. The solve
 * sheet redraws that conclusion every time it opens, but the history list's bars and the method
 * figures read the stored rows, and neither can afford to replay hundreds of solves each time they
 * are shown. So the store is brought up to date once, when the method changes, rather than every
 * reader paying for it forever.
 *
 * <p><b>What it refuses to touch.</b> A solve is only emptied when it was read right through, came
 * out solved, and still fitted nothing — then the moves really do not bear the method out. Short of
 * that, the stored breakdown stands:
 *
 * <ul>
 *   <li>a solve that cannot be replayed at all keeps what it has, since nothing can rebuild it;
 *   <li>a solve that did not come out solved keeps it too, because a walk from the wrong start
 *       state fits no method either, and this cannot tell the two apart.
 * </ul>
 *
 * <p>Nothing here is one-way. The raw record is never written, so setting the method back and
 * reading again returns the very breakdown that was there before.
 */
public final class SolveReinterpreter {

  private SolveReinterpreter() {
  }

  /** Follows the reading, and can stop it: nothing is written until every solve has been read. */
  public interface Progress {
    /** @return false to give up on the whole run */
    boolean onRead(int done, int total);
  }

  /**
   * The solves whose stored breakdown should be replaced, each carrying the one it was read into,
   * or none where the solve no longer fits the method. Empty is a legitimate answer, and null says
   * the run was stopped part way and must be thrown away whole.
   */
  public static List<SolveTime> reread(List<SolveTime> solves, CubeMethod method,
      Progress progress) {
    List<SolveTime> rewritten = new ArrayList<SolveTime>();
    for (int i = 0; i < solves.size(); i++) {
      SolveTime solve = solves.get(i);
      StoredSolveReplay.Result result = StoredSolveReplay.reinterpret(solve.getScramble(),
          solve.getSmartcubeMoves(), method);
      if (result != null && (result.getMethod() != null || result.reachedSolved())) {
        solve.setSmartcubeMethod(result.getMethod());
        solve.setSmartcubeSteps(result.getMethod() == null
            ? Collections.<SolveStep>emptyList() : result.getSteps());
        solve.setSmartcubeStoppedStep(result.getStoppedStep());
        rewritten.add(solve);
      }
      if (progress != null && !progress.onRead(i + 1, solves.size())) {
        return null;
      }
    }
    return rewritten;
  }
}
