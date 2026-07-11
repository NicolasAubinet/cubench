package com.cube.nanotimer.smartcube.transport;

import java.util.function.Consumer;

/**
 * A thin BLE abstraction the drivers program against, so protocol code never touches the
 * Android BLE APIs directly and stays unit-testable with a fake. The concrete Android
 * implementation is the Nordic-backed transport.
 */
public interface BleTransport {

  /** Start scanning; delivers every advertisement seen. The caller filters by brand. */
  void scan(Consumer<BleScanResult> onResult);

  void stopScan();

  /** Connect to a peripheral by its platform id and discover its GATT table. Blocking. */
  BlePeripheral connect(String deviceId);
}
