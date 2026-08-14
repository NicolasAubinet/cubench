package com.cube.nanotimer.gui.widget;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.cube.nanotimer.R;

/**
 * What the history card's three cells can be set to. The names are the sport's shorthand, and
 * nothing says which of them move with every solve and which look back over everything.
 *
 * <p>Reached from the ? on the picker that lists them, rather than from the card itself: the card
 * is the home screen, and a help button does not belong on it.
 *
 * <p>Takes the blind flag, since a blind solve type is offered a different set: no Ao5, and the
 * accuracies instead.
 */
public class HistoryHelpDialog extends NanoTimerBottomSheetFragment {

  private static final String ARG_BLIND = "blind";

  public static HistoryHelpDialog newInstance(boolean blind) {
    HistoryHelpDialog dialog = new HistoryHelpDialog();
    Bundle bundle = new Bundle();
    bundle.putBoolean(ARG_BLIND, blind);
    dialog.setArguments(bundle);
    return dialog;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.history_help_dialog, container, false);

    boolean blind = getArguments() != null && getArguments().getBoolean(ARG_BLIND);
    v.findViewById(R.id.helpRowBlind).setVisibility(blind ? View.VISIBLE : View.GONE);

    v.findViewById(R.id.buHistoryHelpDone).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        dismiss();
      }
    });
    return v;
  }
}
