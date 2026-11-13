package com.cube.nanotimer.gui.widget.preferences;

import android.content.Context;
import android.graphics.Typeface;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.DurationRuler;

import java.text.NumberFormat;

/**
 * The pause between drill cases, picked on a graduated line from off to five seconds. Kept as text
 * under its key, the way the list it replaced stored it.
 */
public class DrillPauseDialog extends DialogPreference {

  private static final int MAX_MS = 5000;
  private static final int STEP_MS = 100;

  private DurationRuler ruler;

  public DrillPauseDialog(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  /** A pause as the setting says it: "Off", or seconds to a tenth in the user's own notation. */
  public static String format(Context context, long ms) {
    if (ms <= 0) {
      return context.getString(R.string.drill_pause_off);
    }
    NumberFormat seconds = NumberFormat.getNumberInstance();
    seconds.setMaximumFractionDigits(1);
    return context.getString(R.string.drill_pause_seconds, seconds.format(ms / 1000.0));
  }

  @Override
  public CharSequence getSummary() {
    return format(getContext(), Options.INSTANCE.getDrillPauseMs());
  }

  @Override
  protected View onCreateDialogView() {
    Context context = getContext();
    float density = context.getResources().getDisplayMetrics().density;
    LinearLayout column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    int side = Math.round(20 * density);
    column.setPadding(side, Math.round(8 * density), side, Math.round(4 * density));

    final TextView readout = new TextView(context);
    readout.setGravity(Gravity.CENTER);
    readout.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
    readout.setTextColor(ContextCompat.getColor(context, R.color.white));
    Typeface bold = ResourcesCompat.getFont(context, R.font.roboto_bold);
    readout.setTypeface(bold);
    readout.setFontFeatureSettings("tnum");
    column.addView(readout);

    ruler = new DurationRuler(context);
    ruler.setRange(MAX_MS, STEP_MS, context.getString(R.string.drill_pause_off));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.topMargin = Math.round(12 * density);
    column.addView(ruler, params);

    int current = (int) Math.min(MAX_MS, Options.INSTANCE.getDrillPauseMs());
    ruler.setDuration(current);
    readout.setText(format(context, ruler.getDuration()));
    ruler.setListener(new DurationRuler.Listener() {
      @Override
      public void onDuration(int ms) {
        readout.setText(format(getContext(), ms));
      }
    });
    return column;
  }

  @Override
  protected void onDialogClosed(boolean positiveResult) {
    super.onDialogClosed(positiveResult);
    if (positiveResult && ruler != null) {
      String value = String.valueOf(ruler.getDuration());
      if (callChangeListener(value)) {
        persistString(value);
        notifyChanged();
      }
    }
    ruler = null;
  }
}
