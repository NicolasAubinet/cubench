package com.cube.nanotimer.scrambler.cross;

import com.cube.nanotimer.Options.CrossNeutrality;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates per-face {@link CrossSolver}s and the three color-neutrality modes.
 *
 * <ul>
 *   <li><b>Specific</b> &mdash; one chosen face (fixed-color solver).</li>
 *   <li><b>Dual</b> &mdash; the chosen face and its opposite (dual color neutral).</li>
 *   <li><b>Full</b> &mdash; all six faces (full color neutral).</li>
 * </ul>
 *
 * <p>For Dual/Full the eligible faces are returned sorted by optimal cross length &mdash; the
 * "which color is easiest this scramble" comparison CN solvers train with. Per-face tables are
 * built lazily on first use and cached.
 */
public class CrossSolvers {

  private final Map<CrossFace, CrossSolver> byFace = new EnumMap<>(CrossFace.class);

  /** All optimal solutions for one face's cross, with their (shared, optimal) length. */
  public static class FaceSolutions {
    public final CrossFace face;
    public final int length;
    public final List<String[]> solutions;

    FaceSolutions(CrossFace face, List<String[]> solutions) {
      this.face = face;
      this.length = solutions.isEmpty() ? 0 : solutions.get(0).length;
      this.solutions = solutions;
    }
  }

  private CrossSolver forFace(CrossFace face) {
    CrossSolver solver = byFace.get(face);
    if (solver == null) {
      solver = new CrossSolver(face.getCrossPieces());
      byFace.put(face, solver);
    }
    return solver;
  }

  /** The optimal cross length (HTM) on a single face, without listing the solutions. */
  public synchronized int optimalLength(CrossFace face, String scramble) {
    return forFace(face).optimalLength(scramble);
  }

  /** All optimal solutions for a single face. */
  public FaceSolutions solveFace(CrossFace face, String scramble) {
    return new FaceSolutions(face, forFace(face).solve(scramble));
  }

  /**
   * Solves the eligible faces for the given mode and chosen face, sorted by optimal length
   * (shortest first; ties broken by face order). For Specific the list holds the chosen face only.
   */
  public List<FaceSolutions> solve(String scramble, CrossNeutrality mode, CrossFace chosen) {
    List<FaceSolutions> results = new ArrayList<>();
    for (CrossFace face : eligibleFaces(mode, chosen)) {
      results.add(solveFace(face, scramble));
    }
    if (mode != CrossNeutrality.SPECIFIC) {
      results.sort(Comparator.comparingInt(r -> r.length));
    }
    return results;
  }

  static List<CrossFace> eligibleFaces(CrossNeutrality mode, CrossFace chosen) {
    switch (mode) {
      case SPECIFIC:
        return Collections.singletonList(chosen);
      case DUAL:
        List<CrossFace> dual = new ArrayList<>(2);
        dual.add(chosen);
        dual.add(chosen.opposite());
        return dual;
      case FULL:
      default:
        List<CrossFace> all = new ArrayList<>(6);
        Collections.addAll(all, CrossFace.values());
        return all;
    }
  }
}
