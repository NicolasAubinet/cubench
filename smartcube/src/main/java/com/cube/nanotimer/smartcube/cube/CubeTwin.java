package com.cube.nanotimer.smartcube.cube;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;

/**
 * The cube as its reported turns say it stands, checked against the state it says it is really in,
 * so that a screen drawn from those turns can be put right when one of them never arrived.
 *
 * <p>A cube that reports both is authoritative about the state and only informative about the
 * turns: a QiYi skips a state change during fast slices, which is why an H perm turned on M2 left
 * a drill's cube scrambled for the rest of the rep. What was missed is the difference between the
 * two, and it comes back as a state to apply <em>on top of</em> whatever is being drawn, since a
 * drill's cube stands in a frame of its own and the same missing turns belong to it just as much.
 *
 * <p>Nothing is assumed before the first state: until a cube has said where it stands there is no
 * telling what it might have missed, and the turns since are the caller's own to have applied.
 */
public final class CubeTwin {

  /** Null until a state has been seen, which is what makes the first one a seed and not a repair. */
  private CubieCube known;

  /** Forgets where the cube stood, for a caller starting again from whatever it says next. */
  public void reset() {
    known = null;
  }

  /** Every turn the cube reports, including any the caller drops: this one follows the cube. */
  public void onMove(CubeMove move) {
    if (known != null) {
      known.applyMove(move.getFace(), move.isPrime());
    }
  }

  /**
   * The turns missed since the last state, as a state to apply on top of any cube that took the
   * same ones, or null when nothing was missed. Facelets that will not parse are dropped rather
   * than guessed at, and leave the twin where it was.
   */
  public CubieCube missing(CubeState state) {
    CubieCube reported = new CubieCube();
    if (state == null || !reported.fromFacelet(state.getFacelets())) {
      return null;
    }
    CubieCube before = known;
    known = reported;
    return before == null || before.equals(reported)
        ? null : before.inverse().multiply(reported);
  }
}
