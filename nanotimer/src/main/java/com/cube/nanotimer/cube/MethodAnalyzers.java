package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.step.BlindStepDetector;
import com.cube.nanotimer.smartcube.step.CFOPStepDetector;
import com.cube.nanotimer.smartcube.step.RouxStepDetector;
import com.cube.nanotimer.smartcube.step.SolveAnalyzer;
import com.cube.nanotimer.vo.CubeMethod;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs every method's detector over the one solve, so the method is read off the solve instead of
 * being configured. The detectors are pure and cheap, and they see the same moves and states.
 *
 * <p>Exactly one fitting is the answer. None fitting is not a method we know, so no breakdown at
 * all — the alternative would invent one.
 *
 * <p><b>Several fitting is settled by how much each fit is worth, not by a coin toss.</b> The fits
 * are not equally strong. CFOP's is that <em>some</em> face's cross preceded that face's first two
 * layers, and it picks the face from all six — a Roux solve satisfies that on the left or right
 * face, three quarters of whose cross comes with a first block. Roux's fit is a far narrower claim:
 * two blocks in order, with the middle slice still open when they were finished, which held on 1 of
 * 71 recorded CFOP solves. So the detectors are kept in order of how discriminating their fit is,
 * narrowest first, and a tie goes to the first — unless the solve was expected to be a particular
 * method, which beats a guess.
 */
public final class MethodAnalyzers {

  private final Map<CubeMethod, SolveAnalyzer> analyzers =
      new LinkedHashMap<CubeMethod, SolveAnalyzer>();
  private BlindStepDetector blindDetector;

  /**
   * @param blind whether the solve type is a blindfolded one. A blind solve is not a sighted method
   *     read through a blindfold: it is memorised first and its steps are the piece types, so the
   *     sighted detectors have nothing to say about it and are not run at all. There is nothing to
   *     resolve between either — the solve type declares this, it is not being guessed at.
   */
  public MethodAnalyzers(boolean blind) {
    if (blind) {
      this.blindDetector = new BlindStepDetector();
      analyzers.put(CubeMethod.BLIND, new SolveAnalyzer(blindDetector));
      return;
    }
    analyzers.put(CubeMethod.ROUX, new SolveAnalyzer(new RouxStepDetector()));
    analyzers.put(CubeMethod.CFOP, new SolveAnalyzer(new CFOPStepDetector()));
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

  /**
   * The method the solve fitted, or null for none.
   *
   * @param expected the method the solve was expected to be solved with, or null for no
   *     expectation. It settles a solve that fits several, but only when it is one of them: a
   *     method the solve does not fit is no evidence of anything, however it was configured.
   */
  public CubeMethod resolve(CubeMethod expected) {
    List<CubeMethod> fitted = new ArrayList<CubeMethod>();
    for (Map.Entry<CubeMethod, SolveAnalyzer> entry : analyzers.entrySet()) {
      if (entry.getValue().matchesMethod()) {
        fitted.add(entry.getKey());
      }
    }
    if (fitted.isEmpty()) {
      return null;
    }
    if (fitted.size() == 1) {
      return fitted.get(0);
    }
    return fitted.contains(expected) ? expected : fitted.get(0);
  }

  public SolveAnalyzer get(CubeMethod method) {
    return analyzers.get(method);
  }

  /** Any of them: the moves are the one stream, whichever method read them. */
  public SolveAnalyzer moves() {
    return analyzers.values().iterator().next();
  }
}
