package com.cube.nanotimer.cube;

/**
 * Whether the smart cube features are offered at all — the cube connection, the drills, the smart
 * cube settings, and the method a solve type is read as.
 *
 * <p>It is false in a build that ships while the smart cube is unfinished, and the intended way to
 * make it false is one commit on a release branch cut for the occasion: this constant, the Bluetooth
 * permissions out of the manifest, the version and the release notes. Master keeps the feature on,
 * so what is developed is what is tested every day, and nothing has to be held back on a branch or
 * reverted afterwards.
 *
 * <p>Every door the feature has asks this constant. What lies behind a connected cube — a solve's
 * breakdown, the live cube, the scramble follower, the drill screens — needs no check of its own,
 * since without those doors nothing can connect and no solve carries cube data. A door added later
 * has to ask here too.
 *
 * <p>The check that a release really is free of it is the built APK rather than this line: dump its
 * permissions and see no {@code BLUETOOTH_*} and no {@code ACCESS_FINE_LOCATION}. A door left
 * unasked is a door onto a feature that cannot work, and that is what the dump catches.
 */
public final class SmartCubeGate {

  public static final boolean ENABLED = false;

  private SmartCubeGate() {
  }
}
