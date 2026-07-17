package com.cube.nanotimer.gui.widget;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.ConnectCallback;
import com.cube.nanotimer.cube.SmartCubeManager;
import com.cube.nanotimer.smartcube.model.CubeBatteryListener;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;
import com.cube.nanotimer.util.helper.DialogUtils;
import java.util.ArrayList;
import java.util.List;

/** Connect sheet: explains what a smart cube enables, scans, and connects on tap. */
public class SmartCubeConnectDialog extends NanoTimerDialogFragment {

  private TextView tvStatus;
  private TextView tvResyncHint;
  private Button btnResync;
  private Button btnDisconnect;
  private ListView lvCubes;

  private final List<DiscoveredCube> discovered = new ArrayList<>();
  private ArrayAdapter<String> discoveredAdapter;

  private ActivityResultLauncher<String[]> permissionLauncher;
  private ActivityResultLauncher<Intent> enableBluetoothLauncher;

  private final CubeConnectionListener connectionListener = connection -> updateUi();
  private final CubeBatteryListener batteryListener = level -> updateUi();

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    permissionLauncher = registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(), result -> {
          boolean granted = !result.isEmpty();
          for (Boolean g : result.values()) {
            granted = granted && Boolean.TRUE.equals(g);
          }
          if (granted) {
            maybeScan();
          } else {
            tvStatus.setText(R.string.smart_cube_permission_denied);
          }
        });
    enableBluetoothLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
          if (SmartCubeManager.INSTANCE.isBluetoothEnabled()) {
            startScan();
          } else {
            tvStatus.setText(R.string.smart_cube_bluetooth_off);
          }
        });
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    View v = getActivity().getLayoutInflater().inflate(R.layout.smart_cube_connect_dialog, null);
    tvStatus = v.findViewById(R.id.tvSmartCubeStatus);
    tvResyncHint = v.findViewById(R.id.tvSmartCubeResyncHint);
    btnResync = v.findViewById(R.id.btnSmartCubeResync);
    btnDisconnect = v.findViewById(R.id.btnSmartCubeDisconnect);
    lvCubes = v.findViewById(R.id.lvSmartCubes);

    btnResync.setOnClickListener(view -> {
      SmartCubeManager.INSTANCE.syncSolved();
      DialogUtils.showShortInfoMessage(getActivity(), R.string.smart_cube_resynced);
    });

    discoveredAdapter = new ArrayAdapter<>(getActivity(), R.layout.smart_cube_list_item, new ArrayList<>());
    lvCubes.setAdapter(discoveredAdapter);
    lvCubes.setOnItemClickListener((parent, view, position, id) -> connect(discovered.get(position)));

    btnDisconnect.setOnClickListener(view -> SmartCubeManager.INSTANCE.disconnect());

    AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme)
        .setView(v)
        .setPositiveButton(R.string.smart_cube_done, null)
        .create();
    dialog.setCanceledOnTouchOutside(true);
    return dialog;
  }

  @Override
  public void onStart() {
    super.onStart();
    SmartCubeManager.INSTANCE.addConnectionListener(connectionListener);
    SmartCubeManager.INSTANCE.addBatteryListener(batteryListener);
    if (SmartCubeManager.INSTANCE.isConnected()) {
      updateUi();
    } else {
      maybeScan();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    SmartCubeManager.INSTANCE.removeConnectionListener(connectionListener);
    SmartCubeManager.INSTANCE.removeBatteryListener(batteryListener);
    SmartCubeManager.INSTANCE.stopScan();
  }

  private void maybeScan() {
    String[] missing = missingPermissions();
    if (missing.length > 0) {
      permissionLauncher.launch(missing);
    } else if (!SmartCubeManager.INSTANCE.isBluetoothEnabled()) {
      tvStatus.setText(R.string.smart_cube_bluetooth_off);
      enableBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
    } else {
      startScan();
    }
  }

  private void startScan() {
    discovered.clear();
    discoveredAdapter.clear();
    tvStatus.setText(R.string.smart_cube_scanning);
    try {
      SmartCubeManager.INSTANCE.startScan(this::onDiscovered);
    } catch (Exception e) {
      // The adapter can be switched off between the check above and the scan starting.
      tvStatus.setText(R.string.smart_cube_bluetooth_off);
    }
  }

  private void onDiscovered(DiscoveredCube found) {
    for (DiscoveredCube existing : discovered) {
      if (existing.getId().equals(found.getId())) {
        return;
      }
    }
    discovered.add(found);
    discoveredAdapter.add(found.getName() + " (" + found.getBrand() + ")");
  }

  private void connect(DiscoveredCube target) {
    SmartCubeManager.INSTANCE.stopScan();
    tvStatus.setText(getString(R.string.smart_cube_connecting, target.getName()));
    SmartCubeManager.INSTANCE.connect(target, new ConnectCallback() {
      @Override
      public void onConnected() {
        updateUi();
      }

      @Override
      public void onError(Exception e) {
        if (!isAdded()) {
          return;
        }
        tvStatus.setText(getString(R.string.smart_cube_connect_failed, String.valueOf(e.getMessage())));
      }
    });
  }

  private void updateUi() {
    if (!isAdded()) {
      return; // a connect callback can land after the dialog is dismissed
    }
    boolean connected = SmartCubeManager.INSTANCE.isConnected();
    boolean wasConnected = btnDisconnect.getVisibility() == View.VISIBLE;
    btnDisconnect.setVisibility(connected ? View.VISIBLE : View.GONE);
    btnResync.setVisibility(connected ? View.VISIBLE : View.GONE);
    tvResyncHint.setVisibility(connected ? View.VISIBLE : View.GONE);
    lvCubes.setVisibility(connected ? View.GONE : View.VISIBLE);
    if (connected) {
      StringBuilder status = new StringBuilder(getString(R.string.smart_cube_connected));
      DiscoveredCube device = SmartCubeManager.INSTANCE.getConnectedDevice();
      if (device != null) {
        status.append(" · ").append(device.getName());
      }
      Integer battery = SmartCubeManager.INSTANCE.getBattery();
      if (battery != null) {
        status.append('\n').append(getString(R.string.smart_cube_battery, battery));
      }
      tvStatus.setText(status.toString());
    } else if (wasConnected) {
      startScan(); // user disconnected or the cube dropped → back to scanning
    }
  }

  private String[] missingPermissions() {
    List<String> required = new ArrayList<>();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      required.add(Manifest.permission.BLUETOOTH_SCAN);
      required.add(Manifest.permission.BLUETOOTH_CONNECT);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      required.add(Manifest.permission.ACCESS_FINE_LOCATION);
    }
    List<String> missing = new ArrayList<>();
    for (String permission : required) {
      if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
        missing.add(permission);
      }
    }
    return missing.toArray(new String[0]);
  }
}
