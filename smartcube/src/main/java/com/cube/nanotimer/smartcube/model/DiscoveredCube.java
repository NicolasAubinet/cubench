package com.cube.nanotimer.smartcube.model;

/** A cube found during a scan, before connecting. */
public final class DiscoveredCube {

  private final String id;
  private final String name;
  private final CubeBrand brand;
  private final boolean needsMac;

  public DiscoveredCube(String id, String name, CubeBrand brand, boolean needsMac) {
    this.id = id;
    this.name = name;
    this.brand = brand;
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

  /** True when a MAC must be supplied because it couldn't be derived automatically. */
  public boolean needsMac() {
    return needsMac;
  }

  @Override
  public String toString() {
    return "DiscoveredCube(" + name + ", " + brand + ")";
  }
}
