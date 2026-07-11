package com.cube.nanotimer.smartcube.transport;

import java.util.regex.Pattern;

/** BLE UUID helpers. */
public final class BleUuid {

  private static final Pattern SHORT = Pattern.compile("^[0-9a-f]{4}$");

  private BleUuid() {}

  /**
   * Normalise a BLE UUID to a lowercase 128-bit string for comparison. Accepts 16-bit
   * short form (e.g. "0783") and expands it into the Bluetooth base UUID.
   */
  public static String normalize(String uuid) {
    String u = uuid.toLowerCase().replace("{", "").replace("}", "");
    if (SHORT.matcher(u).matches()) {
      u = "0000" + u + "-0000-1000-8000-00805f9b34fb";
    }
    return u;
  }
}
