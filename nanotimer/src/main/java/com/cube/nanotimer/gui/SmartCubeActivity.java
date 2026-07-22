package com.cube.nanotimer.gui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.cube.nanotimer.R;
import com.cube.nanotimer.smartcube.SmartCube;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;
import com.cube.nanotimer.smartcube.scanner.CubeScanner;
import com.cube.nanotimer.smartcube.scanner.CubeScannerFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic bring-up screen for the smart-cube transport: scan, connect, and show the
 * connection state and battery. Superseded by the app-bar cube indicator + CubeManager in
 * a later phase.
 */
public class SmartCubeActivity extends NanoTimerActivity {

  private static final int REQUEST_BLUETOOTH_PERMISSIONS = 20;

  private CubeScanner scanner;
  private SmartCube cube;

  private TextView tvStatus;
  private Button btnDisconnect;

  private final List<DiscoveredCube> discovered = new ArrayList<>();
  private ArrayAdapter<String> discoveredAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.smart_cube_screen);
    setTitle(R.string.smart_cube_title);

    scanner = CubeScannerFactory.create(getApplicationContext());

    tvStatus = findViewById(R.id.tvSmartCubeStatus);
    btnDisconnect = findViewById(R.id.btnSmartCubeDisconnect);
    Button btnScan = findViewById(R.id.btnSmartCubeScan);
    ListView lvCubes = findViewById(R.id.lvSmartCubes);

    discoveredAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
    lvCubes.setAdapter(discoveredAdapter);

    btnScan.setOnClickListener(v -> onScanClicked());
    btnDisconnect.setOnClickListener(v -> onDisconnectClicked());
    lvCubes.setOnItemClickListener((parent, view, position, id) -> connect(discovered.get(position)));
  }

  private void onScanClicked() {
    String[] missing = missingPermissions();
    if (missing.length > 0) {
      ActivityCompat.requestPermissions(this, missing, REQUEST_BLUETOOTH_PERMISSIONS);
      return;
    }
    startScan();
  }

  private void startScan() {
    discovered.clear();
    discoveredAdapter.clear();
    tvStatus.setText(R.string.smart_cube_scanning);
    scanner.scan(found -> runOnUiThread(() -> onCubeDiscovered(found)));
  }

  private void onCubeDiscovered(DiscoveredCube found) {
    for (DiscoveredCube existing : discovered) {
      if (existing.getId().equals(found.getId())) {
        return;
      }
    }
    discovered.add(found);
    discoveredAdapter.add(found.getModelName() + " (" + found.getName() + ")");
  }

  private void connect(DiscoveredCube target) {
    scanner.stopScan();
    tvStatus.setText(getString(R.string.smart_cube_connecting, target.getName()));
    new Thread(() -> {
      try {
        SmartCube connected = scanner.connect(target);
        runOnUiThread(() -> onConnected(connected));
      } catch (Exception e) {
        runOnUiThread(() ->
            tvStatus.setText(getString(R.string.smart_cube_connect_failed, String.valueOf(e.getMessage()))));
      }
    }).start();
  }

  private void onConnected(SmartCube connected) {
    cube = connected;
    btnDisconnect.setVisibility(View.VISIBLE);
    connected.addConnectionListener(connection -> runOnUiThread(this::updateStatus));
    connected.addBatteryListener(level -> runOnUiThread(this::updateStatus));
    connected.addStateListener(state -> runOnUiThread(this::updateStatus));
    connected.requestBattery();
    updateStatus();
  }

  private void updateStatus() {
    if (cube == null) {
      return;
    }
    StringBuilder status = new StringBuilder(getString(R.string.smart_cube_connected));
    status.append(" · ").append(cube.getConnection());
    Integer battery = cube.getBatteryLevel();
    if (battery != null) {
      status.append('\n').append(getString(R.string.smart_cube_battery, battery));
    }
    status.append('\n').append(cube.getCurrentState().isSolved() ? "SOLVED" : "scrambled");
    tvStatus.setText(status.toString());
  }

  private void onDisconnectClicked() {
    disconnectCube();
    tvStatus.setText(R.string.smart_cube_status_idle);
  }

  private void disconnectCube() {
    SmartCube toDisconnect = cube;
    cube = null;
    btnDisconnect.setVisibility(View.GONE);
    if (toDisconnect != null) {
      new Thread(toDisconnect::disconnect).start();
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
      if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
        missing.add(permission);
      }
    }
    return missing.toArray(new String[0]);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode != REQUEST_BLUETOOTH_PERMISSIONS) {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
      return;
    }
    boolean granted = grantResults.length > 0;
    for (int result : grantResults) {
      if (result != PackageManager.PERMISSION_GRANTED) {
        granted = false;
      }
    }
    if (granted) {
      startScan();
    } else {
      tvStatus.setText(R.string.smart_cube_permission_denied);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    scanner.stopScan();
    disconnectCube();
  }
}
