package com.cube.nanotimer.util.usage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

/** The weekly download of the usage figures: see {@link UsageShares}. */
public final class UsageSharesWorker extends Worker {

  private static final int MAX_ATTEMPTS = 3;

  public UsageSharesWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
    super(context, parameters);
  }

  @NonNull
  @Override
  public Result doWork() {
    Context context = getApplicationContext();
    UsageClient client = UsageClient.configured(context);
    if (client == null) {
      return Result.success();
    }
    try {
      String text = client.shares();
      if (text != null) {
        UsageShares.store(context, text);
      }
      return Result.success(); // a refusal or a bad snapshot keeps the figures already here
    } catch (IOException e) {
      return getRunAttemptCount() + 1 < MAX_ATTEMPTS ? Result.retry() : Result.success();
    }
  }
}
