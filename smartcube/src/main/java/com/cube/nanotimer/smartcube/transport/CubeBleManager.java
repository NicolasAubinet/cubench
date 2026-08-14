package com.cube.nanotimer.smartcube.transport;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.callback.DataReceivedCallback;

/**
 * Nordic {@link BleManager} for a smart cube: accepts any GATT profile (the driver
 * validates the services it needs) and exposes generic connect / notify / write
 * operations. {@link #connectTo} blocks and must run off the main thread; notify and
 * write are queued and return immediately, so they are safe to call from any thread.
 */
final class CubeBleManager extends BleManager {

  /** The largest an Android ATT MTU goes. A peripheral that wants less answers with less. */
  private static final int MAX_MTU = 517;

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
    // The driver enables notifications and writes its handshake explicitly. All this has to do is
    // make room for them.
    //
    // ⚠️ The MTU is not optional. A GAN or a MoYu packet is exactly 20 bytes, which is all the
    // default ATT MTU of 23 leaves for a payload — so nothing needed this until QiYi, whose hello is
    // 32 bytes out and whose state change is 96 back. A notification longer than the MTU is not
    // fragmented, it is dropped, so without this the QiYi connects, is written a truncated hello it
    // cannot read, and stays mute for good. Asked for here rather than in a driver: initialize()
    // finishes before connectTo() returns, so every handshake already has the room, and a cube that
    // does not need it is not harmed by having it.
    requestMtu(MAX_MTU).enqueue();
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
    enableNotifications(characteristic).enqueue();
  }

  void writeTo(BluetoothGattCharacteristic characteristic, byte[] data) {
    writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue();
  }

  void disconnectDevice() {
    disconnect().enqueue();
  }
}
