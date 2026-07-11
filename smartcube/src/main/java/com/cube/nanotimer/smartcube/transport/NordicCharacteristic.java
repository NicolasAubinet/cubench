package com.cube.nanotimer.smartcube.transport;

import android.bluetooth.BluetoothGattCharacteristic;
import java.util.function.Consumer;

final class NordicCharacteristic implements BleCharacteristic {

  private final CubeBleManager manager;
  private final BluetoothGattCharacteristic characteristic;
  private volatile Consumer<int[]> valueConsumer;

  NordicCharacteristic(CubeBleManager manager, BluetoothGattCharacteristic characteristic) {
    this.manager = manager;
    this.characteristic = characteristic;
  }

  @Override
  public String getUuid() {
    return BleUuid.normalize(characteristic.getUuid().toString());
  }

  @Override
  public void addValueListener(Consumer<int[]> onValue) {
    this.valueConsumer = onValue;
  }

  @Override
  public void enableNotifications() {
    manager.enableNotify(characteristic, (device, data) -> {
      Consumer<int[]> consumer = valueConsumer;
      byte[] value = data.getValue();
      if (consumer != null && value != null) {
        consumer.accept(toIntArray(value));
      }
    });
  }

  @Override
  public void write(int[] data) {
    manager.writeTo(characteristic, toByteArray(data));
  }

  private static int[] toIntArray(byte[] bytes) {
    int[] out = new int[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      out[i] = bytes[i] & 0xff;
    }
    return out;
  }

  private static byte[] toByteArray(int[] data) {
    byte[] out = new byte[data.length];
    for (int i = 0; i < data.length; i++) {
      out[i] = (byte) data[i];
    }
    return out;
  }
}
