package com.cube.nanotimer.smartcube.transport;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** One advertisement packet from a scan. */
public final class BleScanResult {

  private final String deviceId;
  private final String name;
  private final List<String> serviceUuids;
  private final Map<Integer, int[]> manufacturerData;
  private final Integer rssi;

  public BleScanResult(String deviceId, String name, List<String> serviceUuids,
      Map<Integer, int[]> manufacturerData) {
    this(deviceId, name, serviceUuids, manufacturerData, null);
  }

  public BleScanResult(String deviceId, String name, List<String> serviceUuids,
      Map<Integer, int[]> manufacturerData, Integer rssi) {
    this.deviceId = deviceId;
    this.name = name;
    this.serviceUuids = serviceUuids == null ? Collections.emptyList() : serviceUuids;
    this.manufacturerData = manufacturerData == null ? Collections.emptyMap() : manufacturerData;
    this.rssi = rssi;
  }

  /** Platform device id. On Android this is the MAC; elsewhere an opaque handle. */
  public String getDeviceId() {
    return deviceId;
  }

  public String getName() {
    return name;
  }

  public List<String> getServiceUuids() {
    return serviceUuids;
  }

  /** Manufacturer-specific data keyed by company identifier code. */
  public Map<Integer, int[]> getManufacturerData() {
    return manufacturerData;
  }

  /** Received signal strength in dBm (roughly -40 close by to -100 at the edge), null if unknown. */
  public Integer getRssi() {
    return rssi;
  }
}
