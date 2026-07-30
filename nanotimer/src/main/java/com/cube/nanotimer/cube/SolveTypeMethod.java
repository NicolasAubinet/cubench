package com.cube.nanotimer.cube;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveType;

/**
 * Which method a solve type's solves are read as, resolved rather than stored: a type that names one
 * is solved that way, and every other one follows the preferred method. Keeping the preference the
 * only copy is what lets changing it re-read the solves of every type that did not override it.
 *
 * <p>It lives here rather than on {@link SolveType} because the answer needs {@link Options}, which
 * the model module cannot see.
 */
public final class SolveTypeMethod {

  private SolveTypeMethod() {
  }

  /** Never null: a breakdown read under no method at all is a guess, which is not on offer. */
  public static CubeMethod of(SolveType solveType) {
    if (solveType.isBlind()) {
      // Memorised first, and its steps are the piece types: the blind flag settles this, not the column.
      return CubeMethod.BLIND;
    }
    CubeMethod override = solveType.getMethodOverride();
    return override != null ? override : Options.INSTANCE.getPreferredMethod();
  }
}
