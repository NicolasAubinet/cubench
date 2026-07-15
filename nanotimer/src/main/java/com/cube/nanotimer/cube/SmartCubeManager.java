package com.cube.nanotimer.cube;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.cube.nanotimer.smartcube.SmartCube;
import com.cube.nanotimer.smartcube.model.CubeBatteryListener;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeMoveListener;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.CubeStateListener;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;
import com.cube.nanotimer.smartcube.scanner.CubeScanner;
import com.cube.nanotimer.smartcube.scanner.CubeScannerFactory;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns the live smart-cube connection for the whole app, surviving navigation. Consumers
 * register with the manager (not the cube), so listeners persist across reconnections; the
 * manager re-wires each new {@link SmartCube} internally. Blocking BLE work runs off-thread
 * and every listener callback is marshalled to the main thread.
 */
public enum SmartCubeManager {
  INSTANCE;

  private Context context;
  private CubeScanner scanner;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final ExecutorService bleExecutor = Executors.newSingleThreadExecutor();

  private volatile SmartCube cube;
  private volatile DiscoveredCube connectedDevice;
  private volatile CubeConnection connection = CubeConnection.DISCONNECTED;
  private volatile Integer battery;
  private volatile CubeState currentState;

  private final CopyOnWriteArrayList<CubeConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
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
      bleExecutor.execute(() -> current.syncState(new CubeState(CubeState.SOLVED_FACELETS)));
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
      for (CubeConnectionListener listener : connectionListeners) {
        listener.onConnection(newConnection);
      }
    });
  }
}
