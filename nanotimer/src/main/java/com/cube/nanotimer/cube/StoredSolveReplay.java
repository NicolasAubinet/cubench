package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.step.BlindResidual;
import com.cube.nanotimer.smartcube.step.LostReading;
import com.cube.nanotimer.smartcube.step.ParityCheck;
import com.cube.nanotimer.smartcube.step.SolveAnalyzer;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import java.util.Collections;
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
 * <p>The one gyro answer that <em>is</em> kept is the grip the solve was picked up in, written in
 * front of the stored moves because a blind solve's targets are spelled through it and no move
 * token gives it back: every token is dated at a move, and a slice opening the solve has already
 * carried the core round by the time the first one is written. A blind solve recorded before the
 * grip was kept is read again by nobody, which is why one is refused rather than guessed at.
 *
 * <p><b>The one assumption it makes that the live reading does not.</b> Live, the analysis starts
 * from the state the cube reports; here it starts from the scramble applied to a solved cube. Those
 * agree only if the scramble was really performed — guaranteed for a followed scramble, which is
 * checked to completion before the timer arms, but not for a solve type whose scramble cannot be
 * followed. A walk from the wrong state almost always fits no method and falls back on its own; the
 * reached-solved check below catches the rest, so a wrong reading is never preferred to the stored one.
 */
public final class StoredSolveReplay {

  private StoredSolveReplay() {
  }

  /** A breakdown read afresh: which method the moves fitted, and how far they got. */
  public static final class Result {
    private final CubeMethod method;
    private final List<SolveStep> steps;
    private final Integer stoppedStep;
    private final BlindResidual residual;
    private final LostReading lostReading;
    private final ParityCheck parityCheck;
    private final boolean reachedSolved;

    Result(CubeMethod method, List<SolveStep> steps, Integer stoppedStep,
        BlindResidual residual, LostReading lostReading, ParityCheck parityCheck,
        boolean reachedSolved) {
      this.method = method;
      this.steps = steps;
      this.stoppedStep = stoppedStep;
      this.residual = residual;
      this.lostReading = lostReading;
      this.parityCheck = parityCheck;
      this.reachedSolved = reachedSolved;
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

    /** What the cube was left in, where the method can say it: never stored, always read again. */
    public BlindResidual getResidual() {
      return residual;
    }

    /** Where the reading stopped short of the turning, or null where it did not. */
    public LostReading getLostReading() {
      return lostReading;
    }

    /** Whether the parity was the one the scramble asked for, or null where nothing is certain. */
    public ParityCheck getParityCheck() {
      return parityCheck;
    }

    /**
     * Whether the replayed moves brought the cube out solved, which is what says the walk started
     * where the solve did. It is the difference between a solve that genuinely fits no method and
     * one this cannot judge: a walk from the wrong state fits none either, and so does a solve that
     * was abandoned part way. Only a solve that came out solved and still fitted nothing is known
     * to fit nothing.
     */
    public boolean reachedSolved() {
      return reachedSolved;
    }
  }

  /**
   * Null when the solve cannot be read again at all — no moves, no scramble, a scramble that is not
   * a 3x3 one, a blind solve recorded before its grip was kept, or a walk that did not end where the
   * solve did. The caller keeps what was recorded in that case; nothing is worse off than before.
   *
   * <p>A solve that was read but fitted no method comes back with a null {@link Result#getMethod()}
   * and no steps, which is a different answer from being unable to read it: the moves are known and
   * they bear the method out or they do not. {@link Result#reachedSolved()} is what says how much
   * that answer is worth.
   *
   * @param expected the method the solve type is read as, and the only one on offer: a solve that
   *     does not fit it is left unread rather than filed under the method it happens to fit.
   */
  public static Result reinterpret(String scramble, String storedMoves, CubeMethod expected) {
    if (scramble == null || storedMoves == null || storedMoves.isEmpty()) {
      return null;
    }
    // Blind spells its targets through the grip, which the stream carries in front of the moves.
    // Never taken from its opening rotation token: that is the frame at the first move, which a
    // slice opening the solve has already carried a quarter turn.
    String pickup = SolveMovesFormat.pickupOf(storedMoves);
    if (expected == CubeMethod.BLIND && pickup == null) {
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
      MethodAnalyzers analyzers = new MethodAnalyzers(expected);
      if (pickup != null) {
        analyzers.setPickupRotation(CubeRotation.byNotation(pickup));
      }
      analyzers.start(new CubeState(cube.toFaceCube()), 0);
      boolean reachedSolved = false;
      for (SolveMovesFormat.Move move : SolveMovesFormat.parse(storedMoves)) {
        String notation = move.getNotation();
        if (SolveMovesFormat.isRotation(notation)) {
          continue; // the detectors read states, in which a whole-cube rotation is not a change
        }
        analyzers.onMove(new CubeMove(face(notation), prime(notation), move.getOffsetMs()));
        apply(cube, notation);
        String facelets = cube.toFaceCube();
        reachedSolved |= CubieCube.SOLVED_FACELET.equals(facelets);
        analyzers.onState(new CubeState(facelets));
      }
      CubeMethod method = analyzers.resolve();
      if (method == null) {
        return new Result(null, Collections.<SolveStep>emptyList(), null, null, null, null,
            reachedSolved);
      }
      SolveAnalyzer analyzer = analyzers.get(method);
      Integer stoppedStep = analyzer.getStoppedStep();
      // A reading that says the solve ran to the end has to have reached solved. Where it did not,
      // the walk started somewhere the solve did not — see the start-state note on the class. It is
      // reaching solved and not ending there: a blind solver cannot see they are done and may turn
      // on past it, and those turns are the recorded tail rather than an unfinished solve.
      if (stoppedStep == null && !analyzer.isComplete()) {
        return null;
      }
      return new Result(method, SolveStepConverter.toSolveSteps(analyzer.getStepTimes()),
          stoppedStep, analyzer.getResidual(), analyzer.getLostReading(),
          analyzer.getParityCheck(), reachedSolved);
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
