package com.cube.nanotimer.gui.widget;

/**
 * Implemented by an activity whose picker carries a help button. The picker itself is generic and
 * has nothing to say about what it lists, so the ? reports back with the picker's own id and the
 * activity opens whichever panel belongs to it.
 */
public interface SelectorHelpHandler {

  /** The ? on the picker with this id was pressed. */
  void onSelectorHelp(int id);
}
