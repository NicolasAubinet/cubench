package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.widget.TextView;
import androidx.core.content.res.ResourcesCompat;
import com.cube.nanotimer.R;

import java.util.HashMap;
import java.util.Map;

/**
 * The set of fonts the user can pick for the main timer display (see the "Timer font"
 * setting). Each value knows how to apply itself to a {@link TextView}, so the same
 * definition drives both the live timer ({@link DigitalTextView}) and the preview rows
 * in the font chooser dialog.
 *
 * <p>The {@code id} is stored in the preferences (as the {@code ListPreference} entry
 * value) and must stay stable across releases.
 */
public enum TimerFont {
  MODERN(1, R.string.timer_font_modern, R.font.roboto_bold, null, Typeface.NORMAL, "tnum", 1.00f),
  CLASSIC(2, R.string.timer_font_classic, 0, "Digital_dream_Fat_Skew_Narrow.ttf", Typeface.NORMAL, null, 0.77f),
  MONOSPACE(3, R.string.timer_font_mono, 0, "DroidSansMono.ttf", Typeface.BOLD, "tnum", 0.99f),
  LIGHT(4, R.string.timer_font_light, R.font.roboto_light, null, Typeface.NORMAL, "tnum", 1.00f),
  CONDENSED(5, R.string.timer_font_condensed, R.font.roboto_condensed_bold, null, Typeface.NORMAL, "tnum", 1.00f);

  private final int id;
  private final int nameResId;
  private final int fontResId;      // font resource, or 0 to load from assets/fonts/
  private final String assetFile;   // font file under assets/fonts/, unused when fontResId is set
  private final int style;          // Typeface.NORMAL / BOLD / ...
  private final String fontFeature; // OpenType feature settings (e.g. "tnum"), or null
  private final float sizeScale;    // multiplier applied to the base text size to even out apparent size

  // Asset typefaces are expensive to create, so cache them across the app.
  private static final Map<String, Typeface> assetCache = new HashMap<>();

  TimerFont(int id, int nameResId, int fontResId, String assetFile, int style, String fontFeature, float sizeScale) {
    this.id = id;
    this.nameResId = nameResId;
    this.fontResId = fontResId;
    this.assetFile = assetFile;
    this.style = style;
    this.fontFeature = fontFeature;
    this.sizeScale = sizeScale;
  }

  public int getId() {
    return id;
  }

  public int getNameResId() {
    return nameResId;
  }

  public float getSizeScale() {
    return sizeScale;
  }

  public static TimerFont getDefault() {
    return MODERN;
  }

  public static TimerFont fromId(int id) {
    for (TimerFont font : values()) {
      if (font.id == id) {
        return font;
      }
    }
    return getDefault();
  }

  /** Applies this font (typeface + feature settings) to the given text view. */
  public void applyTo(TextView textView) {
    textView.setTypeface(getTypeface(textView.getContext()));
    textView.setFontFeatureSettings(fontFeature); // null clears any previously set features
  }

  private Typeface getTypeface(Context context) {
    Typeface base = (fontResId != 0) ? getFontResource(context) : getAssetFont(context);
    return (style == Typeface.NORMAL) ? base : Typeface.create(base, style);
  }

  // ResourcesCompat keeps its own cache, so the faces shared with the rest of the app are only
  // read once whichever screen asks for them first.
  private Typeface getFontResource(Context context) {
    Typeface font = null;
    try {
      font = ResourcesCompat.getFont(context.getApplicationContext(), fontResId);
    } catch (RuntimeException e) {
      Log.e("NanoTimer", "Unable to load timer font: " + name(), e);
    }
    return (font != null) ? font : Typeface.defaultFromStyle(style);
  }

  private Typeface getAssetFont(Context context) {
    Typeface base = assetCache.get(assetFile);
    if (base == null) {
      try {
        base = Typeface.createFromAsset(context.getApplicationContext().getAssets(), "fonts/" + assetFile);
      } catch (RuntimeException e) {
        Log.e("NanoTimer", "Unable to load timer font: " + assetFile, e);
        base = Typeface.defaultFromStyle(style);
      }
      assetCache.put(assetFile, base);
    }
    return base;
  }

}
