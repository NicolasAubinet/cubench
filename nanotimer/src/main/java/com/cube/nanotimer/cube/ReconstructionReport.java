package com.cube.nanotimer.cube;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveTime;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A "Reconstruction wrong?" report as the coach service takes it: one versioned JSON object.
 *
 * <p>{@code smartcube} holds the stored fields under the names the shared text gives them, so the
 * replay tests read a report's moves, steps and gyro track as they read a paste. {@code shown} is
 * the breakdown as the app displayed it, which says what the user saw after the reading changes.
 */
public final class ReconstructionReport {

  public static final int VERSION = 1;

  private ReconstructionReport() {
  }

  /**
   * @param gyroTrack the solve's stored track, read separately, or null where it has none
   * @param cube the cube that recorded the solve, read separately, or null where it is not known
   * @param replyEmail what the user typed, or empty
   */
  public static JSONObject build(Context context, SolveTime solveTime, CubeType cubeType,
      String gyroTrack, String cube, String comment, String replyEmail) {
    try {
      JSONObject report = new JSONObject()
          .put("report_version", VERSION)
          .put("report_id", UUID.randomUUID().toString())
          .put("created_at", iso(System.currentTimeMillis()))
          .put("comment", comment)
          .put("app", app(context))
          .put("device", device())
          .put("solve", solve(solveTime, cubeType))
          .put("smartcube", smartcube(SolveShareFormat.rawFields(solveTime, gyroTrack, cube)))
          .put("shown", new JSONObject().put("breakdown",
              SolveShareFormat.smartcubeSection(context, solveTime, null, null, false)));
      if (!replyEmail.isEmpty()) {
        report.put("reply_email", replyEmail);
      }
      return report;
    } catch (JSONException e) {
      throw new IllegalStateException(e); // only strings and numbers go in
    }
  }

  static JSONObject solve(SolveTime solveTime, CubeType cubeType) throws JSONException {
    return new JSONObject()
        .put("id", solveTime.getId())
        .put("puzzle", cubeType.getName())
        .put("solve_type", solveTime.getSolveType() == null ? null
            : solveTime.getSolveType().getName())
        .put("time_ms", solveTime.getTime())
        .put("time_text", FormatterService.INSTANCE.formatSolveTime(solveTime))
        .put("penalty", solveTime.isDNF() ? "dnf" : solveTime.isPlusTwo() ? "+2" : "none")
        .put("timestamp", iso(solveTime.getTimestamp()))
        .put("scramble", solveTime.getScramble());
  }

  static JSONObject smartcube(Map<String, String> fields) throws JSONException {
    JSONObject smartcube = new JSONObject();
    for (Map.Entry<String, String> field : fields.entrySet()) {
      smartcube.put(field.getKey(), field.getValue());
    }
    return smartcube;
  }

  private static JSONObject app(Context context) throws JSONException {
    JSONObject app = new JSONObject();
    try {
      PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      app.put("version_name", info.versionName).put("version_code", info.versionCode);
    } catch (PackageManager.NameNotFoundException e) {
      // Our own package; the report still goes without the version.
    }
    boolean debug = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    return app.put("build_type", debug ? "debug" : "release");
  }

  private static JSONObject device() throws JSONException {
    return new JSONObject()
        .put("release", Build.VERSION.RELEASE)
        .put("sdk", Build.VERSION.SDK_INT)
        .put("manufacturer", Build.MANUFACTURER)
        .put("model", Build.MODEL);
  }

  static String iso(long epochMs) {
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
    format.setTimeZone(TimeZone.getTimeZone("UTC"));
    return format.format(new Date(epochMs));
  }
}
