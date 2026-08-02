package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.OrientationHistory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The grip every frame of a solve is measured from: the reading at the first followed scramble
 * move, uprighted. That is the one grip whose label can be known, because it is the one the solver
 * can be asked for — the end of a scramble cannot be, since cubers turn the cube mid-scramble to
 * make a {@code B} easier and put it back only sometimes.
 *
 * <p>Owned by {@link SmartCubeSolveController} and shared by everything that reads the gyro:
 * {@link RotationTracker} resolves its frames against it, and the stored gyro track is written
 * beside it. One holder rather than one per reader, so a future "reset gyro" has a single home.
 *
 * <p>The anchor fixes the initial <em>offset</em> — where front is. It does not fix drift: yaw
 * wanders over a solve and one anchor at the start cannot correct that. Which is why the track is
 * stored raw beside the reference rather than composed with it — see {@link GyroTrackFormat}.
 */
public final class GyroReference {

  private CubeOrientation reference;

  /** Feed the reading at every followed scramble move: the first one is the reference. */
  public void anchor(CubeOrientation reading) {
    if (reference == null && reading != null) {
      reference = CubeRotation.upright(reading);
    }
  }

  /** Forget it: the cube was set down mid-scramble and may be picked back up any way up. */
  public void restart() {
    reference = null;
  }

  public boolean isSet() {
    return reference != null;
  }

  /** The reference itself, for storing beside a track. Null until a scramble has been followed. */
  public CubeOrientation get() {
    return reference;
  }

  /** The frame a reading was taken in: the delta from the reference, snapped to the nearest of 24. */
  public CubeRotation frameOf(CubeOrientation reading) {
    return reference == null || reading == null ? null
        : CubeRotation.closest(reference.deltaTo(CubeRotation.upright(reading)));
  }

  /**
   * The frame a stretch of readings agrees on, or null where none of them gave one. A grip held for
   * seconds is a hundred readings, and any single one can be caught mid-tilt and snapped to a
   * neighbouring frame — which is how a blind solve turned red in front came to be named through the
   * scramble's own grip, off one reading taken while the hands were still settling.
   *
   * <p>Weighted by <b>how long each reading stands</b>, not by how many there are: readings pile up
   * while the cube is being turned, so counting them lets a peek outvote the grip it is turned back
   * to. Measured over one blind memorisation, counting made the solving grip 39% of the window and
   * timing made it 47%.
   *
   * @param untilMs when the stretch ends, so the last reading is worth the time it actually stood
   */
  public CubeRotation frameOver(List<OrientationHistory.Sample> readings, long untilMs) {
    Map<String, Long> held = new HashMap<String, Long>();
    CubeRotation best = null;
    long bestMs = -1;
    for (int i = 0; i < readings.size(); i++) {
      CubeRotation frame = frameOf(readings.get(i).getOrientation());
      if (frame == null) {
        continue;
      }
      long ends = i + 1 < readings.size() ? readings.get(i + 1).getTimestampMs() : untilMs;
      Long seen = held.get(frame.getNotation());
      long ms = (seen == null ? 0 : seen)
          + Math.max(0, ends - readings.get(i).getTimestampMs());
      held.put(frame.getNotation(), ms);
      if (ms >= bestMs) { // ties to the later reading, the nearer to what the frame is wanted for
        best = frame;
        bestMs = ms;
      }
    }
    return best;
  }
}
