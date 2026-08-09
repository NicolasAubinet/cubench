package com.cube.nanotimer.vo;

/**
 * What became of one piece a breakdown step's name says it shot at, which is what colours that piece
 * when the step is shown. The datamodel counterpart of the smart cube analyzer's own mark, decoupled
 * from it the same way the step itself is.
 */
public enum PieceMark {
  /** The algorithm put it where it belongs, in position and orientation. */
  HOME,
  /** The algorithm moved it without putting it home, which on its own is what an open cycle looks
   * like. */
  TOUCHED,
  /** What the solve was left with is exactly what this algorithm named, so this is where it went
   * wrong. */
  WRONG,
}
