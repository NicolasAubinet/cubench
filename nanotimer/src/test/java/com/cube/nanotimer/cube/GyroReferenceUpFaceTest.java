package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import org.junit.Test;

/**
 * Which face is up is gravity's answer, not the anchor's. A grip taken while the cube was upside
 * down used to make upside down read as square, so a solver who connected with yellow up was drawn
 * yellow down for the rest of the session.
 */
public class GyroReferenceUpFaceTest {

  private static final CubeOrientation REST = new CubeOrientation(1, 0, 0, 0);

  /** Anchored square and held square: the grip the reference was taken in is still its own frame. */
  @Test
  public void aGripAnchoredSquareIsSquare() {
    assertEquals("", frame(REST, REST));
  }

  /** Anchored square, then turned over: the frame follows, as it always did. */
  @Test
  public void turningOverFromASquareAnchorReads() {
    assertEquals("z2", frame(REST, held("z2")));
    assertEquals("x2", frame(REST, held("x2")));
    assertEquals("z", frame(REST, held("z")));
  }

  /** ⚠️ The bug: anchored upside down, the cube is upside down, and it must say so. */
  @Test
  public void aGripAnchoredUpsideDownIsNotSquare() {
    assertEquals("z2", frame(held("z2"), held("z2")));
    assertEquals("x2", frame(held("x2"), held("x2")));
    assertEquals("z", frame(held("z"), held("z")));
  }

  /** And the cube turned back the right way up from such an anchor reads as square. */
  @Test
  public void turningBackUpFromAnUpsideDownAnchorIsSquare() {
    assertEquals("", frame(held("z2"), REST));
    assertEquals("", frame(held("x"), REST));
  }

  /**
   * Yaw is still the anchor's to give: it is arbitrary per gyro session and drifts, and nothing
   * else can say where front is. Anchoring a quarter turn round still makes that grip square.
   */
  @Test
  public void yawIsStillTakenFromTheAnchor() {
    assertEquals("", frame(aboutU(90), aboutU(90)));
    assertEquals("", frame(aboutU(-120), aboutU(-120)));
    assertEquals("y'", frame(aboutU(90), REST));
  }

  /**
   * The blast radius, stated as a test: for a grip taken with U up — every solver who connects
   * holding the cube normally — reducing it to its yaw changes nothing at all. An uprighted U-up
   * grip is already a turn about the vertical, so there is no swing to drop.
   */
  @Test
  public void aGripTakenTheRightWayUpIsUntouched() {
    for (double yaw : new double[] {0, 37, 90, -128, 180}) {
      for (double tilt : new double[] {0, 9, 25}) {
        CubeOrientation upright = CubeRotation.upright(tipped(aboutU(yaw), tilt));
        assertEquals("yaw " + yaw + " tilt " + tilt,
            0, upright.angleToDegrees(CubeRotation.yawOnly(upright)), 1e-4);
      }
    }
  }

  private static CubeOrientation tipped(CubeOrientation grip, double degrees) {
    double half = Math.toRadians(degrees) / 2;
    return grip.multiply(new CubeOrientation(Math.cos(half), Math.sin(half), 0, 0));
  }

  private static String frame(CubeOrientation anchor, CubeOrientation reading) {
    GyroReference reference = new GyroReference();
    reference.anchor(anchor);
    return reference.frameOf(reading).getNotation();
  }

  /** The cube turned by {@code rotation}: the turn relabelled into the gyro's axes, which are the
   *  cube's with y and z swapped, then applied to a square grip. */
  private static CubeOrientation held(String rotation) {
    return REST.multiply(inGyroAxes(CubeRotation.byNotation(rotation).quaternion()));
  }

  private static CubeOrientation inGyroAxes(CubeOrientation q) {
    return new CubeOrientation(q.getW(), q.getX(), -q.getZ(), q.getY());
  }

  private static CubeOrientation aboutU(double degrees) {
    double half = Math.toRadians(-degrees) / 2;
    return REST.multiply(new CubeOrientation(Math.cos(half), 0, 0, Math.sin(half)));
  }
}
