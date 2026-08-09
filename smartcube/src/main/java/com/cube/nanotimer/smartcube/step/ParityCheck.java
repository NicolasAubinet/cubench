package com.cube.nanotimer.smartcube.step;

/**
 * Whether the parity was the one the scramble asked for, said of a solve left on a two-and-two swap.
 *
 * <p>That swap is an odd permutation, and an odd permutation is exactly what one parity algorithm
 * fixes: nothing built out of three-cycles can reach it or leave it. So the scramble's own
 * permutation settles which of the two mistakes was made, whatever else the solve did.
 *
 * <p>It is worth saying on its own because it is the one mistake nothing else here can point at. A
 * parity never done leaves pieces out that no algorithm ever claimed, so the marking deliberately
 * blames nobody for it and the verdict line says the shape without saying whose fault it is.
 */
public enum ParityCheck {
  /** The scramble needed a parity and none was read: the swap left over is that parity, still owed. */
  SKIPPED,
  /** The scramble needed no parity, so the swap left over was put there by an algorithm nothing
   * called for. */
  NEEDLESS,
}
