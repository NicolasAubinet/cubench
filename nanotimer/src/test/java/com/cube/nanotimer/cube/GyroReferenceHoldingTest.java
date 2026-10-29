package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import org.junit.Test;

/** A declared grip fixes the reference exactly, however far the yaw has drifted. */
public class GyroReferenceHoldingTest {

  @Test
  public void aDeclaredGripIsDrawnSquareAtAnyDrift() {
    for (CubeRotation grip : CubeRotation.all()) {
      for (double drift : new double[] {0, 7, -30, 44, 46, 60, -100, 135, 180}) {
        GyroReference reference = new GyroReference();
        assertTrue(grip + " drift " + drift,
            reference.anchorHolding(reading(grip, drift, 10), grip));
        CubeOrientation pose =
            CubeRotation.continuousFrame(reference.get(), reading(grip, drift, 0));
        assertEquals(grip + " drift " + drift, 0, pose.angleToDegrees(grip.quaternion()), 0.5);
      }
    }
  }

  @Test
  public void anotherFaceOnTopAnchorsNothing() {
    GyroReference reference = new GyroReference();
    CubeRotation yellowUp = CubeRotation.holding('D', 'F');
    assertFalse(reference.anchorHolding(reading(CubeRotation.byNotation(""), 20, 0), yellowUp));
    assertFalse(reference.isSet());
  }

  /** Held at {@code grip}, tipped towards the solver by {@code tilt}, yaw off by {@code drift}. */
  private static CubeOrientation reading(CubeRotation grip, double drift, double tilt) {
    CubeOrientation q = grip.quaternion();
    CubeOrientation held = new CubeOrientation(q.getW(), q.getX(), -q.getZ(), q.getY());
    return about(1, 0, 0, tilt).multiply(about(0, 0, 1, drift)).multiply(held);
  }

  private static CubeOrientation about(double x, double y, double z, double degrees) {
    double half = Math.toRadians(degrees) / 2;
    double s = Math.sin(half);
    return new CubeOrientation(Math.cos(half), x * s, y * s, z * s);
  }
}
