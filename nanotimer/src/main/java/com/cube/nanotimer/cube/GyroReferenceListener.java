package com.cube.nanotimer.cube;

/** Told when the grip everything is measured from has been taken, re-taken, or forgotten. */
public interface GyroReferenceListener {

  void onGyroReferenceChanged();
}
