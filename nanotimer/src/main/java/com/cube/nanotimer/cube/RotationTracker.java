package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads the frame every solve move was turned in straight off the gyro, and writes down whatever
 * whole-cube rotation carries the solve from one frame to the next. Measured per move like this the
 * frame is right for 78% of the moves of seven captured solves, against 55% for the still-window
 * grip tracking it replaced.
 *
 * <p><strong>The frames are measured from {@link GyroReference}</strong>, the grip the gyro session
 * was anchored in — held there rather than here because the stored gyro track and the live mirror
 * are written against the same reference and a re-anchor has to reach all three.
 *
 * <p>Slices are {@link SliceSpinDetector}'s, since the core rocks over ~150 ms and every reading
 * taken at a move inside an LSE is mid-rock. Its spins are folded in here, and no frame is read at
 * a move inside one of its pairs — what is left is the solver's own turning.
 */
public final class RotationTracker {

  /**
   * How many moves the gyro is given to report a wide's swing before a disagreeing frame is read as
   * a regrip instead. The reading at a move can predate the turning that move did, and on a scripted
   * solve demanding it by the very next move threw away eleven of twenty wides the solver had made.
   */
  private static final int GYRO_LAG_FRAMES = 2;

  private final List<Frame> frames = new ArrayList<Frame>();
  private final GyroReference reference;

  public RotationTracker(GyroReference reference) {
    this.reference = reference;
  }

  /** Sample the reading at a solve move: the frame it was made in is the delta from the reference. */
  public void onMove(CubeOrientation reading, long moveTimestampMs) {
    CubeRotation frame = reference.frameOf(reading);
    if (frame == null) {
      return; // no reference yet, or no gyro: the move stands in whatever frame the last one set
    }
    frames.add(new Frame(frame, moveTimestampMs));
  }

  /**
   * The frame the solve was picked up in — the rotation the solver made taking the cube up, since
   * the scramble is turned green in front and the solve in whatever grip they prefer. Null until a
   * move has been sampled, or when there is no gyro to sample.
   *
   * <p>Read at the <b>first move not inside a slice pair</b>, and not simply at the first move. A
   * slice carries the core round over ~150 ms, so a reading taken at either of its two faces is
   * mid-rock and can land the frame a quarter turn out — which one captured solve did, opening on a
   * pair 3 ms apart and spelling every blind target through the wrong grip. Nothing is lost by
   * waiting: the solver has not regripped a few milliseconds in, so any early move answers this.
   */
  public CubeRotation getPickupRotation(List<Rotation> pairs) {
    for (Frame frame : frames) {
      if (!insideAnyPair(pairs, frame.timestampMs)) {
        return frame.rotation;
      }
    }
    return frames.isEmpty() ? null : frames.get(0).rotation; // nothing settled yet: the best there is
  }

  private static boolean insideAnyPair(List<Rotation> pairs, long timestampMs) {
    for (Rotation pair : pairs) {
      if (pair.getPairFromMs() <= timestampMs && timestampMs <= pair.getTimestampMs()) {
        return true;
      }
    }
    return false;
  }

  public List<Rotation> getRotations() {
    return getRotations(Collections.<Rotation>emptyList());
  }

  /**
   * The solve's rotation tokens: every core spin, and wherever the gyro's frame differs from what
   * the tokens so far add up to, the regrip making up the difference, dated at the move it shows at.
   * No hysteresis — waiting for a second move to agree costs more than the noise it suppresses
   * (frame 78% down to 66%).
   */
  public List<Rotation> getRotations(List<Rotation> coreSpins) {
    List<Rotation> rotations = new ArrayList<Rotation>();
    CubeRotation written = CubeRotation.byNotation(""); // what the tokens so far add up to
    int nextSpin = 0;
    int lagging = 0; // frames still owed to a wide whose swing the gyro has not reported yet
    for (int f = 0; f < frames.size(); f++) {
      Frame frame = frames.get(f);
      boolean stale = false; // this move's reading predates its own turning: see isWide
      while (nextSpin < coreSpins.size()
          && coreSpins.get(nextSpin).getTimestampMs() <= frame.timestampMs) {
        Rotation spin = coreSpins.get(nextSpin++);
        CubeRotation spun = CubeRotation.byNotation(spin.getNotation());
        CubeRotation after = written.then(spun.seenFrom(written)); // a rock turns the frame the last one left
        if (spin.isWide()) {
          boolean behind = frame.rotation.getNotation().equals(written.getNotation());
          if (!behind && !frame.rotation.getNotation().equals(after.getNotation())) {
            continue; // the frame is off doing something else: see isWide
          }
          stale = behind; // its reading predates the swing, so nothing here is a regrip
          lagging = behind ? GYRO_LAG_FRAMES : 0;
        }
        rotations.add(spin);
        written = after;
      }
      if (stale || frame.rotation.getNotation().equals(written.getNotation())
          || insideSlicePair(coreSpins, nextSpin, frame.timestampMs)) {
        continue;
      }
      if (lagging > 0) {
        lagging--;
        continue; // still owed a wide's swing: this reading is behind it, not a regrip
      }
      rotations.add(new Rotation(written.to(frame.rotation).getNotation(), frame.timestampMs));
      written = frame.rotation;
    }
    for (; nextSpin < coreSpins.size(); nextSpin++) {
      if (!coreSpins.get(nextSpin).isWide()) {
        rotations.add(coreSpins.get(nextSpin)); // a solve ending on a slice: its rock has no move after
      }
    }
    return rotations;
  }

  /**
   * Whether a move sits inside a slice pair whose rock has not been taken yet: the reading there is
   * mid-rock by construction, so no frame is read at it and the pair's turning is left to
   * {@link SliceSpinDetector}, which measures it from settled readings either side.
   */
  private static boolean insideSlicePair(List<Rotation> coreSpins, int nextSpin,
      long moveTimestampMs) {
    return nextSpin < coreSpins.size()
        && coreSpins.get(nextSpin).getPairFromMs() <= moveTimestampMs;
  }

  /** Forget the solve's frames. The reference is the caller's to restart: it outlives a tracker. */
  public void reset() {
    frames.clear();
  }

  /** The frame one move was made in, as the gyro read it, and the moment the cube dates that move. */
  private static final class Frame {

    private final CubeRotation rotation;
    private final long timestampMs;

    private Frame(CubeRotation rotation, long timestampMs) {
      this.rotation = rotation;
      this.timestampMs = timestampMs;
    }
  }

  /** A whole-cube rotation, at the moment of the move it preceded. */
  public static final class Rotation {

    private final String notation;
    private final long timestampMs;
    private final long pairFromMs;
    private final boolean wide;

    Rotation(String notation, long timestampMs) {
      this(notation, timestampMs, Long.MAX_VALUE);
    }

    /** A core spin, which also knows where the pair it was measured across began. */
    Rotation(String notation, long timestampMs, long pairFromMs) {
      this(notation, timestampMs, pairFromMs, false);
    }

    Rotation(String notation, long timestampMs, long pairFromMs, boolean wide) {
      this.notation = notation;
      this.timestampMs = timestampMs;
      this.pairFromMs = pairFromMs;
      this.wide = wide;
    }

    /** One or two tokens, e.g. {@code "y"} or {@code "y x'"}. */
    public String getNotation() {
      return notation;
    }

    public long getTimestampMs() {
      return timestampMs;
    }

    /** The first face of the pair this spin was measured across; never set on a regrip. */
    long getPairFromMs() {
      return pairFromMs;
    }

    /**
     * A wide's core swing, which the frames may lag behind rather than confirm. The cube reports a
     * turn the moment it is done and the sample behind it is up to a frame older, so at a wide's own
     * move the frame usually has not caught up — measured on a scripted solve, demanding that it had
     * threw away eleven of twenty wides the solver had actually made. A frame that has not moved is
     * therefore taken as lagging and its reading ignored, exactly as one inside a slice pair is; only
     * a frame that has moved somewhere else denies the swing, which is what keeps a wide from taking
     * a bite out of a real reorientation.
     */
    boolean isWide() {
      return wide;
    }
  }
}
