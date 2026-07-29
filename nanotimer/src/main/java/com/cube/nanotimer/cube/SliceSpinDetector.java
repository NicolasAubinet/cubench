package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the slices a solve was turned with, by asking the gyro whether the core rocked.
 *
 * <p>A slice reaches the move stream as two opposite faces at once, exactly like a two-handed turn
 * of the same faces; only the core tells them apart, since the slice carries it and the gyro round.
 * Runs after the solve because the reading proving the rock settles only once the turn is over.
 *
 * <p>{@link RotationTracker} cannot answer this: it samples the gyro <em>at</em> a move, and inside
 * an LSE every such reading is mid-rock. It folds the spins found here into its own frame instead.
 */
public final class SliceSpinDetector {

  /**
   * How far either side of a pair the settled readings are taken. Measured on a V10: at 50 ms the
   * core is still mid-rock (48° of a 90° step), by 150–200 ms it has arrived and holds.
   */
  static final long SETTLE_MS = 200;

  /** Two slices closer than this are one M2: measured at 115 ms on a real solve. */
  private static final long DOUBLE_GAP_MS = 250;

  /**
   * How far off the expected quarter turn the core may read and still count as having rocked. The
   * pair of faces names the spin outright, so the only question is rocked or held still, and a still
   * core reads at its 4° noise floor: across seven captured solves real rocks spread from 0° to
   * 42.8° and stopped dead. Anything from 43 to 90 behaves alike; the 35° a lattice match wants cuts
   * six real rocks off the tail, each losing the frame a quarter turn for the rest of the solve.
   */
  private static final double ROCK_TOLERANCE_DEGREES = 45;

  private final List<Turn> turns = new ArrayList<Turn>();

  /** Where the orientation at a past moment is read back from. */
  public interface Orientations {
    CubeOrientation at(long timestampMs);
  }

  /** Feed every solve move: its cube timestamp is fitted to host time, the clock the gyro uses. */
  public void onMove(CubeMove move) {
    turns.add(new Turn(move.getNotation(), move.getCubeTimestampMs()));
  }

  public void reset() {
    turns.clear();
  }

  /**
   * Every slice the core is confirmed to have rocked through, in the order they were turned. Each
   * spin is dated one millisecond behind its pair, since the stored form writes a rotation ahead of
   * the move it precedes and the display fold looks for it behind the pair, not inside it.
   */
  public List<RotationTracker.Rotation> coreSpins(Orientations orientations) {
    List<RotationTracker.Rotation> spins = new ArrayList<RotationTracker.Rotation>();
    for (int i = 0; i + 1 < turns.size(); i++) {
      String[] slice = Slices.forPair(turns.get(i).notation, turns.get(i + 1).notation);
      if (slice == null || turns.get(i + 1).atMs - turns.get(i).atMs > Slices.WINDOW_MS) {
        continue;
      }
      long pairFromMs = turns.get(i).atMs;
      if (isDoubleSlice(i, slice, orientations)) {
        spins.add(new RotationTracker.Rotation(slice[1], turns.get(i + 1).atMs + 1, pairFromMs));
        spins.add(new RotationTracker.Rotation(slice[1], turns.get(i + 3).atMs + 1, pairFromMs));
        i += 3;
        continue;
      }
      if (!rocked(turns.get(i), turns.get(i + 1), slice[1], orientations)) {
        continue;
      }
      spins.add(new RotationTracker.Rotation(slice[1], turns.get(i + 1).atMs + 1, pairFromMs));
      i++; // both faces are spoken for: the second cannot also open a pair
    }
    return spins;
  }

  /**
   * Every pair of opposite faces close enough together to be one slice's two turns, whether or not
   * the core is confirmed to have rocked — asked while the solve is still running, which is before
   * {@link #coreSpins} can answer anything: the reading that proves a rock settles 200 ms later.
   *
   * <p>The shape of the pair is enough for what wants it. A reading taken between two faces this
   * close is taken mid-turn either way, and the frame read there is not to be trusted.
   */
  public List<RotationTracker.Rotation> possiblePairs() {
    List<RotationTracker.Rotation> pairs = new ArrayList<RotationTracker.Rotation>();
    for (int i = 0; i + 1 < turns.size(); i++) {
      String[] slice = Slices.forPair(turns.get(i).notation, turns.get(i + 1).notation);
      if (slice == null || turns.get(i + 1).atMs - turns.get(i).atMs > Slices.WINDOW_MS) {
        continue;
      }
      pairs.add(new RotationTracker.Rotation(slice[1], turns.get(i + 1).atMs, turns.get(i).atMs));
      i++; // both faces are spoken for: the second cannot also open a pair
    }
    return pairs;
  }

  /**
   * True when turns {@code i..i+3} are one slice twice over: each half hides the other's rock, so
   * the four are measured as one 180° step. Both halves still get a spin; the display recollapses.
   */
  private boolean isDoubleSlice(int i, String[] slice, Orientations orientations) {
    if (i + 3 >= turns.size()) {
      return false;
    }
    String[] second = Slices.forPair(turns.get(i + 2).notation, turns.get(i + 3).notation);
    if (second == null || !second[0].equals(slice[0])
        || turns.get(i + 3).atMs - turns.get(i + 2).atMs > Slices.WINDOW_MS
        || turns.get(i + 2).atMs - turns.get(i + 1).atMs > DOUBLE_GAP_MS) {
      return false;
    }
    return rocked(turns.get(i), turns.get(i + 3), halfTurnOf(slice[1]), orientations);
  }

  private static String halfTurnOf(String spin) {
    CubeRotation quarter = CubeRotation.byNotation(spin);
    return quarter.then(quarter).getNotation();
  }

  /** Whether the core turned by {@code spin} across these two turns. */
  private boolean rocked(Turn from, Turn to, String spin, Orientations orientations) {
    CubeOrientation before = orientations.at(from.atMs - SETTLE_MS);
    CubeOrientation after = orientations.at(to.atMs + SETTLE_MS);
    if (before == null || after == null) {
      return false; // no gyro, or no reading near enough: the faces stand as themselves
    }
    CubeRotation rocked = CubeRotation.nearest(before.deltaTo(after), ROCK_TOLERANCE_DEGREES);
    return rocked != null && rocked.getNotation().equals(spin);
  }

  /** One face turn, at the moment the cube dates it. */
  private static final class Turn {

    private final String notation;
    private final long atMs;

    private Turn(String notation, long atMs) {
      this.notation = notation;
      this.atMs = atMs;
    }
  }
}
