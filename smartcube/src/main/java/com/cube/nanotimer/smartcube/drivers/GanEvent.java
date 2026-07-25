package com.cube.nanotimer.smartcube.drivers;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeState;

/** One decoded event from a GAN cube's notification stream. */
public sealed interface GanEvent
    permits GanEvent.StateEvent, GanEvent.MoveEvent, GanEvent.GyroEvent, GanEvent.DesyncEvent,
        GanEvent.HistoryRequestEvent, GanEvent.BatteryEvent, GanEvent.InfoEvent,
        GanEvent.DisconnectEvent {

  /** The cube's own full state, which re-anchors move tracking. */
  final class StateEvent implements GanEvent {
    private final CubeState state;

    public StateEvent(CubeState state) {
      this.state = state;
    }

    public CubeState getState() {
      return state;
    }
  }

  /** A single move plus the resulting full cube state. */
  final class MoveEvent implements GanEvent {
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
  final class GyroEvent implements GanEvent {
    private final CubeOrientation orientation;

    public GyroEvent(CubeOrientation orientation) {
      this.orientation = orientation;
    }

    public CubeOrientation getOrientation() {
      return orientation;
    }
  }

  /**
   * More moves happened than could be recovered, so the tracked model no longer matches the cube.
   * Moves are ignored until fresh facelets re-anchor it.
   */
  final class DesyncEvent implements GanEvent {
    private final int lostMoves;

    public DesyncEvent(int lostMoves) {
      this.lostMoves = lostMoves;
    }

    /** Moves the cube reported that never reached the model. */
    public int getLostMoves() {
      return lostMoves;
    }
  }

  /**
   * The parser needs moves it never saw. The driver answers by writing
   * {@link GanProtocol#encodeMoveHistory}; the recovered moves arrive as a normal packet.
   * Gen3/Gen4 only — a Gen2 packet already carries its own history.
   */
  final class HistoryRequestEvent implements GanEvent {
    private final int serial;
    private final int count;

    public HistoryRequestEvent(int serial, int count) {
      this.serial = serial;
      this.count = count;
    }

    public int getSerial() {
      return serial;
    }

    public int getCount() {
      return count;
    }
  }

  final class BatteryEvent implements GanEvent {
    private final int level;

    public BatteryEvent(int level) {
      this.level = level;
    }

    public int getLevel() {
      return level;
    }
  }

  final class InfoEvent implements GanEvent {
    private final String hardwareName;
    private final String hardwareVersion;
    private final String softwareVersion;
    private final boolean gyroSupported;

    public InfoEvent(String hardwareName, String hardwareVersion, String softwareVersion,
        boolean gyroSupported) {
      this.hardwareName = hardwareName;
      this.hardwareVersion = hardwareVersion;
      this.softwareVersion = softwareVersion;
      this.gyroSupported = gyroSupported;
    }

    public String getHardwareName() {
      return hardwareName;
    }

    public String getHardwareVersion() {
      return hardwareVersion;
    }

    public String getSoftwareVersion() {
      return softwareVersion;
    }

    public boolean isGyroSupported() {
      return gyroSupported;
    }
  }

  /** The cube asked to end the session (it is powering down). */
  final class DisconnectEvent implements GanEvent {
  }
}
