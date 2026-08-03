package com.cube.nanotimer.cube;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.cube.nanotimer.smartcube.SmartCube;
import com.cube.nanotimer.smartcube.model.CubeBatteryListener;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeMoveListener;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.CubeStateListener;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;
import com.cube.nanotimer.smartcube.model.OrientationHistory;
import com.cube.nanotimer.smartcube.scanner.CubeScanner;
import com.cube.nanotimer.smartcube.scanner.CubeScannerFactory;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns the live smart-cube connection for the whole app, surviving navigation. Consumers
 * register with the manager (not the cube), so listeners persist across reconnections; the
 * manager re-wires each new {@link SmartCube} internally. Blocking BLE work runs off-thread
 * and every listener callback is marshalled to the main thread.
 *
 * <p>It also owns the {@link GyroReference}: the grip everything measuring the gyro measures from.
 * It lives here because it is worth exactly one gyro session — the cube's own fusion establishes
 * its yaw zero at power-up, so a reference outliving the connection measures against a zero that no
 * longer exists.
 */
public enum SmartCubeManager {
  INSTANCE;

  /** Two gyro periods, so each tick of {@link #anchorWhenStill} sees a genuinely new reading. */
  private static final long STILL_POLL_MS = 100;

  /** How far the cube may drift between two ticks and still count as held still. */
  private static final double STILL_DEGREES = 8.0;

  /** Long enough for a slice to finish, short enough not to leave a fresh cube unanchored. */
  private static final long STILL_TIMEOUT_MS = 2000;

  private Context context;
  private CubeScanner scanner;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final ExecutorService bleExecutor = Executors.newSingleThreadExecutor();

  private volatile SmartCube cube;
  private volatile DiscoveredCube connectedDevice;
  private volatile CubeConnection connection = CubeConnection.DISCONNECTED;
  private volatile Integer battery;
  private volatile CubeState currentState;

  // One reference for every reader of the gyro: the frames, the live mirror and the stored track.
  private final GyroReference gyroReference = new GyroReference();
  private CubeOrientation stillCandidate; // main thread only, like the two below
  private long stillSince;
  private boolean anchoring;

  private final CopyOnWriteArrayList<CubeConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<GyroReferenceListener> gyroReferenceListeners = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<CubeBatteryListener> batteryListeners = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<CubeStateListener> stateListeners = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<CubeMoveListener> moveListeners = new CopyOnWriteArrayList<>();

  public void init(Context context) {
    this.context = context.getApplicationContext();
  }

  public void startScan(SmartCubeScanListener listener) {
    ensureScanner();
    scanner.scan(found -> mainHandler.post(() -> listener.onCubeDiscovered(found)));
  }

  /** Whether the device has a Bluetooth adapter and it is currently on. */
  public boolean isBluetoothEnabled() {
    BluetoothAdapter adapter;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      BluetoothManager manager = context.getSystemService(BluetoothManager.class);
      adapter = manager != null ? manager.getAdapter() : null;
    } else {
      adapter = BluetoothAdapter.getDefaultAdapter();
    }
    return adapter != null && adapter.isEnabled();
  }

  public void stopScan() {
    if (scanner != null) {
      scanner.stopScan();
    }
  }

  public void connect(DiscoveredCube device, ConnectCallback callback) {
    connect(device, null, callback);
  }

  public void connect(DiscoveredCube device, String macAddress, ConnectCallback callback) {
    ensureScanner();
    scanner.stopScan();
    updateConnection(CubeConnection.CONNECTING);
    bleExecutor.execute(() -> {
      disconnectInternal();
      try {
        SmartCube connected = scanner.connect(device, macAddress);
        wireCube(device, connected);
        if (callback != null) {
          mainHandler.post(callback::onConnected);
        }
      } catch (Exception e) {
        cube = null;
        connectedDevice = null;
        updateConnection(CubeConnection.DISCONNECTED);
        if (callback != null) {
          mainHandler.post(() -> callback.onError(e));
        }
      }
    });
  }

  public void disconnect() {
    stopScan();
    bleExecutor.execute(this::disconnectInternal);
  }

  public void requestBattery() {
    SmartCube current = cube;
    if (current != null) {
      bleExecutor.execute(current::requestBattery);
    }
  }

  /**
   * Realign the tracked state to a solved cube. A cube whose own model has drifted keeps reporting
   * a state the cube is not in (and can even report an impossible one), which no amount of
   * reconnecting fixes: it is the cube that is wrong, and only the solver can say so.
   */
  public void syncSolved() {
    SmartCube current = cube;
    if (current != null) {
      // "My cube is solved" says how the cube is held as much as how it is turned: the grip at the
      // press is the one to square the mirror to and to measure every later frame from.
      reanchorGyro();
      bleExecutor.execute(() -> current.syncState(new CubeState(CubeState.SOLVED_FACELETS)));
    }
  }

  /** The grip every reader of the gyro measures from. Never null, though it may hold nothing yet. */
  public GyroReference getGyroReference() {
    return gyroReference;
  }

  /**
   * Take the grip again, whatever stands now: the cube is being held the way it should be measured
   * from. The old one stays up until the new one is ready, so nothing on screen goes blank while the
   * cube settles.
   */
  public void reanchorGyro() {
    mainHandler.post(() -> anchorGyro(true));
  }

  /**
   * Take one only if the session never got one — for callers noticing there is nothing to measure
   * from. Safe to call as often as it is noticed: it stands aside for a grip and for a wait already
   * under way, so it cannot keep restarting the wait it is asking for.
   */
  public void anchorGyroIfUnset() {
    mainHandler.post(() -> anchorGyro(false));
  }

  public void addGyroReferenceListener(GyroReferenceListener listener) {
    gyroReferenceListeners.add(listener);
  }

  public void removeGyroReferenceListener(GyroReferenceListener listener) {
    gyroReferenceListeners.remove(listener);
  }

  /** @param force take one even where a grip already stands; otherwise only fill an empty one */
  private void anchorGyro(boolean force) {
    if (!force && (anchoring || gyroReference.isSet())) {
      return;
    }
    // Forced, this restarts a wait already under way rather than being swallowed by it: the solver
    // pressing the button a second after connecting means the grip they are in now, not that one.
    mainHandler.removeCallbacks(anchorWhenStill);
    anchoring = true;
    stillCandidate = null;
    stillSince = SystemClock.uptimeMillis();
    mainHandler.postDelayed(anchorWhenStill, STILL_POLL_MS);
  }

  /**
   * Takes the reference off the first reading with the cube <em>at rest</em>, not off whatever it
   * reads right now.
   *
   * <p>⚠️ <b>This is the whole of the fast-slice bug.</b> A cube calls a turn done the moment it
   * registers the last quarter turn — measured on hardware, with 25° to 110° of core rotation still
   * to come. Anchoring there pins the frame to a spinning core, and since the reference stands for
   * the session, the cube stays that far out until something re-takes it.
   *
   * <p>Polled at 100 ms, which is two gyro periods, so every tick is a genuinely new sample rather
   * than the same one read twice — that would read as perfectly still and is the trap this is shaped
   * around. A hand turning the cube over moves it well under {@link #STILL_DEGREES} in that time; a
   * core mid-slice moves an order of magnitude more.
   *
   * <p>Keeps polling through readings that are not there: a cube's gyro stream can start well after
   * its connection is ready. Past the timeout it settles for whatever it can get, since a cube that
   * is simply never held still is better followed from a mid-turn frame than from none at all.
   */
  private final Runnable anchorWhenStill = new Runnable() {
    @Override
    public void run() {
      CubeOrientation reading = getOrientation();
      boolean timedOut = SystemClock.uptimeMillis() - stillSince >= STILL_TIMEOUT_MS;
      if (reading != null) {
        boolean still =
            stillCandidate != null && stillCandidate.angleToDegrees(reading) < STILL_DEGREES;
        stillCandidate = reading;
        if (still || timedOut) {
          anchoring = false;
          gyroReference.anchor(reading);
          notifyGyroReferenceChanged();
          return;
        }
      } else if (timedOut) {
        anchoring = false;
        return; // no gyro on this cube, or its stream never started: nothing to measure from
      }
      mainHandler.postDelayed(this, STILL_POLL_MS);
    }
  };

  private void forgetGyroReference() {
    mainHandler.removeCallbacks(anchorWhenStill);
    anchoring = false;
    if (gyroReference.isSet()) {
      gyroReference.restart();
      notifyGyroReferenceChanged();
    }
  }

  private void notifyGyroReferenceChanged() {
    for (GyroReferenceListener listener : gyroReferenceListeners) {
      listener.onGyroReferenceChanged();
    }
  }

  public boolean isConnected() {
    return cube != null && connection == CubeConnection.READY;
  }

  public CubeConnection getConnection() {
    return connection;
  }

  public Integer getBattery() {
    return battery;
  }

  public CubeState getCurrentState() {
    return currentState;
  }

  /** How the cube is being held, or null without a connected cube or a gyro reading yet. */
  public CubeOrientation getOrientation() {
    SmartCube connected = cube;
    return connected == null ? null : connected.getOrientation();
  }

  /** How the cube was held at a moment already past, or null if nothing was read near it. */
  public CubeOrientation getOrientationAt(long timestampMs) {
    SmartCube connected = cube;
    return connected == null ? null : connected.getOrientationAt(timestampMs);
  }

  /** Every reading taken across a window already past, oldest first; empty with no cube or no gyro. */
  public List<OrientationHistory.Sample> getOrientationsBetween(long fromMs, long toMs) {
    SmartCube connected = cube;
    return connected == null ? Collections.<OrientationHistory.Sample>emptyList()
        : connected.getOrientationsBetween(fromMs, toMs);
  }

  public DiscoveredCube getConnectedDevice() {
    return connectedDevice;
  }

  public void addConnectionListener(CubeConnectionListener listener) {
    connectionListeners.add(listener);
    listener.onConnection(connection);
  }

  public void removeConnectionListener(CubeConnectionListener listener) {
    connectionListeners.remove(listener);
  }

  public void addBatteryListener(CubeBatteryListener listener) {
    batteryListeners.add(listener);
    if (battery != null) {
      listener.onBattery(battery);
    }
  }

  public void removeBatteryListener(CubeBatteryListener listener) {
    batteryListeners.remove(listener);
  }

  public void addStateListener(CubeStateListener listener) {
    stateListeners.add(listener);
    if (currentState != null) {
      listener.onState(currentState);
    }
  }

  public void removeStateListener(CubeStateListener listener) {
    stateListeners.remove(listener);
  }

  public void addMoveListener(CubeMoveListener listener) {
    moveListeners.add(listener);
  }

  public void removeMoveListener(CubeMoveListener listener) {
    moveListeners.remove(listener);
  }

  private void ensureScanner() {
    if (scanner == null) {
      scanner = CubeScannerFactory.create(context);
    }
  }

  private void wireCube(DiscoveredCube device, SmartCube connected) {
    cube = connected;
    connectedDevice = device;
    currentState = connected.getCurrentState();
    battery = connected.getBatteryLevel();
    connected.addConnectionListener(this::onCubeConnection);
    connected.addBatteryListener(this::onCubeBattery);
    connected.addStateListener(this::onCubeState);
    connected.addMoveListener(this::onCubeMove);
    connected.requestBattery();
    updateConnection(connected.getConnection());
  }

  private void disconnectInternal() {
    SmartCube toDisconnect = cube;
    cube = null;
    connectedDevice = null;
    battery = null;
    currentState = null;
    if (toDisconnect != null) {
      try {
        toDisconnect.disconnect();
      } catch (Exception ignored) {
      }
    }
    updateConnection(CubeConnection.DISCONNECTED);
  }

  private void onCubeConnection(CubeConnection newConnection) {
    if (newConnection == CubeConnection.LOST || newConnection == CubeConnection.DISCONNECTED) {
      cube = null;
    }
    updateConnection(newConnection);
  }

  private void onCubeBattery(int level) {
    battery = level;
    mainHandler.post(() -> {
      for (CubeBatteryListener listener : batteryListeners) {
        listener.onBattery(level);
      }
    });
  }

  private void onCubeState(CubeState state) {
    currentState = state;
    mainHandler.post(() -> {
      for (CubeStateListener listener : stateListeners) {
        listener.onState(state);
      }
    });
  }

  private void onCubeMove(CubeMove move) {
    mainHandler.post(() -> {
      for (CubeMoveListener listener : moveListeners) {
        listener.onMove(move);
      }
    });
  }

  private void updateConnection(CubeConnection newConnection) {
    connection = newConnection;
    mainHandler.post(() -> {
      if (newConnection == CubeConnection.READY) {
        // A fresh gyro session, with a yaw zero of its own: take the grip it opens in. Not forced,
        // so a cube merely re-reporting ready does not re-square what the solver already squared.
        anchorGyro(false);
      } else if (newConnection != CubeConnection.CONNECTING) {
        forgetGyroReference(); // the zero it was measured against went with the connection
      }
      for (CubeConnectionListener listener : connectionListeners) {
        listener.onConnection(newConnection);
      }
    });
  }
}
