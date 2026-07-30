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
 * <p><strong>The reference is the reading at the first scramble move</strong>, uprighted — the one
 * grip whose label can be known, because it is the one the solver can be asked for. The end of a
 * scramble cannot: cubers turn the cube mid-scramble to make a {@code B} easier and put it back only
 * sometimes.
 *
 * <p>Slices are {@link SliceSpinDetector}'s, since the core rocks over ~150 ms and every reading
 * taken at a move inside an LSE is mid-rock. Its spins are folded in here, and no frame is read at
 * a move inside one of its pairs — what is left is the solver's own turning.
 */
public final class RotationTracker {

  private final List<Frame> frames = new ArrayList<Frame>();
  private CubeOrientation reference; // the grip the scramble was begun in, its tilt taken out

  /** Feed the reading at every followed scramble move: the first one is the reference. */
  public void anchor(CubeOrientation reading) {
    if (reference == null && reading != null) {
      reference = CubeRotation.upright(reading);
    }
  }

  /** Forget the reference: the cube was set down mid-scramble and may be picked up any way up. */
  public void restartAnchor() {
    reference = null;
  }

  /** Sample the reading at a solve move: the frame it was made in is the delta from the reference. */
  public void onMove(CubeOrientation reading, long moveTimestampMs) {
    if (reference == null || reading == null) {
      return; // no reference yet, or no gyro: the move stands in whatever frame the last one set
    }
    CubeOrientation delta = reference.deltaTo(CubeRotation.upright(reading));
    frames.add(new Frame(CubeRotation.closest(delta), moveTimestampMs));
  }

  /**
   * The frame a reading of its own was taken in — one from before the solve's first move, which is
   * the last moment no slice has carried the core round yet. Null with no reference or no reading.
   */
  public CubeRotation frameOf(CubeOrientation reading) {
    return reference == null || reading == null ? null
        : CubeRotation.closest(reference.deltaTo(CubeRotation.upright(reading)));
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
    for (Frame frame : frames) {
      while (nextSpin < coreSpins.size()
          && coreSpins.get(nextSpin).getTimestampMs() <= frame.timestampMs) {
        Rotation spin = coreSpins.get(nextSpin++);
        rotations.add(spin);
        CubeRotation spun = CubeRotation.byNotation(spin.getNotation());
        written = written.then(spun.seenFrom(written)); // a rock turns the frame the last one left
      }
      if (frame.rotation.getNotation().equals(written.getNotation())
          || insideSlicePair(coreSpins, nextSpin, frame.timestampMs)) {
        continue;
      }
      rotations.add(new Rotation(written.to(frame.rotation).getNotation(), frame.timestampMs));
      written = frame.rotation;
    }
    for (; nextSpin < coreSpins.size(); nextSpin++) {
      rotations.add(coreSpins.get(nextSpin)); // a solve ending on a slice: its rock has no move after
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

  public void reset() {
    frames.clear();
    reference = null;
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

    Rotation(String notation, long timestampMs) {
      this(notation, timestampMs, Long.MAX_VALUE);
    }

    /** A core spin, which also knows where the pair it was measured across began. */
    Rotation(String notation, long timestampMs, long pairFromMs) {
      this.notation = notation;
      this.timestampMs = timestampMs;
      this.pairFromMs = pairFromMs;
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
  }
}
