package com.cube.nanotimer.gui.widget.preferences;

import android.content.Context;
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
 * Also serves the inspection time, which used to have a scrolling wheel of its own.
 */
public class NumberEntryDialog extends DialogPreference {

  private EditText tfValue;
  private LinearLayout presetRow;

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
    LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View layout = inflater.inflate(R.layout.number_picker, null);
    tfValue = (EditText) layout.findViewById(R.id.tfValue);
    presetRow = (LinearLayout) layout.findViewById(R.id.presetRow);

    SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getContext());
    Integer value = p.getInt(getKey(), defaultValue);
    tfValue.setText(value.toString());
    tfValue.setSelection(tfValue.getText().length());

    final int maxTextSizeLength = String.valueOf(max).length();
    tfValue.setFilters( new InputFilter[] { new InputFilter.LengthFilter(maxTextSizeLength) } );

    TextView tvUnit = (TextView) layout.findViewById(R.id.tvUnit);
    if (!TextUtils.isEmpty(unit)) {
      tvUnit.setText(unit);
      tvUnit.setVisibility(View.VISIBLE);
    }

    addPresets(inflater);

    Button buPlus = (Button) layout.findViewById(R.id.buPlus);
    buPlus.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        int val = getValue();
        if (val < max) {
          setValue(Math.min(val + step, max));
        }
      }
    });

    Button buMinus = (Button) layout.findViewById(R.id.buMinus);
    buMinus.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        int val = getValue();
        if (val > min) {
          setValue(Math.max(val - step, min));
        }
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

    return layout;
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
        public void onClick(View view) {
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
    if (presetRow == null) {
      return;
    }
    int value = getValue();
    for (int i = 0; i < presetRow.getChildCount(); i++) {
      presetRow.getChildAt(i).setSelected(presets[i] == value);
    }
  }

  private void setValue(int value) {
    tfValue.setText(String.valueOf(value));
    tfValue.setSelection(tfValue.getText().length());
  }

  @Override
  protected void onDialogClosed(boolean positiveResult) {
    super.onDialogClosed(positiveResult);

    if (positiveResult) {
      int time = checkMinMaxValue(getValue());
      if (callChangeListener(time)) {
        persistInt(time);
      }
    }
  }

  private int checkMinMaxValue(int n) {
    if (n < min) {
      n = min;
    } else if (n > max) {
      n = max;
    }
    return n;
  }

  /** An emptied field reads as the minimum, so the steppers and OK keep working while it is blank. */
  private int getValue() {
    try {
      return Integer.parseInt(tfValue.getText().toString());
    } catch (NumberFormatException e) {
      return min;
    }
  }

}
