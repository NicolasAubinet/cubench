package com.cube.nanotimer.cube;

/** Outcome of a connect attempt, delivered on the main thread. */
public interface ConnectCallback {
  void onConnected();

  void onError(Exception e);
}
