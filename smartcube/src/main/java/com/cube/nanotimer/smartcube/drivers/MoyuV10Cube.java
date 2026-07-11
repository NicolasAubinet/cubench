package com.cube.nanotimer.smartcube.drivers;

import com.cube.nanotimer.smartcube.SmartCube;
import com.cube.nanotimer.smartcube.model.CubeBatteryListener;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeMoveListener;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.CubeStateListener;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;
import com.cube.nanotimer.smartcube.transport.BleCharacteristic;
import com.cube.nanotimer.smartcube.transport.BlePeripheral;
import com.cube.nanotimer.smartcube.transport.BleService;
import com.cube.nanotimer.smartcube.transport.BleUuid;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A connected MoYu V10, translating parser events into the {@link SmartCube} callbacks. */
final class MoyuV10Cube implements SmartCube {

  private final DiscoveredCube device;
  private final BlePeripheral peripheral;
  private final MoyuV10Parser parser;

  private final List<CubeMoveListener> moveListeners = new CopyOnWriteArrayList<>();
  private final List<CubeStateListener> stateListeners = new CopyOnWriteArrayList<>();
  private final List<CubeConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
  private final List<CubeBatteryListener> batteryListeners = new CopyOnWriteArrayList<>();

  private BleCharacteristic writeChr;
  private CubeState lastState = CubeState.SOLVED;
  private CubeConnection connection = CubeConnection.CONNECTING;
  private Integer batteryLevel;

  MoyuV10Cube(DiscoveredCube device, BlePeripheral peripheral, MoyuV10Parser parser) {
    this.device = device;
    this.peripheral = peripheral;
    this.parser = parser;
  }

  void start() {
    BleService service = findService();
    BleCharacteristic readChr = findCharacteristic(service, MoyuV10Driver.READ_CHR_UUID, "notify");
    writeChr = findCharacteristic(service, MoyuV10Driver.WRITE_CHR_UUID, "write");

    readChr.enableNotifications();
    readChr.addValueListener(this::onData);
    peripheral.addConnectionListener(up -> {
      if (!up) {
        setConnection(CubeConnection.LOST);
      }
    });

    writeChr.write(parser.encodeRequest(MoyuV10Parser.OP_INFO));
    writeChr.write(parser.encodeRequest(MoyuV10Parser.OP_STATUS));
    writeChr.write(parser.encodeRequest(MoyuV10Parser.OP_POWER));
    setConnection(CubeConnection.READY);
  }

  private BleService findService() {
    String want = BleUuid.normalize(MoyuV10Driver.SERVICE_UUID);
    for (BleService service : peripheral.discoverServices()) {
      if (service.getUuid().equals(want)) {
        return service;
      }
    }
    throw new IllegalStateException("MoYu V10 service not found");
  }

  private static BleCharacteristic findCharacteristic(BleService service, String uuid, String label) {
    String want = BleUuid.normalize(uuid);
    for (BleCharacteristic chr : service.getCharacteristics()) {
      if (chr.getUuid().equals(want)) {
        return chr;
      }
    }
    throw new IllegalStateException("MoYu V10 " + label + " characteristic not found");
  }

  private void onData(int[] raw) {
    for (MoyuEvent event : parser.parse(raw, System.currentTimeMillis())) {
      if (event instanceof MoyuEvent.StateEvent state) {
        lastState = state.getState();
        notifyState(lastState);
      } else if (event instanceof MoyuEvent.MoveEvent move) {
        notifyMove(move.getMove());
        lastState = move.getStateAfter();
        notifyState(lastState);
      } else if (event instanceof MoyuEvent.BatteryEvent battery) {
        batteryLevel = battery.getLevel();
        notifyBattery(battery.getLevel());
      }
      // InfoEvent carries nothing consumers need yet.
    }
    if (parser.pollNeedsResync()) {
      requestState(); // re-anchor from the cube after an unrecoverable move gap
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

  @Override
  public void requestState() {
    parser.resetAnchor();
    writeChr.write(parser.encodeRequest(MoyuV10Parser.OP_STATUS));
  }

  @Override
  public void requestBattery() {
    writeChr.write(parser.encodeRequest(MoyuV10Parser.OP_POWER));
  }

  @Override
  public void syncState(CubeState state) {
    parser.setState(state);
    lastState = state;
  }

  @Override
  public void resetGyro() {
    // The V10 gyro/orientation-reset opcode is not yet reverse-engineered. No-op for now.
  }

  @Override
  public void disconnect() {
    peripheral.disconnect();
    setConnection(CubeConnection.DISCONNECTED);
  }
}
