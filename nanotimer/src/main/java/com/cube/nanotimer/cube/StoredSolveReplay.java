package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.step.SolveAnalyzer;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import java.util.List;

/**
 * Reads a stored solve's breakdown again, from its scramble and its moves, rather than trusting the
 * one written when it was recorded.
 *
 * <p>The breakdown is not a fact about a solve, it is a conclusion drawn from one: the scramble and
 * the move stream are the record, and which method those moves fitted — and where its steps fell —
 * follows from them. So a solve type that changes its method, or a detector that gets better, applies
 * to the history and not only to what is solved next.
 *
 * <p><b>What this cannot recover.</b> The rotation tokens in the stored stream were resolved from the
 * gyro when the solve was recorded, and no gyro reading is kept. A fix to how rotations are
 * <em>derived</em> is therefore invisible here — only what is read <em>from</em> the stored stream
 * changes. Detectors read states and never move letters, so they are unaffected by this either way.
 *
 * <p><b>The one assumption it makes that the live reading does not.</b> Live, the analysis starts
 * from the state the cube reports; here it starts from the scramble applied to a solved cube. Those
 * agree only if the scramble was really performed — guaranteed for a followed scramble, which is
 * checked to completion before the timer arms, but not for a solve type whose scramble cannot be
 * followed. A walk from the wrong state almost always fits no method and falls back on its own; the
 * solved-cube check below catches the rest, so a wrong reading is never preferred to the stored one.
 */
public final class StoredSolveReplay {

  private StoredSolveReplay() {
  }

  /** A breakdown read afresh: which method the moves fitted, and how far they got. */
  public static final class Result {
    private final CubeMethod method;
    private final List<SolveStep> steps;
    private final Integer stoppedStep;

    Result(CubeMethod method, List<SolveStep> steps, Integer stoppedStep) {
      this.method = method;
      this.steps = steps;
      this.stoppedStep = stoppedStep;
    }

    public CubeMethod getMethod() {
      return method;
    }

    public List<SolveStep> getSteps() {
      return steps;
    }

    public Integer getStoppedStep() {
      return stoppedStep;
    }
  }

  /**
   * Null when the solve cannot be read again — no moves, no scramble, a scramble that is not a 3x3
   * one, or a blind solve, whose detector needs the grip it was memorised in and that is not stored.
   * The caller shows what was recorded in that case; nothing is worse off than before.
   *
   * @param expected the solve type's method, which settles a solve fitting several. A method the
   *     moves do not fit is not imposed here any more than it is live.
   */
  public static Result reinterpret(String scramble, String storedMoves, CubeMethod expected) {
    if (scramble == null || storedMoves == null || storedMoves.isEmpty()
        || expected == CubeMethod.BLIND) {
      return null;
    }
    try {
      CubieCube cube = new CubieCube();
      cube.fromFacelet(CubieCube.SOLVED_FACELET);
      for (String token : scramble.trim().split("\\s+")) {
        if (!isFaceTurn(token)) {
          return null; // another puzzle's notation: its letters would walk a 3x3 somewhere arbitrary
        }
        apply(cube, token);
      }
      MethodAnalyzers analyzers = new MethodAnalyzers(false);
      analyzers.start(new CubeState(cube.toFaceCube()), 0);
      for (SolveMovesFormat.Move move : SolveMovesFormat.parse(storedMoves)) {
        String notation = move.getNotation();
        if (SolveMovesFormat.isRotation(notation)) {
          continue; // the detectors read states, in which a whole-cube rotation is not a change
        }
        analyzers.onMove(new CubeMove(face(notation), prime(notation), move.getOffsetMs()));
        apply(cube, notation);
        analyzers.onState(new CubeState(cube.toFaceCube()));
      }
      CubeMethod method = analyzers.resolve(expected);
      if (method == null) {
        return null; // the moves fit no method we know: keep what was recorded rather than empty it
      }
      SolveAnalyzer analyzer = analyzers.get(method);
      Integer stoppedStep = analyzer.getStoppedStep();
      // A reading that says the solve ran to the end has to end on a solved cube. Where it does not,
      // the walk started somewhere the solve did not — see the start-state note on the class.
      if (stoppedStep == null && !cube.isSolved()) {
        return null;
      }
      return new Result(method, SolveStepConverter.toSolveSteps(analyzer.getStepTimes()),
          stoppedStep);
    } catch (RuntimeException e) {
      return null; // a scramble in another puzzle's notation, a truncated stream: fall back
    }
  }

  /** A 3x3 face turn and nothing else: {@code R}, {@code R'} or {@code R2}. */
  private static boolean isFaceTurn(String token) {
    if (token.isEmpty() || "UDLRFB".indexOf(token.charAt(0)) < 0) {
      return false;
    }
    return token.length() == 1
        || (token.length() == 2 && (token.endsWith("'") || token.endsWith("2")));
  }

  private static void apply(CubieCube cube, String token) {
    for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
      cube.applyMove(face(token), prime(token));
    }
  }

  private static Face face(String token) {
    return Face.valueOf(token.substring(0, 1));
  }

  private static boolean prime(String token) {
    return token.endsWith("'");
  }
}
