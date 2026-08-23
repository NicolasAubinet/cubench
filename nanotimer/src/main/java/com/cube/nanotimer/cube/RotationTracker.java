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
   *
   * <p><strong>The grip is written down before any frame is compared against it.</strong> A solve
   * opening on a slice has no frame read at its first moves, so by the time one is the tokens
   * already carry that slice's spin, and the difference left over is the grip — but written there it
   * lands <em>after</em> the slice instead of before it, which names the slice through the wrong
   * frame and prints the grip as a regrip nobody made. So the opening difference is hoisted to the
   * front, where a solve not opening on a slice puts it anyway. See {@link #gripAt}.
   */
  public List<Rotation> getRotations(List<Rotation> coreSpins) {
    List<Rotation> rotations = new ArrayList<Rotation>();
    CubeRotation written = CubeRotation.byNotation(""); // what the tokens so far add up to
    int nextSpin = 0;
    int lagging = 0; // frames still owed to a wide whose swing the gyro has not reported yet
    boolean opening = true; // still before the first frame read against the tokens: see gripAt
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
          opening = false; // a wide has already swung the frame: see gripAt
        }
        rotations.add(spin);
        written = after;
      }
      boolean insidePair = insideSlicePair(coreSpins, nextSpin, frame.timestampMs);
      if (stale || frame.rotation.getNotation().equals(written.getNotation()) || insidePair) {
        opening &= insidePair; // a frame that could be read has been: the opening is over
        continue;
      }
      if (lagging > 0) {
        lagging--;
        opening = false;
        continue; // still owed a wide's swing: this reading is behind it, not a regrip
      }
      if (opening && !written.isIdentity()) {
        rotations.add(0, new Rotation(gripAt(written, frame.rotation), openedAtMs(rotations)));
        opening = false;
        written = frame.rotation; // the grip plus the spins already folded: nothing else is owed
        continue;
      }
      opening = false;
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
   * The grip a solve opening on a slice was picked up in: the part of its first readable frame that
   * the spins already folded do not account for.
   *
   * <p>Written at the opening rather than where it surfaces, which is the whole point. The frames
   * compose in the cube's own axes, so a grip {@code G} followed by a spin {@code s} reads as
   * {@code G·s} — and the same difference written after the spin instead is {@code s⁻¹·G·s}, a
   * different rotation, at a moment the solver did nothing. That phantom re-letters the rest of the
   * solve and names the opening slice itself through the wrong frame.
   *
   * <p>⚠️ <b>The stored pick-up grip is not what this reads.</b> That one is measured over the
   * window before the first move, which for a solve the cube auto-starts is no window at all, so it
   * can come back holding the swing of a wide the solve opened on — the scripted wide drill stores a
   * grip its own ground truth disowns. Nothing here trusts it: a wide at the opening ends the
   * opening, and what is hoisted is only ever the difference the frames themselves show.
   */
  private static String gripAt(CubeRotation written, CubeRotation frame) {
    return written.to(CubeRotation.byNotation("")).then(frame).getNotation();
  }

  /**
   * When the solve opened, so the grip is written ahead of its first move and of the spins folded
   * since. Ahead of the move and not merely of the spin: a pair whose first face the gyro said
   * nothing at would otherwise take the grip token between its two halves and stop it folding.
   */
  private long openedAtMs(List<Rotation> spins) {
    long from = frames.get(0).timestampMs;
    for (Rotation spin : spins) {
      from = Math.min(from, spin.getPairFromMs());
    }
    return from;
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
