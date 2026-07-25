package com.cube.nanotimer.smartcube.drivers;

/** What the driver can ask a GAN cube for. */
public enum GanRequest {
  FACELETS,
  HARDWARE,
  BATTERY,

  /** Tell the cube its current position <em>is</em> solved. */
  RESET
}
