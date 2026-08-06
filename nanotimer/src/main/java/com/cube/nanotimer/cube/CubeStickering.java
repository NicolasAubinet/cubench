package com.cube.nanotimer.cube;

/**
 * Which stickers of the drawn cube keep their colour, in the form the twisty player takes.
 *
 * <p><b>A mask names pieces, not the slots they are in.</b> The player holds one object per piece
 * and moves it to wherever that piece currently sits, so the mask rides along and a cross drawn on a
 * scramble stays drawn on the same four edges as they are solved. The two readings agree on a solved
 * cube and on nothing else, which is exactly the way round to get this wrong quietly.
 *
 * <p>Each piece carries a run of five values, one per orientation index, which is what the library's
 * own default mask does. Five covers every orbit on every puzzle it draws, and a piece whose entry
 * is short of the orientation being asked for is what it cannot survive.
 */
public final class CubeStickering {

  private static final int EDGES = 12;
  private static final int CORNERS = 8;
  private static final int CENTERS = 6;

  private static final String SHOWN = "regular";
  private static final String GREY = "ignored";

  private CubeStickering() {
  }

  /** Every sticker in its own colour, which is what a cube not being masked looks like. */
  public static String full() {
    return mask(all(EDGES, true), all(CORNERS, true), all(CENTERS, true));
  }

  /** Nothing readable: the cube is there, and says nothing about where anything is. */
  public static String allGrey() {
    return mask(all(EDGES, false), all(CORNERS, false), all(CENTERS, false));
  }

  /**
   * The four edges of a cross and the six centres, everything else grey.
   *
   * <p>The centres are not decoration. With them grey there is nothing to say which face an edge
   * belongs to, so the case cannot be read at all.
   *
   * @param crossEdges the four edges, by the slot each belongs in, in
   *     {@link com.cube.nanotimer.smartcube.cube.CubieCube}'s numbering
   */
  public static String crossAndCentres(int[] crossEdges) {
    boolean[] edges = new boolean[EDGES];
    for (int edge : crossEdges) {
      edges[CubePatternFormat.playerEdge(edge)] = true;
    }
    return mask(edges, all(CORNERS, false), all(CENTERS, true));
  }

  private static boolean[] all(int count, boolean shown) {
    boolean[] pieces = new boolean[count];
    for (int i = 0; i < count; i++) {
      pieces[i] = shown;
    }
    return pieces;
  }

  private static String mask(boolean[] edges, boolean[] corners, boolean[] centers) {
    StringBuilder sb = new StringBuilder(1200);
    sb.append("{\"orbits\":{\"EDGES\":");
    appendOrbit(sb, edges);
    sb.append(",\"CORNERS\":");
    appendOrbit(sb, corners);
    sb.append(",\"CENTERS\":");
    appendOrbit(sb, centers);
    sb.append("}}");
    return sb.toString();
  }

  private static void appendOrbit(StringBuilder sb, boolean[] pieces) {
    sb.append("{\"pieces\":[");
    for (int i = 0; i < pieces.length; i++) {
      String value = pieces[i] ? SHOWN : GREY;
      sb.append(i == 0 ? "" : ",").append("{\"facelets\":[");
      for (int orientation = 0; orientation < 5; orientation++) {
        sb.append(orientation == 0 ? "" : ",").append('"').append(value).append('"');
      }
      sb.append("]}");
    }
    sb.append("]}");
  }
}
