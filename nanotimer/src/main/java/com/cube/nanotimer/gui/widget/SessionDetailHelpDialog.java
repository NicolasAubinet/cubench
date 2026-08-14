package com.cube.nanotimer.gui.widget;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.cube.nanotimer.R;

/**
 * What the session dialog's statistics mean. They are shown there as bare numbers and defined
 * nowhere else in the app, which is worst for the ones a name does not give away: deviation,
 * accuracy and the two averages that trim.
 *
 * <p>Takes the blind flag so it explains the same statistics the dialog behind it is showing: a
 * blind session swaps the plain average and the best averages for the success average, accuracy
 * and the best Mo3.
 */
public class SessionDetailHelpDialog extends NanoTimerBottomSheetFragment {

  private static final String ARG_BLIND = "blind";

  public static SessionDetailHelpDialog newInstance(boolean blind) {
    SessionDetailHelpDialog dialog = new SessionDetailHelpDialog();
    Bundle bundle = new Bundle();
    bundle.putBoolean(ARG_BLIND, blind);
    dialog.setArguments(bundle);
    return dialog;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.sessiondetail_help_dialog, container, false);

    boolean blind = getArguments() != null && getArguments().getBoolean(ARG_BLIND);
    v.findViewById(R.id.helpRowAverage).setVisibility(blind ? View.GONE : View.VISIBLE);
    v.findViewById(R.id.helpRowBestAverages).setVisibility(blind ? View.GONE : View.VISIBLE);
    v.findViewById(R.id.helpRowSuccessAverage).setVisibility(blind ? View.VISIBLE : View.GONE);
    v.findViewById(R.id.helpRowAccuracy).setVisibility(blind ? View.VISIBLE : View.GONE);
    v.findViewById(R.id.helpRowBestMo3).setVisibility(blind ? View.VISIBLE : View.GONE);

    v.findViewById(R.id.buSessionDetailHelpDone).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        dismiss();
      }
    });
    return v;
  }
}
