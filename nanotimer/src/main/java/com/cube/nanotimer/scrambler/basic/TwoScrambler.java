package com.cube.nanotimer.scrambler.basic;

import com.cube.nanotimer.scrambler.randomstate.RSTwoScrambler;

/**
 * A random-move 2x2 scramble, turning three mutually adjacent faces only.
 *
 * <p>A 2x2 has no fixed centres, so an opposite face is the near one up to a whole-cube rotation:
 * D is U turned the other way once the cube is held differently, which makes U' D exactly a y' and
 * nothing at all to the puzzle. Turning all six faces therefore spends moves on rotations rather
 * than on scrambling, and a scramble that reads U' D asks for a regrip that buys nothing.
 *
 * <p>Eleven moves, the length of an official scramble and of the random-state ones this stands in
 * for: three generators need more moves than six did to reach as scrambled a state.
 */
public class TwoScrambler extends AbstractCubeScrambler {

  @Override
  protected String[][] getMoves() {
    return new String[][]{
        {"U"},
        {"R"},
        {"F"}
    };
  }

  @Override
  protected int getMoveCount() {
    return RSTwoScrambler.SCRAMBLE_LENGTH;
  }
}
