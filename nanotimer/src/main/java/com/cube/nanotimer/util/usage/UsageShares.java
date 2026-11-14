package com.cube.nanotimer.util.usage;

import android.content.Context;
import android.content.pm.PackageManager;

import com.cube.nanotimer.R;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.UsageFigures;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The usage figures the case popup and the drill's choose step order algorithms by, as the coach
 * service publishes them ({@code GET /v1/usage/shares}): kept in a file, fetched at most weekly.
 *
 * <p>Two sources, the newer wins: the last download, and a snapshot bundled in the APK at release
 * ({@value #ASSET}, optional), which a fresh install starts from. A download older than the app's
 * last update loses to the bundled one.
 *
 * <p>Fetching sends nothing but the request: no sharing id, no Coach ID.
 */
public final class UsageShares {

  static final String FILE = "usage_shares.json";
  static final String ASSET = "usage_shares.json";
  /** Larger than any snapshot the service can publish; a bigger answer is not read. */
  private static final int MAX_BYTES = 2 * 1024 * 1024;

  private static final Object LOCK = new Object();
  private static boolean downloaded;

  private UsageShares() {
  }

  /** Reads the newest snapshot into {@link CaseAlgorithms}. Blocks: off the main thread. */
  public static void load(Context context) {
    File file = new File(context.getFilesDir(), FILE);
    boolean fresh = file.exists() && file.lastModified() >= lastUpdate(context);
    UsageFigures figures = fresh ? parse(readFile(file)) : null;
    if (figures == null) {
      figures = parse(readAsset(context));
    }
    if (figures == null && !fresh) {
      figures = parse(readFile(file));
    }
    synchronized (LOCK) {
      if (figures != null && !downloaded) { // a download while this read is newer than it
        CaseAlgorithms.setUsage(figures);
      }
    }
  }

  /** A share as a row shows it: whole percent, and under one percent as such rather than as 0. */
  public static String label(Context context, float share) {
    int rounded = Math.round(share);
    return rounded < 1 ? context.getString(R.string.case_algorithm_share_small)
        : context.getString(R.string.case_algorithm_share, rounded);
  }

  /** Keeps a downloaded snapshot and shows it at once. False when it does not read. */
  static boolean store(Context context, String text) throws IOException {
    UsageFigures figures = parse(text);
    if (figures == null) {
      return false;
    }
    File file = new File(context.getFilesDir(), FILE);
    File part = new File(context.getFilesDir(), FILE + ".part");
    try (FileOutputStream out = new FileOutputStream(part)) {
      out.write(text.getBytes(StandardCharsets.UTF_8));
    }
    if (!part.renameTo(file)) {
      part.delete();
      throw new IOException("could not keep " + FILE);
    }
    synchronized (LOCK) {
      downloaded = true;
      CaseAlgorithms.setUsage(figures);
    }
    return true;
  }

  /**
   * The service's snapshot, {@code {"cases": {"pll_t": {"users": 120, "algorithms": [{"moves":
   * "...", "share": 61.5}]}}}}, or null when it does not read. A malformed case is skipped.
   */
  static UsageFigures parse(String text) {
    if (text == null) {
      return null;
    }
    try {
      JSONObject all = new JSONObject(text).getJSONObject("cases");
      Map<String, UsageFigures.Case> cases = new LinkedHashMap<String, UsageFigures.Case>();
      for (Iterator<String> codes = all.keys(); codes.hasNext(); ) {
        String code = codes.next();
        JSONObject one = all.optJSONObject(code);
        JSONArray rows = one == null ? null : one.optJSONArray("algorithms");
        if (rows == null) {
          continue;
        }
        List<UsageFigures.Share> shares = new ArrayList<UsageFigures.Share>();
        for (int i = 0; i < rows.length(); i++) {
          JSONObject row = rows.optJSONObject(i);
          String moves = row == null ? null : row.optString("moves", null);
          double share = row == null ? Double.NaN : row.optDouble("share", Double.NaN);
          if (moves != null && share >= 0 && share <= 100) {
            shares.add(new UsageFigures.Share(moves, (float) share));
          }
        }
        cases.put(code, new UsageFigures.Case(one.optInt("users", 0), shares));
      }
      return new UsageFigures(cases);
    } catch (JSONException e) {
      return null;
    }
  }

  static String read(InputStream in) throws IOException {
    try (InputStream stream = in) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      for (int read; (read = stream.read(buffer)) != -1; ) {
        bytes.write(buffer, 0, read);
        if (bytes.size() > MAX_BYTES) {
          throw new IOException("usage snapshot too large");
        }
      }
      return bytes.toString(StandardCharsets.UTF_8.name());
    }
  }

  private static String readFile(File file) {
    try {
      return read(new FileInputStream(file));
    } catch (IOException e) {
      return null;
    }
  }

  private static String readAsset(Context context) {
    try {
      return read(context.getAssets().open(ASSET));
    } catch (IOException e) {
      return null; // none bundled
    }
  }

  private static long lastUpdate(Context context) {
    try {
      return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
    } catch (PackageManager.NameNotFoundException e) {
      return 0;
    }
  }
}
