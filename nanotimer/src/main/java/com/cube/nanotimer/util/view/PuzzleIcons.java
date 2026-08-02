package com.cube.nanotimer.util.view;

import com.cube.nanotimer.R;
import com.cube.nanotimer.vo.CubeType;

/**
 * The mark for a puzzle: its own face, drawn as its cuts. A cube is the face and its grid, which is
 * what tells a 3x3 from a 5x5 at a glance; the rest are the shapes they are.
 */
public final class PuzzleIcons {

  private PuzzleIcons() {
  }

  public static int forCubeType(CubeType cubeType) {
    if (cubeType == null) {
      return R.drawable.ic_puzzle_3x3;
    }
    switch (cubeType) {
      case TWO_BY_TWO:
        return R.drawable.ic_puzzle_2x2;
      case FOUR_BY_FOUR:
        return R.drawable.ic_puzzle_4x4;
      case FIVE_BY_FIVE:
        return R.drawable.ic_puzzle_5x5;
      case SIX_BY_SIX:
        return R.drawable.ic_puzzle_6x6;
      case SEVEN_BY_SEVEN:
        return R.drawable.ic_puzzle_7x7;
      case MEGAMINX:
        return R.drawable.ic_puzzle_megaminx;
      case FTO:
        return R.drawable.ic_puzzle_fto;
      case PYRAMINX:
        return R.drawable.ic_puzzle_pyraminx;
      case SKEWB:
        return R.drawable.ic_puzzle_skewb;
      case SQUARE1:
        return R.drawable.ic_puzzle_square1;
      case CLOCK:
        return R.drawable.ic_puzzle_clock;
      default:
        return R.drawable.ic_puzzle_3x3;
    }
  }
}
