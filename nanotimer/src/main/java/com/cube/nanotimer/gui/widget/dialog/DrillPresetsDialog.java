package com.cube.nanotimer.gui.widget.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.DrillCasePreset;
import com.cube.nanotimer.util.YesNoListener;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.GUIUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The named picks of a case family, raised over the picker they belong to.
 *
 * <p><b>No preset is ever "in effect".</b> The pick behind this popup is the only state there is,
 * and a row is lit while the pick happens to be exactly what that preset holds. That is why nothing
 * here is confirmed, saved or reverted: ticking a case in the picker afterwards simply stops the row
 * matching, which is the truth of what happened and costs the user no explaining.
 *
 * <p>Not a fragment: it is a prompt raised over a dialog, as the algorithms sheet raises its own.
 */
public class DrillPresetsDialog {

  public interface Listener {
    /** Ticks exactly these cases in the picker behind the popup. */
    void onPresetApplied(Set<String> cases);

    /** A preset was saved, updated, renamed or deleted. The pick itself has not moved. */
    void onPresetsChanged();
  }

  private final Activity activity;
  private final String family;
  private final Set<String> picked;
  private final Listener listener;
  private final List<DrillCasePreset> presets;

  private AlertDialog dialog;
  private LinearLayout rows;
  private View empty;
  private TextView save;
  private View hint;
  private EditText field;
  /** Whether the popup shows the name field rather than the presets. */
  private boolean naming;
  /** The preset being renamed, or null while a new one is named. */
  private DrillCasePreset renaming;

  /** @param picked what the picker has ticked, which is what a preset saved here would hold */
  public static void show(Activity activity, String family, Set<String> picked, Listener listener) {
    new DrillPresetsDialog(activity, family, picked, listener).open();
  }

  private DrillPresetsDialog(Activity activity, String family, Set<String> picked,
      Listener listener) {
    this.activity = activity;
    this.family = family;
    this.picked = picked;
    this.listener = listener;
    this.presets = Options.INSTANCE.getDrillCasePresets(family);
  }

  private void open() {
    View view = LayoutInflater.from(activity).inflate(R.layout.drill_presets_dialog, null);
    rows = view.findViewById(R.id.llPresets);
    empty = view.findViewById(R.id.tvPresetsEmpty);
    hint = view.findViewById(R.id.tvPresetsHint);
    save = view.findViewById(R.id.tvPresetsSave);
    field = view.findViewById(R.id.etPresetName);
    save.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        askForName(null);
      }
    });
    field.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      @Override
      public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
          submitName();
          return true;
        }
        return false;
      }
    });

    dialog = new AlertDialog.Builder(activity, R.style.NanoTimerDialogTheme)
        .setTitle(R.string.drill_presets)
        .setView(view)
        .setPositiveButton(R.string.close, null)
        .setNegativeButton(R.string.cancel, null)
        .create();
    dialog.show();
    // This window takes the keyboard itself, so it is never raised against the picker underneath.
    dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    // Bound after showing so that Save can refuse an empty name without the popup closing.
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (naming) {
          submitName();
        } else {
          dialog.dismiss();
        }
      }
    });
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showList();
      }
    });
    showList();
  }

  private void refresh() {
    if (naming) {
      return;
    }
    rows.removeAllViews();
    for (DrillCasePreset preset : presets) {
      rows.addView(row(preset));
    }
    boolean any = !presets.isEmpty();
    empty.setVisibility(any ? View.GONE : View.VISIBLE);
    hint.setVisibility(any ? View.VISIBLE : View.GONE);
    // A preset holding no case would drill nothing, and a pick that already has a name does not
    // need a second one.
    boolean nameable = !picked.isEmpty() && DrillCasePreset.matching(presets, picked) == null;
    save.setVisibility(nameable ? View.VISIBLE : View.GONE);
    save.setText(activity.getResources().getQuantityString(R.plurals.drill_presets_save,
        picked.size(), picked.size()));
  }

  /** One preset: its name, how many cases it holds, and whether the pick stands at it. */
  private View row(final DrillCasePreset preset) {
    LinearLayout row = new LinearLayout(activity);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(12), dp(11), dp(12), dp(11));
    row.setBackgroundResource(
        preset.holds(picked) ? R.drawable.case_alg_mine : R.drawable.case_alg);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.topMargin = dp(6);
    row.setLayoutParams(params);
    row.setClickable(true);
    row.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        listener.onPresetApplied(preset.getCases());
        dialog.dismiss();
      }
    });
    row.setOnLongClickListener(new View.OnLongClickListener() {
      @Override
      public boolean onLongClick(View v) {
        manage(preset);
        return true;
      }
    });

    TextView name = GUIUtils.newTextView(activity);
    name.setText(preset.getName());
    name.setTextSize(15);
    name.setTextColor(ContextCompat.getColor(activity, R.color.white));
    name.setLayoutParams(new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    row.addView(name);

    TextView count = GUIUtils.newTextView(activity);
    count.setText(String.valueOf(preset.getCases().size()));
    count.setTextSize(13);
    count.setTextColor(ContextCompat.getColor(activity, R.color.secondary_text));
    count.setPadding(dp(8), 0, 0, 0);
    row.addView(count);
    return row;
  }

  private void manage(final DrillCasePreset preset) {
    // Updating is only offered when there is something to update it with: a preset taking an empty
    // pick would hold no case, and a drill cannot be started from one.
    final boolean updatable = !picked.isEmpty();
    List<CharSequence> items = new ArrayList<CharSequence>();
    if (updatable) {
      items.add(activity.getString(R.string.drill_presets_update));
    }
    items.add(activity.getString(R.string.rename));
    items.add(activity.getString(R.string.delete));
    new AlertDialog.Builder(activity, R.style.NanoTimerDialogTheme)
        .setTitle(preset.getName())
        .setItems(items.toArray(new CharSequence[items.size()]),
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface d, int which) {
                int chosen = updatable ? which : which + 1;
                if (chosen == 0) {
                  preset.setCases(picked);
                  store();
                } else if (chosen == 1) {
                  askForName(preset);
                } else {
                  confirmDelete(preset);
                }
              }
            })
        .show();
  }

  /** Asked, because the pick a preset was made from is recoverable from nowhere else. */
  private void confirmDelete(final DrillCasePreset preset) {
    DialogUtils.showYesNoConfirmation(activity,
        activity.getString(R.string.drill_presets_delete_confirm, preset.getName()),
        new YesNoListener() {
          @Override
          public void onYes() {
            presets.remove(preset);
            store();
          }

          @Override
          public void onNo() {
          }
        });
  }

  /**
   * Turns the popup into a name field, for a new preset or to rename one. A name already in use
   * takes that preset over rather than standing beside it: two presets under one name is not a
   * state the list could explain.
   */
  private void askForName(DrillCasePreset preset) {
    naming = true;
    // A typed name is worth more than a stray tap outside the popup.
    dialog.setCanceledOnTouchOutside(false);
    renaming = preset;
    dialog.setTitle(preset == null ? R.string.drill_presets_name : R.string.rename);
    rows.setVisibility(View.GONE);
    empty.setVisibility(View.GONE);
    save.setVisibility(View.GONE);
    hint.setVisibility(View.GONE);
    field.setVisibility(View.VISIBLE);
    field.setText(preset == null ? "" : preset.getName());
    field.setSelection(field.getText().length());
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.save);
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(View.VISIBLE);
    field.requestFocus();
    field.post(new Runnable() {
      @Override
      public void run() {
        keyboard().showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
      }
    });
  }

  /** Back to the presets, from the name field or when the popup opens. */
  private void showList() {
    naming = false;
    dialog.setCanceledOnTouchOutside(true);
    renaming = null;
    if (field.getVisibility() == View.VISIBLE) {
      keyboard().hideSoftInputFromWindow(field.getWindowToken(), 0);
    }
    field.setVisibility(View.GONE);
    rows.setVisibility(View.VISIBLE);
    dialog.setTitle(R.string.drill_presets);
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.close);
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(View.GONE);
    refresh();
  }

  /** An empty name leaves the field standing rather than saving nothing. */
  private void submitName() {
    String typed = field.getText().toString().trim();
    if (typed.isEmpty()) {
      return;
    }
    DrillCasePreset existing = DrillCasePreset.named(presets, typed);
    if (existing != null && existing != renaming) {
      confirmReplace(renaming, typed);
      return;
    }
    keep(renaming, typed);
    showList();
  }

  private InputMethodManager keyboard() {
    return (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
  }

  /**
   * A name already in use takes the preset that had it, cases and all, and nothing else could give
   * those back. So it is asked exactly as a delete is, and saying no leaves the field standing with
   * the name still in it to be changed.
   */
  private void confirmReplace(final DrillCasePreset renaming, final String name) {
    DialogUtils.showYesNoConfirmation(activity,
        activity.getString(R.string.drill_presets_replace_confirm, name),
        new YesNoListener() {
          @Override
          public void onYes() {
            keep(renaming, name);
            showList();
          }

          @Override
          public void onNo() {
          }
        });
  }

  private void keep(DrillCasePreset renaming, String name) {
    DrillCasePreset existing = DrillCasePreset.named(presets, name);
    if (renaming != null) {
      if (existing != null && existing != renaming) {
        presets.remove(existing);
      }
      renaming.setName(name);
    } else if (existing != null) {
      existing.setCases(picked);
    } else {
      presets.add(new DrillCasePreset(name, picked));
    }
    store();
  }

  private void store() {
    Options.INSTANCE.setDrillCasePresets(family, presets);
    listener.onPresetsChanged();
    refresh();
  }

  private int dp(int value) {
    return (int) (value * activity.getResources().getDisplayMetrics().density);
  }
}
