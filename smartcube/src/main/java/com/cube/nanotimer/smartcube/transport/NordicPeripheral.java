package com.cube.nanotimer.smartcube.transport;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import no.nordicsemi.android.ble.observer.ConnectionObserver;

final class NordicPeripheral implements BlePeripheral {

  private final BluetoothDevice device;
  private final String name;
  private final CubeBleManager manager;

  NordicPeripheral(BluetoothDevice device, String name, CubeBleManager manager) {
    this.device = device;
    this.name = name;
    this.manager = manager;
  }

  @Override
  public String getId() {
    return device.getAddress();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void addConnectionListener(Consumer<Boolean> onConnected) {
    manager.setConnectionObserver(new ConnectionObserver() {
      @Override
      public void onDeviceConnecting(BluetoothDevice device) {}

      @Override
      public void onDeviceConnected(BluetoothDevice device) {}

      @Override
      public void onDeviceFailedToConnect(BluetoothDevice device, int reason) {
        onConnected.accept(false);
      }

      @Override
      public void onDeviceReady(BluetoothDevice device) {}

      @Override
      public void onDeviceDisconnecting(BluetoothDevice device) {}

      @Override
      public void onDeviceDisconnected(BluetoothDevice device, int reason) {
        onConnected.accept(false);
      }
    });
  }

  @Override
  public List<BleService> discoverServices() {
    List<BleService> out = new ArrayList<>();
    if (manager.gatt() != null) {
      for (BluetoothGattService service : manager.gatt().getServices()) {
        out.add(new NordicService(manager, service));
      }
    }
    return out;
  }

  @Override
  public void disconnect() {
    manager.disconnectDevice();
  }
}
