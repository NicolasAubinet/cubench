package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeState;

/**
 * The standing difference between where a cube says it is and where it really is, applied to every
 * state it reports.
 *
 * <p>A cube that cannot be told it is solved (see {@code SmartCube.supportsStateReset}) tracks its
 * own state from an anchor of its own, and hands that back on every connect. Once that anchor is
 * wrong (a corner twisted by hand, a turn its sensors read wrong, an anchor taken while the cube was
 * not solved), no amount of reconnecting helps, and the app would draw a cube the solver is not
 * holding.
 *
 * <p><b>Why one stored state is enough.</b> Both models see the same turns, so {@code real ·
 * reported⁻¹} never changes, because the turns cancel on the right. Capture it once, at the moment the
 * solver says the cube is solved and {@code real} is therefore the identity, and it corrects every
 * later reading. What caused the drift does not matter, only that both sides have seen the same
 * turns since.
 */
public final class CubeStateCorrection {

  private static final CubeStateCorrection NONE = new CubeStateCorrection(null);

  /** {@code real · reported⁻¹}, or null where nothing needs correcting. */
  private final CubieCube offset;

  private CubeStateCorrection(CubieCube offset) {
    this.offset = offset;
  }

  /** Report states as the cube gives them. */
  public static CubeStateCorrection none() {
    return NONE;
  }

  /**
   * The correction that makes {@code reported} read as solved, for a cube the solver has just said
   * is solved. A cube already reporting solved needs none.
   */
  public static CubeStateCorrection capturedFrom(CubeState reported) {
    CubieCube cube = parse(reported);
    if (cube == null || cube.isSolved()) {
      return NONE;
    }
    return new CubeStateCorrection(cube.inverse());
  }

  /** A stored correction, by the facelets {@link #getFacelets} wrote. */
  public static CubeStateCorrection stored(String facelets) {
    CubieCube cube = parse(facelets == null ? null : new CubeState(facelets));
    return cube == null || cube.isSolved() ? NONE : new CubeStateCorrection(cube);
  }

  public boolean isNone() {
    return offset == null;
  }

  /** The correction itself as facelets, to be handed back to {@link #stored}; null where none. */
  public String getFacelets() {
    return offset == null ? null : offset.toFaceCube();
  }

  /** {@code reported} as the cube really is. Unparseable states are passed through untouched. */
  public CubeState apply(CubeState reported) {
    if (offset == null || reported == null) {
      return reported;
    }
    CubieCube cube = parse(reported);
    return cube == null ? reported : new CubeState(offset.multiply(cube).toFaceCube());
  }

  private static CubieCube parse(CubeState state) {
    if (state == null || state.getFacelets() == null) {
      return null;
    }
    CubieCube cube = new CubieCube();
    return cube.fromFacelet(state.getFacelets()) ? cube : null;
  }
}
