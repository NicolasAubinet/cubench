package com.cube.nanotimer.gui.widget.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.cube.nanotimer.R;

/**
 * A number preference: a stepper around an editable value, optionally preceded by a row of preset
 * chips and followed by the unit the number is counted in.
 * <p>
 * Also serves the inspection time, which used to have a scrolling wheel of its own. Screens outside
 * the settings open the same panel through {@link #pick}, so no other number control is needed.
 */
public class NumberEntryDialog extends DialogPreference {

  /** Takes the value the picker was closed on. */
  public interface Listener {
    void onNumberPicked(int value);
  }

  private Panel panel;

  private int min = 0;
  private int max = 99999;
  private int defaultValue = 0;
  private int step = 1;
  private int[] presets;
  private CharSequence unit;

  public NumberEntryDialog(Context context, AttributeSet attrs) {
    super(context, attrs);
    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NumberLimit);
    if (a.hasValue(R.styleable.NumberLimit_min)) {
      min = a.getInt(R.styleable.NumberLimit_min, min);
    }
    if (a.hasValue(R.styleable.NumberLimit_max)) {
      max = a.getInt(R.styleable.NumberLimit_max, max);
    }
    if (a.hasValue(R.styleable.NumberLimit_defaultVal)) {
      defaultValue = a.getInt(R.styleable.NumberLimit_defaultVal, defaultValue);
    }
    if (a.hasValue(R.styleable.NumberLimit_step)) {
      step = a.getInt(R.styleable.NumberLimit_step, step);
    }
    if (a.hasValue(R.styleable.NumberLimit_presets)) {
      presets = context.getResources().getIntArray(
         a.getResourceId(R.styleable.NumberLimit_presets, 0));
    }
    if (a.hasValue(R.styleable.NumberLimit_unit)) {
      unit = a.getText(R.styleable.NumberLimit_unit);
    }
    a.recycle();
  }

  @Override
  protected View onCreateDialogView() {
    SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getContext());
    panel = new Panel(getContext(), min, max, step, presets, unit, p.getInt(getKey(), defaultValue));
    return panel.view;
  }

  @Override
  protected void onDialogClosed(boolean positiveResult) {
    super.onDialogClosed(positiveResult);

    if (positiveResult) {
      int value = panel.getValue();
      if (callChangeListener(value)) {
        persistInt(value);
      }
    }
  }

  /** Opens the same panel outside the settings screen; {@code listener} gets the value on OK. */
  public static void pick(Context context, int titleRes, int min, int max, int step,
     int presetsArrayRes, int unitRes, int current, final Listener listener) {
    final Panel panel = new Panel(context, min, max, step,
       context.getResources().getIntArray(presetsArrayRes), context.getString(unitRes), current);
    new AlertDialog.Builder(context, R.style.NanoTimerDialogTheme)
       .setTitle(titleRes)
       .setView(panel.view)
       .setNegativeButton(R.string.cancel, null)
       .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int which) {
           listener.onNumberPicked(panel.getValue());
         }
       })
       .show();
  }

  /** The presets/stepper/unit panel itself, so a preference and a plain dialog share one control. */
  private static class Panel {

    private final View view;
    private final EditText tfValue;
    private final LinearLayout presetRow;
    private final int min;
    private final int max;
    private final int step;
    private final int[] presets;

    Panel(Context context, int min, int max, int step, int[] presets, CharSequence unit,
       int current) {
      this.min = min;
      this.max = max;
      this.step = step;
      this.presets = presets;

      LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      view = inflater.inflate(R.layout.number_picker, null);
      tfValue = (EditText) view.findViewById(R.id.tfValue);
      presetRow = (LinearLayout) view.findViewById(R.id.presetRow);

      setValue(current);
      tfValue.setFilters(new InputFilter[] { new InputFilter.LengthFilter(String.valueOf(max).length()) });

      TextView tvUnit = (TextView) view.findViewById(R.id.tvUnit);
      if (!TextUtils.isEmpty(unit)) {
        tvUnit.setText(unit);
        tvUnit.setVisibility(View.VISIBLE);
      }

      addPresets(inflater);

      Button buPlus = (Button) view.findViewById(R.id.buPlus);
      buPlus.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          setValue(Math.min(getValue() + Panel.this.step, Panel.this.max));
        }
      });

      Button buMinus = (Button) view.findViewById(R.id.buMinus);
      buMinus.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          setValue(Math.max(getValue() - Panel.this.step, Panel.this.min));
        }
      });

      tfValue.addTextChangedListener(new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) { }

        @Override
        public void afterTextChanged(Editable s) {
          markSelectedPreset();
        }
      });
    }

    private void addPresets(LayoutInflater inflater) {
      if (presets == null || presets.length == 0) {
        return;
      }
      for (final int preset : presets) {
        TextView chip = (TextView) inflater.inflate(R.layout.number_preset_chip, presetRow, false);
        chip.setText(String.valueOf(preset));
        chip.setOnClickListener(new OnClickListener() {
          @Override
          public void onClick(View v) {
            setValue(preset);
          }
        });
        presetRow.addView(chip);
      }
      // The row is centered, so the first chip must not carry the gap that separates the others.
      ViewGroup.MarginLayoutParams first =
         (ViewGroup.MarginLayoutParams) presetRow.getChildAt(0).getLayoutParams();
      first.leftMargin = 0;
      presetRow.setVisibility(View.VISIBLE);
      markSelectedPreset();
    }

    private void markSelectedPreset() {
      int value = getValue();
      for (int i = 0; i < presetRow.getChildCount(); i++) {
        presetRow.getChildAt(i).setSelected(presets[i] == value);
      }
    }

    private void setValue(int value) {
      tfValue.setText(String.valueOf(value));
      tfValue.setSelection(tfValue.getText().length());
    }

    /** An emptied field reads as the minimum, so the steppers and OK keep working while it is blank. */
    private int getValue() {
      int value;
      try {
        value = Integer.parseInt(tfValue.getText().toString());
      } catch (NumberFormatException e) {
        return min;
      }
      return Math.max(min, Math.min(max, value));
    }
  }

}
