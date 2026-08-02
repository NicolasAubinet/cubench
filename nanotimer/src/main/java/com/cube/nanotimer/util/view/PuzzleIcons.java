package com.cube.nanotimer.util.view;

import com.cube.nanotimer.R;
import com.cube.nanotimer.vo.CubeType;

/**
 * The mark for a puzzle: its own face drawn as its cuts, and its own colour. A cube is the face and
 * its grid, which is what tells a 3x3 from a 5x5 at a glance; the rest are the shapes they are.
 */
public final class PuzzleIcons {

  private PuzzleIcons() {
  }

  /** The hue this puzzle wears wherever it is named. */
  public static int colorForCubeType(CubeType cubeType) {
    if (cubeType == null) {
      return R.color.puzzle_3x3;
    }
    switch (cubeType) {
      case TWO_BY_TWO:
        return R.color.puzzle_2x2;
      case FOUR_BY_FOUR:
        return R.color.puzzle_4x4;
      case FIVE_BY_FIVE:
        return R.color.puzzle_5x5;
      case SIX_BY_SIX:
        return R.color.puzzle_6x6;
      case SEVEN_BY_SEVEN:
        return R.color.puzzle_7x7;
      case MEGAMINX:
        return R.color.puzzle_megaminx;
      case FTO:
        return R.color.puzzle_fto;
      case PYRAMINX:
        return R.color.puzzle_pyraminx;
      case SKEWB:
        return R.color.puzzle_skewb;
      case SQUARE1:
        return R.color.puzzle_square1;
      case CLOCK:
        return R.color.puzzle_clock;
      default:
        return R.color.puzzle_3x3;
    }
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
