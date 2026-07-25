package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the slices a solve was turned with, by asking the gyro whether the core rocked.
 *
 * <p>The cube senses only its six outer faces, so a slice arrives as two opposite faces turned at
 * once — indistinguishable, in the move stream alone, from a genuine two-handed turn of those same
 * faces. The difference is physical: a slice turns the middle layer, which carries the core and the
 * gyro with it, while a two-hander leaves the core still. So each candidate pair is settled by the
 * readings either side of it: a clean quarter turn about the axis that slice would rock is a slice,
 * anything less is two honest face turns.
 *
 * <p>Runs over the whole solve once it is finished, rather than live, because the reading that
 * proves the rock only settles after the turn is over. {@link RotationTracker} cannot answer this:
 * it reports the <em>grip</em>, deliberately conservatively, and dates a rotation at the next move
 * rather than at the event.
 */
public final class SliceSpinDetector {

  /**
   * How far either side of a pair the settled readings are taken. Measured on a V10: at 50 ms the
   * core is still mid-rock (48° of a 90° step), by 150–200 ms it has arrived and holds.
   */
  private static final long SETTLE_MS = 200;

  private final List<Turn> turns = new ArrayList<Turn>();

  /** Where the orientation at a past moment is read back from. */
  public interface Orientations {
    CubeOrientation at(long timestampMs);
  }

  /**
   * Feed every solve move. Its cube timestamp is fitted to host time, so it dates the turn on the
   * same clock the gyro samples carry — and unlike the moment the move was handled, it is right
   * per move: a packet can deliver several at once, which would otherwise share one instant.
   */
  public void onMove(CubeMove move) {
    turns.add(new Turn(move.getNotation(), move.getCubeTimestampMs()));
  }

  public void reset() {
    turns.clear();
  }

  /**
   * The solve's rotation tokens: {@code gripRotations} with each detected slice's core spin added,
   * and any grip rotation that was really one of those spins removed.
   *
   * <p>The two must be reconciled rather than merely merged. A slice held long enough to leave a
   * still window reads to the grip tracker as an ordinary reorientation — the gyro signal is the
   * same event — so kept alongside its own spin it would turn the frame twice for one rock.
   */
  public List<RotationTracker.Rotation> merge(List<RotationTracker.Rotation> gripRotations,
      Orientations orientations) {
    List<RotationTracker.Rotation> merged = new ArrayList<RotationTracker.Rotation>(gripRotations);
    for (int i = 0; i + 1 < turns.size(); i++) {
      String[] slice = Slices.forPair(turns.get(i).notation, turns.get(i + 1).notation);
      if (slice == null || turns.get(i + 1).atMs - turns.get(i).atMs > Slices.WINDOW_MS) {
        continue;
      }
      if (!coreRocked(turns.get(i), turns.get(i + 1), slice[1], orientations)) {
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

  private boolean coreRocked(Turn from, Turn to, String spin, Orientations orientations) {
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
