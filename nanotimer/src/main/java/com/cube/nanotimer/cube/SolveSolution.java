package com.cube.nanotimer.cube;

import com.cube.nanotimer.cube.SolveMovesFormat.Move;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the solver actually did, rebuilt from the stored moves and the step durations: the moves of
 * each step, split again at its parts. Nothing about the split is persisted — a move belongs to the
 * step whose window its offset falls in, which is how the analyzer assigned it in the first place.
 *
 * <p>Counts are in half turns, the metric speedcubers quote: the cube reports {@code R2} as two
 * quarter turns, so consecutive turns of the same face in the same direction fold back into one.
 */
public final class SolveSolution {

  private static final String GROUP_SEPARATOR = " · ";

  private final List<Step> steps;
  private final int moveCount;
  private final long timeMs;

  private SolveSolution(List<Step> steps, int moveCount, long timeMs) {
    this.steps = Collections.unmodifiableList(steps);
    this.moveCount = moveCount;
    this.timeMs = timeMs;
  }

  /** Empty when the solve carries no moves, so callers can hide the section on one check. */
  public static SolveSolution from(String storedMoves, List<SolveStep> solveSteps, long timeMs) {
    List<Move> moves = inSolversFrame(SolveMovesFormat.parse(storedMoves));
    if (moves.isEmpty() || solveSteps == null || solveSteps.isEmpty()) {
      return new SolveSolution(new ArrayList<Step>(), 0, timeMs);
    }
    List<Step> steps = new ArrayList<Step>();
    int total = 0;
    int taken = 0;
    long boundaryMs = 0;
    for (int i = 0; i < solveSteps.size(); i++) {
      SolveStep solveStep = solveSteps.get(i);
      long stepStartMs = boundaryMs;
      boundaryMs += solveStep.getTotalMs();
      int end = endOf(moves, taken, boundaryMs);
      Step step = new Step(i, solveStep.getName(),
          groupsFor(moves, taken, end, solveStep.getSubSteps(), stepStartMs));
      steps.add(step);
      total += step.getMoveCount();
      taken = end;
    }
    return new SolveSolution(steps, total, timeMs);
  }

  /**
   * Rewrites the stored stream the way the solver would have written it.
   *
   * <p>The cube names its faces in its own frame, which never moves: it reports {@code B} for the
   * same piece of plastic however the cube is being held. Read literally next to a rotation that is
   * wrong — turn the cube and {@code B} is no longer where a reader's {@code B} is. So each face
   * letter is carried through the rotations made before it, and each rotation is itself re-expressed
   * from the frame the solver was already in, leaving a sequence that can just be followed.
   *
   * <p>The stored stream stays raw for exactly this reason: the frame is rebuilt on the way out, so
   * a correction here fixes solves already recorded.
   */
  private static List<Move> inSolversFrame(List<Move> stored) {
    CubeRotation frame = CubeRotation.byNotation("");
    List<Move> rewritten = new ArrayList<Move>(stored.size());
    for (int i = 0; i < stored.size(); i++) {
      Move move = stored.get(i);
      String notation = move.getNotation();
      if (SolveMovesFormat.isRotation(notation)) {
        // One reorientation is stored as tokens sharing an offset; relabelled one at a time its
        // spelling would be misread as being about moved axes, so it is reassembled first.
        StringBuilder composite = new StringBuilder(notation);
        while (i + 1 < stored.size() && stored.get(i + 1).getOffsetMs() == move.getOffsetMs()
            && SolveMovesFormat.isRotation(stored.get(i + 1).getNotation())) {
          composite.append(' ').append(stored.get(++i).getNotation());
        }
        CubeRotation rotation = CubeRotation.byNotation(composite.toString());
        if (rotation == null) {
          continue;
        }
        CubeRotation seen = rotation.seenFrom(frame);
        rewritten.add(new Move(seen.getNotation(), move.getOffsetMs()));
        frame = frame.then(seen); // the solver-frame rotation: then() composes in that frame
      } else {
        rewritten.add(new Move(frame.mapFace(notation.charAt(0)) + notation.substring(1),
            move.getOffsetMs()));
      }
    }
    return rewritten;
  }

  /**
   * A step's moves, split into one group per part — the pairs of an F2L, the looks of an OLL. The
   * groups stay aligned with the parts, empty ones included, so each part can be shown its own
   * count; anything the parts did not account for trails behind them.
   */
  private static List<String> groupsFor(List<Move> moves, int from, int to, List<SolveStep> parts,
      long stepStartMs) {
    List<String> groups = new ArrayList<String>();
    if (parts.isEmpty()) {
      groups.add(toHalfTurns(moves.subList(from, to)));
      return groups;
    }
    long boundaryMs = stepStartMs;
    int taken = from;
    for (SolveStep part : parts) {
      boundaryMs += part.getTotalMs();
      int end = endOf(moves, taken, boundaryMs);
      groups.add(toHalfTurns(moves.subList(taken, end)));
      taken = end;
    }
    if (to > taken) {
      groups.add(toHalfTurns(moves.subList(taken, to)));
    }
    return groups;
  }

  private static int endOf(List<Move> moves, int from, long boundaryMs) {
    int end = from;
    while (end < moves.size() && moves.get(end).getOffsetMs() <= boundaryMs) {
      end++;
    }
    return end;
  }

  private static String toHalfTurns(List<Move> moves) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < moves.size(); i++) {
      String notation = moves.get(i).getNotation();
      boolean isDouble = i + 1 < moves.size() && moves.get(i + 1).getNotation().equals(notation)
          && notation.indexOf(' ') < 0; // "y z2" twice is not a half turn of anything
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(isDouble ? notation.substring(0, 1) + "2" : notation);
      if (isDouble) {
        i++;
      }
    }
    return sb.toString();
  }

  public List<Step> getSteps() {
    return steps;
  }

  public boolean isEmpty() {
    return steps.isEmpty();
  }

  public int getMoveCount() {
    return moveCount;
  }

  /** Turns per second over the whole solve, 0 when it has no duration to divide by. */
  public double getTps() {
    return timeMs > 0 ? moveCount * 1000d / timeMs : 0;
  }

  public static final class Step {

    private final int index;
    private final String name;
    private final List<String> groups;
    private final int moveCount;

    Step(int index, String name, List<String> groups) {
      this.index = index;
      this.name = name;
      this.groups = Collections.unmodifiableList(groups);
      this.moveCount = countMoves(groups);
    }

    private static int countMoves(List<String> groups) {
      int count = 0;
      for (String group : groups) {
        count += countMoves(group);
      }
      return count;
    }

    /** Rotations are shown but never counted: turning the whole cube solves nothing. */
    private static int countMoves(String group) {
      if (group.isEmpty()) {
        return 0;
      }
      int count = 0;
      for (String token : group.split(" ")) {
        if (!SolveMovesFormat.isRotation(token)) {
          count++;
        }
      }
      return count;
    }

    /** The moves of one part, by its position in the step — 0 for a part built with none. */
    public int getPartMoveCount(int part) {
      return part < groups.size() ? countMoves(groups.get(part)) : 0;
    }

    /** The moves of one part, by its position in the step — empty for a part built with none. */
    public String getPartMoves(int part) {
      return part < groups.size() ? groups.get(part) : "";
    }

    public int getIndex() {
      return index;
    }

    public String getName() {
      return name;
    }

    public int getMoveCount() {
      return moveCount;
    }

    /** The parts joined for display, separated so the slot and look boundaries stay readable. */
    public String getMoves() {
      StringBuilder sb = new StringBuilder();
      for (String group : groups) {
        if (group.isEmpty()) { // a part built with no move of its own would show as a stray separator
          continue;
        }
        if (sb.length() > 0) {
          sb.append(GROUP_SEPARATOR);
        }
        sb.append(group);
      }
      return sb.toString();
    }
  }
}
