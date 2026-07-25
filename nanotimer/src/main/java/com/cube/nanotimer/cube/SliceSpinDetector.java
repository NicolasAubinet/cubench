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
 * <p>{@link RotationTracker} cannot answer this: it reports the <em>grip</em>, deliberately
 * conservatively, and dates a rotation at the next move rather than at the event.
 */
public final class SliceSpinDetector {

  /**
   * How far either side of a pair the settled readings are taken. Measured on a V10: at 50 ms the
   * core is still mid-rock (48° of a 90° step), by 150–200 ms it has arrived and holds.
   */
  private static final long SETTLE_MS = 200;

  /** Two slices closer than this are one M2: measured at 115 ms on a real solve. */
  private static final long DOUBLE_GAP_MS = 250;

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
   * The solve's rotation tokens: the grip tracker's, plus each slice's core spin, minus any grip
   * rotation that was one of those spins — the same event twice would turn the frame twice.
   */
  public List<RotationTracker.Rotation> merge(List<RotationTracker.Rotation> gripRotations,
      Orientations orientations) {
    List<RotationTracker.Rotation> merged = new ArrayList<RotationTracker.Rotation>(gripRotations);
    for (int i = 0; i + 1 < turns.size(); i++) {
      String[] slice = Slices.forPair(turns.get(i).notation, turns.get(i + 1).notation);
      if (slice == null || turns.get(i + 1).atMs - turns.get(i).atMs > Slices.WINDOW_MS) {
        continue;
      }
      if (isDoubleSlice(i, slice, orientations)) {
        removeGripRotation(merged, halfTurnOf(slice[1]), turns.get(i).atMs, nextMoveMs(i + 4));
        merged.add(new RotationTracker.Rotation(slice[1], turns.get(i + 1).atMs + 1));
        merged.add(new RotationTracker.Rotation(slice[1], turns.get(i + 3).atMs + 1));
        i += 3;
        continue;
      }
      if (!rocked(turns.get(i), turns.get(i + 1), slice[1], orientations)) {
        continue;
      }
      removeGripRotation(merged, slice[1], turns.get(i).atMs, nextMoveMs(i + 2));
      // A rotation is written ahead of the move it precedes, so a spin dated with the pair would
      // land in front of it. One millisecond later leaves it where the fold looks: just behind.
      merged.add(new RotationTracker.Rotation(slice[1], turns.get(i + 1).atMs + 1));
      i++; // both faces are spoken for: the second cannot also open a pair
    }
    sortByTime(merged);
    return merged;
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
    CubeRotation rocked = CubeRotation.nearest(before.deltaTo(after));
    return rocked != null && rocked.getNotation().equals(spin);
  }

  /** The grip tracker dates a rotation at the first move made in the new grip: the one after. */
  private long nextMoveMs(int index) {
    return index < turns.size() ? turns.get(index).atMs : Long.MAX_VALUE;
  }

  private static void removeGripRotation(List<RotationTracker.Rotation> rotations, String notation,
      long afterMs, long untilMs) {
    for (int i = 0; i < rotations.size(); i++) {
      RotationTracker.Rotation rotation = rotations.get(i);
      if (rotation.getNotation().equals(notation) && rotation.getTimestampMs() > afterMs
          && rotation.getTimestampMs() <= untilMs) {
        rotations.remove(i);
        return;
      }
    }
  }

  private static void sortByTime(List<RotationTracker.Rotation> rotations) {
    for (int i = 1; i < rotations.size(); i++) { // insertion sort: a solve holds a handful of these
      RotationTracker.Rotation rotation = rotations.get(i);
      int j = i - 1;
      while (j >= 0 && rotations.get(j).getTimestampMs() > rotation.getTimestampMs()) {
        rotations.set(j + 1, rotations.get(j--));
      }
      rotations.set(j + 1, rotation);
    }
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
