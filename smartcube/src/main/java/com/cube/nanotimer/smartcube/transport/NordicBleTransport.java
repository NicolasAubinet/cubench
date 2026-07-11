package com.cube.nanotimer.smartcube.transport;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.SparseArray;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanRecord;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

/**
 * {@link BleTransport} backed by the Nordic Android-BLE-Library and its scanner-compat.
 * The single file in the module that touches Android BLE. Callers must hold the runtime
 * BLUETOOTH_SCAN / BLUETOOTH_CONNECT permissions, and run {@link #connect} off the main
 * thread (the Nordic requests block until complete).
 */
public final class NordicBleTransport implements BleTransport {

  private final Context context;
  private final Map<String, BluetoothDevice> devices = new ConcurrentHashMap<>();
  private final Map<String, String> names = new ConcurrentHashMap<>();
  private ScanCallback scanCallback;

  public NordicBleTransport(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void scan(Consumer<BleScanResult> onResult) {
    stopScan();
    scanCallback = new ScanCallback() {
      @Override
      public void onScanResult(int callbackType, ScanResult result) {
        BleScanResult scanResult = toResult(result);
        devices.put(scanResult.getDeviceId(), result.getDevice());
        if (scanResult.getName() != null) {
          names.put(scanResult.getDeviceId(), scanResult.getName());
        }
        onResult.accept(scanResult);
      }
    };
    ScanSettings settings = new ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setReportDelay(0)
        .setUseHardwareBatchingIfSupported(false)
        .build();
    BluetoothLeScannerCompat.getScanner().startScan(new ArrayList<>(), settings, scanCallback);
  }

  @Override
  public void stopScan() {
    if (scanCallback != null) {
      BluetoothLeScannerCompat.getScanner().stopScan(scanCallback);
      scanCallback = null;
    }
  }

  @Override
  public BlePeripheral connect(String deviceId) {
    BluetoothDevice device = devices.get(deviceId);
    if (device == null) {
      throw new IllegalStateException("Unknown device " + deviceId + " — scan before connecting");
    }
    CubeBleManager manager = new CubeBleManager(context);
    manager.connectTo(device);
    return new NordicPeripheral(device, names.get(deviceId), manager);
  }

  private static BleScanResult toResult(ScanResult result) {
    ScanRecord record = result.getScanRecord();
    String name = record != null ? record.getDeviceName() : null;
    List<String> serviceUuids = new ArrayList<>();
    Map<Integer, int[]> manufacturerData = new HashMap<>();
    if (record != null) {
      if (record.getServiceUuids() != null) {
        for (ParcelUuid uuid : record.getServiceUuids()) {
          serviceUuids.add(BleUuid.normalize(uuid.getUuid().toString()));
        }
      }
      SparseArray<byte[]> msd = record.getManufacturerSpecificData();
      if (msd != null) {
        for (int i = 0; i < msd.size(); i++) {
          manufacturerData.put(msd.keyAt(i), toIntArray(msd.valueAt(i)));
        }
      }
    }
    return new BleScanResult(result.getDevice().getAddress(), name, serviceUuids, manufacturerData);
  }

  private static int[] toIntArray(byte[] bytes) {
    if (bytes == null) {
      return new int[0];
    }
    int[] out = new int[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      out[i] = bytes[i] & 0xff;
    }
    return out;
  }
}
