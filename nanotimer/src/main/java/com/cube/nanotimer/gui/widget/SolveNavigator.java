package com.cube.nanotimer.gui.widget;

import com.cube.nanotimer.vo.SolveTime;

/**
 * The list a solve was opened from, asked for what sits either side of it. Held by the screen that
 * owns the list rather than handed to the dialog, since a history runs to thousands of solves and
 * the dialog only ever needs the next one.
 */
public interface SolveNavigator {

  /**
   * @param direction -1 for the solve above the given one in the list, 1 for the one below
   * @return the neighbour, or null at either end of what the list has loaded
   */
  SolveTime getNeighbourSolve(SolveTime solveTime, int direction);

}
