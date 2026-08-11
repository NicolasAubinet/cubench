package com.cube.nanotimer.smartcube.step;

/**
 * Where a solve stopped being readable, and how much turning went unread after it.
 *
 * <p>An algorithm executed wrongly but cleanly still moves three pieces, so it lands and is read,
 * wrongly named and all. One executed <em>badly</em> leaves the cube nowhere near a landing, and
 * nothing after it can be read at all: no algorithm is recognised, no name is given, and the
 * breakdown simply stops. That silence is itself the diagnosis, and saying it is the point of this
 * class. Without it a table that ends early reads as a solve that ended early.
 *
 * <p>It is also what stops the marking lying. A piece left out is blamed on the algorithms that
 * shot at it, which is only honest while every move is accounted for: past this point the unread
 * turning could have put it right, so nothing is blamed on anything.
 */
public final class LostReading {

  private final String after;
  private final int moves;

  LostReading(String after, int moves) {
    this.after = after;
    this.moves = moves;
  }

  /** The name code of the last algorithm read, which the display localizes as it does any other. */
  public String getAfter() {
    return after;
  }

  /** How many moves were made past it without anything being read from them. */
  public int getMoves() {
    return moves;
  }
}
