package com.cube.nanotimer.smartcube.scanner;

import android.content.Context;
import com.cube.nanotimer.smartcube.driver.CubeDriverRegistry;
import com.cube.nanotimer.smartcube.drivers.MoyuV10Driver;
import com.cube.nanotimer.smartcube.transport.BleTransport;
import com.cube.nanotimer.smartcube.transport.NordicBleTransport;

/** Creates scanners wired to the built-in brand drivers. */
public final class CubeScannerFactory {

  private static boolean driversRegistered = false;

  private CubeScannerFactory() {}

  /** Create a scanner over the real Nordic BLE transport (the app entry point). */
  public static CubeScanner create(Context context) {
    return create(new NordicBleTransport(context));
  }

  /** Create a scanner over the given transport (a fake in tests, the Nordic transport in the app). */
  public static CubeScanner create(BleTransport transport) {
    registerBuiltInDrivers();
    return new DefaultCubeScanner(transport);
  }

  private static synchronized void registerBuiltInDrivers() {
    if (driversRegistered) {
      return;
    }
    driversRegistered = true;
    CubeDriverRegistry.INSTANCE.register(new MoyuV10Driver());
  }
}
