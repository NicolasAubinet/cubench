package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.content.SharedPreferences;
import com.cube.nanotimer.AppLaunchStats;
import com.cube.nanotimer.util.helper.Utils;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;

/**
 * Requests the Play In-App Review card at a good moment.
 * Google gives no feedback on whether the card was shown or the user rated, and it throttles
 * how often it appears, so the local gate below only serves to keep our requests sparse.
 */
public class InAppReviewManager {

  private final static int SOLVES_UNTIL_PROMPT = 50;
  private final static int DAYS_UNTIL_PROMPT = 3;
  private final static int DAYS_BETWEEN_REQUESTS = 30;

  /**
   * Set to true (and upload to an internal testing track) to fire on the first solve, to check
   * how the card looks. BuildConfig.DEBUG is no use here: the card only shows for a build that
   * Play installed, which is never a debug build.
   */
  private final static boolean TEST_MODE = false;

  private final static String PREFS_NAME = "apprater";
  private final static String KEY_LAST_REQUEST = "date_lastrequest";
  private final static String KEY_DONT_SHOW_AGAIN = "dontshowagain";

  /**
   * Requests a review if the local gate allows it. Fails silently if the flow is unavailable
   * (offline, no Play Store, quota reached).
   */
  public static void maybeRequestReview(final Activity activity, int solvesCount) {
    if (!canRequest(activity, solvesCount)) {
      return;
    }

    final ReviewManager manager = ReviewManagerFactory.create(activity);
    manager.requestReviewFlow().addOnCompleteListener(request -> {
      if (!request.isSuccessful()) {
        return;
      }
      ReviewInfo reviewInfo = request.getResult();
      manager.launchReviewFlow(activity, reviewInfo).addOnCompleteListener(result -> {
        // Resolves the same way whether or not the card was shown, so this only advances the cooldown.
        recordRequested(activity);
      });
    });
  }

  private static boolean canRequest(Activity activity, int solvesCount) {
    SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, 0);
    if (prefs.getBoolean(KEY_DONT_SHOW_AGAIN, false)) { // set by the legacy rate dialog
      return false;
    }
    if (TEST_MODE) {
      return true;
    }

    long firstLaunchDate = AppLaunchStats.getFirstLaunchDate(activity);
    long lastRequestDate = prefs.getLong(KEY_LAST_REQUEST, 0);

    long currentTime = System.currentTimeMillis();
    return solvesCount >= SOLVES_UNTIL_PROMPT &&
        currentTime >= firstLaunchDate + Utils.daysToMs(DAYS_UNTIL_PROMPT) &&
        (lastRequestDate == 0 || currentTime >= lastRequestDate + Utils.daysToMs(DAYS_BETWEEN_REQUESTS));
  }

  private static void recordRequested(Activity activity) {
    activity.getSharedPreferences(PREFS_NAME, 0).edit()
        .putLong(KEY_LAST_REQUEST, System.currentTimeMillis())
        .apply();
  }

}
