package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the slices and wide moves a solve was turned with, by asking the gyro whether the core
 * rocked.
 *
 * <p>A slice reaches the move stream as two opposite faces at once, a wide as the single opposite
 * face, and each is indistinguishable from ordinary turns of the same faces. Only the core tells
 * them apart, and it is asked the same question both times — did it swing the one quarter turn this
 * move would have swung it? Runs after the solve: the reading proving the rock settles late.
 *
 * <p>{@link RotationTracker} cannot answer this: it samples the gyro <em>at</em> a move, and inside
 * an LSE every such reading is mid-rock. It folds the spins found here into its own frame instead.
 */
public final class SliceSpinDetector {

  /**
   * How far either side of a pair the settled readings are taken. Measured on a V10: at 50 ms the
   * core is still mid-rock (48° of a 90° step), by 150–200 ms it has arrived and holds. Shared with
   * the pick-up read, which wants the same thing of a reading and must not drift from this.
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
   * Every core spin the solve is confirmed to have made, slices and wides alike, in turn order. A
   * slice's spin is dated a millisecond <em>behind</em> its pair and a wide's a millisecond
   * <em>ahead</em> of its face, which is what the display fold keys on.
   */
  public List<RotationTracker.Rotation> coreSpins(Orientations orientations) {
    boolean[] claimed = new boolean[turns.size()];
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
        claim(claimed, i, i + 3);
        i += 3;
        continue;
      }
      if (!rocked(turns.get(i), turns.get(i + 1), slice[1], orientations)) {
        continue;
      }
      spins.add(new RotationTracker.Rotation(slice[1], turns.get(i + 1).atMs + 1, pairFromMs));
      claim(claimed, i, i + 1);
      i++; // both faces are spoken for: the second cannot also open a pair
    }
    return merged(spins, wideSpins(claimed, orientations));
  }

  /**
   * Every wide move: a lone face whose core swung the quarter turn that would make it wide, read
   * {@link #SETTLE_MS} either side of the turn. A solver who reorients and turns the opposite face
   * inside that window has done something physically identical to a wide; <b>this calls it a
   * wide</b>, and {@link RotationTracker.Rotation#isWide} does the rest of the judging.
   *
   * <p>Dated a millisecond ahead of the face, where a slice's is dated behind its pair: the cube
   * reports a turn once it is done, so this spin would otherwise be minted as an ordinary regrip on
   * the face's own timestamp, and nothing else is ever dated off a move.
   */
  private List<RotationTracker.Rotation> wideSpins(boolean[] claimed, Orientations orientations) {
    List<RotationTracker.Rotation> spins = new ArrayList<RotationTracker.Rotation>();
    for (int i = 0; i < turns.size(); i++) {
      Turn turn = turns.get(i);
      String spin = claimed[i] ? null : Wides.spinFor(turn.notation);
      if (spin != null && rocked(turn, turn, spin, orientations)) {
        spins.add(new RotationTracker.Rotation(spin, turn.atMs - 1, turn.atMs - 1, true));
      }
    }
    return spins;
  }

  private static void claim(boolean[] claimed, int from, int to) {
    for (int i = from; i <= to; i++) {
      claimed[i] = true;
    }
  }

  /** The two sorted runs as one, since {@link RotationTracker} folds them in by time. */
  private static List<RotationTracker.Rotation> merged(List<RotationTracker.Rotation> slices,
      List<RotationTracker.Rotation> wides) {
    if (wides.isEmpty()) {
      return slices;
    }
    List<RotationTracker.Rotation> all = new ArrayList<RotationTracker.Rotation>(slices);
    all.addAll(wides);
    // Stable, so a slice keeps the claim where the two land on the same millisecond.
    Collections.sort(all, Comparator.comparingLong(RotationTracker.Rotation::getTimestampMs));
    return all;
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

  /** Whether the core turned by {@code spin} across these two turns; one turn twice for a wide. */
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
