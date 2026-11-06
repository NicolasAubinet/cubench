package com.cube.nanotimer.coach;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * How many moves a solver's crosses take against the fewest each scramble allowed on the same face
 * ({@link CrossTurn}), over the family window's plain crosses. X-crosses are counted apart and left
 * out: their moves are a pair's too.
 *
 * <p>Measured on the owner's 264 plain crosses (2026-10-30): 5.82 optimal, 7.64 turned, so 1.82
 * over; 26% were 3 or more over; 1 X-cross. The optimal matches the published 5.8 for one colour.
 */
public final class CrossMoveFigure {

  /** Moves over the optimal from which a cross counts as a long one. */
  public static final int LONG_EXTRA = 3;

  private final int solves;
  private final int xCrosses;
  private final double turnedMoves;
  private final double optimalMoves;
  private final int longCrosses;

  CrossMoveFigure(int solves, int xCrosses, double turnedMoves, double optimalMoves,
      int longCrosses) {
    this.solves = solves;
    this.xCrosses = xCrosses;
    this.turnedMoves = turnedMoves;
    this.optimalMoves = optimalMoves;
    this.longCrosses = longCrosses;
  }

  /** The figure over these crosses, or null under {@code floor} plain ones. */
  static CrossMoveFigure of(List<CrossTurn> crosses, int floor) {
    int plain = 0;
    int xCrosses = 0;
    int turned = 0;
    int optimal = 0;
    int longCrosses = 0;
    for (CrossTurn cross : crosses) {
      if (cross.isXCross()) {
        xCrosses++;
        continue;
      }
      plain++;
      turned += cross.getTurnedMoves();
      optimal += cross.getOptimalMoves();
      longCrosses += cross.getExtraMoves() >= LONG_EXTRA ? 1 : 0;
    }
    if (plain < floor || plain == 0) {
      return null;
    }
    return new CrossMoveFigure(plain, xCrosses, tenths((double) turned / plain),
        tenths((double) optimal / plain), longCrosses);
  }

  private static double tenths(double value) {
    return Math.round(value * 10) / 10.0;
  }

  /** Plain crosses counted. */
  public int getSolves() {
    return solves;
  }

  /** X-crosses seen in the window and left out. */
  public int getXCrosses() {
    return xCrosses;
  }

  /** Mean HTM a plain cross took. */
  public double getTurnedMoves() {
    return turnedMoves;
  }

  /** Mean fewest moves the same crosses could have taken. */
  public double getOptimalMoves() {
    return optimalMoves;
  }

  /** Mean moves over the optimal: the two means' difference, both off the same crosses. */
  public double getExtraMoves() {
    return tenths(turnedMoves - optimalMoves);
  }

  /** Plain crosses {@link #LONG_EXTRA} or more moves over their optimal. */
  public int getLongCrosses() {
    return longCrosses;
  }

  /** A field by its payload name, or null for a name it has not. */
  public Double field(String name) {
    if ("solves".equals(name)) {
      return Double.valueOf(solves);
    }
    if ("x_crosses".equals(name)) {
      return Double.valueOf(xCrosses);
    }
    if ("turned_moves".equals(name)) {
      return Double.valueOf(turnedMoves);
    }
    if ("optimal_moves".equals(name)) {
      return Double.valueOf(optimalMoves);
    }
    if ("extra_moves".equals(name)) {
      return Double.valueOf(getExtraMoves());
    }
    if ("long_crosses".equals(name)) {
      return Double.valueOf(longCrosses);
    }
    return null;
  }

  public JSONObject toJson() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("solves", solves);
    json.put("x_crosses", xCrosses);
    json.put("turned_moves", turnedMoves);
    json.put("optimal_moves", optimalMoves);
    json.put("extra_moves", getExtraMoves());
    json.put("long_crosses", longCrosses);
    return json;
  }

  public static CrossMoveFigure fromJson(JSONObject json) throws JSONException {
    return new CrossMoveFigure(json.getInt("solves"), json.getInt("x_crosses"),
        json.getDouble("turned_moves"), json.getDouble("optimal_moves"),
        json.getInt("long_crosses"));
  }
}
