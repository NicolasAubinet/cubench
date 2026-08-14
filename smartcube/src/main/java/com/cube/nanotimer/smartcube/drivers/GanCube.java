package com.cube.nanotimer.smartcube.drivers;

import android.util.Log;
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

  /**
   * Logs every gyro sample with the moment it arrived, for offline analysis by
   * {@code tools/gyroprobe.py}.
   *
   * <p>⚠️ Turn this on before tuning anything that reads the gyro. <b>No GAN has ever been
   * measured.</b> Both the pose smoothing in {@code live.html} and the still-detection the
   * straighten button waits on are shaped around a report rate and a noise floor taken from a
   * MoYu V10, and a cube that differs in either breaks both — a slower cube is read twice and
   * mistaken for perfectly still, a noisier one never reads still at all.
   *
   * <p>Off for release: it costs a string per sample, and the stream is continuous.
   */
  private static final boolean CAPTURE = false;
  private static final String CAPTURE_TAG = "GyroProbe";

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

  /** The cube said it was powering down. Cleared by the next thing it says. */
  private volatile boolean asleep;

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
    List<GanEvent> events = protocol.parse(raw, nowMs);
    if (asleep && saidMoreThanGoodbye(events)) {
      // It said it was going and then spoke anyway, so it never went. Its battery is the one thing
      // that goes stale while it dozes, and only a request gets it back.
      asleep = false;
      request(GanRequest.BATTERY);
    }
    for (GanEvent event : events) {
      if (event instanceof GanEvent.GyroEvent gyro) {
        lastOrientation = gyro.getOrientation(); // streamed fast: stored for polling, never broadcast
        history.onSample(gyro.getOrientation(), nowMs);
        capture(nowMs, gyro.getOrientation());
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
        // ⚠️ NOT a lost connection. A GAN announces this when it dozes off after sitting still, and
        // the link usually survives it: the cube goes quiet and streams again on the next turn.
        // Calling it LOST here is what left the app half dead — the manager dropped the cube, so the
        // chip lost its battery and the gyro stopped, while moves kept arriving from this object and
        // nothing ever put the connection back. The transport's own callback is what knows the link
        // is really gone; all this needs to do is notice the cube stopped talking.
        asleep = true;
      }
      // InfoEvent carries nothing consumers need yet.
    }
    if (!protocol.needsAnchor()) {
      stopAnchoring(); // a fresh state packet re-anchored us
    }
  }

  /** One gyro sample, as it arrived: {@code G <hostMs> <w> <x> <y> <z>}. */
  private static void capture(long atMs, CubeOrientation orientation) {
    if (!CAPTURE || orientation == null) {
      return;
    }
    try {
      Log.i(CAPTURE_TAG, "G " + atMs + " " + orientation.getW() + " " + orientation.getX()
          + " " + orientation.getY() + " " + orientation.getZ());
    } catch (RuntimeException e) {
      // JVM unit tests have no android.util.Log; the capture is a hardware-only diagnostic.
    }
  }

  /** A cube repeating its power-down announcement is not a cube waking up. */
  private static boolean saidMoreThanGoodbye(List<GanEvent> events) {
    for (GanEvent event : events) {
      if (!(event instanceof GanEvent.DisconnectEvent)) {
        return true;
      }
    }
    return false;
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
  public List<OrientationHistory.Sample> getOrientationsBetween(long fromMs, long toMs) {
    return history.between(fromMs, toMs);
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

  /** Every GAN generation takes the reset opcode. */
  @Override
  public boolean supportsStateReset() {
    return true;
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
