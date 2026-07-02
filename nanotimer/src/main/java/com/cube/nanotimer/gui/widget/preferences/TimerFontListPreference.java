package com.cube.nanotimer.gui.widget.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.TextView;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.view.TimerFont;

/**
 * A {@link ListPreference} whose dialog shows every choice rendered in the actual timer
 * font, using a sample solve time as the preview text. Selecting a row confirms the
 * choice immediately (like a standard single-choice list preference).
 */
public class TimerFontListPreference extends ListPreference {

  private static final String SAMPLE_TIME = "12:34.50";
  private static final float PREVIEW_BASE_SIZE_SP = 32f;

  public TimerFontListPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
    final CharSequence[] entryValues = getEntryValues();
    final int selectedIndex = findIndexOfValue(getValue());

    ArrayAdapter<CharSequence> adapter =
        new ArrayAdapter<CharSequence>(getContext(), R.layout.timer_font_row, R.id.tvFontName, getEntries()) {
          @Override
          public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            TextView preview = (TextView) view.findViewById(R.id.tvFontPreview);
            RadioButton radio = (RadioButton) view.findViewById(R.id.rbFontSelected);
            preview.setText(SAMPLE_TIME);
            TimerFont font = TimerFont.fromId(Integer.parseInt(entryValues[position].toString()));
            font.applyTo(preview);
            preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, PREVIEW_BASE_SIZE_SP * font.getSizeScale());
            radio.setChecked(position == selectedIndex);
            return view;
          }
        };

    builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        // setValueIndex persists the new value and notifies listeners; the timer picks it
        // up via the shared-preference change listener / on resume.
        setValueIndex(which);
        dialog.dismiss();
      }
    });

    // Selecting a row is the confirmation, so drop the default "OK" button.
    builder.setPositiveButton(null, null);
  }

}
