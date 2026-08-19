package com.cube.nanotimer.gui.widget.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import com.cube.nanotimer.R;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.cube.SmartCubeGate;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.TimerQuickAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SolveTypeAddDialog extends ConfirmDialog {

  public static final String KEY_BLD = "key_bld";
  public static final String KEY_INSPECTION = "key_inspection";
  public static final String KEY_SCRAMBLE_TYPE = "key_scrambleType";
  public static final String KEY_QUICK_ACTION = "key_quickAction";
  /** The chosen method's code, empty to follow the preferred method. A blind type always follows. */
  public static final String KEY_METHOD = "key_method";

  private static final String ARG_FIELD_CREATOR = "fieldCreator";
  private static final String ARG_CUBE_TYPE = "cubeType";
  private static final String ARG_EDIT = "edit";
  private static final String ARG_EDIT_POSITION = "editPosition";
  private static final String ARG_EDIT_NAME = "editName";
  private static final String ARG_EDIT_BLIND = "editBlind";
  private static final String ARG_EDIT_INSPECTION = "editInspection";
  private static final String ARG_EDIT_SCRAMBLE_NAME = "editScrambleName";
  private static final String ARG_EDIT_QUICK_ACTION = "editQuickAction";
  private static final String ARG_EDIT_METHOD = "editMethod";

  /**
   * The spinner's choices: following the preferred method first, then each sighted method that can
   * override it, in order of how common they are. Null is the follower, and the one stored as NULL.
   */
  private static final CubeMethod[] METHODS =
      {null, CubeMethod.CFOP, CubeMethod.ROUX, CubeMethod.LBL};

  // Offered in the order the timer menu lists them, with the opt-out last.
  private static final TimerQuickAction[] QUICK_ACTIONS = {
      TimerQuickAction.SCRAMBLE_VIEW, TimerQuickAction.PLUS_TWO, TimerQuickAction.DNF,
      TimerQuickAction.DELETE, TimerQuickAction.LAST_SOLVE, TimerQuickAction.ADD_TIME,
      TimerQuickAction.CROSS_SOLVER, TimerQuickAction.NONE};

  private EditText tfName;
  private LinearLayout scrambleTypeLayout;
  private Spinner spScrambleType;
  private Spinner spQuickAction;
  private Spinner spMethod;
  private SwitchCompat swBlind;
  private SwitchCompat swInspection;
  // What the inspection switch held before blind mode forced it off, to give back when blind is unticked.
  private boolean inspectionBeforeBlind = true;

  private ScrambleType previousScrambleType;
  // Spinner position of the edited solve type's scramble type, resolved while the list is built.
  private int editScrambleTypePosition;
  // The quick actions that apply to this cube type, in spinner order.
  private final List<TimerQuickAction> quickActions = new ArrayList<>();

  public static SolveTypeAddDialog newInstance(FieldCreator fieldCreator, CubeType cubeType) {
    SolveTypeAddDialog frag = new SolveTypeAddDialog();
    Bundle args = new Bundle();
    args.putSerializable(ARG_FIELD_CREATOR, fieldCreator);
    args.putString(ARG_CUBE_TYPE, cubeType.toString());
    frag.setArguments(args);
    return frag;
  }

  // Opens the same dialog pre-filled with an existing solve type's info, to edit it in place.
  // fieldEditor must also implement FieldCreator (they are the same object) - it is stored once.
  // scrambleTypeName is the name of the solve type's scramble type, or null for the default scramble.
  public static <T extends FieldCreator & FieldEditor> SolveTypeAddDialog newInstanceForEdit(
      T fieldEditor, CubeType cubeType, int position, String name, boolean blind, boolean inspection,
      String scrambleTypeName, TimerQuickAction quickAction, CubeMethod method) {
    SolveTypeAddDialog frag = new SolveTypeAddDialog();
    Bundle args = new Bundle();
    args.putSerializable(ARG_FIELD_CREATOR, fieldEditor);
    args.putString(ARG_CUBE_TYPE, cubeType.toString());
    args.putBoolean(ARG_EDIT, true);
    args.putInt(ARG_EDIT_POSITION, position);
    args.putString(ARG_EDIT_NAME, name);
    args.putBoolean(ARG_EDIT_BLIND, blind);
    args.putBoolean(ARG_EDIT_INSPECTION, inspection);
    args.putString(ARG_EDIT_SCRAMBLE_NAME, scrambleTypeName);
    args.putInt(ARG_EDIT_QUICK_ACTION, quickAction.getId());
    args.putString(ARG_EDIT_METHOD, method == null ? "" : method.getCode());
    frag.setArguments(args);
    return frag;
  }

  private boolean isEditMode() {
    return getArguments().getBoolean(ARG_EDIT, false);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    dialog = buildDialog();

    tfName = (EditText) view.findViewById(R.id.tfName);

    scrambleTypeLayout = (LinearLayout) view.findViewById(R.id.scrambleTypeLayout);

    final CubeType cubeType = CubeType.valueOf(getArguments().getString(ARG_CUBE_TYPE));
    ScrambleType[] scrambleTypes = cubeType.getAvailableScrambleTypes();
    if (scrambleTypes.length > 0) {
      scrambleTypeLayout.setVisibility(View.VISIBLE);

      String editScrambleTypeName = getArguments().getString(ARG_EDIT_SCRAMBLE_NAME);
      List<CharSequence> scrambleTypesNames = new ArrayList<>();
      for (int i = 0; i < scrambleTypes.length; i++) {
        scrambleTypesNames.add(getScrambleTypeTextString(scrambleTypes[i]));
        // Match by name (ScrambleType compares by name) to find the position to pre-select in edit mode.
        if (editScrambleTypeName != null && editScrambleTypeName.equals(scrambleTypes[i].getName())) {
          editScrambleTypePosition = i;
        }
      }

      spScrambleType = (Spinner) view.findViewById(R.id.spScrambleType);
      ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(getContext(), R.layout.spinner_item, scrambleTypesNames);
      adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
      spScrambleType.setAdapter(adapter);
      if (scrambleTypesNames.size() > 0) {
        spScrambleType.setSelection(0);
      }

      spScrambleType.setOnItemSelectedListener(new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
          String tfNameText = tfName.getText().toString().trim();
          // adapt solve type name automatically if the field is empty, or if it contains the value of the previously selected scramble type
          if (pos > 0 && (tfNameText.isEmpty() || (previousScrambleType != null && tfNameText.equals(getScrambleTypeTextString(previousScrambleType))))) {
            ScrambleType scrambleType = cubeType.getAvailableScrambleTypes()[pos];
            tfName.setText(getScrambleTypeTextString(scrambleType));
            previousScrambleType = scrambleType;
          }
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
        }
      });
    } else {
      scrambleTypeLayout.setVisibility(View.GONE);
    }

    swBlind = (SwitchCompat) view.findViewById(R.id.swBlind);
    swInspection = (SwitchCompat) view.findViewById(R.id.swInspection);
    swInspection.setChecked(true);
    // The switch is not clickable itself: the whole row is, so the label answers to a tap too.
    view.findViewById(R.id.rowBlind).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        swBlind.toggle();
      }
    });
    view.findViewById(R.id.rowInspection).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        swInspection.toggle();
      }
    });
    initQuickActionSpinner(cubeType);
    initMethodSpinner();

    ((TextView) view.findViewById(R.id.tvCubeType)).setText(cubeType.getName());
    ((TextView) view.findViewById(R.id.tvDialogTitle)).setText(
        isEditMode() ? R.string.edit_solvetype : R.string.add_solvetype);

    if (isEditMode()) {
      // Pre-fill with the existing solve type's values. Setting the name up front also stops the
      // scramble spinner's auto-naming listener from overwriting it (it only kicks in on an empty name).
      tfName.setText(getArguments().getString(ARG_EDIT_NAME));
      tfName.setSelection(0, tfName.length());
      if (spScrambleType != null && editScrambleTypePosition < spScrambleType.getCount()) {
        spScrambleType.setSelection(editScrambleTypePosition);
      }
      swBlind.setChecked(getArguments().getBoolean(ARG_EDIT_BLIND, false));
      swInspection.setChecked(getArguments().getBoolean(ARG_EDIT_INSPECTION, true));
      selectQuickAction(TimerQuickAction.fromId(
          getArguments().getInt(ARG_EDIT_QUICK_ACTION, TimerQuickAction.getDefault(false).getId())));
      selectMethod(CubeMethod.fromCode(getArguments().getString(ARG_EDIT_METHOD, "")));
    } else {
      selectQuickAction(TimerQuickAction.getDefault(false));
      selectMethod(null); // a new type follows the preferred method rather than freezing a copy of it
    }
    refreshInspectionEnabled();
    refreshMethodEnabled();

    // Only attached once the values above are in place, so pre-filling does not trip it.
    swBlind.setOnCheckedChangeListener(new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        // Follow the blind flag only while the quick action is still the other mode's default:
        // an explicitly chosen action is the user's, and stays put.
        if (getSelectedQuickAction() == TimerQuickAction.getDefault(!isChecked)) {
          selectQuickAction(TimerQuickAction.getDefault(isChecked));
        }
        // Blind mode takes inspection away rather than defaulting it, so remember what to give back.
        if (isChecked) {
          inspectionBeforeBlind = swInspection.isChecked();
        }
        swInspection.setChecked(!isChecked && inspectionBeforeBlind);
        refreshInspectionEnabled();
        refreshMethodEnabled();
      }
    });

    return dialog;
  }

  // The frame's own button bar is left off: the layout ends on the rounded action row instead.
  private Dialog buildDialog() {
    view = getCustomView();
    AlertDialog d = new AlertDialog.Builder(getActivity(), getDialogTheme()).setView(view).create();

    ((Button) view.findViewById(R.id.buConfirm)).setText(isEditMode() ? R.string.save : R.string.add);
    view.findViewById(R.id.buConfirm).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        onConfirm();
      }
    });
    view.findViewById(R.id.buCancel).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        dismiss();
      }
    });

    showSoftKeyboard(d);
    return d;
  }

  /**
   * A blindfolded solver has nothing to inspect, memorisation being the phase inspection would sit
   * in, so blind mode holds the switch off and says why rather than leaving it dead without a word.
   */
  private void refreshInspectionEnabled() {
    boolean enabled = !swBlind.isChecked();
    swInspection.setEnabled(enabled);
    view.findViewById(R.id.rowInspection).setEnabled(enabled);
    view.findViewById(R.id.tvInspectionTitle).setAlpha(enabled ? 1f : 0.5f);
    ((TextView) view.findViewById(R.id.tvInspectionSummary)).setText(
        enabled ? R.string.inspection_summary : R.string.inspection_summary_blind);
  }

  /**
   * A blindfolded solve is not a sighted method read through a blindfold — it is memorised first and
   * its steps are the piece types — so the blind switch answers this and the spinner gives way to
   * what it answered, rather than sitting there greyed out over a choice that no longer applies.
   */
  private void refreshMethodEnabled() {
    boolean sighted = !swBlind.isChecked();
    spMethod.setVisibility(sighted ? View.VISIBLE : View.GONE);
    view.findViewById(R.id.tvMethodBlind).setVisibility(sighted ? View.GONE : View.VISIBLE);
    view.findViewById(R.id.tvMethodLabel).setAlpha(sighted ? 1f : 0.5f);
  }

  private void initMethodSpinner() {
    // Set up either way, and only the field hidden: a type edited in a build without the smart cube
    // keeps the method it was given rather than being quietly reset to the preferred one.
    view.findViewById(R.id.methodSection)
        .setVisibility(SmartCubeGate.ENABLED ? View.VISIBLE : View.GONE);
    spMethod = (Spinner) view.findViewById(R.id.spMethod);
    List<CharSequence> names = new ArrayList<>();
    for (CubeMethod method : METHODS) {
      names.add(getMethodName(method));
    }
    ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(getContext(), R.layout.spinner_item, names);
    adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
    spMethod.setAdapter(adapter);

    view.findViewById(R.id.buMethodInfo).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        DialogUtils.showOkDialog(getActivity(), R.string.method, R.string.method_info_message);
      }
    });
  }

  /** The follower names the method it currently stands for, that choice being invisible otherwise. */
  private CharSequence getMethodName(CubeMethod method) {
    if (method != null) {
      return getString(Utils.getMethodLabel(method));
    }
    return getString(R.string.method_default,
        getString(Utils.getMethodLabel(Options.INSTANCE.getPreferredMethod())));
  }

  /** Null when the type follows the preferred method rather than overriding it. */
  private CubeMethod getSelectedMethod() {
    int position = spMethod.getSelectedItemPosition();
    return (position >= 0 && position < METHODS.length) ? METHODS[position] : METHODS[0];
  }

  /** A blind type follows too: its method is settled by the blind flag, and its spinner is hidden. */
  private void selectMethod(CubeMethod method) {
    CubeMethod wanted = (method == CubeMethod.BLIND) ? null : method;
    for (int i = 0; i < METHODS.length; i++) {
      if (METHODS[i] == wanted) {
        spMethod.setSelection(i);
        return;
      }
    }
    spMethod.setSelection(0);
  }

  private void initQuickActionSpinner(CubeType cubeType) {
    quickActions.clear();
    for (TimerQuickAction action : QUICK_ACTIONS) {
      if (action == TimerQuickAction.CROSS_SOLVER && cubeType != CubeType.THREE_BY_THREE) {
        continue; // the cross solver only knows 3x3
      }
      quickActions.add(action);
    }

    spQuickAction = (Spinner) view.findViewById(R.id.spQuickAction);
    spQuickAction.setAdapter(new QuickActionAdapter(getContext(), quickActions));
  }

  private int getQuickActionLabel(TimerQuickAction action) {
    switch (action) {
      case SCRAMBLE_VIEW: return R.string.scramble_view;
      case PLUS_TWO:      return R.string.add_penalty;
      case DNF:           return R.string.DNF;
      case DELETE:        return R.string.delete;
      case LAST_SOLVE:    return R.string.last_solve;
      case ADD_TIME:      return R.string.add_time;
      case CROSS_SOLVER:  return R.string.cross_solver;
      default:            return R.string.quick_action_none;
    }
  }

  /** The menu's own icons, so an action is picked by the sight of it, not only by its name. */
  private int getQuickActionIcon(TimerQuickAction action) {
    switch (action) {
      // The flat white variant, the one the menu shows: the coloured icon is for the action bar.
      case SCRAMBLE_VIEW: return R.drawable.ic_menu_scramble_view;
      case PLUS_TWO:      return R.drawable.ic_menu_plus_two;
      case DNF:           return R.drawable.ic_menu_dnf;
      case DELETE:        return R.drawable.ic_menu_delete;
      case LAST_SOLVE:    return R.drawable.ic_menu_last_solve;
      case ADD_TIME:      return R.drawable.ic_menu_add_time;
      case CROSS_SOLVER:  return R.drawable.ic_menu_cross_solver;
      default:            return 0; // "none" has nothing to show
    }
  }

  private class QuickActionAdapter extends ArrayAdapter<TimerQuickAction> {

    QuickActionAdapter(Context context, List<TimerQuickAction> actions) {
      super(context, R.layout.spinner_item_icon, actions);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      return buildRow(position, parent, R.layout.spinner_item_icon);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
      return buildRow(position, parent, R.layout.spinner_dropdown_item_icon);
    }

    // convertView is ignored: a Spinner pools closed and opened rows together, so it comes back
    // as the wrong layout.
    private View buildRow(int position, ViewGroup parent, int layoutId) {
      View row = LayoutInflater.from(getContext()).inflate(layoutId, parent, false);
      TimerQuickAction action = getItem(position);
      ((TextView) row.findViewById(R.id.tvText)).setText(getQuickActionLabel(action));
      ImageView imgIcon = (ImageView) row.findViewById(R.id.imgIcon);
      int iconRes = getQuickActionIcon(action);
      if (iconRes != 0) {
        imgIcon.setImageResource(iconRes);
        imgIcon.setVisibility(View.VISIBLE);
      } else {
        imgIcon.setVisibility(View.INVISIBLE); // keeps "none" aligned with the rows that have one
      }
      return row;
    }
  }

  private TimerQuickAction getSelectedQuickAction() {
    int position = spQuickAction.getSelectedItemPosition();
    return (position >= 0 && position < quickActions.size()) ? quickActions.get(position) : TimerQuickAction.NONE;
  }

  private void selectQuickAction(TimerQuickAction action) {
    int position = quickActions.indexOf(action);
    spQuickAction.setSelection(position >= 0 ? position : 0);
  }

  private String getScrambleTypeTextString(ScrambleType scrambleType) {
    return Utils.toScrambleTypeLocalizedName(getContext(), scrambleType);
  }

  @Override
  protected void onConfirm() {
    Properties props = new Properties();
    props.put(KEY_BLD, String.valueOf(swBlind.isChecked()));
    props.put(KEY_INSPECTION, String.valueOf(swInspection.isChecked()));
    int scrambleTypeItemPosition = -1;
    if (spScrambleType != null) {
      scrambleTypeItemPosition = spScrambleType.getSelectedItemPosition();
    }
    props.put(KEY_SCRAMBLE_TYPE, String.valueOf(scrambleTypeItemPosition));
    // The default is stored as no answer at all, so a type left on it follows the default wherever
    // it moves next. Picking it by hand is the same thing, and reads the same on screen.
    TimerQuickAction quickAction = getSelectedQuickAction();
    boolean isDefault = (quickAction == TimerQuickAction.getDefault(swBlind.isChecked()));
    props.put(KEY_QUICK_ACTION, isDefault ? "" : String.valueOf(quickAction.getId()));
    // A blind type is read as blind whatever the spinner still holds, so it stores no sighted
    // override: a type ticked blind would otherwise keep one nothing can act on.
    CubeMethod method = swBlind.isChecked() ? null : getSelectedMethod();
    props.put(KEY_METHOD, method == null ? "" : method.getCode());

    boolean confirmed;
    if (isEditMode()) {
      FieldEditor fieldEditor = (FieldEditor) getArguments().getSerializable(ARG_FIELD_CREATOR);
      confirmed = fieldEditor.editField(getArguments().getInt(ARG_EDIT_POSITION), tfName.getText().toString(), props);
    } else {
      FieldCreator fieldCreator = (FieldCreator) getArguments().getSerializable(ARG_FIELD_CREATOR);
      confirmed = fieldCreator.createField(tfName.getText().toString(), props);
    }
    if (confirmed) {
      dialog.dismiss();
    }
  }

  @Override
  protected View getCustomView() {
    LayoutInflater factory = LayoutInflater.from(getActivity());
    return factory.inflate(R.layout.solvetype_add_dialog, null);
  }

}
