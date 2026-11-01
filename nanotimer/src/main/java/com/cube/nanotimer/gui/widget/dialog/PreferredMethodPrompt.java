package com.cube.nanotimer.gui.widget.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeMethod;

/**
 * Asks, once, which method the solver uses. Put at the first connection because that is the moment
 * the answer starts to matter: before a cube, nothing reads a solve's steps. The timer asks again
 * if that moment was missed, the sheet having closed mid-connect or the cube having auto-connected.
 *
 * <p>The answer is kept in the one place rather than copied onto the solve types, so it holds for
 * the types that predate the question as well as the ones made after it, and changing it later
 * re-reads every type that did not override it.
 *
 * <p>It is not dismissible, and there is no "work it out yourself" option: reading the method off
 * the solve is a guess, and a wrong guess breaks a solve down into steps it never had.
 */
public final class PreferredMethodPrompt {

  /** The sighted methods a solve can be broken down into, in order of how common they are. */
  private static final CubeMethod[] METHOD_CHOICES =
      {CubeMethod.CFOP, CubeMethod.ROUX, CubeMethod.LBL};

  private PreferredMethodPrompt() {
  }

  public static void askOnce(Activity activity) {
    if (activity.isFinishing() || Options.INSTANCE.isPreferredMethodAsked()) {
      return;
    }
    LayoutInflater inflater = activity.getLayoutInflater();
    View content = inflater.inflate(R.layout.preferred_method_dialog, null);
    LinearLayout choices = content.findViewById(R.id.methodChoices);
    AlertDialog dialog = new AlertDialog.Builder(activity, R.style.NanoTimerDialogTheme)
        .setView(content)
        .setCancelable(false)
        .create();
    for (CubeMethod method : METHOD_CHOICES) {
      choices.addView(methodCard(inflater, method, choices, dialog));
    }
    dialog.show();
  }

  /** A method as a card: what it is called, and the steps it will be read as. */
  private static View methodCard(LayoutInflater inflater, CubeMethod method, LinearLayout parent,
      AlertDialog dialog) {
    View card = inflater.inflate(R.layout.preferred_method_item, parent, false);
    ((ImageView) card.findViewById(R.id.imgMethod)).setImageResource(methodIcon(method));
    ((TextView) card.findViewById(R.id.tvMethodName)).setText(Utils.getMethodLabel(method));

    LinearLayout steps = card.findViewById(R.id.methodSteps);
    for (int step : methodSteps(method)) {
      TextView chip = (TextView) inflater.inflate(R.layout.method_step_chip, steps, false);
      chip.setText(step);
      steps.addView(chip);
    }

    if (parent.getChildCount() > 0) {
      ((LinearLayout.LayoutParams) card.getLayoutParams()).topMargin =
          parent.getResources().getDimensionPixelSize(R.dimen.smart_cube_row_gap);
    }
    card.setOnClickListener(view -> {
      Options.INSTANCE.setPreferredMethodAsked(true);
      Options.INSTANCE.setPreferredMethod(method);
      dialog.dismiss();
    });
    return card;
  }

  /** The step names the breakdown uses, so the card shows what picking the method buys. */
  private static int[] methodSteps(CubeMethod method) {
    if (method == CubeMethod.ROUX) {
      return new int[] {R.string.smartcube_step_fb, R.string.smartcube_step_sb,
          R.string.smartcube_step_cmll, R.string.smartcube_step_lse};
    }
    if (method == CubeMethod.LBL) {
      return new int[] {R.string.smartcube_step_cross, R.string.smartcube_step_layer1,
          R.string.smartcube_step_layer2, R.string.smartcube_step_ll};
    }
    return new int[] {R.string.smartcube_step_cross, R.string.smartcube_step_f2l,
        R.string.smartcube_step_oll, R.string.smartcube_step_pll};
  }

  private static int methodIcon(CubeMethod method) {
    if (method == CubeMethod.ROUX) {
      return R.drawable.ic_method_roux;
    }
    return method == CubeMethod.LBL ? R.drawable.ic_method_lbl : R.drawable.ic_method_cfop;
  }
}
