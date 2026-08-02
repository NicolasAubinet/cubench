package com.cube.nanotimer.util;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.WindowManager;

public class ScaleUtils {

  private static final int LAYOUT_WIDTH = 480;
  private static final int LAYOUT_HEIGHT = 762;

  // Asked of the caller's own context every time. Held on to, it would outlive the activity it came
  // from and keep reporting the bounds that activity was destroyed at -- so leaving a screen in
  // landscape would scale every screen opened after it as though the phone were still sideways.
  private static WindowManager windowManager(Context c) {
    return (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
  }

  public static int getScreenHeight(Context c) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      return windowManager(c).getCurrentWindowMetrics().getBounds().height();
    } else {
      return windowManager(c).getDefaultDisplay().getHeight();
    }
  }

  public static int getScreenWidth(Context c) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      return windowManager(c).getCurrentWindowMetrics().getBounds().width();
    } else {
      return windowManager(c).getDefaultDisplay().getWidth();
    }
  }

  public static float getXScale(Context c) {
    float xScale;
    int orientation = c.getResources().getConfiguration().orientation;
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      xScale = (float) getScreenWidth(c) / LAYOUT_WIDTH;
    } else {
      xScale = (float) getScreenWidth(c) / LAYOUT_HEIGHT;
    }
    return xScale;
  }

  /** The factor the px-authored layouts are drawn at on this screen. */
  public static float getScale(Context c) {
    return Math.min(getXScale(c), getYScale(c));
  }

  public static float getYScale(Context c) {
    float yScale;
    int orientation = c.getResources().getConfiguration().orientation;
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      yScale = (float) getScreenHeight(c) / LAYOUT_HEIGHT;
    } else {
      yScale = (float) getScreenHeight(c) / LAYOUT_WIDTH;
    }
    return yScale;
  }

}
