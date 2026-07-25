package com.cube.nanotimer.vo;

/**
 * The timer menu action that gets a spot in the action bar instead of the overflow menu.
 * Chosen per solve type; the ids are stored in the database and must stay stable.
 */
public enum TimerQuickAction {

  NONE(0),
  SCRAMBLE_VIEW(1),
  PLUS_TWO(2),
  DNF(3),
  DELETE(4),
  LAST_SOLVE(5),
  ADD_TIME(6),
  CROSS_SOLVER(7);

  private final int id;

  TimerQuickAction(int id) {
    this.id = id;
  }

  public int getId() {
    return id;
  }

  public static TimerQuickAction fromId(int id) {
    for (TimerQuickAction action : values()) {
      if (action.id == id) {
        return action;
      }
    }
    return SCRAMBLE_VIEW;
  }

  /** Blind solves default to DNF: the scramble is of no use once the solver is blindfolded. */
  public static TimerQuickAction getDefault(boolean blind) {
    return blind ? DNF : SCRAMBLE_VIEW;
  }

}
