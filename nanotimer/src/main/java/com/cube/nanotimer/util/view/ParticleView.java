package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;
import com.cube.nanotimer.R;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A one-shot confetti burst drawn over the timer, fired on a personal best. It sits idle (drawing
 * nothing) until {@link #burst()}, runs itself frame by frame, then clears — so it is cheap to leave
 * mounted as a full-screen overlay. Non-clickable, so it never swallows a timer tap.
 */
public class ParticleView extends View {

  private static final int PARTICLE_COUNT = 90;
  private static final long LIFETIME_MS = 1300;
  private static final float GRAVITY = 1600f; // px per second^2

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Random random = new Random();
  private final List<Particle> particles = new ArrayList<>();
  private final int[] palette;

  private long lastFrameMs;

  public ParticleView(Context context) {
    super(context);
    palette = loadPalette(context);
    setClickable(false);
    setFocusable(false);
  }

  public ParticleView(Context context, AttributeSet attributes) {
    super(context, attributes);
    palette = loadPalette(context);
    setClickable(false);
    setFocusable(false);
  }

  private static int[] loadPalette(Context context) {
    return new int[] {
        ContextCompat.getColor(context, R.color.new_record),
        ContextCompat.getColor(context, R.color.step_cross),
        ContextCompat.getColor(context, R.color.step_f2l),
        ContextCompat.getColor(context, R.color.step_oll),
        ContextCompat.getColor(context, R.color.step_pll),
    };
  }

  /** Fires a fresh burst from the lower-centre, upward and outward like a confetti cannon. */
  public void burst() {
    int w = getWidth();
    int h = getHeight();
    if (w == 0 || h == 0) {
      return;
    }
    particles.clear();
    float originX = w / 2f;
    float originY = h * 0.72f;
    float unit = h / 800f; // scale speeds/sizes to the screen so the burst reads the same everywhere
    for (int i = 0; i < PARTICLE_COUNT; i++) {
      Particle p = new Particle();
      double angle = -Math.PI / 2 + (random.nextDouble() - 0.5) * (Math.PI * 0.9); // fan around straight up
      float speed = (700 + random.nextFloat() * 900) * unit;
      p.x = originX + (random.nextFloat() - 0.5f) * 40 * unit;
      p.y = originY;
      p.vx = (float) Math.cos(angle) * speed;
      p.vy = (float) Math.sin(angle) * speed;
      p.size = (6 + random.nextFloat() * 8) * unit;
      p.color = palette[random.nextInt(palette.length)];
      p.angle = random.nextFloat() * 360;
      p.angularVel = (random.nextFloat() - 0.5f) * 720;
      p.life = 1f;
      particles.add(p);
    }
    lastFrameMs = 0;
    postInvalidateOnAnimation();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (particles.isEmpty()) {
      return;
    }
    long now = System.currentTimeMillis();
    float dt = lastFrameMs == 0 ? 0 : Math.min(0.05f, (now - lastFrameMs) / 1000f);
    lastFrameMs = now;

    for (int i = particles.size() - 1; i >= 0; i--) {
      Particle p = particles.get(i);
      p.vy += GRAVITY * dt;
      p.x += p.vx * dt;
      p.y += p.vy * dt;
      p.angle += p.angularVel * dt;
      p.life -= dt * 1000 / LIFETIME_MS;
      if (p.life <= 0) {
        particles.remove(i);
        continue;
      }
      drawParticle(canvas, p);
    }

    if (particles.isEmpty()) {
      lastFrameMs = 0;
    } else {
      postInvalidateOnAnimation();
    }
  }

  private void drawParticle(Canvas canvas, Particle p) {
    paint.setColor(p.color);
    paint.setAlpha((int) (255 * Math.max(0f, Math.min(1f, p.life))));
    canvas.save();
    canvas.rotate(p.angle, p.x, p.y);
    float half = p.size / 2f;
    canvas.drawRect(p.x - half, p.y - half, p.x + half, p.y + half, paint);
    canvas.restore();
  }

  private static class Particle {
    float x, y, vx, vy, size, angle, angularVel, life;
    int color = Color.WHITE;
  }
}
