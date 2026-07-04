package com.cube.nanotimer.gui.widget.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ScrambleType;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SolveTypeAddDialog extends ConfirmDialog {

  public static final String KEY_BLD = "key_bld";
  public static final String KEY_SCRAMBLE_TYPE = "key_scrambleType";

  private static final String ARG_FIELD_CREATOR = "fieldCreator";
  private static final String ARG_CUBE_TYPE = "cubeType";
  private static final String ARG_EDIT = "edit";
  private static final String ARG_EDIT_POSITION = "editPosition";
  private static final String ARG_EDIT_NAME = "editName";
  private static final String ARG_EDIT_BLIND = "editBlind";
  private static final String ARG_EDIT_SCRAMBLE_NAME = "editScrambleName";

  private EditText tfName;
  private LinearLayout scrambleTypeLayout;
  private Spinner spScrambleType;

  private ScrambleType previousScrambleType;
  // Spinner position of the edited solve type's scramble type, resolved while the list is built.
  private int editScrambleTypePosition;

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
      T fieldEditor, CubeType cubeType, int position, String name, boolean blind, String scrambleTypeName) {
    SolveTypeAddDialog frag = new SolveTypeAddDialog();
    Bundle args = new Bundle();
    args.putSerializable(ARG_FIELD_CREATOR, fieldEditor);
    args.putString(ARG_CUBE_TYPE, cubeType.toString());
    args.putBoolean(ARG_EDIT, true);
    args.putInt(ARG_EDIT_POSITION, position);
    args.putString(ARG_EDIT_NAME, name);
    args.putBoolean(ARG_EDIT_BLIND, blind);
    args.putString(ARG_EDIT_SCRAMBLE_NAME, scrambleTypeName);
    frag.setArguments(args);
    return frag;
  }

  private boolean isEditMode() {
    return getArguments().getBoolean(ARG_EDIT, false);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    dialog = getDialog(isEditMode() ? R.string.save : R.string.add);

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

    if (isEditMode()) {
      // Pre-fill with the existing solve type's values. Setting the name up front also stops the
      // scramble spinner's auto-naming listener from overwriting it (it only kicks in on an empty name).
      tfName.setText(getArguments().getString(ARG_EDIT_NAME));
      tfName.setSelection(0, tfName.length());
      if (spScrambleType != null && editScrambleTypePosition < spScrambleType.getCount()) {
        spScrambleType.setSelection(editScrambleTypePosition);
      }
      CheckBox cbBlind = (CheckBox) view.findViewById(R.id.cbBlind);
      cbBlind.setChecked(getArguments().getBoolean(ARG_EDIT_BLIND, false));
    }

    return dialog;
  }

  private String getScrambleTypeTextString(ScrambleType scrambleType) {
    return Utils.toScrambleTypeLocalizedName(getContext(), scrambleType);
  }

  @Override
  protected void onConfirm() {
    CheckBox cbBlind = (CheckBox) view.findViewById(R.id.cbBlind);

    Properties props = new Properties();
    props.put(KEY_BLD, String.valueOf(cbBlind.isChecked()));
    int scrambleTypeItemPosition = -1;
    if (spScrambleType != null) {
      scrambleTypeItemPosition = spScrambleType.getSelectedItemPosition();
    }
    props.put(KEY_SCRAMBLE_TYPE, String.valueOf(scrambleTypeItemPosition));

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
