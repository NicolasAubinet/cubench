package com.cube.nanotimer.cube;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.util.usage.UsageSharing;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;

/**
 * Connects the last cube by itself when the user has asked for that on the smart-cube sheet.
 *
 * <p>A scan is a short burst, never a standing one: when the app starts or comes back to the
 * foreground (a screen woken from sleep included), and once after a connected cube drops; the
 * smart-cube sheet's own scan connects the cube too (see {@link #wants}). It stops after {@link #SCAN_MS}, when the cube
 * is found, or when the app goes to the background, so a cube left switched off costs nothing.
 * Disconnecting by hand holds it off until a cube is connected again, or it would undo the button.
 */
public enum SmartCubeAutoConnect {
  INSTANCE;

  /** Long enough for a sleeping cube that has just been picked up to start advertising. */
  private static final long SCAN_MS = 20000;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private Context context;
  private int startedActivities;
  private boolean heldOff;
  private boolean scanning;
  private CubeConnection lastConnection = CubeConnection.DISCONNECTED;

  private final SmartCubeScanListener scanListener = this::onDiscovered;
  private final Runnable timeout = this::stop;

  /** Hooks into the app's lifecycle. Called once, when the app starts. */
  public void init(Application application) {
    context = application;
    application.registerActivityLifecycleCallbacks(new ForegroundTracker());
    SmartCubeManager.INSTANCE.addConnectionListener(this::onConnection);
  }

  /** The user disconnected on purpose: no reconnecting until they connect a cube again. */
  public void holdOff() {
    heldOff = true;
    stop();
  }

  /**
   * Whether a cube just found should be connected without a tap: it is the last one, and the user
   * wants that and has not just disconnected. The sheet asks this, since its scan replaces this one.
   */
  public boolean wants(DiscoveredCube found) {
    return !heldOff && Options.INSTANCE.isSmartCubeAutoConnect()
        && found.getId().equals(Options.INSTANCE.getSmartCubeLastId());
  }

  private void tryConnect() {
    String id = Options.INSTANCE.getSmartCubeLastId();
    // A scan already running is ours or the sheet's; once the sheet has taken the radio, ours is over.
    if (heldOff || id == null || !SmartCubeGate.ENABLED
        || SmartCubeManager.INSTANCE.isScanning()
        || !Options.INSTANCE.isSmartCubeAutoConnect()
        || SmartCubeManager.INSTANCE.isConnected()
        || SmartCubeManager.INSTANCE.getConnection() == CubeConnection.CONNECTING
        || !hasPermissions() || !SmartCubeManager.INSTANCE.isBluetoothEnabled()) {
      return;
    }
    try {
      SmartCubeManager.INSTANCE.startScan(scanListener);
    } catch (RuntimeException e) {
      return; // Bluetooth went off or the permission was revoked in between: try again next time
    }
    scanning = true;
    handler.removeCallbacks(timeout);
    handler.postDelayed(timeout, SCAN_MS);
  }

  private void stop() {
    handler.removeCallbacks(timeout);
    if (scanning) {
      scanning = false;
      SmartCubeManager.INSTANCE.stopScan(scanListener);
    }
  }

  private void onDiscovered(DiscoveredCube found) {
    if (scanning && found.getId().equals(Options.INSTANCE.getSmartCubeLastId())) {
      handler.removeCallbacks(timeout);
      scanning = false; // connect() ends the scan itself
      SmartCubeManager.INSTANCE.connect(found, null);
    }
  }

  private void onConnection(CubeConnection connection) {
    CubeConnection previous = lastConnection;
    lastConnection = connection;
    if (connection == CubeConnection.READY) {
      heldOff = false;
      stop(); // a cube connected from the sheet ends any search for the old one
      DiscoveredCube device = SmartCubeManager.INSTANCE.getConnectedDevice();
      if (device != null) {
        Options.INSTANCE.setSmartCubeLastId(device.getId());
        UsageSharing.schedule(context); // here, once the id it checks for is saved
      }
    } else if (connection == CubeConnection.LOST && previous == CubeConnection.READY
        && startedActivities > 0) {
      tryConnect(); // the cube dropped: one search, in case it comes straight back
    }
  }

  private boolean hasPermissions() {
    for (String permission : SmartCubeManager.requiredPermissions()) {
      if (ContextCompat.checkSelfPermission(context, permission)
          != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  /** The app is in the foreground while any of its activities is started. */
  private class ForegroundTracker implements Application.ActivityLifecycleCallbacks {
    private boolean recreating; // a rotation stops the screen and starts it again: not a return

    @Override
    public void onActivityStarted(Activity activity) {
      if (recreating) {
        recreating = false;
        return;
      }
      if (startedActivities++ == 0) {
        tryConnect();
      }
    }

    @Override
    public void onActivityStopped(Activity activity) {
      if (activity.isChangingConfigurations()) {
        recreating = true;
        return;
      }
      if (--startedActivities == 0) {
        stop();
      }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
  }
}
