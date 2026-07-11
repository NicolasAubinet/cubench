package com.cube.nanotimer.smartcube.driver;

import java.util.ArrayList;
import java.util.List;

/**
 * Global registry of brand drivers. Each driver is registered once; the scanner asks the
 * registry which driver handles a discovered advertisement.
 */
public enum CubeDriverRegistry {
  INSTANCE;

  private final List<CubeDriver> drivers = new ArrayList<>();

  public void register(CubeDriver driver) {
    drivers.add(driver);
  }

  public CubeDriver driverFor(CubeAdvertisement adv) {
    for (CubeDriver driver : drivers) {
      if (driver.matches(adv)) {
        return driver;
      }
    }
    return null;
  }
}
