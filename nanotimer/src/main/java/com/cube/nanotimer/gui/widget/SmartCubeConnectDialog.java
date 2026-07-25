package com.cube.nanotimer.gui.widget;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.ConnectCallback;
import com.cube.nanotimer.cube.SmartCubeManager;
import com.cube.nanotimer.smartcube.model.CubeBatteryListener;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.view.SmartCubeRadarView;
import java.util.ArrayList;
import java.util.List;

/**
 * The smart-cube sheet: explains what a smart cube buys you, scans for one, and connects on tap.
 *
 * <p>It opens on the explanation the first time and on the pairing panel every time after that, so
 * the pitch is made once rather than sat between the user and their cube forever. The help button
 * brings the explanation back on demand.
 */
public class SmartCubeConnectDialog extends NanoTimerBottomSheetFragment {

  private View introPanel;
  private View pairPanel;
  private Button btnIntroContinue;
  private View pairActions;
  private ImageButton btnHelp;

  private SmartCubeRadarView radar;
  private ImageView imgGlyph;
  private TextView tvPill;
  private TextView tvTitle;
  private TextView tvBody;
  private Button btnFix;
  private TextView tvListLabel;
  private LinearLayout cubeList;
  private View resyncBlock;
  private Button btnResync;
  private Button btnDisconnect;

  private final List<DiscoveredCube> discovered = new ArrayList<>();
  private boolean scanning;

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
            showPermissionNeeded();
          }
        });
    enableBluetoothLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
          if (SmartCubeManager.INSTANCE.isBluetoothEnabled()) {
            startScan();
          } else {
            showBluetoothOff();
          }
        });
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
    View v = inflater.inflate(R.layout.smart_cube_connect_dialog, container, false);

    introPanel = v.findViewById(R.id.smartCubeIntroPanel);
    pairPanel = v.findViewById(R.id.smartCubePairPanel);
    btnIntroContinue = v.findViewById(R.id.buSmartCubeIntroContinue);
    pairActions = v.findViewById(R.id.smartCubePairActions);

    radar = v.findViewById(R.id.smartCubeRadar);
    imgGlyph = v.findViewById(R.id.imgSmartCubeGlyph);
    tvPill = v.findViewById(R.id.tvSmartCubeStatusPill);
    tvTitle = v.findViewById(R.id.tvSmartCubeStatusTitle);
    tvBody = v.findViewById(R.id.tvSmartCubeStatusBody);
    btnFix = v.findViewById(R.id.buSmartCubeFix);
    tvListLabel = v.findViewById(R.id.tvSmartCubeListLabel);
    cubeList = v.findViewById(R.id.smartCubeList);
    resyncBlock = v.findViewById(R.id.smartCubeResyncBlock);
    btnResync = v.findViewById(R.id.btnSmartCubeResync);
    btnDisconnect = v.findViewById(R.id.btnSmartCubeDisconnect);

    btnHelp = v.findViewById(R.id.buSmartCubeHelp);
    btnHelp.setOnClickListener(view -> showIntro());
    btnIntroContinue.setOnClickListener(view -> {
      Options.INSTANCE.setSmartCubeIntroSeen(true);
      showPairing();
    });

    btnResync.setOnClickListener(view -> {
      SmartCubeManager.INSTANCE.syncSolved();
      DialogUtils.showShortInfoMessage(getActivity(), R.string.smart_cube_resynced);
    });
    btnDisconnect.setOnClickListener(view -> SmartCubeManager.INSTANCE.disconnect());
    v.findViewById(R.id.buSmartCubeDone).setOnClickListener(view -> dismiss());

    return v;
  }

  @Override
  public void onStart() {
    super.onStart();
    SmartCubeManager.INSTANCE.addConnectionListener(connectionListener);
    SmartCubeManager.INSTANCE.addBatteryListener(batteryListener);
    // A cube already connected is the answer to "what is this", so the pitch is skipped for it.
    if (!Options.INSTANCE.isSmartCubeIntroSeen() && !SmartCubeManager.INSTANCE.isConnected()) {
      showIntro();
    } else {
      showPairing();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    SmartCubeManager.INSTANCE.removeConnectionListener(connectionListener);
    SmartCubeManager.INSTANCE.removeBatteryListener(batteryListener);
    stopScan();
  }

  /** The explanation. Nothing scans behind it: a permission prompt over the pitch reads as a trap. */
  private void showIntro() {
    stopScan();
    introPanel.setVisibility(View.VISIBLE);
    // "Pair my cube" would be an odd thing to offer someone whose cube is already paired.
    btnIntroContinue.setText(SmartCubeManager.INSTANCE.isConnected()
        ? R.string.ok : R.string.smart_cube_intro_continue);
    btnIntroContinue.setVisibility(View.VISIBLE);
    pairPanel.setVisibility(View.GONE);
    pairActions.setVisibility(View.GONE);
    btnHelp.setVisibility(View.GONE); // this panel is the help
    radar.showIdle();
  }

  private void showPairing() {
    introPanel.setVisibility(View.GONE);
    btnIntroContinue.setVisibility(View.GONE);
    pairPanel.setVisibility(View.VISIBLE);
    pairActions.setVisibility(View.VISIBLE);
    btnHelp.setVisibility(View.VISIBLE);
    if (SmartCubeManager.INSTANCE.isConnected()) {
      updateUi();
    } else {
      maybeScan();
    }
  }

  private boolean showingIntro() {
    return introPanel.getVisibility() == View.VISIBLE;
  }

  private void maybeScan() {
    String[] missing = missingPermissions();
    if (missing.length > 0) {
      permissionLauncher.launch(missing);
    } else if (!SmartCubeManager.INSTANCE.isBluetoothEnabled()) {
      showBluetoothOff();
      enableBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
    } else {
      startScan();
    }
  }

  private void startScan() {
    discovered.clear();
    cubeList.removeAllViews();
    try {
      SmartCubeManager.INSTANCE.startScan(this::onDiscovered);
      scanning = true;
      showSearching();
    } catch (SecurityException e) {
      showPermissionNeeded(); // the permission was revoked from under us — not an adapter problem
    } catch (Exception e) {
      // The adapter can be switched off between the check above and the scan starting.
      showBluetoothOff();
    }
  }

  private void stopScan() {
    scanning = false;
    SmartCubeManager.INSTANCE.stopScan();
  }

  /** A cube already on the list is re-advertising: keep its bars live rather than ignoring it. */
  private void onDiscovered(DiscoveredCube found) {
    for (int i = 0; i < discovered.size(); i++) {
      if (discovered.get(i).getId().equals(found.getId())) {
        discovered.set(i, found);
        bindSignal(cubeList.getChildAt(i), found.getRssi());
        return;
      }
    }
    discovered.add(found);
    addCubeRow(found);
    showSearching();
  }

  private void addCubeRow(DiscoveredCube cube) {
    View row = getLayoutInflater().inflate(R.layout.smart_cube_list_item, cubeList, false);
    ((TextView) row.findViewById(R.id.tvCubeModel)).setText(cube.getModelName());
    ((TextView) row.findViewById(R.id.tvCubeName)).setText(cube.getName());
    bindSignal(row, cube.getRssi());
    // Read the cube off the list rather than capturing it: later advertisements replace the entry.
    row.setOnClickListener(view -> connect(discovered.get(cubeList.indexOfChild(view))));
    if (cubeList.getChildCount() > 0) {
      ((LinearLayout.LayoutParams) row.getLayoutParams()).topMargin =
          getResources().getDimensionPixelSize(R.dimen.smart_cube_row_gap);
    }
    cubeList.addView(row);
  }

  /** Four bars of signal, filled from the advertisement's dBm. Hidden when the scan reports none. */
  private void bindSignal(View row, Integer rssi) {
    if (row == null) {
      return;
    }
    View bars = row.findViewById(R.id.cubeSignal);
    if (rssi == null) {
      bars.setVisibility(View.GONE);
      return;
    }
    bars.setVisibility(View.VISIBLE);
    int lit = signalBars(rssi);
    int on = ContextCompat.getColor(requireContext(), R.color.signal_on);
    int off = ContextCompat.getColor(requireContext(), R.color.signal_off);
    int[] ids = {R.id.cubeSignal1, R.id.cubeSignal2, R.id.cubeSignal3, R.id.cubeSignal4};
    for (int i = 0; i < ids.length; i++) {
      row.findViewById(ids[i]).setBackgroundColor(i < lit ? on : off);
    }
  }

  /** Usable BLE range runs from about -40 dBm in the hand to -100 at the edge of the room. */
  private static int signalBars(int rssi) {
    if (rssi >= -60) {
      return 4;
    } else if (rssi >= -70) {
      return 3;
    } else if (rssi >= -80) {
      return 2;
    }
    return 1;
  }

  private void connect(DiscoveredCube target) {
    stopScan();
    radar.showSearching(); // still working — the halo only settles once the cube answers
    tvPill.setVisibility(View.GONE);
    tvTitle.setText(getString(R.string.smart_cube_connecting, target.getModelName()));
    tvBody.setText(target.getName());
    tvListLabel.setVisibility(View.GONE);
    cubeList.setVisibility(View.GONE);
    btnFix.setVisibility(View.GONE);

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
        showConnectFailed(e);
      }
    });
  }

  /** Re-renders whichever state the sheet is in. Connection callbacks can land at any moment. */
  private void updateUi() {
    if (!isAdded() || showingIntro()) {
      return; // a connect callback can land after the dialog is dismissed, or behind the intro
    }
    // Registering a listener fires it straight away, so this can run before a panel is even chosen:
    // it may re-render, but it must never be what starts a scan.
    boolean wasConnected = btnDisconnect.getVisibility() == View.VISIBLE;
    if (SmartCubeManager.INSTANCE.isConnected()) {
      showConnected();
    } else if (scanning) {
      showSearching();
    } else if (wasConnected) {
      maybeScan(); // the user disconnected, or the cube dropped → back to looking for one
    }
  }

  private void showConnected() {
    Integer battery = SmartCubeManager.INSTANCE.getBattery();
    DiscoveredCube device = SmartCubeManager.INSTANCE.getConnectedDevice();

    radar.showLinked(battery);
    setGlyphActive(true);
    if (battery == null) {
      setPill(getString(R.string.smart_cube_connected), R.color.pill_live_bg, R.color.pill_live_text);
    } else {
      setPill(getString(R.string.smart_cube_connected_battery, battery),
          R.color.pill_live_bg, R.color.pill_live_text);
    }
    tvTitle.setText(device != null ? device.getModelName() : getString(R.string.smart_cube_title));
    setBody(device != null && device.getMacAddress() != null
        ? getString(R.string.smart_cube_mac, device.getMacAddress()) : null);

    btnFix.setVisibility(View.GONE);
    tvListLabel.setVisibility(View.GONE);
    cubeList.setVisibility(View.GONE);
    resyncBlock.setVisibility(View.VISIBLE);
    btnDisconnect.setVisibility(View.VISIBLE);
  }

  private void showSearching() {
    radar.showSearching();
    setGlyphActive(true);
    setPill(getString(R.string.smart_cube_scanning), R.color.pill_scan_bg, R.color.lightblue);
    tvTitle.setText(R.string.smart_cube_searching_title);
    setBody(getString(R.string.smart_cube_searching_body));

    boolean empty = discovered.isEmpty();
    tvListLabel.setText(empty ? getString(R.string.smart_cube_none_yet)
        : getResources().getQuantityString(
            R.plurals.smart_cube_found_count, discovered.size(), discovered.size()));
    tvListLabel.setVisibility(View.VISIBLE);
    cubeList.setVisibility(empty ? View.GONE : View.VISIBLE);
    btnFix.setVisibility(View.GONE);
    resyncBlock.setVisibility(View.GONE);
    btnDisconnect.setVisibility(View.GONE);
  }

  private void showBluetoothOff() {
    showProblem(getString(R.string.smart_cube_bluetooth_off_title),
        getString(R.string.smart_cube_bluetooth_off_body),
        R.string.smart_cube_bluetooth_on_action,
        view -> enableBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)));
  }

  private void showPermissionNeeded() {
    showProblem(getString(R.string.smart_cube_permission_title),
        getString(R.string.smart_cube_permission_body),
        R.string.smart_cube_permission_action,
        view -> permissionLauncher.launch(missingPermissions()));
  }

  private void showConnectFailed(Exception e) {
    showProblem(getString(R.string.smart_cube_failed_title),
        getString(R.string.smart_cube_connect_failed, String.valueOf(e.getMessage())),
        R.string.smart_cube_scan_again, view -> maybeScan());
  }

  /** A stopped state, with the button that gets past it — never a dead end. */
  private void showProblem(String title, String body, int actionText, View.OnClickListener action) {
    scanning = false;
    radar.showIdle();
    setGlyphActive(false);
    tvPill.setVisibility(View.GONE); // the title already carries the state
    tvTitle.setText(title);
    setBody(body);

    btnFix.setText(actionText);
    btnFix.setOnClickListener(action);
    btnFix.setVisibility(View.VISIBLE);
    tvListLabel.setVisibility(View.GONE);
    cubeList.setVisibility(View.GONE);
    resyncBlock.setVisibility(View.GONE);
    btnDisconnect.setVisibility(View.GONE);
  }

  /** The glyph goes grey when the halo stops: nothing is happening, and it should look like it. */
  private void setGlyphActive(boolean active) {
    imgGlyph.setColorFilter(ContextCompat.getColor(requireContext(),
        active ? R.color.lightblue : R.color.gray700));
  }

  private void setPill(String text, int backgroundColor, int textColor) {
    tvPill.setText(text);
    tvPill.setTextColor(ContextCompat.getColor(requireContext(), textColor));
    ViewCompat.setBackgroundTintList(tvPill,
        ColorStateList.valueOf(ContextCompat.getColor(requireContext(), backgroundColor)));
    tvPill.setVisibility(View.VISIBLE);
  }

  private void setBody(String text) {
    tvBody.setText(text);
    tvBody.setVisibility(text == null ? View.GONE : View.VISIBLE);
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
