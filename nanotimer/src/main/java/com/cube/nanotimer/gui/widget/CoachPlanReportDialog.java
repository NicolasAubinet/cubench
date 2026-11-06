package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.cube.nanotimer.R;
import com.cube.nanotimer.util.coach.CoachClient;
import com.cube.nanotimer.util.coach.CoachPlanReport;
import com.cube.nanotimer.util.helper.Utils;

/** "Report this plan", from the Plan tab's menu: a reason, an optional note, sent at once. */
public final class CoachPlanReportDialog {

  private CoachPlanReportDialog() {
  }

  public static void show(final Activity activity, final CoachClient client, final String planId) {
    View body = activity.getLayoutInflater().inflate(R.layout.coach_plan_report_dialog, null);
    final RadioGroup reasons = body.findViewById(R.id.rgPlanReportReason);
    final EditText tfComment = body.findViewById(R.id.tfPlanReportComment);

    final AlertDialog dialog = new AlertDialog.Builder(activity, R.style.NanoTimerDialogTheme)
        .setTitle(R.string.coach_report_title)
        .setView(body)
        .setPositiveButton(R.string.share_send_report, null)
        .setNegativeButton(R.string.cancel, null)
        .show();
    final Button btSend = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
    btSend.setEnabled(false);
    reasons.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(RadioGroup group, int checkedId) {
        btSend.setEnabled(checkedId != -1);
      }
    });
    btSend.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        CoachPlanReport.Reason reason = reasonOf(reasons.getCheckedRadioButtonId());
        if (reason == null) {
          return;
        }
        send(activity, client, planId, CoachPlanReport.body(reason,
            tfComment.getText().toString(), Utils.getAppVersion(activity)), dialog);
      }
    });
  }

  private static CoachPlanReport.Reason reasonOf(int checkedId) {
    if (checkedId == R.id.rbPlanReportOffensive) {
      return CoachPlanReport.Reason.OFFENSIVE;
    } else if (checkedId == R.id.rbPlanReportHarmful) {
      return CoachPlanReport.Reason.HARMFUL;
    } else if (checkedId == R.id.rbPlanReportOther) {
      return CoachPlanReport.Reason.OTHER;
    }
    return null;
  }

  /** The dialog stays up while sending, and after a failure, so the note is not lost. */
  private static void send(final Activity activity, final CoachClient client, final String planId,
      final String body, final AlertDialog dialog) {
    final Button btSend = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
    btSend.setEnabled(false);
    final Context appContext = activity.getApplicationContext();
    new Thread(new Runnable() {
      @Override
      public void run() {
        final CoachClient.PlanReportOutcome outcome = client.sendPlanReport(planId, body);
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            boolean done = outcome != CoachClient.PlanReportOutcome.FAILED;
            if (done && dialog.isShowing() && !activity.isFinishing()) {
              dialog.dismiss();
            } else if (!done) {
              btSend.setEnabled(true);
            }
            int message = outcome == CoachClient.PlanReportOutcome.SENT ? R.string.coach_report_sent
                : outcome == CoachClient.PlanReportOutcome.GONE ? R.string.coach_report_gone
                : R.string.coach_report_failed;
            Toast.makeText(appContext, message,
                done ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
          }
        });
      }
    }, "plan-report").start();
  }
}
