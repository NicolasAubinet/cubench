package com.cube.nanotimer.util.helper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ActivityNotFoundException;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.core.app.ShareCompat;
import androidx.core.content.ContextCompat;
import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.ReconstructionReport;
import com.cube.nanotimer.cube.SolveShareFormat;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.ScrambleFormatterService;
import com.cube.nanotimer.util.coach.CoachClient;
import com.cube.nanotimer.util.coach.ReportUploadWorker;
import com.cube.nanotimer.util.YesNoListener;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveTypeStep;
import java.io.IOException;

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

  public static AlertDialog showConfirmCancelDialog(Context context, int titleId, String message, int parConfirmMessageId, int parCancelMessageId, final YesNoListener listener) {
    DialogInterface.OnClickListener clickListener = getYesNoClickListener(listener);

    AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.NanoTimerDialogTheme);
    return builder.setTitle(titleId)
        .setMessage(message)
        .setPositiveButton(parConfirmMessageId, clickListener)
        .setNegativeButton(parCancelMessageId, clickListener).show();
  }

  /** For a confirmation that throws something away: the answer is filled red rather than styled
   * like the way out, and a title names the consequence before the message spells it out. */
  public static AlertDialog showDestructiveConfirmDialog(Context context, int titleId, int messageId, int parConfirmMessageId, int parCancelMessageId, final YesNoListener listener) {
    return showDestructiveConfirmDialog(context, titleId, context.getString(messageId), parConfirmMessageId, parCancelMessageId, listener);
  }

  public static AlertDialog showDestructiveConfirmDialog(Context context, int titleId, String message, int parConfirmMessageId, int parCancelMessageId, final YesNoListener listener) {
    DialogInterface.OnClickListener clickListener = getYesNoClickListener(listener);

    AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.NanoTimerDangerDialogTheme);
    return builder.setTitle(titleId)
        .setMessage(message)
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

  /**
   * Shares one solve: the time and the scramble, and the step breakdown where a cube recorded one.
   * Nothing is asked, because there is nothing to ask — the breakdown is the solve as the sheet
   * tells it. The raw data underneath, which is for looking into a reading that came out wrong
   * rather than for reading, has its own way out in {@link #reportReconstruction}.
   */
  public static void shareTime(Activity activity, SolveTime solveTime, CubeType cubeType) {
    String timeStr = FormatterService.INSTANCE.formatSolveTime(solveTime);
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
    // Empty for a solve type carrying its own steps, whose breakdown is the one already shared above.
    String breakdown = solveTime.hasSmartcubeMoves()
        ? SolveShareFormat.smartcubeSection(activity, solveTime, null, null, false) : "";
    if (!breakdown.isEmpty()) {
      text += "\n\n" + breakdown;
    }
    shareData(activity, subject, text, null, "text/plain");
  }

  /**
   * The other way out, from the breakdown's own "Reconstruction wrong?": says what will leave the
   * phone, takes the one thing the data cannot say for itself, and sends the lot to the developer.
   * It goes straight to the coach service, kept on the phone until it arrives; a build without a
   * coach address mails it instead, which is the only place the mail route is left.
   */
  public static void reportReconstruction(final Activity activity, final SolveTime solveTime,
      final CubeType cubeType) {
    View body = activity.getLayoutInflater().inflate(R.layout.report_reconstruction_dialog, null);
    final EditText tfComment = (EditText) body.findViewById(R.id.tfReportComment);
    final EditText tfEmail = (EditText) body.findViewById(R.id.tfReportEmail);
    final boolean byPost = CoachClient.configured(activity) != null;
    if (!byPost) {
      tfEmail.setVisibility(View.GONE); // a mail already says who sent it
    }

    final AlertDialog dialog = new AlertDialog.Builder(activity, R.style.NanoTimerDialogTheme)
        .setTitle(R.string.report_reconstruction)
        .setView(body)
        .setPositiveButton(R.string.share_send_report, null)
        .setNegativeButton(R.string.close, null)
        .show();
    if (!byPost) {
      // Send leaves the dialog up until an app is picked, so backing out of the chooser keeps the comment.
      closeOnAppChosen(activity, dialog);
    }
    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        final String comment = tfComment.getText().toString().trim();
        final String email = tfEmail.getText().toString().trim();
        if (!email.isEmpty() && !email.contains("@")) {
          tfEmail.setError(activity.getString(R.string.report_reconstruction_email_invalid));
          return;
        }
        withStoredSmartcubeData(activity, solveTime, dialog, new StoredSmartcubeData() {
          @Override
          public void with(String gyroTrack, String cube) {
            if (byPost) {
              postReport(activity, solveTime, cubeType, gyroTrack, cube, comment, email, dialog);
            } else {
              sendReport(activity, solveTime, cubeType, gyroTrack, cube, comment);
            }
          }
        });
      }
    });
  }

  /**
   * Builds the report off the main thread, since reading the solve again replays it, keeps it on
   * the phone and closes. The upload happens in the background, so "sent" is said at once.
   */
  private static void postReport(final Activity activity, final SolveTime solveTime,
      final CubeType cubeType, final String gyroTrack, final String cube, final String comment,
      final String email, final AlertDialog dialog) {
    final Button btSend = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
    btSend.setEnabled(false);
    final Context appContext = activity.getApplicationContext();
    new Thread(new Runnable() {
      @Override
      public void run() {
        boolean kept;
        try {
          ReportUploadWorker.send(appContext, ReconstructionReport.build(activity, solveTime,
              cubeType, gyroTrack, cube, comment, email));
          kept = true;
        } catch (IOException e) {
          kept = false;
        }
        final boolean sent = kept;
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (sent) {
              dialog.dismiss();
              Toast.makeText(appContext, R.string.report_reconstruction_sent, Toast.LENGTH_SHORT).show();
            } else {
              btSend.setEnabled(true);
              Toast.makeText(appContext, R.string.report_reconstruction_not_kept, Toast.LENGTH_LONG).show();
            }
          }
        });
      }
    }, "report").start();
  }

  /** Runs on the main thread once a solve's stored track and cube are read. */
  private interface StoredSmartcubeData {
    void with(String gyroTrack, String cube);
  }

  private static String reportAppChosenAction(Context context) {
    return context.getPackageName() + ".REPORT_APP_CHOSEN";
  }

  /** The chooser reports the app picked (API 22+), and that pick is the send. */
  private static void closeOnAppChosen(final Activity activity, final AlertDialog dialog) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
      return;
    }
    final BroadcastReceiver receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        dialog.dismiss();
      }
    };
    ContextCompat.registerReceiver(activity, receiver, new IntentFilter(reportAppChosenAction(activity)),
        ContextCompat.RECEIVER_NOT_EXPORTED);
    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
      @Override
      public void onDismiss(DialogInterface d) {
        activity.unregisterReceiver(receiver);
      }
    });
  }

  /** The track and the cube are fetched here rather than carried on the solve: the track is
   * kilobytes, and a history screen holds hundreds of solves it would never look at. */
  private static void withStoredSmartcubeData(final Activity activity, final SolveTime solveTime,
      final AlertDialog dialog, final StoredSmartcubeData then) {
    if (solveTime.getId() <= 0) {
      then.with(null, solveTime.getSmartcubeCube());
      return;
    }
    final Button btSend = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
    btSend.setEnabled(false); // a second tap during the fetch would open the chooser twice
    App.INSTANCE.getService().getGyroTrack(solveTime.getId(), new DataCallback<String>() {
      @Override
      public void onData(final String gyroTrack) {
        App.INSTANCE.getService().getSmartcubeCube(solveTime.getId(), new DataCallback<String>() {
          @Override
          public void onData(final String cube) {
            activity.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                btSend.setEnabled(true);
                if (!activity.isFinishing()) {
                  then.with(gyroTrack, cube);
                }
              }
            });
          }
        });
      }
    });
  }

  /** Opens on what the user saw, typed or still to type. A phone with no mail app falls back to the
   * chooser, where the same text goes by whatever it does have. */
  private static void sendReport(Activity activity, SolveTime solveTime, CubeType cubeType,
      String gyroTrack, String cube, String comment) {
    String timeStr = FormatterService.INSTANCE.formatSolveTime(solveTime);
    String opening = comment.isEmpty()
        ? activity.getString(R.string.report_reconstruction_intro) + "\n\n\n" : comment;
    String text = activity.getString(R.string.report_reconstruction_header) + "\n\n" + opening + "\n\n"
        + activity.getString(R.string.report_reconstruction_environment,
            Utils.getAppBuild(activity), Build.VERSION.RELEASE, Build.MODEL) + "\n"
        + cubeType.getName() + " · " + timeStr + " · "
        + FormatterService.INSTANCE.formatExportDateTime(solveTime.getTimestamp()) + "\n\n"
        + activity.getString(R.string.scramble) + "\n"
        + ScrambleFormatterService.INSTANCE.formatScrambleForExport(solveTime.getScramble(), cubeType)
        + "\n\n" + SolveShareFormat.smartcubeSection(activity, solveTime, gyroTrack, cube, true);
    // Tagged and untranslated, so every report a mailbox rule has to catch looks alike.
    String subject = activity.getString(R.string.report_reconstruction_subject, cubeType.getName(),
        timeStr, Utils.getAppVersion(activity));

    // A send rather than a mailto, because a mailto is addressed by its URI and Gmail then drops
    // the subject and the body, which arrive as extras. The mail mime type keeps the list to apps
    // that send mail; a phone with none of those falls back to sharing the same text anywhere.
    Intent mail = new Intent(Intent.ACTION_SEND)
        .setType("message/rfc822")
        .putExtra(Intent.EXTRA_EMAIL, new String[] { activity.getString(R.string.email) })
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text);
    String title = activity.getString(R.string.send_via);
    Intent chooser;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
      // Mutable so the chooser can fill in the app picked; the package keeps the intent explicit.
      int flags = PendingIntent.FLAG_UPDATE_CURRENT
          | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
      Intent chosen = new Intent(reportAppChosenAction(activity)).setPackage(activity.getPackageName());
      IntentSender onChosen = PendingIntent.getBroadcast(activity, 0, chosen, flags).getIntentSender();
      chooser = Intent.createChooser(mail, title, onChosen);
    } else {
      chooser = Intent.createChooser(mail, title);
    }
    try {
      activity.startActivity(chooser);
    } catch (ActivityNotFoundException e) {
      shareData(activity, subject, text, null, "text/plain");
    }
  }

  /**
   * Caps a dialog's body to the room its buttons leave. A dialog measures its view unbounded, so an
   * overflowing body squeezes the button panel below it and clips the buttons instead of scrolling.
   */
  public static void fitToWindow(final AlertDialog dialog, final View body) {
    body.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT; // the room may have grown
    body.requestLayout();
    body.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        body.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        if (dialog.getWindow() == null || dialog.getButton(DialogInterface.BUTTON_POSITIVE) == null) {
          return;
        }
        // The squeeze on the button panel is the overflow.
        View buttonBar = (View) dialog.getButton(DialogInterface.BUTTON_POSITIVE).getParent();
        View buttonPanel = (View) buttonBar.getParent();
        int needed = buttonBar.getHeight() + buttonPanel.getPaddingTop() + buttonPanel.getPaddingBottom();
        int overflow = needed - buttonPanel.getHeight();
        if (overflow > 0) {
          body.getLayoutParams().height = body.getHeight() - overflow;
          body.requestLayout();
        }
      }
    });
  }

  public static void copyScrambleToClipboard(Context context, String scramble) {
    copyToClipboard(context, "scramble", scramble, R.string.scramble_copied);
  }

  public static void copyToClipboard(Context context, String label, String text, int copiedMessageId) {
    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard != null) {
      clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
      showShortInfoMessage(context, copiedMessageId);
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
