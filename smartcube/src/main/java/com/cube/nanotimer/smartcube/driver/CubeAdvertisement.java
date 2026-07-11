package com.cube.nanotimer.smartcube.driver;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Advertisement data a driver inspects to decide whether it handles a device. */
public final class CubeAdvertisement {

  private final String id;
  private final String name;
  private final List<String> serviceUuids;
  private final Map<Integer, int[]> manufacturerData;

  public CubeAdvertisement(String id, String name, List<String> serviceUuids,
      Map<Integer, int[]> manufacturerData) {
    this.id = id;
    this.name = name;
    this.serviceUuids = serviceUuids == null ? Collections.emptyList() : serviceUuids;
    this.manufacturerData = manufacturerData == null ? Collections.emptyMap() : manufacturerData;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public List<String> getServiceUuids() {
    return serviceUuids;
  }

  /** Manufacturer data keyed by company identifier code. Used for MAC extraction. */
  public Map<Integer, int[]> getManufacturerData() {
    return manufacturerData;
  }
}
