package com.cube.nanotimer.smartcube.transport;

import java.util.function.Consumer;

public interface BleCharacteristic {

  String getUuid();

  /** Subscribe to notification/indication values pushed by the peripheral. */
  void addValueListener(Consumer<int[]> onValue);

  void enableNotifications();

  void write(int[] data);
}
