package com.cube.nanotimer.smartcube.scanner;

import com.cube.nanotimer.smartcube.transport.BleCharacteristic;
import com.cube.nanotimer.smartcube.transport.BlePeripheral;
import com.cube.nanotimer.smartcube.transport.BleScanResult;
import com.cube.nanotimer.smartcube.transport.BleService;
import com.cube.nanotimer.smartcube.transport.BleTransport;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A BLE stack that answers from memory, so scan → connect → decode can be driven end to end without
 * hardware. Shared by every driver's scanner test: what differs between brands is the UUIDs and the
 * packets, never the transport.
 */
final class FakeBle {

  private FakeBle() {
  }

  static final class Chr implements BleCharacteristic {
    private final String uuid;
    private final List<Consumer<int[]>> listeners = new ArrayList<>();
    final List<int[]> written = new ArrayList<>();

    Chr(String uuid) {
      this.uuid = uuid;
    }

    /** Deliver a notification, as the peripheral would. */
    void push(int[] value) {
      for (Consumer<int[]> listener : listeners) {
        listener.accept(value);
      }
    }

    @Override
    public String getUuid() {
      return uuid;
    }

    @Override
    public void addValueListener(Consumer<int[]> onValue) {
      listeners.add(onValue);
    }

    @Override
    public void enableNotifications() {}

    @Override
    public void write(int[] data) {
      written.add(data);
    }
  }

  static final class Service implements BleService {
    private final String uuid;
    private final List<BleCharacteristic> characteristics;

    Service(String uuid, List<BleCharacteristic> characteristics) {
      this.uuid = uuid;
      this.characteristics = characteristics;
    }

    @Override
    public String getUuid() {
      return uuid;
    }

    @Override
    public List<BleCharacteristic> getCharacteristics() {
      return characteristics;
    }
  }

  static final class Peripheral implements BlePeripheral {
    private final String id;
    private final String name;
    private final List<BleService> services;

    Peripheral(String id, String name, List<BleService> services) {
      this.id = id;
      this.name = name;
      this.services = services;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public void addConnectionListener(Consumer<Boolean> onConnected) {}

    @Override
    public List<BleService> discoverServices() {
      return services;
    }

    @Override
    public void disconnect() {}
  }

  static final class Transport implements BleTransport {
    private final BlePeripheral peripheral;
    private final BleScanResult result;

    Transport(BlePeripheral peripheral, BleScanResult result) {
      this.peripheral = peripheral;
      this.result = result;
    }

    @Override
    public void scan(Consumer<BleScanResult> onResult) {
      onResult.accept(result);
    }

    @Override
    public void stopScan() {}

    @Override
    public BlePeripheral connect(String deviceId) {
      return peripheral;
    }
  }
}
