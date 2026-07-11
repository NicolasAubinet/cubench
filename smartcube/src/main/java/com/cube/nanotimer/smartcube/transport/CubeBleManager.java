package com.cube.nanotimer.smartcube.transport;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.callback.DataReceivedCallback;

/**
 * Nordic {@link BleManager} for a smart cube: accepts any GATT profile (the driver
 * validates the services it needs) and exposes generic blocking connect / notify / write
 * operations. All blocking calls must run off the main thread.
 */
final class CubeBleManager extends BleManager {

  private volatile BluetoothGatt gatt;

  CubeBleManager(Context context) {
    super(context);
  }

  @Override
  protected boolean isRequiredServiceSupported(BluetoothGatt gatt) {
    this.gatt = gatt;
    return true;
  }

  @Override
  protected void initialize() {
    // The driver enables notifications and writes its handshake explicitly.
  }

  @Override
  protected void onServicesInvalidated() {
    gatt = null;
  }

  BluetoothGatt gatt() {
    return gatt;
  }

  void connectTo(BluetoothDevice device) {
    try {
      connect(device).useAutoConnect(false).retry(3, 100).timeout(15000).await();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to connect to " + device.getAddress(), e);
    }
  }

  void enableNotify(BluetoothGattCharacteristic characteristic, DataReceivedCallback callback) {
    setNotificationCallback(characteristic).with(callback);
    try {
      enableNotifications(characteristic).await();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to enable notifications", e);
    }
  }

  void writeTo(BluetoothGattCharacteristic characteristic, byte[] data) {
    try {
      writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).await();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to write characteristic", e);
    }
  }

  void disconnectDevice() {
    disconnect().enqueue();
  }
}
