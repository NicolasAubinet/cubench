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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** A connected GAN cube, translating parser events into the {@link SmartCube} callbacks. */
final class GanCube implements SmartCube {

  /** How often to re-ask for facelets while the model is unanchored and dropping every move. */
  private static final long ANCHOR_RETRY_INTERVAL_MS = 1000;

  private final DiscoveredCube device;
  private final BlePeripheral peripheral;
  private final int[] mac;
  private final boolean moyuAi;

  private final List<CubeMoveListener> moveListeners = new CopyOnWriteArrayList<>();
  private final List<CubeStateListener> stateListeners = new CopyOnWriteArrayList<>();
  private final List<CubeConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
  private final List<CubeBatteryListener> batteryListeners = new CopyOnWriteArrayList<>();

  private final ScheduledExecutorService anchorScheduler =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "gan-anchor");
        thread.setDaemon(true);
        return thread;
      });
  private final Object anchorLock = new Object();

  private final OrientationHistory history = new OrientationHistory();

  private GanProtocol protocol;
  private BleCharacteristic commandChr;
  private CubeState lastState = CubeState.SOLVED;
  private volatile CubeOrientation lastOrientation;
  private CubeConnection connection = CubeConnection.CONNECTING;
  private ScheduledFuture<?> anchorRetry;

  GanCube(DiscoveredCube device, BlePeripheral peripheral, int[] mac, boolean moyuAi) {
    this.device = device;
    this.peripheral = peripheral;
    this.mac = mac;
    this.moyuAi = moyuAi;
  }

  void start() {
    Map<String, BleService> byUuid = new HashMap<>();
    for (BleService service : peripheral.discoverServices()) {
      byUuid.put(service.getUuid(), service);
    }
    GanDriver.Generation generation = null;
    BleService service = null;
    for (GanDriver.Generation candidate : GanDriver.GENERATIONS) {
      service = byUuid.get(BleUuid.normalize(candidate.getService()));
      if (service != null) {
        generation = candidate;
        break;
      }
    }
    if (generation == null) {
      throw new IllegalStateException("No supported GAN service found");
    }

    commandChr = findCharacteristic(service, generation.getCommandChrUuid(), "command");
    BleCharacteristic stateChr = findCharacteristic(service, generation.getStateChrUuid(), "state");
    protocol = generation.build(mac, moyuAi);

    stateChr.enableNotifications();
    stateChr.addValueListener(this::onData);
    peripheral.addConnectionListener(up -> {
      if (!up) {
        stopAnchoring();
        setConnection(CubeConnection.LOST);
      }
    });

    request(GanRequest.HARDWARE);
    request(GanRequest.BATTERY);
    request(GanRequest.FACELETS); // moves are ignored until this lands and anchors the model
    setConnection(CubeConnection.READY);
  }

  private static BleCharacteristic findCharacteristic(BleService service, String uuid, String label) {
    String want = BleUuid.normalize(uuid);
    for (BleCharacteristic chr : service.getCharacteristics()) {
      if (chr.getUuid().equals(want)) {
        return chr;
      }
    }
    throw new IllegalStateException("GAN " + label + " characteristic not found");
  }

  private void request(GanRequest request) {
    int[] msg = protocol.encodeRequest(request);
    if (msg != null) {
      commandChr.write(msg);
    }
  }

  private void onData(int[] raw) {
    long nowMs = System.currentTimeMillis();
    for (GanEvent event : protocol.parse(raw, nowMs)) {
      if (event instanceof GanEvent.GyroEvent gyro) {
        lastOrientation = gyro.getOrientation(); // streamed fast: stored for polling, never broadcast
        history.onSample(gyro.getOrientation(), nowMs);
      } else if (event instanceof GanEvent.StateEvent state) {
        lastState = state.getState();
        notifyState(lastState);
      } else if (event instanceof GanEvent.MoveEvent move) {
        notifyMove(move.getMove());
        lastState = move.getStateAfter();
        notifyState(lastState);
      } else if (event instanceof GanEvent.HistoryRequestEvent history) {
        requestMoveHistory(history);
      } else if (event instanceof GanEvent.DesyncEvent) {
        beginAnchoring();
      } else if (event instanceof GanEvent.BatteryEvent battery) {
        notifyBattery(battery.getLevel());
      } else if (event instanceof GanEvent.DisconnectEvent) {
        setConnection(CubeConnection.LOST);
      }
      // InfoEvent carries nothing consumers need yet.
    }
    if (!protocol.needsAnchor()) {
      stopAnchoring(); // a fresh state packet re-anchored us
    }
  }

  private void requestMoveHistory(GanEvent.HistoryRequestEvent history) {
    int[] msg = protocol.encodeMoveHistory(history.getSerial(), history.getCount());
    if (msg == null) {
      return;
    }
    try {
      commandChr.write(msg);
    } catch (RuntimeException e) {
      // A write that fails is not worth reacting to: the next move event re-detects the same gap
      // and asks again.
    }
  }

  /**
   * Ask the cube for its real state. Moves stay ignored until the answer lands, so keep asking until
   * it does — a request can be lost the same way a move was, and unanchored the parser drops every
   * move packet, so only a timer can drive the retry.
   */
  private void beginAnchoring() {
    synchronized (anchorLock) {
      if (anchorRetry != null || anchorScheduler.isShutdown()) {
        return; // one chain at a time: a second would double the request rate
      }
      anchorRetry = anchorScheduler.scheduleWithFixedDelay(() -> {
        if (!protocol.needsAnchor() || connection != CubeConnection.READY) {
          stopAnchoring();
          return;
        }
        try {
          request(GanRequest.FACELETS);
        } catch (RuntimeException e) {
          // A failed write must not break the chain, or the cube stays unanchored for good.
        }
      }, 0, ANCHOR_RETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
  }

  private void stopAnchoring() {
    synchronized (anchorLock) {
      if (anchorRetry != null) {
        anchorRetry.cancel(false);
        anchorRetry = null;
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
    return protocol == null ? null : protocol.getBatteryLevel();
  }

  /** Null until a reading arrives, and forever on a cube with no gyro: not every GAN has one. */
  @Override
  public CubeOrientation getOrientation() {
    return lastOrientation;
  }

  @Override
  public CubeOrientation getOrientationAt(long timestampMs) {
    return history.at(timestampMs);
  }

  @Override
  public void requestState() {
    request(GanRequest.FACELETS);
  }

  @Override
  public void requestBattery() {
    request(GanRequest.BATTERY);
  }

  @Override
  public void syncState(CubeState state) {
    // Unlike the V10, a GAN cube tracks state in its own firmware, so a local-only realign would be
    // undone by its next facelets. Solved is the one state it can be told to adopt; anything else
    // can only move the model here.
    if (state.isSolved()) {
      request(GanRequest.RESET);
    }
    protocol.setState(state);
    lastState = state;
    notifyState(state); // the screens track the cube by its state stream, so the realignment must show
  }

  @Override
  public void resetGyro() {
    // No GAN generation exposes a gyro-reset opcode. No-op.
  }

  @Override
  public void disconnect() {
    stopAnchoring();
    anchorScheduler.shutdownNow();
    peripheral.disconnect();
    setConnection(CubeConnection.DISCONNECTED);
  }
}
