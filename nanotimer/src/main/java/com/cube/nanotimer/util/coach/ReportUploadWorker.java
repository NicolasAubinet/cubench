package com.cube.nanotimer.util.coach;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Sends the reconstruction reports waiting on the phone.
 *
 * <p>A report is written to {@code pending_reports} before anything is sent, so a dead network or a
 * crash loses nothing, and it stays there until the service has it. A resend carries the same
 * {@code report_id}, which the service keeps once. A report the service refuses for what it is
 * (any 4xx but 429) is dropped, since sending it again cannot change the answer, and so is one
 * still waiting after two weeks.
 */
public final class ReportUploadWorker extends Worker {

  private static final String WORK = "recon_reports";
  private static final String WORK_LATER = "recon_reports_later";
  private static final String DIR = "pending_reports";
  private static final long KEPT_MS = TimeUnit.DAYS.toMillis(14);

  public ReportUploadWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
    super(context, parameters);
  }

  /** Keeps the report on the phone and sends it as soon as there is a network. */
  public static void send(Context context, JSONObject report) throws IOException {
    String id;
    try {
      id = report.getString("report_id");
    } catch (JSONException e) {
      throw new IllegalArgumentException("A report carries its id", e);
    }
    File dir = dir(context);
    File partial = new File(dir, id + ".part");
    try (OutputStream out = new FileOutputStream(partial)) {
      out.write(report.toString().getBytes(StandardCharsets.UTF_8));
    }
    if (!partial.renameTo(new File(dir, id + ".json"))) {
      throw new IOException("Could not keep report " + id);
    }
    enqueue(context, WORK, 0);
  }

  @NonNull
  @Override
  public Result doWork() {
    CoachClient client = CoachClient.configured(getApplicationContext());
    File[] waiting = dir(getApplicationContext()).listFiles((d, name) -> name.endsWith(".json"));
    if (client == null || waiting == null) {
      return Result.success();
    }
    Arrays.sort(waiting, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
    boolean retry = false;
    for (File file : waiting) {
      if (System.currentTimeMillis() - file.lastModified() > KEPT_MS) {
        file.delete();
        continue;
      }
      CoachClient.ReportAnswer answer;
      try {
        answer = client.postReport(read(file));
      } catch (IOException e) {
        retry = true;
        continue;
      }
      if (answer.status == 429) {
        // Every report behind it would be refused too, so all of them wait as long as asked.
        enqueue(getApplicationContext(), WORK_LATER, Math.max(answer.retryAfterS, 300));
        return Result.success();
      }
      if (answer.status >= 500) {
        retry = true;
      } else {
        file.delete(); // 2xx is kept by the service; any other 4xx never will be
      }
    }
    return retry ? Result.retry() : Result.success();
  }

  private static void enqueue(Context context, String name, long delayS) {
    OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ReportUploadWorker.class)
        .setConstraints(new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
        .setInitialDelay(delayS, TimeUnit.SECONDS)
        .build();
    WorkManager.getInstance(context.getApplicationContext())
        .enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request);
  }

  private static File dir(Context context) {
    File dir = new File(context.getFilesDir(), DIR);
    dir.mkdirs();
    return dir;
  }

  private static String read(File file) throws IOException {
    byte[] bytes = new byte[(int) file.length()];
    try (FileInputStream in = new FileInputStream(file)) {
      int read = 0;
      while (read < bytes.length) {
        int count = in.read(bytes, read, bytes.length - read);
        if (count < 0) {
          break;
        }
        read += count;
      }
    }
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
