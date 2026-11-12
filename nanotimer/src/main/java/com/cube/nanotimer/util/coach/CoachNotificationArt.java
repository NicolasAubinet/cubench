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
 * recap: a dark tile with the coach's week grid for its large icon, and a banner of a calendar page
 * whose nine days are the right face of the app's logo cube, its top face as the header, the first
 * day lit for the week that starts. No words in either, so there is nothing to translate.
 */
final class CoachNotificationArt {

  private static final int BANNER_W = 1024;
  private static final int BANNER_H = 512;
  private static final int BADGE = 256;
  private static final int LIGHT_BLUE = 0xFF6CCCEC;
  private static final int RIM = 0xFF2F74D6;
  private static final int GLINT = 0xFFA9DDF7;
  /** The logo's right face, row by row from the near edge: deep, pale and dark stickers. */
  private static final char[] FACE = "DPKDPPDPK".toCharArray();

  private CoachNotificationArt() {
  }

  /** The logo's face as this week's calendar page, in two faint rings, for the expanded view. */
  static Bitmap planBanner(Context context) {
    Bitmap bitmap = Bitmap.createBitmap(BANNER_W, BANNER_H, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    float cx = BANNER_W / 2f;
    float cy = BANNER_H / 2f;

    paint.setShader(new LinearGradient(BANNER_W * 0.3f, 0, BANNER_W * 0.7f, BANNER_H,
        new int[] {0xFF142C45, 0xFF10161D}, new float[] {0f, 0.65f}, Shader.TileMode.CLAMP));
    canvas.drawRect(0, 0, BANNER_W, BANNER_H, paint);

    paint.setShader(new RadialGradient(cx, cy, 250, new int[] {0x002F74D6, 0x002F74D6,
        0x262F74D6}, new float[] {0f, 0.75f, 1f}, Shader.TileMode.CLAMP));
    canvas.drawCircle(cx, cy, 250, paint);
    paint.setShader(null);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(3);
    paint.setColor(0x304FA8EA);
    canvas.drawCircle(cx, cy, 250, paint);
    paint.setColor(0x184FA8EA);
    canvas.drawCircle(cx, cy, 357, paint);
    paint.setStyle(Paint.Style.FILL);

    paint.setColor(GLINT);
    sparkle(canvas, paint, BANNER_W * 0.21f + 23, BANNER_H * 0.19f + 23, 23);
    sparkle(canvas, paint, BANNER_W * 0.77f - 15, BANNER_H * 0.27f + 15, 15);
    sparkle(canvas, paint, BANNER_W * 0.81f - 18, BANNER_H * 0.81f - 18, 18);

    drawCalendar(canvas, paint, cx, cy);
    return bitmap;
  }

  /** The coach's week grid in light blue on a dark tile, for the notification's large icon. */
  static Bitmap planBadge(Context context) {
    Bitmap bitmap = Bitmap.createBitmap(BADGE, BADGE, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    RectF tile = new RectF(2, 2, BADGE - 2, BADGE - 2);
    float corner = BADGE * 0.27f;
    paint.setShader(new LinearGradient(0, 0, BADGE * 0.35f, BADGE, 0xFF23353E, 0xFF1A1D21,
        Shader.TileMode.CLAMP));
    canvas.drawRoundRect(tile, corner, corner, paint);
    paint.setShader(null);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(4);
    paint.setColor(0x666CCCEC);
    canvas.drawRoundRect(tile, corner, corner, paint);
    paint.setStyle(Paint.Style.FILL);
    Drawable icon = DrawableCompat.wrap(
        ContextCompat.getDrawable(context, R.drawable.ic_stat_coach).mutate());
    DrawableCompat.setTint(icon, LIGHT_BLUE);
    int inset = Math.round(BADGE * 0.2f);
    icon.setBounds(inset, inset, BADGE - inset, BADGE - inset);
    icon.draw(canvas);
    return bitmap;
  }

  /** A calendar page centred on (cx, cy): the logo's top face as header, its right face as days. */
  private static void drawCalendar(Canvas canvas, Paint paint, float cx, float cy) {
    float w = 344;
    float h = 356;
    RectF page = new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
    float corner = 43;

    paint.setColor(0xFF08111D);
    paint.setShadowLayer(60, 0, 0, 0x602F74D6);
    canvas.drawRoundRect(page, corner, corner, paint);
    paint.setShadowLayer(40, 0, 30, 0xA0000000);
    canvas.drawRoundRect(page, corner, corner, paint);
    paint.clearShadowLayer();

    canvas.save();
    Path clip = new Path();
    clip.addRoundRect(page, corner, corner, Path.Direction.CW);
    canvas.clipPath(clip);
    float header = 73;
    paint.setShader(new LinearGradient(0, page.top, 0, page.top + header,
        new int[] {0xFFD8F0FC, 0xFF8FCFF2, 0xFF4FB2EC}, new float[] {0f, 0.55f, 1f},
        Shader.TileMode.CLAMP));
    canvas.drawRect(page.left, page.top, page.right, page.top + header, paint);
    paint.setShader(null);
    canvas.restore();

    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(7);
    paint.setColor(RIM);
    canvas.drawRoundRect(page, corner, corner, paint);
    paint.setStyle(Paint.Style.FILL);

    float pad = 20;
    float gap = 13;
    float gridTop = page.top + header + pad;
    float cell = (w - 2 * pad - 2 * gap) / 3f;
    float cellH = (page.bottom - pad - gridTop - 2 * gap) / 3f;
    for (int i = 0; i < FACE.length; i++) {
      float left = page.left + pad + (i % 3) * (cell + gap);
      float top = gridTop + (i / 3) * (cellH + gap);
      RectF sticker = new RectF(left, top, left + cell, top + cellH);
      if (i == 0) {
        paint.setColor(0xFF8FD3F7);
        paint.setShadowLayer(44, 0, 0, 0xFF8FD3F7); // the week's first day, lit
        canvas.drawRoundRect(sticker, 10, 10, paint);
        canvas.drawRoundRect(sticker, 10, 10, paint);
        paint.clearShadowLayer();
      }
      paint.setShader(new LinearGradient(sticker.left, sticker.top, sticker.right, sticker.bottom,
          stickerColours(FACE[i]), new float[] {0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
      canvas.drawRoundRect(sticker, 10, 10, paint);
      paint.setShader(null);
      if (i == 0) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(0xFFDFF4FD);
        canvas.drawRoundRect(sticker, 10, 10, paint);
        paint.setStyle(Paint.Style.FILL);
      }
    }

    paint.setColor(0xFFEAF7FD);
    paint.setShadowLayer(6, 0, 3, 0x80000000);
    float ringW = 23;
    float ringH = 56;
    for (float x : new float[] {cx - 75, cx + 75}) {
      canvas.drawRoundRect(new RectF(x - ringW / 2, page.top - 17, x + ringW / 2,
          page.top - 17 + ringH), ringW / 2, ringW / 2, paint);
    }
    paint.clearShadowLayer();
  }

  /** A sticker's gloss, light at its top-left corner, as the logo draws them. */
  private static int[] stickerColours(char kind) {
    switch (kind) {
      case 'P':
        return new int[] {0xFFC2D8EE, 0xFF93B6DA, 0xFF7FA3CB};
      case 'K':
        return new int[] {0xFF2E63B8, 0xFF1C4790, 0xFF163A78};
      default:
        return new int[] {0xFF4A8DEB, 0xFF2A62C4, 0xFF1F4FA8};
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
