package com.cube.nanotimer.smartcube.model;

/**
 * A full snapshot of the cube's state, backed by the 54-character facelet string in
 * URFDLB face order (the representation csTimer's toFaceCube() produces). Consumers diff
 * and compare these snapshots.
 */
public final class CubeState {

  public static final String SOLVED_FACELETS =
      "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";

  public static final CubeState SOLVED = new CubeState(SOLVED_FACELETS);

  private final String facelets;

  public CubeState(String facelets) {
    this.facelets = facelets;
  }

  /** 54 facelet colours, faces in URFDLB order, 9 stickers each. */
  public String getFacelets() {
    return facelets;
  }

  public boolean isSolved() {
    return SOLVED_FACELETS.equals(facelets);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CubeState && ((CubeState) other).facelets.equals(facelets);
  }

  @Override
  public int hashCode() {
    return facelets.hashCode();
  }

  @Override
  public String toString() {
    return "CubeState(" + facelets + ")";
  }
}
