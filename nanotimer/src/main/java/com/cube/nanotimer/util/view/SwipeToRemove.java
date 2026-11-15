package com.cube.nanotimer.util.view;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Swipe a row left to remove it: the row follows the finger, and let go past a third of its width
 * it slides out and {@link Listener#onRemoved} is called; short of that it springs back. A drag
 * that starts more vertical than horizontal is left to the scrolling parent.
 */
public final class SwipeToRemove implements View.OnTouchListener {

  public interface Listener {
    void onRemoved(View row);
  }

  private static final float REMOVE_SHARE = 1f / 3;
  private static final long SLIDE_MS = 180;

  private final Listener listener;
  private final int slop;
  private float downX;
  private float downY;
  private boolean dragging;

  private SwipeToRemove(View row, Listener listener) {
    this.listener = listener;
    slop = ViewConfiguration.get(row.getContext()).getScaledTouchSlop();
  }

  public static void attach(View row, Listener listener) {
    row.setOnTouchListener(new SwipeToRemove(row, listener));
  }

  @Override
  public boolean onTouch(View row, MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downX = event.getRawX();
        downY = event.getRawY();
        dragging = false;
        return true;
      case MotionEvent.ACTION_MOVE:
        float dx = event.getRawX() - downX;
        float dy = event.getRawY() - downY;
        if (!dragging && dx < -slop && Math.abs(dx) > Math.abs(dy)) {
          dragging = true;
          row.getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (dragging) {
          row.setTranslationX(Math.min(0, dx));
          row.setAlpha(1f - Math.min(1f, -dx / row.getWidth()) * 0.6f);
        }
        return true;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (dragging && -row.getTranslationX() > row.getWidth() * REMOVE_SHARE
            && event.getActionMasked() == MotionEvent.ACTION_UP) {
          row.animate().translationX(-row.getWidth()).alpha(0f).setDuration(SLIDE_MS)
              .withEndAction(() -> listener.onRemoved(row)).start();
        } else {
          row.animate().translationX(0).alpha(1f).setDuration(SLIDE_MS).start();
        }
        dragging = false;
        return true;
      default:
        return false;
    }
  }
}
