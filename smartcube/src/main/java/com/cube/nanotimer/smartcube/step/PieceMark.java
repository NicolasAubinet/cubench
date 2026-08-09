package com.cube.nanotimer.smartcube.step;

/**
 * What became of one piece an algorithm's name says it shot at, which is what colours that piece in
 * the breakdown.
 *
 * <p>The two marks answer different questions and both are worth saying. {@link #HOME} is what the
 * algorithm did at the time, and stands whatever happens later. {@link #WRONG} is what the cube says
 * at the end, and it is laid on an algorithm only where the solve was left with exactly the pieces
 * that algorithm named. A piece can carry one in an early algorithm and the other in a late one, and
 * that pair reads as what it is: you had it, and then you broke it.
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
