package com.cube.nanotimer.smartcube.drivers;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeState;

/** One decoded event from the MoYu V10's notification stream. */
public sealed interface MoyuEvent
    permits MoyuEvent.StateEvent, MoyuEvent.MoveEvent, MoyuEvent.BatteryEvent, MoyuEvent.InfoEvent,
        MoyuEvent.GyroEvent {

  /** The cube's initial full state (anchors move tracking). */
  final class StateEvent implements MoyuEvent {
    private final CubeState state;

    public StateEvent(CubeState state) {
      this.state = state;
    }

    public CubeState getState() {
      return state;
    }
  }

  /** A single move plus the resulting full cube state. */
  final class MoveEvent implements MoyuEvent {
    private final CubeMove move;
    private final CubeState stateAfter;

    public MoveEvent(CubeMove move, CubeState stateAfter) {
      this.move = move;
      this.stateAfter = stateAfter;
    }

    public CubeMove getMove() {
      return move;
    }

    public CubeState getStateAfter() {
      return stateAfter;
    }
  }

  /** The cube's physical orientation, streamed continuously and unrelated to moves. */
  final class GyroEvent implements MoyuEvent {
    private final CubeOrientation orientation;

    public GyroEvent(CubeOrientation orientation) {
      this.orientation = orientation;
    }

    public CubeOrientation getOrientation() {
      return orientation;
    }
  }

  final class BatteryEvent implements MoyuEvent {
    private final int level;

    public BatteryEvent(int level) {
      this.level = level;
    }

    public int getLevel() {
      return level;
    }
  }

  final class InfoEvent implements MoyuEvent {
    private final String deviceName;
    private final String hardwareVersion;
    private final String softwareVersion;

    public InfoEvent(String deviceName, String hardwareVersion, String softwareVersion) {
      this.deviceName = deviceName;
      this.hardwareVersion = hardwareVersion;
      this.softwareVersion = softwareVersion;
    }

    public String getDeviceName() {
      return deviceName;
    }

    public String getHardwareVersion() {
      return hardwareVersion;
    }

    public String getSoftwareVersion() {
      return softwareVersion;
    }
  }
}
