package com.cube.nanotimer.smartcube.drivers;

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
import com.cube.nanotimer.smartcube.transport.BleCharacteristic;
import com.cube.nanotimer.smartcube.transport.BlePeripheral;
import com.cube.nanotimer.smartcube.transport.BleService;
import com.cube.nanotimer.smartcube.transport.BleUuid;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A connected QiYi cube, translating parser events into the {@link SmartCube} callbacks.
 *
 * <p>The simplest driver here, because the cube is authoritative: every state change carries the
 * full facelet state, so there is no local model to anchor, drift or re-sync. What it does need and
 * the others do not is a greeting — the cube is mute until the app writes its own MAC back to it.
 */
final class QiyiCube implements SmartCube {

  /** The cube is mute until greeted, so a lost hello write is a dead connection, not one packet. */
  private static final long HELLO_RETRY_INTERVAL_MS = 1000;

  private final DiscoveredCube device;
  private final BlePeripheral peripheral;
  private final QiyiParser parser;

  private final List<CubeMoveListener> moveListeners = new CopyOnWriteArrayList<>();
  private final List<CubeStateListener> stateListeners = new CopyOnWriteArrayList<>();
  private final List<CubeConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
  private final List<CubeBatteryListener> batteryListeners = new CopyOnWriteArrayList<>();

  private final ScheduledExecutorService helloScheduler =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "qiyi-hello");
        thread.setDaemon(true);
        return thread;
      });
  private final Object helloLock = new Object();

  private BleCharacteristic chr;
  private CubeState lastState = CubeState.SOLVED;
  private CubeConnection connection = CubeConnection.CONNECTING;
  private Integer batteryLevel;
  private volatile boolean greeted;
  private ScheduledFuture<?> helloRetry;

  QiyiCube(DiscoveredCube device, BlePeripheral peripheral, QiyiParser parser) {
    this.device = device;
    this.peripheral = peripheral;
    this.parser = parser;
  }

  void start() {
    BleService service = findService();
    chr = findCharacteristic(service);

    chr.enableNotifications();
    chr.addValueListener(this::onData);
    peripheral.addConnectionListener(up -> {
      if (!up) {
        stopGreeting();
        setConnection(CubeConnection.LOST);
      }
    });

    chr.write(parser.encodeAppHello());
    setConnection(CubeConnection.READY);
    keepSayingHello();
  }

  /** One service, one characteristic for both write and notify — unlike every other brand. */
  private BleService findService() {
    String want = BleUuid.normalize(QiyiDriver.SERVICE_UUID);
    for (BleService service : peripheral.discoverServices()) {
      if (service.getUuid().equals(want)) {
        return service;
      }
    }
    throw new IllegalStateException("QiYi service not found");
  }

  private static BleCharacteristic findCharacteristic(BleService service) {
    String want = BleUuid.normalize(QiyiDriver.CHR_UUID);
    for (BleCharacteristic candidate : service.getCharacteristics()) {
      if (candidate.getUuid().equals(want)) {
        return candidate;
      }
    }
    throw new IllegalStateException("QiYi characteristic not found");
  }

  /** Keep saying hello until the cube says hello back; until then it reports nothing at all. */
  private void keepSayingHello() {
    synchronized (helloLock) {
      if (helloRetry != null || helloScheduler.isShutdown()) {
        return;
      }
      helloRetry = helloScheduler.scheduleWithFixedDelay(() -> {
        if (greeted || connection != CubeConnection.READY) {
          stopGreeting();
          return;
        }
        try {
          chr.write(parser.encodeAppHello());
        } catch (RuntimeException e) {
          // A failed write must not break the chain, or the cube stays mute for good.
        }
      }, HELLO_RETRY_INTERVAL_MS, HELLO_RETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
  }

  private void stopGreeting() {
    synchronized (helloLock) {
      if (helloRetry != null) {
        helloRetry.cancel(false);
        helloRetry = null;
      }
    }
  }

  private void onData(int[] raw) {
    long nowMs = System.currentTimeMillis();
    for (QiyiEvent event : parser.parse(raw, nowMs)) {
      if (event instanceof QiyiEvent.AckRequestEvent ack) {
        try {
          chr.write(ack.getMessage());
        } catch (RuntimeException e) {
          // A failed ACK is not worth reacting to — the cube re-sends.
        }
      } else if (event instanceof QiyiEvent.HelloEvent hello) {
        greeted = true;
        stopGreeting();
        lastState = hello.getState();
        notifyState(lastState);
      } else if (event instanceof QiyiEvent.StateEvent state) {
        lastState = state.getState();
        notifyState(lastState);
      } else if (event instanceof QiyiEvent.MoveEvent move) {
        notifyMove(move.getMove());
        lastState = move.getStateAfter();
        notifyState(lastState);
      } else if (event instanceof QiyiEvent.BatteryEvent battery) {
        batteryLevel = battery.getLevel();
        notifyBattery(battery.getLevel());
      }
    }
  }

  private void setConnection(CubeConnection newConnection) {
    connection = newConnection;
    for (CubeConnectionListener listener : connectionListeners) {
      listener.onConnection(newConnection);
    }
  }

  private void notifyMove(CubeMove move) {
    for (CubeMoveListener listener : moveListeners) {
      listener.onMove(move);
    }
  }

  private void notifyState(CubeState state) {
    for (CubeStateListener listener : stateListeners) {
      listener.onState(state);
    }
  }

  private void notifyBattery(int level) {
    for (CubeBatteryListener listener : batteryListeners) {
      listener.onBattery(level);
    }
  }

  @Override
  public DiscoveredCube getDevice() {
    return device;
  }

  @Override
  public void addMoveListener(CubeMoveListener listener) {
    moveListeners.add(listener);
  }

  @Override
  public void addStateListener(CubeStateListener listener) {
    stateListeners.add(listener);
  }

  @Override
  public void addConnectionListener(CubeConnectionListener listener) {
    connectionListeners.add(listener);
  }

  @Override
  public void addBatteryListener(CubeBatteryListener listener) {
    batteryListeners.add(listener);
  }

  @Override
  public CubeConnection getConnection() {
    return connection;
  }

  @Override
  public CubeState getCurrentState() {
    return lastState;
  }

  @Override
  public Integer getBatteryLevel() {
    return batteryLevel;
  }

  /** Always null: the QiYi cube has no gyroscope. */
  @Override
  public CubeOrientation getOrientation() {
    return null;
  }

  @Override
  public CubeOrientation getOrientationAt(long timestampMs) {
    return null;
  }

  @Override
  public List<OrientationHistory.Sample> getOrientationsBetween(long fromMs, long toMs) {
    return Collections.emptyList();
  }

  @Override
  public void requestState() {
    chr.write(parser.encodeRequestState());
  }

  /**
   * There is no battery request: the level rides along on the cube hello and on every state change,
   * so the cached value is already as fresh as the last turn.
   */
  @Override
  public void requestBattery() {
  }

  /**
   * Nothing to realign: no local model is integrated here, and the cube's next state change
   * overwrites whatever is set. (Its own sync opcode is not in the ported spec — sync confirmation
   * is only ever seen as the reply.)
   */
  @Override
  public void syncState(CubeState state) {
    lastState = state;
  }

  @Override
  public boolean supportsStateReset() {
    return false;
  }

  @Override
  public void resetGyro() {
    // The QiYi cube has no gyro.
  }

  @Override
  public void disconnect() {
    stopGreeting();
    helloScheduler.shutdownNow();
    peripheral.disconnect();
    setConnection(CubeConnection.DISCONNECTED);
  }
}
