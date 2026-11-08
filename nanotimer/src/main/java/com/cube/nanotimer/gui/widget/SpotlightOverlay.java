package com.cube.nanotimer.gui.widget;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

/**
 * Dims the whole window except a circle around one view, with a caption under it, to show the user
 * where something lives. It is modal: a tap on the circle runs the target's action and closes it;
 * anything else is ignored until the owner dismisses it (back, or a solve starting).
 *
 * <p>The target is looked up again before every frame, so a menu rebuilt or a rotation moves the
 * circle with it; while the target is not laid out yet, only the dim shows.
 */
public class SpotlightOverlay extends FrameLayout {

  public interface TargetFinder {
    View find();
  }

  private static final int SCRIM = 0xB8000000;
  private static final int FADE_MS = 200;
  private static final int PULSE_MS = 1400;

  private final TargetFinder finder;
  private final Runnable onTargetTap;
  private final TextView caption;
  private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path scrimPath = new Path();
  private final RectF hole = new RectF();
  private final int[] location = new int[2];
  private final int[] ownLocation = new int[2];
  private final float holePadding;
  private final float captionGap;
  private boolean hasHole;
  private float pulse;
  private ValueAnimator pulseAnimator;

  private final ViewTreeObserver.OnPreDrawListener tracker = new ViewTreeObserver.OnPreDrawListener() {
    @Override
    public boolean onPreDraw() {
      if (updateHole()) {
        invalidate();
      }
      return true;
    }
  };

  private SpotlightOverlay(Context context, TargetFinder finder, CharSequence text, Runnable onTargetTap) {
    super(context);
    this.finder = finder;
    this.onTargetTap = onTargetTap;
    setWillNotDraw(false);
    setClickable(true);
    holePadding = dp(6);
    captionGap = dp(14);

    scrimPaint.setColor(SCRIM);
    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setStrokeWidth(dp(2));
    ringPaint.setColor(ContextCompat.getColor(context, R.color.lightblue));

    caption = new TextView(context);
    caption.setText(text);
    caption.setTextColor(ContextCompat.getColor(context, R.color.white));
    caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    caption.setLineSpacing(0, 1.15f);
    int padH = (int) dp(16);
    int padV = (int) dp(12);
    caption.setPadding(padH, padV, padH, padV);
    GradientDrawable background = new GradientDrawable();
    background.setColor(ContextCompat.getColor(context, R.color.dialog_surface));
    background.setCornerRadius(dp(12));
    background.setStroke((int) dp(1), ContextCompat.getColor(context, R.color.accent_wash_30));
    caption.setBackground(background);
    caption.setMaxWidth((int) dp(300));
    LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
        Gravity.TOP | Gravity.END);
    int margin = (int) dp(16);
    params.setMargins(margin, 0, margin, 0);
    caption.setVisibility(INVISIBLE); // until there is a hole to hang it under
    addView(caption, params);
  }

  /** Adds the overlay over the whole window of {@code activity}. */
  public static SpotlightOverlay show(Activity activity, TargetFinder finder, CharSequence text,
      Runnable onTargetTap) {
    SpotlightOverlay overlay = new SpotlightOverlay(activity, finder, text, onTargetTap);
    ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
    decor.addView(overlay, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    overlay.setAlpha(0f);
    overlay.animate().alpha(1f).setDuration(FADE_MS);
    return overlay;
  }

  public void dismiss() {
    ViewGroup parent = (ViewGroup) getParent();
    if (parent != null) {
      parent.removeView(this);
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    getViewTreeObserver().addOnPreDrawListener(tracker);
    pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
    pulseAnimator.setDuration(PULSE_MS);
    pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
    pulseAnimator.addUpdateListener(a -> {
      pulse = (float) a.getAnimatedValue();
      invalidate();
    });
    pulseAnimator.start();
  }

  @Override
  protected void onDetachedFromWindow() {
    getViewTreeObserver().removeOnPreDrawListener(tracker);
    if (pulseAnimator != null) {
      pulseAnimator.cancel();
    }
    super.onDetachedFromWindow();
  }

  /** @return whether the hole moved, appeared or went */
  private boolean updateHole() {
    View target = finder.find();
    boolean found = target != null && target.isShown() && target.getWidth() > 0;
    if (!found) {
      boolean changed = hasHole;
      hasHole = false;
      caption.setVisibility(INVISIBLE);
      return changed;
    }
    target.getLocationInWindow(location);
    getLocationInWindow(ownLocation);
    float cx = location[0] - ownLocation[0] + target.getWidth() / 2f;
    float cy = location[1] - ownLocation[1] + target.getHeight() / 2f;
    float radius = Math.max(target.getWidth(), target.getHeight()) / 2f + holePadding;
    if (hasHole && hole.centerX() == cx && hole.centerY() == cy && hole.width() == radius * 2) {
      return false;
    }
    hole.set(cx - radius, cy - radius, cx + radius, cy + radius);
    hasHole = true;
    LayoutParams params = (LayoutParams) caption.getLayoutParams();
    int top = (int) (hole.bottom + captionGap);
    if (params.topMargin != top) {
      params.topMargin = top;
      caption.setLayoutParams(params);
    }
    caption.setVisibility(VISIBLE);
    return true;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    scrimPath.reset();
    scrimPath.setFillType(Path.FillType.EVEN_ODD);
    scrimPath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
    if (hasHole) {
      scrimPath.addCircle(hole.centerX(), hole.centerY(), hole.width() / 2f, Path.Direction.CW);
    }
    canvas.drawPath(scrimPath, scrimPaint);
    if (hasHole) {
      float radius = hole.width() / 2f;
      canvas.drawCircle(hole.centerX(), hole.centerY(), radius, ringPaint);
      // A second ring that swells out and fades, so the eye finds the circle.
      ringPaint.setAlpha((int) (255 * (1f - pulse)));
      canvas.drawCircle(hole.centerX(), hole.centerY(), radius + dp(14) * pulse, ringPaint);
      ringPaint.setAlpha(255);
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (event.getActionMasked() == MotionEvent.ACTION_UP && hasHole
        && hole.contains(event.getX(), event.getY())) {
      dismiss();
      onTargetTap.run();
    }
    return true;
  }

  private float dp(float value) {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
  }
}
