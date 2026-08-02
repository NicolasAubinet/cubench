package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.OrientationHistory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The stored form of a solve's gyro track: the small physical rotations the solver actually made,
 * which the discrete {@code x}/{@code y}/{@code z} tokens of {@link SolveMovesFormat} throw away.
 * Peeking at a piece is turning of exactly this kind, and it is the whole of what the track shows.
 *
 * <p><strong>Keyframes, not a sample dump.</strong> The cube reports at ~20 Hz; a reading is kept
 * only where it says something — the orientation has moved past {@link #KEYFRAME_DEGREES} since the
 * last kept one, or the cube has just stopped moving and this is the pose it arrived at. Playback
 * SLERPs between them. The reduction is only 1.3–3×: a solve has almost no motionless stretches to
 * drop, so the angular threshold does nearly all the work.
 *
 * <p><strong>Measured on a real 24.9 s solve: 320 keyframes, 4.3 KB, 12.8 keyframes/s.</strong>
 * {@link #KEYFRAME_DEGREES} is the fidelity dial, and 5° reconstructs to within 4–6° at the 90th
 * percentile <em>while the cube is being held or peeked at</em> — the error that matters, the rest
 * being fast flicks that are a blur on screen anyway. {@link #STILL_DEGREES} is the second dial:
 * about a fifth of those keyframes are arrival poses no threshold would have kept.
 *
 * <p><strong>The readings are stored raw, with the reference beside them rather than composed
 * in.</strong> Composing at record time would bake today's anchor rule into the data and make it
 * non-re-derivable — the same trap that leaves the rotation tokens unfixable in hindsight. Raw plus
 * a reference costs 8 more bytes for the solve and keeps everything derived recomputable, so a later
 * change to the anchor rule, or a re-anchoring that corrects yaw drift, applies to every solve
 * already captured. Yaw drift is real and unmeasured; nothing here tries to correct it.
 *
 * <p>Binary rather than the readable text {@link SolveMovesFormat} uses, because a thousand
 * quaternions are not readable at any encoding: a version byte, the reference, then one record per
 * keyframe, base64'd. Encoded here rather than through {@code android.util.Base64} so the format
 * can be exercised by a plain unit test, and {@code java.util.Base64} is API 26 against minSdk 21.
 */
public final class GyroTrackFormat {

  /**
   * How far the cube must have turned since the last kept reading for the next one to be kept. The
   * fidelity dial: 3° costs about twice 10° and reconstructs about three times as closely.
   */
  private static final double KEYFRAME_DEGREES = 5;

  /**
   * A step smaller than this between consecutive readings is the gyro's noise, not the solver. Used
   * only to notice that the cube has stopped, so the pose it stopped in is kept exactly rather than
   * up to {@link #KEYFRAME_DEGREES} out — an arrival pose is the one the eye actually reads.
   */
  private static final double STILL_DEGREES = 2;

  /** One record: a uint16 gap from the previous keyframe, then w, x, y, z as int16. */
  private static final int RECORD_BYTES = 10;
  private static final int HEADER_BYTES = 1 + 8; // version, then the reference quaternion
  private static final byte VERSION = 1;
  private static final int MAX_GAP_MS = 0xFFFF;
  private static final double SCALE = 32767;

  private GyroTrackFormat() {
  }

  /**
   * Keyframes {@code samples} and encodes them, or returns null where there is nothing worth
   * storing — no gyro on the cube, or a window that has already fallen out of the sample buffer.
   *
   * @param solveStartMs the moment offsets are measured from, the same origin
   *     {@link SolveMovesFormat} gives the moves, so a keyframe and a turn can be read against each
   *     other directly. Samples are filed under the host clock and moves under the cube's own,
   *     fitted to it — the same near-identity the slice and pick-up reads already rely on.
   * @param reference the grip the solve's frames are measured from
   *     ({@link GyroReference#get()}), or null where no scramble was followed to anchor one
   */
  public static String format(List<OrientationHistory.Sample> samples, CubeOrientation reference,
      long solveStartMs) {
    List<Keyframe> keyframes = keyframes(samples, solveStartMs);
    if (keyframes.isEmpty()) {
      return null;
    }
    byte[] bytes = new byte[HEADER_BYTES + RECORD_BYTES * keyframes.size()];
    bytes[0] = VERSION;
    // An absent reference goes in as four zeros, which no unit quaternion can be.
    putQuaternion(bytes, 1, reference);
    int at = HEADER_BYTES;
    long previousMs = 0;
    for (Keyframe keyframe : keyframes) {
      putUint16(bytes, at, (int) (keyframe.offsetMs - previousMs));
      putQuaternion(bytes, at + 2, keyframe.orientation);
      previousMs = keyframe.offsetMs;
      at += RECORD_BYTES;
    }
    return base64(bytes);
  }

  /**
   * The track as the orientations a 3D cube should be drawn at — the reference applied, so the
   * poses are in the cube's own axes and a renderer can use them directly. Empty for anything that
   * is not a readable track.
   *
   * <p>Where the track carries no reference — a solve whose scramble was never followed, so no grip
   * could be labelled — <b>its own first pose stands in</b>. The cube then starts square and shows
   * every turn the solver made relative to that, which is the whole point of the track; only the
   * absolute grip is lost, and it was never known for those solves anyway.
   */
  public static List<Keyframe> posesOf(String stored) {
    return posesOf(stored, Collections.<SolveSolution.FrameAt>emptyList());
  }

  /**
   * As above, but as the <b>residual</b> left once the frame the reconstruction already has the
   * cube in is taken back out.
   *
   * <p>⚠️ <b>Without this the cube is turned twice.</b> A replay animates the solver's rotations, so
   * the cube on screen is already reoriented; the gyro track measures that same physical turning
   * from the scramble reference. Rotating the puzzle object by the whole pose on top of that turns
   * it a second time and the last layer comes up under the wrong face. What the renderer wants is
   * only the part the reconstruction does not already express: the tilts and peeks, which is the
   * whole reason the track is kept.
   *
   * <p>⚠️ <b>And the frame is NOT the emitted rotation tokens.</b> A slice rocks the core, turning
   * the frame while emitting no token, so re-deriving the frame from the tokens under-counts it —
   * measured on the Roux capture, the residual then still swings a full 180°. It comes from
   * {@link SolveSolution#framesOf}, which is the reconstruction's own accumulated frame.
   *
   * <p>Both are world-frame rotations from the same reference, so the wobble the eye should see is
   * {@code D = P·F⁻¹}, and the renderer draws {@code D·F} — the cube at its reconstructed frame,
   * perturbed by exactly how far the real one sat from it.
   *
   * @param frames the reconstruction's frame over time ({@link SolveSolution#framesOf})
   */
  public static List<Keyframe> posesOf(String stored, List<SolveSolution.FrameAt> frames) {
    GyroTrack track = parse(stored);
    if (track == null) {
      return new ArrayList<Keyframe>();
    }
    CubeOrientation reference = track.getReference() != null ? track.getReference()
        : track.getKeyframes().get(0).getOrientation();
    List<Keyframe> poses = new ArrayList<Keyframe>();
    CubeRotation frame = CubeRotation.byNotation("");
    int next = 0;
    for (Keyframe keyframe : track.getKeyframes()) {
      while (next < frames.size() && frames.get(next).getOffsetMs() <= keyframe.getOffsetMs()) {
        frame = frames.get(next++).getFrame();
      }
      CubeOrientation physical =
          CubeRotation.continuousFrame(reference, keyframe.getOrientation());
      poses.add(new Keyframe(keyframe.getOffsetMs(),
          physical.multiply(frame.quaternion().inverse())));
    }
    return poses;
  }

  /** Reads a stored track back, or null for anything it cannot read as one. */
  public static GyroTrack parse(String stored) {
    byte[] bytes = unbase64(stored);
    if (bytes == null || bytes.length < HEADER_BYTES + RECORD_BYTES
        || bytes[0] != VERSION
        || (bytes.length - HEADER_BYTES) % RECORD_BYTES != 0) {
      return null;
    }
    CubeOrientation reference = getQuaternion(bytes, 1);
    List<Keyframe> keyframes = new ArrayList<Keyframe>();
    long offsetMs = 0;
    for (int at = HEADER_BYTES; at < bytes.length; at += RECORD_BYTES) {
      offsetMs += getUint16(bytes, at);
      keyframes.add(new Keyframe(offsetMs, getQuaternion(bytes, at + 2)));
    }
    return new GyroTrack(reference, keyframes);
  }

  /**
   * The readings worth keeping, oldest first. The first and last are always kept, so the track
   * spans the window it was cut from and playback has a pose to start and end on.
   */
  private static List<Keyframe> keyframes(List<OrientationHistory.Sample> samples,
      long solveStartMs) {
    List<Keyframe> keyframes = new ArrayList<Keyframe>();
    if (samples == null || samples.isEmpty()) {
      return keyframes;
    }
    CubeOrientation kept = null;
    boolean moving = false;
    for (int i = 0; i < samples.size(); i++) {
      CubeOrientation reading = samples.get(i).getOrientation();
      double step = i == 0 ? 0 : reading.angleToDegrees(samples.get(i - 1).getOrientation());
      boolean stopped = moving && step < STILL_DEGREES; // the pose the cube arrived in
      moving = step >= STILL_DEGREES;
      if (kept == null || stopped || i == samples.size() - 1
          || reading.angleToDegrees(kept) >= KEYFRAME_DEGREES) {
        add(keyframes, samples.get(i).getTimestampMs() - solveStartMs, reading);
        kept = reading;
      }
    }
    return keyframes;
  }

  /**
   * Appends a keyframe, holding the last pose across any gap too long for a record's uint16. A
   * blind solve memorised with the cube held still is the case: nothing moves, so nothing is kept,
   * and 65 s later the gap no longer fits.
   */
  private static void add(List<Keyframe> keyframes, long offsetMs, CubeOrientation reading) {
    if (!keyframes.isEmpty()) {
      Keyframe previous = keyframes.get(keyframes.size() - 1);
      for (long gapMs = offsetMs - previous.offsetMs; gapMs > MAX_GAP_MS; gapMs -= MAX_GAP_MS) {
        keyframes.add(new Keyframe(keyframes.get(keyframes.size() - 1).offsetMs + MAX_GAP_MS,
            previous.orientation));
      }
    }
    keyframes.add(new Keyframe(Math.max(0, offsetMs), reading));
  }

  /** A solve's gyro track: where it was measured from, and the poses the cube passed through. */
  public static final class GyroTrack {

    private final CubeOrientation reference;
    private final List<Keyframe> keyframes;

    private GyroTrack(CubeOrientation reference, List<Keyframe> keyframes) {
      this.reference = reference;
      this.keyframes = keyframes;
    }

    /** The grip the solve's frames are measured from, or null where none was anchored. */
    public CubeOrientation getReference() {
      return reference;
    }

    public List<Keyframe> getKeyframes() {
      return keyframes;
    }
  }

  /** One kept reading, at its offset from the solve's start. Playback SLERPs between them. */
  public static final class Keyframe {

    private final long offsetMs;
    private final CubeOrientation orientation;

    private Keyframe(long offsetMs, CubeOrientation orientation) {
      this.offsetMs = offsetMs;
      this.orientation = orientation;
    }

    public long getOffsetMs() {
      return offsetMs;
    }

    public CubeOrientation getOrientation() {
      return orientation;
    }
  }

  private static void putQuaternion(byte[] bytes, int at, CubeOrientation q) {
    if (q == null) {
      return; // four zeros: the absent reference, which no unit quaternion can be mistaken for
    }
    putInt16(bytes, at, q.getW());
    putInt16(bytes, at + 2, q.getX());
    putInt16(bytes, at + 4, q.getY());
    putInt16(bytes, at + 6, q.getZ());
  }

  /**
   * Renormalised, and it matters far more than it looks: rounding four components to int16 leaves
   * the norm up to 1.5e-5 off, and an angle read off a quaternion that is merely <em>near</em> unit
   * comes out ~0.6° wrong — two orders worse than the 0.003° the rounding itself costs, because
   * {@code acos} near 1 turns a tiny norm error into a visible angle.
   */
  private static CubeOrientation getQuaternion(byte[] bytes, int at) {
    CubeOrientation q = new CubeOrientation(getInt16(bytes, at), getInt16(bytes, at + 2),
        getInt16(bytes, at + 4), getInt16(bytes, at + 6));
    double norm = Math.sqrt(q.normSquared());
    return norm == 0 ? null // the four zeros an absent reference goes in as
        : new CubeOrientation(q.getW() / norm, q.getX() / norm, q.getY() / norm, q.getZ() / norm);
  }

  private static void putInt16(byte[] bytes, int at, double component) {
    putUint16(bytes, at, (int) Math.round(Math.max(-1, Math.min(1, component)) * SCALE) & 0xFFFF);
  }

  private static double getInt16(byte[] bytes, int at) {
    return (short) getUint16(bytes, at) / SCALE;
  }

  private static void putUint16(byte[] bytes, int at, int value) {
    bytes[at] = (byte) (value >> 8);
    bytes[at + 1] = (byte) value;
  }

  private static int getUint16(byte[] bytes, int at) {
    return ((bytes[at] & 0xFF) << 8) | (bytes[at + 1] & 0xFF);
  }

  private static final String ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

  /** Standard base64, without the padding a column of our own has no use for. */
  private static String base64(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (int at = 0; at < bytes.length; at += 3) {
      int taken = Math.min(3, bytes.length - at);
      int group = 0;
      for (int i = 0; i < 3; i++) {
        group |= (i < taken ? bytes[at + i] & 0xFF : 0) << (16 - 8 * i);
      }
      for (int i = 0; i <= taken; i++) {
        sb.append(ALPHABET.charAt((group >> (18 - 6 * i)) & 0x3F));
      }
    }
    return sb.toString();
  }

  private static byte[] unbase64(String encoded) {
    if (encoded == null || encoded.isEmpty() || encoded.length() % 4 == 1) {
      return null;
    }
    byte[] bytes = new byte[encoded.length() * 3 / 4];
    int out = 0;
    for (int at = 0; at < encoded.length(); at += 4) {
      int taken = Math.min(4, encoded.length() - at);
      int group = 0;
      for (int i = 0; i < 4; i++) {
        int digit = i < taken ? ALPHABET.indexOf(encoded.charAt(at + i)) : 0;
        if (digit < 0) {
          return null;
        }
        group |= digit << (18 - 6 * i);
      }
      for (int i = 0; i < taken - 1; i++) {
        bytes[out++] = (byte) (group >> (16 - 8 * i));
      }
    }
    return bytes;
  }
}
