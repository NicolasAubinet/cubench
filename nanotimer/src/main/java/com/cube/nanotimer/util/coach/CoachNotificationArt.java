package com.cube.nanotimer.util.coach;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.cube.nanotimer.R;

/**
 * The pictures a new-plan notification carries, so it reads as an event rather than another daily
 * recap: a gold badge for its large icon, and a banner of the week's calendar, a cube face for its
 * days, in a burst of light. No words in either, so there is nothing to translate.
 */
final class CoachNotificationArt {

  private static final int BANNER_W = 1024;
  private static final int BANNER_H = 512;
  private static final int BADGE = 256;
  private static final int RAYS = 18;
  private static final int GOLD = 0xFFE3C33C;
  private static final int DEEP_GOLD = 0xFFB8891A;
  /** A cube face's stickers for the calendar's nine days, read row by row. */
  private static final int[] DAYS = {0xFFE3C33C, 0xFF3C74D4, 0xFFE3C33C, 0xFFD84A3A, 0xFFE3C33C,
      0xFF4BAF5A, 0xFFE3C33C, 0xFFF2F2F2, 0xFFDE8A3A};

  private CoachNotificationArt() {
  }

  /** The week's calendar in a burst of gold, for the expanded notification. */
  static Bitmap planBanner(Context context) {
    Bitmap bitmap = Bitmap.createBitmap(BANNER_W, BANNER_H, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    float cx = BANNER_W / 2f;
    float cy = BANNER_H / 2f;

    paint.setShader(new LinearGradient(0, 0, 0, BANNER_H, 0xFF2B2612, 0xFF15181B,
        Shader.TileMode.CLAMP));
    canvas.drawRect(0, 0, BANNER_W, BANNER_H, paint);

    float radius = BANNER_W * 0.6f;
    paint.setShader(new RadialGradient(cx, cy, radius, new int[] {0x88E3C33C, 0x33E3C33C,
        0x00E3C33C}, new float[] {0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
    Path rays = new Path();
    double slice = 2 * Math.PI / RAYS;
    for (int i = 0; i < RAYS; i++) {
      double from = i * slice + slice / 4;
      double to = from + slice * 0.45;
      rays.moveTo(cx, cy);
      rays.lineTo(cx + (float) Math.cos(from) * radius, cy + (float) Math.sin(from) * radius);
      rays.lineTo(cx + (float) Math.cos(to) * radius, cy + (float) Math.sin(to) * radius);
      rays.close();
    }
    canvas.drawPath(rays, paint);
    paint.setShader(new RadialGradient(cx, cy, 220, 0x66E3C33C, 0x00E3C33C,
        Shader.TileMode.CLAMP));
    canvas.drawCircle(cx, cy, 220, paint);
    paint.setShader(null);

    drawCalendar(canvas, paint, cx, cy + 6, 250);

    paint.setColor(0xFFFFF1B8);
    sparkle(canvas, paint, cx - 230, cy - 120, 22);
    sparkle(canvas, paint, cx + 250, cy - 70, 16);
    sparkle(canvas, paint, cx + 200, cy + 140, 24);
    sparkle(canvas, paint, cx - 260, cy + 110, 14);
    sparkle(canvas, paint, cx - 330, cy - 10, 10);
    sparkle(canvas, paint, cx + 340, cy + 40, 11);
    return bitmap;
  }

  /** The coach's week grid on a gold disc, for the notification's large icon. */
  static Bitmap planBadge(Context context) {
    Bitmap bitmap = Bitmap.createBitmap(BADGE, BADGE, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setShader(new LinearGradient(0, 0, BADGE, BADGE, GOLD, DEEP_GOLD,
        Shader.TileMode.CLAMP));
    canvas.drawCircle(BADGE / 2f, BADGE / 2f, BADGE / 2f, paint);
    Drawable icon = DrawableCompat.wrap(
        ContextCompat.getDrawable(context, R.drawable.ic_stat_coach).mutate());
    DrawableCompat.setTint(icon, 0xFF1B1E21);
    int inset = Math.round(BADGE * 0.24f);
    icon.setBounds(inset, inset, BADGE - inset, BADGE - inset);
    icon.draw(canvas);
    return bitmap;
  }

  /** A calendar page, {@code size} wide: a gold header with two rings over a 3x3 face of days. */
  private static void drawCalendar(Canvas canvas, Paint paint, float cx, float cy, float size) {
    float half = size / 2f;
    RectF page = new RectF(cx - half, cy - half, cx + half, cy + half);
    float corner = size * 0.1f;
    paint.setColor(0x66000000);
    canvas.drawRoundRect(new RectF(page.left + 6, page.top + 12, page.right + 6,
        page.bottom + 12), corner, corner, paint);
    paint.setColor(0xFF23272B);
    canvas.drawRoundRect(page, corner, corner, paint);

    float header = size * 0.22f;
    paint.setShader(new LinearGradient(0, page.top, 0, page.top + header, GOLD, DEEP_GOLD,
        Shader.TileMode.CLAMP));
    Path band = new Path();
    band.addRoundRect(new RectF(page.left, page.top, page.right, page.top + header),
        new float[] {corner, corner, corner, corner, 0, 0, 0, 0}, Path.Direction.CW);
    canvas.drawPath(band, paint);
    paint.setShader(null);

    float ringW = size * 0.07f;
    float ringH = size * 0.2f;
    paint.setColor(0xFFF2F2F2);
    for (float x : new float[] {cx - size * 0.22f, cx + size * 0.22f}) {
      canvas.drawRoundRect(new RectF(x - ringW / 2, page.top - ringH * 0.45f, x + ringW / 2,
          page.top + ringH * 0.55f), ringW / 2, ringW / 2, paint);
    }

    float pad = size * 0.08f;
    float gap = size * 0.045f;
    float gridTop = page.top + header + pad;
    float cellW = (size - 2 * pad - 2 * gap) / 3f;
    float cellH = (size - header - 2 * pad - 2 * gap) / 3f;
    float cellCorner = cellH * 0.18f;
    for (int i = 0; i < DAYS.length; i++) {
      float left = page.left + pad + (i % 3) * (cellW + gap);
      float top = gridTop + (i / 3) * (cellH + gap);
      paint.setColor(DAYS[i]);
      canvas.drawRoundRect(new RectF(left, top, left + cellW, top + cellH), cellCorner,
          cellCorner, paint);
    }
  }

  /** A four-pointed glint. */
  private static void sparkle(Canvas canvas, Paint paint, float x, float y, float r) {
    Path star = new Path();
    float waist = r * 0.22f;
    star.moveTo(x, y - r);
    star.quadTo(x + waist, y - waist, x + r, y);
    star.quadTo(x + waist, y + waist, x, y + r);
    star.quadTo(x - waist, y + waist, x - r, y);
    star.quadTo(x - waist, y - waist, x, y - r);
    star.close();
    canvas.drawPath(star, paint);
  }
}
