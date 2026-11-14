package com.cube.nanotimer.util.usage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.cube.AlgorithmVotes;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.vo.CaseHistory;
import com.cube.nanotimer.vo.SolveTime;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The daily usage send: reads the recent smart-cube solves, and sends the cases whose vote changed
 * since the service last took one (every case, the first time). Nothing changed, nothing is sent.
 */
public final class UsageWorker extends Worker {

  /** About five showings of each OLL case; the votes read a case's five latest answers. */
  static final int SOLVES_READ = 300;
  /** After this many tries in a row the send waits for the next day. */
  private static final int MAX_ATTEMPTS = 5;
  private static final long READ_WAIT_S = 120;

  public UsageWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
    super(context, parameters);
  }

  @NonNull
  @Override
  public Result doWork() {
    Context context = getApplicationContext();
    App.INSTANCE.setApplicationContext(context); // the process may have been started for this alone
    UsageClient client = UsageClient.configured(context);
    if (!UsageSharing.isActive(context) || client == null || !client.canSend()) {
      return Result.success();
    }
    // Taken once, before anything slow: a switch-off meanwhile clears it, and must not mint a new one
    String sharingId = Options.INSTANCE.getUsageSharingId();
    try {
      Map<String, String> changed = AlgorithmVotes.changedSince(
          AlgorithmVotes.readFrom(recentSolves()), Options.INSTANCE.getUsageReported());
      if (changed.isEmpty() || !stillSharing(sharingId)) {
        return Result.success();
      }
      UsageReport report = UsageReport.of(changed);
      UsageReport.Answer answer;
      try {
        answer = client.send(report, sharingId);
      } finally {
        // Turned off while this was on its way: its forget may already have run, so ask again.
        if (!stillSharing(sharingId)) {
          UsageSharing.forget(context, sharingId);
        }
      }
      if (answer == null || !stillSharing(sharingId)) {
        return Result.success(); // no integrity token today, or nothing to keep
      }
      if (answer.getKind() == UsageReport.Kind.RETRY) {
        return retry();
      }
      if (answer.getKind() == UsageReport.Kind.TAKEN) {
        Options.INSTANCE.addUsageReported(answer.getTaken());
      }
      return Result.success();
    } catch (IOException e) {
      return retry();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.retry();
    }
  }

  private Result retry() {
    return getRunAttemptCount() + 1 < MAX_ATTEMPTS ? Result.retry() : Result.success();
  }

  private static boolean stillSharing(String sharingId) {
    return Options.INSTANCE.isUsageSharingOn()
        && sharingId.equals(Options.INSTANCE.peekUsageSharingId());
  }

  private static List<SolveTime> recentSolves() throws InterruptedException, IOException {
    final CountDownLatch latch = new CountDownLatch(1);
    final CaseHistory[] history = new CaseHistory[1];
    App.INSTANCE.getService().getCaseHistory(SOLVES_READ, new DataCallback<CaseHistory>() {
      @Override
      public void onData(CaseHistory data) {
        history[0] = data;
        latch.countDown();
      }
    });
    if (!latch.await(READ_WAIT_S, TimeUnit.SECONDS) || history[0] == null) {
      throw new IOException("recent solves could not be read"); // retried like a failed send
    }
    return history[0].getSolves();
  }
}
