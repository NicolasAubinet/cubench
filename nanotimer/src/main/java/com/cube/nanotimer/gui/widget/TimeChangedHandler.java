package com.cube.nanotimer.gui.widget;

import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;

import java.io.Serializable;

public interface TimeChangedHandler extends Serializable {

  void onTimeChanged(SolveTime solveTime);
  void onTimeDeleted(SolveTime solveTime);

  /**
   * A solve type's blind method was changed from a solve of it. Each solve carries its own copy of
   * its type, so every other copy the host holds with that id has to be told.
   */
  void onBlindMethodChanged(SolveType solveType);

}
