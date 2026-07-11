package com.cube.nanotimer.smartcube.transport;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import java.util.ArrayList;
import java.util.List;

final class NordicService implements BleService {

  private final CubeBleManager manager;
  private final BluetoothGattService service;

  NordicService(CubeBleManager manager, BluetoothGattService service) {
    this.manager = manager;
    this.service = service;
  }

  @Override
  public String getUuid() {
    return BleUuid.normalize(service.getUuid().toString());
  }

  @Override
  public List<BleCharacteristic> getCharacteristics() {
    List<BleCharacteristic> out = new ArrayList<>();
    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
      out.add(new NordicCharacteristic(manager, characteristic));
    }
    return out;
  }
}
