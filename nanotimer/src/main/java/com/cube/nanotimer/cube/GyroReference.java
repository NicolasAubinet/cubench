package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.OrientationHistory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The grip every frame of a solve is measured from, and every pose the live mirror is drawn at:
 * one uprighted reading, taken once per gyro session and held for the whole of it.
 *
 * <p>Owned by {@link SmartCubeManager}, which takes it, and shared by everything that reads the
 * gyro: {@link RotationTracker} resolves its frames against it, the live mirror draws against it,
 * and the stored gyro track is written beside it. One holder rather than one per reader.
 *
 * <p>⚠️ <b>It used to be re-taken at the first move of every scramble, and that is the fault this
 * replaced.</b> Uprighting already settles the up face from gravity, so a fresh reading adds
 * nothing but the yaw — and yaw wanders by hundredths of a degree a minute, so one datum outlives a
 * whole session of solving. What re-taking it did add was a jump: the grip you finish a solve in is
 * not the grip you start scrambling in, and at that first move the cube on screen swung by the
 * difference. Taken once, there is nothing to swing.
 *
 * <p>The anchor fixes the <em>offset</em> — where front is. It does not fix drift, which is why the
 * track is stored raw beside the reference rather than composed with it — see
 * {@link GyroTrackFormat}.
 *
 * <p>⚠️ <b>Only the yaw is the anchor's to give.</b> The fusion is gravity-referenced, so which
 * face is up is absolute and cannot drift; yaw is arbitrary per gyro session and can. Holding the
 * whole grip made whatever face was up when it was taken read as the top of the cube, so a solver
 * who connected with yellow up, or whose cube dropped and reconnected while it sat on the table,
 * was drawn yellow down for the rest of the session. See {@link CubeRotation#yawOnly}.
 */
public final class GyroReference {

  // Volatile because the live mirror's page polls it from the WebView's own thread, while the
  // manager anchors it on the main one.
  private volatile CubeOrientation reference;

  /**
   * The grip to measure from, <b>reduced to its yaw</b>, replacing whatever stood before. Deliberately overwrites: it is taken
   * at moments that mean it — a fresh connection, or the solver saying so — rather than fed
   * speculatively, so the newest answer is the right one. A reading that is not there anchors
   * nothing, and leaves the previous grip standing.
   */
  public void anchor(CubeOrientation reading) {
    if (reading != null) {
      reference = CubeRotation.yawOnly(CubeRotation.upright(reading));
    }
  }

  /** Forget it: the gyro zero it was measured against went with the connection. */
  public void restart() {
    reference = null;
  }

  public boolean isSet() {
    return reference != null;
  }

  /** The reference itself, for storing beside a track. Null until a session has anchored one. */
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
