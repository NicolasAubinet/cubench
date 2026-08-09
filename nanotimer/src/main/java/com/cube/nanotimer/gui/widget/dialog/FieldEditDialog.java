package com.cube.nanotimer.gui.widget.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import com.cube.nanotimer.R;

public class FieldEditDialog extends ConfirmDialog {

  private int pos;
  private EditText tfName;

  private static final String ARG_FIELD_RENAMER = "fieldRenamer";
  private static final String ARG_POS = "pos";
  private static final String ARG_DEFAULT_NAME = "defaultName";

  public static FieldEditDialog newInstance(FieldRenamer fieldRenamer, int pos, String defaultName) {
    FieldEditDialog frag = new FieldEditDialog();
    Bundle args = new Bundle();
    args.putSerializable(ARG_FIELD_RENAMER, fieldRenamer);
    args.putInt(ARG_POS, pos);
    args.putString(ARG_DEFAULT_NAME, defaultName);
    frag.setArguments(args);
    return frag;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    dialog = getDialog(R.string.confirm);
    ((TextView) view.findViewById(R.id.tvDialogTitle)).setText(R.string.rename);

    pos = getArguments().getInt(ARG_POS);
    String defaultName = getArguments().getString(ARG_DEFAULT_NAME);

    tfName = (EditText) view.findViewById(R.id.tfName);
    tfName.setText(defaultName);
    tfName.setSelection(0, tfName.length());
    return dialog;
  }

  @Override
  protected void onConfirm() {
    FieldRenamer fieldRenamer = (FieldRenamer) getArguments().getSerializable(ARG_FIELD_RENAMER);
    if (fieldRenamer.renameField(pos, tfName.getText().toString())) {
      dialog.dismiss();
    }
  }

}
