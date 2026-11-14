package com.cube.nanotimer.cube;

import com.cube.nanotimer.cube.SolveMovesFormat.Move;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A Roux solve's frame after the first block, read from the method instead of the gyro.
 *
 * <p>Once the first block is built the solver holds it still on the left and turns only {@code U},
 * {@code R}, {@code r} and {@code M}. So a lone turn of the block's own face is the cube reporting an
 * {@code r}, and an opposite pair is an {@code M} (the display already folds {@code r} beside
 * {@code R} into one). That pins the frame from the face stream alone, which matters because the
 * gyro rides the core: around each wide and slice it swings a quarter turn and often swings back a
 * move later, and every turn read in between comes out on the wrong face ({@code U} as {@code B}).
 * The 2026-11-14 report showed a second block full of {@code B}, {@code L} and rotations that reads
 * as plain {@code r U R M} this way.
 *
 * <p>The frame at the start of the second block is the one of the 24 under which the second block
 * and the last six edges read as that move set. Where it differs from the frame the gyro left the
 * first block in, the difference is shown as one rotation closing the first block. CMLL algorithms
 * may turn the block's face for real, so there a lone turn is a wide only where the gyro said so.
 * The reading is kept only when it reads better than the gyro's.
 */
final class RouxFrames {

  private static final String ROUX_LETTERS = "URrM";

  private RouxFrames() {
  }

  /** The stored stream with the frame after the first block rewritten, or as it was. */
  static List<Move> reread(List<Move> stored, List<SolveStep> solveSteps) {
    if (solveSteps == null || solveSteps.size() < 2) {
      return stored;
    }
    long fbEnd = solveSteps.get(0).getTotalMs();
    long sbEnd = fbEnd + solveSteps.get(1).getTotalMs();
    long cmllEnd = solveSteps.size() > 2 ? sbEnd + solveSteps.get(2).getTotalMs() : Long.MAX_VALUE;
    Windows windows = new Windows(fbEnd, sbEnd, cmllEnd);

    List<Move> firstBlock = new ArrayList<Move>();
    for (Move move : stored) {
      if (move.getOffsetMs() <= fbEnd) {
        firstBlock.add(move);
      }
    }
    if (firstBlock.size() == stored.size()) {
      return stored;
    }
    CubeRotation gyroFrame = SolveSolution.frameAfter(firstBlock);
    List<Move> best = null;
    int bestBad = Integer.MAX_VALUE;
    for (CubeRotation start : CubeRotation.all()) {
      List<Move> candidate = rewrite(stored, firstBlock, gyroFrame, start, windows);
      int bad = badLetters(candidate, solveSteps, windows);
      if (bad < bestBad || bad == bestBad && start.equals(gyroFrame)) { // a tie keeps the gyro's
        best = candidate;
        bestBad = bad;
      }
    }
    return bestBad < badLetters(stored, solveSteps, windows) ? best : stored;
  }

  private static List<Move> rewrite(List<Move> stored, List<Move> firstBlock,
      CubeRotation gyroFrame, CubeRotation start, Windows windows) {
    List<Move> out = new ArrayList<Move>(firstBlock);
    if (!start.equals(gyroFrame)) {
      out.add(new Move(correction(gyroFrame, start).getNotation(), windows.fbEnd));
    }
    Set<Integer> gyroWides = new HashSet<Integer>();
    Set<Integer> gyroSlices = new HashSet<Integer>();
    gyroReadings(stored, gyroWides, gyroSlices);
    CubeRotation frame = start;
    for (int i = firstBlock.size(); i < stored.size(); i++) {
      Move move = stored.get(i);
      String notation = move.getNotation();
      if (SolveMovesFormat.isRotation(notation)) {
        continue;
      }
      boolean blockFace = frame.mapFace(notation.charAt(0)) == 'L';
      boolean wide = windows.inCmll(move.getOffsetMs())
          ? gyroWides.contains(i) || blockFace && gyroSlices.contains(i)
          : blockFace;
      String spin = wide ? Wides.spinFor(notation) : null;
      if (spin != null) {
        out.add(new Move(spin, move.getOffsetMs() - 1)); // a wide's spin undercuts its face by 1 ms
        frame = frame.then(CubeRotation.byNotation(spin).seenFrom(frame));
      }
      out.add(move);
    }
    return out;
  }

  /** The cube-frame rotation that takes a solver held in {@code from} to holding it in {@code to}. */
  private static CubeRotation correction(CubeRotation from, CubeRotation to) {
    for (CubeRotation candidate : CubeRotation.all()) {
      if (from.then(candidate.seenFrom(from)).equals(to)) {
        return candidate;
      }
    }
    throw new IllegalStateException("no rotation from " + from + " to " + to);
  }

  /** Indexes of the faces the gyro reported as a wide, and as half of a slice. */
  private static void gyroReadings(List<Move> stored, Set<Integer> wides, Set<Integer> slices) {
    for (int i = 0; i < stored.size(); i++) {
      if (SolveMovesFormat.isRotation(stored.get(i).getNotation())) {
        if (SolveSolution.wideFace(stored, i) != null) {
          wides.add(i + 1);
        }
      } else if (SolveSolution.sliceCoreSpin(stored, i) != null) {
        slices.add(i);
        slices.add(i + 1);
      } else if (SolveSolution.straddledSlice(stored, i) > i) {
        slices.add(i);
        slices.add(i + 2);
      }
    }
  }

  /** How many displayed tokens of the second block and the last six edges are not Roux's. */
  private static int badLetters(List<Move> stream, List<SolveStep> solveSteps, Windows windows) {
    int bad = 0;
    for (Move move : SolveSolution.spell(stream, null, null, solveSteps)) {
      if (windows.isRouxOnly(move.getOffsetMs())
          && ROUX_LETTERS.indexOf(move.getNotation().charAt(0)) < 0) {
        bad++;
      }
    }
    return bad;
  }

  private static final class Windows {

    private final long fbEnd;
    private final long sbEnd;
    private final long cmllEnd;

    private Windows(long fbEnd, long sbEnd, long cmllEnd) {
      this.fbEnd = fbEnd;
      this.sbEnd = sbEnd;
      this.cmllEnd = cmllEnd;
    }

    private boolean inCmll(long offsetMs) {
      return offsetMs > sbEnd && offsetMs <= cmllEnd;
    }

    private boolean isRouxOnly(long offsetMs) {
      return offsetMs > fbEnd && !inCmll(offsetMs);
    }
  }
}
