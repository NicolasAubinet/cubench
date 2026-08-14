package com.cube.nanotimer.smartcube.drivers;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;

/** One decoded event from the QiYi cube's notification stream. */
public sealed interface QiyiEvent
    permits QiyiEvent.HelloEvent, QiyiEvent.StateEvent, QiyiEvent.MoveEvent,
        QiyiEvent.BatteryEvent, QiyiEvent.AckRequestEvent {

  /** The cube answered the app's hello. Carries its full state. */
  final class HelloEvent implements QiyiEvent {
    private final CubeState state;

    public HelloEvent(CubeState state) {
      this.state = state;
    }

    public CubeState getState() {
      return state;
    }
  }

  /**
   * A full state snapshot with no move attached — a pulled state, or a state change the cube
   * reported without naming a face.
   */
  final class StateEvent implements QiyiEvent {
    private final CubeState state;

    public StateEvent(CubeState state) {
      this.state = state;
    }

    public CubeState getState() {
      return state;
    }
  }

  /** A single move plus the cube's own full state after it. */
  final class MoveEvent implements QiyiEvent {
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

  final class BatteryEvent implements QiyiEvent {
    private final int level;

    public BatteryEvent(int level) {
      this.level = level;
    }

    public int getLevel() {
      return level;
    }
  }

  /**
   * The cube wants this message acknowledged; {@code message} is ready to write as-is. The parser
   * stays pure by asking rather than writing, the way the Gen3/Gen4 history request does.
   */
  final class AckRequestEvent implements QiyiEvent {
    private final int[] message;

    public AckRequestEvent(int[] message) {
      this.message = message;
    }

    public int[] getMessage() {
      return message;
    }
  }
}
