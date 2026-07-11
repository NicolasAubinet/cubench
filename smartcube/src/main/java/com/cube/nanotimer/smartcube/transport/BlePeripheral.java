package com.cube.nanotimer.smartcube.transport;

import java.util.List;
import java.util.function.Consumer;

/** A connected peripheral. */
public interface BlePeripheral {

  String getId();

  String getName();

  /** Notified with {@code false} when the link drops. */
  void addConnectionListener(Consumer<Boolean> onConnected);

  List<BleService> discoverServices();

  void disconnect();
}
