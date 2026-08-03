package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.step.RouxStepDetector;
import com.cube.nanotimer.smartcube.step.SolveAnalyzer;
import com.cube.nanotimer.vo.SolveStep;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Walks a solve exactly as it was recorded — its scramble and the raw token stream the database
 * holds — so what the reconstruction makes of it can be read token by token offline. The frame is
 * rebuilt at display, so a recorded solve is a complete fixture: a fix shows up on it without the
 * cube.
 */
final class RecordedSolveReplay {

  private final String scramble;
  private final String storedMoves;
  private final CubieCube cube = new CubieCube();

  RecordedSolveReplay(String scramble, String storedMoves) {
    this.scramble = scramble;
    this.storedMoves = storedMoves;
  }

  /** Prints every stored token with the frame it is read in and the letter it comes out as. */
  void printFrames() {
    applyScramble();
    CubeRotation frame = CubeRotation.byNotation("");
    List<SolveMovesFormat.Move> stored = SolveMovesFormat.parse(storedMoves);
    System.out.println("stored token   frame after   solver sees");
    for (int i = 0; i < stored.size(); i++) {
      SolveMovesFormat.Move move = stored.get(i);
      String notation = move.getNotation();
      String seen;
      if (SolveMovesFormat.isRotation(notation)) {
        StringBuilder composite = new StringBuilder(notation);
        while (i + 1 < stored.size() && stored.get(i + 1).getOffsetMs() == move.getOffsetMs()
            && SolveMovesFormat.isRotation(stored.get(i + 1).getNotation())) {
          composite.append(' ').append(stored.get(++i).getNotation());
        }
        CubeRotation rotation = CubeRotation.byNotation(composite.toString());
        CubeRotation inFrame = rotation.seenFrom(frame);
        frame = frame.then(inFrame);
        seen = "[" + inFrame + "]";
      } else {
        seen = frame.mapFace(notation.charAt(0)) + notation.substring(1);
        apply(notation);
      }
      System.out.printf("  %-12s %-13s %s%n",
          notation + "@" + move.getOffsetMs(), frame.toString(), seen);
    }
  }

  /**
   * The whole solve as the screen would show it, in one step so nothing is split off. A misplaced
   * rotation surfaces here as a face letter, or a slice, named on the wrong axis.
   */
  String display() {
    return wholeSolve().getMoves();
  }

  /** The same sequence, with the moves that undid each other fenced off. */
  String marked() {
    return MarkedMoves.of(wholeSolve());
  }

  private SolveSolution.Step wholeSolve() {
    return SolveSolution.from(storedMoves,
        Arrays.asList(new SolveStep(0, "all", 0, 600_000, Collections.<SolveStep>emptyList())))
        .getSteps().get(0);
  }

  /**
   * The frame the step detector reads the solve in, from the states alone — no gyro anywhere in it.
   * A Roux solve's blocks name the pair of faces it was turned on, which is the frame's ground
   * truth, and the solved flag says the stored stream really is the whole solve.
   */
  String detectedFrame() {
    applyScramble();
    RouxStepDetector detector = new RouxStepDetector();
    SolveAnalyzer analyzer = new SolveAnalyzer(detector);
    analyzer.start(new CubeState(cube.toFaceCube()), 0);
    for (SolveMovesFormat.Move move : SolveMovesFormat.parse(storedMoves)) {
      if (SolveMovesFormat.isRotation(move.getNotation())) {
        continue;
      }
      analyzer.onMove(new CubeMove(face(move.getNotation()), prime(move.getNotation()),
          move.getOffsetMs()));
      apply(move.getNotation());
      analyzer.onState(new CubeState(cube.toFaceCube()));
    }
    return "left=" + detector.getLeftFace() + " down=" + detector.getDownFace()
        + " solved=" + cube.isSolved();
  }

  private void applyScramble() {
    cube.fromFacelet(CubieCube.SOLVED_FACELET);
    for (String token : scramble.trim().split("\\s+")) {
      for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
        cube.applyMove(face(token), prime(token));
      }
    }
  }

  private void apply(String token) {
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
