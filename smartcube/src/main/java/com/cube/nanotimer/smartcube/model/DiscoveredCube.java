package com.cube.nanotimer.smartcube.model;

/** A cube found during a scan, before connecting. */
public final class DiscoveredCube {

  private final String id;
  private final String name;
  private final CubeBrand brand;
  private final String modelName;
  private final String macAddress;
  private final boolean needsMac;

  public DiscoveredCube(String id, String name, CubeBrand brand, String modelName,
      String macAddress, boolean needsMac) {
    this.id = id;
    this.name = name;
    this.brand = brand;
    this.modelName = modelName;
    this.macAddress = macAddress;
    this.needsMac = needsMac;
  }

  /** Platform BLE id (not the MAC — the MAC may still need deriving). */
  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public CubeBrand getBrand() {
    return brand;
  }

  /** Human-readable model label (e.g. "MoYu WeiLong V11 AI"), from the driver. */
  public String getModelName() {
    return modelName;
  }

  /** The cube's MAC address when the driver could derive it, else null. */
  public String getMacAddress() {
    return macAddress;
  }

  /** True when a MAC must be supplied because it couldn't be derived automatically. */
  public boolean needsMac() {
    return needsMac;
  }

  @Override
  public String toString() {
    return "DiscoveredCube(" + name + ", " + modelName + ")";
  }
}
