package com.cube.nanotimer.gui.widget.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.appcompat.widget.SwitchCompat;
import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.ResultListener;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;

public class AddNewTimeDialog extends ConfirmDialog {

  private EditText tfMinutes;
  private EditText tfSeconds;
  private EditText tfHundreds;
  private EditText tfScramble;
  private SwitchCompat cbDNF;

  private static final String ARG_RESULT_LISTENER = "resultListener";
  private static final String ARG_SOLVE_TYPE = "solveType";
  private static final String ARG_SCRAMBLE = "scramble";

  public static AddNewTimeDialog newInstance(ResultListener resultListener, SolveType solveType, String scramble) {
    AddNewTimeDialog frag = new AddNewTimeDialog();

    Bundle args = new Bundle();
    args.putSerializable(ARG_RESULT_LISTENER, resultListener);
    args.putSerializable(ARG_SOLVE_TYPE, solveType);
    args.putString(ARG_SCRAMBLE, scramble);
    frag.setArguments(args);

    return frag;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    dialog = getDialog(R.string.add);
    return dialog;
  }

  @Override
  protected void onConfirm() {
    int minutes;
    int seconds;
    int millis;
    try {
      minutes = getIntValue(tfMinutes);
      seconds = getIntValue(tfSeconds);
      millis = getIntValue(tfHundreds, 3); // milliseconds
    } catch (NumberFormatException e) {
      DialogUtils.showInfoMessage(getContext(), R.string.invalid_integer_value);
      return;
    }
    if (!checkTimeValues(minutes, seconds, millis)) {
      return;
    }

    long time;
    if (cbDNF.isChecked()) {
      time = -1;
    }
    else {
      time = minutes * 60000;
      time += seconds * 1000;
      time += millis;
    }

    Bundle args = getArguments();
    final ResultListener resultListener = (ResultListener) args.getSerializable(ARG_RESULT_LISTENER) ;
    SolveType solveType = (SolveType) args.getSerializable(ARG_SOLVE_TYPE);
    String scramble = tfScramble.getText().toString().trim();
    if (scramble.isEmpty()) {
      scramble = null;
    }

    SolveTime solveTime = new SolveTime();
    solveTime.setTime(time);
    solveTime.setTimestamp(System.currentTimeMillis());
    solveTime.setSolveType(solveType);
    solveTime.setScramble(scramble);

    App.INSTANCE.getService().saveTime(solveTime, new DataCallback<SolveAverages>() {
      @Override
      public void onData(SolveAverages data) {
        if (resultListener != null) {
          resultListener.onResult(data);
        }
      }
    });

    dialog.dismiss();
  }

  @Override
  protected View getCustomView() {
    LayoutInflater factory = LayoutInflater.from(getActivity());
    View view = factory.inflate(R.layout.add_new_time_dialog, null);
    tfMinutes = (EditText) view.findViewById(R.id.tfMinutes);
    tfSeconds = (EditText) view.findViewById(R.id.tfSeconds);
    tfHundreds = (EditText) view.findViewById(R.id.tfHundreds);
    tfScramble = (EditText) view.findViewById(R.id.tfScramble);
    cbDNF = view.findViewById(R.id.cbDNF);

    tfScramble.setText(getArguments().getString(ARG_SCRAMBLE));
    // First tap (the one that focuses the field) selects the whole scramble so it can be
    // wiped/replaced in one go; once focused, further taps position the cursor normally.
    tfScramble.setOnTouchListener(new View.OnTouchListener() {
      private boolean focusedByThisTouch;

      @Override
      public boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
          case MotionEvent.ACTION_DOWN:
            focusedByThisTouch = !v.hasFocus();
            break;
          case MotionEvent.ACTION_UP:
            if (focusedByThisTouch) {
              focusedByThisTouch = false;
              v.post(new Runnable() {
                @Override
                public void run() {
                  tfScramble.selectAll();
                }
              });
            }
            break;
        }
        return false; // don't consume: normal focus and keyboard handling still runs
      }
    });

    tfMinutes.addTextChangedListener(new OnNumericFieldKeyListener(tfSeconds));
    tfSeconds.addTextChangedListener(new OnNumericFieldKeyListener(tfHundreds));
    tfHundreds.addTextChangedListener(new OnNumericFieldKeyListener(null));

    tfSeconds.setOnKeyListener(new OnBackspaceToPreviousFieldListener(tfMinutes));
    tfHundreds.setOnKeyListener(new OnBackspaceToPreviousFieldListener(tfSeconds));

    // The whole DNF row is tappable; dim the time fields while DNF is on since they are ignored.
    LinearLayout dnfRow = view.findViewById(R.id.llDNFRow);
    dnfRow.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        cbDNF.setChecked(!cbDNF.isChecked());
      }
    });
    cbDNF.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean checked) {
        float alpha = checked ? 0.35f : 1f;
        tfMinutes.setEnabled(!checked);
        tfSeconds.setEnabled(!checked);
        tfHundreds.setEnabled(!checked);
        tfMinutes.setAlpha(alpha);
        tfSeconds.setAlpha(alpha);
        tfHundreds.setAlpha(alpha);
      }
    });
    return view;
  }

  private int getIntValue(EditText editText) throws NumberFormatException {
    return getIntValue(editText, 0);
  }

  private int getIntValue(EditText editText, int minTextSize) throws NumberFormatException {
    int value = 0;
    StringBuilder stringValue = new StringBuilder(editText.getText().toString());
    while (stringValue.length() < minTextSize) {
      stringValue.append("0");
    }
    if (!stringValue.toString().trim().isEmpty()) {
      value = Integer.parseInt(stringValue.toString());
    }
    return value;
  }

  private boolean checkTimeValues(int minutes, int seconds, int hundreds) {
    boolean valid = true;
    if (minutes < 0) {
      tfMinutes.requestFocus();
      DialogUtils.showInfoMessage(getContext(), getString(R.string.invalid_value_for_field, getString(R.string.minutes)));
      valid = false;
    } else if (seconds < 0 || seconds >= 60) {
      tfSeconds.requestFocus();
      DialogUtils.showInfoMessage(getContext(), getString(R.string.invalid_value_for_field, getString(R.string.seconds)));
      valid = false;
    } else if (hundreds < 0 || hundreds >= 1000) {
      tfHundreds.requestFocus();
      DialogUtils.showInfoMessage(getContext(), getString(R.string.invalid_value_for_field, getString(R.string.hundreds)));
      valid = false;
    } else if (minutes == 0 && seconds == 0 && hundreds == 0 && !cbDNF.isChecked()) {
      DialogUtils.showInfoMessage(getContext(), R.string.please_set_a_value);
      valid = false;
    }
    return valid;
  }

  class OnNumericFieldKeyListener implements TextWatcher {
    private EditText next;

    public OnNumericFieldKeyListener(EditText next) {
      this.next = next;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
      if (s.length() >= 2) {
        if (next != null) {
          next.requestFocus();
        }
      }
    }
  }

  // When backspacing at the start of a field, jump back to the previous field and erase there,
  // so that holding/spamming backspace from the last field clears everything.
  class OnBackspaceToPreviousFieldListener implements View.OnKeyListener {
    private EditText previous;

    public OnBackspaceToPreviousFieldListener(EditText previous) {
      this.previous = previous;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN && previous != null) {
        EditText current = (EditText) v;
        if (current.getSelectionStart() == 0 && current.getSelectionEnd() == 0) {
          previous.requestFocus();
          Editable prevText = previous.getText();
          if (prevText.length() > 0) {
            prevText.delete(prevText.length() - 1, prevText.length());
          }
          previous.setSelection(previous.getText().length());
          return true;
        }
      }
      return false;
    }
  }

}
