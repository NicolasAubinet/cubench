package com.cube.nanotimer.util.view;

import com.cube.nanotimer.R;
import com.cube.nanotimer.vo.SolveType;

/**
 * The mark for a solve type. Its name is the user's own words, so the only thing about it a screen
 * can say without being told is what kind it is: timed plainly, timed blind, or timed in steps.
 * That is what the icon draws and what the colour says, wherever a solve type is named.
 */
public final class SolveTypeIcons {

  private SolveTypeIcons() {
  }

  public static int forSolveType(SolveType solveType) {
    if (solveType == null) {
      return R.drawable.ic_solvetype_normal;
    }
    if (solveType.isBlind()) {
      return R.drawable.ic_solvetype_blind;
    }
    return solveType.hasSteps() ? R.drawable.ic_solvetype_steps : R.drawable.ic_solvetype_normal;
  }

  public static int colorForSolveType(SolveType solveType) {
    if (solveType == null) {
      return R.color.solvetype_plain;
    }
    if (solveType.isBlind()) {
      return R.color.solvetype_blind;
    }
    return solveType.hasSteps() ? R.color.solvetype_steps : R.color.solvetype_plain;
  }
}
