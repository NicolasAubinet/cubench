package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.vo.SolveStep;

import java.util.ArrayList;
import java.util.List;

/**
 * The pause before a CFOP solve's final AUF: the last-layer turns that end the PLL after its
 * algorithm. The pause is the longest gap from the algorithm's last other turn through those turns,
 * so an algorithm that itself ends on a U turn still has its pause found before the AUF.
 */
final class FinalAuf {

  /** A PLL that ended on its algorithm, with no AUF after it. */
  static final long NONE = -1;

  private static final String PLL = "pll";
  private static final String PLL_SKIP = "pll_skip";

  private FinalAuf() {
  }

  /**
   * @param steps the solve's steps from its start, the PLL last
   * @param crossFace the face the cross was built on, in the cube's own frame
   * @return the pause in ms, {@link #NONE}, or null where no PLL algorithm was turned: a skip, a
   *     solve stopped before its PLL, or a PLL of last-layer turns alone
   */
  static Long read(String storedMoves, List<SolveStep> steps, Face crossFace) {
    if (steps.isEmpty() || crossFace == null) {
      return null;
    }
    String name = steps.get(steps.size() - 1).getName();
    if (name == null || PLL_SKIP.equals(name) || !(PLL.equals(name) || name.startsWith(PLL + "_"))) {
      return null;
    }
    long startMs = 0;
    for (int i = 0; i < steps.size() - 1; i++) {
      startMs += steps.get(i).getTotalMs();
    }
    List<SolveMovesFormat.Move> turns = new ArrayList<SolveMovesFormat.Move>();
    for (SolveMovesFormat.Move move : SolveMovesFormat.parse(storedMoves)) {
      if (!SolveMovesFormat.isRotation(move.getNotation()) && move.getOffsetMs() > startMs) {
        turns.add(move);
      }
    }
    String lastLayer = opposite(crossFace).name();
    int tail = turns.size();
    while (tail > 0 && turns.get(tail - 1).getNotation().startsWith(lastLayer)) {
      tail--;
    }
    if (tail == 0) {
      return null;
    }
    if (tail == turns.size()) {
      return NONE;
    }
    long pause = 0;
    for (int i = tail; i < turns.size(); i++) {
      pause = Math.max(pause, turns.get(i).getOffsetMs() - turns.get(i - 1).getOffsetMs());
    }
    return pause;
  }

  private static Face opposite(Face face) {
    switch (face) {
      case U: return Face.D;
      case D: return Face.U;
      case L: return Face.R;
      case R: return Face.L;
      case F: return Face.B;
      default: return Face.F;
    }
  }
}
