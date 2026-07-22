package com.cube.nanotimer.smartcube.scanner;

import com.cube.nanotimer.smartcube.SmartCube;
import com.cube.nanotimer.smartcube.driver.CubeAdvertisement;
import com.cube.nanotimer.smartcube.driver.CubeDriver;
import com.cube.nanotimer.smartcube.driver.CubeDriverRegistry;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;
import com.cube.nanotimer.smartcube.transport.BlePeripheral;
import com.cube.nanotimer.smartcube.transport.BleScanResult;
import com.cube.nanotimer.smartcube.transport.BleTransport;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Default {@link CubeScanner}: runs a BLE scan through a {@link BleTransport}, matches each
 * advertisement against the driver registry, and connects via the winning driver. Brand
 * detection is automatic.
 */
public final class DefaultCubeScanner implements CubeScanner {

  private final BleTransport transport;
  private final Map<String, CubeAdvertisement> advertisements = new ConcurrentHashMap<>();
  private final Map<String, CubeDriver> drivers = new ConcurrentHashMap<>();

  public DefaultCubeScanner(BleTransport transport) {
    this.transport = transport;
  }

  @Override
  public void scan(Consumer<DiscoveredCube> onDiscovered) {
    transport.scan(result -> {
      CubeAdvertisement adv = toAdvertisement(result);
      CubeDriver driver = CubeDriverRegistry.INSTANCE.driverFor(adv);
      if (driver == null) {
        return;
      }
      advertisements.put(result.getDeviceId(), adv);
      drivers.put(result.getDeviceId(), driver);
      onDiscovered.accept(new DiscoveredCube(
          result.getDeviceId(), result.getName(), driver.getBrand(), driver.getModelName(adv),
          driver.getMacAddress(adv), driver.needsExplicitMac(adv)));
    });
  }

  @Override
  public SmartCube connect(DiscoveredCube cube, String macAddress) {
    CubeDriver driver = drivers.get(cube.getId());
    CubeAdvertisement adv = advertisements.get(cube.getId());
    if (driver == null || adv == null) {
      throw new IllegalStateException("Scan and discover " + cube.getId() + " before connecting");
    }
    BlePeripheral peripheral = transport.connect(cube.getId());
    return driver.connect(peripheral, adv, macAddress);
  }

  @Override
  public void stopScan() {
    transport.stopScan();
  }

  private static CubeAdvertisement toAdvertisement(BleScanResult result) {
    return new CubeAdvertisement(
        result.getDeviceId(), result.getName(), result.getServiceUuids(), result.getManufacturerData());
  }
}
