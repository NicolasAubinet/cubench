package com.cube.nanotimer.util.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

/**
 * The pause between two drill cases: the solved cube softens and steps back, and the rep just
 * finished rises from its slot under the cube into a dial in the middle of the cube's room, its name
 * and time larger. The dial's inner ring sweeps round as the rep's split, its outer ring runs out
 * with the pause, and the line's bar and words give way to the parts' figures under the time. When
 * the pause ends the cube comes back into focus first and the next case is dealt onto it, while the
 * line settles back into its slot.
 *
 * <p>From Android 12 the cube itself is blurred and dimmed, which leaves the beat's glow behind it
 * showing round the result; before that a dark cover over the cube does the dimming alone.
 *
 * <p>Kept out of the drill screen, as {@link DrillRepFlourish} is: it only moves views. The screen
 * decides when a pause starts and ends, and the next case is still timed from when it is dealt.
 */
public final class DrillRepPause {

  private static final float SCALE = 1.25f;
  private static final float SCRIM_ALPHA = 0.88f;
  private static final float CUBE_SCALE = 0.92f;
  private static final float CUBE_BLUR_DP = 14;
  /** How much of the cube's brightness is left at the deepest of the pause. */
  private static final float CUBE_DIM = 0.4f;
  private static final long IN_MS = 420;
  private static final long TITLE_DELAY_MS = 140;
  private static final long FIGURES_DELAY_MS = 180;
  private static final long EXTRAS_IN_MS = 260;
  private static final long OUT_MS = 320;
  private static final long EXTRAS_OUT_MS = 120;
  /** How long the cube takes to come back into focus before the next case is dealt onto it. */
  private static final long CLEAR_MS = 180;
  private static final long SWEEP_DELAY_MS = 60;
  private static final long SWEEP_MS = 620;
  private static final float GAP_DP = 18;
  private static final float TITLE_RISE_DP = 10;
  private static final float DIAL_DP = 112;
  private static final float DIAL_MIN_DP = 88;
  private static final float DIAL_MARGIN_DP = 16;
  /** Between the time and the figures under it. */
  private static final float FIGURES_GAP_DP = 10;
  /** Kept clear inside the ring round the name, time and figures. */
  private static final float INNER_PAD_DP = 18;

  /** Material's emphasized curves: a quick start that settles slowly, and its mirror. */
  private static final Interpolator SETTLE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
  private static final Interpolator LEAVE = new PathInterpolator(0.3f, 0f, 0.1f, 1f);

  private final View stage;
  private final View scrim;
  private final View cube;
  private final View line;
  private final View name;
  private final View time;
  private final View[] fades;
  private final TextView title;
  private final DrillPauseDialView dial;
  private final float density;
  private final boolean blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
  private float softness;
  private AnimatorSet running;
  private ViewTreeObserver.OnPreDrawListener placing;
  private boolean up;
  private Runnable pending;
  private final Runnable runPending = new Runnable() {
    @Override
    public void run() {
      Runnable then = pending;
      pending = null;
      if (then != null) {
        then.run();
      }
    }
  };

  /**
   * @param scrim covers the cube, inside the cube's own frame, which is the room the rep rises into
   * @param cube the drawn cube, blurred where the system can
   * @param line the rep's line, a sibling of the cube's frame, moved and scaled by the pause
   * @param name the rep's name, inside the line: the top of what stays in the dial
   * @param time the rep's time, inside the line: the bottom of what stays in the dial
   * @param fades the rest of the line, which gives way to the dial's own figures
   * @param title over the cube's frame, above the dial: shown for a titled pause only
   * @param dial over the cube's frame
   */
  public DrillRepPause(View scrim, View cube, View line, View name, View time, View[] fades,
      TextView title, DrillPauseDialView dial) {
    this.stage = (View) scrim.getParent();
    this.scrim = scrim;
    this.cube = cube;
    this.line = line;
    this.name = name;
    this.time = time;
    this.fades = fades.clone();
    this.title = title;
    this.dial = dial;
    density = scrim.getResources().getDisplayMetrics().density;
  }

  /** Whether a pause is up, or ending with its next case still to deal. */
  public boolean isActive() {
    return up || pending != null;
  }

  /**
   * Brings the finished rep forward for the pause.
   *
   * @param heading said above the rep, or null for a plain pause
   * @param durationMs how long the outer ring takes to run out
   * @param parts the rep's split, drawn round the dial; empty for a rep with none
   */
  public void enter(CharSequence heading, final long durationMs, List<DrillRepParts.Part> parts) {
    cancel();
    up = true;
    title.setText(heading);
    title.setVisibility(heading == null ? View.GONE : View.VISIBLE);
    title.setAlpha(0f);
    dial.setParts(parts);
    dial.setSweep(0f);
    dial.setRemaining(1f);
    dial.setFiguresAlpha(0f);
    dial.setAlpha(1f);
    dial.setVisibility(View.VISIBLE);
    if (!blurs) {
      scrim.setAlpha(0f);
      scrim.setVisibility(View.VISIBLE);
    }
    // Placed once laid out: the title has no height before that
    placing = new ViewTreeObserver.OnPreDrawListener() {
      @Override
      public boolean onPreDraw() {
        stopPlacing();
        if (up) {
          animateIn(durationMs);
        }
        return true;
      }
    };
    line.getViewTreeObserver().addOnPreDrawListener(placing);
    line.invalidate();
  }

  /**
   * Ends the pause: the cube comes back into focus, then {@code dealt} runs, unless {@code now}
   * runs it at once (a turn already made for the next case). The line goes home meanwhile.
   */
  public void leave(Runnable dealt, boolean now) {
    stopPlacing();
    stage.removeCallbacks(runPending);
    pending = null;
    if (up) {
      up = false;
      animateOut();
    }
    if (now) {
      dealt.run();
    } else {
      pending = dealt;
      stage.postDelayed(runPending, CLEAR_MS);
    }
  }

  /** Drops the pause at once, nothing dealt: everything back at rest. */
  public void cancel() {
    stopPlacing();
    stage.removeCallbacks(runPending);
    pending = null;
    up = false;
    if (running != null) {
      running.cancel();
      running = null;
    }
    rest();
  }

  private void animateIn(long durationMs) {
    float gap = GAP_DP * density;
    float titleRoom = title.getVisibility() == View.VISIBLE ? title.getHeight() + gap : 0;
    float reach = dial.outerReach();
    float radius = Math.min(DIAL_DP * density, Math.min(stage.getWidth() / 2f,
        (stage.getHeight() - titleRoom) / 2f) - reach - DIAL_MARGIN_DP * density);
    radius = Math.max(radius, DIAL_MIN_DP * density);
    float centre = Math.max(titleRoom + radius + reach, (stage.getHeight() + titleRoom) / 2f);
    title.setTranslationY(centre - radius - reach - gap - title.getHeight()
        - TITLE_RISE_DP * density);

    // The name and time, scaled to fit inside the ring, centred with the figures under them
    float nameTop = offsetInLine(name);
    float head = offsetInLine(time) + time.getHeight() - nameTop;
    float figuresGap = FIGURES_GAP_DP * density;
    float figures = dial.figuresHeight();
    float inner = 2 * (radius - INNER_PAD_DP * density);
    float scale = Math.min(SCALE, (inner - figuresGap - figures) / head);
    float headTop = centre - (head * scale + figuresGap + figures) / 2;
    float chord = 2 * (float) Math.sqrt(Math.max(0, square(radius) - square(centre - headTop)))
        - 2 * INNER_PAD_DP * density;
    float headWidth = Math.max(widthOf(name), widthOf(time));
    if (chord > 0 && headWidth * scale > chord) {
      scale = chord / headWidth;
      headTop = centre - (head * scale + figuresGap + figures) / 2;
    }
    float pivot = line.getHeight() / 2f;
    float lift = stage.getTop() + headTop - (line.getTop() + pivot + (nameTop - pivot) * scale);
    dial.place(centre, radius, headTop + head * scale + figuresGap);

    List<Animator> parts = new ArrayList<Animator>();
    if (blurs) {
      parts.add(timed(soften(1f), IN_MS, 0, SETTLE));
    } else {
      parts.add(timed(ObjectAnimator.ofFloat(scrim, View.ALPHA, SCRIM_ALPHA), IN_MS, 0, SETTLE));
    }
    parts.add(timed(ObjectAnimator.ofFloat(cube, View.SCALE_X, CUBE_SCALE), IN_MS, 0, SETTLE));
    parts.add(timed(ObjectAnimator.ofFloat(cube, View.SCALE_Y, CUBE_SCALE), IN_MS, 0, SETTLE));
    parts.add(timed(ObjectAnimator.ofFloat(line, View.TRANSLATION_Y, lift), IN_MS, 0, SETTLE));
    Interpolator swell = new OvershootInterpolator(1.4f);
    parts.add(timed(ObjectAnimator.ofFloat(line, View.SCALE_X, scale), IN_MS, 0, swell));
    parts.add(timed(ObjectAnimator.ofFloat(line, View.SCALE_Y, scale), IN_MS, 0, swell));
    for (View fade : fades) {
      parts.add(timed(ObjectAnimator.ofFloat(fade, View.ALPHA, 0f), EXTRAS_OUT_MS, 0, SETTLE));
    }
    parts.add(timed(ObjectAnimator.ofFloat(title, View.ALPHA, 1f), EXTRAS_IN_MS, TITLE_DELAY_MS,
        SETTLE));
    parts.add(timed(ObjectAnimator.ofFloat(title, View.TRANSLATION_Y,
        title.getTranslationY() + TITLE_RISE_DP * density), EXTRAS_IN_MS, TITLE_DELAY_MS, SETTLE));
    parts.add(timed(dialValue(0f, 1f, Dial.SWEEP), SWEEP_MS, SWEEP_DELAY_MS, SETTLE));
    parts.add(timed(dialValue(0f, 1f, Dial.FIGURES), EXTRAS_IN_MS, FIGURES_DELAY_MS, SETTLE));
    parts.add(timed(dialValue(1f, 0f, Dial.REMAINING), durationMs, 0, new LinearInterpolator()));
    play(parts);
  }

  private void animateOut() {
    List<Animator> parts = new ArrayList<Animator>();
    Interpolator clear = new DecelerateInterpolator();
    if (blurs) {
      parts.add(timed(soften(0f), CLEAR_MS, 0, clear));
    } else {
      parts.add(timed(ObjectAnimator.ofFloat(scrim, View.ALPHA, 0f), CLEAR_MS, 0, clear));
    }
    parts.add(timed(ObjectAnimator.ofFloat(cube, View.SCALE_X, 1f), CLEAR_MS, 0, clear));
    parts.add(timed(ObjectAnimator.ofFloat(cube, View.SCALE_Y, 1f), CLEAR_MS, 0, clear));
    parts.add(timed(ObjectAnimator.ofFloat(title, View.ALPHA, 0f), EXTRAS_OUT_MS, 0, clear));
    parts.add(timed(ObjectAnimator.ofFloat(dial, View.ALPHA, 0f), EXTRAS_OUT_MS, 0, clear));
    for (View fade : fades) {
      parts.add(timed(ObjectAnimator.ofFloat(fade, View.ALPHA, 1f), OUT_MS, EXTRAS_OUT_MS, clear));
    }
    parts.add(timed(ObjectAnimator.ofFloat(line, View.TRANSLATION_Y, 0f), OUT_MS, 0, LEAVE));
    parts.add(timed(ObjectAnimator.ofFloat(line, View.SCALE_X, 1f), OUT_MS, 0, LEAVE));
    parts.add(timed(ObjectAnimator.ofFloat(line, View.SCALE_Y, 1f), OUT_MS, 0, LEAVE));
    AnimatorSet set = play(parts);
    set.addListener(new AnimatorListenerAdapter() {
      private boolean cancelled;

      @Override
      public void onAnimationCancel(Animator animation) {
        cancelled = true;
      }

      @Override
      public void onAnimationEnd(Animator animation) {
        if (!cancelled && !up) {
          hideExtras();
        }
      }
    });
  }

  private enum Dial { SWEEP, FIGURES, REMAINING }

  private ValueAnimator dialValue(float from, float to, final Dial which) {
    ValueAnimator animator = ValueAnimator.ofFloat(from, to);
    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        float value = (Float) animation.getAnimatedValue();
        if (which == Dial.SWEEP) {
          dial.setSweep(value);
        } else if (which == Dial.FIGURES) {
          dial.setFiguresAlpha(value);
        } else {
          dial.setRemaining(value);
        }
      }
    });
    return animator;
  }

  /** Where a view inside the line stands, from the line's top, before any scaling. */
  private float offsetInLine(View view) {
    float y = 0;
    for (View v = view; v != line && v != null; v = (View) v.getParent()) {
      y += v.getTop();
    }
    return y;
  }

  /** The width a view's content takes: a text view's text, else the view. */
  private static float widthOf(View view) {
    if (view instanceof TextView) {
      TextView text = (TextView) view;
      return text.getPaint().measureText(text.getText().toString())
          + text.getTotalPaddingLeft() + text.getTotalPaddingRight();
    }
    return view.getWidth();
  }

  private static float square(float value) {
    return value * value;
  }

  /** The cube's blur and dimming, from where it stands to {@code to}: 0 sharp, 1 deepest. */
  private ValueAnimator soften(float to) {
    ValueAnimator animator = ValueAnimator.ofFloat(softness, to);
    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        setSoftness((Float) animation.getAnimatedValue());
      }
    });
    return animator;
  }

  private void setSoftness(float value) {
    softness = value;
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      return;
    }
    soften(cube, value, CUBE_BLUR_DP * density * value);
  }

  @RequiresApi(Build.VERSION_CODES.S)
  private static void soften(View view, float value, float radius) {
    if (value <= 0f) {
      view.setRenderEffect(null);
      return;
    }
    float light = 1f - (1f - CUBE_DIM) * value;
    ColorMatrix dim = new ColorMatrix();
    dim.setScale(light, light, light, 1f);
    view.setRenderEffect(RenderEffect.createChainEffect(
        RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(dim)),
        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)));
  }

  private AnimatorSet play(List<Animator> parts) {
    if (running != null) {
      running.cancel();
    }
    AnimatorSet set = new AnimatorSet();
    set.playTogether(parts);
    running = set;
    set.start();
    return set;
  }

  private static Animator timed(Animator animator, long durationMs, long delayMs,
      Interpolator interpolator) {
    animator.setDuration(durationMs);
    animator.setStartDelay(delayMs);
    animator.setInterpolator(interpolator);
    return animator;
  }

  private void stopPlacing() {
    if (placing != null) {
      line.getViewTreeObserver().removeOnPreDrawListener(placing);
      placing = null;
    }
  }

  private void rest() {
    line.setTranslationY(0f);
    line.setScaleX(1f);
    line.setScaleY(1f);
    cube.setScaleX(1f);
    cube.setScaleY(1f);
    for (View fade : fades) {
      fade.setAlpha(1f);
    }
    setSoftness(0f);
    hideExtras();
  }

  private void hideExtras() {
    scrim.setVisibility(View.GONE);
    title.setVisibility(View.GONE);
    dial.setVisibility(View.GONE);
  }
}
