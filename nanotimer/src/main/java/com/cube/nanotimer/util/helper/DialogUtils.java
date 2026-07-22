package com.cube.nanotimer.util.helper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ActivityNotFoundException;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.core.app.ShareCompat;
import android.widget.Toast;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SolveShareFormat;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.ScrambleFormatterService;
import com.cube.nanotimer.util.YesNoListener;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveTypeStep;

public class DialogUtils {

  public static void showFragment(FragmentActivity a, DialogFragment df) {
    df.show(a.getSupportFragmentManager(), "dialog");
  }

  public static void showInfoMessage(Context context, String message) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
  }

  public static void showInfoMessage(Context context, int messageId) {
    Toast.makeText(context, messageId, Toast.LENGTH_LONG).show();
  }

  public static void showShortInfoMessage(Context context, int messageId) {
    Toast.makeText(context, messageId, Toast.LENGTH_SHORT).show();
  }

  public static AlertDialog showYesNoConfirmation(Context context, String message, final YesNoListener listener) {
    DialogInterface.OnClickListener clickListener = getYesNoClickListener(listener);

    AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.NanoTimerDialogTheme);
    return builder.setMessage(message)
        .setPositiveButton(R.string.yes, clickListener)
        .setNegativeButton(R.string.no, clickListener).show();
  }

  public static AlertDialog showYesNoConfirmation(Context context, int messageId, final YesNoListener listener) {
    return showConfirmCancelDialog(context, messageId, R.string.yes, R.string.no, listener);
  }

  public static AlertDialog showConfirmCancelDialog(Context context, int messageId, int parConfirmMessageId, int parCancelMessageId, final YesNoListener listener) {
    DialogInterface.OnClickListener clickListener = getYesNoClickListener(listener);

    AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.NanoTimerDialogTheme);
    return builder.setMessage(messageId)
        .setPositiveButton(parConfirmMessageId, clickListener)
        .setNegativeButton(parCancelMessageId, clickListener).show();
  }

  public static AlertDialog showOkDialog(Context context, String title, String message) {
    return new AlertDialog.Builder(context, R.style.NanoTimerDialogTheme)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show();
  }

  public static AlertDialog showOkDialog(Context context, int titleId, int messageId) {
    return new AlertDialog.Builder(context, R.style.NanoTimerDialogTheme)
            .setTitle(titleId)
            .setMessage(messageId)
            .setPositiveButton(R.string.ok, null)
            .show();
  }

  public static void shareData(Activity activity, String subject, String text, Uri uri, String mimeType) {
    ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.from(activity)
      .setType(mimeType)
      .setSubject(subject)
      .setText(text)
      .setChooserTitle(R.string.send_via);
    if (uri != null) {
      builder.setStream(uri);
    }
    Intent i = builder.createChooserIntent()
      .addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    try {
      activity.startActivity(i);
    } catch (ActivityNotFoundException e) {
      showInfoMessage(activity, R.string.no_app_to_share);
    }
  }

  /** A cube-recorded solve first asks whether to include the breakdown and reconstruction —
   * the long form hands over everything needed to look into a solve, but is noise for a plain
   * "look at my time" share. */
  public static void shareTime(final Activity activity, final SolveTime solveTime, final CubeType cubeType) {
    if (!solveTime.hasSmartcubeMoves()) {
      shareTime(activity, solveTime, cubeType, false);
      return;
    }
    showYesNoConfirmation(activity, R.string.share_include_breakdown, new YesNoListener() {
      @Override
      public void onYes() {
        shareTime(activity, solveTime, cubeType, true);
      }

      @Override
      public void onNo() {
        shareTime(activity, solveTime, cubeType, false);
      }
    });
  }

  private static void shareTime(Activity activity, SolveTime solveTime, CubeType cubeType,
      boolean withSmartcubeData) {
    String timeStr = FormatterService.INSTANCE.formatSolveTime(solveTime.getTime());
    String timestampStr = FormatterService.INSTANCE.formatExportDateTime(solveTime.getTimestamp());
    String subject = activity.getString(R.string.share_time_subject, timeStr);
    String playStorePage = "http://play.google.com/store/apps/details?id=" + activity.getPackageName();
    String scramble = ScrambleFormatterService.INSTANCE.formatScrambleForExport(solveTime.getScramble(), cubeType);
    String text;
    if (solveTime.hasSteps()) {
      StringBuilder stepsSb = new StringBuilder();
      SolveTypeStep[] stepsNames = solveTime.getSolveType().getSteps();
      Long[] stepsTimes = solveTime.getStepsTimes();
      for (int i = 0; i < stepsTimes.length; i++) {
        String stepName = stepsNames[i].getName();
        String stepTime = FormatterService.INSTANCE.formatSolveTime(stepsTimes[i]);
        stepsSb.append("- ").append(stepName).append(": ").append(stepTime).append("\n");
      }
      text = activity.getString(R.string.share_time_steps_text, cubeType.getName(), timeStr, stepsSb.toString(), scramble, timestampStr, playStorePage);
    } else {
      text = activity.getString(R.string.share_time_text, cubeType.getName(), timeStr, scramble, timestampStr, playStorePage);
    }
    if (withSmartcubeData) {
      text += "\n\n" + SolveShareFormat.smartcubeSection(activity, solveTime);
    }
    shareData(activity, subject, text, null, "text/plain");
  }

  public static void copyScrambleToClipboard(Context context, String scramble) {
    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard != null) {
      clipboard.setPrimaryClip(ClipData.newPlainText("scramble", scramble));
      showShortInfoMessage(context, R.string.scramble_copied);
    }
  }

  private static DialogInterface.OnClickListener getYesNoClickListener(final YesNoListener listener) {
    return new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        switch (which) {
          case DialogInterface.BUTTON_POSITIVE:
            listener.onYes();
            break;
          case DialogInterface.BUTTON_NEGATIVE:
            listener.onNo();
            break;
        }
      }
    };
  }

}
