package com.cube.nanotimer.util.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/**
 * A layout whose content can be handed left and right. A horizontal drag slides everything inside it
 * with the finger; a far enough throw asks the listener to put the next one up, and the new content
 * slides in from the side the old one left towards. A drag the listener has no answer for springs
 * back, which is what says there is nothing further that way.
 *
 * <p>Only horizontal drags are taken, and only once they clearly beat the vertical one: this sits in
 * a sheet that is dragged down to close, and that gesture has to keep working.
 */
public class SwipeSwitchLayout extends LinearLayout {

  /** Asked for the content of the neighbour a throw is calling for. */
  public interface OnSwitch {
    /**
     * @param direction -1 for the neighbour to the left, 1 for the one to the right
     * @return true when the content was replaced, false to spring back
     */
    boolean onSwitch(int direction);
  }

  private static final int OUT_MS = 110;
  private static final int IN_MS = 150;
  private static final int BACK_MS = 180;
  private static final int THROW_PART = 5; // a fifth of the width commits the switch

  private OnSwitch listener;
  private int touchSlop;
  private float downX;
  private float downY;
  private boolean dragging;
  private boolean switching;

  public SwipeSwitchLayout(Context context) {
    super(context);
    init(context);
  }

  public SwipeSwitchLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  private void init(Context context) {
    touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
  }

  public void setOnSwitch(OnSwitch listener) {
    this.listener = listener;
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    if (listener == null || switching) {
      return false;
    }
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        down(event);
        break;
      case MotionEvent.ACTION_MOVE:
        return startedDragging(event); // taken off the child only once it is plainly sideways
      default:
        dragging = false;
    }
    return false;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (listener == null || switching) {
      return super.onTouchEvent(event);
    }
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        // Claimed even though nothing is done with it yet: a press no view takes is a gesture the
        // window drops, and the drag that follows would never arrive.
        down(event);
        return true;
      case MotionEvent.ACTION_MOVE:
        if (dragging || startedDragging(event)) {
          setTranslationX(event.getRawX() - downX);
        }
        return true;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (dragging) {
          dragging = false;
          release();
        }
        return true;
      default:
        break;
    }
    return super.onTouchEvent(event);
  }

  /** Screen coordinates: the local ones move with the view, and a drag measured against itself
   * only ever gets half way. */
  private void down(MotionEvent event) {
    downX = event.getRawX();
    downY = event.getRawY();
    dragging = false;
  }

  /** True once the drag is half again as far sideways as down; a sloppy vertical one is the sheet's. */
  private boolean startedDragging(MotionEvent event) {
    if (dragging) {
      return true;
    }
    float dx = Math.abs(event.getRawX() - downX);
    float dy = Math.abs(event.getRawY() - downY);
    dragging = dx > touchSlop && dx > dy * 1.5f;
    return dragging;
  }

  /** A throw past the commit distance switches; anything shorter falls back where it came from. */
  private void release() {
    float moved = getTranslationX();
    if (Math.abs(moved) < getWidth() / THROW_PART) {
      springBack();
      return;
    }
    final int direction = moved > 0 ? -1 : 1;
    final float away = moved > 0 ? getWidth() : -getWidth();
    switching = true;
    animate().translationX(away).alpha(0f).setDuration(OUT_MS).withEndAction(new Runnable() {
      @Override
      public void run() {
        if (listener.onSwitch(direction)) {
          setTranslationX(-away); // the new content comes from the side the old one went towards
        } // and without one, it comes back from the side it left, which is what says there is none
        animate().translationX(0f).alpha(1f).setDuration(IN_MS).withEndAction(done);
      }
    });
  }

  private final Runnable done = new Runnable() {
    @Override
    public void run() {
      switching = false;
    }
  };

  private void springBack() {
    animate().translationX(0f).setDuration(BACK_MS).start();
  }

}
