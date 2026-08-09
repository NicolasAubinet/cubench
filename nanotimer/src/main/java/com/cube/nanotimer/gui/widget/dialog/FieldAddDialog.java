package com.cube.nanotimer.gui.widget.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import com.cube.nanotimer.R;

public class FieldAddDialog extends ConfirmDialog {

  private static final String FIELD_CREATOR_KEY = "fieldCreator";

  public static FieldAddDialog newInstance(FieldCreator fieldCreator) {
    FieldAddDialog frag = new FieldAddDialog();

    Bundle args = new Bundle();
    args.putSerializable(FIELD_CREATOR_KEY, fieldCreator);
    frag.setArguments(args);

    return frag;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    dialog = getDialog(R.string.add);
    ((TextView) view.findViewById(R.id.tvDialogTitle)).setText(R.string.add);
    return dialog;
  }

  @Override
  protected void onConfirm() {
    EditText tfName = (EditText) view.findViewById(R.id.tfName);
    FieldCreator fieldCreator = (FieldCreator) getArguments().getSerializable(FIELD_CREATOR_KEY);
    if (fieldCreator.createField(tfName.getText().toString(), null)) {
      dialog.dismiss();
    }
  }

}
