package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.step.BlindStepDetector;
import com.cube.nanotimer.smartcube.step.CFOPStepDetector;
import com.cube.nanotimer.smartcube.step.LblStepDetector;
import com.cube.nanotimer.smartcube.step.RouxStepDetector;
import com.cube.nanotimer.smartcube.step.SolveAnalyzer;
import com.cube.nanotimer.vo.CubeMethod;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs the detector of the method the solve type is read as, and says whether the solve fitted it.
 *
 * <p><b>The method is declared, never guessed.</b> A solve type says how its solves are solved —
 * from its blind flag, its own method, or the preferred one — and a solve either bears that out or
 * is left unread. A Roux solve done on a CFOP solve type is not a Roux solve to be filed as one: it
 * is a solve on the wrong solve type, and a breakdown that quietly disagrees with what the type
 * says would be worse than none. Nothing is resolved between methods, so nothing has to be settled
 * when several would fit — which they often would, CFOP's fit being that <em>some</em> face's cross
 * preceded that face's first two layers, satisfied by a Roux first block on the left or right.
 *
 * <p>The moves are kept either way: an unrecognised solve still has a solution worth having.
 */
public final class MethodAnalyzers {

  private final Map<CubeMethod, SolveAnalyzer> analyzers =
      new LinkedHashMap<CubeMethod, SolveAnalyzer>();
  private BlindStepDetector blindDetector;

  /**
   * @param method the method this solve type's solves are read as. A blind solve is not a sighted
   *     method read through a blindfold — it is memorised first and its steps are the piece types
   *     — which is why each method brings its own detector and no other one runs.
   */
  public MethodAnalyzers(CubeMethod method) {
    if (method == CubeMethod.BLIND) {
      this.blindDetector = new BlindStepDetector();
      analyzers.put(CubeMethod.BLIND, new SolveAnalyzer(blindDetector));
    } else if (method == CubeMethod.ROUX) {
      analyzers.put(CubeMethod.ROUX, new SolveAnalyzer(new RouxStepDetector()));
    } else if (method == CubeMethod.LBL) {
      analyzers.put(CubeMethod.LBL, new SolveAnalyzer(new LblStepDetector()));
    } else {
      analyzers.put(CubeMethod.CFOP, new SolveAnalyzer(new CFOPStepDetector()));
    }
  }

  /** Blind names its targets in the grip they were memorised in; no sighted detector wants this. */
  public void setPickupRotation(CubeRotation pickup) {
    if (blindDetector != null) {
      blindDetector.setPickupRotation(pickup);
    }
  }

  public void start(CubeState startState, long startTimestampMs) {
    for (SolveAnalyzer analyzer : analyzers.values()) {
      analyzer.start(startState, startTimestampMs);
    }
  }

  public void onMove(CubeMove move) {
    for (SolveAnalyzer analyzer : analyzers.values()) {
      analyzer.onMove(move);
    }
  }

  public void onState(CubeState state) {
    for (SolveAnalyzer analyzer : analyzers.values()) {
      analyzer.onState(state);
    }
  }

  /** The method the solve was read as, or null where the solve did not bear it out. */
  public CubeMethod resolve() {
    for (Map.Entry<CubeMethod, SolveAnalyzer> entry : analyzers.entrySet()) {
      if (entry.getValue().matchesMethod()) {
        return entry.getKey();
      }
    }
    return null;
  }

  public SolveAnalyzer get(CubeMethod method) {
    return analyzers.get(method);
  }

  /** Any of them: the moves are the one stream, whichever method read them. */
  public SolveAnalyzer moves() {
    return analyzers.values().iterator().next();
  }
}
